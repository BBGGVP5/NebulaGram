// Package singboxcfg builds sing-box configurations and reads sing-box
// subscription profiles.
//
// sing-box carries the QUIC-based protocols Xray does not implement
// (Hysteria2, TUIC). Everything else runs on Xray; see model.EngineFor.
package singboxcfg

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/nebulagram/nebulagram/core/model"
)

// Options mirrors xraycfg.Options for the sing-box side.
type Options struct {
	SocksPort  int
	HTTPPort   int // 0 = mixed inbound only
	ListenAddr string
	LogLevel   string
	DNS        string
}

// Defaults returns the options NebulaGram ships with.
func Defaults(socksPort int) Options {
	return Options{SocksPort: socksPort, ListenAddr: "127.0.0.1", LogLevel: "warn", DNS: "1.1.1.1"}
}

// Build renders a full sing-box config exposing a local mixed (SOCKS+HTTP)
// inbound that forwards everything to the given server.
func Build(s model.Server, o Options) ([]byte, error) {
	if s.Engine() != model.EngineSingBox {
		return nil, fmt.Errorf("singboxcfg: %s is handled by xray, not sing-box", s.Protocol)
	}
	if o.SocksPort == 0 {
		return nil, errors.New("singboxcfg: socks port is required")
	}
	if o.ListenAddr == "" {
		o.ListenAddr = "127.0.0.1"
	}
	if o.LogLevel == "" {
		o.LogLevel = "warn"
	}
	outbound, err := BuildOutbound(s)
	if err != nil {
		return nil, err
	}
	cfg := map[string]any{
		"log": map[string]any{"level": o.LogLevel, "timestamp": false},
		"inbounds": []any{map[string]any{
			"type":        "mixed",
			"tag":         "mixed-in",
			"listen":      o.ListenAddr,
			"listen_port": o.SocksPort,
			"sniff":       true,
		}},
		"outbounds": []any{outbound, map[string]any{"type": "direct", "tag": "direct"}},
		"route":     map[string]any{"final": "proxy"},
	}
	if o.DNS != "" {
		cfg["dns"] = map[string]any{"servers": []any{map[string]any{"address": o.DNS}}}
	}
	return json.MarshalIndent(cfg, "", "  ")
}

// BuildOutbound renders the proxy outbound alone.
func BuildOutbound(s model.Server) (map[string]any, error) {
	out := map[string]any{
		"type":        string(s.Protocol),
		"tag":         "proxy",
		"server":      s.Address,
		"server_port": s.Port,
	}
	switch s.Protocol {
	case model.Hysteria2:
		out["password"] = s.Password
	case model.TUIC:
		out["uuid"] = s.UUID
		out["password"] = s.Password
		out["congestion_control"] = "bbr"
		out["udp_relay_mode"] = "native"
	default:
		return nil, fmt.Errorf("singboxcfg: unsupported protocol %q", s.Protocol)
	}
	out["tls"] = buildTLS(s)
	return out, nil
}

func buildTLS(s model.Server) map[string]any {
	tls := map[string]any{
		"enabled":     true,
		"server_name": firstNonEmpty(s.SNI, s.Address),
	}
	if s.AllowInsecure {
		tls["insecure"] = true
	}
	if s.ALPN != "" {
		tls["alpn"] = splitCSV(s.ALPN)
	}
	return tls
}

// sbOutbound mirrors the sing-box outbound fields NebulaLink understands.
type sbOutbound struct {
	Type       string `json:"type"`
	Tag        string `json:"tag"`
	Server     string `json:"server"`
	ServerPort int    `json:"server_port"`
	UUID       string `json:"uuid"`
	Password   string `json:"password"`
	Method     string `json:"method"`
	Flow       string `json:"flow"`
	TLS        struct {
		Enabled    bool     `json:"enabled"`
		ServerName string   `json:"server_name"`
		Insecure   bool     `json:"insecure"`
		ALPN       []string `json:"alpn"`
		UTLS       struct {
			Fingerprint string `json:"fingerprint"`
		} `json:"utls"`
		Reality struct {
			Enabled   bool   `json:"enabled"`
			PublicKey string `json:"public_key"`
			ShortID   string `json:"short_id"`
		} `json:"reality"`
	} `json:"tls"`
	Transport struct {
		Type        string `json:"type"`
		Path        string `json:"path"`
		Host        any    `json:"host"`
		ServiceName string `json:"service_name"`
	} `json:"transport"`
}

type sbProfile struct {
	Outbounds []sbOutbound `json:"outbounds"`
}

// ParseProfile reads a sing-box subscription profile into servers.
func ParseProfile(data []byte) ([]model.Server, error) {
	var p sbProfile
	if err := json.Unmarshal(data, &p); err != nil {
		return nil, err
	}
	var servers []model.Server
	for _, o := range p.Outbounds {
		s, err := convert(o)
		if err != nil {
			continue
		}
		s.ID = s.StableID()
		servers = append(servers, s)
	}
	if len(servers) == 0 {
		return nil, errors.New("singboxcfg: profile has no proxy outbound")
	}
	return servers, nil
}

func convert(o sbOutbound) (model.Server, error) {
	s := model.Server{
		Name:     o.Tag,
		Protocol: model.Protocol(o.Type),
		Address:  o.Server,
		Port:     o.ServerPort,
		UUID:     o.UUID,
		Password: o.Password,
		Method:   o.Method,
		Flow:     o.Flow,
		Network:  "tcp",
		Security: "none",
	}
	switch s.Protocol {
	case model.VLESS, model.VMess, model.Trojan, model.Shadowsocks, model.Hysteria2, model.TUIC:
	default:
		return model.Server{}, fmt.Errorf("singboxcfg: outbound type %q is not a proxy", o.Type)
	}
	if s.Address == "" || s.Port == 0 {
		return model.Server{}, errors.New("singboxcfg: outbound has no server:port")
	}

	if o.TLS.Enabled {
		s.Security = "tls"
		s.SNI = o.TLS.ServerName
		s.AllowInsecure = o.TLS.Insecure
		s.ALPN = strings.Join(o.TLS.ALPN, ",")
		s.Fingerprint = o.TLS.UTLS.Fingerprint
		if o.TLS.Reality.Enabled {
			s.Security = "reality"
			s.PublicKey = o.TLS.Reality.PublicKey
			s.ShortID = o.TLS.Reality.ShortID
		}
	}
	switch o.Transport.Type {
	case "ws", "httpupgrade":
		s.Network = o.Transport.Type
		s.Path = o.Transport.Path
		s.Host = hostString(o.Transport.Host)
	case "grpc":
		s.Network = "grpc"
		s.ServiceName = o.Transport.ServiceName
	case "http":
		s.Network = "h2"
		s.Path = o.Transport.Path
		s.Host = hostString(o.Transport.Host)
	}
	return s, nil
}

// hostString accepts both the string and []string forms sing-box allows.
func hostString(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case []any:
		parts := make([]string, 0, len(t))
		for _, item := range t {
			if s, ok := item.(string); ok {
				parts = append(parts, s)
			}
		}
		return strings.Join(parts, ",")
	}
	return ""
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if v != "" {
			return v
		}
	}
	return ""
}

func splitCSV(v string) []string {
	parts := strings.Split(v, ",")
	for i := range parts {
		parts[i] = strings.TrimSpace(parts[i])
	}
	return parts
}
