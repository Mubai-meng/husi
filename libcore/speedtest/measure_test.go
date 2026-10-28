package speedtest

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

// TestMeasureKernelOffline 离线验证自研测量内核(不经任何代理/外部网络):
// 本地 httptest 服务器满速推送/接收已知字节, 校验
//  1. 客户端计数字节 == 服务器实发/实收字节(计数无丢失);
//  2. 测得速率与真实字节速率同量级(比值 ~1, 不再有 speedtest-go
//     计量层 70 倍级别的偏差);
//  3. 单位语义: 速率确由 字节/时间 导出, 不存在换算环节。
func TestMeasureKernelOffline(t *testing.T) {
	payload := make([]byte, 1<<20) // 1 MiB
	for i := range payload {
		payload[i] = byte(i)
	}

	var serverSent atomic.Int64
	var serverRecv atomic.Int64
	mux := http.NewServeMux()
	// 下载端点: 模拟 random4000x4000.jpg, 满速推送 100MiB。
	mux.HandleFunc("/speedtest/random4000x4000.jpg", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Length", "104857600")
		flusher := w.(http.Flusher)
		for i := 0; i < 100; i++ {
			if _, err := w.Write(payload); err != nil {
				return
			}
			serverSent.Add(int64(len(payload)))
			flusher.Flush()
		}
	})
	// 上传端点: 读干并计数。
	mux.HandleFunc("/speedtest/upload.php", func(w http.ResponseWriter, r *http.Request) {
		n, _ := io.Copy(io.Discard, r.Body)
		serverRecv.Add(n)
		w.WriteHeader(http.StatusOK)
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	// ---- 下载 ----
	dlCtx, dlCancel := context.WithTimeout(context.Background(), 3*time.Second)
	dlMeter, err := measureDownload(dlCtx, measureConfig{connections: 3},
		srv.URL+"/speedtest/random4000x4000.jpg", nil)
	dlCancel()
	if err != nil {
		t.Fatalf("download: %v", err)
	}
	dlBps, dlTotal, dlElapsed := dlMeter.phaseRate(warmupFor(3 * time.Second))
	realDlBps := float64(serverSent.Load()) / 3.0 // 近似真实速率(测试固定 3s)

	t.Logf("DL: client_counted=%d server_sent=%d elapsed=%s rate=%d B/s real~=%.0f B/s ratio=%.3f",
		dlTotal, serverSent.Load(), dlElapsed, dlBps, realDlBps, float64(dlBps)/realDlBps)

	if dlTotal == 0 {
		t.Fatal("download: no bytes counted")
	}
	// 到时中止时服务器已写出但仍在内核缓冲的在途字节不会被客户端读到,
	// 客户端计数应略小于服务器实发; 容差 64MB(远超 6 连接的缓冲上界)。
	inFlight := serverSent.Load() - dlTotal
	if inFlight < 0 || inFlight > 64<<20 {
		t.Fatalf("download: byte count mismatch client=%d server=%d (in-flight=%d)", dlTotal, serverSent.Load(), inFlight)
	}
	if dlBps <= 0 {
		t.Fatal("download: rate <= 0")
	}
	ratio := float64(dlBps) / realDlBps
	if ratio < 0.5 || ratio > 2.0 {
		t.Fatalf("download: rate ratio %.3f out of sane range", ratio)
	}

	// ---- 上传 ----
	upCtx, upCancel := context.WithTimeout(context.Background(), 3*time.Second)
	upMeter, err := measureUpload(upCtx, measureConfig{connections: 3},
		srv.URL+"/speedtest/upload.php", nil)
	upCancel()
	if err != nil {
		t.Fatalf("upload: %v", err)
	}
	upBps, upTotal, upElapsed := upMeter.phaseRate(warmupFor(3 * time.Second))
	realUpBps := float64(serverRecv.Load()) / 3.0

	t.Logf("UL: client_counted=%d server_received=%d elapsed=%s rate=%d B/s real~=%.0f B/s ratio=%.3f",
		upTotal, serverRecv.Load(), upElapsed, upBps, realUpBps, float64(upBps)/realUpBps)

	if upTotal == 0 {
		t.Fatal("upload: no bytes counted")
	}
	// 上传: 字节数在 socket 层(net.Conn.Write)统计, 到时中止时已写出的
	// 可能略多于服务器读到的(在途字节, 内核 socket 缓冲 + 并发连接)。
	// ⚠️ 这里才是 P2-1 的证据: 旧实现在请求体 reader 层计数, 同样的回环
	// 场景下客户端计数比服务器实收高 5.1%; 改到 socket 层后应在 1% 以内。
	diff := upTotal - serverRecv.Load()
	diffPct := float64(diff) / float64(serverRecv.Load()) * 100
	t.Logf("UL byte diff=%d (%.3f%%)", diff, diffPct)
	if diff > 32<<20 || diff < -(32<<20) {
		t.Fatalf("upload: byte count mismatch client=%d server=%d (diff=%d)", upTotal, serverRecv.Load(), diff)
	}
	if diffPct > 1.0 || diffPct < -1.0 {
		t.Fatalf("upload: client/bytes deviation %.3f%% exceeds 1%% (socket-level counting regressed)", diffPct)
	}
	if upBps <= 0 {
		t.Fatal("upload: rate <= 0")
	}
	upRatio := float64(upBps) / realUpBps
	if upRatio < 0.5 || upRatio > 2.0 {
		t.Fatalf("upload: rate ratio %.3f out of sane range", upRatio)
	}

	t.Logf("VERDICT: PASS (byte counting exact, rate within sane range, unit = Bytes/s guaranteed)")
}

// TestDownloadURLFor 校验 speedtest.net 下载 URL 构造。
func TestDownloadURLFor(t *testing.T) {
	got := downloadURLFor("http://speedtest.example.com:8080/speedtest/upload.php")
	want := "http://speedtest.example.com:8080/speedtest/random4000x4000.jpg"
	if got != want {
		t.Fatalf("downloadURLFor = %q, want %q", got, want)
	}
}

// TestWarmupFor 校验自适应预热(审查 P0-1): 短阶段不该再固定丢 1s。
func TestWarmupFor(t *testing.T) {
	cases := []struct {
		phase time.Duration
		want  time.Duration
	}{
		{10 * time.Second, measureWarmup},            // 长阶段: 封顶 1s
		{6 * time.Second, measureWarmup},             // 6/5=1.2s → 封顶 1s
		{5 * time.Second, measureWarmup},             // 恰好 1s
		{4 * time.Second, 800 * time.Millisecond},    // 4/5
		{3 * time.Second, 600 * time.Millisecond},    // 3/5
		{1 * time.Second, minWarmup},                 // 0.2s → 下限 300ms
		{0, minWarmup},                               // 非法输入
	}
	for _, c := range cases {
		if got := warmupFor(c.phase); got != c.want {
			t.Fatalf("warmupFor(%s) = %s, want %s", c.phase, got, c.want)
		}
	}
}

// TestPhaseRateSkipsWarmup 校验最终速率确实是"去预热后的稳态窗口速率":
// 构造前 1s 低速 + 之后高速的采样序列, 去预热结果应接近高速段。
func TestPhaseRateSkipsWarmup(t *testing.T) {
	m := newThroughputMeter()
	m.start = time.Now().Add(-4 * time.Second)
	m.marks = nil
	// 前 1s: 10 MB/s; 后 3s: 100 MB/s(线性记录, 每 250ms 一点)。
	var n int64
	steps := []struct {
		at  time.Duration
		rate int64
	}{
		// 0~1s 段
		{0, 0}, {250 * time.Millisecond, 10 << 20 / 4}, {500 * time.Millisecond, 10 << 20 / 2},
		{750 * time.Millisecond, 10 << 20 * 3 / 4}, {1 * time.Second, 10 << 20},
	}
	for _, s := range steps {
		n = s.rate
		m.marks = append(m.marks, meterMark{t: m.start.Add(s.at), n: n})
	}
	// 1s 之后每 250ms 追加 25MB(= 100MB/s)。
	for i := 1; i <= 12; i++ {
		n += 25 << 20
		m.marks = append(m.marks, meterMark{t: m.start.Add(time.Second + time.Duration(i)*250*time.Millisecond), n: n})
	}
	m.total.Store(n)

	bps, total, elapsed := m.phaseRate(time.Second)
	if total != n {
		t.Fatalf("total = %d, want %d", total, n)
	}
	// 稳态窗口 [1s, 4s] = 3s, 期间字节 = 100MB/s * 3s。
	want := int64(100 << 20)
	if bps < want*95/100 || bps > want*105/100 {
		t.Fatalf("phaseRate = %d B/s, want ~%d B/s (elapsed=%s)", bps, want, elapsed)
	}
}

// TestClassifyPhaseFailure 校验失败分类: 超时不得被判成"节点不可用"
// (审查 P1-6), 主动取消不得算错误。
func TestClassifyPhaseFailure(t *testing.T) {
	mk := func(msg string) *throughputMeter {
		m := newThroughputMeter()
		if msg != "" {
			m.recordError(errors.New(msg))
		}
		return m
	}
	cases := []struct {
		name     string
		phaseErr error
		msg      string
		total    int64
		want     string
	}{
		{"有数据即成功", context.DeadlineExceeded, "connection refused", 1024, FailureNone},
		{"主动取消", context.Canceled, "", 0, FailureCancelled},
		{"无数据且到期", context.DeadlineExceeded, "", 0, FailureTimeout},
		{"连接被拒", context.DeadlineExceeded, "dial tcp: connect: connection refused", 0, FailureConnect},
		{"TLS 失败", context.DeadlineExceeded, "tls: handshake failure", 0, FailureConnect},
		{"HTTP 403", context.DeadlineExceeded, "http status 403", 0, FailureHTTPStatus},
		{"未知错误+阶段到期", context.DeadlineExceeded, "some weird thing", 0, FailureTimeout},
		{"未知错误+非到期", nil, "some weird thing", 0, FailureZeroBytes},
	}
	for _, c := range cases {
		kind, detail := classifyPhaseFailure(c.phaseErr, mk(c.msg), c.total)
		if kind != c.want {
			t.Fatalf("%s: kind = %q, want %q (detail=%q)", c.name, kind, c.want, detail)
		}
	}
}

// TestFetchServerList 校验自研发现的响应解析与坏条目过滤。
func TestFetchServerList(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/js/servers", func(w http.ResponseWriter, r *http.Request) {
		if got := r.URL.Query().Get("search"); got != "Tokyo" {
			t.Errorf("search param = %q, want Tokyo", got)
		}
		_, _ = w.Write([]byte(`[
			{"id":"1","url":"http://a.example:8080/upload.php","name":"A","sponsor":"S1","country":"JP"},
			{"id":"2","url":"","name":"B","sponsor":"S2"},
			{"id":"3","url":"http://b.example/upload.php","name":"C","sponsor":"S3","country":"JP"}
		]`))
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	oldURL := serverListURL
	serverListURL = srv.URL + "/api/js/servers"
	defer func() { serverListURL = oldURL }()

	client, err := newMeasureClient(measureConfig{connections: 1}, nil)
	if err != nil {
		t.Fatalf("newMeasureClient: %v", err)
	}
	servers, err := fetchServerList(context.Background(), client, "Tokyo")
	if err != nil {
		t.Fatalf("fetchServerList: %v", err)
	}
	if len(servers) != 2 {
		t.Fatalf("len(servers) = %d, want 2 (empty url filtered)", len(servers))
	}
	if servers[0].displayName() != "S1 (A)" {
		t.Fatalf("displayName = %q", servers[0].displayName())
	}
	if got := downloadURLFor(servers[1].URL); got != "http://b.example/random4000x4000.jpg" {
		t.Fatalf("downloadURLFor = %q", got)
	}
}

// TestInjectSpeedtestLoopbackTag 校验出站 tag 的选取(审查 P1-2):
// 传了真实存在的主出站 tag 就用它(不再被 firstTestableOutbound 跳过
// selector/urltest); 传了不存在的 tag 必须安全回退。
func TestInjectSpeedtestLoopbackTag(t *testing.T) {
	configJSON := `{
		"outbounds": [
			{"type": "direct", "tag": "direct"},
			{"type": "selector", "tag": "my-selector", "outbounds": ["n1"]},
			{"type": "socks", "tag": "n1", "server": "127.0.0.1", "server_port": 1080}
		],
		"route": {"default_mark": 1234}
	}`

	parse := func(t *testing.T, injected string) map[string]any {
		t.Helper()
		var root map[string]any
		if err := json.Unmarshal([]byte(injected), &root); err != nil {
			t.Fatalf("unmarshal: %v", err)
		}
		return root
	}

	// 1) 显式传入 selector 根 tag: route.final 必须是它, 而不是被跳过后的 n1。
	injected, cred, err := InjectSpeedtestLoopback(configJSON, "my-selector", 12345, true)
	if err != nil {
		t.Fatalf("inject: %v", err)
	}
	root := parse(t, injected)
	route := root["route"].(map[string]any)
	if route["final"] != "my-selector" {
		t.Fatalf("route.final = %v, want my-selector", route["final"])
	}
	if route["auto_detect_interface"] != true {
		t.Fatalf("auto_detect_interface = %v, want true (protect available)", route["auto_detect_interface"])
	}
	if route["default_mark"] != float64(1234) {
		t.Fatalf("default_mark lost: %v", route["default_mark"])
	}
	inbounds := root["inbounds"].([]any)
	if len(inbounds) != 1 {
		t.Fatalf("inbounds = %d, want 1 (original inbounds dropped)", len(inbounds))
	}
	in0 := inbounds[0].(map[string]any)
	if in0["listen"] != "127.0.0.1" || in0["listen_port"] != float64(12345) {
		t.Fatalf("inbound = %v", in0)
	}
	// 回环入站必须带认证(审查 P2-7): 否则测速窗口内本机任意 App 都能把
	// 它当代理用, 白嫖被测节点流量。
	users, _ := in0["users"].([]any)
	if len(users) != 1 {
		t.Fatalf("inbound users = %v, want exactly 1", in0["users"])
	}
	user0 := users[0].(map[string]any)
	if user0["username"] != cred.Username || user0["password"] != cred.Password {
		t.Fatalf("inbound users %v != credential %+v", users, cred)
	}
	if len(cred.Username) != 16 || len(cred.Password) != 16 {
		t.Fatalf("credential length unexpected: %+v", cred)
	}
	if got := cred.ProxyURL(12345); got !=
		"socks5://"+cred.Username+":"+cred.Password+"@127.0.0.1:12345" {
		t.Fatalf("ProxyURL = %q", got)
	}

	// 2) 传一个不存在的 tag(自定义 config 型 profile 的 mainTag 可能是
	//    别的语义)必须回退, 不能生成 route.final 指向不存在出站的配置。
	injected, cred2, err := InjectSpeedtestLoopback(configJSON, "no-such-tag", 12345, false)
	if err != nil {
		t.Fatalf("inject fallback: %v", err)
	}
	route = parse(t, injected)["route"].(map[string]any)
	if route["final"] != "n1" {
		t.Fatalf("fallback final = %v, want n1", route["final"])
	}
	if _, ok := route["auto_detect_interface"]; ok {
		t.Fatal("auto_detect_interface must be absent in direct mode")
	}
	// 每个会话的凭据必须独立(不是全局固定值)。
	if cred2.Username == cred.Username || cred2.Password == cred.Password {
		t.Fatal("credential is not unique per session")
	}
}
