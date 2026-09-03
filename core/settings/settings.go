// Package settings holds NebulaLink's user-facing preferences and the
// declarative description of the settings UI.
//
// The schema in menu.go is the single source of truth for the tunnel screens on
// all three platforms: Android, iOS and desktop each render it with native
// widgets, so a new option is added once, here, and appears everywhere. Only
// the strings live in the platform resource files.
package settings

import "strings"

// Mode is how traffic reaches the tunnel.
type Mode string

const (
	// ModeProxy runs a local SOCKS5 endpoint and points the messenger at it.
	// It needs no VPN permission and works on a sideloaded iOS build.
	ModeProxy Mode = "proxy"
	// ModeVPN captures the whole device through the platform VPN API.
	ModeVPN Mode = "vpn"
)

// PingType selects how latency is measured.
type PingType string

const (
	PingTCP PingType = "tcp"
	PingURL PingType = "url"
)

// Settings is the complete tunnel configuration. It is persisted as JSON and
// exposed to the clients through the api package.
type Settings struct {
	Mode      Mode `json:"mode"`
	SocksPort int  `json:"socks_port"`
	HTTPPort  int  `json:"http_port"` // 0 = disabled

	// Identity sent to a device-limited panel.
	HWID      string `json:"hwid"`
	UserAgent string `json:"user_agent,omitempty"`

	// Latency checking.
	PingType    PingType `json:"ping_type"`
	PingURL     string   `json:"ping_url,omitempty"`
	AutoPing    bool     `json:"auto_ping"`
	AutoPingMin int      `json:"auto_ping_min,omitempty"`

	// Behaviour.
	RefreshOnStart  bool `json:"refresh_on_start"`
	SwitchOnFailure bool `json:"switch_on_failure"`
	RouteCalls      bool `json:"route_calls"`
	DualCore        bool `json:"dual_core"`

	// Networking internals.
	DNS      string `json:"dns,omitempty"`
	LogLevel string `json:"log_level,omitempty"`

	// Server list presentation.
	SelectedServerID string `json:"selected_server_id,omitempty"`
	ProtocolFilter   string `json:"protocol_filter,omitempty"`
	SearchQuery      string `json:"search_query,omitempty"`
	ServerSort       string `json:"server_sort"` // default = subscription order; latency = measured delay
	PerPage          int    `json:"per_page,omitempty"`
}

// Default returns the settings a fresh installation starts with.
func Default() Settings {
	return Settings{
		Mode:            ModeProxy,
		SocksPort:       10808,
		HTTPPort:        0,
		PingType:        PingTCP,
		AutoPing:        false,
		AutoPingMin:     15,
		RefreshOnStart:  false,
		SwitchOnFailure: false,
		RouteCalls:      true,
		DualCore:        false,
		DNS:             "", // empty: the server resolves, see xraycfg
		LogLevel:        "warning",
		PerPage:         50,
		ServerSort:      "default",
	}
}

// Normalize repairs values that arrived from an older build or a hand-edited
// file, so a bad stored value can never wedge the client.
func (s *Settings) Normalize() {
	def := Default()
	if s.Mode != ModeProxy && s.Mode != ModeVPN {
		s.Mode = def.Mode
	}
	if s.SocksPort <= 0 || s.SocksPort > 65535 {
		s.SocksPort = def.SocksPort
	}
	if s.HTTPPort < 0 || s.HTTPPort > 65535 {
		s.HTTPPort = 0
	}
	if s.PingType != PingTCP && s.PingType != PingURL {
		s.PingType = def.PingType
	}
	if s.AutoPingMin <= 0 {
		s.AutoPingMin = def.AutoPingMin
	}
	if s.PerPage <= 0 || s.PerPage > 500 {
		s.PerPage = def.PerPage
	}
	if s.ServerSort != "default" && s.ServerSort != "latency" {
		s.ServerSort = def.ServerSort
	}
	if s.LogLevel == "" {
		s.LogLevel = def.LogLevel
	}
	s.HWID = strings.TrimSpace(s.HWID)
}
