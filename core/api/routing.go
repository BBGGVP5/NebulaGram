package api

import (
	"fmt"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
	"github.com/nebulagram/nebulagram/core/tunnel"
)

func (c *Core) routingView() map[string]any {
	return map[string]any{
		"routing":    c.st().Settings().Routing,
		"presets":    model.RoutePresets(),
		"geo_assets": tunnel.GeoAssetsPresent(),
	}
}

func (c *Core) handleRoutingGet([]byte) (any, error) {
	return c.routingView(), nil
}

// handleRoutingSet replaces the whole table. Unlike settings.set it refuses a
// bad rule with a message instead of quietly dropping the table, so the screen
// can point at what to fix.
func (c *Core) handleRoutingSet(payload []byte) (any, error) {
	var req struct {
		Routing model.Routing `json:"routing"`
	}
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	clean, err := req.Routing.Clean()
	if err != nil {
		return nil, err
	}
	return c.applyRouting(clean)
}

func (c *Core) handleRoutingPreset(payload []byte) (any, error) {
	var req idRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	preset, ok := model.FindRoutePreset(req.ID)
	if !ok {
		return nil, fmt.Errorf("nebulalink: no routing preset %q", req.ID)
	}
	clean, err := preset.Clean()
	if err != nil {
		return nil, err
	}
	return c.applyRouting(clean)
}

// applyRouting stores the table and, when the tunnel is up, restarts it on the
// same server so the rules take effect now rather than on the next connect.
func (c *Core) applyRouting(r model.Routing) (any, error) {
	if _, err := c.st().UpdateSettings(func(s *settings.Settings) { s.Routing = r }); err != nil {
		return nil, err
	}
	if status := c.tunnel.Status(); status.State == model.StateConnected && status.Server != nil {
		for _, server := range c.st().Servers() {
			if server.ID == status.Server.ID {
				if err := c.tunnel.Start(server, c.st().Settings()); err != nil {
					return nil, err
				}
				break
			}
		}
	}
	return c.routingView(), nil
}
