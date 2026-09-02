package xraycfg

import (
	"encoding/json"
	"testing"
)

// userConfig is the kind of hand-written Xray config a panel hands out: its own
// listener, its own outbound tag, and routing rules that reference that tag.
const userConfig = `{
  "log": {"loglevel": "info"},
  "inbounds": [{"tag": "in", "protocol": "socks", "port": 1080, "listen": "0.0.0.0"},
               {"tag": "tun", "protocol": "dokodemo-door", "port": 12345}],
  "outbounds": [
    {"tag": "vpn", "protocol": "vless",
     "settings": {"vnext": [{"address": "de.example.com", "port": 443,
       "users": [{"id": "uuid", "flow": "xtls-rprx-vision", "encryption": "none"}]}]},
     "streamSettings": {"network": "tcp", "security": "reality",
       "realitySettings": {"serverName": "www.microsoft.com", "publicKey": "pk", "shortId": "01"}}},
    {"tag": "direct", "protocol": "freedom"},
    {"tag": "block", "protocol": "blackhole"}
  ],
  "routing": {"rules": [
    {"type": "field", "domain": ["geosite:category-ads"], "outboundTag": "block"},
    {"type": "field", "domain": ["geosite:ru"], "outboundTag": "direct"},
    {"type": "field", "network": "tcp,udp", "outboundTag": "vpn"}
  ]},
  "dns": {"servers": ["8.8.8.8"]}
}`

// normalized runs the config as if geoip.dat and geosite.dat were installed,
// which is the case where the panel's rules survive untouched.
func normalized(t *testing.T) map[string]any {
	t.Helper()
	options := Defaults(10808)
	options.GeoAssets = true
	out, err := Normalize([]byte(userConfig), options)
	if err != nil {
		t.Fatalf("Normalize: %v", err)
	}
	var cfg map[string]any
	if err := json.Unmarshal(out, &cfg); err != nil {
		t.Fatalf("result is not JSON: %v", err)
	}
	return cfg
}

func TestNormalizeInstallsOurInbound(t *testing.T) {
	cfg := normalized(t)
	inbounds, ok := cfg["inbounds"].([]any)
	if !ok || len(inbounds) == 0 {
		t.Fatalf("inbounds = %v", cfg["inbounds"])
	}
	first := inbounds[0].(map[string]any)
	if first["tag"] != "socks-in" || first["port"].(float64) != 10808 {
		t.Errorf("our socks inbound is not first: %v", first)
	}
	if first["listen"] != "127.0.0.1" {
		t.Errorf("listen = %v, want loopback", first["listen"])
	}
	// The user's own socks listener would fight ours for traffic, the
	// dokodemo-door one is theirs to keep... except it is also a local
	// listener, so both are dropped and nothing else is.
	for _, item := range inbounds[1:] {
		inbound := item.(map[string]any)
		switch inbound["protocol"] {
		case "socks", "http", "mixed", "dokodemo-door":
			t.Errorf("a local listener survived normalisation: %v", inbound)
		}
	}
}

func TestNormalizeRetagsProxyAndRouting(t *testing.T) {
	cfg := normalized(t)

	outbounds := cfg["outbounds"].([]any)
	if tag := outbounds[0].(map[string]any)["tag"]; tag != "proxy" {
		t.Errorf("proxy outbound tag = %v, want proxy", tag)
	}
	if tag := outbounds[1].(map[string]any)["tag"]; tag != "direct" {
		t.Errorf("the direct outbound was renamed: %v", tag)
	}

	rules := cfg["routing"].(map[string]any)["rules"].([]any)
	tags := make([]string, 0, len(rules))
	for _, rule := range rules {
		tags = append(tags, rule.(map[string]any)["outboundTag"].(string))
	}
	want := []string{"block", "direct", "proxy"}
	for i := range want {
		if tags[i] != want[i] {
			t.Fatalf("routing tags = %v, want %v", tags, want)
		}
	}
}

func TestNormalizeKeepsUserSections(t *testing.T) {
	cfg := normalized(t)
	if cfg["dns"] == nil {
		t.Error("the user's dns section was dropped")
	}
	if level := cfg["log"].(map[string]any)["loglevel"]; level != "info" {
		t.Errorf("loglevel = %v, want the user's own info", level)
	}
	policy := cfg["policy"].(map[string]any)["system"].(map[string]any)
	if policy["statsOutboundUplink"] != true {
		t.Error("traffic counters were not enabled")
	}
}

func TestLooksLikeConfigRejectsSingBox(t *testing.T) {
	singbox := `{"outbounds":[{"type":"vless","server":"a.example","server_port":443}]}`
	if LooksLikeConfig([]byte(singbox)) {
		t.Error("a sing-box profile was taken for an Xray configuration")
	}
	if LooksLikeConfig([]byte("vless://uuid@a.example:443")) {
		t.Error("a share link was taken for a configuration")
	}
	if !LooksLikeConfig([]byte(userConfig)) {
		t.Error("a real Xray configuration was not recognised")
	}
}

func TestProbeTargetFindsTheEndpoint(t *testing.T) {
	host, port := ProbeTarget([]byte(userConfig))
	if host != "de.example.com" || port != 443 {
		t.Errorf("probe target = %s:%d, want de.example.com:443", host, port)
	}
}

func TestNormalizeRejectsConfigWithoutOutbounds(t *testing.T) {
	if _, err := Normalize([]byte(`{"inbounds":[]}`), Defaults(10808)); err == nil {
		t.Fatal("expected an error for a configuration with no outbounds")
	}
}

// Without the data files Xray refuses to load a configuration that mentions
// geoip: or geosite: at all — it fails the whole config, not the rule. Panels
// ship such rules constantly, so the alternative to stripping them is a server
// that simply cannot connect.
func TestNormalizeDropsGeoRulesWithoutTheDataFiles(t *testing.T) {
	out, err := Normalize([]byte(userConfig), Defaults(10808))
	if err != nil {
		t.Fatalf("Normalize: %v", err)
	}
	var cfg map[string]any
	if err := json.Unmarshal(out, &cfg); err != nil {
		t.Fatal(err)
	}

	rules := cfg["routing"].(map[string]any)["rules"].([]any)
	if len(rules) != 1 {
		t.Fatalf("got %d rules, want only the one that needs no data file", len(rules))
	}
	rule := rules[0].(map[string]any)
	if rule["outboundTag"] != "proxy" || rule["network"] != "tcp,udp" {
		t.Errorf("the surviving rule is not the plain one: %v", rule)
	}

	// The rest of the configuration is untouched.
	if cfg["dns"] == nil {
		t.Error("the dns section went with the rules")
	}
	if len(cfg["outbounds"].([]any)) != 3 {
		t.Error("outbounds were altered")
	}
}

func TestStripLeavesRulesThatMatchOnSomethingElse(t *testing.T) {
	// A rule mixing a geosite entry with a plain domain keeps the plain one.
	config := `{"outbounds":[{"tag":"vpn","protocol":"vless","settings":{"vnext":[{"address":"a.example","port":443,"users":[{"id":"u"}]}]}}],
	  "routing":{"rules":[
	    {"type":"field","domain":["geosite:ads","example.com"],"outboundTag":"vpn"},
	    {"type":"field","ip":["geoip:ru"],"outboundTag":"vpn"}]}}`

	out, err := Normalize([]byte(config), Defaults(10808))
	if err != nil {
		t.Fatalf("Normalize: %v", err)
	}
	var cfg map[string]any
	if err := json.Unmarshal(out, &cfg); err != nil {
		t.Fatal(err)
	}
	rules := cfg["routing"].(map[string]any)["rules"].([]any)
	if len(rules) != 1 {
		t.Fatalf("got %d rules, want the mixed one to survive alone", len(rules))
	}
	domains := rules[0].(map[string]any)["domain"].([]any)
	if len(domains) != 1 || domains[0] != "example.com" {
		t.Errorf("domains = %v, want just the plain entry", domains)
	}
}
