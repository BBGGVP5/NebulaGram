// Package xray runs Xray-core inside NebulaLink.
//
// It is the only place in the repository that knows the Xray API, which is why
// it lives in its own module: an Xray upgrade touches this file and nothing
// else. The core module stays dependency-free and every client keeps talking to
// the same tunnel.Instance interface.
package xray

import (
	"bytes"
	"errors"
	"fmt"
	"sync"

	xraycore "github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/stats"
	"github.com/xtls/xray-core/infra/conf/serial"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/tunnel"
)

// Counter names Xray publishes for the proxy outbound when the generated
// config enables statistics (see xraycfg.Options.StatsAPI).
const (
	uplinkCounter   = "outbound>>>proxy>>>traffic>>>uplink"
	downlinkCounter = "outbound>>>proxy>>>traffic>>>downlink"
)

// Register makes this core available to the tunnel manager. Every client calls
// it once during startup.
func Register() {
	tunnel.Register(model.EngineXray, func() tunnel.Instance { return &instance{} })
}

// instance is one running Xray server.
type instance struct {
	mu      sync.Mutex
	server  *xraycore.Instance
	manager stats.Manager
}

// Start parses the generated JSON config and brings the server up. Starting an
// already-running instance is refused rather than silently leaking the old one.
func (i *instance) Start(config []byte) error {
	i.mu.Lock()
	defer i.mu.Unlock()

	if i.server != nil {
		return errors.New("xray: instance is already running")
	}
	if len(config) == 0 {
		return errors.New("xray: empty configuration")
	}

	parsed, err := serial.LoadJSONConfig(bytes.NewReader(config))
	if err != nil {
		return fmt.Errorf("xray: cannot parse configuration: %w", err)
	}
	server, err := xraycore.New(parsed)
	if err != nil {
		return fmt.Errorf("xray: cannot create instance: %w", err)
	}
	if err := server.Start(); err != nil {
		_ = server.Close()
		return fmt.Errorf("xray: cannot start: %w", err)
	}

	i.server = server
	// Statistics are optional: a config built without them still runs, the
	// traffic counters simply stay at zero.
	if manager, ok := server.GetFeature(stats.ManagerType()).(stats.Manager); ok {
		i.manager = manager
	}
	return nil
}

// Stop shuts the server down. Stopping a stopped instance is not an error, so
// the manager can call it unconditionally.
func (i *instance) Stop() error {
	i.mu.Lock()
	defer i.mu.Unlock()

	if i.server == nil {
		return nil
	}
	err := i.server.Close()
	i.server = nil
	i.manager = nil
	if err != nil {
		return fmt.Errorf("xray: cannot stop: %w", err)
	}
	return nil
}

// Stats reports bytes sent and received through the proxy outbound.
func (i *instance) Stats() (uplink, downlink int64, err error) {
	i.mu.Lock()
	defer i.mu.Unlock()

	if i.server == nil {
		return 0, 0, errors.New("xray: instance is not running")
	}
	if i.manager == nil {
		return 0, 0, nil
	}
	return counterValue(i.manager, uplinkCounter), counterValue(i.manager, downlinkCounter), nil
}

func counterValue(manager stats.Manager, name string) int64 {
	counter := manager.GetCounter(name)
	if counter == nil {
		return 0
	}
	return counter.Value()
}

// Version reports the linked Xray-core version, shown on the About screen.
func (i *instance) Version() string { return xraycore.Version() }
