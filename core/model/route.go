package model

import (
	"errors"
	"fmt"
	"net"
	"strings"
)

// RouteAction is where a routing rule sends matching traffic.
type RouteAction string

const (
	RouteProxy  RouteAction = "proxy"
	RouteDirect RouteAction = "direct"
	RouteBlock  RouteAction = "block"
)

// Valid reports whether a is one of the three actions.
func (a RouteAction) Valid() bool {
	return a == RouteProxy || a == RouteDirect || a == RouteBlock
}

// RouteRule is one line of the routing screen: a domain, a domain suffix, an IP
// or a CIDR, and where it goes.
type RouteRule struct {
	Match  string      `json:"match"`
	Action RouteAction `json:"action"`
}

// Routing is the user's routing table. The zero value routes everything
// through the proxy, which is what NebulaLink did before rules existed.
type Routing struct {
	Rules []RouteRule `json:"rules,omitempty"`
	// Default is where traffic no rule matches goes; empty means proxy.
	Default RouteAction `json:"default,omitempty"`
	// TelegramFollowsRules is the explicit opt-out from the guarantee that the
	// messenger's own servers always go through the proxy. Off by default.
	TelegramFollowsRules bool `json:"telegram_follows_rules,omitempty"`
	// Preset names the preset the table was last built from, for the UI only.
	Preset string `json:"preset,omitempty"`
}

// DefaultAction resolves an empty default to proxy.
func (r Routing) DefaultAction() RouteAction {
	if r.Default.Valid() {
		return r.Default
	}
	return RouteProxy
}

// Active reports whether the table changes anything compared to "everything
// through the proxy". An inactive table leaves generated configs untouched.
func (r Routing) Active() bool {
	if r.DefaultAction() != RouteProxy {
		return true
	}
	for _, rule := range r.Rules {
		if rule.Action != RouteProxy {
			return true
		}
	}
	return false
}

// MatchKind tells domain rules from address rules.
type MatchKind int

const (
	MatchDomain MatchKind = iota
	MatchIP
)

// ParseRouteMatch turns what the user typed into the form Xray understands.
//
//	example.com, .example.com, *.example.com -> domain:example.com (suffix)
//	full:example.com, keyword:x, regexp:x, geosite:x  -> kept as written
//	1.2.3.4, 10.0.0.0/8, 2001:db8::/32, geoip:ru      -> address rules
//
// Non-ASCII domains (сайт.рф) are converted to punycode.
func ParseRouteMatch(raw string) (MatchKind, string, error) {
	value := strings.ToLower(strings.TrimSpace(raw))
	if value == "" {
		return 0, "", errors.New("routing: empty rule")
	}
	if strings.ContainsAny(value, " \t\r\n,\"") {
		return 0, "", fmt.Errorf("routing: %q holds more than one value", raw)
	}
	switch {
	case strings.HasPrefix(value, "geoip:"):
		if len(value) == len("geoip:") {
			return 0, "", fmt.Errorf("routing: %q names no country", raw)
		}
		return MatchIP, value, nil
	case strings.HasPrefix(value, "geosite:"):
		if len(value) == len("geosite:") {
			return 0, "", fmt.Errorf("routing: %q names no list", raw)
		}
		return MatchDomain, value, nil
	case strings.HasPrefix(value, "keyword:"), strings.HasPrefix(value, "regexp:"):
		if strings.HasSuffix(value, ":") {
			return 0, "", fmt.Errorf("routing: %q is empty", raw)
		}
		return MatchDomain, value, nil
	}
	if _, network, err := net.ParseCIDR(value); err == nil {
		return MatchIP, network.String(), nil
	}
	if ip := net.ParseIP(value); ip != nil {
		return MatchIP, ip.String(), nil
	}

	prefix := "domain:"
	switch {
	case strings.HasPrefix(value, "full:"):
		prefix, value = "full:", strings.TrimPrefix(value, "full:")
	case strings.HasPrefix(value, "domain:"):
		value = strings.TrimPrefix(value, "domain:")
	}
	value = strings.TrimPrefix(value, "*.")
	value = strings.Trim(value, ".")
	if value == "" {
		return 0, "", fmt.Errorf("routing: %q is not a domain", raw)
	}
	labels := strings.Split(value, ".")
	for i, label := range labels {
		ascii, err := punycodeLabel(label)
		if err != nil {
			return 0, "", fmt.Errorf("routing: %q: %w", raw, err)
		}
		if ascii == "" || len(ascii) > 63 || !validLabel(ascii) {
			return 0, "", fmt.Errorf("routing: %q is not a domain", raw)
		}
		labels[i] = ascii
	}
	value = strings.Join(labels, ".")
	if len(value) > 253 {
		return 0, "", fmt.Errorf("routing: %q is too long", raw)
	}
	return MatchDomain, prefix + value, nil
}

func validLabel(label string) bool {
	for _, r := range label {
		if !(r >= 'a' && r <= 'z' || r >= '0' && r <= '9' || r == '-' || r == '_') {
			return false
		}
	}
	return true
}

// punycodeLabel encodes one label per RFC 3492; ASCII labels pass through.
func punycodeLabel(label string) (string, error) {
	ascii := true
	for _, r := range label {
		if r >= 0x80 {
			ascii = false
			break
		}
	}
	if ascii {
		return label, nil
	}
	const (
		base, tmin, tmax, skew, damp = 36, 1, 26, 38, 700
		initialBias, initialN        = 72, 128
	)
	runes := []rune(label)
	var out []byte
	for _, r := range runes {
		if r < 0x80 {
			out = append(out, byte(r))
		}
	}
	basic := len(out)
	handled := basic
	if basic > 0 {
		out = append(out, '-')
	}
	digit := func(d int) byte {
		if d < 26 {
			return byte('a' + d)
		}
		return byte('0' + d - 26)
	}
	adapt := func(delta, points int, first bool) int {
		if first {
			delta /= damp
		} else {
			delta /= 2
		}
		delta += delta / points
		k := 0
		for delta > ((base-tmin)*tmax)/2 {
			delta /= base - tmin
			k += base
		}
		return k + (base-tmin+1)*delta/(delta+skew)
	}
	n, delta, bias := initialN, 0, initialBias
	for handled < len(runes) {
		m := int(^uint(0) >> 1)
		for _, r := range runes {
			if int(r) >= n && int(r) < m {
				m = int(r)
			}
		}
		if (m-n)*(handled+1) > 1<<30 {
			return "", errors.New("punycode overflow")
		}
		delta += (m - n) * (handled + 1)
		n = m
		for _, r := range runes {
			if int(r) < n {
				delta++
			}
			if int(r) == n {
				q := delta
				for k := base; ; k += base {
					t := k - bias
					if t < tmin {
						t = tmin
					} else if t > tmax {
						t = tmax
					}
					if q < t {
						break
					}
					out = append(out, digit(t+(q-t)%(base-t)))
					q = (q - t) / (base - t)
				}
				out = append(out, digit(q))
				bias = adapt(delta, handled+1, handled == basic)
				delta = 0
				handled++
			}
		}
		delta++
		n++
	}
	return "xn--" + string(out), nil
}

// TelegramDomains are the messenger's own web hosts.
var TelegramDomains = []string{
	"domain:telegram.org", "domain:t.me", "domain:telegram.me", "domain:telegram.dog",
	"domain:telegra.ph", "domain:telesco.pe", "domain:tdesktop.com", "domain:cdn-telegram.org",
	"domain:telegram-cdn.org", "domain:tg.dev", "domain:graph.org", "domain:fragment.com",
}

// TelegramCIDRs are the data-centre ranges Telegram publishes. The app talks to
// its servers by address, so these, not the domains, carry the messenger.
var TelegramCIDRs = []string{
	"91.105.192.0/23", "91.108.4.0/22", "91.108.8.0/22", "91.108.12.0/22",
	"91.108.16.0/22", "91.108.20.0/22", "91.108.56.0/22", "95.161.64.0/20",
	"149.154.160.0/20", "185.76.151.0/24",
	"2001:67c:4e8::/48", "2001:b28:f23c::/48", "2001:b28:f23d::/48",
	"2001:b28:f23f::/48", "2a0a:f280::/32",
}

// RoutePreset is a ready-made table offered on the routing screen.
type RoutePreset struct {
	ID      string  `json:"id"`
	TitleRU string  `json:"title_ru"`
	TitleEN string  `json:"title_en"`
	Routing Routing `json:"routing"`
}

// Preset identifiers.
const (
	PresetRUDirect     = "ru_direct"
	PresetTelegramOnly = "telegram_only"
	PresetAllProxy     = "all_proxy"
)

// RoutePresets lists the presets in display order.
func RoutePresets() []RoutePreset {
	return []RoutePreset{
		{ID: PresetRUDirect, TitleRU: "RU-сайты напрямую", TitleEN: "Russian sites direct",
			Routing: Routing{Preset: PresetRUDirect, Rules: []RouteRule{
				{Match: "ru", Action: RouteDirect},
				{Match: "xn--p1ai", Action: RouteDirect},
				{Match: "su", Action: RouteDirect},
				{Match: "geoip:ru", Action: RouteDirect},
			}}},
		{ID: PresetTelegramOnly, TitleRU: "Только Telegram через прокси", TitleEN: "Only Telegram through the proxy",
			Routing: Routing{Preset: PresetTelegramOnly, Default: RouteDirect}},
		{ID: PresetAllProxy, TitleRU: "Всё через прокси", TitleEN: "Everything through the proxy",
			Routing: Routing{Preset: PresetAllProxy}},
	}
}

// FindRoutePreset returns the preset with the given id.
func FindRoutePreset(id string) (Routing, bool) {
	for _, p := range RoutePresets() {
		if p.ID == id {
			return p.Routing, true
		}
	}
	return Routing{}, false
}

// Clean validates every rule and returns the table in canonical form. The
// stored match keeps what the user typed (trimmed), so the screen shows it back
// unchanged; translation to Xray happens at build time.
func (r Routing) Clean() (Routing, error) {
	out := Routing{Default: r.Default, TelegramFollowsRules: r.TelegramFollowsRules, Preset: r.Preset}
	if out.Default != "" && !out.Default.Valid() {
		return Routing{}, fmt.Errorf("routing: unknown default action %q", r.Default)
	}
	if out.Default == RouteProxy {
		out.Default = ""
	}
	if len(r.Rules) > 500 {
		return Routing{}, errors.New("routing: too many rules")
	}
	for _, rule := range r.Rules {
		if !rule.Action.Valid() {
			return Routing{}, fmt.Errorf("routing: unknown action %q", rule.Action)
		}
		if _, _, err := ParseRouteMatch(rule.Match); err != nil {
			return Routing{}, err
		}
		out.Rules = append(out.Rules, RouteRule{Match: strings.TrimSpace(rule.Match), Action: rule.Action})
	}
	return out, nil
}
