package autoselect

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
)

func servers(ids ...string) []model.Server {
	out := make([]model.Server, len(ids))
	for i, id := range ids {
		out[i] = model.Server{ID: id}
	}
	return out
}

func TestMeasureAllCapsConcurrencyAndTimesOut(t *testing.T) {
	var running, peak int32
	measure := func(ctx context.Context, s model.Server, _ time.Duration) int {
		n := atomic.AddInt32(&running, 1)
		defer atomic.AddInt32(&running, -1)
		for {
			p := atomic.LoadInt32(&peak)
			if n <= p || atomic.CompareAndSwapInt32(&peak, p, n) {
				break
			}
		}
		if s.ID == "slow" {
			<-ctx.Done()
			return Failed
		}
		time.Sleep(5 * time.Millisecond)
		return 40
	}
	list := servers("a", "b", "c", "d", "e", "f", "slow")
	start := time.Now()
	results := MeasureAll(context.Background(), list, measure, 3, 50*time.Millisecond)
	if atomic.LoadInt32(&peak) > 3 {
		t.Errorf("peak concurrency %d, cap 3", peak)
	}
	if time.Since(start) > 2*time.Second {
		t.Error("the per-server timeout did not apply")
	}
	if results["slow"] != Failed || results["a"] != 40 || len(results) != len(list) {
		t.Errorf("results = %v", results)
	}
}

func TestMeasureAllCancelledLeavesServersUnmeasured(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	results := MeasureAll(ctx, servers("a", "b"), func(context.Context, model.Server, time.Duration) int { return 1 }, 1, time.Second)
	if len(results) != 0 {
		t.Errorf("cancelled check reported %v", results)
	}
}

func TestBest(t *testing.T) {
	list := servers("a", "b", "c", "d")
	id, ms, ok := Best(list, map[string]int{"a": 120, "b": Failed, "c": 80, "d": 80})
	if !ok || id != "c" || ms != 80 {
		t.Errorf("Best = %s %d %v, want c 80 (ties keep list order)", id, ms, ok)
	}
	if _, _, ok := Best(list, map[string]int{"a": Failed}); ok {
		t.Error("no answer must not produce a pick")
	}
}

func TestDecideHysteresis(t *testing.T) {
	cases := []struct {
		name       string
		current    string
		currentMs  int
		best       string
		bestMs     int
		bestOK     bool
		wantSwitch string
		wantReason Reason
	}{
		{"first pick", "", 0, "b", 50, true, "b", ReasonNoCurrent},
		{"already best", "a", 50, "a", 50, true, "", ReasonNone},
		{"jitter on a fast server", "a", 90, "b", 60, true, "", ReasonNone},
		{"small relative gain on a slow server", "a", 400, "b", 340, true, "", ReasonNone},
		{"clearly faster", "a", 400, "b", 200, true, "b", ReasonFaster},
		{"current failed", "a", Failed, "b", 900, true, "b", ReasonFailed},
		{"nothing answered", "a", Failed, "", Failed, false, "", ReasonNoneAnswer},
	}
	for _, tc := range cases {
		got, reason := Decide(tc.current, tc.currentMs, tc.best, tc.bestMs, tc.bestOK, DefaultHysteresis)
		if got != tc.wantSwitch || reason != tc.wantReason {
			t.Errorf("%s: Decide = %q %q, want %q %q", tc.name, got, reason, tc.wantSwitch, tc.wantReason)
		}
	}
}
