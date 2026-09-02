// Command nldiag drives the NebulaLink core from a terminal.
//
// It talks to the core through the same JSON facade the Android, iOS and
// desktop clients use, so whatever it proves here holds for them: a
// subscription that loads, a server that starts, and real traffic through the
// local SOCKS5 endpoint.
//
//	nldiag -url https://panel.example/api/sub/abc            # load and measure
//	nldiag -url https://panel.example/api/sub/abc -connect   # and connect
//	nldiag -url ... -connect -filter Finland                 # pick a server by name
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"sort"
	"strings"
	"time"

	"github.com/nebulagram/nebulagram/bind/cores"
	"github.com/nebulagram/nebulagram/core/api"
	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/probe"
)

func main() {
	url := flag.String("url", "", "subscription URL")
	link := flag.String("link", "", "a single share link or a pasted Xray config file")
	filter := flag.String("filter", "", "connect to the first server whose name contains this")
	connect := flag.Bool("connect", false, "start the tunnel and check that traffic flows")
	measure := flag.Int("measure", 12, "how many servers to latency-check")
	testURL := flag.String("test-url", probe.DefaultTestURL, "URL to fetch through the tunnel")
	dir := flag.String("dir", "", "state directory (default: a temporary one)")
	flag.Parse()

	if *url == "" && *link == "" {
		flag.Usage()
		os.Exit(2)
	}

	cores.Register()
	core := api.New()

	stateDir := *dir
	if stateDir == "" {
		temp, err := os.MkdirTemp("", "nldiag")
		if err != nil {
			fail("cannot create a state directory: %v", err)
		}
		defer os.RemoveAll(temp)
		stateDir = temp
	}

	call(core, "core.init", map[string]any{
		"dir": stateDir, "os": "diag", "os_version": "1", "model": "cli",
	})

	versions := call(core, "core.versions", nil)
	fmt.Printf("cores: %s\n", compact(versions))

	if *url != "" {
		result := call(core, "subscription.add", map[string]any{"url": *url})
		fmt.Printf("subscription: %s\n", compact(result))
	}
	if *link != "" {
		payload := *link
		if data, err := os.ReadFile(*link); err == nil {
			payload = string(data) // a path was given, not a link
		}
		result := call(core, "server.addLink", map[string]any{"link": payload})
		fmt.Printf("added: %s\n", compact(result))
	}

	servers := listServers(core)
	fmt.Printf("servers: %d\n", len(servers))

	if *measure > 0 {
		ids := make([]string, 0, *measure)
		for i, s := range servers {
			if i >= *measure {
				break
			}
			ids = append(ids, s.ID)
		}
		started := time.Now()
		call(core, "probe.servers", map[string]any{"ids": ids})
		fmt.Printf("measured %d servers in %s\n", len(ids), time.Since(started).Round(time.Millisecond))

		servers = listServers(core)
		shown := servers
		if len(shown) > 8 {
			shown = shown[:8]
		}
		for _, s := range shown {
			fmt.Printf("  %-42s %-12s %6s  %s\n", trim(s.Name, 42), s.Protocol, latency(s), s.Engine())
		}
	}

	if !*connect {
		return
	}

	target := pick(servers, *filter)
	if target == nil {
		fail("no server matches %q", *filter)
	}
	fmt.Printf("\nconnecting to %s (%s, %s)\n", target.Name, target.Protocol, target.Engine())

	status := call(core, "tunnel.start", map[string]any{"id": target.ID})
	fmt.Printf("status: %s\n", compact(status))

	check := call(core, "probe.url", map[string]any{"url": *testURL})
	fmt.Printf("traffic through the tunnel: %s\n", compact(check))

	final := call(core, "tunnel.status", nil)
	fmt.Printf("counters: %s\n", compact(final))

	call(core, "tunnel.stop", nil)
	fmt.Println("stopped")
}

// call runs one core method and stops the program on failure, since every step
// here is a precondition for the next.
func call(core *api.Core, method string, payload any) json.RawMessage {
	body := ""
	if payload != nil {
		encoded, err := json.Marshal(payload)
		if err != nil {
			fail("cannot encode the payload for %s: %v", method, err)
		}
		body = string(encoded)
	}
	var response api.Response
	if err := json.Unmarshal([]byte(core.Call(method, body)), &response); err != nil {
		fail("%s returned unreadable JSON: %v", method, err)
	}
	if !response.OK {
		fail("%s: %s", method, response.Error)
	}
	return response.Data
}

func listServers(core *api.Core) []model.Server {
	var page struct {
		Servers []model.Server `json:"servers"`
	}
	if err := json.Unmarshal(call(core, "servers.list", map[string]any{"per_page": 500}), &page); err != nil {
		fail("cannot read the server list: %v", err)
	}
	sort.SliceStable(page.Servers, func(i, j int) bool {
		return rank(page.Servers[i]) < rank(page.Servers[j])
	})
	return page.Servers
}

// rank puts measured servers first, fastest to slowest, then the unmeasured
// ones, then the ones that did not answer.
func rank(s model.Server) int {
	switch {
	case s.LatencyMs > 0:
		return s.LatencyMs
	case s.LatencyMs == 0:
		return 1 << 20
	default:
		return 1 << 21
	}
}

func pick(servers []model.Server, filter string) *model.Server {
	for i := range servers {
		if filter == "" || strings.Contains(strings.ToLower(servers[i].Name), strings.ToLower(filter)) {
			if servers[i].LatencyMs != probe.Failed {
				return &servers[i]
			}
		}
	}
	return nil
}

func latency(s model.Server) string {
	switch {
	case s.LatencyMs > 0:
		return fmt.Sprintf("%d ms", s.LatencyMs)
	case s.LatencyMs == probe.Failed:
		return "—"
	default:
		return "?"
	}
}

func trim(text string, width int) string {
	runes := []rune(text)
	if len(runes) <= width {
		return text
	}
	return string(runes[:width-1]) + "…"
}

func compact(data json.RawMessage) string {
	if len(data) == 0 {
		return "ok"
	}
	return string(data)
}

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "nldiag: "+format+"\n", args...)
	os.Exit(1)
}
