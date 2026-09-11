package remnawave

import (
	"context"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/nebulagram/nebulagram/core/model"
)

func endpoint(address string, port int) model.Server {
	return model.Server{Address: address, Port: port}
}

// A panel that does not recognise the client answers with its refusal shaped
// like a server list: one entry at 0.0.0.0:1 named "client not supported".
const refusal = "vless://00000000-0000-0000-0000-000000000000@0.0.0.0:1?encryption=none&type=tcp&security=none#unsupported"

const singboxProfile = `{"outbounds":[
  {"type":"vless","tag":"Finland","server":"example.org","server_port":443,"uuid":"1f1e2aba-f6ee-481e-a2e6-1851e27f2218","flow":"xtls-rprx-vision",
   "tls":{"enabled":true,"server_name":"google.com","reality":{"enabled":true,"public_key":"key","short_id":"ab"}}},
  {"type":"selector","tag":"Autobalance","outbounds":["Finland"]}
]}`

func panel(t *testing.T, accepted string) (*httptest.Server, *[]string) {
	t.Helper()
	var seen []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ua := r.Header.Get("User-Agent")
		seen = append(seen, ua)
		if ua == accepted {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(singboxProfile))
			return
		}
		_, _ = w.Write([]byte(base64.StdEncoding.EncodeToString([]byte(refusal))))
	}))
	t.Cleanup(server.Close)
	return server, &seen
}

func TestRefusedClientIsAskedAgainAsOneThePanelServes(t *testing.T) {
	server, seen := panel(t, CompatibleUserAgent)
	client := &Client{}
	res, err := client.Fetch(context.Background(), server.URL, "Panel")
	if err != nil {
		t.Fatalf("fetch: %v", err)
	}
	if res.Format != "sing-box" {
		t.Fatalf("format = %q, want sing-box", res.Format)
	}
	if len(res.Servers) != 1 || res.Servers[0].Address != "example.org" {
		t.Fatalf("servers = %+v", res.Servers)
	}
	if len(*seen) != 2 || (*seen)[0] != DefaultUserAgent || (*seen)[1] != CompatibleUserAgent {
		t.Fatalf("asked as %v, want our own name first", *seen)
	}
}

func TestAPinnedUserAgentIsNotSecondGuessed(t *testing.T) {
	server, seen := panel(t, CompatibleUserAgent)
	client := &Client{Device: Device{UserAgent: "MyPanel/2.0"}}
	if _, err := client.Fetch(context.Background(), server.URL, "Panel"); err == nil {
		t.Fatal("want the refusal reported, got a result")
	}
	if len(*seen) != 1 || (*seen)[0] != "MyPanel/2.0" {
		t.Fatalf("asked as %v, want exactly the pinned name", *seen)
	}
}

func TestOurOwnNameIsKeptWhenItAlreadyWorks(t *testing.T) {
	server, seen := panel(t, DefaultUserAgent)
	client := &Client{}
	res, err := client.Fetch(context.Background(), server.URL, "Panel")
	if err != nil {
		t.Fatalf("fetch: %v", err)
	}
	if len(res.Servers) != 1 {
		t.Fatalf("servers = %+v", res.Servers)
	}
	if len(*seen) != 1 {
		t.Fatalf("asked %d times, want one", len(*seen))
	}
}

func TestPlaceholderEndpointsNeverReachTheList(t *testing.T) {
	for _, address := range []string{"0.0.0.0", "::", ""} {
		if !placeholder(endpoint(address, 443)) {
			t.Errorf("%q accepted as a real endpoint", address)
		}
	}
	if !placeholder(endpoint("example.org", 0)) {
		t.Error("port 0 accepted as a real endpoint")
	}
	if !placeholder(model.Server{Address: "example.org", Port: 443, UUID: "00000000-0000-0000-0000-000000000000"}) {
		t.Error("the all-zero identity of a refusal stub was accepted")
	}
	if placeholder(endpoint("127.0.0.1", 1080)) {
		t.Error("a local proxy was dropped")
	}
	if placeholder(endpoint("example.org", 443)) {
		t.Error("a real endpoint was dropped")
	}
	// A panel refusing an unknown client sometimes points every entry at a
	// site it does not operate; those are a refusal, not a server list.
	for _, address := range []string{"google.com", "www.google.com", "GOOGLE.COM", "apple.com"} {
		if !placeholder(endpoint(address, 456)) {
			t.Errorf("%q accepted as a real endpoint", address)
		}
	}
	if placeholder(endpoint("node.google.com.example.net", 443)) {
		t.Error("a host that merely contains a decoy name was dropped")
	}
}
