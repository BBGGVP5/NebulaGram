package api

import (
	"context"
	"errors"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/probe"
	"github.com/nebulagram/nebulagram/core/settings"
)

type probeRequest struct {
	IDs       []string          `json:"ids"`
	Timeout   int               `json:"timeout"` // seconds, 0 = 5, maximum 60
	RequestID string            `json:"request_id"`
	Method    settings.PingType `json:"method,omitempty"`
}
type probeBatch struct {
	id     string
	cancel context.CancelFunc
}

func (c *Core) rememberProbe(id string) {
	if c.probeRecent == nil {
		c.probeRecent = make(map[string]time.Time)
	}
	now := time.Now()
	for id, at := range c.probeRecent {
		if now.Sub(at) > time.Hour {
			delete(c.probeRecent, id)
		}
	}
	if len(c.probeRecent) >= 128 {
		var oldest string
		var at time.Time
		for key, value := range c.probeRecent {
			if oldest == "" || value.Before(at) {
				oldest, at = key, value
			}
		}
		delete(c.probeRecent, oldest)
	}
	c.probeRecent[id] = now
}

func (c *Core) handleProbeCancel(payload []byte) (any, error) {
	var request struct {
		RequestID string `json:"request_id"`
	}
	if err := decode(payload, &request); err != nil {
		return nil, err
	}
	if len(request.RequestID) == 0 || len(request.RequestID) > 128 {
		return nil, errors.New("nebulalink: invalid probe request_id")
	}
	c.probeMu.Lock()
	defer c.probeMu.Unlock()
	cancelled := c.probing != nil && c.probing.id == request.RequestID
	if cancelled {
		c.probing.cancel()
	}
	c.rememberProbe(request.RequestID)
	return map[string]any{"request_id": request.RequestID, "cancelled": cancelled}, nil
}

func (c *Core) handleProbe(payload []byte) (any, error) {
	var req probeRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	if req.RequestID == "" {
		req.RequestID = NewHWID()
	}
	if len(req.RequestID) > 128 || len(req.IDs) > 10000 || req.Timeout < 0 || req.Timeout > 60 {
		return nil, errors.New("nebulalink: invalid probe request")
	}
	cfg := c.st().Settings()
	method := req.Method
	if method == "" {
		method = cfg.PingType
	}
	if method != settings.PingNimbo && method != settings.PingTCP && method != settings.PingHTTP && method != settings.PingURL {
		return nil, errors.New("nebulalink: unknown ping method")
	}
	// URL batches historically measure TCP endpoints, not the active URL.
	measuredMethod := method
	if measuredMethod == settings.PingURL {
		measuredMethod = settings.PingTCP
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	batch := &probeBatch{id: req.RequestID, cancel: cancel}
	c.probeMu.Lock()
	if c.probing != nil {
		c.probeMu.Unlock()
		return nil, errors.New("nebulalink: a probe batch is already running")
	}
	if _, found := c.probeRecent[req.RequestID]; found {
		cancel()
	}
	c.probing = batch
	c.probeMu.Unlock()
	defer func() { c.probeMu.Lock(); c.probing = nil; c.rememberProbe(req.RequestID); c.probeMu.Unlock() }()
	wanted := make(map[string]bool, len(req.IDs))
	for _, id := range req.IDs {
		wanted[id] = true
	}
	var targets []model.Server
	for _, server := range c.st().Servers() {
		if len(wanted) == 0 || wanted[server.ID] {
			targets = append(targets, server)
		}
	}
	if len(targets) == 0 {
		return nil, errors.New("nebulalink: nothing to check")
	}
	timeout := time.Duration(req.Timeout) * time.Second
	if timeout == 0 {
		timeout = 5 * time.Second
	}
	results := make(map[string]int, len(targets))
	checkedAt := int64(0)
	progress := func(id string, ms int, done bool) {
		data := map[string]any{"request_id": req.RequestID, "latency_method": measuredMethod, "completed": len(results), "total": len(targets), "done": done, "cancelled": ctx.Err() != nil}
		if id != "" {
			data["id"] = id
			data["latency_ms"] = ms
			data["checked_at"] = checkedAt
		}
		c.emit("probe.progress", data)
	}
	progress("", 0, false)
	if method == settings.PingNimbo {
		for _, server := range targets {
			if ctx.Err() != nil {
				break
			}
			ms, err := probe.Nimbo(ctx, server, cfg.PingURL, timeout)
			if ctx.Err() != nil {
				break
			} // Cancellation is not a failed unvisited server.
			if err != nil {
				ms = probe.Failed
			}
			checkedAt = time.Now().Unix()
			if err := c.st().UpdateServerLatencyMethod(map[string]int{server.ID: ms}, string(measuredMethod), checkedAt); err != nil {
				return nil, err
			}
			results[server.ID] = ms
			progress(server.ID, ms, false)
		}
	} else {
		// Preserve legacy endpoint methods and URL's historical batch TCP
		// behavior. The distinct probe.url command remains active-tunnel-only.
		legacy := probe.MethodTCP
		if method == settings.PingHTTP {
			legacy = probe.MethodHTTP
			if timeout < 8*time.Second {
				timeout = 8 * time.Second
			}
		}
		probe.Batch(ctx, targets, 16, timeout, legacy)
		if ctx.Err() == nil {
			for _, server := range targets {
				checkedAt = time.Now().Unix()
				if err := c.st().UpdateServerLatencyMethod(map[string]int{server.ID: server.LatencyMs}, string(measuredMethod), checkedAt); err != nil {
					return nil, err
				}
				results[server.ID] = server.LatencyMs
				progress(server.ID, server.LatencyMs, false)
			}
		}
	}
	progress("", 0, true)
	return results, nil
}
