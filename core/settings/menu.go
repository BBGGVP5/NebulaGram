package settings

// RowType tells a client which native widget to render.
type RowType string

const (
	RowNav    RowType = "nav"    // opens another screen
	RowSwitch RowType = "switch" // boolean setting
	RowSelect RowType = "select" // one of Options
	RowText   RowType = "text"   // free-form string, edited in a dialog
	RowNumber RowType = "number" // integer, edited in a dialog
	RowAction RowType = "action" // fires a command, holds no value
	RowInfo   RowType = "info"   // read-only line
	RowCard   RowType = "card"   // the big connect / server cards
)

// Option is one choice of a RowSelect row.
type Option struct {
	Value    string `json:"value"`
	TitleKey string `json:"title_key"`
	Title    string `json:"title"` // English fallback when a key is missing
}

// Row is a single line of the settings UI.
type Row struct {
	Key         string   `json:"key"` // settings field or command name
	Type        RowType  `json:"type"`
	TitleKey    string   `json:"title_key"`
	Title       string   `json:"title"`
	SubtitleKey string   `json:"subtitle_key,omitempty"`
	Subtitle    string   `json:"subtitle,omitempty"`
	Icon        string   `json:"icon,omitempty"`    // platform-neutral icon name
	Screen      string   `json:"screen,omitempty"`  // target screen id for RowNav
	Command     string   `json:"command,omitempty"` // api command for RowAction
	Options     []Option `json:"options,omitempty"`
	Min         int      `json:"min,omitempty"`
	Max         int      `json:"max,omitempty"`
	Destructive bool     `json:"destructive,omitempty"`
	// VisibleIf hides the row unless the named capability is present, e.g.
	// "vpn_supported". Clients that do not know a flag render the row.
	VisibleIf string `json:"visible_if,omitempty"`
}

// Section groups rows under an optional header.
type Section struct {
	TitleKey string `json:"title_key,omitempty"`
	Title    string `json:"title,omitempty"`
	Rows     []Row  `json:"rows"`
}

// Screen is one page of the tunnel UI.
type Screen struct {
	ID       string    `json:"id"`
	TitleKey string    `json:"title_key"`
	Title    string    `json:"title"`
	Sections []Section `json:"sections"`
}

// Screen identifiers, referenced by RowNav.Screen and by the clients.
const (
	ScreenHome     = "nebulalink.home"
	ScreenServers  = "nebulalink.servers"
	ScreenAdvanced = "nebulalink.advanced"
	ScreenAbout    = "nebulalink.about"
)

// Menu returns the full declarative UI. Clients render it top to bottom; the
// order here is the order on screen.
func Menu() []Screen {
	return []Screen{homeScreen(), serversScreen(), advancedScreen(), aboutScreen()}
}

func homeScreen() Screen {
	return Screen{
		ID: ScreenHome, TitleKey: "nl_title", Title: "NebulaLink",
		Sections: []Section{
			{Rows: []Row{
				{Key: "connection", Type: RowCard, TitleKey: "nl_connection", Title: "Connection", Icon: "shield"},
				{Key: "selected_server", Type: RowCard, TitleKey: "nl_current_server", Title: "Current server", Icon: "globe"},
			}},
			{Rows: []Row{
				{Key: "source", Type: RowNav, Screen: ScreenServers, Icon: "folder",
					TitleKey: "nl_source", Title: "Server source",
					SubtitleKey: "nl_source_sub", Subtitle: "Subscriptions, manual keys and filters"},
				{Key: "open_provider", Type: RowAction, Command: "provider.open", Icon: "info",
					TitleKey: "nl_open_provider", Title: "Open provider page",
					SubtitleKey: "nl_open_provider_sub", Subtitle: "Top up, renew or read announcements"},
				{Key: "advanced", Type: RowNav, Screen: ScreenAdvanced, Icon: "settings",
					TitleKey: "nl_advanced", Title: "Advanced",
					SubtitleKey: "nl_advanced_sub", Subtitle: "Latency method, device id and cores"},
			}},
		},
	}
}

func serversScreen() Screen {
	return Screen{
		ID: ScreenServers, TitleKey: "nl_servers", Title: "NebulaLink servers",
		Sections: []Section{
			{TitleKey: "nl_sec_source", Title: "SOURCE AND FILTERS", Rows: []Row{
				{Key: "provider", Type: RowNav, Screen: "nebulalink.provider", Icon: "folder",
					TitleKey: "nl_provider", Title: "Provider",
					SubtitleKey: "nl_provider_sub", Subtitle: "Pick a built-in or custom source"},
				{Key: "search_query", Type: RowText, Icon: "search",
					TitleKey: "nl_search", Title: "Search",
					SubtitleKey: "nl_search_sub", Subtitle: "By server or source name"},
				{Key: "protocol_filter", Type: RowSelect, Icon: "filter",
					TitleKey: "nl_protocol", Title: "Protocol",
					SubtitleKey: "nl_protocol_sub", Subtitle: "Show only the selected protocol",
					Options: protocolOptions()},
			}},
			{TitleKey: "nl_sec_actions", Title: "ACTIONS", Rows: []Row{
				{Key: "refresh", Type: RowAction, Command: "subscription.refreshAll", Icon: "refresh",
					TitleKey: "nl_refresh", Title: "Refresh subscriptions",
					SubtitleKey: "nl_refresh_sub", Subtitle: "Fetch fresh servers from saved sources"},
				{Key: "check_page", Type: RowAction, Command: "probe.servers", Icon: "gauge",
					TitleKey: "nl_check_page", Title: "Check page",
					SubtitleKey: "nl_check_page_sub", Subtitle: "Measures the servers currently visible"},
				{Key: "add_key", Type: RowAction, Command: "server.addLink", Icon: "edit",
					TitleKey: "nl_add_key", Title: "Add server key",
					SubtitleKey: "nl_add_key_sub", Subtitle: "VLESS, VMess, Trojan, Shadowsocks, Hysteria2 or TUIC"},
				{Key: "add_subscription", Type: RowAction, Command: "subscription.add", Icon: "download",
					TitleKey: "nl_add_sub", Title: "Add subscription",
					SubtitleKey: "nl_add_sub_sub", Subtitle: "HTTP or HTTPS link to a Remnawave panel"},
				{Key: "clear", Type: RowAction, Command: "server.clearAll", Icon: "trash", Destructive: true,
					TitleKey: "nl_clear", Title: "Clear servers",
					SubtitleKey: "nl_clear_sub", Subtitle: "Subscriptions are kept, the tunnel is stopped"},
			}},
			{TitleKey: "nl_sec_servers", Title: "SERVERS", Rows: []Row{
				{Key: "server_list", Type: RowCard, Icon: "list",
					TitleKey: "nl_server_list", Title: "Server list"},
				{Key: "per_page", Type: RowNumber, Min: 10, Max: 200, Icon: "chart",
					TitleKey: "nl_per_page", Title: "Servers per page",
					SubtitleKey: "nl_per_page_sub", Subtitle: "More servers at once, less paging"},
			}},
		},
	}
}

func advancedScreen() Screen {
	return Screen{
		ID: ScreenAdvanced, TitleKey: "nl_advanced", Title: "Advanced",
		Sections: []Section{
			{Rows: []Row{
				{Key: "mode", Type: RowSelect, Icon: "route", VisibleIf: "vpn_supported",
					TitleKey: "nl_mode", Title: "Tunnel mode",
					SubtitleKey: "nl_mode_sub", Subtitle: "Messenger only, or the whole device",
					Options: []Option{
						{Value: string(ModeProxy), TitleKey: "nl_mode_proxy", Title: "Messenger only (local proxy)"},
						{Value: string(ModeVPN), TitleKey: "nl_mode_vpn", Title: "Whole device (VPN)"},
					}},
				{Key: "ping_type", Type: RowSelect, Icon: "gauge",
					TitleKey: "nl_ping_type", Title: "Ping type",
					Options: []Option{
						{Value: string(PingTCP), TitleKey: "nl_ping_tcp", Title: "TCP"},
						{Value: string(PingURL), TitleKey: "nl_ping_url", Title: "URL"},
					}},
				{Key: "hwid", Type: RowText, Icon: "edit",
					TitleKey: "nl_hwid", Title: "HWID",
					SubtitleKey: "nl_hwid_sub", Subtitle: "Device id sent to a device-limited panel"},
				{Key: "refresh_on_start", Type: RowSwitch, Icon: "download",
					TitleKey: "nl_refresh_on_start", Title: "Refresh subscriptions on launch",
					SubtitleKey: "nl_refresh_on_start_sub", Subtitle: "On open, when the lists are stale"},
				{Key: "auto_ping", Type: RowSwitch, Icon: "chart",
					TitleKey: "nl_auto_ping", Title: "Automatic latency check",
					SubtitleKey: "nl_auto_ping_sub", Subtitle: "Periodic, always over TCP so the tunnel is never interrupted"},
			}},
			{TitleKey: "nl_sec_calls", Title: "CALLS", Rows: []Row{
				{Key: "call_state", Type: RowAction, Command: "calls.stats", Icon: "chart",
					TitleKey: "nl_call_state", Title: "Call state",
					SubtitleKey: "nl_call_state_sub", Subtitle: "Hooks / requests / matched / sent / received"},
				{Key: "route_calls", Type: RowSwitch, Icon: "lock",
					TitleKey: "nl_route_calls", Title: "Calls through NebulaLink",
					SubtitleKey: "nl_route_calls_sub", Subtitle: "Call media goes through your server, with no third-party relays"},
			}},
			{TitleKey: "nl_sec_core", Title: "CORE", Rows: []Row{
				{Key: "switch_on_failure", Type: RowSwitch, Icon: "refresh",
					TitleKey: "nl_switch_on_failure", Title: "Switch server on failure"},
				{Key: "dual_core", Type: RowSwitch, Icon: "key",
					TitleKey: "nl_dual_core", Title: "Both cores at once",
					SubtitleKey: "nl_dual_core_sub", Subtitle: "Keeps Xray and sing-box loaded, so switching protocols is instant"},
				{Key: "socks_port", Type: RowNumber, Min: 1024, Max: 65535, Icon: "route",
					TitleKey: "nl_socks_port", Title: "Local SOCKS port"},
				{Key: "dns", Type: RowText, Icon: "globe",
					TitleKey: "nl_dns", Title: "DNS inside the tunnel"},
				{Key: "versions", Type: RowInfo, Icon: "info",
					TitleKey: "nl_versions", Title: "Component versions"},
			}},
		},
	}
}

func aboutScreen() Screen {
	return Screen{
		ID: ScreenAbout, TitleKey: "nl_about", Title: "About NebulaLink",
		Sections: []Section{{Rows: []Row{
			{Key: "versions", Type: RowInfo, Icon: "info", TitleKey: "nl_versions", Title: "Component versions"},
			{Key: "logs", Type: RowAction, Command: "log.export", Icon: "download",
				TitleKey: "nl_export_logs", Title: "Export logs"},
			{Key: "reset", Type: RowAction, Command: "settings.reset", Icon: "trash", Destructive: true,
				TitleKey: "nl_reset", Title: "Reset NebulaLink settings"},
		}}},
	}
}

func protocolOptions() []Option {
	return []Option{
		{Value: "", TitleKey: "nl_all_protocols", Title: "All protocols"},
		{Value: "vless", Title: "VLESS", TitleKey: "nl_proto_vless"},
		{Value: "vmess", Title: "VMess", TitleKey: "nl_proto_vmess"},
		{Value: "trojan", Title: "Trojan", TitleKey: "nl_proto_trojan"},
		{Value: "shadowsocks", Title: "Shadowsocks", TitleKey: "nl_proto_ss"},
		{Value: "hysteria2", Title: "Hysteria2", TitleKey: "nl_proto_hy2"},
		{Value: "tuic", Title: "TUIC", TitleKey: "nl_proto_tuic"},
	}
}
