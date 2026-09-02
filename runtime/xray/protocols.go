package xray

// The features Xray links into NebulaGram.
//
// Upstream offers main/distro/all, which registers everything Xray can do —
// including the whole command-line tool, the gRPC control API, WireGuard and
// mKCP. None of that is reachable from a messenger, and it costs tens of
// megabytes of native code in every APK. This list is the client-side subset:
// the outbounds a panel actually hands out, the transports they run over, and
// the routing and DNS a profile may configure.
//
// Build with `-tags xray_full` to get upstream's complete registry instead;
// see protocols_full.go.

import (
	// Mandatory plumbing: without these an instance has no dispatcher and no
	// way to open inbound or outbound connections.
	_ "github.com/xtls/xray-core/app/dispatcher"
	_ "github.com/xtls/xray-core/app/proxyman/inbound"
	_ "github.com/xtls/xray-core/app/proxyman/outbound"

	// Sections a generated or pasted configuration may carry.
	_ "github.com/xtls/xray-core/app/dns"
	_ "github.com/xtls/xray-core/app/log"
	_ "github.com/xtls/xray-core/app/policy"
	_ "github.com/xtls/xray-core/app/router"
	_ "github.com/xtls/xray-core/app/stats"
	_ "github.com/xtls/xray-core/transport/internet/tagged/taggedimpl"

	// Local inbounds we open ourselves.
	_ "github.com/xtls/xray-core/proxy/http"
	_ "github.com/xtls/xray-core/proxy/socks"

	// Outbounds: the proxy protocols, plus the two pseudo-outbounds every
	// routing table uses.
	_ "github.com/xtls/xray-core/proxy/blackhole"
	_ "github.com/xtls/xray-core/proxy/freedom"
	_ "github.com/xtls/xray-core/proxy/shadowsocks"
	_ "github.com/xtls/xray-core/proxy/trojan"
	_ "github.com/xtls/xray-core/proxy/vless/outbound"
	_ "github.com/xtls/xray-core/proxy/vmess/outbound"

	// Transports and security layers.
	_ "github.com/xtls/xray-core/transport/internet/grpc"
	_ "github.com/xtls/xray-core/transport/internet/httpupgrade"
	_ "github.com/xtls/xray-core/transport/internet/reality"
	_ "github.com/xtls/xray-core/transport/internet/splithttp"
	_ "github.com/xtls/xray-core/transport/internet/tcp"
	_ "github.com/xtls/xray-core/transport/internet/tls"
	_ "github.com/xtls/xray-core/transport/internet/udp"
	_ "github.com/xtls/xray-core/transport/internet/websocket"

	// Header obfuscation used by tcp transports.
	_ "github.com/xtls/xray-core/transport/internet/headers/http"
	_ "github.com/xtls/xray-core/transport/internet/headers/noop"
)
