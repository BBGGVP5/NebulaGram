// Package model describes the data NebulaLink passes between the Go core and
// every platform client. Everything here is plain JSON-serialisable data: the
// Android, iOS and desktop forks all see these exact shapes.
package model

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
)

// Protocol is the wire protocol of an outbound server.
type Protocol string

const (
	VLESS       Protocol = "vless"
	VMess       Protocol = "vmess"
	Trojan      Protocol = "trojan"
	Shadowsocks Protocol = "shadowsocks"
	Hysteria2   Protocol = "hysteria2"
	TUIC        Protocol = "tuic"
	// Custom is a complete core configuration the user pasted in, run as
	// written instead of generated from the fields above.
	Custom Protocol = "custom"
)

// Engine is the tunnel core able to run a given protocol.
type Engine string

const (
	EngineXray    Engine = "xray"
	EngineSingBox Engine = "sing-box"
)

// EngineFor reports which core can run p. Xray covers the classic v2ray family;
// QUIC-based protocols are handled by sing-box.
func EngineFor(p Protocol) Engine {
	switch p {
	case Hysteria2, TUIC:
		return EngineSingBox
	default:
		return EngineXray
	}
}

// Server is one outbound endpoint, parsed from a share link or a subscription.
type Server struct {
	ID       string   `json:"id"`   // stable, derived from the link contents
	Name     string   `json:"name"` // display name (fragment of the link)
	Protocol Protocol `json:"protocol"`
	Address  string   `json:"address"`
	Port     int      `json:"port"`

	// Credentials. UUID doubles as the password for trojan/ss/hysteria2.
	UUID     string `json:"uuid,omitempty"`
	Password string `json:"password,omitempty"`
	Method   string `json:"method,omitempty"`   // shadowsocks cipher
	AlterID  int    `json:"alter_id,omitempty"` // legacy vmess
	Flow     string `json:"flow,omitempty"`

	// Transport.
	Network     string `json:"network,omitempty"` // tcp | ws | grpc | xhttp | httpupgrade | h2
	Path        string `json:"path,omitempty"`
	Host        string `json:"host,omitempty"`
	ServiceName string `json:"service_name,omitempty"` // grpc
	Mode        string `json:"mode,omitempty"`         // grpc / xhttp mode
	HeaderType  string `json:"header_type,omitempty"`

	// Security layer.
	Security      string `json:"security,omitempty"` // none | tls | reality
	SNI           string `json:"sni,omitempty"`
	ALPN          string `json:"alpn,omitempty"`
	Fingerprint   string `json:"fingerprint,omitempty"`
	PublicKey     string `json:"public_key,omitempty"` // reality
	ShortID       string `json:"short_id,omitempty"`   // reality
	SpiderX       string `json:"spider_x,omitempty"`   // reality
	AllowInsecure bool   `json:"allow_insecure,omitempty"`

	// Provenance and presentation.
	// Config holds a whole Xray (or sing-box) configuration for Custom
	// servers. CoreHint says which core it is written for, because the
	// protocol field cannot tell us.
	// A subscription may hand out a whole template per server instead of a
	// share link, so Config keeps that document verbatim. ConfigTag names the
	// outbound to route through when one document describes several servers,
	// which is how sing-box profiles are shaped.
	Config    string `json:"config,omitempty"`
	ConfigTag string `json:"config_tag,omitempty"`
	ConfigRef string `json:"config_ref,omitempty"` // pool key, used on disk only
	CoreHint  Engine `json:"core_hint,omitempty"`

	Source  string `json:"source,omitempty"`  // subscription / provider name
	Country string `json:"country,omitempty"` // ISO 3166-1 alpha-2, best effort
	Flag    string `json:"flag,omitempty"`    // emoji flag pulled from the name
	Raw     string `json:"raw,omitempty"`     // original share link

	// Measurement, filled in by the probe package.
	LatencyMs int   `json:"latency_ms,omitempty"` // 0 = never measured, -1 = failed
	CheckedAt int64 `json:"checked_at,omitempty"` // unix seconds
}

// Engine reports which tunnel core runs this server.
func (s Server) Engine() Engine {
	if s.CoreHint != "" {
		return s.CoreHint
	}
	return EngineFor(s.Protocol)
}

// Subscription is a saved remote source of servers.
type Subscription struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	URL          string `json:"url"`
	Kind         string `json:"kind"`       // "remnawave" | "generic"
	UserAgent    string `json:"user_agent"` // override, empty = default
	UpdatedAt    int64  `json:"updated_at"` // unix seconds of last successful fetch
	ServerCount  int    `json:"server_count"`
	LastError    string `json:"last_error,omitempty"`
	AutoUpdateHr int    `json:"auto_update_hr,omitempty"` // 0 = manual only

	// Remnawave subscription-info headers, surfaced in the UI.
	Info *SubscriptionInfo `json:"info,omitempty"`
}

// SubscriptionInfo mirrors the profile metadata a Remnawave panel returns
// alongside the server list (traffic quota, expiry, support link).
type SubscriptionInfo struct {
	Title          string `json:"title,omitempty"`
	SupportURL     string `json:"support_url,omitempty"`
	Announce       string `json:"announce,omitempty"`
	ProfileWebPage string `json:"profile_web_page,omitempty"`
	UpdateInterval int    `json:"update_interval,omitempty"` // hours
	Upload         int64  `json:"upload,omitempty"`
	Download       int64  `json:"download,omitempty"`
	Total          int64  `json:"total,omitempty"`  // 0 = unlimited
	Expire         int64  `json:"expire,omitempty"` // unix seconds, 0 = never
}

// Used returns consumed traffic in bytes.
func (i SubscriptionInfo) Used() int64 { return i.Upload + i.Download }

// TunnelState is the lifecycle of the local tunnel.
type TunnelState string

const (
	StateDisconnected TunnelState = "disconnected"
	StateConnecting   TunnelState = "connecting"
	StateConnected    TunnelState = "connected"
	StateFailed       TunnelState = "failed"
)

// Status is the snapshot every client polls (or receives via callback) to
// render the connection card.
type Status struct {
	State      TunnelState `json:"state"`
	Server     *Server     `json:"server,omitempty"`
	Engine     Engine      `json:"engine,omitempty"`
	Mode       string      `json:"mode"` // "proxy" | "vpn"
	SocksPort  int         `json:"socks_port,omitempty"`
	HTTPPort   int         `json:"http_port,omitempty"`
	Since      int64       `json:"since,omitempty"` // unix seconds the tunnel came up
	Uplink     int64       `json:"uplink,omitempty"`
	Downlink   int64       `json:"downlink,omitempty"`
	LastError  string      `json:"last_error,omitempty"`
	CoreVer    string      `json:"core_version,omitempty"`
	SingBoxVer string      `json:"singbox_version,omitempty"`
}

// StableID is a stable identity for a server, derived from the endpoint and
// credentials only. Renaming a server on the panel must not change it, or the
// user's selection would be lost on every subscription refresh.
func (s Server) StableID() string {
	h := sha256.New()
	fmt.Fprintf(h, "%s|%s|%d|%s|%s|%s|%s|%s|%s",
		s.Protocol, s.Address, s.Port, s.UUID, s.Password, s.Network, s.Path, s.PublicKey, s.Config)
	return hex.EncodeToString(h.Sum(nil))[:16]
}

// ForClient strips the template from a server before it crosses into the UI.
// A panel profile runs to tens of kilobytes per server, and a list of fifty
// would put megabytes through the binding on every screen refresh — the clients
// never read the document, they only ever pass the id back.
func (s Server) ForClient() Server {
	s.Config = ""
	s.ConfigRef = ""
	return s
}

// ForClientAll maps ForClient over a list.
func ForClientAll(servers []Server) []Server {
	out := make([]Server, len(servers))
	for i, s := range servers {
		out[i] = s.ForClient()
	}
	return out
}
