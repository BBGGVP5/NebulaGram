package api

import "github.com/nebulagram/nebulagram/core/model"

func (c *Core) handleServerDetails(payload []byte) (any, error) {
	var req struct {
		ID      string              `json:"id"`
		Details model.ServerDetails `json:"details"`
	}
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	return c.st().SetServerDetails(req.ID, req.Details)
}
