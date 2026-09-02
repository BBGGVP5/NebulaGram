package xraycfg

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

// LooksLikeConfig reports whether a payload is a complete Xray configuration
// rather than a share link or a subscription profile: it has outbounds written
// the Xray way, with a "protocol" field (sing-box calls that field "type").
func LooksLikeConfig(raw []byte) bool {
	trimmed := strings.TrimSpace(string(raw))
	if !strings.HasPrefix(trimmed, "{") {
		return false
	}
	var probe struct {
		Outbounds []struct {
			Protocol string `json:"protocol"`
			Type     string `json:"type"`
		} `json:"outbounds"`
	}
	if err := json.Unmarshal(raw, &probe); err != nil {
		return false
	}
	for _, outbound := range probe.Outbounds {
		if outbound.Protocol != "" && outbound.Type == "" {
			return true
		}
	}
	return false
}

// Normalize adapts a user-supplied Xray configuration so NebulaLink can run it:
// it puts our local SOCKS inbound in front, names the proxy outbound "proxy" so
// the traffic counters find it, and turns statistics on. Everything else the
// user wrote — routing, DNS, balancers, fragmentation — is left untouched.
func Normalize(raw []byte, o Options) ([]byte, error) {
	if o.SocksPort == 0 {
		return nil, errors.New("xraycfg: socks port is required")
	}
	if o.ListenAddr == "" {
		o.ListenAddr = "127.0.0.1"
	}

	var cfg map[string]any
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return nil, fmt.Errorf("xraycfg: cannot parse the configuration: %w", err)
	}
	if _, ok := cfg["outbounds"]; !ok {
		return nil, errors.New("xraycfg: the configuration has no outbounds")
	}

	if _, ok := cfg["log"]; !ok && o.LogLevel != "" {
		cfg["log"] = map[string]any{"loglevel": o.LogLevel}
	}
	if !o.GeoAssets {
		// Panels routinely ship rules like geoip:ru or geosite:vk, and Xray
		// refuses to start the whole configuration when the data files are
		// missing — not just the rule. Dropping those rules keeps the server
		// usable; everything they would have sent direct goes through the
		// tunnel instead, which for a messenger is the safer default anyway.
		stripGeoRules(cfg)
	}
	cfg["inbounds"] = mergeInbounds(cfg["inbounds"], o)

	if tag := renameProxyOutbound(cfg); tag != "" && o.StatsAPI {
		enableStats(cfg)
	}
	return json.MarshalIndent(cfg, "", "  ")
}

// mergeInbounds drops whatever local listeners the config carried — they would
// fight ours for the port — and keeps any other inbound the user declared.
func mergeInbounds(existing any, o Options) []any {
	inbounds := buildInbounds(o)
	list, ok := existing.([]any)
	if !ok {
		return inbounds
	}
	for _, item := range list {
		inbound, ok := item.(map[string]any)
		if !ok {
			continue
		}
		switch inbound["protocol"] {
		case "socks", "http", "mixed", "dokodemo-door":
			continue
		}
		inbounds = append(inbounds, inbound)
	}
	return inbounds
}

// renameProxyOutbound gives the first real proxy outbound the tag "proxy" and
// rewrites every reference to its old tag, so routing keeps working while the
// stats counters land on a name we know. Returns the tag in use.
func renameProxyOutbound(cfg map[string]any) string {
	outbounds, ok := cfg["outbounds"].([]any)
	if !ok {
		return ""
	}
	for _, item := range outbounds {
		outbound, ok := item.(map[string]any)
		if !ok {
			continue
		}
		protocol, _ := outbound["protocol"].(string)
		switch protocol {
		case "", "freedom", "blackhole", "dns", "loopback":
			continue
		}
		previous, _ := outbound["tag"].(string)
		if previous == "proxy" {
			return "proxy"
		}
		outbound["tag"] = "proxy"
		if previous != "" {
			retagRouting(cfg, previous, "proxy")
		}
		return "proxy"
	}
	return ""
}

// retagRouting follows a renamed outbound through the routing section.
func retagRouting(cfg map[string]any, from, to string) {
	routing, ok := cfg["routing"].(map[string]any)
	if !ok {
		return
	}
	if rules, ok := routing["rules"].([]any); ok {
		for _, item := range rules {
			rule, ok := item.(map[string]any)
			if !ok {
				continue
			}
			if tag, _ := rule["outboundTag"].(string); tag == from {
				rule["outboundTag"] = to
			}
		}
	}
	if balancers, ok := routing["balancers"].([]any); ok {
		for _, item := range balancers {
			balancer, ok := item.(map[string]any)
			if !ok {
				continue
			}
			selectors, ok := balancer["selector"].([]any)
			if !ok {
				continue
			}
			for i, selector := range selectors {
				if text, _ := selector.(string); text == from {
					selectors[i] = to
				}
			}
		}
	}
}

func enableStats(cfg map[string]any) {
	if _, ok := cfg["stats"]; !ok {
		cfg["stats"] = map[string]any{}
	}
	policy, ok := cfg["policy"].(map[string]any)
	if !ok {
		policy = map[string]any{}
	}
	system, ok := policy["system"].(map[string]any)
	if !ok {
		system = map[string]any{}
	}
	system["statsOutboundUplink"] = true
	system["statsOutboundDownlink"] = true
	policy["system"] = system
	cfg["policy"] = policy
}

// ProbeTarget reports the endpoint of the proxy outbound, so a pasted config
// can be latency-checked like any other server. Returns an empty host when the
// outbound shape is one we do not recognise.
func ProbeTarget(raw []byte) (host string, port int) {
	var cfg struct {
		Outbounds []json.RawMessage `json:"outbounds"`
	}
	if err := json.Unmarshal(raw, &cfg); err != nil {
		return "", 0
	}
	for _, outbound := range cfg.Outbounds {
		server, err := ParseOutbound(outbound)
		if err != nil {
			continue
		}
		return server.Address, server.Port
	}
	return "", 0
}

// ConfigName reads the display name from the fields panels commonly add to an
// exported configuration.
func ConfigName(raw []byte) string {
	var named struct {
		Remarks string `json:"remarks"`
		Name    string `json:"name"`
		Tag     string `json:"tag"`
	}
	if err := json.Unmarshal(raw, &named); err != nil {
		return ""
	}
	for _, candidate := range []string{named.Remarks, named.Name, named.Tag} {
		if strings.TrimSpace(candidate) != "" {
			return candidate
		}
	}
	return ""
}

// geoPrefixes are the matchers that need geoip.dat or geosite.dat on disk.
var geoPrefixes = []string{"geosite:", "geoip:", "ext:"}

func needsGeoData(value string) bool {
	for _, prefix := range geoPrefixes {
		if strings.HasPrefix(value, prefix) {
			return true
		}
	}
	return false
}

// stripGeoRules removes every matcher that would need a data file, and then
// drops rules left with nothing to match on. Returns how many rules went.
func stripGeoRules(cfg map[string]any) int {
	removed := 0
	if routing, ok := cfg["routing"].(map[string]any); ok {
		if rules, ok := routing["rules"].([]any); ok {
			kept := make([]any, 0, len(rules))
			for _, item := range rules {
				rule, ok := item.(map[string]any)
				if !ok {
					kept = append(kept, item)
					continue
				}
				for _, field := range []string{"domain", "ip", "source"} {
					if list, ok := rule[field].([]any); ok {
						filtered := filterGeo(list)
						if len(filtered) == 0 {
							delete(rule, field)
						} else {
							rule[field] = filtered
						}
					}
				}
				if hasMatcher(rule) {
					kept = append(kept, rule)
				} else {
					removed++
				}
			}
			routing["rules"] = kept
		}
	}
	// A DNS server can be scoped to a geosite list as well.
	if dns, ok := cfg["dns"].(map[string]any); ok {
		if servers, ok := dns["servers"].([]any); ok {
			for _, item := range servers {
				server, ok := item.(map[string]any)
				if !ok {
					continue
				}
				for _, field := range []string{"domains", "expectIPs"} {
					if list, ok := server[field].([]any); ok {
						if filtered := filterGeo(list); len(filtered) == 0 {
							delete(server, field)
						} else {
							server[field] = filtered
						}
					}
				}
			}
		}
	}
	return removed
}

func filterGeo(list []any) []any {
	kept := make([]any, 0, len(list))
	for _, entry := range list {
		if text, ok := entry.(string); ok && needsGeoData(text) {
			continue
		}
		kept = append(kept, entry)
	}
	return kept
}

// hasMatcher reports whether a routing rule still selects anything. A rule with
// only an outboundTag left would send every connection down that outbound.
func hasMatcher(rule map[string]any) bool {
	for _, field := range []string{
		"domain", "ip", "port", "sourcePort", "network", "source",
		"user", "inboundTag", "protocol", "attrs", "domainMatcher",
	} {
		switch value := rule[field].(type) {
		case []any:
			if len(value) > 0 {
				return true
			}
		case string:
			if value != "" {
				return true
			}
		case nil:
		default:
			return true
		}
	}
	return false
}
