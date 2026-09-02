package singboxcfg

import (
	"encoding/json"
	"testing"

	"github.com/nebulagram/nebulagram/core/model"
)

// panelProfile is the shape a panel hands out for sing-box: one document that
// describes every server, with a tun listener and its own routing.
const panelProfile = `{
  "log": {"level": "info"},
  "dns": {"servers": [{"tag": "remote", "address": "https://1.1.1.1/dns-query"}]},
  "inbounds": [{"type": "tun", "tag": "tun-in", "auto_route": true},
               {"type": "mixed", "tag": "mixed-in", "listen_port": 2080}],
  "outbounds": [
    {"type": "vless", "tag": "🇫🇮 Finland", "server": "fi.example.com", "server_port": 443,
     "uuid": "uuid", "flow": "xtls-rprx-vision",
     "tls": {"enabled": true, "server_name": "fi.example.com",
             "utls": {"fingerprint": "chrome"},
             "reality": {"enabled": true, "public_key": "pk", "short_id": "01"}}},
    {"type": "hysteria2", "tag": "🇳🇱 Netherlands", "server": "nl.example.com", "server_port": 7443,
     "password": "secret", "tls": {"enabled": true, "server_name": "nl.example.com"}},
    {"type": "selector", "tag": "auto", "outbounds": ["🇫🇮 Finland", "🇳🇱 Netherlands"]},
    {"type": "direct", "tag": "direct"}
  ],
  "route": {"final": "auto", "auto_detect_interface": true,
            "rules": [{"domain_suffix": [".ru"], "outbound": "direct"}]}
}`

func TestParseProfileKeepsTheWholeDocument(t *testing.T) {
	servers, err := ParseProfile([]byte(panelProfile))
	if err != nil {
		t.Fatalf("ParseProfile: %v", err)
	}
	if len(servers) != 2 {
		t.Fatalf("got %d servers, want the two proxies", len(servers))
	}
	for _, s := range servers {
		if s.Config == "" {
			t.Errorf("%s carries no configuration", s.Name)
		}
		if s.ConfigTag != s.Name {
			t.Errorf("%s points at outbound %q", s.Name, s.ConfigTag)
		}
		if s.Engine() != model.EngineSingBox {
			t.Errorf("%s would run on %s", s.Name, s.Engine())
		}
	}
	if servers[0].Security != "reality" || servers[0].PublicKey != "pk" {
		t.Errorf("reality parameters were lost: %+v", servers[0])
	}
	if servers[1].Protocol != model.Hysteria2 {
		t.Errorf("second protocol = %q, want hysteria2", servers[1].Protocol)
	}
}

func TestNormalizeRoutesToTheChosenOutbound(t *testing.T) {
	out, err := Normalize([]byte(panelProfile), "🇳🇱 Netherlands", Defaults(10808))
	if err != nil {
		t.Fatalf("Normalize: %v", err)
	}
	var cfg map[string]any
	if err := json.Unmarshal(out, &cfg); err != nil {
		t.Fatalf("result is not JSON: %v", err)
	}

	inbounds := cfg["inbounds"].([]any)
	if len(inbounds) != 1 {
		t.Fatalf("got %d inbounds, want only ours", len(inbounds))
	}
	inbound := inbounds[0].(map[string]any)
	if inbound["type"] != "mixed" || inbound["listen_port"].(float64) != 10808 {
		t.Errorf("our inbound is wrong: %v", inbound)
	}
	if inbound["listen"] != "127.0.0.1" {
		t.Errorf("listen = %v, want loopback — a tun inbound needs privileges we do not hold", inbound["listen"])
	}

	route := cfg["route"].(map[string]any)
	if route["final"] != "🇳🇱 Netherlands" {
		t.Errorf("route.final = %v, want the chosen outbound", route["final"])
	}
	if route["rules"] == nil {
		t.Error("the panel's routing rules were dropped")
	}
	if cfg["dns"] == nil {
		t.Error("the panel's dns section was dropped")
	}
	if level := cfg["log"].(map[string]any)["level"]; level != "info" {
		t.Errorf("loglevel = %v, want the profile's own info", level)
	}
}

func TestNormalizeRejectsUnknownTag(t *testing.T) {
	if _, err := Normalize([]byte(panelProfile), "🇩🇪 Germany", Defaults(10808)); err == nil {
		t.Fatal("expected an error for an outbound the profile does not have")
	}
	if _, err := Normalize([]byte(panelProfile), "", Defaults(10808)); err == nil {
		t.Fatal("expected an error when no outbound is selected")
	}
}

func TestBuildStillWorksForPlainServers(t *testing.T) {
	server := model.Server{
		Protocol: model.Hysteria2, Address: "hy.example.com", Port: 443,
		Password: "secret", SNI: "hy.example.com",
	}
	out, err := Build(server, Defaults(10808))
	if err != nil {
		t.Fatalf("Build: %v", err)
	}
	var cfg map[string]any
	if err := json.Unmarshal(out, &cfg); err != nil {
		t.Fatal(err)
	}
	outbound := cfg["outbounds"].([]any)[0].(map[string]any)
	if outbound["type"] != "hysteria2" || outbound["password"] != "secret" {
		t.Errorf("outbound = %v", outbound)
	}
}
