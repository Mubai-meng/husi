package libcore

import (
	"fmt"
	"testing"
	"time"

	"github.com/sagernet/sing-box"

	"github.com/xchacha20-poly1305/husi/libcore/v2/speedtest"
)

// TestSpeedtestE2E 桌面端到端: 回环注入 + 独立实例 + speedtest-go 完整链路。
// 出站用本机真实代理(socks5 127.0.0.1:2080), 模拟"经被测出站"的完整路径。
// 通过 = 链路本身正确; 失败 = 复现真机错误并拿到更完整的诊断信息。
func TestSpeedtestE2E(t *testing.T) {
	configJSON := `{"outbounds":[{"type":"socks","tag":"target","server":"127.0.0.1","server_port":2080}]}`
	port, err := speedtest.PickLoopbackPort()
	if err != nil {
		t.Fatalf("pick port: %v", err)
	}
	testConfig, err := speedtest.InjectSpeedtestLoopback(configJSON, "target", port)
	if err != nil {
		t.Fatalf("inject: %v", err)
	}
	t.Logf("injected: %s", testConfig)

	ctx := baseContext(nil)
	options, err := parseConfig(ctx, testConfig)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	instance, err := box.New(box.Options{Options: options, Context: ctx})
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	if err = instance.Start(); err != nil {
		t.Fatalf("start: %v", err)
	}
	defer closeBoxTimeout(instance, func() {})

	done := make(chan struct{})
	var runErr error
	var result speedtest.Result
	go func() {
		defer close(done)
		result, runErr = speedtest.Run(ctx, speedtest.Options{
			ProxyURL:        fmt.Sprintf("socks5://127.0.0.1:%d", port),
			MaxConnections:  2,
			DownloadSeconds: 5,
			UploadSeconds:   5,
			LogFunc:         func(s string) { t.Log("DIAG " + s) },
		}, func(e speedtest.Event) {
			t.Logf("event: phase=%s rate=%d msg=%s", e.Phase, e.RateBps, e.Message)
		})
	}()

	select {
	case <-done:
	case <-time.After(90 * time.Second):
		t.Fatal("speedtest timeout 90s")
	}
	if runErr != nil {
		t.Fatalf("run error: %v (result=%+v)", runErr, result)
	}
	t.Logf("result: %+v", result)
	if result.Error != "" {
		t.Fatalf("result error: %s", result.Error)
	}
}
