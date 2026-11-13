package model

import "testing"

func TestParseRouteMatch(t *testing.T) {
	cases := []struct {
		in   string
		kind MatchKind
		want string
	}{
		{"example.com", MatchDomain, "domain:example.com"},
		{" *.Example.COM ", MatchDomain, "domain:example.com"},
		{".example.com", MatchDomain, "domain:example.com"},
		{"ru", MatchDomain, "domain:ru"},
		{"рф", MatchDomain, "domain:xn--p1ai"},
		{"пример.рф", MatchDomain, "domain:xn--e1afmkfd.xn--p1ai"},
		{"full:api.example.com", MatchDomain, "full:api.example.com"},
		{"keyword:ads", MatchDomain, "keyword:ads"},
		{"geosite:category-ads", MatchDomain, "geosite:category-ads"},
		{"10.1.2.3/8", MatchIP, "10.0.0.0/8"},
		{"1.1.1.1", MatchIP, "1.1.1.1"},
		{"2001:db8::/32", MatchIP, "2001:db8::/32"},
		{"geoip:ru", MatchIP, "geoip:ru"},
	}
	for _, tc := range cases {
		kind, got, err := ParseRouteMatch(tc.in)
		if err != nil || kind != tc.kind || got != tc.want {
			t.Errorf("ParseRouteMatch(%q) = %v %q %v, want %v %q", tc.in, kind, got, err, tc.kind, tc.want)
		}
	}
	for _, bad := range []string{"", "  ", "a b", "a,b", "exa!mple.com", "geoip:", "*.", "full:"} {
		if _, _, err := ParseRouteMatch(bad); err == nil {
			t.Errorf("ParseRouteMatch(%q) accepted", bad)
		}
	}
}

func TestRoutingCleanAndActive(t *testing.T) {
	if (Routing{}).Active() {
		t.Error("an empty table must not be active")
	}
	if (Routing{Rules: []RouteRule{{Match: "a.com", Action: RouteProxy}}}).Active() {
		t.Error("proxy-only rules change nothing")
	}
	if !(Routing{Default: RouteDirect}).Active() {
		t.Error("a direct default is active")
	}
	if _, err := (Routing{Rules: []RouteRule{{Match: "a.com", Action: "vpn"}}}).Clean(); err == nil {
		t.Error("unknown action accepted")
	}
	if _, err := (Routing{Default: "elsewhere"}).Clean(); err == nil {
		t.Error("unknown default accepted")
	}
	clean, err := (Routing{Default: RouteProxy, Rules: []RouteRule{{Match: "  a.com ", Action: RouteDirect}}}).Clean()
	if err != nil || clean.Default != "" || clean.Rules[0].Match != "a.com" {
		t.Errorf("Clean = %+v, %v", clean, err)
	}
	for _, p := range RoutePresets() {
		if _, err := p.Routing.Clean(); err != nil {
			t.Errorf("preset %s does not validate: %v", p.ID, err)
		}
	}
}
