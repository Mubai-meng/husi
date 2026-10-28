// measure.go — 自研吞吐计量内核。
//
// 背景: speedtest-go v1.8.3 的计量层(DLSpeed/ULSpeed, EWMA/Welford 管线)
// 经离线仲裁测试证实不可靠 —— 本地回环满速场景(实测 ~440MB/s)其原始
// 速率值与真实字节速率/比特速率的比值均不接近 1(0.014 / 0.002), 偏差
// 约 70 倍且无法用任何单位解释。因此本包已完全移除 speedtest-go
// (服务器发现也自研, 见 servers.go), 测量内核完全自持:
//
//   - 下载字节在响应体读取处计数(应用层实际消费的净载荷);
//   - 上传字节在 socket 层计数(DialContext 返回的 net.Conn.Write),
//     即真正写进内核的字节 —— 2026-10-26 审查实测: 之前在请求体 reader
//     处计数会把 TLS/socket 缓冲的在途量也算进去, 回环下已高估 5.1%,
//     高 RTT 链路更严重(审查 P2-1);
//   - 时间自己记: mark 采样点(时刻+累计字节), 单调时钟;
//   - 速率 = 字节增量 ÷ 时长, 单位恒为 Bytes/s, 不存在单位换算环节;
//   - 最终速率跳过预热期(TCP 慢启动/连接建立), 预热时长按阶段长度自适应
//     (审查 P0-1: 固定 1s 预热在 4s 预算下只剩 3s 稳态窗口);
//   - 稳态窗口/进度窗口的边界字节数用采样点线性插值求出, 而不是取"最近的
//     采样点"(审查 P2-3: 实际窗口长度在 [1s,1.25s] 浮动导致数字跳)。
//
// 所有测量流量仍 100% 经过调用方提供的代理 client(回环 sing-box 入站)。
package speedtest

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"math/rand"
	"net"
	"net/http"
	"net/url"
	"path"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const (
	// downloadFileName speedtest.net 服务器提供的最大档位测试文件,
	// 单请求约 16MB; 请求期间被到时中止时已收字节照常计数。
	downloadFileName = "random4000x4000.jpg"
	// uploadChunkSize 单次上传请求的 payload 大小(8MB): 大块减少
	// 请求往返占比, 高吞吐链路尤其明显。
	uploadChunkSize = 8 << 20
	// measureWarmup 最终速率跳过的预热时长上限(TCP 慢启动/连接建立)。
	// 实际使用值由 warmupFor 按阶段长度自适应, 不会超过此上限。
	measureWarmup = time.Second
	// minWarmup 自适应预热的时长下限: 阶段极短时也要丢掉最初一段,
	// 否则把握手/慢启动算进稳态。
	minWarmup = 300 * time.Millisecond
	// progressInterval 进度采样与事件上报间隔。
	progressInterval = 250 * time.Millisecond
	// readBufferSize 下载读取缓冲(256KB): 高吞吐(如 500Mbps≈60MB/s)
	// 时 64KB 缓冲意味着每连接每秒上千次 Read 系统调用, 256KB 显著
	// 降低用户态/内核态切换开销。
	readBufferSize = 256 << 10
	// maxConsecutiveErrors 单 worker 连续请求失败上限(超过即退出该 worker)。
	maxConsecutiveErrors = 5
)

// warmupFor 按阶段时长自适应预热时长: min(1s, 阶段时长/5), 不低于
// minWarmup。4s 阶段丢 0.8s、3s 阶段丢 0.6s, 而不是固定 1s(审查 P0-1)。
func warmupFor(phase time.Duration) time.Duration {
	if phase <= 0 {
		return minWarmup
	}
	warmup := phase / 5
	if warmup > measureWarmup {
		warmup = measureWarmup
	}
	if warmup < minWarmup {
		warmup = minWarmup
	}
	return warmup
}

// measureConfig 单个测量阶段的网络配置。
//
// proxyURL 是唯一的网络出口(回环 sing-box mixed 入站); 为空只用于离线
// 单元测试, 生产路径绝不允许为空。
type measureConfig struct {
	proxyURL    string
	connections int
}

func (c measureConfig) conns() int {
	if c.connections < 1 {
		return 1
	}
	return c.connections
}

// writeCountingConn 在 socket 层统计写出的字节数。
type writeCountingConn struct {
	net.Conn
	onWrite func(int64)
}

func (c *writeCountingConn) Write(p []byte) (int, error) {
	n, err := c.Conn.Write(p)
	if n > 0 {
		c.onWrite(int64(n))
	}
	return n, err
}

// newMeasureClient 构造一个测量专用 client。
//
//   - Proxy 显式设置(代理 URL 解析失败时直接报错, 绝不静默退化为直连);
//   - DisableCompression 关闭透明 gzip(防止计数被压缩层干扰);
//   - ForceAttemptHTTP2 关闭多路复用(保证 N 条真实并发 TCP 连接,
//     与 Karing PC 行为一致);
//   - onSocketWrite 非空时包装 DialContext 返回的 net.Conn, 用于上传阶段
//     的 socket 层计数。代价是会把 TLS 记录头(~0.2%)与握手字节算进去,
//     但比在请求体 reader 层计数(含缓冲在途量, 实测 +5.1%)精确得多。
func newMeasureClient(cfg measureConfig, onSocketWrite func(int64)) (*http.Client, error) {
	var proxyFunc func(*http.Request) (*url.URL, error)
	if cfg.proxyURL != "" {
		proxyURL, err := url.Parse(cfg.proxyURL)
		if err != nil {
			return nil, fmt.Errorf("parse proxy url: %w", err)
		}
		if proxyURL.Host == "" {
			return nil, fmt.Errorf("parse proxy url: missing host in %q", cfg.proxyURL)
		}
		proxyFunc = http.ProxyURL(proxyURL)
	}

	dialer := &net.Dialer{
		Timeout:   15 * time.Second,
		KeepAlive: 30 * time.Second,
	}
	dialContext := dialer.DialContext
	if onSocketWrite != nil {
		baseDial := dialContext
		dialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
			conn, err := baseDial(ctx, network, addr)
			if err != nil {
				return nil, err
			}
			return &writeCountingConn{Conn: conn, onWrite: onSocketWrite}, nil
		}
	}

	return &http.Client{
		Transport: &http.Transport{
			Proxy:                 proxyFunc,
			DialContext:           dialContext,
			DisableCompression:    true,
			ForceAttemptHTTP2:     false,
			MaxIdleConns:          cfg.conns(),
			MaxIdleConnsPerHost:   cfg.conns(),
			IdleConnTimeout:       time.Minute,
			TLSHandshakeTimeout:   10 * time.Second,
			ResponseHeaderTimeout: 30 * time.Second,
		},
	}, nil
}

// throughputMeter 累计字节数 + 采样点, 支持窗口速率与去预热最终速率。
type throughputMeter struct {
	total atomic.Int64
	start time.Time

	// activeWorkers 阶段结束时仍在跑的 worker 数(审查 P2-8: 静默减员时
	// 上层可据此判断"连接不足, 结果偏低")。
	activeWorkers atomic.Int32
	// requestErrors 请求失败累计次数(含非 200 响应)。
	requestErrors atomic.Int64
	// workerDropouts 因连续失败提前退出的 worker 数(审查 P2-8)。
	// 单 worker 连续 5 次失败就退出, 其余继续 —— 最终可能只剩 1~2 条连接
	// 测得一个偏低的值却仍"成功"上报, 上层完全看不出连接已减员。
	workerDropouts atomic.Int32

	mu    sync.Mutex
	marks []meterMark // 按时间递增

	errMu   sync.Mutex
	lastErr string
}

type meterMark struct {
	t time.Time
	n int64 // 该时刻的累计字节数
}

func newThroughputMeter() *throughputMeter {
	m := &throughputMeter{start: time.Now()}
	// 起始采样点: 让 valueAt 在 [start, start+warmup] 区间也能插值,
	// 而不是因为首个采样点晚于窗口边界而退化为端点值。
	m.marks = append(m.marks, meterMark{t: m.start, n: 0})
	return m
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

// recordError 记录一次请求失败(审查 P1-3: 之前所有失败都被折叠成
// "download measured 0 bytes", 真实原因全部丢失)。
func (m *throughputMeter) recordError(err error) {
	if err == nil {
		return
	}
	m.requestErrors.Add(1)
	m.errMu.Lock()
	m.lastErr = err.Error()
	m.errMu.Unlock()
}

// LastError 最后一次请求失败的原因; 无失败返回 ""。
func (m *throughputMeter) LastError() string {
	m.errMu.Lock()
	defer m.errMu.Unlock()
	return m.lastErr
}

// RequestErrors 请求失败累计次数。
func (m *throughputMeter) RequestErrors() int64 { return m.requestErrors.Load() }

// ActiveWorkers 阶段结束时仍在运行的 worker 数(wg.Wait 之后恒为 0,
// 仅用于阶段中途诊断)。
func (m *throughputMeter) ActiveWorkers() int { return int(m.activeWorkers.Load()) }

// WorkerDropouts 因连续请求失败提前退出的 worker 数。
func (m *throughputMeter) WorkerDropouts() int { return int(m.workerDropouts.Load()) }

// dropWorker 记一次 worker 提前退出(连续失败超限)。
func (m *throughputMeter) dropWorker() { m.workerDropouts.Add(1) }

// valueAt 求 t 时刻的累计字节数(采样点线性插值), 至少返回已有端点值。
func (m *throughputMeter) valueAt(t time.Time) int64 {
	m.mu.Lock()
	defer m.mu.Unlock()
	n := len(m.marks)
	if n == 0 {
		return 0
	}
	if !t.After(m.marks[0].t) {
		return m.marks[0].n
	}
	if !t.Before(m.marks[n-1].t) {
		return m.marks[n-1].n
	}
	for i := 1; i < n; i++ {
		cur := m.marks[i]
		if cur.t.Before(t) {
			continue
		}
		prev := m.marks[i-1]
		span := cur.t.Sub(prev.t).Seconds()
		if span <= 0 {
			return cur.n
		}
		frac := t.Sub(prev.t).Seconds() / span
		if frac < 0 {
			frac = 0
		} else if frac > 1 {
			frac = 1
		}
		return prev.n + int64(float64(cur.n-prev.n)*frac)
	}
	return m.marks[n-1].n
}

// windowRate 最近 window 时长的平均速率(Bytes/s), 边界字节数用插值求,
// 采样不足 window 时退化为自启动以来的平均速率。
func (m *throughputMeter) windowRate(window time.Duration) int64 {
	now := time.Now()
	total := m.total.Load()
	baseT := now.Add(-window)
	if baseT.Before(m.start) {
		baseT = m.start
	}
	dt := now.Sub(baseT).Seconds()
	if dt <= 0 {
		return 0
	}
	return int64(float64(total-m.valueAt(baseT)) / dt)
}

// phaseRate 测量结束后的最终速率(Bytes/s): 跳过前 warmup 时长, 用之后
// 的字节增量 ÷ 时长。
//
// 边界字节数用插值求出(审查 P2-2/P2-3), 因此不再需要"剩余窗口不足
// 500ms 就退化为含预热的全程平均"这种兜底 —— 那个兜底对短阶段等于把
// 慢启动算进结果, 是失真而非保守。稳态窗口过短时由调用方在诊断日志里
// 标注 low_confidence。
func (m *throughputMeter) phaseRate(warmup time.Duration) (bps int64, total int64, elapsed time.Duration) {
	now := time.Now()
	total = m.total.Load()
	elapsed = now.Sub(m.start)
	if elapsed <= 0 || total <= 0 {
		return 0, total, elapsed
	}
	steadyStart := m.start.Add(warmup)
	if !steadyStart.Before(now) {
		steadyStart = m.start
	}
	base := m.valueAt(steadyStart)
	dt := now.Sub(steadyStart).Seconds()
	if dt <= 0 {
		return 0, total, elapsed
	}
	delta := total - base
	if delta < 0 {
		delta = total
	}
	return int64(float64(delta) / dt), total, elapsed
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

// recoverWorker 兜住 worker 内的 panic: 项目开启过
// debug.SetPanicOnFault(true), native fault 会转成 panic, 没有 recover
// 时会直接带走整个会话(审查 §四.4)。
func recoverWorker(meter *throughputMeter, phase string) {
	if r := recover(); r != nil {
		meter.recordError(fmt.Errorf("%s worker panic: %v", phase, r))
	}
}

// measureDownload 并发 connections 条连接持续 GET url, 计数实际收到的
// 字节, 阻塞直到 ctx 结束。onProgress(窗口速率 Bytes/s) 每 250ms 触发
// 一次, 可为 nil。
func measureDownload(ctx context.Context, cfg measureConfig, dlURL string, onProgress func(int64)) (*throughputMeter, error) {
	client, err := newMeasureClient(cfg, nil)
	if err != nil {
		return nil, err
	}
	// 一次性实例: 结束即关掉空闲连接, 不留到 IdleConnTimeout(审查 P2-9)。
	defer client.CloseIdleConnections()

	meter := newThroughputMeter()
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	var wg sync.WaitGroup
	for i := 0; i < cfg.conns(); i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			meter.activeWorkers.Add(1)
			defer meter.activeWorkers.Add(-1)
			defer recoverWorker(meter, "download")

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
					meter.recordError(err)
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						meter.dropWorker()
						return
					}
					sleepCtx(ctx, 200*time.Millisecond)
					continue
				}
				if resp.StatusCode != http.StatusOK {
					_ = resp.Body.Close()
					meter.recordError(fmt.Errorf("http status %d", resp.StatusCode))
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						meter.dropWorker()
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
						if rerr != io.EOF && ctx.Err() == nil {
							meter.recordError(rerr)
						}
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
	return meter, nil
}

// measureUpload 并发 connections 条连接持续 POST uploadURL, payload 为
// uploadChunkSize 大小的随机字节(复用同一 buffer)。
//
// 字节数在 socket 层统计(net.Conn.Write), 即真正写出去的量 ——
// 审查 P2-1: 之前在请求体 reader 处计数会把缓冲在途量算进去, 回环实测
// 就高估 5.1%, 真实高 RTT 链路更严重。阻塞直到 ctx 结束。
func measureUpload(ctx context.Context, cfg measureConfig, uploadURL string, onProgress func(int64)) (*throughputMeter, error) {
	meter := newThroughputMeter()
	client, err := newMeasureClient(cfg, meter.add)
	if err != nil {
		return nil, err
	}
	defer client.CloseIdleConnections()

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	payload := make([]byte, uploadChunkSize)
	_, _ = rand.Read(payload) // 随机内容, 避免被中间层零压缩/零缓存

	var wg sync.WaitGroup
	for i := 0; i < cfg.conns(); i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			meter.activeWorkers.Add(1)
			defer meter.activeWorkers.Add(-1)
			defer recoverWorker(meter, "upload")

			consecutiveErr := 0
			for ctx.Err() == nil {
				body := bytes.NewReader(payload)
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
					meter.recordError(err)
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						meter.dropWorker()
						return
					}
					sleepCtx(ctx, 200*time.Millisecond)
					continue
				}
				// 排干响应体以复用连接。
				_, _ = io.Copy(io.Discard, resp.Body)
				_ = resp.Body.Close()
				if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
					meter.recordError(fmt.Errorf("http status %d", resp.StatusCode))
					if consecutiveErr++; consecutiveErr >= maxConsecutiveErrors {
						meter.dropWorker()
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
	return meter, nil
}

// progressLoop 周期性采样并在有回调时上报窗口速率。
func progressLoop(ctx context.Context, meter *throughputMeter, onProgress func(int64)) {
	// 与 worker 同样的兜底(审查 §四.4): 这里每 250ms 跑一次, 一旦
	// debug.SetPanicOnFault(true) 下的 native fault 落到本 goroutine,
	// 没有 recover 会直接带走整个进程。
	defer recoverWorker(meter, "progress")

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

// classifyPhaseFailure 把阶段结束状态映射成稳定的失败类型, 供上层决定
// 是否自动剔除节点、给用户什么提示(审查 P1-3/P1-6)。
//
// phaseErr 必须是阶段 ctx 在被 cancel 之前记录的错误值, 否则永远得到
// context.Canceled。
func classifyPhaseFailure(phaseErr error, meter *throughputMeter, total int64) (kind string, detail string) {
	if total > 0 {
		return FailureNone, ""
	}
	last := ""
	if meter != nil {
		last = meter.LastError()
	}
	// 主动取消(用户停止/切下一个节点)不算节点故障。
	if errors.Is(phaseErr, context.Canceled) {
		if last != "" {
			return FailureCancelled, last
		}
		return FailureCancelled, "cancelled before any data"
	}
	switch {
	case last == "":
		if errors.Is(phaseErr, context.DeadlineExceeded) {
			return FailureTimeout, "no data before phase timeout"
		}
		return FailureZeroBytes, "no data received"
	case isHTTPStatusError(last):
		return FailureHTTPStatus, last
	case errors.Is(phaseErr, context.DeadlineExceeded) && !isConnectError(last):
		return FailureTimeout, last
	case isConnectError(last):
		return FailureConnect, last
	default:
		// 未知错误: 归为"测不出数据", 不算确定性故障 —— 避免误删节点。
		return FailureZeroBytes, last
	}
}

func isHTTPStatusError(s string) bool {
	const prefix = "http status "
	return len(s) >= len(prefix) && s[:len(prefix)] == prefix
}

// isConnectError 识别"节点确定不可用"类错误(连接被拒/不可达/DNS/TLS)。
// 只有这一类才允许自动剔除节点(审查 P1-6: 超时不该等于不可用)。
func isConnectError(s string) bool {
	patterns := [...]string{
		"connection refused",
		"connection reset",
		"no route to host",
		"network is unreachable",
		"host is down",
		"tls:",
		"x509:",
		"no such host",
		"server misbehaving",
		"proxyconnect",
		"socks connect",
	}
	lower := strings.ToLower(s)
	for _, p := range patterns {
		if strings.Contains(lower, p) {
			return true
		}
	}
	return false
}
