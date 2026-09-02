package tunnel

import (
	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
)

// BuildConfigForTest renders the core configuration for a server without
// starting anything, so tests can check what a subscription would actually run.
func BuildConfigForTest(server model.Server, cfg settings.Settings) ([]byte, error) {
	return buildConfig(server, cfg, cfg.SocksPort, cfg.HTTPPort)
}

// ResolvePortForTest exposes the free-port fallback.
func ResolvePortForTest(wanted int) (int, error) { return resolvePort(wanted) }
