package xraycfg

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/nebulagram/nebulagram/core/model"
)

type rule struct {
	OutboundTag string   `json:"outboundTag"`
	Domain      []string `json:"domain"`
	IP          []string `json:"ip"`
	Network     string   `json:"network"`
	Protocol    []string `json:"protocol"`
	InboundTag  []string `json:"inboundTag"`
}

func builtRules(t *testing.T, r model.Routing, geo bool) []rule {
	t.Helper()
	o := Defaults(10808)
	o.Routing, o.GeoAssets = r, geo
	raw, err := Build(model.Server{Protocol: model.VLESS, Address: "x.example.com", Port: 443, UUID: "u"}, o)
	if err != nil {
		t.Fatal(err)
	}
	var cfg struct {
		Routing struct{ Rules []rule } `json:"routing"`
	}
	if err := json.Unmarshal(raw, &cfg); err != nil {
		t.Fatal(err)
	}
	return cfg.Routing.Rules
}

func TestBuildWithoutRoutingIsUnchanged(t *testing.T) {
	rules := builtRules(t, model.Routing{}, true)
	if len(rules) != 1 || rules[0].OutboundTag != "block" || rules[0].Protocol[0] != "bittorrent" {
		t.Fatalf("rules = %+v, want only the bittorrent block", rules)
	}
}

func TestBuildTranslatesRulesInOrder(t *testing.T) {
	rules := builtRules(t, model.Routing{Rules: []model.RouteRule{
		{Match: "example.ru", Action: model.RouteDirect},
		{Match: "10.0.0.0/8", Action: model.RouteDirect},
		{Match: "ads.example.com", Action: model.RouteBlock},
		{Match: "work.example.com", Action: model.RouteProxy},
	}}, true)
	// bittorrent, telegram domains, telegram ranges, then the four rules.
	if len(rules) != 7 {
		t.Fatalf("got %d rules: %+v", len(rules), rules)
	}
	if rules[1].OutboundTag != "proxy" || !contains(rules[1].Domain, "domain:telegram.org") {
		t.Errorf("telegram domains must come first through the proxy: %+v", rules[1])
	}
	if rules[2].OutboundTag != "proxy" || !contains(rules[2].IP, "149.154.160.0/20") {
		t.Errorf("telegram ranges must come first through the proxy: %+v", rules[2])
	}
	want := []rule{
		{OutboundTag: "direct", Domain: []string{"domain:example.ru"}},
		{OutboundTag: "direct", IP: []string{"10.0.0.0/8"}},
		{OutboundTag: "block", Domain: []string{"domain:ads.example.com"}},
		{OutboundTag: "proxy", Domain: []string{"domain:work.example.com"}},
	}
	for i, w := range want {
		got := rules[3+i]
		if got.OutboundTag != w.OutboundTag || strings.Join(got.Domain, ",") != strings.Join(w.Domain, ",") ||
			strings.Join(got.IP, ",") != strings.Join(w.IP, ",") {
			t.Errorf("rule %d = %+v, want %+v", i, got, w)
		}
	}
}

func TestTelegramOnlyPreset(t *testing.T) {
	preset, _ := model.FindRoutePreset(model.PresetTelegramOnly)
	rules := builtRules(t, preset, false)
	last := rules[len(rules)-1]
	if last.OutboundTag != "direct" || last.Network != "tcp,udp" {
		t.Errorf("the default must end in a direct catch-all: %+v", last)
	}
	if rules[1].OutboundTag != "proxy" || rules[2].OutboundTag != "proxy" {
		t.Error("telegram must stay proxied ahead of the catch-all")
	}
}

func TestTelegramCanFollowRulesWhenAsked(t *testing.T) {
	rules := builtRules(t, model.Routing{Default: model.RouteDirect, TelegramFollowsRules: true}, false)
	for _, r := range rules {
		if contains(r.IP, "149.154.160.0/20") {
			t.Fatal("telegram was forced through the proxy after the user opted out")
		}
	}
}

func TestGeoRulesNeedAssets(t *testing.T) {
	preset, _ := model.FindRoutePreset(model.PresetRUDirect)
	withGeo := builtRules(t, preset, true)
	without := builtRules(t, preset, false)
	if len(withGeo) != len(without)+1 {
		t.Fatalf("geoip:ru should be dropped without assets: %d vs %d", len(withGeo), len(without))
	}
	if !contains(withGeo[len(withGeo)-1].IP, "geoip:ru") {
		t.Errorf("geoip:ru missing: %+v", withGeo[len(withGeo)-1])
	}
	if !contains(without[4].Domain, "domain:xn--p1ai") {
		t.Errorf(".рф rule missing: %+v", without)
	}
}

func TestNormalizeAppliesRoutingToPastedConfig(t *testing.T) {
	raw := `{"outbounds":[{"tag":"main","protocol":"vless","settings":{}},{"tag":"direct","protocol":"freedom"}],
	"routing":{"rules":[{"type":"field","inboundTag":["dns-in"],"outboundTag":"direct"},{"type":"field","domain":["domain:x.com"],"outboundTag":"main"}]}}`
	o := Defaults(10808)
	o.Routing = model.Routing{Rules: []model.RouteRule{{Match: "ads.com", Action: model.RouteBlock}}, Default: model.RouteDirect}
	out, err := Normalize([]byte(raw), o)
	if err != nil {
		t.Fatal(err)
	}
	var cfg struct {
		Outbounds []struct{ Tag, Protocol string } `json:"outbounds"`
		Routing   struct{ Rules []rule }           `json:"routing"`
	}
	if err := json.Unmarshal(out, &cfg); err != nil {
		t.Fatal(err)
	}
	tags := map[string]string{}
	for _, ob := range cfg.Outbounds {
		tags[ob.Tag] = ob.Protocol
	}
	if tags["nl-direct"] != "freedom" || tags["nl-block"] != "blackhole" || tags["proxy"] != "vless" {
		t.Fatalf("outbounds = %+v", cfg.Outbounds)
	}
	r := cfg.Routing.Rules
	if len(r) != 6 {
		t.Fatalf("rules = %+v", r)
	}
	if len(r[0].InboundTag) == 0 {
		t.Errorf("inbound plumbing must stay first: %+v", r[0])
	}
	if r[1].OutboundTag != "proxy" || r[3].OutboundTag != "nl-block" || r[4].OutboundTag != "nl-direct" || r[4].Network == "" {
		t.Errorf("rules = %+v", r)
	}
	if r[5].OutboundTag != "proxy" || !contains(r[5].Domain, "domain:x.com") {
		t.Errorf("the panel's own rule must follow ours, retagged: %+v", r[5])
	}
}

func TestNormalizeWithoutRoutingKeepsPanelRules(t *testing.T) {
	raw := `{"outbounds":[{"tag":"main","protocol":"vless","settings":{}}],"routing":{"rules":[{"type":"field","domain":["x.com"],"outboundTag":"main"}]}}`
	out, err := Normalize([]byte(raw), Defaults(10808))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(out), "nl-direct") || strings.Contains(string(out), "149.154.160.0/20") {
		t.Error("an empty table must not touch a pasted configuration")
	}
}

func contains(list []string, v string) bool {
	for _, item := range list {
		if item == v {
			return true
		}
	}
	return false
}
