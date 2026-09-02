//go:build xray_full

package xray

// Upstream's complete registry, for a build that must run anything Xray
// supports — WireGuard, mKCP, fakedns, the gRPC control API. It roughly triples
// the native code shipped in the app, so it is opt-in.
import _ "github.com/xtls/xray-core/main/distro/all"
