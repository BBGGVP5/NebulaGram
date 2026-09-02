// Package xraycfg turns a model.Server into an Xray-core configuration, and
// reads servers back out of a v2ray-json subscription profile.
//
// The generated config always exposes a local SOCKS5 (and optional HTTP)
// inbound: that is the proxy mode every NebulaGram client uses by default. VPN
// mode reuses the same config and adds a tun inbound on the platform side.
package xraycfg

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/nebulagram/nebulagram/core/model"
)

// Options controls the local side of the generated config.
type Options struct {
	SocksPort  int    // required
	HTTPPort   int    // 0 = no http inbound
	ListenAddr string // default 127.0.0.1
	EnableUDP  bool   // udp over socks, needed for calls
	Sniffing   bool   // domain sniffing for routing/DNS
	LogLevel   string // none|error|warning|info|debug
	StatsAPI   bool   // expose the local stats API for the traffic counters
	DNS        string // upstream DNS used inside the tunnel, e.g. "1.1.1.1"
}

// Defaults returns the options NebulaGram ships with.
func Defaults(socksPort int) Options {
	return Options{
		SocksPort:  socksPort,
		ListenAddr: "127.0.0.1",
		EnableUDP:  true,
		Sniffing:   true,
		LogLevel:   "warning",
		StatsAPI:   true,
		DNS:        "1.1.1.1",
	}
}

// Build renders the complete Xray JSON config for one server.
func Build(s model.Server, o Options) ([]byte, error) {
	if s.Engine() != model.EngineXray {
		return nil, fmt.Errorf("xraycfg: %s is handled by sing-box, not xray", s.Protocol)
	}
	if o.SocksPort == 0 {
		return nil, errors.New("xraycfg: socks port is required")
	}
	if o.ListenAddr == "" {
		o.ListenAddr = "127.0.0.1"
	}
	if o.LogLevel == "" {
		o.LogLevel = "warning"
	}

	outbound, err := BuildOutbound(s)
	if err != nil {
		return nil, err
	}

	cfg := map[string]any{
		"log":       map[string]any{"loglevel": o.LogLevel},
		"inbounds":  buildInbounds(o),
		"outbounds": []any{outbound, directOutbound(), blockOutbound()},
		"routing": map[string]any{
			"domainStrategy": "IPIfNonMatch",
			"rules": []any{
				map[string]any{"type": "field", "outboundTag": "block", "protocol": []string{"bittorrent"}},
			},
		},
	}
	if o.DNS != "" {
		cfg["dns"] = map[string]any{"servers": []any{o.DNS, "localhost"}}
	}
	if o.StatsAPI {
		cfg["stats"] = map[string]any{}
		cfg["policy"] = map[string]any{
			"system": map[string]any{"statsOutboundUplink": true, "statsOutboundDownlink": true},
		}
	}
	return json.MarshalIndent(cfg, "", "  ")
}

func buildInbounds(o Options) []any {
	socks := map[string]any{
		"tag":      "socks-in",
		"listen":   o.ListenAddr,
		"port":     o.SocksPort,
		"protocol": "socks",
		"settings": map[string]any{"auth": "noauth", "udp": o.EnableUDP},
	}
	if o.Sniffing {
		socks["sniffing"] = map[string]any{
			"enabled":      true,
			"destOverride": []string{"http", "tls", "quic"},
			"routeOnly":    false,
		}
	}
	inbounds := []any{socks}
	if o.HTTPPort > 0 {
		inbounds = append(inbounds, map[string]any{
			"tag":      "http-in",
			"listen":   o.ListenAddr,
			"port":     o.HTTPPort,
			"protocol": "http",
		})
	}
	return inbounds
}

func directOutbound() map[string]any {
	return map[string]any{"tag": "direct", "protocol": "freedom", "settings": map[string]any{}}
}

func blockOutbound() map[string]any {
	return map[string]any{"tag": "block", "protocol": "blackhole", "settings": map[string]any{}}
}

// BuildOutbound renders just the proxy outbound; the tunnel manager reuses it
// for latency probes that must not spin up a full instance.
func BuildOutbound(s model.Server) (map[string]any, error) {
	out := map[string]any{"tag": "proxy", "protocol": string(s.Protocol)}

	switch s.Protocol {
	case model.VLESS:
		user := map[string]any{"id": s.UUID, "encryption": "none"}
		if s.Flow != "" {
			user["flow"] = s.Flow
		}
		out["settings"] = vnextSettings(s, user)
	case model.VMess:
		user := map[string]any{"id": s.UUID, "alterId": s.AlterID, "security": "auto"}
		out["settings"] = vnextSettings(s, user)
	case model.Trojan:
		out["settings"] = map[string]any{"servers": []any{map[string]any{
			"address": s.Address, "port": s.Port, "password": s.Password,
		}}}
	case model.Shadowsocks:
		out["settings"] = map[string]any{"servers": []any{map[string]any{
			"address": s.Address, "port": s.Port, "method": s.Method, "password": s.Password,
		}}}
	default:
		return nil, fmt.Errorf("xraycfg: unsupported protocol %q", s.Protocol)
	}

	if stream := buildStream(s); len(stream) > 0 {
		out["streamSettings"] = stream
	}
	return out, nil
}

func vnextSettings(s model.Server, user map[string]any) map[string]any {
	return map[string]any{"vnext": []any{map[string]any{
		"address": s.Address,
		"port":    s.Port,
		"users":   []any{user},
	}}}
}

func buildStream(s model.Server) map[string]any {
	stream := map[string]any{}
	network := s.Network
	if network == "" {
		network = "tcp"
	}
	stream["network"] = network

	switch network {
	case "ws":
		ws := map[string]any{"path": orDefault(s.Path, "/")}
		if s.Host != "" {
			ws["host"] = s.Host
		}
		stream["wsSettings"] = ws
	case "httpupgrade":
		hu := map[string]any{"path": orDefault(s.Path, "/")}
		if s.Host != "" {
			hu["host"] = s.Host
		}
		stream["httpupgradeSettings"] = hu
	case "xhttp", "splithttp":
		stream["network"] = "xhttp"
		xh := map[string]any{"path": orDefault(s.Path, "/")}
		if s.Host != "" {
			xh["host"] = s.Host
		}
		if s.Mode != "" {
			xh["mode"] = s.Mode
		}
		stream["xhttpSettings"] = xh
	case "grpc":
		grpc := map[string]any{"serviceName": s.ServiceName}
		if s.Mode == "multi" {
			grpc["multiMode"] = true
		}
		stream["grpcSettings"] = grpc
	case "h2", "http":
		stream["network"] = "http"
		h2 := map[string]any{"path": orDefault(s.Path, "/")}
		if s.Host != "" {
			h2["host"] = splitCSV(s.Host)
		}
		stream["httpSettings"] = h2
	case "tcp":
		if s.HeaderType == "http" {
			stream["tcpSettings"] = map[string]any{"header": map[string]any{
				"type": "http",
				"request": map[string]any{
					"path":    []string{orDefault(s.Path, "/")},
					"headers": map[string]any{"Host": splitCSV(s.Host)},
				},
			}}
		}
	}

	switch s.Security {
	case "tls":
		stream["security"] = "tls"
		stream["tlsSettings"] = tlsSettings(s)
	case "reality":
		stream["security"] = "reality"
		reality := map[string]any{
			"serverName":  orDefault(s.SNI, s.Address),
			"publicKey":   s.PublicKey,
			"shortId":     s.ShortID,
			"fingerprint": orDefault(s.Fingerprint, "chrome"),
		}
		if s.SpiderX != "" {
			reality["spiderX"] = s.SpiderX
		}
		stream["realitySettings"] = reality
	}
	return stream
}

func tlsSettings(s model.Server) map[string]any {
	tls := map[string]any{
		"serverName":  orDefault(s.SNI, orDefault(s.Host, s.Address)),
		"fingerprint": orDefault(s.Fingerprint, "chrome"),
	}
	if s.ALPN != "" {
		tls["alpn"] = splitCSV(s.ALPN)
	}
	if s.AllowInsecure {
		tls["allowInsecure"] = true
	}
	return tls
}

func orDefault(v, fallback string) string {
	if strings.TrimSpace(v) == "" {
		return fallback
	}
	return v
}

func splitCSV(v string) []string {
	if v == "" {
		return nil
	}
	parts := strings.Split(v, ",")
	for i := range parts {
		parts[i] = strings.TrimSpace(parts[i])
	}
	return parts
}
