package xray

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"net"
	"net/http"
	"net/url"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/xtls/xray-core/app/proxyman"
	"github.com/xtls/xray-core/common"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/net/cnc"
	"github.com/xtls/xray-core/common/serial"
	"github.com/xtls/xray-core/common/session"
	xcore "github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/features/outbound"
	"github.com/xtls/xray-core/features/stats"
	"github.com/xtls/xray-core/infra/conf"
	"github.com/xtls/xray-core/transport"
	"github.com/xtls/xray-core/transport/pipe"
)

// One coordinator covers every factory instance: Xray's DNS/system dialer are
// process globals. Connector transitions take priority over temporary probes.
var runtimeOwner = struct {
	lifecycle sync.Mutex
	mu        sync.Mutex
	active    *instance
	probe     *probeLease
	changing  bool
	changed   chan struct{}
	slot      chan struct{}
}{slot: make(chan struct{}, 1)}

type probeLease struct {
	server    *xcore.Instance
	temporary bool
	cancel    context.CancelFunc
	done      chan struct{}
}

// Returns with mu and lifecycle held. Never wait for cleanup while holding mu.
func beginTransition() {
	runtimeOwner.lifecycle.Lock()
	runtimeOwner.mu.Lock()
	runtimeOwner.changing = true
	runtimeOwner.changed = make(chan struct{})
	lease := runtimeOwner.probe
	if lease != nil {
		lease.cancel()
	}
	runtimeOwner.mu.Unlock()
	if lease != nil {
		<-lease.done
	}
	runtimeOwner.mu.Lock()
}

func endTransition() {
	runtimeOwner.changing = false
	close(runtimeOwner.changed)
	runtimeOwner.mu.Unlock()
	runtimeOwner.lifecycle.Unlock()
}

func borrowProbe(ctx context.Context, cancel context.CancelFunc) (*probeLease, error) {
	for {
		select {
		case runtimeOwner.slot <- struct{}{}:
		case <-ctx.Done():
			return nil, ctx.Err()
		}
		runtimeOwner.mu.Lock()
		if runtimeOwner.changing {
			changed := runtimeOwner.changed
			runtimeOwner.mu.Unlock()
			<-runtimeOwner.slot
			select {
			case <-changed:
				continue
			case <-ctx.Done():
				return nil, ctx.Err()
			}
		}
		if ctx.Err() != nil {
			runtimeOwner.mu.Unlock()
			<-runtimeOwner.slot
			return nil, ctx.Err()
		}
		lease := &probeLease{cancel: cancel, done: make(chan struct{})}
		runtimeOwner.probe = lease
		var err error
		if runtimeOwner.active != nil {
			lease.server = runtimeOwner.active.server
		} else {
			// No listeners, no routes, no user environment. This instance only
			// supplies pinned protocol features while the connector is stopped.
			var config *xcore.Config
			config, err = (&conf.Config{LogConfig: &conf.LogConfig{LogLevel: "none"}}).Build()
			if err == nil {
				lease.server, err = xcore.New(config)
			}
			lease.temporary = true
			if err == nil {
				err = lease.server.Start()
			}
		}
		runtimeOwner.mu.Unlock()
		if err != nil || lease.server == nil {
			lease.release()
			return nil, errors.New("NIMBO_SETUP")
		}
		return lease, nil
	}
}

func (lease *probeLease) release() {
	lease.cancel()
	if lease.temporary && lease.server != nil {
		_ = lease.server.Close()
	}
	runtimeOwner.mu.Lock()
	runtimeOwner.probe = nil
	close(lease.done)
	runtimeOwner.mu.Unlock()
	<-runtimeOwner.slot
}

// Obtain an instance context through the public CreateObject API rather than
// forging Xray's private context key or using go:linkname.
type probeContextConfig struct{}

func init() {
	common.Must(common.RegisterConfig((*probeContextConfig)(nil), func(ctx context.Context, _ interface{}) (interface{}, error) { return ctx, nil }))
}

type probeContext struct {
	context.Context
	values context.Context
}

func (c probeContext) Value(key any) any {
	if value := c.Context.Value(key); value != nil {
		return value
	}
	return c.values.Value(key)
}

// Nimbo measures raw GET-to-headers latency through exactly this server. A
// private handler borrows the live core but is NEVER registered in its outbound
// manager: live balancers cannot select it and the manager's tag cache is not
// mutated. No active proxy/route/stats selection is replaced.
func Nimbo(ctx context.Context, server model.Server, target string, timeout time.Duration) (int, error) {
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	if timeout > time.Minute {
		timeout = time.Minute
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	u, err := url.ParseRequestURI(target)
	if err != nil || u.Hostname() == "" || u.User != nil || u.Fragment != "" || (u.Scheme != "http" && u.Scheme != "https") || len(target) > 4096 {
		return -1, errors.New("NIMBO_URL")
	}
	var random [16]byte
	if _, err := rand.Read(random[:]); err != nil {
		return -1, errors.New("NIMBO_ID")
	}
	tag := "__nimbo_" + hex.EncodeToString(random[:])
	out, err := probeOutbound(server, tag)
	if err != nil {
		return -1, err
	}
	// Outbound.Build does not load a root Config or mutate process environment.
	config, err := out.Build()
	if err != nil {
		return -1, errors.New("NIMBO_CONFIG")
	}
	// Validate the original mux config, then substitute an owned, cancellable
	// worker. Pinned upstream ClientManager has no Close and creates workers
	// on context.Background(), so using it would outlive our runtime lease.
	senderRaw, err := config.SenderSettings.GetInstance()
	if err != nil {
		return -1, errors.New("NIMBO_CONFIG")
	}
	sender := senderRaw.(*proxyman.SenderConfig)
	muxConfig := sender.MultiplexSettings
	sender.MultiplexSettings = nil
	config.SenderSettings = serial.ToTypedMessage(sender)
	lease, err := borrowProbe(ctx, cancel)
	if err != nil {
		return -1, err
	}
	defer lease.release()
	if ctx.Err() != nil {
		return -1, ctx.Err()
	}
	// Handler construction may register counters under its unique tag. Remove
	// only those counters, after dispatch/handler cleanup; active counters remain.
	if sm, ok := lease.server.GetFeature(stats.ManagerType()).(stats.Manager); ok {
		defer func() {
			_ = sm.UnregisterCounter("outbound>>>" + tag + ">>>traffic>>>uplink")
			_ = sm.UnregisterCounter("outbound>>>" + tag + ">>>traffic>>>downlink")
		}()
	}
	raw, err := xcore.CreateObject(lease.server, config)
	if err != nil {
		return -1, errors.New("NIMBO_HANDLER")
	}
	handler, ok := raw.(outbound.Handler)
	if !ok {
		return -1, errors.New("NIMBO_HANDLER")
	}
	defer handler.Close()
	if err := handler.Start(); err != nil {
		return -1, errors.New("NIMBO_HANDLER")
	}
	values, err := xcore.CreateObject(lease.server, &probeContextConfig{})
	if err != nil {
		return -1, errors.New("NIMBO_CONTEXT")
	}
	ctx = probeContext{Context: ctx, values: values.(context.Context)}
	if muxConfig != nil && muxConfig.Enabled && muxConfig.Concurrency >= 0 {
		owned := &probeMux{Handler: handler, ctx: ctx, strategy: sender.TargetStrategy, concurrency: muxConfig.Concurrency}
		defer owned.Close()
		handler = owned
	}
	return requestThroughHandler(ctx, handler, target, tag)
}

type probeConnections struct {
	sync.Mutex
	closed      bool
	connections []net.Conn
	workers     sync.WaitGroup
}

func (p *probeConnections) close() {
	p.Lock()
	defer p.Unlock()
	p.closed = true
	for _, c := range p.connections {
		_ = c.Close()
	}
	p.connections = nil
}

func requestThroughHandler(ctx context.Context, handler outbound.Handler, target, tag string) (int, error) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	connections := &probeConnections{}
	defer func() { cancel(); connections.close(); connections.workers.Wait() }()
	stop := context.AfterFunc(ctx, connections.close)
	defer stop()
	tr := &http.Transport{DisableKeepAlives: true, MaxResponseHeaderBytes: 32 * 1024,
		DialContext: func(_ context.Context, network, address string) (net.Conn, error) {
			if network != "tcp" && network != "tcp4" && network != "tcp6" {
				return nil, errors.New("NIMBO_NETWORK")
			}
			destination, err := xnet.ParseDestination("tcp:" + address)
			if err != nil {
				return nil, err
			}
			connections.Lock()
			defer connections.Unlock()
			if connections.closed || ctx.Err() != nil {
				return nil, context.Canceled
			}
			upReader, upWriter := pipe.New(pipe.WithSizeLimit(64 * 1024))
			downReader, downWriter := pipe.New(pipe.WithSizeLimit(64 * 1024))
			conn := cnc.NewConnection(cnc.ConnectionInputMulti(upWriter), cnc.ConnectionOutputMulti(downReader))
			connections.connections = append(connections.connections, conn)
			streamCtx := session.ContextWithOutbounds(ctx, []*session.Outbound{{Target: destination, OriginalTarget: destination, Tag: tag}})
			streamCtx = session.ContextWithContent(streamCtx, new(session.Content))
			connections.workers.Add(1)
			go func() {
				defer connections.workers.Done()
				// Mux Dispatch transfers ownership and returns before the HTTP
				// stream finishes. Interrupt only on panic, not a normal return.
				defer func() {
					if recover() != nil {
						common.Interrupt(upReader)
						common.Interrupt(downWriter)
					}
				}()
				handler.Dispatch(streamCtx, &transport.Link{Reader: upReader, Writer: downWriter})
			}()
			return conn, nil
		}}
	defer tr.CloseIdleConnections()
	client := &http.Client{Transport: tr, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return -1, errors.New("NIMBO_URL")
	}
	req.Header.Set("User-Agent", "NebulaLink-Nimbo/1")
	started := time.Now()
	response, err := client.Do(req)
	elapsed := int(time.Since(started).Milliseconds())
	if err != nil {
		return -1, errors.New("NIMBO_NETWORK")
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return -1, errors.New("NIMBO_HTTP_STATUS")
	}
	return elapsed, nil
}
