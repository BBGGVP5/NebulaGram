// Package tunnel owns the lifecycle of the running proxy.
//
// The actual cores are not linked in here. Xray-core and sing-box are heavy Go
// libraries whose APIs move between releases, so each is wrapped behind the
// Instance interface and registered at startup (see runtime/xray and
// runtime/singbox). That keeps this package — and every client that talks to
// it — stable when a core is upgraded.
package tunnel

import (
	"errors"
	"fmt"
	"net"
	"strconv"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
	"github.com/nebulagram/nebulagram/core/singboxcfg"
	"github.com/nebulagram/nebulagram/core/xraycfg"
)

// Instance is one running core. Implementations must be safe to call from any
// goroutine and must tolerate Stop on an already-stopped instance.
type Instance interface {
	Start(config []byte) error
	Stop() error
	Stats() (uplink, downlink int64, err error)
	Version() string
}

// Factory creates a fresh instance of a core.
type Factory func() Instance

var (
	registryMu sync.RWMutex
	registry   = map[model.Engine]Factory{}
)

// Register makes a core available to the manager. Platform code calls this
// once during startup.
func Register(engine model.Engine, factory Factory) {
	registryMu.Lock()
	defer registryMu.Unlock()
	registry[engine] = factory
}

// Available reports whether a core is registered.
func Available(engine model.Engine) bool {
	registryMu.RLock()
	defer registryMu.RUnlock()
	return registry[engine] != nil
}

// Versions returns the version string of every registered core.
func Versions() map[string]string {
	registryMu.RLock()
	defer registryMu.RUnlock()
	out := make(map[string]string, len(registry))
	for engine, factory := range registry {
		out[string(engine)] = factory().Version()
	}
	return out
}

func factoryFor(engine model.Engine) (Factory, error) {
	registryMu.RLock()
	defer registryMu.RUnlock()
	f := registry[engine]
	if f == nil {
		return nil, fmt.Errorf("tunnel: core %q is not available in this build", engine)
	}
	return f, nil
}

// Observer is notified on every state change, so clients can update the
// connection card without polling.
type Observer func(model.Status)

// Manager starts and stops the tunnel and owns the current status.
type Manager struct {
	mu       sync.Mutex
	instance Instance
	engine   model.Engine
	status   model.Status

	observerMu sync.RWMutex
	observers  []Observer
}

// New returns a stopped manager.
func New() *Manager {
	return &Manager{status: model.Status{State: model.StateDisconnected}}
}

// Observe registers a status listener. The listener is called immediately with
// the current status.
func (m *Manager) Observe(o Observer) {
	m.observerMu.Lock()
	m.observers = append(m.observers, o)
	m.observerMu.Unlock()
	o(m.Status())
}

// Status returns the current snapshot, refreshing the traffic counters.
func (m *Manager) Status() model.Status {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.instance != nil && m.status.State == model.StateConnected {
		if up, down, err := m.instance.Stats(); err == nil {
			m.status.Uplink, m.status.Downlink = up, down
		}
	}
	return m.status
}

// Start brings the tunnel up on the given server. An already-running tunnel is
// stopped first, so the caller can treat Start as "switch to this server".
func (m *Manager) Start(server model.Server, cfg settings.Settings) error {
	engine := server.Engine()
	factory, err := factoryFor(engine)
	if err != nil {
		m.fail(server, err)
		return err
	}
	// The configured port may be taken, or reserved by the system — Windows
	// hands whole ranges to Hyper-V, and another proxy app may already hold the
	// usual one. Falling back to a free port keeps the tunnel usable; the
	// clients read the port they should proxy through from the status event.
	socksPort, err := resolvePort(cfg.SocksPort)
	if err != nil {
		m.fail(server, err)
		return err
	}
	httpPort := 0
	if cfg.HTTPPort > 0 {
		if httpPort, err = resolvePort(cfg.HTTPPort); err != nil {
			m.fail(server, err)
			return err
		}
	}

	config, err := buildConfig(server, cfg, socksPort, httpPort)
	if err != nil {
		m.fail(server, err)
		return err
	}

	shown := server.ForClient()

	m.mu.Lock()
	if m.instance != nil {
		_ = m.instance.Stop()
		m.instance = nil
	}
	m.status = model.Status{
		State:     model.StateConnecting,
		Server:    &shown,
		Engine:    engine,
		Mode:      string(cfg.Mode),
		SocksPort: socksPort,
		HTTPPort:  httpPort,
	}
	m.mu.Unlock()
	m.notify()

	instance := factory()
	if err := instance.Start(config); err != nil {
		wrapped := fmt.Errorf("tunnel: %s failed to start: %w", engine, err)
		m.fail(server, wrapped)
		return wrapped
	}

	m.mu.Lock()
	m.instance = instance
	m.engine = engine
	m.status.State = model.StateConnected
	m.status.Since = time.Now().Unix()
	m.status.CoreVer = instance.Version()
	m.status.LastError = ""
	m.mu.Unlock()
	m.notify()
	return nil
}

// Stop tears the tunnel down. Stopping a stopped tunnel is not an error.
func (m *Manager) Stop() error {
	m.mu.Lock()
	instance := m.instance
	m.instance = nil
	server := m.status.Server
	mode := m.status.Mode
	m.status = model.Status{State: model.StateDisconnected, Server: server, Mode: mode}
	m.mu.Unlock()

	var err error
	if instance != nil {
		err = instance.Stop()
	}
	m.notify()
	return err
}

// Running reports whether the tunnel is up.
func (m *Manager) Running() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.status.State == model.StateConnected
}

func (m *Manager) fail(server model.Server, err error) {
	shown := server.ForClient()
	m.mu.Lock()
	m.status = model.Status{
		State:     model.StateFailed,
		Server:    &shown,
		Engine:    server.Engine(),
		LastError: err.Error(),
	}
	m.mu.Unlock()
	m.notify()
}

func (m *Manager) notify() {
	status := m.Status()
	m.observerMu.RLock()
	observers := append([]Observer(nil), m.observers...)
	m.observerMu.RUnlock()
	for _, o := range observers {
		o(status)
	}
}

// resolvePort returns the wanted port when it can be bound, and a free one
// otherwise. There is an unavoidable gap between the check and the core
// binding, but nothing else on the device is racing us for an ephemeral port.
func resolvePort(wanted int) (int, error) {
	if wanted > 0 {
		listener, err := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(wanted)))
		if err == nil {
			_ = listener.Close()
			return wanted, nil
		}
	}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, fmt.Errorf("tunnel: no local port is available: %w", err)
	}
	defer listener.Close()
	return listener.Addr().(*net.TCPAddr).Port, nil
}

// buildConfig renders the core config for a server.
func buildConfig(server model.Server, cfg settings.Settings, socksPort, httpPort int) ([]byte, error) {
	if socksPort <= 0 {
		return nil, errors.New("tunnel: socks port is not configured")
	}
	// A pasted configuration is run as the user wrote it; we only graft our
	// local inbound onto it.
	if server.Config != "" {
		switch server.Engine() {
		case model.EngineXray:
			opts := xraycfg.Defaults(socksPort)
			opts.HTTPPort = httpPort
			opts.LogLevel = cfg.LogLevel
			return xraycfg.Normalize([]byte(server.Config), opts)
		case model.EngineSingBox:
			opts := singboxcfg.Defaults(socksPort)
			opts.HTTPPort = httpPort
			return singboxcfg.Normalize([]byte(server.Config), server.ConfigTag, opts)
		default:
			return nil, fmt.Errorf("tunnel: %s configurations are not supported yet", server.Engine())
		}
	}

	switch server.Engine() {
	case model.EngineXray:
		opts := xraycfg.Defaults(socksPort)
		opts.HTTPPort = httpPort
		opts.LogLevel = cfg.LogLevel
		opts.DNS = cfg.DNS
		return xraycfg.Build(server, opts)
	case model.EngineSingBox:
		opts := singboxcfg.Defaults(socksPort)
		opts.HTTPPort = httpPort
		opts.DNS = cfg.DNS
		return singboxcfg.Build(server, opts)
	default:
		return nil, fmt.Errorf("tunnel: no core handles %q", server.Protocol)
	}
}
