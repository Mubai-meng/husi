// Package speedtest 实现独立于延迟测试的带宽(吞吐)测速。
//
// 服务器发现使用 github.com/showwin/speedtest-go(其计量层经离线仲裁
// 测试证实不可靠, 已弃用, 见 measure.go 注释); 吞吐测量完全自研:
// 字节自己数、时间自己记, 速率 = 字节增量 ÷ 时长, 单位恒为 Bytes/s。
//
// 所有测量流量强制经过调用方提供的本地代理(即 sing-box mixed 入站,
// 仅监听 127.0.0.1), 本包自身绝不创建任何对外直连 socket;
// speedtest-go 内部的 ICMP/TCP ping 路径(会绕过代理直连)从不被调用。
//
// 带宽测速与 husi 既有延迟测试(urltest/ping)是两套完全独立的任务:
// 不共享实例、不共享状态、不共享调度。
//
// 安全边界(必须遵守):
//   - 测速实例与正在运行的服务实例完全隔离, 绝不在运行实例上
//     切换出站 —— 安卓 sing-box 频繁并发切换出站极易 panic 崩溃;
//   - 本包只负责"经 socks5/http 代理跑测速请求", 实例的创建
//     与销毁由调用方(libcore 包)完成。
package speedtest

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"time"

	speedtestgo "github.com/showwin/speedtest-go/speedtest"
)

const SpeedtestInboundTag = "__husi_speedtest_in"

// Options 一次带宽测速的参数。
type Options struct {
	// ProxyURL 本地 sing-box mixed 入站地址, 如 "socks5://127.0.0.1:PORT"。
	// 唯一的网络出口, 空则拒绝运行。
	ProxyURL string
	// MaxConnections 单方向并发连接数, <1 时按 1。
	MaxConnections int
	// DownloadSeconds 下载阶段时长上限, <=0 时按 10s。到时即停,
	// 已累计的速率仍然有效(部分结果)。
	DownloadSeconds int32
	// UploadSeconds 上传阶段时长上限, <=0 时按 10s。
	UploadSeconds int32
	// MeasureUpload 是否测上传。
	MeasureUpload bool
	// ServerKeyword speedtest.net 服务器模糊筛选, 空=按地理位置距离。
	ServerKeyword string
	// LogFunc 诊断日志回调(nil 静默)。libcore 侧传 log.Info, 测试传 t.Logf。
	LogFunc func(string)
}

// Event 过程事件(进度/阶段通知)。
type Event struct {
	Phase   string `json:"phase"`             // "server" | "download" | "upload" | "done"
	RateBps int64  `json:"rate_bps"`          // 窗口速率(最近 1s 平均), Bytes/s
	Message string `json:"message,omitempty"` // 阶段说明/服务器名/错误细节
}

// Result 最终测速结果。速率单位 Bytes/s(自研内核, 字节与计时全部自持,
// 不存在单位换算环节); 0 表示该方向未测或失败。
type Result struct {
	DownloadBps int64  `json:"download_bps"`
	UploadBps   int64  `json:"upload_bps"`
	ServerID    string `json:"server_id,omitempty"`
	ServerName  string `json:"server_name,omitempty"`
	Error       string `json:"error,omitempty"`

	// ---- 诊断字段(用户报告"结果不准确"后加入, 便于核对速率 = 总字节/耗时) ----
	TotalDownloadBytes int64 `json:"total_download_bytes,omitempty"`
	DownloadElapsedMs  int64 `json:"download_elapsed_ms,omitempty"`
	TotalUploadBytes   int64 `json:"total_upload_bytes,omitempty"`
	UploadElapsedMs    int64 `json:"upload_elapsed_ms,omitempty"`
}

// EventFunc 过程事件回调, 可能来自多个内部 goroutine, 调用方需自行保证
// 回调实现的可重入性(在 libcore 侧统一串行化为单条 JSON 事件)。
type EventFunc func(Event)

// defaultPhaseSeconds 未显式配置时单阶段默认时长。
const defaultPhaseSeconds = 10

// Run 执行一次完整带宽测速。ctx 取消会中止测量并返回当前已测得的
// 部分速率(不算错误)。onEvent 可为 nil。
func Run(ctx context.Context, opts Options, onEvent EventFunc) (Result, error) {
	var result Result
	if opts.ProxyURL == "" {
		return result, errors.New("speedtest: empty proxy url")
	}
	if opts.DownloadSeconds <= 0 {
		opts.DownloadSeconds = defaultPhaseSeconds
	}
	if opts.UploadSeconds <= 0 {
		opts.UploadSeconds = defaultPhaseSeconds
	}
	if onEvent == nil {
		onEvent = func(Event) {}
	}
	diag := func(line string) {
		if opts.LogFunc != nil {
			opts.LogFunc(line)
		}
	}
	connections := max(opts.MaxConnections, 1)

	// ---- 服务器发现(仅此一处使用 speedtest-go) ----
	//
	// WithDoer(&http.Client{}): 防止库把自身 Transport 挂到
	// http.DefaultClient 上造成全局污染; 发现请求始终经由
	// UserConfig.Proxy 指定的代理发起, 不会直连。
	client := speedtestgo.New(
		speedtestgo.WithDoer(&http.Client{}),
		speedtestgo.WithUserConfig(&speedtestgo.UserConfig{
			Proxy:          opts.ProxyURL,
			MaxConnections: connections,
			Keyword:        opts.ServerKeyword,
		}),
	)

	// 用户地理位置用于按距离挑选服务器; 走节点访问, 失败不致命。
	if _, err := client.FetchUserInfoContext(ctx); err != nil {
		onEvent(Event{Phase: "server", Message: "fetch user info: " + err.Error()})
	}

	servers, err := client.FetchServerListContext(ctx)
	if err != nil {
		return result, fmt.Errorf("fetch server list: %w", err)
	}
	if len(servers) == 0 {
		return result, errors.New("speedtest: no server available")
	}
	server := servers[0]
	result.ServerID = server.ID
	result.ServerName = server.Sponsor + " (" + server.Name + ")"
	onEvent(Event{Phase: "server", Message: result.ServerName})

	// ---- 自研测量内核 ----
	//
	// 测量 client 与发现 client 完全独立: http.Transport 原生支持
	// socks5:// 代理 URL(内部 SOCKS5 CONNECT), 出口仍是同一个回环
	// mixed 入站; DisableCompression 关闭透明 gzip(防止计数被压缩
	// 层干扰), ForceAttemptHTTP2 关闭多路复用(保证 N 条真实并发
	// TCP 连接, 与 Karing PC 行为一致)。
	proxyURL, err := url.Parse(opts.ProxyURL)
	if err != nil {
		return result, fmt.Errorf("parse proxy url: %w", err)
	}
	measureClient := &http.Client{
		Transport: &http.Transport{
			Proxy:                 http.ProxyURL(proxyURL),
			DisableCompression:    true,
			ForceAttemptHTTP2:     false,
			MaxIdleConns:          connections,
			MaxIdleConnsPerHost:   connections,
			IdleConnTimeout:       time.Minute,
			TLSHandshakeTimeout:   10 * time.Second,
			ResponseHeaderTimeout: 30 * time.Second,
		},
	}

	// 下载阶段: N 连接持续 GET 最大档位测试文件, 到时/取消即停,
	// 已收字节照常计数。
	dlURL := downloadURLFor(server.URL)
	if dlURL == "" {
		return result, fmt.Errorf("speedtest: bad server url %q", server.URL)
	}
	diag(fmt.Sprintf("speedtest: download phase start, server=%q url=%s conns=%d duration=%ds",
		result.ServerName, dlURL, connections, opts.DownloadSeconds))
	dlCtx, dlCancel := context.WithTimeout(ctx, time.Duration(opts.DownloadSeconds)*time.Second)
	dlMeter := measureDownload(dlCtx, measureClient, dlURL, connections, func(rate int64) {
		onEvent(Event{Phase: "download", RateBps: rate})
	})
	dlCancel()
	dlBps, dlTotal, dlElapsed := dlMeter.phaseRate(measureWarmup)
	result.DownloadBps = dlBps
	result.TotalDownloadBytes = dlTotal
	result.DownloadElapsedMs = dlElapsed.Milliseconds()
	// 诊断日志(进 husi 日志页): 速率直接可由 总字节/耗时 核算, 一目了然。
	diag(fmt.Sprintf(
		"speedtest diag: download total=%d bytes elapsed=%.2fs rate=%d B/s (%.2f Mbps)",
		dlTotal, dlElapsed.Seconds(), dlBps, float64(dlBps)*8/1e6,
	))
	if result.DownloadBps <= 0 {
		result.Error = "download measured 0 bytes"
		return result, nil // 部分失败也算完成, 结果如实上报
	}

	if opts.MeasureUpload {
		upURL := resolveUploadURL(ctx, measureClient, server.URL)
		diag(fmt.Sprintf("speedtest: upload phase start, url=%s conns=%d duration=%ds",
			upURL, connections, opts.UploadSeconds))
		upCtx, upCancel := context.WithTimeout(ctx, time.Duration(opts.UploadSeconds)*time.Second)
		upMeter := measureUpload(upCtx, measureClient, upURL, connections, func(rate int64) {
			onEvent(Event{Phase: "upload", RateBps: rate})
		})
		upCancel()
		upBps, upTotal, upElapsed := upMeter.phaseRate(measureWarmup)
		result.UploadBps = upBps
		result.TotalUploadBytes = upTotal
		result.UploadElapsedMs = upElapsed.Milliseconds()
		diag(fmt.Sprintf(
			"speedtest diag: upload total=%d bytes elapsed=%.2fs rate=%d B/s (%.2f Mbps)",
			upTotal, upElapsed.Seconds(), upBps, float64(upBps)*8/1e6,
		))
	}
	return result, nil
}

// InjectSpeedtestLoopback 把任意 husi 节点配置改造成"测速专用"配置:
//  1. 丢弃原有全部 inbounds(避免端口冲突/意外启动 TUN), 注入一个仅监听
//     127.0.0.1:listenPort 的 mixed 入站;
//  2. 丢弃原 route 规则并设置 route.final=outboundTag —— 防止测速流量
//     被用户分流规则(如 speedtest.net 直连规则)劫持, 保证 100% 走被测节点;
//  3. outboundTag 为空时自动挑选第一个可测出站(跳过 urltest/selector/
//     dns/direct/block, 与 Karing cluster 的跳过名单一致)。
//
// 操作原始 JSON(而非 option 结构体)以尽量降低 sing-box 版本演进带来的
// 字段漂移风险。
func InjectSpeedtestLoopback(configJSON, outboundTag string, listenPort int) (string, error) {
	var root map[string]any
	if err := json.Unmarshal([]byte(configJSON), &root); err != nil {
		return "", fmt.Errorf("speedtest: parse config: %w", err)
	}
	if root == nil {
		root = make(map[string]any)
	}

	tag := outboundTag
	if tag == "" {
		tag = firstTestableOutbound(root)
	}
	if tag == "" {
		return "", errors.New("speedtest: no testable outbound in config")
	}

	// 仅保留回环 mixed 入站。
	root["inbounds"] = []any{map[string]any{
		"type":        "mixed",
		"tag":         SpeedtestInboundTag,
		"listen":      "127.0.0.1",
		"listen_port": listenPort,
	}}
	// 路由收敛: 丢弃规则, 全部走被测出站。
	// ⚠️ auto_detect_interface 必须无条件强制为 true(Android 上即 protect
	// 绕过本机 VPN TUN 的开关): 2026-10-01 日志实测, 缺失时测速实例的
	// 出站连接被正在运行的 VPN TUN 全量劫持, 变成"测速实例 → 主实例
	// trojan → 节点"的双重代理, 且主实例把测速流量全部计入被测节点的
	// 流量统计(卡片 ↑↓ 暴涨)。buildConfig 产出的原配置虽带此字段, 但为
	// 防上游字段漂移, 这里不再依赖原值, 直接强制注入。
	newRoute := map[string]any{"final": tag, "auto_detect_interface": true}
	if oldRoute, ok := root["route"].(map[string]any); ok {
		for _, key := range []string{"default_mark", "default_domain_resolver"} {
			if v, exists := oldRoute[key]; exists {
				newRoute[key] = v
			}
		}
	}
	root["route"] = newRoute

	out, err := json.Marshal(root)
	if err != nil {
		return "", fmt.Errorf("speedtest: re-marshal config: %w", err)
	}
	return string(out), nil
}

// firstTestableOutbound 从 outbounds/endpoints 中找第一个可测出站 tag。
func firstTestableOutbound(root map[string]any) string {
	skip := map[string]bool{
		"urltest": true, "selector": true, "dns": true, "direct": true, "block": true,
	}
	for _, key := range []string{"outbounds", "endpoints"} {
		list, _ := root[key].([]any)
		for _, o := range list {
			m, ok := o.(map[string]any)
			if !ok {
				continue
			}
			if t, _ := m["type"].(string); skip[t] {
				continue
			}
			if tag, _ := m["tag"].(string); tag != "" {
				return tag
			}
		}
	}
	// 兜底: 第一个带 tag 的条目。
	for _, key := range []string{"outbounds", "endpoints"} {
		list, _ := root[key].([]any)
		for _, o := range list {
			if m, ok := o.(map[string]any); ok {
				if tag, _ := m["tag"].(string); tag != "" {
					return tag
				}
			}
		}
	}
	return ""
}

// PickLoopbackPort 预分配一个本地回环空闲端口(实例启动前先占位再释放,
// 竞争窗口极小, 失败可重试)。
func PickLoopbackPort() (int, error) {
	l, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		return 0, err
	}
	port := l.Addr().(*net.TCPAddr).Port
	_ = l.Close()
	return port, nil
}
