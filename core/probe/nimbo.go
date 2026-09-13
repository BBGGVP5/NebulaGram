package probe

import (
	"context"
	"errors"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
)

// NimboFunc measures a real GET through this server, not the active proxy.
// Implementations own routing, deadline and cleanup and return raw milliseconds.
type NimboFunc func(context.Context, model.Server, string, time.Duration) (int, error)

var nimbo struct {
	sync.RWMutex
	probe NimboFunc
}

// RegisterNimbo keeps the core independent of the concrete Xray runtime.
func RegisterNimbo(f NimboFunc) {
	nimbo.Lock()
	nimbo.probe = f
	nimbo.Unlock()
}

func Nimbo(ctx context.Context, server model.Server, target string, timeout time.Duration) (int, error) {
	nimbo.RLock()
	f := nimbo.probe
	nimbo.RUnlock()
	if f == nil {
		return Failed, errors.New("NIMBO_UNAVAILABLE")
	}
	if target == "" {
		target = DefaultTestURL
	}
	return f(ctx, server, target, timeout)
}
