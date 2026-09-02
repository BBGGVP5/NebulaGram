// Package probe measures server latency.
//
// Two methods, matching the "Ping type" setting: TCP measures the handshake to
// the endpoint and never disturbs a live tunnel, while URL measures a real HTTP
// round trip through the local SOCKS proxy and therefore reflects what the user
// actually experiences.
package probe

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
)

// Failed is the latency stored for a server that did not answer.
const Failed = -1

// DefaultTestURL is a small, unauthenticated endpoint that answers 204.
const DefaultTestURL = "https://www.gstatic.com/generate_204"

// TCP measures the time to complete a TCP handshake with the server endpoint.
// Returns Failed when the endpoint does not answer within timeout.
func TCP(ctx context.Context, s model.Server, timeout time.Duration) int {
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	addr := net.JoinHostPort(s.Address, strconv.Itoa(s.Port))
	start := time.Now()
	var d net.Dialer
	conn, err := d.DialContext(ctx, "tcp", addr)
	if err != nil {
		return Failed
	}
	elapsed := int(time.Since(start).Milliseconds())
	_ = conn.Close()
	if elapsed == 0 {
		elapsed = 1
	}
	return elapsed
}

// URL measures a full request through the local SOCKS5 proxy the tunnel
// exposes. It only makes sense while the tunnel is up.
func URL(ctx context.Context, testURL string, socksAddr string, timeout time.Duration) (int, error) {
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	if testURL == "" {
		testURL = DefaultTestURL
	}
	client := &http.Client{
		Timeout: timeout,
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return DialSOCKS5(ctx, socksAddr, addr, timeout)
			},
			DisableKeepAlives: true,
		},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, testURL, nil)
	if err != nil {
		return Failed, err
	}
	start := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return Failed, err
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))
	elapsed := int(time.Since(start).Milliseconds())
	if elapsed == 0 {
		elapsed = 1
	}
	return elapsed, nil
}

// Batch measures many servers concurrently and writes the result back into the
// slice. It is what the "Check page" action calls for the visible page.
func Batch(ctx context.Context, servers []model.Server, concurrency int, timeout time.Duration) {
	if concurrency <= 0 {
		concurrency = 16
	}
	sem := make(chan struct{}, concurrency)
	var wg sync.WaitGroup
	now := time.Now().Unix()
	for i := range servers {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			servers[idx].LatencyMs = TCP(ctx, servers[idx], timeout)
			servers[idx].CheckedAt = now
		}(i)
	}
	wg.Wait()
}

// DialSOCKS5 opens a connection to target through an unauthenticated SOCKS5
// proxy. Implemented here so the core stays dependency-free.
func DialSOCKS5(ctx context.Context, proxyAddr, target string, timeout time.Duration) (net.Conn, error) {
	var d net.Dialer
	conn, err := d.DialContext(ctx, "tcp", proxyAddr)
	if err != nil {
		return nil, err
	}
	if timeout > 0 {
		_ = conn.SetDeadline(time.Now().Add(timeout))
	}
	if err := socks5Handshake(conn, target); err != nil {
		_ = conn.Close()
		return nil, err
	}
	_ = conn.SetDeadline(time.Time{})
	return conn, nil
}

func socks5Handshake(conn net.Conn, target string) error {
	// Greeting: SOCKS5, one method, "no authentication".
	if _, err := conn.Write([]byte{0x05, 0x01, 0x00}); err != nil {
		return err
	}
	reply := make([]byte, 2)
	if _, err := io.ReadFull(conn, reply); err != nil {
		return err
	}
	if reply[0] != 0x05 || reply[1] != 0x00 {
		return errors.New("probe: socks5 proxy rejected the no-auth method")
	}

	host, portStr, err := net.SplitHostPort(target)
	if err != nil {
		return err
	}
	port, err := strconv.Atoi(portStr)
	if err != nil {
		return err
	}
	req := []byte{0x05, 0x01, 0x00}
	switch ip := net.ParseIP(host); {
	case ip == nil:
		if len(host) > 255 {
			return errors.New("probe: hostname too long for socks5")
		}
		req = append(req, 0x03, byte(len(host)))
		req = append(req, host...)
	case ip.To4() != nil:
		req = append(req, 0x01)
		req = append(req, ip.To4()...)
	default:
		req = append(req, 0x04)
		req = append(req, ip.To16()...)
	}
	req = append(req, byte(port>>8), byte(port))
	if _, err := conn.Write(req); err != nil {
		return err
	}

	head := make([]byte, 4)
	if _, err := io.ReadFull(conn, head); err != nil {
		return err
	}
	if head[1] != 0x00 {
		return fmt.Errorf("probe: socks5 connect failed with status 0x%02x", head[1])
	}
	// Drain the bound address so the stream starts at the payload.
	var skip int
	switch head[3] {
	case 0x01:
		skip = 4
	case 0x04:
		skip = 16
	case 0x03:
		length := make([]byte, 1)
		if _, err := io.ReadFull(conn, length); err != nil {
			return err
		}
		skip = int(length[0])
	default:
		return errors.New("probe: socks5 returned an unknown address type")
	}
	if _, err := io.CopyN(io.Discard, conn, int64(skip)+2); err != nil {
		return err
	}
	return nil
}
