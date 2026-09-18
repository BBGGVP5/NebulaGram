package xray

import (
	"encoding/json"
	"errors"
	"strings"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/xraycfg"
	"github.com/xtls/xray-core/infra/conf"
)

// Only the selected outbound is built. No user Env, inbound/TUN, DNS or
// routing is loaded into the process. The active core remains authoritative.
func probeOutbound(s model.Server, tag string) (*conf.OutboundDetourConfig, error) {
	if s.Engine() != model.EngineXray {
		return nil, errors.New("NIMBO_UNSUPPORTED_CORE")
	}
	var candidates []json.RawMessage
	if s.Config != "" {
		if len(s.Config) > 2*1024*1024 {
			return nil, errors.New("NIMBO_CONFIG_TOO_LARGE")
		}
		var document struct {
			Outbounds []json.RawMessage `json:"outbounds"`
		}
		if json.Unmarshal([]byte(s.Config), &document) != nil || len(document.Outbounds) == 0 || len(document.Outbounds) > 128 {
			return nil, errors.New("NIMBO_CONFIG")
		}
		candidates = document.Outbounds
	} else {
		out, err := xraycfg.BuildOutbound(s)
		if err != nil {
			return nil, errors.New("NIMBO_UNSUPPORTED_PROTOCOL")
		}
		raw, err := json.Marshal(out)
		if err != nil {
			return nil, errors.New("NIMBO_CONFIG")
		}
		candidates = []json.RawMessage{raw}
	}
	var selected []json.RawMessage
	for _, raw := range candidates {
		var identity struct {
			Protocol string `json:"protocol"`
			Tag      string `json:"tag"`
		}
		if json.Unmarshal(raw, &identity) != nil {
			return nil, errors.New("NIMBO_CONFIG")
		}
		if s.ConfigTag != "" {
			if identity.Tag == s.ConfigTag {
				selected = append(selected, raw)
			}
			continue
		}
		switch identity.Protocol {
		case "freedom", "blackhole", "dns", "loopback":
		default:
			selected = append(selected, raw)
		}
	}
	if len(selected) != 1 {
		return nil, errors.New("NIMBO_AMBIGUOUS_ROUTE")
	}
	var out conf.OutboundDetourConfig
	if json.Unmarshal(selected[0], &out) != nil {
		return nil, errors.New("NIMBO_CONFIG")
	}
	switch out.Protocol {
	case "vless", "vmess", "trojan", "shadowsocks", "socks", "http":
	default:
		return nil, errors.New("NIMBO_UNSUPPORTED_PROTOCOL")
	}
	var raw any
	if json.Unmarshal(selected[0], &raw) != nil || unsafeProbeFields(raw) || out.ProxySettings != nil {
		return nil, errors.New("NIMBO_UNSAFE_ROUTE")
	}
	// sendThrough is a real native bind, not a display label. Preserve it;
	// outbound.Build validates it. Transport/Reality/mux settings stay intact.
	out.Tag = tag
	return &out, nil
}

func unsafeProbeFields(value any) bool {
	switch v := value.(type) {
	case map[string]any:
		for key, child := range v {
			switch strings.ToLower(key) {
			case "dialerproxy", "certificatefile", "keyfile", "masterkeylog", "secretslog":
				if child != nil && child != "" {
					return true
				}
			}
			if unsafeProbeFields(child) {
				return true
			}
		}
	case []any:
		for _, child := range v {
			if unsafeProbeFields(child) {
				return true
			}
		}
	}
	return false
}
