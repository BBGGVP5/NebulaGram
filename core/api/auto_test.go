package api

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
	"github.com/nebulagram/nebulagram/core/tunnel"
)

type recordingInstance struct {
	mu      *sync.Mutex
	configs *[]string
}

func (f recordingInstance) Start(config []byte) error {
	f.mu.Lock()
	*f.configs = append(*f.configs, string(config))
	f.mu.Unlock()
	return nil
}
func (recordingInstance) Stop() error                  { return nil }
func (recordingInstance) Stats() (int64, int64, error) { return 0, 0, nil }
func (recordingInstance) Version() string              { return "fake" }

// withFakeXray registers a core that only records the configs it was given.
func withFakeXray(t *testing.T) func() []string {
	t.Helper()
	var mu sync.Mutex
	var configs []string
	tunnel.Register(model.EngineXray, func() tunnel.Instance { return recordingInstance{mu: &mu, configs: &configs} })
	t.Cleanup(func() { tunnel.Register(model.EngineXray, nil) })
	return func() []string {
		mu.Lock()
		defer mu.Unlock()
		return append([]string(nil), configs...)
	}
}

func addThree(t *testing.T, c *Core) map[string]string {
	t.Helper()
	payload, _ := json.Marshal(linkRequest{Link: "vless://u@de.example.com:443#DE\nvless://u@nl.example.com:443#NL\nvless://u@fi.example.com:443#FI"})
	call(t, c, "server.addLink", string(payload))
	ids := map[string]string{}
	for _, s := range c.st().Servers() {
		ids[s.Name] = s.ID
	}
	if len(ids) != 3 {
		t.Fatalf("servers = %v", ids)
	}
	return ids
}

type latencyTable struct {
	mu sync.Mutex
	ms map[string]int
}

func (l *latencyTable) set(name string, ms int) {
	l.mu.Lock()
	l.ms[name] = ms
	l.mu.Unlock()
}

func (l *latencyTable) measure(_ context.Context, s model.Server, _ time.Duration) int {
	l.mu.Lock()
	defer l.mu.Unlock()
	if ms, ok := l.ms[s.Name]; ok {
		return ms
	}
	return -1
}

func TestAutoConnectsToFastestAndKeepsItUnderJitter(t *testing.T) {
	withFakeXray(t)
	c := newCore(t)
	ids := addThree(t, c)
	latency := &latencyTable{ms: map[string]int{"DE": 120, "NL": 60, "FI": 300}}
	c.auto.measure = latency.measure

	var state AutoState
	if err := json.Unmarshal(call(t, c, "auto.set", `{"enabled":true}`), &state); err != nil {
		t.Fatal(err)
	}
	if state.ServerID != ids["NL"] || state.LatencyMs != 60 || state.State != "ready" || !state.Enabled {
		t.Fatalf("auto picked %+v, want NL at 60 ms", state)
	}
	if c.tunnel.Running() {
		t.Fatal("enabling auto must not connect a stopped tunnel")
	}
	call(t, c, "tunnel.start", "")
	if status := c.tunnel.Status(); status.Server == nil || status.Server.ID != ids["NL"] {
		t.Fatalf("connected to %+v, want NL", status.Server)
	}

	// DE becomes slightly faster: within the hysteresis, stay.
	latency.set("DE", 40)
	call(t, c, "auto.check", "")
	if c.tunnel.Status().Server.ID != ids["NL"] {
		t.Fatal("auto flapped on a 20 ms gain")
	}
	// NL degrades badly: move.
	latency.set("NL", 400)
	call(t, c, "auto.check", "")
	if c.tunnel.Status().Server.ID != ids["DE"] {
		t.Fatalf("auto stayed on a slow server: %+v", c.tunnel.Status().Server)
	}
	// DE stops answering: move to whatever answers.
	latency.set("DE", -1)
	if err := json.Unmarshal(call(t, c, "auto.check", ""), &state); err != nil {
		t.Fatal(err)
	}
	if c.tunnel.Status().Server.ID != ids["FI"] || state.Reason != "failed" {
		t.Fatalf("after a failure auto is on %+v (%+v)", c.tunnel.Status().Server, state)
	}
	if got := c.st().Settings().SelectedServerID; got != ids["FI"] {
		t.Errorf("the pick must become the selection, got %s", got)
	}

	// A manual choice overrules auto.
	call(t, c, "server.select", `{"id":"`+ids["NL"]+`"}`)
	if c.st().Settings().AutoSelect {
		t.Error("selecting a server by hand must turn auto off")
	}
}

func TestAutoFallsBackWhenNothingAnswers(t *testing.T) {
	withFakeXray(t)
	c := newCore(t)
	ids := addThree(t, c)
	c.auto.measure = (&latencyTable{ms: map[string]int{}}).measure
	call(t, c, "server.select", `{"id":"`+ids["FI"]+`"}`)
	call(t, c, "settings.set", `{"auto_select":true}`)
	call(t, c, "tunnel.start", "")
	if status := c.tunnel.Status(); status.Server == nil || status.Server.ID != ids["FI"] {
		t.Fatalf("with no answers the last pick must still connect: %+v", status.Server)
	}
}

func TestRoutingSetValidatesAndReachesTheConfig(t *testing.T) {
	configs := withFakeXray(t)
	c := newCore(t)
	addThree(t, c)
	call(t, c, "tunnel.start", "")

	var resp Response
	_ = json.Unmarshal([]byte(c.Call("routing.set", `{"routing":{"rules":[{"match":"bad host","action":"direct"}]}}`)), &resp)
	if resp.OK {
		t.Fatal("an invalid rule was accepted")
	}
	call(t, c, "routing.set", `{"routing":{"rules":[{"match":"example.ru","action":"direct"}]}}`)
	all := configs()
	last := all[len(all)-1]
	if !strings.Contains(last, "domain:example.ru") || !strings.Contains(last, "149.154.160.0/20") {
		t.Fatal("the running tunnel was not restarted with the new rules")
	}
	call(t, c, "routing.preset", `{"id":"telegram_only"}`)
	var cfg settings.Settings
	_ = json.Unmarshal(call(t, c, "settings.get", ""), &cfg)
	if cfg.Routing.Default != model.RouteDirect || cfg.Routing.Preset != model.PresetTelegramOnly {
		t.Errorf("preset not stored: %+v", cfg.Routing)
	}
	var view struct {
		Presets []model.RoutePreset `json:"presets"`
	}
	_ = json.Unmarshal(call(t, c, "routing.get", ""), &view)
	if len(view.Presets) < 2 {
		t.Error("routing.get must list the presets")
	}
}
