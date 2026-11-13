package xraycfg

import (
	"strings"

	"github.com/nebulagram/nebulagram/core/model"
)

// Outbound tags the routing rules point at.
type routeTags struct {
	proxy, direct, block string
}

var builtTags = routeTags{proxy: "proxy", direct: "direct", block: "block"}

func (t routeTags) of(a model.RouteAction) string {
	switch a {
	case model.RouteDirect:
		return t.direct
	case model.RouteBlock:
		return t.block
	default:
		return t.proxy
	}
}

// RoutingRules translates the user's table into Xray routing rules, in the
// order Xray evaluates them (first match wins):
//
//  1. Telegram's own domains and data centres go through the proxy, unless the
//     user explicitly let Telegram follow the rules. Without this a preset such
//     as "RU sites direct" or a catch-all "direct" would take the messenger out
//     of the tunnel, which is the one thing NebulaLink exists to prevent.
//  2. The user's rules, one Xray rule each, in the order on screen.
//  3. A catch-all for the default action when it is not "proxy" — the first
//     outbound is Xray's default, and that is the proxy.
//
// geoip:/geosite: rules are dropped when the data files are missing, because
// Xray refuses to load a configuration that mentions them.
func RoutingRules(r model.Routing, geoAssets bool) []any {
	return routingRules(r, geoAssets, builtTags)
}

func routingRules(r model.Routing, geoAssets bool, tags routeTags) []any {
	if !r.Active() {
		return nil
	}
	var rules []any
	if !r.TelegramFollowsRules {
		rules = append(rules,
			map[string]any{"type": "field", "outboundTag": tags.proxy, "domain": append([]string(nil), model.TelegramDomains...)},
			map[string]any{"type": "field", "outboundTag": tags.proxy, "ip": append([]string(nil), model.TelegramCIDRs...)},
		)
	}
	for _, rule := range r.Rules {
		kind, value, err := model.ParseRouteMatch(rule.Match)
		if err != nil || !rule.Action.Valid() {
			continue // Clean refuses these on the way in; never fail a build on them.
		}
		if !geoAssets && (strings.HasPrefix(value, "geoip:") || strings.HasPrefix(value, "geosite:")) {
			continue
		}
		field := "domain"
		if kind == model.MatchIP {
			field = "ip"
		}
		rules = append(rules, map[string]any{"type": "field", "outboundTag": tags.of(rule.Action), field: []string{value}})
	}
	if def := r.DefaultAction(); def != model.RouteProxy {
		rules = append(rules, map[string]any{"type": "field", "outboundTag": tags.of(def), "network": "tcp,udp"})
	}
	return rules
}

// applyRouting grafts the user's table onto a pasted configuration. Its own
// rules stay, after ours: the user's screen is the newer decision. Profiles
// with balancers are left alone, since there is no single proxy tag to target.
func applyRouting(cfg map[string]any, r model.Routing, geoAssets bool) {
	if !r.Active() || hasBalancers(cfg) {
		return
	}
	outbounds, _ := cfg["outbounds"].([]any)
	tags := routeTags{proxy: "proxy", direct: "nl-direct", block: "nl-block"}
	present := map[string]bool{}
	for _, raw := range outbounds {
		if out, ok := raw.(map[string]any); ok {
			if tag, _ := out["tag"].(string); tag != "" {
				present[tag] = true
			}
		}
	}
	if !present["proxy"] {
		return // renameProxyOutbound found nothing to call the proxy
	}
	rules := routingRules(r, geoAssets, tags)
	if len(rules) == 0 {
		return
	}
	if !present[tags.direct] {
		out := directOutbound()
		out["tag"] = tags.direct
		outbounds = append(outbounds, out)
	}
	if !present[tags.block] {
		out := blockOutbound()
		out["tag"] = tags.block
		outbounds = append(outbounds, out)
	}
	cfg["outbounds"] = outbounds

	routing, _ := cfg["routing"].(map[string]any)
	if routing == nil {
		routing = map[string]any{"domainStrategy": "AsIs"}
	}
	// Rules bound to an inbound (a DNS inbound, an API inbound) describe
	// plumbing, not destinations, and must keep seeing their traffic before
	// a catch-all of ours could take it.
	existing, _ := routing["rules"].([]any)
	var plumbing, rest []any
	for _, raw := range existing {
		if rule, ok := raw.(map[string]any); ok && rule["inboundTag"] != nil {
			plumbing = append(plumbing, raw)
		} else {
			rest = append(rest, raw)
		}
	}
	merged := append(plumbing, rules...)
	routing["rules"] = append(merged, rest...)
	cfg["routing"] = routing
}
