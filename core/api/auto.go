package api

import (
	"context"
	"errors"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/nebulagram/nebulagram/core/autoselect"
	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/probe"
	"github.com/nebulagram/nebulagram/core/settings"
	"github.com/nebulagram/nebulagram/core/tunnel"
)

// Auto-select limits. The concurrency cap matters: a subscription of a hundred
// servers probed all at once looks like a scan to the network in between and
// starves the live tunnel.
const (
	autoConcurrency   = 8
	autoProbeTimeout  = 5 * time.Second
	autoHTTPTimeout   = 8 * time.Second
	autoBatchTimeout  = 90 * time.Second
	autoStartAttempts = 3
	autoTick          = time.Minute
	// After a connection failure auto re-checks at once, but no more than
	// this many times per window, so a dead network does not spin the core.
	autoFailureBudget = 3
	autoFailureWindow = 5 * time.Minute
)

// AutoState is what the "Auto" row shows: which server auto picked and how
// fast it answered.
type AutoState struct {
	Enabled    bool   `json:"enabled"`
	State      string `json:"state"` // idle | checking | ready | no_answer | failed
	ServerID   string `json:"server_id,omitempty"`
	ServerName string `json:"server_name,omitempty"`
	Flag       string `json:"flag,omitempty"`
	LatencyMs  int    `json:"latency_ms"`
	Method     string `json:"latency_method,omitempty"`
	CheckedAt  int64  `json:"checked_at,omitempty"`
	Reason     string `json:"reason,omitempty"`
	Measured   int    `json:"measured"`
	Total      int    `json:"total"`
	Error      string `json:"error,omitempty"`
}

type autoPilot struct {
	run      sync.Mutex // one check at a time
	mu       sync.Mutex
	state    AutoState
	last     time.Time
	failures []time.Time
	once     sync.Once
	checking atomic.Bool // a check is switching servers right now
	// measure replaces the real probes in tests.
	measure autoselect.MeasureFunc
}

var errAutoBusy = errors.New("nebulalink: an automatic check is already running")

func (c *Core) autoSnapshot() AutoState {
	c.auto.mu.Lock()
	state := c.auto.state
	c.auto.mu.Unlock()
	if state.State == "" {
		state.State = "idle"
	}
	if st := c.st(); st != nil {
		state.Enabled = st.Settings().AutoSelect
	}
	return state
}

func (c *Core) setAuto(mutate func(*AutoState)) AutoState {
	c.auto.mu.Lock()
	mutate(&c.auto.state)
	c.auto.mu.Unlock()
	state := c.autoSnapshot()
	c.emit("auto.status", state)
	return state
}

// autoMethod is the latency method auto measures with: the user's ping type,
// except that URL — which only measures the running tunnel — falls back to TCP,
// as the batch check does.
func autoMethod(cfg settings.Settings) settings.PingType {
	switch cfg.PingType {
	case settings.PingNimbo, settings.PingHTTP:
		return cfg.PingType
	default:
		return settings.PingTCP
	}
}

func (c *Core) autoMeasure(cfg settings.Settings) (autoselect.MeasureFunc, time.Duration) {
	if c.auto.measure != nil {
		return c.auto.measure, autoProbeTimeout
	}
	switch autoMethod(cfg) {
	case settings.PingNimbo:
		return func(ctx context.Context, s model.Server, timeout time.Duration) int {
			ms, err := probe.Nimbo(ctx, s, cfg.PingURL, timeout)
			if err != nil {
				return autoselect.Failed
			}
			return ms
		}, autoProbeTimeout
	case settings.PingHTTP:
		return func(ctx context.Context, s model.Server, timeout time.Duration) int {
			return probe.HTTPGet(ctx, s, timeout)
		}, autoHTTPTimeout
	default:
		return func(ctx context.Context, s model.Server, timeout time.Duration) int {
			return probe.TCP(ctx, s, timeout)
		}, autoProbeTimeout
	}
}

// autoCheck measures every server and moves the tunnel when Decide says so.
// connect brings the tunnel up even when it is down (the "connect" button with
// auto on); otherwise a stopped tunnel only gets its selection updated.
// wait=false skips the check when another one is running.
func (c *Core) autoCheck(_ string, connect, wait bool) (AutoState, error) {
	if wait {
		c.auto.run.Lock()
	} else if !c.auto.run.TryLock() {
		return c.autoSnapshot(), errAutoBusy
	}
	defer c.auto.run.Unlock()
	c.auto.checking.Store(true)
	defer c.auto.checking.Store(false)
	c.auto.mu.Lock()
	c.auto.last = time.Now()
	c.auto.mu.Unlock()

	cfg := c.st().Settings()
	var targets []model.Server
	for _, server := range c.st().Servers() {
		if tunnel.Available(server.Engine()) {
			targets = append(targets, server)
		}
	}
	if len(targets) == 0 {
		err := errors.New("nebulalink: no servers to choose from")
		c.setAuto(func(s *AutoState) { s.State, s.Error = "failed", err.Error() })
		return c.autoSnapshot(), err
	}
	c.setAuto(func(s *AutoState) {
		s.State, s.Error, s.Reason, s.Measured, s.Total = "checking", "", "", 0, len(targets)
	})

	measure, timeout := c.autoMeasure(cfg)
	ctx, cancel := context.WithTimeout(context.Background(), autoBatchTimeout)
	defer cancel()
	results := autoselect.MeasureAll(ctx, targets, measure, autoConcurrency, timeout)
	method := string(autoMethod(cfg))
	checkedAt := time.Now().Unix()
	if len(results) > 0 {
		_ = c.st().UpdateServerLatencyMethod(results, method, checkedAt)
	}

	running := c.tunnel.Running()
	current := ""
	if running {
		if status := c.tunnel.Status(); status.Server != nil {
			current = status.Server.ID
		}
	}
	currentMs, known := results[current]
	if current != "" && !known {
		currentMs = autoselect.Failed // gone from the list
	}
	if current != "" && known && currentMs < 0 {
		// One lost answer is not a dead server. Ask again before moving, or a
		// single dropped packet would flap the tunnel.
		for _, server := range targets {
			if server.ID == current {
				one, stop := context.WithTimeout(ctx, timeout)
				currentMs = measure(one, server, timeout)
				stop()
				if currentMs >= 0 {
					results[current] = currentMs
					_ = c.st().UpdateServerLatencyMethod(map[string]int{current: currentMs}, method, time.Now().Unix())
				}
				break
			}
		}
	}
	bestID, bestMs, bestOK := autoselect.Best(targets, results)
	switchTo, reason := autoselect.Decide(current, currentMs, bestID, bestMs, bestOK, autoselect.DefaultHysteresis)

	byID := make(map[string]model.Server, len(targets))
	for _, server := range targets {
		byID[server.ID] = server
	}
	finish := func(id, state string, err error) (AutoState, error) {
		server := byID[id]
		ms, measured := results[id]
		if !measured {
			ms = autoselect.Failed
		}
		snapshot := c.setAuto(func(s *AutoState) {
			s.State, s.Reason, s.Measured, s.Total = state, string(reason), len(results), len(targets)
			s.ServerID, s.ServerName, s.Flag = server.ID, server.Name, server.Flag
			s.LatencyMs, s.Method, s.CheckedAt = ms, method, checkedAt
			s.Error = ""
			if err != nil {
				s.Error = err.Error()
			}
		})
		return snapshot, err
	}

	if !bestOK {
		return finish(current, "no_answer", nil)
	}
	if switchTo == "" {
		if !running && connect {
			switchTo = bestID
		} else {
			return finish(orDefault(current, bestID), "ready", nil)
		}
	}
	if !running && !connect {
		// Nothing is running: only remember the pick for the next connect.
		_ = c.st().Select(switchTo)
		return finish(switchTo, "ready", nil)
	}

	// Try the best few in order: a server can answer a probe and still refuse
	// a tunnel, and the next fastest is a better answer than an error.
	candidates := rankAnswered(targets, results, switchTo)
	var lastErr error
	for i, id := range candidates {
		if i >= autoStartAttempts {
			break
		}
		if err := c.st().Select(id); err != nil {
			lastErr = err
			continue
		}
		selected := c.st().Selected()
		if selected == nil {
			continue
		}
		if err := c.tunnel.Start(*selected, c.st().Settings()); err != nil {
			lastErr = err
			continue
		}
		return finish(id, "ready", nil)
	}
	if lastErr == nil {
		lastErr = errors.New("nebulalink: no server accepted the connection")
	}
	return finish(switchTo, "failed", lastErr)
}

// rankAnswered lists answering servers fastest first, with first at the head.
func rankAnswered(servers []model.Server, results map[string]int, first string) []string {
	var ids []string
	for _, server := range servers {
		if ms, ok := results[server.ID]; ok && ms >= 0 && server.ID != first {
			ids = append(ids, server.ID)
		}
	}
	sort.SliceStable(ids, func(i, j int) bool { return results[ids[i]] < results[ids[j]] })
	return append([]string{first}, ids...)
}

// startAutoPilot runs the periodic re-check and reacts to connection failures.
// Called once from core.init.
func (c *Core) startAutoPilot() {
	c.auto.once.Do(func() {
		c.tunnel.Observe(func(status model.Status) {
			// Failures of the check's own attempts are handled by the check.
			if status.State != model.StateFailed || c.auto.checking.Load() || c.st() == nil || !c.st().Settings().AutoSelect {
				return
			}
			if !c.spendFailureBudget() {
				return
			}
			// From a goroutine: observers run under the manager's notify, and
			// a check started inside its own Start failure would deadlock.
			go func() { _, _ = c.autoCheck("failure", true, false) }()
		})
		go func() {
			ticker := time.NewTicker(autoTick)
			defer ticker.Stop()
			for range ticker.C {
				c.autoTickOnce(time.Now())
			}
		}()
	})
}

func (c *Core) autoTickOnce(now time.Time) {
	st := c.st()
	if st == nil {
		return
	}
	cfg := st.Settings()
	if !cfg.AutoSelect || !c.tunnel.Running() {
		return
	}
	c.auto.mu.Lock()
	due := now.Sub(c.auto.last) >= time.Duration(cfg.AutoSelectMin)*time.Minute
	c.auto.mu.Unlock()
	if !due {
		return
	}
	// A manual "check servers" batch already measures everything; do not
	// stack a second one on top of it.
	c.probeMu.Lock()
	busy := c.probing != nil
	c.probeMu.Unlock()
	if busy {
		return
	}
	_, _ = c.autoCheck("periodic", false, false)
}

func (c *Core) spendFailureBudget() bool {
	c.auto.mu.Lock()
	defer c.auto.mu.Unlock()
	now := time.Now()
	kept := c.auto.failures[:0]
	for _, at := range c.auto.failures {
		if now.Sub(at) < autoFailureWindow {
			kept = append(kept, at)
		}
	}
	c.auto.failures = kept
	if len(kept) >= autoFailureBudget {
		return false
	}
	c.auto.failures = append(c.auto.failures, now)
	return true
}

// --- handlers ---------------------------------------------------------------

func (c *Core) handleAutoStatus([]byte) (any, error) {
	return c.autoSnapshot(), nil
}

// handleAutoSet turns auto on or off. Turning it on measures at once and, when
// the tunnel is up, moves it to the fastest server.
func (c *Core) handleAutoSet(payload []byte) (any, error) {
	var req struct {
		Enabled bool `json:"enabled"`
	}
	if err := decode(payload, &req); err != nil {
		return nil, err
	}
	if _, err := c.st().UpdateSettings(func(s *settings.Settings) { s.AutoSelect = req.Enabled }); err != nil {
		return nil, err
	}
	if !req.Enabled {
		return c.setAuto(func(s *AutoState) { s.State = "idle" }), nil
	}
	return c.autoCheck("enabled", c.tunnel.Running(), true)
}

func (c *Core) handleAutoCheck([]byte) (any, error) {
	if !c.st().Settings().AutoSelect {
		return nil, errors.New("nebulalink: automatic selection is off")
	}
	return c.autoCheck("manual", c.tunnel.Running(), true)
}
