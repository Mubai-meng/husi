// measure.go — 自研吞吐计量内核。
//
// 背景: speedtest-go v1.8.3 的计量层(DLSpeed/ULSpeed, EWMA/Welford 管线)
// 经离线仲裁测试证实不可靠 —— 本地回环满速场景(实测 ~440MB/s)其原始
// 速率值与真实字节速率/比特速率的比值均不接近 1(0.014 / 0.002), 偏差
// 约 70 倍且无法用任何单位解释。因此本包只保留 speedtest-go 的服务器
// 发现能力, 测量内核完全自研:
//
//   - 字节自己数: 下载在响应体读取处计数, 上传在请求体写出处计数
//     (即实际打到网络上的字节, 到时中止时也包含已写出的部分);
//   - 时间自己记: mark 采样点(时刻+累计字节), 单调时钟;
//   - 速率 = 字节增量 ÷ 时长, 单位恒为 Bytes/s, 不存在单位换算环节;
//   - 最终速率跳过前 measureWarmup 的预热期(TCP 慢启动/连接建立);
//   - 进度速率用最近 1s 窗口, 每 progressInterval 采样一次。
//
// 所有测量流量仍 100% 经过调用方提供的代理 client(回环 sing-box 入站)。
package speedtest

import (
	"bytes"
	"context"
	"io"
	"math/rand"
	"net/http"
	"net/url"
	"path"
	"sync"
	"sync/atomic"
	"time"
)

const (
	// downloadFileName speedtest.net 服务器提供的最大档位测试文件,
	// 单请求约 16MB; 请求期间被到时中止时已收字节照常计数。
	downloadFileName = "random4000x4000.jpg"
	// uploadChunkSize 单次上传请求的 payload 大小(4MB)。
	uploadChunkSize = 4 << 20
	// measureWarmup 最终速率跳过的预热时长(TCP 慢启动/连接建立)。
	measureWarmup = time.Second
	// progressInterval 进度采样与事件上报间隔。
	progressInterval = 250 * time.Millisecond
	// readBufferSize 下载读取缓冲。
	readBufferSize = 64 << 10
	// maxConsecutiveErrors 单 worker 连续请求失败上限(超过即退出该 worker)。
	maxConsecutiveErrors = 5
)

// throughputMeter 累计字节数 + 采样点, 支持窗口速率与去预热最终速率。
type throughputMeter struct {
	total atomic.Int64
	start time.Time

	mu    sync.Mutex
	marks []meterMark // 按时间递增
}

type meterMark struct {
	t time.Time
	n int64 // 该时刻的累计字节数
}

func newThroughputMeter() *throughputMeter {
	return &throughputMeter{start: time.Now()}
}

func (m *throughputMeter) add(n int64) { m.total.Add(n) }

// mark 记录一个(时刻, 累计字节)采样点。
func (m *throughputMeter) mark() {
	m.mu.Lock()
	m.marks = append(m.marks, meterMark{t: time.Now(), n: m.total.Load()})
	m.mu.Unlock()
}

// Total 累计字节数。
func (m *throughputMeter) Total() int64 { return m.total.Load() }

// windowRate 最近 window 时长的平均速率(Bytes/s); 采样不足 window 时
// 退化为自启动以来的平均速率。
func (m *throughputMeter) windowRate(window time.Duration) int64 {
	now := time.Now()
	total := m.total.Load()
	m.mu.Lock()
	defer m.mu.Unlock()
	if len(m.marks) == 0 {
		return 0
	}
	var baseN int64
	var baseT time.Time
	for _, mk := range m.marks {
		if now.Sub(mk.t) >= window {
			baseN, baseT = mk.n, mk.t
		} else {
			break
		}
	}
	if baseT.IsZero() {
		// 所有采样点都不足 window: 用启动时刻兜底。
		baseT = m.start
	}
	dt := now.Sub(baseT).Seconds()
	if dt <= 0 {
		return 0
	}
	return int64(float64(total-baseN) / dt)
}

// phaseRate 测量结束后的最终速率(Bytes/s): 跳过前 warmup 时长, 用之后
// 的字节增量 ÷ 时长。预热期覆盖全程(测量过短)或剩余窗口不足 500ms 时
// 退化为全程平均(偏保守, 不会虚高)。
func (m *throughputMeter) phaseRate(warmup time.Duration) (bps int64, total int64, elapsed time.Duration) {
	now := time.Now()
	total = m.total.Load()
	elapsed = now.Sub(m.start)
	if elapsed <= 0 || total <= 0 {
		return 0, total, elapsed
	}
	var baseN int64
	var baseT time.Time
	m.mu.Lock()
	for _, mk := range m.marks {
		if mk.t.Sub(m.start) >= warmup {
			baseN, baseT = mk.n, mk.t
			break
		}
	}
	m.mu.Unlock()
	if baseT.IsZero() || now.Sub(baseT) < 500*time.Millisecond {
		return int64(float64(total) / elapsed.Seconds()), total, elapsed
	}
	dt := now.Sub(baseT).Seconds()
	return int64(float64(total-baseN) / dt), total, elapsed
}

// countingReader 包装请求体, 在读出处计数(= 传输层实际消费/打出的字节)。
type countingReader struct {
	r      io.Reader
	onRead func(int64)
}

func (c *countingReader) Read(p []byte) (int, error) {
	n, err := c.r.Read(p)
	if n > 0 {
		c.onRead(int64(n))
	}
	return n, err
}

// downloadURLFor 由 speedtest.net 服务器的 upload.php URL 构造下载文件
// URL(与其官方协议一致: 同目录下 random{N}x{N}.jpg)。
func downloadURLFor(serverURL string) string {
	u, err := url.Parse(serverURL)
	if err != nil || u.Path == "" {
		return ""
	}
	u.Path = path.Dir(u.Path)
	return u.JoinPath(downloadFileName).String()
}

// resolveUploadURL 与 speedtest-go 的 resolveUploadURL 语义一致: HEAD
// 请求跟随重定向, 用最终 URL 作为上传端点(部分服务器会 302)。
func resolveUploadURL(ctx context.Context, client *http.Client, serverURL string) string {
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, serverURL, nil)
	if err != nil {
		return serverURL
	}
	resp, err := client.Do(req)
	if err != nil {
		return serverURL
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.Request == nil || resp.Request.URL == nil {
		return serverURL
	}
	if resolved := resp.Request.URL.String(); resolved != "" {
		return resolved
	}
	return serverURL
}

// measureDownload 并发 connections 条连接持续 GET url, 计数实际收到的
// 字节, 阻塞直到 ctx 结束。onProgress(窗口速率 Bytes/s) 每 250ms 触发
// 一次, 可为 nil。返回计量器供最终速率计算。
func measureDownload(ctx context.Context, client *http.Client, dlURL string, connections int, onProgress func(int64)) *throughputMeter {
	meter := newThroughputMeter()
	if connections < 1 {
		connections = 1
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	var wg sync.WaitGroup
	for i := 0; i < connections; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			buf := make([]byte, readBufferSize)
			consecutiveErr := 0
			for ctx.Err() == nil {
				req, err := http.NewRequestWithContext(ctx, http.MethodGet, dlURL, nil)
				if err != nil {
					return
				}
				resp, err := client.Do(req)
				if err != nil {
					if ctx.Err() != nil {
						return // 到时/取消, 正常结束
					}
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						return
					}
					sleepCtx(ctx, 200*time.Millisecond)
					continue
				}
				if resp.StatusCode != http.StatusOK {
					_ = resp.Body.Close()
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						return
					}
					sleepCtx(ctx, 200*time.Millisecond)
					continue
				}
				consecutiveErr = 0
				// 循环读取并计数, 直到 EOF 或到时中止。
				for {
					n, rerr := resp.Body.Read(buf)
					if n > 0 {
						meter.add(int64(n))
					}
					if rerr != nil {
						break
					}
				}
				_ = resp.Body.Close()
			}
		}()
	}

	go progressLoop(ctx, meter, onProgress)
	wg.Wait()
	meter.mark()
	return meter
}

// measureUpload 并发 connections 条连接持续 POST uploadURL, payload 为
// uploadChunkSize 大小的随机字节(复用同一 buffer), 在请求体读出处计数。
// 阻塞直到 ctx 结束。
func measureUpload(ctx context.Context, client *http.Client, uploadURL string, connections int, onProgress func(int64)) *throughputMeter {
	meter := newThroughputMeter()
	if connections < 1 {
		connections = 1
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	payload := make([]byte, uploadChunkSize)
	_, _ = rand.Read(payload) // 随机内容, 避免被中间层零压缩/零缓存

	var wg sync.WaitGroup
	for i := 0; i < connections; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			consecutiveErr := 0
			for ctx.Err() == nil {
				body := &countingReader{r: bytes.NewReader(payload), onRead: meter.add}
				req, err := http.NewRequestWithContext(ctx, http.MethodPost, uploadURL, body)
				if err != nil {
					return
				}
				req.ContentLength = int64(len(payload))
				req.Header.Set("Content-Type", "application/octet-stream")
				resp, err := client.Do(req)
				if err != nil {
					if ctx.Err() != nil {
						return
					}
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						return
					}
					sleepCtx(ctx, 200*time.Millisecond)
					continue
				}
				// 排干响应体以复用连接。
				_, _ = io.Copy(io.Discard, resp.Body)
				_ = resp.Body.Close()
				if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						return
					}
					sleepCtx(ctx, 200*time.Millisecond)
					continue
				}
				consecutiveErr = 0
			}
		}()
	}

	go progressLoop(ctx, meter, onProgress)
	wg.Wait()
	meter.mark()
	return meter
}

// progressLoop 周期性采样并在有回调时上报窗口速率。
func progressLoop(ctx context.Context, meter *throughputMeter, onProgress func(int64)) {
	ticker := time.NewTicker(progressInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			meter.mark()
			if onProgress != nil {
				onProgress(meter.windowRate(time.Second))
			}
		}
	}
}

func sleepCtx(ctx context.Context, d time.Duration) {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
	case <-t.C:
	}
}
