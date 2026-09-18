package xray

import (
	"context"
	"sync"

	"github.com/xtls/xray-core/common"
	"github.com/xtls/xray-core/common/dice"
	"github.com/xtls/xray-core/common/mux"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/session"
	"github.com/xtls/xray-core/features/outbound"
	"github.com/xtls/xray-core/transport"
	"github.com/xtls/xray-core/transport/internet"
	"github.com/xtls/xray-core/transport/pipe"
)

// A single GET needs one mux carrier, not an unbounded picker/cache. All carrier
// I/O is joined before releasing the core lease. This keeps the configured Mux
// wire protocol without upstream's background worker / non-Closable manager.
// UDP/XUDP is irrelevant: diagnostics only issue TCP HTTP(S) requests.
type probeMux struct {
	outbound.Handler
	ctx         context.Context
	strategy    internet.DomainStrategy
	concurrency int32
	mu          sync.Mutex
	worker      *mux.ClientWorker
	cancel      context.CancelFunc
	done        chan struct{}
	carrier     *transport.Link
}

func (m *probeMux) Dispatch(ctx context.Context, link *transport.Link) {
	ob := session.OutboundsFromContext(ctx)[0]
	// Mirror pinned Handler.Dispatch targetStrategy before multiplexing the real
	// target; the virtual v1.mux.cool carrier must never itself be DNS-resolved.
	if m.strategy.HasStrategy() && ob.Target.Address.Family().IsDomain() {
		ips, err := internet.LookupForIP(ob.Target.Address.Domain(), m.strategy, nil)
		if err != nil && m.strategy.ForceIP() {
			common.Interrupt(link.Reader)
			common.Interrupt(link.Writer)
			return
		}
		if err == nil && len(ips) > 0 {
			ob.Target.Address = xnet.IPAddress(ips[dice.Roll(len(ips))])
		}
	}
	m.mu.Lock()
	if m.worker == nil {
		upReader, upWriter := pipe.New(pipe.WithSizeLimit(64 * 1024))
		downReader, downWriter := pipe.New(pipe.WithSizeLimit(64 * 1024))
		concurrency := m.concurrency
		if concurrency == 0 {
			concurrency = 8
		}
		worker, err := mux.NewClientWorker(transport.Link{Reader: downReader, Writer: upWriter}, mux.ClientStrategy{MaxConcurrency: uint32(concurrency), MaxConnection: 128})
		if err != nil {
			m.mu.Unlock()
			common.Interrupt(upReader)
			common.Interrupt(upWriter)
			common.Interrupt(downReader)
			common.Interrupt(downWriter)
			common.Interrupt(link.Reader)
			common.Interrupt(link.Writer)
			return
		}
		m.worker = worker
		m.done = make(chan struct{})
		m.carrier = &transport.Link{Reader: upReader, Writer: downWriter}
		carrierCtx, cancel := context.WithCancel(m.ctx)
		m.cancel = cancel
		target := xnet.TCPDestination(xnet.DomainAddress("v1.mux.cool"), 9527)
		carrierCtx = session.ContextWithOutbounds(carrierCtx, []*session.Outbound{{Target: target, OriginalTarget: target, Tag: m.Tag()}})
		carrierCtx = session.ContextWithContent(carrierCtx, &session.Content{SkipDNSResolve: true})
		go func() {
			defer close(m.done)
			defer func() {
				if recover() != nil {
					common.Interrupt(m.carrier.Reader)
					common.Interrupt(m.carrier.Writer)
				}
			}()
			defer worker.Close()
			m.Handler.Dispatch(carrierCtx, m.carrier)
		}()
	}
	worker := m.worker
	m.mu.Unlock()
	if !worker.Dispatch(ctx, link) {
		common.Interrupt(link.Reader)
		common.Interrupt(link.Writer)
	}
}

func (m *probeMux) Close() error {
	m.mu.Lock()
	if m.worker == nil {
		m.mu.Unlock()
		return nil
	}
	m.cancel()
	_ = m.worker.Close()
	common.Interrupt(m.carrier.Reader)
	common.Interrupt(m.carrier.Writer)
	done := m.done
	m.mu.Unlock()
	<-done
	return nil // the underlying handler has its own later defer
}
