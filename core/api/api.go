// Package api is the single entry point every client talks to.
//
// One method — Call(method, payloadJSON) -> resultJSON — is the entire surface.
// A JSON facade rather than a wide typed API is deliberate: gomobile (Android),
// cgo (iOS, desktop) and any future binding all expose the same two strings, so
// adding a feature never means touching three binding layers, and a client
// built against an older core simply gets an "unknown method" error instead of
// failing to link.
package api

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/link"
	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/onboarding"
	"github.com/nebulagram/nebulagram/core/probe"
	"github.com/nebulagram/nebulagram/core/remnawave"
	"github.com/nebulagram/nebulagram/core/settings"
	"github.com/nebulagram/nebulagram/core/store"
	"github.com/nebulagram/nebulagram/core/tunnel"
	"github.com/nebulagram/nebulagram/core/xraycfg"
)

// Version is the NebulaLink core version, surfaced in the About screen.
const Version = "1.0.0"

// Response is the envelope every call returns.
type Response struct {
	OK    bool            `json:"ok"`
	Error string          `json:"error,omitempty"`
	Data  json.RawMessage `json:"data,omitempty"`
}

// Core holds the state shared by every call.
type Core struct {
	mu      sync.Mutex
	store   *store.Store
	tunnel  *tunnel.Manager
	device  remnawave.Device
	ready   bool
	onEvent func(string)
}

// New returns an uninitialised core; call "core.init" before anything else.
func New() *Core {
	return &Core{tunnel: tunnel.New()}
}

// SetEventSink installs a callback that receives status updates as JSON. The
// platform side forwards them to the UI thread.
func (c *Core) SetEventSink(sink func(string)) {
	c.mu.Lock()
	c.onEvent = sink
	c.mu.Unlock()
	c.tunnel.Observe(func(s model.Status) {
		c.emit("tunnel.status", s)
	})
}

func (c *Core) emit(event string, payload any) {
	c.mu.Lock()
	sink := c.onEvent
	c.mu.Unlock()
	if sink == nil {
		return
	}
	body, err := json.Marshal(map[string]any{"event": event, "data": payload})
	if err != nil {
		return
	}
	sink(string(body))
}

// Call dispatches one request. It never panics out to the caller: a failure
// always comes back as an envelope with ok=false.
func (c *Core) Call(method, payload string) (result string) {
	defer func() {
		if r := recover(); r != nil {
			result = encode(nil, fmt.Errorf("nebulalink: internal error: %v", r))
		}
	}()
	handler, ok := handlers[method]
	if !ok {
		return encode(nil, fmt.Errorf("nebulalink: unknown method %q", method))
	}
	if method != "core.init" && !c.initialised() {
		return encode(nil, errors.New("nebulalink: core.init has not been called"))
	}
	data, err := handler(c, []byte(payload))
	return encode(data, err)
}

func (c *Core) initialised() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.ready
}

func encode(data any, err error) string {
	resp := Response{OK: err == nil}
	if err != nil {
		resp.Error = err.Error()
	} else if data != nil {
		body, marshalErr := json.Marshal(data)
		if marshalErr != nil {
			resp = Response{OK: false, Error: marshalErr.Error()}
		} else {
			resp.Data = body
		}
	}
	out, marshalErr := json.Marshal(resp)
	if marshalErr != nil {
		return `{"ok":false,"error":"nebulalink: cannot encode response"}`
	}
	return string(out)
}

type handler func(*Core, []byte) (any, error)

var handlers = map[string]handler{
	"core.init":               (*Core).handleInit,
	"core.versions":           (*Core).handleVersions,
	"menu.get":                (*Core).handleMenu,
	"onboarding.flow":         (*Core).handleOnboardingFlow,
	"onboarding.connect":      (*Core).handleOnboardingConnect,
	"settings.get":            (*Core).handleSettingsGet,
	"settings.set":            (*Core).handleSettingsSet,
	"settings.reset":          (*Core).handleSettingsReset,
	"hwid.reset":              (*Core).handleHWIDReset,
	"servers.list":            (*Core).handleServersList,
	"server.select":           (*Core).handleServerSelect,
	"server.addLink":          (*Core).handleServerAddLink,
	"server.clearAll":         (*Core).handleServerClear,
	"subscription.add":        (*Core).handleSubscriptionAdd,
	"subscription.list":       (*Core).handleSubscriptionList,
	"subscription.remove":     (*Core).handleSubscriptionRemove,
	"subscription.refreshAll": (*Core).handleSubscriptionRefresh,
	"probe.servers":           (*Core).handleProbe,
	"probe.url":               (*Core).handleProbeURL,
	"tunnel.start":            (*Core).handleTunnelStart,
	"tunnel.stop":             (*Core).handleTunnelStop,
	"tunnel.status":           (*Core).handleTunnelStatus,
	"provider.open":           (*Core).handleProviderOpen,
}

// --- core lifecycle ---------------------------------------------------------

type initRequest struct {
	Dir       string `json:"dir"`
	OS        string `json:"os"`
	OSVersion string `json:"os_version"`
	Model     string `json:"model"`
	UserAgent string `json:"user_agent"`
}

func (c *Core) handleInit(payload []byte) (any, error) {
	var req initRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	st, err := store.Open(req.Dir)
	if err != nil {
		return nil, err
	}

	c.mu.Lock()
	c.store = st
	c.device = remnawave.Device{
		OS:        req.OS,
		OSVersion: req.OSVersion,
		Model:     req.Model,
		UserAgent: req.UserAgent,
	}
	c.ready = true
	c.mu.Unlock()

	// A device-limited panel needs a stable id; mint one on first run.
	current := st.Settings()
	if current.HWID == "" {
		if _, err := st.UpdateSettings(func(s *settings.Settings) { s.HWID = NewHWID() }); err != nil {
			return nil, err
		}
	}
	return map[string]any{
		"version":  Version,
		"settings": st.Settings(),
		"status":   c.tunnel.Status(),
	}, nil
}

func (c *Core) handleVersions([]byte) (any, error) {
	versions := tunnel.Versions()
	versions["nebulalink"] = Version
	return versions, nil
}

func (c *Core) handleMenu([]byte) (any, error) {
	return settings.Menu(), nil
}

// --- onboarding -------------------------------------------------------------

func (c *Core) handleOnboardingFlow([]byte) (any, error) {
	return onboarding.Flow(), nil
}

// handleOnboardingConnect takes whatever the user pasted on the welcome flow —
// a subscription URL or a single share link — works out which it is, and brings
// the tunnel up. One entry point keeps the first-run screen to a single field.
func (c *Core) handleOnboardingConnect(payload []byte) (any, error) {
	var req struct {
		Input string `json:"input"`
		Name  string `json:"name"`
	}
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	input := strings.TrimSpace(req.Input)
	if input == "" {
		return nil, errors.New("nebulalink: paste a subscription link or a server key")
	}

	if strings.HasPrefix(input, "http://") || strings.HasPrefix(input, "https://") {
		body, _ := json.Marshal(subscriptionRequest{URL: input, Name: req.Name})
		if _, err := c.handleSubscriptionAdd(body); err != nil {
			return nil, err
		}
	} else {
		body, _ := json.Marshal(linkRequest{Link: input, Source: req.Name})
		if _, err := c.handleServerAddLink(body); err != nil {
			return nil, err
		}
	}

	server := c.st().Selected()
	if server == nil {
		return nil, errors.New("nebulalink: nothing to connect to")
	}
	if err := c.tunnel.Start(*server, c.st().Settings()); err != nil {
		return nil, err
	}
	return c.tunnel.Status(), nil
}

// --- settings ---------------------------------------------------------------

func (c *Core) handleSettingsGet([]byte) (any, error) {
	return c.st().Settings(), nil
}

// handleSettingsSet takes a partial settings object: only the fields present in
// the payload are changed. That keeps an older client from resetting options it
// does not know about.
func (c *Core) handleSettingsSet(payload []byte) (any, error) {
	current := c.st().Settings()
	if err := decode(payload, &current); err != nil {
		return nil, err
	}
	return c.st().UpdateSettings(func(s *settings.Settings) { *s = current })
}

func (c *Core) handleSettingsReset([]byte) (any, error) {
	return c.st().UpdateSettings(func(s *settings.Settings) {
		hwid := s.HWID
		*s = settings.Default()
		s.HWID = hwid
	})
}

func (c *Core) handleHWIDReset([]byte) (any, error) {
	return c.st().UpdateSettings(func(s *settings.Settings) { s.HWID = NewHWID() })
}

// NewHWID mints a random device identifier in the 16-hex-character form panels
// expect.
func NewHWID() string {
	var buf [8]byte
	if _, err := rand.Read(buf[:]); err != nil {
		return fmt.Sprintf("%016x", time.Now().UnixNano())
	}
	return hex.EncodeToString(buf[:])
}

// --- servers ----------------------------------------------------------------

type listRequest struct {
	Page    int `json:"page"`     // 1-based; 0 means the first page
	PerPage int `json:"per_page"` // 0 means the stored preference
}

func (c *Core) handleServersList(payload []byte) (any, error) {
	var req listRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	all := c.st().Filtered()
	perPage := req.PerPage
	if perPage <= 0 {
		perPage = c.st().Settings().PerPage
	}
	page := req.Page
	if page <= 0 {
		page = 1
	}
	pages := (len(all) + perPage - 1) / perPage
	if pages == 0 {
		pages = 1
	}
	if page > pages {
		page = pages
	}
	start := (page - 1) * perPage
	end := min(start+perPage, len(all))

	return map[string]any{
		"servers":  model.ForClientAll(all[start:end]),
		"total":    len(all),
		"page":     page,
		"pages":    pages,
		"per_page": perPage,
		"selected": c.st().Settings().SelectedServerID,
	}, nil
}

type idRequest struct {
	ID string `json:"id"`
}

func (c *Core) handleServerSelect(payload []byte) (any, error) {
	var req idRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	if err := c.st().Select(req.ID); err != nil {
		return nil, err
	}
	selected := c.st().Selected()
	if selected == nil {
		return nil, errors.New("nebulalink: the server disappeared while selecting it")
	}
	return selected.ForClient(), nil
}

type linkRequest struct {
	Link   string `json:"link"`
	Source string `json:"source"`
}

func (c *Core) handleServerAddLink(payload []byte) (any, error) {
	var req linkRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	source := orDefault(req.Source, "Manual")
	servers, err := parsePastedConfig(strings.TrimSpace(req.Link), source)
	if err != nil {
		return nil, err
	}
	if servers == nil {
		servers = link.ParseMany(req.Link, source)
	}
	if len(servers) == 0 {
		single, err := link.Parse(req.Link)
		if err != nil {
			return nil, err
		}
		single.Source = source
		servers = []model.Server{single}
	}
	added, err := c.st().AddServers(servers)
	if err != nil {
		return nil, err
	}
	return map[string]any{"added": added, "parsed": len(servers)}, nil
}

// parsePastedConfig recognises the two JSON shapes a user can paste. An array
// is a v2ray-json subscription profile and yields one server per entry; a
// single object is a complete Xray configuration, kept verbatim and run as
// written, so hand-tuned routing, DNS and fragmentation survive. Returns nil
// when the payload is not JSON at all, which sends it down the share-link path.
func parsePastedConfig(input, source string) ([]model.Server, error) {
	raw := []byte(input)
	switch {
	case strings.HasPrefix(input, "["):
		servers, err := xraycfg.ParseV2RayJSON(raw)
		if err != nil {
			return nil, err
		}
		for i := range servers {
			servers[i].Source = source
		}
		return servers, nil

	case strings.HasPrefix(input, "{"):
		if !xraycfg.LooksLikeConfig(raw) {
			return nil, errors.New("nebulalink: this JSON is not an Xray configuration")
		}
		server := model.Server{
			Protocol: model.Custom,
			CoreHint: model.EngineXray,
			Config:   input,
			Source:   source,
			Name:     orDefault(xraycfg.ConfigName(raw), "Custom Xray config"),
		}
		server.Address, server.Port = xraycfg.ProbeTarget(raw)
		server.ID = server.StableID()
		return []model.Server{server}, nil
	}
	return nil, nil
}

func (c *Core) handleServerClear([]byte) (any, error) {
	if c.tunnel.Running() {
		_ = c.tunnel.Stop()
	}
	return nil, c.st().ClearServers()
}

// --- subscriptions ----------------------------------------------------------

type subscriptionRequest struct {
	URL  string `json:"url"`
	Name string `json:"name"`
	Kind string `json:"kind"`
}

func (c *Core) handleSubscriptionAdd(payload []byte) (any, error) {
	var req subscriptionRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	sub := model.Subscription{
		Name: orDefault(req.Name, "Subscription"),
		URL:  req.URL,
		Kind: orDefault(req.Kind, "remnawave"),
	}
	saved, err := c.st().PutSubscription(sub)
	if err != nil {
		return nil, err
	}
	return c.refresh(saved)
}

func (c *Core) handleSubscriptionList([]byte) (any, error) {
	return c.st().Subscriptions(), nil
}

func (c *Core) handleSubscriptionRemove(payload []byte) (any, error) {
	var req idRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	return nil, c.st().RemoveSubscription(req.ID)
}

func (c *Core) handleSubscriptionRefresh([]byte) (any, error) {
	subs := c.st().Subscriptions()
	if len(subs) == 0 {
		return nil, errors.New("nebulalink: no subscriptions saved")
	}
	results := make([]any, 0, len(subs))
	var lastErr error
	for _, sub := range subs {
		res, err := c.refresh(sub)
		if err != nil {
			lastErr = err
			results = append(results, map[string]any{"id": sub.ID, "error": err.Error()})
			continue
		}
		results = append(results, res)
	}
	if len(results) == 0 && lastErr != nil {
		return nil, lastErr
	}
	return results, nil
}

// refresh fetches one subscription and replaces the servers it owns.
func (c *Core) refresh(sub model.Subscription) (any, error) {
	client := &remnawave.Client{Device: c.deviceInfo()}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	res, err := client.Fetch(ctx, sub.URL, sub.Name)
	if err != nil {
		sub.LastError = err.Error()
		_, _ = c.st().PutSubscription(sub)
		return nil, err
	}
	if err := c.st().ReplaceSource(sub.Name, res.Servers); err != nil {
		return nil, err
	}
	sub.LastError = ""
	sub.UpdatedAt = time.Now().Unix()
	sub.ServerCount = len(res.Servers)
	sub.Info = res.Info
	if res.Info != nil && res.Info.UpdateInterval > 0 {
		sub.AutoUpdateHr = res.Info.UpdateInterval
	}
	saved, err := c.st().PutSubscription(sub)
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"subscription": saved,
		"servers":      len(res.Servers),
		"format":       res.Format,
	}, nil
}

// deviceInfo merges the platform identity with the user's HWID.
func (c *Core) deviceInfo() remnawave.Device {
	c.mu.Lock()
	device := c.device
	c.mu.Unlock()
	cfg := c.st().Settings()
	device.HWID = cfg.HWID
	if cfg.UserAgent != "" {
		device.UserAgent = cfg.UserAgent
	}
	return device
}

// --- probing ----------------------------------------------------------------

type probeRequest struct {
	IDs     []string `json:"ids"`     // empty = every server on the current page
	Timeout int      `json:"timeout"` // seconds, 0 = 5
}

func (c *Core) handleProbe(payload []byte) (any, error) {
	var req probeRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	wanted := make(map[string]bool, len(req.IDs))
	for _, id := range req.IDs {
		wanted[id] = true
	}
	var targets []model.Server
	for _, s := range c.st().Servers() {
		if len(wanted) == 0 || wanted[s.ID] {
			targets = append(targets, s)
		}
	}
	if len(targets) == 0 {
		return nil, errors.New("nebulalink: nothing to check")
	}
	timeout := time.Duration(req.Timeout) * time.Second
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	probe.Batch(context.Background(), targets, 16, timeout)

	results := make(map[string]int, len(targets))
	for _, s := range targets {
		results[s.ID] = s.LatencyMs
	}
	if err := c.st().UpdateServerLatency(results); err != nil {
		return nil, err
	}
	return results, nil
}

func (c *Core) handleProbeURL(payload []byte) (any, error) {
	var req struct {
		URL string `json:"url"`
	}
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	status := c.tunnel.Status()
	if status.State != model.StateConnected {
		return nil, errors.New("nebulalink: URL check needs a running tunnel")
	}
	addr := fmt.Sprintf("127.0.0.1:%d", status.SocksPort)
	ms, err := probe.URL(context.Background(), req.URL, addr, 10*time.Second)
	if err != nil {
		return nil, err
	}
	return map[string]int{"latency_ms": ms}, nil
}

// --- tunnel -----------------------------------------------------------------

func (c *Core) handleTunnelStart(payload []byte) (any, error) {
	var req idRequest
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	if req.ID != "" {
		if err := c.st().Select(req.ID); err != nil {
			return nil, err
		}
	}
	server := c.st().Selected()
	if server == nil {
		return nil, errors.New("nebulalink: no server selected")
	}
	if err := c.tunnel.Start(*server, c.st().Settings()); err != nil {
		return nil, err
	}
	return c.tunnel.Status(), nil
}

func (c *Core) handleTunnelStop([]byte) (any, error) {
	if err := c.tunnel.Stop(); err != nil {
		return nil, err
	}
	return c.tunnel.Status(), nil
}

func (c *Core) handleTunnelStatus([]byte) (any, error) {
	return c.tunnel.Status(), nil
}

// --- provider ---------------------------------------------------------------

// handleProviderOpen returns the page the client should open in a browser: the
// panel's own profile page when the subscription advertises one.
func (c *Core) handleProviderOpen([]byte) (any, error) {
	for _, sub := range c.st().Subscriptions() {
		if sub.Info == nil {
			continue
		}
		if url := orDefault(sub.Info.ProfileWebPage, sub.Info.SupportURL); url != "" {
			return map[string]string{"url": url}, nil
		}
	}
	return nil, errors.New("nebulalink: this provider offers no web page")
}

// --- helpers ----------------------------------------------------------------

func (c *Core) st() *store.Store {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.store
}

// decode tolerates an empty payload so calls without arguments can pass "".
func decode(payload []byte, target any) error {
	if len(payload) == 0 {
		return nil
	}
	if err := json.Unmarshal(payload, target); err != nil {
		return fmt.Errorf("nebulalink: bad request payload: %w", err)
	}
	return nil
}

func orDefault(v, fallback string) string {
	if v == "" {
		return fallback
	}
	return v
}
