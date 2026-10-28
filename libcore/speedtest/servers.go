// servers.go — 自研 speedtest.net 服务器发现(2026-10-26 取代 speedtest-go)。
//
// 为什么不再用 speedtest-go 的 FetchServerListContext(审查 P0-2/P0-3):
//   - 它拿到列表后会对**每台**服务器再并发 ping 一轮, 用的是
//     context.Background()+4s(不继承调用方 ctx), 默认 HTTP 模式每台还要
//     多发一次 warmup 请求 —— 冷缓存时等于在正式测量之前先朝被测链路打
//     2N 个请求: 既占节点流量, 又把链路"搞热"、造成排队, 污染随后的计量;
//     取消/超时也必须等这 4 秒窗口走完才返回;
//   - 它的 New() 会先把自身 Transport 挂到 http.DefaultClient 上
//     (全局劫持且不恢复), 之后再应用 WithDoer —— 调用方注释里的
//     "防全局污染"根本不成立;
//   - 一旦有人把 PingMode 改成 TCP/ICMP, 那些 ping 走 tcpDialer/ipDialer,
//     **完全绕过代理直连**, 直接破坏"测量流量 100% 经 sing-box"的安全边界。
//
// 业务上只用到 url/sponsor/name 三个字段, 其余能力全部弃用, 因此这里自己
// 拉 https://www.speedtest.net/api/js/servers(请求与测量走同一个受控
// client, 同一个回环 mixed 入站, 不存在任何直连路径)。
package speedtest

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sync"
	"time"
)

const (
	// serverListMaxBytes 列表响应体读取上限(正常 ~10 条, 几十 KB)。
	serverListMaxBytes = 4 << 20
	// serverListCacheTTL 列表跨会话缓存有效期。列表只是全局候选集,
	// 具体下载请求仍然 100% 经被测节点的回环入站, 所以可以跨会话复用;
	// 命中时完全跳过发现阶段, 把每节点预算全部留给下载计量。
	serverListCacheTTL = 30 * time.Minute
	// serverListStaleTTL 拉取失败时允许回退的旧缓存龄期(慢节点上拉取
	// 失败很常见, 有一份旧列表好过整批失败)。
	serverListStaleTTL = 24 * time.Hour
	// browserUserAgent js 接口对空 UA 可能返回空列表, 用浏览器 UA。
	browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
		"AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

	// probeMaxCandidates 一次最多探测几台候选服务器。
	probeMaxCandidates = 5
	// probeTimeout 单台探测的超时。
	probeTimeout = 2 * time.Second
	// probeTotalTimeout 全部探测的总超时(并行, 所以约等于最慢的一台)。
	probeTotalTimeout = 2500 * time.Millisecond
	// probeRangeBytes 探测请求只取前面这么点字节, 避免真的开始下载。
	probeRangeBytes = 2048
)

// serverListURL speedtest.net 的 js 服务器列表接口。变量形式只为测试
// 能把它指向 httptest 服务器。
var serverListURL = "https://www.speedtest.net/api/js/servers"

// serverEntry speedtest.net 列表里我们真正需要的字段。
type serverEntry struct {
	ID      string `json:"id"`
	URL     string `json:"url"`
	Name    string `json:"name"`
	Sponsor string `json:"sponsor"`
	Country string `json:"country"`
}

// displayName 卡片/日志里展示的名字, 与旧实现保持一致("Sponsor (Name)")。
func (s serverEntry) displayName() string {
	switch {
	case s.Sponsor == "":
		return s.Name
	case s.Name == "":
		return s.Sponsor
	default:
		return s.Sponsor + " (" + s.Name + ")"
	}
}

// ---- 跨会话缓存 ----

type serverCacheEntry struct {
	servers []serverEntry
	fetched time.Time
}

var (
	serverCacheMu   sync.Mutex
	serverListCache = map[string]serverCacheEntry{}
)

func cachedServerList(keyword string, maxAge time.Duration) ([]serverEntry, bool) {
	serverCacheMu.Lock()
	defer serverCacheMu.Unlock()
	e, ok := serverListCache[keyword]
	if !ok || time.Since(e.fetched) > maxAge || len(e.servers) == 0 {
		return nil, false
	}
	return e.servers, true
}

func storeServerList(keyword string, servers []serverEntry) {
	if len(servers) == 0 {
		return
	}
	serverCacheMu.Lock()
	defer serverCacheMu.Unlock()
	serverListCache[keyword] = serverCacheEntry{servers: servers, fetched: time.Now()}
}

// fetchServerList 拉取服务器列表。keyword 非空时交给服务端做模糊筛选
// (与 speedtest-go 的 search 参数一致)。
func fetchServerList(ctx context.Context, client *http.Client, keyword string) ([]serverEntry, error) {
	u, err := url.Parse(serverListURL)
	if err != nil {
		return nil, err
	}
	query := u.Query()
	if keyword != "" {
		query.Set("search", keyword)
	}
	u.RawQuery = query.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", browserUserAgent)
	req.Header.Set("Accept", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("server list http status %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, serverListMaxBytes))
	if err != nil {
		return nil, err
	}
	var raw []serverEntry
	if err = json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("decode server list: %w", err)
	}
	// 过滤掉没有可用 URL 的条目(下游 downloadURLFor 依赖它)。
	servers := make([]serverEntry, 0, len(raw))
	for _, s := range raw {
		if s.URL == "" {
			continue
		}
		servers = append(servers, s)
	}
	if len(servers) == 0 {
		return nil, fmt.Errorf("server list empty (keyword=%q)", keyword)
	}
	return servers, nil
}

// resolveServers 取服务器列表: 内存缓存 → 现拉 → 回退旧缓存。
//
// keyword 会作为 search 参数发给服务端; cacheKey 是缓存隔离键, 必须包含
// "这个列表是按哪个出口 IP 的地理位置排的"这一信息(审查 P2-4), 见
// Options.ServerCacheKey。
func resolveServers(
	ctx context.Context,
	client *http.Client,
	keyword string,
	cacheKey string,
	diag func(string),
) ([]serverEntry, error) {
	if cacheKey == "" {
		cacheKey = keyword
	}
	if cached, ok := cachedServerList(cacheKey, serverListCacheTTL); ok {
		diag(fmt.Sprintf("speedtest: server list cache hit (%d servers, key=%q), skip discovery",
			len(cached), cacheKey))
		return cached, nil
	}
	servers, err := fetchServerList(ctx, client, keyword)
	if err != nil {
		if stale, ok := cachedServerList(cacheKey, serverListStaleTTL); ok {
			diag("speedtest: fetch server list failed, fallback to stale cache: " + err.Error())
			return stale, nil
		}
		return nil, fmt.Errorf("fetch server list: %w", err)
	}
	storeServerList(cacheKey, servers)
	return servers, nil
}

// pickServer 从候选里挑一台"现在确实可用且 RTT 最低"的服务器。
//
// 审查 P2-4: 旧实现直接用 servers[0] —— 列表顺序是按**拉取列表时那个
// 出口 IP** 的地理距离排的, 而列表会被跨会话缓存复用, 换到别的地区节点
// 后这个顺序就不再代表该节点的最优选择; 首选服务器故障/限流时整节点直接
// 失败, 没有任何回退。这里用一次小的 Range 请求实测 RTT, 顺便把不可用
// 的候选排掉。探测走同一条受控链路, 且发生在计量开始之前。
func pickServer(
	ctx context.Context,
	client *http.Client,
	servers []serverEntry,
	diag func(string),
) serverEntry {
	if len(servers) == 0 {
		return serverEntry{}
	}
	if len(servers) == 1 {
		return servers[0]
	}
	n := len(servers)
	if n > probeMaxCandidates {
		n = probeMaxCandidates
	}
	candidates := servers[:n]

	type probeResult struct {
		index int
		rtt   time.Duration
	}

	probeCtx, cancel := context.WithTimeout(ctx, probeTotalTimeout)
	defer cancel()

	results := make(chan probeResult, n)
	for i, s := range candidates {
		go func(i int, s serverEntry) {
			// panic 兜底 + 必须回传一条结果: 这个 goroutine 若不 send,
			// 主循环要一直等到 probeCtx 超时(审查 §四.4)。
			defer func() {
				if r := recover(); r != nil {
					results <- probeResult{index: i, rtt: 0}
				}
			}()
			results <- probeResult{index: i, rtt: probeServerRTT(probeCtx, client, s.URL)}
		}(i, s)
	}

	best := -1
	var bestRTT time.Duration
	received := 0
	for received < n {
		select {
		case r := <-results:
			received++
			if r.rtt <= 0 {
				continue
			}
			if best < 0 || r.rtt < bestRTT || (r.rtt == bestRTT && r.index < best) {
				best, bestRTT = r.index, r.rtt
			}
		case <-probeCtx.Done():
			received = n // 超时: 用已收到/已默认的结果
		}
	}

	if best < 0 {
		// 全部探测失败: 退回第一台, 让测量阶段去暴露真实原因。
		diag(fmt.Sprintf("speedtest: all %d server probes failed, fallback to %q",
			n, candidates[0].displayName()))
		return candidates[0]
	}
	if best != 0 {
		diag(fmt.Sprintf("speedtest: probe picked server #%d %q (rtt=%s) instead of #0 %q",
			best, candidates[best].displayName(), bestRTT, candidates[0].displayName()))
	} else {
		diag(fmt.Sprintf("speedtest: probe confirmed server #0 %q (rtt=%s)",
			candidates[0].displayName(), bestRTT))
	}
	return candidates[best]
}

// probeServerRTT 对下载端点发一次小 Range 请求, 返回首字节耗时;
// 失败(或非 2xx)返回 0。
func probeServerRTT(ctx context.Context, client *http.Client, serverURL string) time.Duration {
	dlURL := downloadURLFor(serverURL)
	if dlURL == "" {
		return 0
	}
	probeCtx, cancel := context.WithTimeout(ctx, probeTimeout)
	defer cancel()

	req, err := http.NewRequestWithContext(probeCtx, http.MethodGet, dlURL, nil)
	if err != nil {
		return 0
	}
	req.Header.Set("Range", fmt.Sprintf("bytes=0-%d", probeRangeBytes-1))

	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return 0
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return 0
	}
	// 读到首字节再计时: 大 RTT 链路上首字节时间才代表真实可用性。
	if _, err = io.CopyN(io.Discard, resp.Body, 1); err != nil {
		return 0
	}
	return time.Since(start)
}
