// Package speedtest 实现独立于延迟测试的带宽(吞吐)测速。
//
// 服务器发现与吞吐测量全部自研(2026-10-26 起不再依赖 speedtest-go,
// 原因见 servers.go 头部注释): 字节自己数、时间自己记, 速率 =
// 字节增量 ÷ 时长, 单位恒为 Bytes/s。
//
// 所有测量流量强制经过调用方提供的本地代理(即 sing-box mixed 入站,
// 仅监听 127.0.0.1), 本包自身绝不创建任何对外直连 socket, 也不做任何
// 形式的主机探测(旧实现里 speedtest-go 的 ICMP/TCP ping 路径是
// 潜在的直连泄漏点, 已随依赖一并移除)。
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
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"time"
)

const SpeedtestInboundTag = "__husi_speedtest_in"

// 失败类型(Result.FailureKind)。稳定的枚举值, 供 Kotlin 侧决定
// 自动剔除策略与提示文案 —— 不再靠匹配错误字符串(审查 P1-3/P1-6)。
const (
	FailureNone       = ""             // 未失败
	FailureDiscovery  = "discovery"    // 拉服务器列表失败
	FailureConfig     = "config"       // 代理地址/配置问题
	FailureConnect    = "connect"      // 连接被拒/不可达/DNS/TLS —— 确定性故障
	FailureTimeout    = "timeout"      // 阶段计时到期仍无数据
	FailureHTTPStatus = "http_status"  // 服务器返回非 2xx
	FailureZeroBytes  = "zero_bytes"   // 有连接但一个字节都没收到(原因未知)
	FailureCancelled  = "cancelled"    // 被主动取消(用户停止/切节点)
)

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
	// ServerCacheKey 服务器列表缓存的隔离键, 由调用方传被测节点的唯一
	// 标识(husi 侧传根出站 tag)。审查 P2-4: api/js/servers 返回的列表是
	// 按**请求方出口 IP** 的地理距离排好序的, 而发现请求本身就走被测
	// 节点 —— 同一份列表只对同一个出口位置有意义, 只用 keyword 做键会让
	// 换国家/地区的节点复用上一个节点那份排序(选到远端服务器)。
	ServerCacheKey string
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
	// FailureKind 失败类型(见 Failure* 常量); 空 = 未失败。
	FailureKind string `json:"failure_kind,omitempty"`
	// RequestErrors 请求失败累计次数(诊断: 明显偏大说明链路不稳)。
	RequestErrors int64 `json:"request_errors,omitempty"`
	// WorkerDropouts 因连续失败提前退出的并发连接数(审查 P2-8)。
	WorkerDropouts int `json:"worker_dropouts,omitempty"`
	// ActiveConnections 阶段结束时的有效连接数 = 配置并发 - WorkerDropouts;
	// 小于配置值时上层应提示"连接不足, 结果可能偏低"。
	ActiveConnections int `json:"active_connections,omitempty"`

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
		result.FailureKind = FailureConfig
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
	measureCfg := measureConfig{proxyURL: opts.ProxyURL, connections: connections}

	// ---- 服务器发现 / 探测共用的轻量 client ----
	//
	// 与下载计量的 client 分开: 这里只需要少量并发, 且要在计量开始前
	// 就可用。代理 URL 解析失败属于配置问题, 直接失败(绝不静默直连)。
	diagClient, err := newMeasureClient(measureConfig{proxyURL: opts.ProxyURL, connections: 2}, nil)
	if err != nil {
		result.FailureKind = FailureConfig
		return result, fmt.Errorf("speedtest: %w", err)
	}
	defer diagClient.CloseIdleConnections()

	// 缓存键 = keyword + 节点隔离键(见 Options.ServerCacheKey)。
	serverCacheKey := opts.ServerKeyword + "|" + opts.ServerCacheKey
	servers, err := resolveServers(ctx, diagClient, opts.ServerKeyword, serverCacheKey, diag)
	if err != nil {
		result.FailureKind = FailureDiscovery
		return result, err
	}
	if len(servers) == 0 {
		result.FailureKind = FailureDiscovery
		return result, errors.New("speedtest: no server available")
	}

	server := pickServer(ctx, diagClient, servers, diag)
	result.ServerID = server.ID
	result.ServerName = server.displayName()
	onEvent(Event{Phase: "server", Message: result.ServerName})

	// ---- 下载阶段: N 连接持续 GET 最大档位测试文件, 到时/取消即停 ----
	dlURL := downloadURLFor(server.URL)
	if dlURL == "" {
		result.FailureKind = FailureConfig
		return result, fmt.Errorf("speedtest: bad server url %q", server.URL)
	}
	dlDuration := time.Duration(opts.DownloadSeconds) * time.Second
	dlWarmup := warmupFor(dlDuration)
	diag(fmt.Sprintf(
		"speedtest: download phase start, server=%q url=%s conns=%d duration=%ds warmup=%s",
		result.ServerName, dlURL, connections, opts.DownloadSeconds, dlWarmup,
	))

	dlCtx, dlCancel := context.WithTimeout(ctx, dlDuration)
	dlMeter, err := measureDownload(dlCtx, measureCfg, dlURL, func(rate int64) {
		onEvent(Event{Phase: "download", RateBps: rate})
	})
	dlPhaseErr := dlCtx.Err() // 必须在 cancel 之前取, 否则永远是 Canceled
	dlCancel()
	if err != nil {
		result.FailureKind = FailureConfig
		return result, fmt.Errorf("speedtest: download phase: %w", err)
	}

	dlBps, dlTotal, dlElapsed := dlMeter.phaseRate(dlWarmup)
	result.DownloadBps = dlBps
	result.TotalDownloadBytes = dlTotal
	result.DownloadElapsedMs = dlElapsed.Milliseconds()
	result.RequestErrors = dlMeter.RequestErrors()
	result.WorkerDropouts = dlMeter.WorkerDropouts()
	result.ActiveConnections = max(connections-dlMeter.WorkerDropouts(), 0)
	// 诊断日志(进 husi 日志页): 速率直接可由 总字节/耗时 核算, 一目了然。
	diag(fmt.Sprintf(
		"speedtest diag: download total=%d bytes elapsed=%.2fs warmup=%s rate=%d B/s (%.2f Mbps)"+
			" conns=%d(active=%d dropped=%d) errors=%d%s",
		dlTotal, dlElapsed.Seconds(), dlWarmup, dlBps, float64(dlBps)*8/1e6,
		connections, result.ActiveConnections, dlMeter.WorkerDropouts(), dlMeter.RequestErrors(),
		lowConfidenceNote(dlElapsed, dlWarmup),
	))
	if dlBps <= 0 {
		// ⚠️ 不能再无条件覆盖成 "download measured 0 bytes"(审查 P1-3):
		// 代理没监听 / 实例没起来 / 403 / DNS 失败 / 连接被拒 全被折叠成
		// 同一句话, runErr 分支永远走不到, 线上完全无法诊断。
		kind, detail := classifyPhaseFailure(dlPhaseErr, dlMeter, dlTotal)
		result.FailureKind = kind
		result.Error = "download failed (" + kind + "): " + detail
		return result, nil // 部分失败也算完成, 结果如实上报
	}

	if opts.MeasureUpload {
		upURL := resolveUploadURL(ctx, diagClient, server.URL)
		upDuration := time.Duration(opts.UploadSeconds) * time.Second
		upWarmup := warmupFor(upDuration)
		diag(fmt.Sprintf("speedtest: upload phase start, url=%s conns=%d duration=%ds warmup=%s",
			upURL, connections, opts.UploadSeconds, upWarmup))

		upCtx, upCancel := context.WithTimeout(ctx, upDuration)
		upMeter, upErr := measureUpload(upCtx, measureCfg, upURL, func(rate int64) {
			onEvent(Event{Phase: "upload", RateBps: rate})
		})
		upPhaseErr := upCtx.Err()
		upCancel()
		if upErr != nil {
			// 上传阶段起不来不影响已测得的下载结果。
			diag("speedtest: upload phase setup failed: " + upErr.Error())
		} else {
			upBps, upTotal, upElapsed := upMeter.phaseRate(upWarmup)
			result.UploadBps = upBps
			result.TotalUploadBytes = upTotal
			result.UploadElapsedMs = upElapsed.Milliseconds()
			result.RequestErrors += upMeter.RequestErrors()
			diag(fmt.Sprintf(
				"speedtest diag: upload total=%d bytes elapsed=%.2fs warmup=%s rate=%d B/s (%.2f Mbps)"+
					" conns=%d(active=%d dropped=%d) errors=%d%s",
				upTotal, upElapsed.Seconds(), upWarmup, upBps, float64(upBps)*8/1e6,
				connections, max(connections-upMeter.WorkerDropouts(), 0),
				upMeter.WorkerDropouts(), upMeter.RequestErrors(),
				lowConfidenceNote(upElapsed, upWarmup),
			))
			if upBps <= 0 {
				// 下载已成: 不把整个节点判死, 只记日志说明上传为何为空。
				kind, detail := classifyPhaseFailure(upPhaseErr, upMeter, upTotal)
				diag("speedtest: upload measured 0 bytes (" + kind + "): " + detail)
			}
		}
	}
	return result, nil
}

// lowConfidenceNote 稳态窗口过短时给出诊断标记(审查 P2-2: 短窗口的单点
// 速率对抖动极敏感, 不能当成可信结果)。
func lowConfidenceNote(elapsed, warmup time.Duration) string {
	if elapsed-warmup < 500*time.Millisecond {
		return " low_confidence=true(steady window < 500ms)"
	}
	return ""
}

// InjectSpeedtestLoopback 把任意 husi 节点配置改造成"测速专用"配置:
//  1. 丢弃原有全部 inbounds(避免端口冲突/意外启动 TUN), 注入一个仅监听
//     127.0.0.1:listenPort 的 mixed 入站, 并**强制随机用户名/密码认证**
//     (审查 P2-7: 无认证时, 测速窗口内本机任意 App 都能把这个回环入站
//     当代理用 —— 包括白嫖被测节点的流量);
//  2. 丢弃原 route 规则并设置 route.final=outboundTag —— 防止测速流量
//     被用户分流规则(如 speedtest.net 直连规则)劫持, 保证 100% 走被测节点;
//  3. protectAvailable=true 时注入 route.auto_detect_interface=true
//     (Android 上即 protect, 绕过正在运行的 VPN TUN 防双重代理);
//     false(直连模式, 无 VPN/无平台接口)时不注入 —— 无 TUN 可绕过,
//     而 Android 上缺平台接口时 sing-box 会退到 netlink 监视器路径,
//     新版 Android 对普通应用 netlink 收紧(sing-tun: "netlink socket in
//     Android is banned"), enforceInterfaceMonitor=true 会让 box.New
//     直接失败, 导致直连模式全部节点测速失败;
//  4. outboundTag 为空时自动挑选第一个可测出站(跳过 urltest/selector/
//     dns/direct/block, 与 Karing cluster 的跳过名单一致)。
//
// 操作原始 JSON(而非 option 结构体)以尽量降低 sing-box 版本演进带来的
// 字段漂移风险。
func InjectSpeedtestLoopback(configJSON, outboundTag string, listenPort int, protectAvailable bool) (string, LoopbackCredential, error) {
	var zero LoopbackCredential
	var root map[string]any
	if err := json.Unmarshal([]byte(configJSON), &root); err != nil {
		return "", zero, fmt.Errorf("speedtest: parse config: %w", err)
	}
	if root == nil {
		root = make(map[string]any)
	}

	tag := outboundTag
	if tag != "" && !hasOutboundTag(root, tag) {
		// 调用方给的 tag 在配置里不存在(自定义 config 型 profile 的
		// mainTag 不一定等于真实出站 tag): 回退到自动挑选 —— 否则
		// route.final 指向不存在的出站会让 box.New 直接失败,
		// 整个 profile 永远测不了。
		tag = ""
	}
	if tag == "" {
		tag = firstTestableOutbound(root)
	}
	if tag == "" {
		return "", zero, errors.New("speedtest: no testable outbound in config")
	}

	// 一次性随机凭据(审查 P2-7): 每个会话独立, 会话结束即失效。
	credential, err := newLoopbackCredential()
	if err != nil {
		return "", zero, fmt.Errorf("speedtest: generate credential: %w", err)
	}

	// 仅保留回环 mixed 入站(带认证)。
	root["inbounds"] = []any{map[string]any{
		"type":        "mixed",
		"tag":         SpeedtestInboundTag,
		"listen":      "127.0.0.1",
		"listen_port": listenPort,
		"users": []any{map[string]any{
			"username": credential.Username,
			"password": credential.Password,
		}},
	}}
	// 路由收敛: 丢弃规则, 全部走被测出站。
	// ⚠️ auto_detect_interface 仅在 protect 可用时注入(= 有 platformInterface
	// 的 bridge 路径, 即 VPN 运行中): 缺失时测速实例的出站连接会被正在运行
	// 的 VPN TUN 全量劫持, 变成"测速实例 → 主实例 trojan → 节点"的双重代理
	// (2026-10-01 日志实测)。反过来, 直连模式(无 VPN, platformInterface=nil)
	// 时绝不能注入: Android 上 sing-box 会退到 netlink 监视器路径且
	// enforceInterfaceMonitor=true, netlink 被系统禁用/无默认路由时
	// box.New 直接失败 → 全部节点测速失败(2026-10-04 直连模式全挂的根因)。
	newRoute := map[string]any{"final": tag}
	if protectAvailable {
		newRoute["auto_detect_interface"] = true
	}
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
		return "", zero, fmt.Errorf("speedtest: re-marshal config: %w", err)
	}
	return string(out), credential, nil
}

// LoopbackCredential 测速回环 mixed 入站的一次性随机凭据(审查 P2-7)。
type LoopbackCredential struct {
	Username string
	Password string
}

// ProxyURL 该凭据对应的 socks5 代理地址(测量流量唯一的出口)。
func (c LoopbackCredential) ProxyURL(port int) string {
	u := url.URL{
		Scheme: "socks5",
		User:   url.UserPassword(c.Username, c.Password),
		Host:   net.JoinHostPort("127.0.0.1", strconv.Itoa(port)),
	}
	return u.String()
}

// newLoopbackCredential 生成 8+8 字节的十六进制随机凭据 —— 纯 [0-9a-f],
// 不需要任何 URL/JSON 转义, 也不会与 sing-box 的解析规则冲突。
func newLoopbackCredential() (LoopbackCredential, error) {
	var buf [16]byte
	if _, err := rand.Read(buf[:]); err != nil {
		return LoopbackCredential{}, err
	}
	return LoopbackCredential{
		Username: hex.EncodeToString(buf[:8]),
		Password: hex.EncodeToString(buf[8:]),
	}, nil
}

// hasOutboundTag 判断 tag 是否真的存在于配置的 outbounds/endpoints 中。
func hasOutboundTag(root map[string]any, tag string) bool {
	if tag == "" {
		return false
	}
	for _, key := range []string{"outbounds", "endpoints"} {
		list, _ := root[key].([]any)
		for _, o := range list {
			if m, ok := o.(map[string]any); ok {
				if t, _ := m["tag"].(string); t == tag {
					return true
				}
			}
		}
	}
	return false
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
