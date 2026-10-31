package probe

import (
	"context"
	"net"
	"testing"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
)

// listener returns an address that completes a TCP handshake, so Batch has
// something real to measure rather than a mocked latency.
func listener(t *testing.T) (string, int) {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	t.Cleanup(func() { _ = ln.Close() })
	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				return
			}
			_ = conn.Close()
		}
	}()
	host, port, err := net.SplitHostPort(ln.Addr().String())
	if err != nil {
		t.Fatalf("split: %v", err)
	}
	number, err := net.LookupPort("tcp", port)
	if err != nil {
		t.Fatalf("port: %v", err)
	}
	return host, number
}

func TestBatchMeasuresReachableServers(t *testing.T) {
	host, port := listener(t)
	servers := []model.Server{
		{Address: host, Port: port},
		{Address: host, Port: port},
	}
	Batch(context.Background(), servers, 2, time.Second, MethodTCP)
	for i, server := range servers {
		if server.LatencyMs <= 0 {
			t.Fatalf("server %d: expected a measurement, got %d", i, server.LatencyMs)
		}
		if server.CheckedAt == 0 {
			t.Fatalf("server %d: a measured server must be marked as checked", i)
		}
	}
}

// Отменённая проверка — не результат. Оба метода возвращают Failed, когда
// контекст закончился, и запись такого ответа пометила бы живые серверы
// мёртвыми ровно в тот момент, когда пользователь ушёл с экрана.
func TestCancelledBatchLeavesServersUntouched(t *testing.T) {
	host, port := listener(t)
	servers := make([]model.Server, 64)
	for i := range servers {
		servers[i] = model.Server{Address: host, Port: port, LatencyMs: 42, CheckedAt: 7}
	}

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	Batch(ctx, servers, 4, time.Second, MethodTCP)

	for i, server := range servers {
		if server.LatencyMs != 42 || server.CheckedAt != 7 {
			t.Fatalf("server %d: a cancelled check overwrote it with %d/%d",
				i, server.LatencyMs, server.CheckedAt)
		}
	}
}

// Отмена посреди прогона: часть серверов могла успеть измериться, но ни один
// не должен получить Failed из-за самой отмены.
func TestBatchCancelledMidwayNeverRecordsFailure(t *testing.T) {
	host, port := listener(t)
	servers := make([]model.Server, 256)
	for i := range servers {
		servers[i] = model.Server{Address: host, Port: port, LatencyMs: 42, CheckedAt: 7}
	}

	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		time.Sleep(2 * time.Millisecond)
		cancel()
	}()
	Batch(ctx, servers, 8, time.Second, MethodTCP)

	for i, server := range servers {
		if server.LatencyMs == Failed {
			t.Fatalf("server %d: cancellation was recorded as a failed server", i)
		}
		if server.LatencyMs != 42 && server.CheckedAt != 7 {
			continue // измерен до отмены — это законный результат
		}
		if server.LatencyMs != 42 || server.CheckedAt != 7 {
			t.Fatalf("server %d: half-written result %d/%d", i, server.LatencyMs, server.CheckedAt)
		}
	}
}
