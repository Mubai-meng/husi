package libcore

import (
	"fmt"
	"net"
	"testing"
	"time"

	"github.com/sagernet/sing-box"

	"github.com/xchacha20-poly1305/husi/libcore/v2/speedtest"
)

// TestSpeedtestLoopbackListen 二分定位"socks connect 127.0.0.1:PORT refused":
// 验证 InjectSpeedtestLoopback + box.New/Start 之后, 回环 mixed 入站是否真的
// 在监听。若这里 refused, 问题在实例启动链路; 若可连, 问题在 speedtest-go。
func TestSpeedtestLoopbackListen(t *testing.T) {
	configJSON := `{"outbounds":[{"type":"direct","tag":"target"}]}`
	port, err := speedtest.PickLoopbackPort()
	if err != nil {
		t.Fatalf("pick port: %v", err)
	}
	testConfig, err := speedtest.InjectSpeedtestLoopback(configJSON, "target", port)
	if err != nil {
		t.Fatalf("inject: %v", err)
	}
	t.Logf("injected config: %s", testConfig)

	ctx := baseContext(nil)
	options, err := parseConfig(ctx, testConfig)
	if err != nil {
		t.Fatalf("parse config: %v", err)
	}
	instance, err := box.New(box.Options{
		Options: options,
		Context: ctx,
	})
	if err != nil {
		t.Fatalf("create instance: %v", err)
	}
	if err = instance.Start(); err != nil {
		t.Fatalf("start instance: %v", err)
	}
	defer closeBoxTimeout(instance, func() {})

	// Start 成功后立即尝试回环连接 —— 对应 speedtest-go 的第一跳。
	for i := 0; i < 5; i++ {
		conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), time.Second)
		if err == nil {
			_ = conn.Close()
			t.Logf("loopback %d reachable on attempt %d", port, i+1)
			return
		}
		t.Logf("attempt %d: %v", i+1, err)
		time.Sleep(200 * time.Millisecond)
	}
	t.Fatalf("loopback %d refused after start", port)
}
