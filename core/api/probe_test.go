package api

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/probe"
)

func TestNimboBatchRawProvenanceZeroAndMethodSnapshot(t *testing.T) {
	c := newCore(t)
	_, err := c.st().AddServers([]model.Server{{ID: "a"}, {ID: "b"}})
	if err != nil {
		t.Fatal(err)
	}
	entered := make(chan struct{})
	release := make(chan struct{})
	probe.RegisterNimbo(func(ctx context.Context, s model.Server, url string, d time.Duration) (int, error) {
		if s.ID == "a" {
			close(entered)
			select {
			case <-release:
			case <-ctx.Done():
				return -1, ctx.Err()
			}
			return 0, nil
		}
		return 330, nil
	})
	defer probe.RegisterNimbo(nil)
	var events []string
	c.SetEventSink(func(s string) { events = append(events, s) })
	result := make(chan string, 1)
	go func() { result <- c.Call("probe.servers", `{"request_id":"raw","method":"nimbo"}`) }()
	<-entered
	call(t, c, "settings.set", `{"ping_type":"tcp"}`)
	close(release)
	var response Response
	json.Unmarshal([]byte(<-result), &response)
	if !response.OK || string(response.Data) != `{"a":0,"b":330}` {
		t.Fatalf("raw changed: %+v", response)
	}
	for _, s := range c.st().Servers() {
		if s.LatencyMethod != "nimbo" || s.CheckedAt <= 0 {
			t.Fatalf("lost snapshot: %+v", s)
		}
	}
	for _, raw := range events {
		var e struct {
			Event string
			Data  struct {
				LatencyMethod string `json:"latency_method"`
				CheckedAt     int64  `json:"checked_at"`
				ID            string
			}
		}
		json.Unmarshal([]byte(raw), &e)
		if e.Event == "probe.progress" && (e.Data.LatencyMethod != "nimbo" || e.Data.ID != "" && e.Data.CheckedAt <= 0) {
			t.Fatalf("bad progress: %s", raw)
		}
	}
	listed := call(t, c, "servers.list", `{}`)
	var obj struct {
		Servers []model.Server `json:"servers"`
	}
	if err := json.Unmarshal(listed, &obj); err != nil {
		t.Fatal(err)
	}
	if len(obj.Servers) != 2 || obj.Servers[0].LatencyMethod != "nimbo" || obj.Servers[0].CheckedAt <= 0 || obj.Servers[0].LatencyMs != 0 {
		t.Fatalf("servers.list lost provenance: %s", listed)
	}
	// Model JSON always carries zero and its explicit measured presence.
	raw, _ := json.Marshal(c.st().Servers()[0])
	var zero map[string]any
	json.Unmarshal(raw, &zero)
	if _, ok := zero["latency_ms"]; !ok {
		t.Fatal("zero omitted")
	}
}

func TestNimboCancellationIDsPartialPersistenceAndPrecancel(t *testing.T) {
	c := newCore(t)
	c.st().AddServers([]model.Server{{ID: "a"}, {ID: "b"}, {ID: "c"}})
	c.st().UpdateServerLatencyMethod(map[string]int{"b": 9, "c": 8}, "tcp", 123)
	entered := make(chan struct{})
	probe.RegisterNimbo(func(ctx context.Context, s model.Server, _ string, _ time.Duration) (int, error) {
		if s.ID == "a" {
			return 33, nil
		}
		close(entered)
		<-ctx.Done()
		return -1, ctx.Err()
	})
	defer probe.RegisterNimbo(nil)
	result := make(chan string, 1)
	go func() { result <- c.Call("probe.servers", `{"request_id":"batch","method":"nimbo"}`) }()
	<-entered
	call(t, c, "probe.cancel", `{"request_id":"other"}`)
	select {
	case <-result:
		t.Fatal("wrong ID canceled active batch")
	default:
	}
	call(t, c, "probe.cancel", `{"request_id":"batch"}`)
	select {
	case raw := <-result:
		var r Response
		json.Unmarshal([]byte(raw), &r)
		if !r.OK || string(r.Data) != `{"a":33}` {
			t.Fatalf("partial: %s", raw)
		}
	case <-time.After(time.Second):
		t.Fatal("cancel blocked")
	}
	for _, s := range c.st().Servers() {
		if s.ID == "a" {
			if s.LatencyMethod != "nimbo" {
				t.Fatal("completed result lost")
			}
		} else if s.LatencyMethod != "tcp" || s.CheckedAt != 123 {
			t.Fatal("unvisited cache overwritten")
		}
	}
	call(t, c, "probe.cancel", `{"request_id":"early"}`)
	if string(call(t, c, "probe.servers", `{"request_id":"early","method":"nimbo"}`)) != `{}` {
		t.Fatal("pre-cancel ignored")
	}
}
