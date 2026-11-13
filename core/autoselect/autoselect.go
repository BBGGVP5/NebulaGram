// Package autoselect picks the fastest server of a list and decides when the
// tunnel should move to another one.
//
// It holds no state of its own and starts nothing: the api package measures,
// asks Decide, and switches. Keeping the decision a pure function is what lets
// the hysteresis be tested without a network.
package autoselect

import (
	"context"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
)

// Failed is the latency of a server that did not answer.
const Failed = -1

// MeasureFunc measures one server and returns milliseconds, or Failed. It must
// return once ctx is done.
type MeasureFunc func(ctx context.Context, server model.Server, timeout time.Duration) int

// MeasureAll measures every server, at most concurrency at a time, each within
// timeout. A server left unmeasured because ctx ended is absent from the result
// rather than reported as failed, so a cancelled check never condemns anyone.
func MeasureAll(ctx context.Context, servers []model.Server, measure MeasureFunc, concurrency int, timeout time.Duration) map[string]int {
	if concurrency <= 0 {
		concurrency = 8
	}
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	var (
		mu      sync.Mutex
		wg      sync.WaitGroup
		results = make(map[string]int, len(servers))
		sem     = make(chan struct{}, concurrency)
	)
	for _, server := range servers {
		wg.Add(1)
		go func(server model.Server) {
			defer wg.Done()
			select {
			case sem <- struct{}{}:
			case <-ctx.Done():
				return
			}
			defer func() { <-sem }()
			if ctx.Err() != nil {
				return
			}
			one, cancel := context.WithTimeout(ctx, timeout)
			ms := measure(one, server, timeout)
			cancel()
			if ctx.Err() != nil {
				return
			}
			if ms < 0 {
				ms = Failed
			}
			mu.Lock()
			results[server.ID] = ms
			mu.Unlock()
		}(server)
	}
	wg.Wait()
	return results
}

// Best returns the fastest answering server. Ties go to the earlier server in
// list order, so the pick is stable across identical measurements.
func Best(servers []model.Server, results map[string]int) (id string, ms int, ok bool) {
	ms = Failed
	for _, server := range servers {
		value, measured := results[server.ID]
		if !measured || value < 0 {
			continue
		}
		if !ok || value < ms {
			id, ms, ok = server.ID, value, true
		}
	}
	return id, ms, ok
}

// Hysteresis is how much better another server must be before the tunnel
// moves. Both conditions must hold: an absolute gain filters jitter on fast
// servers, a relative gain filters it on slow ones.
type Hysteresis struct {
	MinGainMs    int
	MinGainRatio float64 // 0.25 = at least 25% faster
}

// DefaultHysteresis is what NebulaLink ships with.
var DefaultHysteresis = Hysteresis{MinGainMs: 50, MinGainRatio: 0.25}

// Reason explains a decision, for the status line and the tests.
type Reason string

const (
	ReasonNone       Reason = ""          // nothing to do
	ReasonNoCurrent  Reason = "initial"   // nothing chosen yet
	ReasonFailed     Reason = "failed"    // the current server stopped answering
	ReasonFaster     Reason = "faster"    // another one is clearly faster
	ReasonNoneAnswer Reason = "no_answer" // nothing answered; stay put
)

// Decide says whether to move from current to the best measured server.
// currentMs is the fresh measurement of the current server (Failed when it did
// not answer, or when it is no longer in the list).
func Decide(currentID string, currentMs int, bestID string, bestMs int, bestOK bool, h Hysteresis) (switchTo string, reason Reason) {
	if !bestOK {
		return "", ReasonNoneAnswer
	}
	if currentID == "" {
		return bestID, ReasonNoCurrent
	}
	if bestID == currentID {
		return "", ReasonNone
	}
	if currentMs < 0 {
		return bestID, ReasonFailed
	}
	gain := currentMs - bestMs
	if gain >= h.MinGainMs && float64(bestMs) <= float64(currentMs)*(1-h.MinGainRatio) {
		return bestID, ReasonFaster
	}
	return "", ReasonNone
}
