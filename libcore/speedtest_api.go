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
//     route.final=被测出站", speedtest-go 所有请求走该入站代理,
//     不存在系统直连连接(见 speedtest 包注释);
//   - platformInterface 为 nil 时也能运行(桌面/无 VPN 场景, 与
//     baseContext(nil) 的容忍行为一致); 安卓 UI 进程直接调用时无法
//     protect(保护 fd 在 :bg 进程), VPN 运行中的测速必须走 bridge。
package libcore

import (
	"context"
	"encoding/json"
	"strconv"
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
//	{"phase":"download","rate_bps":123456}                 下载实时速率(EWMA)
//	{"phase":"upload","rate_bps":123456}                   上传实时速率(EWMA)
//	{"phase":"done","download_bps":...,"upload_bps":...,   最终结果
//	 "server_id":"...","server_name":"...","error":""}
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
}

// speedTestMaxSessions 同时会话上限。每个会话是一个完整 sing-box 实例,
// 资源开销大; 上层(Kotlin 任务队列)应自行控制并发, 这里只是硬性兜底。
const speedTestMaxSessions = 4

var (
	speedTestSessionSeq atomic.Int32
	speedTestSessions   sync.Map // int32 -> *speedTestSession
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

	// 会话数兜底。
	count := 0
	speedTestSessions.Range(func(_, _ any) bool {
		count++
		return true
	})
	if count >= speedTestMaxSessions {
		return 0, E.New("speedtest: too many running sessions")
	}

	port, err := speedtest.PickLoopbackPort()
	if err != nil {
		return 0, E.Cause(err, "pick loopback port")
	}
	// protect 可用性决定 auto_detect_interface 注入与否:
	//   - VPN 运行(bridge, platformInterface != nil): 注入, 防 TUN 双重代理;
	//   - 直连(UI JNI, platformInterface == nil): 不注入, 避免 Android
	//     netlink 监视器路径导致 box.New 失败(详见 InjectSpeedtestLoopback)。
	testConfig, err := speedtest.InjectSpeedtestLoopback(req.Config, req.OutboundTag, port, platformInterface != nil)
	if err != nil {
		return 0, err
	}
	// 内核版本标记: 用于远程分辨设备上跑的是哪一版测速内核
	// (v3 = 自研计量 + auto_detect_interface 按 protect 可用性条件注入)。
	if platformInterface != nil {
		log.Info("speedtest: session start (kernel v3: protect available, auto_detect_interface on)")
	} else {
		log.Info("speedtest: session start (kernel v3: direct mode, no protect, auto_detect_interface off)")
	}

	ctx := parentCtx
	if platformInterface != nil {
		registerPlatformInterface(ctx, platformInterface, true)
	}
	ctx, cancel := context.WithCancel(ctx)

	options, err := parseConfig(ctx, testConfig)
	if err != nil {
		cancel()
		return 0, E.Cause(err, "parse config")
	}
	instance, err := box.New(box.Options{
		Options: options,
		Context: ctx,
	})
	if err != nil {
		cancel()
		return 0, E.Cause(err, "create instance")
	}
	if err = instance.Start(); err != nil {
		cancel()
		return 0, E.Cause(err, "start instance")
	}

	session := &speedTestSession{
		id:     speedTestSessionSeq.Add(1),
		cancel: cancel,
		done:   make(chan struct{}),
	}
	speedTestSessions.Store(session.id, session)

	go func() {
		defer catchPanic("speedtest.session", func(panicErr error) {
			emit(map[string]any{
				"phase": "done",
				"error": panicErr.Error(),
			})
		})
		defer func() {
			// 实例销毁带超时兜底, 防止卡死(复用 ping.go 的关闭模式)。
			closeBoxTimeout(instance, cancel)
			speedTestSessions.Delete(session.id)
			close(session.done)
		}()

		result, runErr := speedtest.Run(ctx, speedtest.Options{
			ProxyURL:        "socks5://127.0.0.1:" + strconv.Itoa(port),
			MaxConnections:  req.MaxConnections,
			DownloadSeconds: req.DownloadSeconds,
			UploadSeconds:   req.UploadSeconds,
			MeasureUpload:   req.MeasureUpload,
			ServerKeyword:   req.ServerKeyword,
			// 诊断日志进 husi 日志页(日志页可搜 "speedtest"):
			// 每阶段输出 总字节/耗时/速率, 速率 = 总字节/耗时 可直接核算。
			LogFunc: func(line string) {
				log.Info("speedtest: ", line)
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
			// 诊断字段(自研内核): 各阶段实收/实发总字节与耗时,
			// 速率 = 总字节/耗时 可直接核算, 无任何单位换算环节。
			"total_download_bytes": result.TotalDownloadBytes,
			"download_elapsed_ms":  result.DownloadElapsedMs,
			"total_upload_bytes":   result.TotalUploadBytes,
			"upload_elapsed_ms":    result.UploadElapsedMs,
		})
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

func emitSpeedTestEvent(listener SpeedTestListener, payload map[string]any) {
	data, err := json.Marshal(payload)
	if err != nil {
		return
	}
	listener.OnEvent(string(data))
}
