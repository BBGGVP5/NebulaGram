package xray

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/xtls/xray-core/features/outbound"
)

// The remote proxy is deliberately NOT another Xray core in this process.
// Xray's real pinned HTTP outbound must CONNECT before the fixture sees GET.
func proxyFixture(t *testing.T, status int, hold <-chan struct{}) (model.Server, *atomic.Int32, <-chan struct{}) {
	t.Helper()
	var gets atomic.Int32
	entered := make(chan struct{}, 16)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodConnect {
			t.Errorf("expected CONNECT, got %s", r.Method)
			return
		}
		c, b, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer c.Close()
		_ = c.SetDeadline(time.Now().Add(5 * time.Second))
		_, _ = b.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n")
		_ = b.Flush()
		req, err := http.ReadRequest(b.Reader)
		if err != nil {
			return
		}
		if req.Method != http.MethodGet {
			t.Errorf("not GET: %s", req.Method)
			return
		}
		gets.Add(1)
		entered <- struct{}{}
		if hold != nil {
			<-hold
		}
		fmt.Fprintf(c, "HTTP/1.1 %d Fixture\r\nContent-Length: 0\r\nLocation: http://direct.invalid/leak\r\nConnection: close\r\n\r\n", status)
	}))
	t.Cleanup(srv.Close)
	u, _ := url.Parse(srv.URL)
	host, port, _ := net.SplitHostPort(u.Host)
	return model.Server{ID: u.Host, Protocol: model.Custom, Config: fmt.Sprintf(`{"outbounds":[{"tag":"node","protocol":"http","settings":{"servers":[{"address":%q,"port":%s}]}}]}`, host, port)}, &gets, entered
}

func TestNimboPrivateHandlersTwoNodesAndActivePreserved(t *testing.T) {
	var direct atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { direct.Add(1); w.WriteHeader(204) }))
	defer target.Close()
	active := &instance{}
	if err := active.Start([]byte(`{"log":{"loglevel":"none"},"stats":{},"policy":{"system":{"statsOutboundUplink":true,"statsOutboundDownlink":true}},"outbounds":[{"tag":"proxy","protocol":"blackhole"}]}`)); err != nil {
		t.Fatal(err)
	}
	defer active.Stop()
	original := active.server
	manager := original.GetFeature(outbound.ManagerType()).(outbound.Manager)
	handler := manager.GetDefaultHandler()
	done := make(chan struct{})
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		for {
			select {
			case <-done:
				return
			default:
				manager.(outbound.HandlerSelector).Select([]string{""})
				_, _, _ = active.Stats()
			}
		}
	}()
	defer func() { close(done); wg.Wait() }()
	a, ag, _ := proxyFixture(t, 204, nil)
	b, bg, _ := proxyFixture(t, 200, nil)
	for _, node := range []model.Server{a, b, a, b} {
		ms, err := Nimbo(context.Background(), node, target.URL, 2*time.Second)
		if err != nil || ms < 0 {
			t.Fatalf("real private handler failed: %d %v", ms, err)
		}
		if active.server != original || manager.GetDefaultHandler() != handler || len(manager.ListHandlers(context.Background())) != 1 {
			t.Fatal("active runtime mutated")
		}
	}
	if ag.Load() != 2 || bg.Load() != 2 || direct.Load() != 0 {
		t.Fatalf("wrong path: a=%d b=%d direct=%d", ag.Load(), bg.Load(), direct.Load())
	}
	if up, down, err := active.Stats(); err != nil || up != 0 || down != 0 {
		t.Fatalf("active stats polluted: %d %d %v", up, down, err)
	}
}

func TestNimboStatusCancelAndConnectorPriority(t *testing.T) {
	for _, status := range []int{302, 503} {
		node, gets, _ := proxyFixture(t, status, nil)
		if ms, err := Nimbo(context.Background(), node, "http://target.invalid/check", time.Second); err == nil || ms != -1 || gets.Load() != 1 {
			t.Fatalf("status %d: %d %v", status, ms, err)
		}
	}
	hold := make(chan struct{})
	defer close(hold)
	node, _, entered := proxyFixture(t, 204, hold)
	result := make(chan error, 1)
	go func() {
		_, err := Nimbo(context.Background(), node, "http://target.invalid/check", 5*time.Second)
		result <- err
	}()
	select {
	case <-entered:
	case <-time.After(3 * time.Second):
		t.Fatal("probe not entered")
	}
	active := &instance{}
	start := make(chan error, 1)
	go func() {
		start <- active.Start([]byte(`{"log":{"loglevel":"none"},"outbounds":[{"tag":"proxy","protocol":"blackhole"}]}`))
	}()
	select {
	case err := <-start:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("connector did not preempt diagnostic")
	}
	defer active.Stop()
	select {
	case err := <-result:
		if err == nil {
			t.Fatal("canceled probe succeeded")
		}
	case <-time.After(time.Second):
		t.Fatal("probe leaked")
	}
	runtimeOwner.mu.Lock()
	leased := runtimeOwner.probe
	owner := runtimeOwner.active
	runtimeOwner.mu.Unlock()
	if leased != nil || owner != active {
		t.Fatal("lease lifecycle corrupted")
	}
}

func TestNimboTLSValidationThroughProxy(t *testing.T) {
	tlsTarget := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { t.Error("untrusted TLS reached HTTP") }))
	defer tlsTarget.Close()
	var connects atomic.Int32
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "CONNECT" {
			t.Error("not CONNECT")
			return
		}
		connects.Add(1)
		dst, err := net.DialTimeout("tcp", r.Host, time.Second)
		if err != nil {
			return
		}
		defer dst.Close()
		src, b, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer src.Close()
		fmt.Fprint(b, "HTTP/1.1 200 OK\r\n\r\n")
		b.Flush()
		done := make(chan struct{})
		go func() { io.Copy(dst, b); dst.Close(); close(done) }()
		io.Copy(src, dst)
		src.Close()
		<-done
	}))
	defer proxy.Close()
	u, _ := url.Parse(proxy.URL)
	host, port, _ := net.SplitHostPort(u.Host)
	node := model.Server{Protocol: model.Custom, Config: fmt.Sprintf(`{"outbounds":[{"protocol":"http","settings":{"servers":[{"address":%q,"port":%s}]}}]}`, host, port)}
	if ms, err := Nimbo(context.Background(), node, tlsTarget.URL, 2*time.Second); err == nil || ms != -1 {
		t.Fatalf("untrusted TLS accepted: %d %v", ms, err)
	}
	if connects.Load() != 1 {
		t.Fatal("TLS did not use exact proxy")
	}
}

func TestNimboConfigFidelityAndFailClosed(t *testing.T) {
	raw := `{"outbounds":[{"tag":"direct","protocol":"freedom"},{"tag":"chosen","protocol":"vless","sendThrough":"127.0.0.1","settings":{"vnext":[{"address":"node.invalid","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000001","encryption":"none"}]}]},"streamSettings":{"network":"ws","security":"reality","realitySettings":{"serverName":"name.invalid","fingerprint":"chrome","publicKey":"abc","shortId":"aabb"},"wsSettings":{"path":"/exact"}},"mux":{"enabled":true,"concurrency":4}}]}`
	out, err := probeOutbound(model.Server{Config: raw}, "private")
	if err != nil {
		t.Fatal(err)
	}
	encoded, _ := json.Marshal(out)
	var doc map[string]any
	json.Unmarshal(encoded, &doc)
	if doc["sendThrough"] != "127.0.0.1" || doc["tag"] != "private" {
		t.Fatalf("bind/tag changed: %s", encoded)
	}
	if out.StreamSetting == nil || out.MuxSettings == nil || !out.MuxSettings.Enabled {
		t.Fatal("transport/mux lost")
	}
	for _, raw := range []string{`{"outbounds":[{"protocol":"freedom"}]}`, `{"outbounds":[{"protocol":"http"},{"protocol":"socks"}]}`, `{"outbounds":[{"protocol":"http","proxySettings":{"tag":"proxy"}}]}`, `{"outbounds":[{"protocol":"http","streamSettings":{"sockopt":{"dialerProxy":"proxy"}}}]}`} {
		if _, err := probeOutbound(model.Server{Config: raw}, "p"); err == nil {
			t.Fatalf("unsafe config accepted: %s", raw)
		}
	}
	if _, err := probeOutbound(model.Server{Config: `{"outbounds":[{"tag":"a","protocol":"http"},{"tag":"b","protocol":"socks"}]}`, ConfigTag: "b"}, "p"); err != nil {
		t.Fatal(err)
	}
}

func TestNimboNeverInterruptsActiveProxyTraffic(t *testing.T) {
	activeNode, activeGets, _ := proxyFixture(t, 204, nil)
	var doc map[string]json.RawMessage
	json.Unmarshal([]byte(activeNode.Config), &doc)
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	address := listener.Addr().String()
	_, port, _ := net.SplitHostPort(address)
	listener.Close()
	config := fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"listen":"127.0.0.1","port":%s,"protocol":"http","settings":{}}],"outbounds":%s}`, port, doc["outbounds"])
	active := &instance{}
	if err := active.Start([]byte(config)); err != nil {
		t.Fatal(err)
	}
	defer active.Stop()
	original := active.server
	proxyURL, _ := url.Parse("http://" + address)
	tr := &http.Transport{Proxy: http.ProxyURL(proxyURL), DisableKeepAlives: true}
	defer tr.CloseIdleConnections()
	client := &http.Client{Transport: tr, Timeout: time.Second}
	request := func() {
		t.Helper()
		r, err := client.Get("http://active-target.invalid/check")
		if err != nil {
			t.Fatal(err)
		}
		r.Body.Close()
		if r.StatusCode != 204 {
			t.Fatal(r.StatusCode)
		}
	}
	request()
	hold := make(chan struct{})
	defer close(hold)
	node, probeGets, entered := proxyFixture(t, 204, hold)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	result := make(chan error, 1)
	go func() { _, err := Nimbo(ctx, node, "http://probe-target.invalid/check", 5*time.Second); result <- err }()
	select {
	case <-entered:
	case <-time.After(2 * time.Second):
		t.Fatal("probe did not start")
	}
	request() // actual app proxy traffic succeeds while diagnostic is suspended
	cancel()
	select {
	case err := <-result:
		if err == nil {
			t.Fatal("cancel succeeded")
		}
	case <-time.After(time.Second):
		t.Fatal("cancel did not clean up")
	}
	request()
	if active.server != original || activeGets.Load() != 3 || probeGets.Load() != 1 {
		t.Fatalf("active affected: active=%d probe=%d", activeGets.Load(), probeGets.Load())
	}
}

// Each remote Xray lives in a separate process, exactly like a real server;
// tests must not hide the process-global DNS/dialer constraint with a second core.
func TestRemoteXrayHelper(t *testing.T) {
	config := os.Getenv("NEBULALINK_TEST_REMOTE_CONFIG")
	if config == "" {
		return
	}
	server := &instance{}
	if err := server.Start([]byte(config)); err != nil {
		t.Fatal(err)
	}
	defer server.Stop()
	fmt.Println("NEBULALINK_READY")
	_, _ = io.Copy(io.Discard, os.Stdin)
}

func remoteVLESS(t *testing.T, target string) model.Server {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close()
	const uuid = "00000000-0000-0000-0000-000000000001"
	config := fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"vless","settings":{"clients":[{"id":%q}],"decryption":"none"}}],"outbounds":[{"protocol":"freedom","settings":{"redirect":%q,"finalRules":[{"action":"allow","ip":["127.0.0.1/32"]}]}}]}`, port, uuid, target)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	cmd := exec.CommandContext(ctx, os.Args[0], "-test.run=^TestRemoteXrayHelper$")
	cmd.Env = append(os.Environ(), "NEBULALINK_TEST_REMOTE_CONFIG="+config)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		cancel()
		t.Fatal(err)
	}
	stdin, err := cmd.StdinPipe()
	if err != nil {
		cancel()
		t.Fatal(err)
	}
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		cancel()
		t.Fatal(err)
	}
	t.Cleanup(func() {
		stdin.Close()
		if err := cmd.Wait(); err != nil {
			t.Errorf("remote fixture: %v", err)
		}
		cancel()
	})
	scanner := bufio.NewScanner(stdout)
	ready := false
	for scanner.Scan() {
		if scanner.Text() == "NEBULALINK_READY" {
			ready = true
			break
		}
	}
	if !ready {
		t.Fatal("remote did not start")
	}
	return model.Server{ID: strconv.Itoa(port), Protocol: model.VLESS, Address: "127.0.0.1", Port: port, UUID: uuid}
}

func TestNimboRealVLESSRemoteProcessesAndMux(t *testing.T) {
	var gets [2]atomic.Int32
	var nodes []model.Server
	for index := range gets {
		index := index
		target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method != "GET" {
				t.Error("not GET")
			}
			gets[index].Add(1)
			w.WriteHeader(204)
		}))
		t.Cleanup(target.Close)
		nodes = append(nodes, remoteVLESS(t, strings.TrimPrefix(target.URL, "http://")))
	}
	active := &instance{}
	if err := active.Start([]byte(`{"log":{"loglevel":"none"},"outbounds":[{"tag":"proxy","protocol":"blackhole"}]}`)); err != nil {
		t.Fatal(err)
	}
	defer active.Stop()
	for _, node := range nodes {
		for _, mux := range []bool{false, true} {
			out, err := probeOutbound(node, "chosen")
			if err != nil {
				t.Fatal(err)
			}
			raw, _ := json.Marshal(out)
			var config map[string]any
			json.Unmarshal(raw, &config)
			if mux {
				config["mux"] = map[string]any{"enabled": true, "concurrency": 2}
			}
			root, _ := json.Marshal(map[string]any{"outbounds": []any{config}})
			node.Config = string(root)
			ms, err := Nimbo(context.Background(), node, "http://never-direct.invalid/check", 3*time.Second)
			if err != nil || ms < 0 {
				t.Fatalf("VLESS mux=%v failed: %d %v", mux, ms, err)
			}
		}
	}
	if gets[0].Load() != 2 || gets[1].Load() != 2 {
		t.Fatalf("wrong distinct remote route: %d %d", gets[0].Load(), gets[1].Load())
	}
}

func TestNimboMuxCancelCleanupBeforeLifecycleChange(t *testing.T) {
	entered := make(chan struct{}, 1)
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { entered <- struct{}{}; <-r.Context().Done() }))
	defer target.Close()
	node := remoteVLESS(t, strings.TrimPrefix(target.URL, "http://"))
	out, err := probeOutbound(node, "private")
	if err != nil {
		t.Fatal(err)
	}
	raw, _ := json.Marshal(out)
	var doc map[string]any
	json.Unmarshal(raw, &doc)
	doc["mux"] = map[string]any{"enabled": true, "concurrency": 2}
	raw, _ = json.Marshal(map[string]any{"outbounds": []any{doc}})
	node.Config = string(raw)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	result := make(chan error, 1)
	go func() { _, err := Nimbo(ctx, node, "http://never-direct.invalid/hold", 5*time.Second); result <- err }()
	select {
	case <-entered:
	case <-time.After(3 * time.Second):
		t.Fatal("mux GET did not reach server")
	}
	cancel()
	select {
	case err := <-result:
		if err == nil {
			t.Fatal("canceled mux succeeded")
		}
	case <-time.After(time.Second):
		t.Fatal("mux cleanup blocked")
	}
	runtimeOwner.mu.Lock()
	lease := runtimeOwner.probe
	runtimeOwner.mu.Unlock()
	if lease != nil {
		t.Fatal("mux lease leaked")
	}
	active := &instance{}
	if err := active.Start([]byte(`{"log":{"loglevel":"none"},"outbounds":[{"protocol":"blackhole"}]}`)); err != nil {
		t.Fatal(err)
	}
	if err := active.Stop(); err != nil {
		t.Fatal(err)
	}
}

func TestNimboRejectsCredentialURLsAndDirectRoutes(t *testing.T) {
	var direct atomic.Int32
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { direct.Add(1) }))
	defer target.Close()
	node, _, _ := proxyFixture(t, 204, nil)
	for _, url := range []string{"http://user:secret@target.invalid/path?private=yes", "file:///tmp/secret", "https://target.invalid/#secret"} {
		ms, err := Nimbo(context.Background(), node, url, time.Second)
		if err == nil || ms != -1 || strings.Contains(err.Error(), "secret") || strings.Contains(err.Error(), "target.invalid") {
			t.Fatalf("unsafe URL/error: %d %v", ms, err)
		}
	}
	if _, err := Nimbo(context.Background(), model.Server{Config: `{"outbounds":[{"protocol":"freedom"}]}`}, target.URL, time.Second); err == nil {
		t.Fatal("DIRECT accepted")
	}
	if direct.Load() != 0 {
		t.Fatal("direct target was fetched")
	}
}
