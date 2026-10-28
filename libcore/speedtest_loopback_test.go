package libcore

import (
	"fmt"
	"io"
	"net"
	"testing"
	"time"

	"github.com/sagernet/sing-box"

	"github.com/xchacha20-poly1305/husi/libcore/v2/speedtest"
)

// TestSpeedtestLoopbackListen 二分定位"socks connect 127.0.0.1:PORT refused":
// 验证 InjectSpeedtestLoopback + box.New/Start 之后, 回环 mixed 入站是否真的
// 在监听。若这里 refused, 问题在实例启动链路; 若可连, 问题在测量阶段。
func TestSpeedtestLoopbackListen(t *testing.T) {
	configJSON := `{"outbounds":[{"type":"direct","tag":"target"}]}`
	port, err := speedtest.PickLoopbackPort()
	if err != nil {
		t.Fatalf("pick port: %v", err)
	}
	testConfig, cred, err := speedtest.InjectSpeedtestLoopback(configJSON, "target", port, false)
	if err != nil {
		t.Fatalf("inject: %v", err)
	}
	t.Logf("injected config: %s", testConfig)
	// 认证凭据必须一直带到测量阶段, 否则回环入站会拒绝连接(P2-7)。
	if cred.Username == "" || cred.Password == "" {
		t.Fatal("loopback credential is empty")
	}

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

	// Start 成功后立即尝试回环连接 —— 对应测量阶段的第一跳。
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

// TestSpeedtestLoopbackRejectsBadCredential 验证回环入站真的校验认证
// (审查 P2-7): 错误密码必须在 SOCKS5 用户名/密码子协商阶段被拒, 而不是
// 照常转发 —— 否则"随机凭据"只是装饰品, 测速窗口内本机任意 App 仍能
// 白嫖被测节点流量。
func TestSpeedtestLoopbackRejectsBadCredential(t *testing.T) {
	configJSON := `{"outbounds":[{"type":"direct","tag":"target"}]}`
	port, err := speedtest.PickLoopbackPort()
	if err != nil {
		t.Fatalf("pick port: %v", err)
	}
	testConfig, cred, err := speedtest.InjectSpeedtestLoopback(configJSON, "target", port, false)
	if err != nil {
		t.Fatalf("inject: %v", err)
	}
	ctx := baseContext(nil)
	options, err := parseConfig(ctx, testConfig)
	if err != nil {
		t.Fatalf("parse config: %v", err)
	}
	instance, err := box.New(box.Options{Options: options, Context: ctx})
	if err != nil {
		t.Fatalf("create instance: %v", err)
	}
	if err = instance.Start(); err != nil {
		t.Fatalf("start instance: %v", err)
	}
	defer closeBoxTimeout(instance, func() {})

	// 正确凭据: 子协商必须成功(status=0)。
	if status := socks5AuthStatus(t, port, cred.Username, cred.Password); status != 0 {
		t.Fatalf("correct credential rejected, socks5 auth status=%d", status)
	}
	// 错误密码: 必须被拒(非 0, 通常是 0x01)。
	if status := socks5AuthStatus(t, port, cred.Username, "bad-"+cred.Password); status == 0 {
		t.Fatal("wrong password accepted — loopback inbound is not enforcing auth")
	}
}

// socks5AuthStatus 做一次最小 SOCKS5 用户名/密码子协商(RFC 1929), 返回
// 服务端的认证结果字节(0 = 成功)。特意不用 http/socks 客户端: 只有手写
// 握手才能把"认证失败"和"目标不可达"区分开。
func socks5AuthStatus(t *testing.T, port int, username, password string) byte {
	t.Helper()
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 2*time.Second)
	if err != nil {
		t.Fatalf("dial loopback: %v", err)
	}
	defer func() { _ = conn.Close() }()
	// greeting: VER=5, NMETHODS=1, METHOD=0x02(username/password)
	if _, err = conn.Write([]byte{5, 1, 2}); err != nil {
		t.Fatalf("write greeting: %v", err)
	}
	reply := make([]byte, 2)
	if _, err = io.ReadFull(conn, reply); err != nil {
		t.Fatalf("read greeting reply: %v", err)
	}
	if reply[1] != 2 {
		t.Fatalf("server selected auth method %d, want 2 (username/password)", reply[1])
	}
	// RFC 1929: VER=1, ULEN, UNAME, PLEN, PASSWD
	req := []byte{1, byte(len(username))}
	req = append(req, username...)
	req = append(req, byte(len(password)))
	req = append(req, password...)
	if _, err = conn.Write(req); err != nil {
		t.Fatalf("write auth: %v", err)
	}
	status := make([]byte, 2)
	if _, err = io.ReadFull(conn, status); err != nil {
		t.Fatalf("read auth reply: %v", err)
	}
	return status[1]
}
