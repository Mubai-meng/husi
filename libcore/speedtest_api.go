// speedtest_api.go — 带宽测速的 JNI 出口与 gRPC(bridge) 共用会话机制。
//
// 新增文件, 不修改 husi 任何既有核心代码; 通过本文件向 Kotlin 暴露
// gomobile 符号: SpeedTestStart / SpeedTestStop / SpeedTestListener。
// bridge 侧 (husi.v1.ApplicationService.SpeedTestRun) 复用同一个
// startSpeedTestSession, 只是执行位置在 core host 进程 —— 那里有
// platformInterface, 测速出站 socket 在 VPN 运行时也能被正确 protect。
//
// 设计要点(务必保持):
//   - 与延迟测试(urltest/ping)完全解耦: 独立会话管理、独立实例、独立
//     生命周期, 不复用 urltest 包的任何状态;
//   - 每个测速会话创建一个一次性 sing-box 实例(与 standaloneURLTest 同
//     模式), 绝不在正在运行的实例上切换出站 —— 安卓 sing-box 频繁并发
//     切换出站极易 panic 崩溃; 测速全程不触碰运行实例;
//   - 测量流量 100% 经过被测节点: 配置被改写为"回环 mixed 入站 +
//     route.final=被测出站", 全部测量请求走该入站代理,
//     不存在系统直连连接(见 speedtest 包注释);
//   - platformInterface 为 nil 时也能运行(桌面/无 VPN 场景, 与
//     baseContext(nil) 的容忍行为一致); 安卓 UI 进程直接调用时无法
//     protect(保护 fd 在 :bg 进程), VPN 运行中的测速必须走 bridge。
package libcore

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/speedtest"
)

// SpeedTestListener gomobile 回调接口, 由 Kotlin 实现。
// 事件为 UTF-8 JSON 文本, 形如:
//
//	{"phase":"server","rate_bps":0,"message":"..."}        阶段通知
//	{"phase":"download","rate_bps":123456}                 下载实时速率(最近 1s 窗口)
//	{"phase":"upload","rate_bps":123456}                   上传实时速率(最近 1s 窗口)
//	{"phase":"log","message":"speedtest diag: ..."}         诊断日志(仅 JNI 路径:
//	                                                        UI 进程没有文件日志器,
//	                                                        由 Kotlin 侧转写进日志页)
//	{"phase":"done","download_bps":...,"upload_bps":...,   最终结果
//	 "server_id":"...","server_name":"...","error":"",
//	 "failure_kind":"connect","request_errors":3}
//
// 回调可能来自 Go 内部 goroutine; gomobile 保证跨线程派发, Kotlin 侧
// 请自行切回主线程更新 UI, 且实现必须可重入。
type SpeedTestListener interface {
	OnEvent(event string)
}

// speedTestParams SpeedTestStart 的 paramsJSON 反序列化目标。
type speedTestParams struct {
	OutboundTag     string `json:"outbound_tag"`
	MaxConnections  int    `json:"max_connections"`
	DownloadSeconds int32  `json:"download_seconds"`
	UploadSeconds   int32  `json:"upload_seconds"`
	MeasureUpload   bool   `json:"measure_upload"`
	ServerKeyword   string `json:"server_keyword"`
	// EmitDiagnosticLogs 与 speedTestRunRequest.EmitDiagnosticLogs 同义。
	EmitDiagnosticLogs bool `json:"emit_diagnostic_logs"`
}

// speedTestRunRequest 会话启动参数, gomobile 入口与 bridge 入口共用。
type speedTestRunRequest struct {
	Config          string
	OutboundTag     string
	MaxConnections  int
	DownloadSeconds int32
	UploadSeconds   int32
	MeasureUpload   bool
	ServerKeyword   string
	// EmitDiagnosticLogs 把诊断日志同时作为 "log" 事件发给调用方。
	// 只有 JNI 路径需要: UI 进程从未 InitCore, 没有文件日志器,
	// log.Info 只会落到 logcat, 日志页搜不到(审查 P1-4)。
	// bridge 路径的 log.Info 已经写进同一份日志文件, 再发一遍是重复。
	EmitDiagnosticLogs bool
}

// speedTestMaxSessions 同时会话上限。每个会话是一个完整 sing-box 实例,
// 资源开销大; 上层(Kotlin 任务队列)应自行控制并发, 这里只是硬性兜底。
const speedTestMaxSessions = 4

var (
	speedTestSessionSeq atomic.Int32
	speedTestSessions   sync.Map // int32 -> *speedTestSession
	// speedTestSessionCount 原子并发会话计数。审查 P2-5: 之前用
	// sync.Map.Range 现数一遍, 两个会话同时启动时会一起通过检查。
	speedTestSessionCount atomic.Int32
)

type speedTestSession struct {
	id     int32
	cancel context.CancelFunc
	done   chan struct{}
}

// SpeedTestStart 启动一次带宽测速会话(gomobile/JNI 入口)。
//
//	config      节点配置 JSON(husi 侧 buildConfig(profile, forTest=true) 的产物);
//	            会被改写为测速专用配置(见 speedtest.InjectSpeedtestLoopback),
//	            原始配置不受影响。
//	paramsJSON  参数 JSON; 传 "" 使用全部默认值。
//	platformInterface 可为 nil(桌面/无 VPN); 安卓 UI 进程没有保护 fd,
//	            VPN 运行时的测速必须经 bridge(SpeedTestRun) 到 :bg 进程执行。
//	listener    事件回调, 不可为 nil。
//
// 返回会话句柄, 供 SpeedTestStop 取消; 会话结束后句柄自动失效。
// 函数立即返回, 测量在后台 goroutine 中进行。
func SpeedTestStart(config, paramsJSON string, platformInterface PlatformInterface, listener SpeedTestListener) (int32, error) {
	if listener == nil {
		return 0, E.New("speedtest: listener is required")
	}
	if config == "" {
		return 0, E.New("speedtest: empty config")
	}

	var params speedTestParams
	if paramsJSON != "" {
		if err := json.Unmarshal([]byte(paramsJSON), &params); err != nil {
			return 0, E.Cause(err, "parse params")
		}
	}

	return startSpeedTestSession(
		baseContext(platformInterface),
		platformInterface,
		speedTestRunRequest{
			Config:          config,
			OutboundTag:     params.OutboundTag,
			MaxConnections:  params.MaxConnections,
			DownloadSeconds: params.DownloadSeconds,
			UploadSeconds:   params.UploadSeconds,
			MeasureUpload:   params.MeasureUpload,
			ServerKeyword:   params.ServerKeyword,
			// JNI 路径的诊断日志改走事件, 由 Kotlin 写进日志页
			// (由调用方在 paramsJSON 里显式开启)。
			EmitDiagnosticLogs: params.EmitDiagnosticLogs,
		},
		func(payload map[string]any) {
			emitSpeedTestEvent(listener, payload)
		},
	)
}

// startSpeedTestSession 会话启动的共用实现(encrypt: gomobile + bridge)。
// 事件经 emit 回调(payload 与 SpeedTestListener JSON 同形); emit 必须可重入。
func startSpeedTestSession(
	parentCtx context.Context,
	platformInterface PlatformInterface,
	req speedTestRunRequest,
	emit func(map[string]any),
) (int32, error) {
	if req.Config == "" {
		return 0, E.New("speedtest: empty config")
	}

	// 会话数兜底(原子占位: 先加再判, 避免并发启动一起通过检查)。
	if speedTestSessionCount.Add(1) > speedTestMaxSessions {
		speedTestSessionCount.Add(-1)
		return 0, E.New("speedtest: too many running sessions")
	}
	// quotaOwned=true 表示配额还挂在本次调用上; 启动成功即交给会话
	// goroutine 在收尾时归还。
	quotaOwned := true
	defer func() {
		if quotaOwned {
			speedTestSessionCount.Add(-1)
		}
	}()

	// 内核版本标记: 用于远程分辨设备上跑的是哪一版测速内核
	// (v4 = 自研计量 + 入站随机认证 + 端口占用重试 + 发现缓存按节点隔离)。
	if platformInterface != nil {
		log.Info("speedtest: session start (kernel v4: protect available, auto_detect_interface on)")
	} else {
		log.Info("speedtest: session start (kernel v4: direct mode, no protect, auto_detect_interface off)")
	}

	ctx := parentCtx
	if platformInterface != nil {
		registerPlatformInterface(ctx, platformInterface, true)
	}
	ctx, cancel := context.WithCancel(ctx)

	// ---- 一次性实例启动: 端口 TOCTOU 重试(审查 P2-6) ----
	//
	// PickLoopbackPort 是 bind→close→再交给 sing-box bind: 上一个会话正在
	// 关闭时(批量里 done 事件发出后实例还要关一会儿)下一个会话可能抢到同一
	// 个端口。撞了就换端口重试, 而不是让整个节点判失败。
	const maxPortAttempts = 3
	var (
		port     int
		cred     speedtest.LoopbackCredential
		instance *box.Box
		started  bool
	)
	defer func() {
		// 启动失败时 ctx 由本函数回收; 成功时所有权交给会话 goroutine。
		if !started {
			cancel()
		}
	}()
	for attempt := 1; ; attempt++ {
		var err error
		port, err = speedtest.PickLoopbackPort()
		if err != nil {
			return 0, E.Cause(err, "pick loopback port")
		}
		// protect 可用性决定 auto_detect_interface 注入与否:
		//   - VPN 运行(bridge, platformInterface != nil): 注入, 防 TUN 双重代理;
		//   - 直连(UI JNI, platformInterface == nil): 不注入, 避免 Android
		//     netlink 监视器路径导致 box.New 失败(详见 InjectSpeedtestLoopback)。
		// 同时注入一次性随机凭据(审查 P2-7), 测量流量用带认证的 socks5 URL。
		testConfig, loopbackCred, injectErr := speedtest.InjectSpeedtestLoopback(
			req.Config, req.OutboundTag, port, platformInterface != nil,
		)
		if injectErr != nil {
			return 0, injectErr
		}
		options, parseErr := parseConfig(ctx, testConfig)
		if parseErr != nil {
			return 0, E.Cause(parseErr, "parse config")
		}
		instance, err = box.New(box.Options{
			Options: options,
			Context: ctx,
		})
		if err == nil {
			err = instance.Start()
		}
		if err == nil {
			cred = loopbackCred
			started = true
			break
		}
		if instance != nil {
			// 启动失败的实例要收掉, 但不能 cancel 共享 ctx（重试还要用它）。
			closeFailedInstance(instance)
			instance = nil
		}
		if attempt >= maxPortAttempts || !isPortInUseError(err) {
			return 0, E.Cause(err, "start instance")
		}
		log.Warn("speedtest: loopback port ", port, " already in use, retry ",
			attempt, "/", maxPortAttempts)
	}

	session := &speedTestSession{
		id:     speedTestSessionSeq.Add(1),
		cancel: cancel,
		done:   make(chan struct{}),
	}
	speedTestSessions.Store(session.id, session)
	// 配额所有权移交给会话 goroutine。
	quotaOwned = false

	go func() {
		defer catchPanic("speedtest.session", func(panicErr error) {
			emit(map[string]any{
				"phase": "done",
				"error": panicErr.Error(),
			})
		})
		// 收尾顺序(审查 P2-5): 先释放配额再关实例 —— closeBoxTimeout
		// 可能耗到 FatalStopTimeout 级别, 之前配额一直占着, 批量里
		// 500ms 的冷却完全盖不住, 会误报 "too many running sessions"。
		releaseQuotaOnce := sync.OnceFunc(func() {
			speedTestSessions.Delete(session.id)
			speedTestSessionCount.Add(-1)
		})
		defer func() {
			releaseQuotaOnce()
			// 实例销毁带超时兜底, 防止卡死(复用 ping.go 的关闭模式)。
			closeBoxTimeout(instance, cancel)
			close(session.done)
		}()

		result, runErr := speedtest.Run(ctx, speedtest.Options{
			// 带认证的回环 socks5 地址(审查 P2-7)。
			ProxyURL: cred.ProxyURL(port),
			// 服务器列表缓存按节点隔离(审查 P2-4): 列表是按发现请求的出口
			// IP 排的, 而发现请求本身走被测节点。
			ServerCacheKey:  req.OutboundTag,
			MaxConnections:  req.MaxConnections,
			DownloadSeconds: req.DownloadSeconds,
			UploadSeconds:   req.UploadSeconds,
			MeasureUpload:   req.MeasureUpload,
			ServerKeyword:   req.ServerKeyword,
			// 诊断日志进 husi 日志页(日志页可搜 "speedtest"):
			// 每阶段输出 总字节/耗时/速率, 速率 = 总字节/耗时 可直接核算。
			LogFunc: func(line string) {
				log.Info("speedtest: ", line)
				// JNI 路径(UI 进程从未 InitCore, 没有文件日志器)把诊断
				// 日志额外作为事件送回 Kotlin, 由它写进日志页(审查 P1-4)。
				if req.EmitDiagnosticLogs {
					emit(map[string]any{"phase": "log", "message": line})
				}
			},
		}, func(e speedtest.Event) {
			emit(map[string]any{
				"phase":    e.Phase,
				"rate_bps": e.RateBps,
				"message":  e.Message,
			})
		})

		if runErr != nil && result.Error == "" {
			result.Error = runErr.Error()
		}
		emit(map[string]any{
			"phase":        "done",
			"download_bps": result.DownloadBps,
			"upload_bps":   result.UploadBps,
			"server_id":    result.ServerID,
			"server_name":  result.ServerName,
			"error":        result.Error,
			// 失败类型: 稳定枚举, Kotlin 侧据此决定是否自动剔除节点
			// (审查 P1-6: 超时不能等同于"节点不可用")。
			"failure_kind":   result.FailureKind,
			"request_errors": result.RequestErrors,
			// 连接减员(审查 P2-8): active_connections < max_connections 说明
			// 部分并发连接连续失败提前退出, 结果偏低, 上层应给出提示。
			"active_connections": result.ActiveConnections,
			"worker_dropouts":    result.WorkerDropouts,
			// 诊断字段(自研内核): 各阶段实收/实发总字节与耗时,
			// 速率 = 总字节/耗时 可直接核算, 无任何单位换算环节。
			"total_download_bytes": result.TotalDownloadBytes,
			"download_elapsed_ms":  result.DownloadElapsedMs,
			"total_upload_bytes":   result.TotalUploadBytes,
			"upload_elapsed_ms":    result.UploadElapsedMs,
		})
		// done 已发: 立刻把配额还掉, 让批量里的下一个节点能马上启动,
		// 不必等实例真正关闭。
		releaseQuotaOnce()
	}()

	return session.id, nil
}

// SpeedTestStop 取消会话(幂等)。已测得的部分速率会通过 done 事件上报。
// 不存在的句柄(已完成/已失效)直接返回 nil。
func SpeedTestStop(handle int32) error {
	v, ok := speedTestSessions.Load(handle)
	if !ok {
		return nil
	}
	session := v.(*speedTestSession)
	session.cancel()
	// 等待会话彻底收尾, 保证调用方 Stop 之后可以立即重绑端口。
	select {
	case <-session.done:
	case <-time.After(35 * time.Second):
		return E.New("speedtest: session stop timeout")
	}
	return nil
}

// closeFailedInstance 带超时兜底关闭一个启动失败的实例。
// 与 closeBoxTimeout 的区别: 这里**不能** cancel ctx —— 端口被占用时要
// 换端口重试, 共享 ctx 必须还活着。
func closeFailedInstance(instance *box.Box) {
	done := make(chan struct{})
	go func() {
		defer catchPanic("speedtest.boxClose", func(error) {})
		defer close(done)
		_ = instance.Close()
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
	}
}

// isPortInUseError 判断实例启动失败是否由回环端口被占用引起(P2-6 重试条件)。
func isPortInUseError(err error) bool {
	if err == nil {
		return false
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "address already in use") ||
		strings.Contains(msg, "eaddrinuse")
}

func emitSpeedTestEvent(listener SpeedTestListener, payload map[string]any) {
	data, err := json.Marshal(payload)
	if err != nil {
		return
	}
	listener.OnEvent(string(data))
}
