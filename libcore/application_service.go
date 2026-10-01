package libcore

import (
	"context"
	"encoding/json"
	"time"

	"github.com/sagernet/sing-box/common/networkquality"
	"github.com/sagernet/sing-box/common/stun"
	"github.com/sagernet/sing-box/daemon"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"github.com/xchacha20-poly1305/husi/libcore/v2/urltest"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/status"
)

// noPluginLauncher serves hosts that cannot spawn children. It rejects specs
// instead of quietly testing an outbound whose plugin never started.
type noPluginLauncher struct{}

var _ pluginpool.Launcher = noPluginLauncher{}

func (noPluginLauncher) RunWithPlugins(specs []*husiv1.PluginProcessSpec, run func() (int32, error)) (int32, error) {
	if len(specs) > 0 {
		return -1, E.New("this host cannot spawn plugin processes")
	}
	return run()
}

// applicationService implements the husi.v1 application surface.
type applicationService struct {
	husiv1.UnimplementedApplicationServiceServer
	platformInterface PlatformInterface
	pluginLauncher    pluginpool.Launcher
}

var (
	_ husiv1.ApplicationServiceServer = (*applicationService)(nil)
	_ coresvc.ServiceRegistrar        = (*applicationService)(nil)
)

// NewApplicationService builds the application surface a core host serves.
// platformInterface backs the throwaway instance a standalone URL test spins up
// (Android only; desktop hosts pass nil), and launcher spawns that test's plugin
// children — a host without one refuses requests that carry plugin specs.
//
// Everything it returns is unexported on purpose: Kotlin reaches this surface
// over gRPC, so nothing here belongs in the Android binding.
func NewApplicationService(platformInterface PlatformInterface, launcher pluginpool.Launcher) coresvc.ServiceRegistrar {
	if launcher == nil {
		launcher = noPluginLauncher{}
	}
	return &applicationService{
		platformInterface: platformInterface,
		pluginLauncher:    launcher,
	}
}

func (s *applicationService) RegisterServices(server *grpc.Server, healthServer *health.Server) {
	husiv1.RegisterApplicationServiceServer(server, s)
	coresvc.ServingStatus(healthServer, husiv1.ApplicationService_ServiceDesc.ServiceName)
}

func (s *applicationService) CheckConfig(ctx context.Context, req *husiv1.CheckConfigRequest) (*husiv1.CheckConfigResponse, error) {
	if err := CheckConfig(req.GetConfig()); err != nil {
		return nil, rpcError(err, codes.InvalidArgument)
	}
	return &husiv1.CheckConfigResponse{}, nil
}

func (s *applicationService) GenerateSchema(ctx context.Context, req *husiv1.GenerateSchemaRequest) (*husiv1.GenerateSchemaResponse, error) {
	var (
		schema string
		err    error
	)
	switch kind := req.GetKind(); kind {
	case husiv1.SchemaKind_SCHEMA_KIND_CONFIG:
		schema, err = GenerateConfigSchema()
	case husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND:
		schema, err = GenerateOutboundSchema()
	case husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE:
		schema, err = GenerateDNSRuleSchema()
	default:
		err = E.New("unknown schema kind: ", kind.String())
	}
	if err != nil {
		return nil, rpcError(err, codes.InvalidArgument)
	}
	return &husiv1.GenerateSchemaResponse{Schema: schema}, nil
}

func (s *applicationService) StandaloneURLTest(ctx context.Context, req *husiv1.StandaloneURLTestRequest) (*husiv1.StandaloneURLTestResponse, error) {
	options := urltest.FlagsFromProto(req.GetOptions())
	latency, err := s.pluginLauncher.RunWithPlugins(req.GetPlugins(), func() (int32, error) {
		return standaloneURLTest(
			req.GetConfig(),
			req.GetOutboundTag(),
			req.GetLink(),
			req.GetTimeoutMs(),
			options,
			s.platformInterface,
		)
	})
	if err != nil {
		return nil, urltest.WrapError(err)
	}
	return &husiv1.StandaloneURLTestResponse{LatencyMs: latency}, nil
}

// SpeedTestRun streams bandwidth-test events from a throwaway instance in this
// host process. Cancelling the stream cancels the session (the session ctx is
// bound to the stream ctx) and closes the instance.
func (s *applicationService) SpeedTestRun(req *husiv1.SpeedTestRunRequest, stream husiv1.ApplicationService_SpeedTestRunServer) error {
	// ⚠️ 崩溃修复(2026-09-29 SIGABRT in :bg): 会话 ctx 绝不能从
	// stream.Context() 派生 —— 那会继承宿主启动时的 service registry,
	// 其中已注册运行实例的 platformInterface; registerPlatformInterface 的
	// MustRegister 会把它覆盖成 forTest=true 的测速 wrapper, 运行实例
	// 之后从 ctx 解析 PlatformInterface 即拿到状态错误的 wrapper, 触发
	// fatal abort。baseContext 为每次会话构建全新的独立 registry。
	base := baseContext(s.platformInterface)
	ctx, cancel := context.WithCancel(base)
	defer cancel()

	// stream 取消(客户端断开/取消测速) → 取消独立 ctx。
	streamDone := make(chan struct{})
	defer close(streamDone)
	go func() {
		select {
		case <-stream.Context().Done():
			cancel()
		case <-streamDone:
		}
	}()

	events := make(chan map[string]any, 16)
	startDone := make(chan error, 1)

	go func() {
		_, startErr := startSpeedTestSession(
			ctx,
			s.platformInterface,
			speedTestRunRequest{
				Config:          req.GetConfig(),
				OutboundTag:     req.GetOutboundTag(),
				MaxConnections:  int(req.GetMaxConnections()),
				DownloadSeconds: req.GetDownloadSeconds(),
				UploadSeconds:   req.GetUploadSeconds(),
				MeasureUpload:   req.GetMeasureUpload(),
				ServerKeyword:   req.GetServerKeyword(),
			},
			func(payload map[string]any) {
				select {
				case events <- payload:
				case <-ctx.Done():
				}
			},
		)
		startDone <- startErr
	}()

	// 启动阶段: 失败立即报错; 成功后进入事件转发循环。
	select {
	case err := <-startDone:
		if err != nil {
			return rpcError(err, codes.FailedPrecondition)
		}
	case <-ctx.Done():
		return status.Error(codes.Canceled, "speedtest: stream cancelled")
	}

	for {
		select {
		case payload := <-events:
			data, err := json.Marshal(payload)
			if err != nil {
				continue
			}
			if err = stream.Send(&husiv1.SpeedTestEvent{Json: string(data)}); err != nil {
				return err
			}
			// done 是终态事件: 发完即收尾(实例由会话 goroutine 自行关闭)。
			if phase, _ := payload["phase"].(string); phase == "done" {
				return nil
			}
		case err := <-startDone:
			// 防御: 正常流程上面已消费; 若再次到达说明启动失败。
			if err != nil {
				return rpcError(err, codes.FailedPrecondition)
			}
			return status.Error(codes.Internal, "speedtest: session ended without done event")
		case <-ctx.Done():
			return status.Error(codes.Canceled, "speedtest: stream cancelled")
		}
	}
}

func (s *applicationService) GetCert(ctx context.Context, req *husiv1.GetCertRequest) (*husiv1.GetCertResponse, error) {
	pem, err := getCert(ctx, req.GetServer(), req.GetServerName(), req.GetMode(), req.GetSocksProxyUrl())
	if err != nil {
		return nil, rpcError(err, codes.InvalidArgument)
	}
	return &husiv1.GetCertResponse{Pem: pem}, nil
}

func (s *applicationService) StandaloneSTUNTest(
	req *husiv1.StandaloneSTUNTestRequest,
	stream husiv1.ApplicationService_StandaloneSTUNTestServer,
) error {
	result, err := stun.Run(stun.Options{
		Server:  req.GetServer(),
		Context: stream.Context(),
		OnProgress: func(progress stun.Progress) {
			_ = stream.Send(daemon.NewSTUNTestProgress(progress))
		},
	})
	if err != nil {
		return streamError(stream.Context(), stream.Send(&daemon.STUNTestProgress{
			IsFinal: true,
			Error:   err.Error(),
		}))
	}
	return streamError(stream.Context(), stream.Send(daemon.NewSTUNTestResult(result)))
}

func (s *applicationService) StandaloneNetworkQualityTest(
	req *husiv1.StandaloneNetworkQualityTestRequest,
	stream husiv1.ApplicationService_StandaloneNetworkQualityTestServer,
) error {
	client := networkquality.NewHTTPClient(nil)
	defer client.CloseIdleConnections()

	measurementClientFactory, err := networkquality.NewOptionalHTTP3Factory(nil, req.GetHttp3())
	if err != nil {
		return rpcError(err, codes.InvalidArgument)
	}

	result, err := networkquality.Run(networkquality.Options{
		ConfigURL:            req.GetConfigUrl(),
		HTTPClient:           client,
		NewMeasurementClient: measurementClientFactory,
		Serial:               req.GetSerial(),
		MaxRuntime:           time.Duration(req.GetMaxRuntimeSeconds()) * time.Second,
		Context:              stream.Context(),
		OnProgress: func(progress networkquality.Progress) {
			_ = stream.Send(daemon.NewNetworkQualityTestProgress(progress))
		},
	})
	if err != nil {
		return streamError(stream.Context(), stream.Send(&daemon.NetworkQualityTestProgress{
			IsFinal: true,
			Error:   err.Error(),
		}))
	}
	return streamError(stream.Context(), stream.Send(daemon.NewNetworkQualityTestResult(result)))
}

// rpcError keeps a status an implementation already chose, and labels a plain
// error with fallback.
func rpcError(err error, fallback codes.Code) error {
	if _, loaded := status.FromError(err); loaded {
		return err
	}
	return status.Error(fallback, err.Error())
}

// streamError reports a cancelled or expired stream as such: the client hanging
// up is not an internal failure.
func streamError(ctx context.Context, err error) error {
	if err == nil {
		return nil
	}
	if ctx.Err() != nil {
		return status.FromContextError(ctx.Err()).Err()
	}
	return rpcError(err, codes.Unknown)
}
