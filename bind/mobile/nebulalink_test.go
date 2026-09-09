package nebulalink

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	xraycore "github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	_ "github.com/xtls/xray-core/proxy/vless/inbound"
)

// Exercises the actual gomobile API and Xray engine used by iOS and Android.
// All endpoints and disposable credentials are local; no provider/account is used.
func TestMobileSubscriptionSOCKSRouteAndStop(t *testing.T) {
	portListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := portListener.Addr().(*net.TCPAddr).Port
	portListener.Close()
	uuid := "c914b63b-7f13-44ad-b5de-f68d3af54024"
	config := fmt.Sprintf(`{"log":{"loglevel":"none"},"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"vless","settings":{"clients":[{"id":%q}],"decryption":"none"}}],"outbounds":[{"protocol":"freedom"}]}`, port, uuid)
	parsed, err := serial.LoadJSONConfig(bytes.NewBufferString(config))
	if err != nil {
		t.Fatal(err)
	}
	server, err := xraycore.New(parsed)
	if err != nil {
		t.Fatal(err)
	}
	if err := server.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { server.Close() })
	var hits atomic.Int32
	endpoint := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.WriteHeader(http.StatusNoContent)
	}))
	defer endpoint.Close()
	key := fmt.Sprintf("vless://%s@127.0.0.1:%d?security=none&type=tcp#Local-test", uuid, port)
	subscription := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprintln(w, key) }))
	defer subscription.Close()
	call := func(method string, payload any) map[string]any {
		t.Helper()
		data, err := json.Marshal(payload)
		if err != nil {
			t.Fatal(err)
		}
		var response map[string]any
		if err := json.Unmarshal([]byte(Call(method, string(data))), &response); err != nil {
			t.Fatal(err)
		}
		if response["ok"] != true {
			t.Fatalf("%s failed", method)
		}
		result, _ := response["data"].(map[string]any)
		return result
	}
	call("core.init", map[string]any{"dir": t.TempDir(), "os": "iOS", "user_agent": "NebulaGram/Test"})
	t.Cleanup(func() { Call("tunnel.stop", "{}"); SetEventSink(nil) })
	call("settings.set", map[string]any{"mode": "proxy"})
	status := call("onboarding.connect", map[string]any{"input": subscription.URL})
	if status["state"] != "connected" || status["mode"] != "proxy" {
		t.Fatal("Expected active local proxy")
	}
	socksPort := int(status["socks_port"].(float64))
	result := call("probe.url", map[string]any{"url": endpoint.URL})
	if result["latency_ms"] == nil || hits.Load() == 0 {
		t.Fatal("No traffic reached the target through SOCKS/VLESS")
	}
	call("tunnel.stop", map[string]any{})
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", socksPort), time.Second)
	if err == nil {
		conn.Close()
		t.Fatal("SOCKS listener survived disconnect")
	}
	if call("tunnel.status", map[string]any{})["state"] != "disconnected" {
		t.Fatal("Wrong stop status")
	}
}
