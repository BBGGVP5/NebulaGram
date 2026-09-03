package api

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
)

// call runs one method and fails the test if the core reports an error.
func call(t *testing.T, c *Core, method, payload string) json.RawMessage {
	t.Helper()
	var resp Response
	if err := json.Unmarshal([]byte(c.Call(method, payload)), &resp); err != nil {
		t.Fatalf("%s: response is not JSON: %v", method, err)
	}
	if !resp.OK {
		t.Fatalf("%s: %s", method, resp.Error)
	}
	return resp.Data
}

func newCore(t *testing.T) *Core {
	t.Helper()
	c := New()
	payload, _ := json.Marshal(initRequest{Dir: t.TempDir(), OS: "Android", OSVersion: "15", Model: "Pixel 9"})
	call(t, c, "core.init", string(payload))
	return c
}

func TestCallRequiresInit(t *testing.T) {
	var resp Response
	_ = json.Unmarshal([]byte(New().Call("settings.get", "")), &resp)
	if resp.OK {
		t.Fatal("settings.get succeeded before core.init")
	}
}

func TestUnknownMethodIsAnError(t *testing.T) {
	var resp Response
	_ = json.Unmarshal([]byte(newCore(t).Call("does.not.exist", "")), &resp)
	if resp.OK || resp.Error == "" {
		t.Fatal("an unknown method must fail with a message")
	}
}

func TestInitMintsHWID(t *testing.T) {
	c := newCore(t)
	var cfg settings.Settings
	if err := json.Unmarshal(call(t, c, "settings.get", ""), &cfg); err != nil {
		t.Fatal(err)
	}
	if len(cfg.HWID) != 16 {
		t.Errorf("hwid = %q, want 16 hex characters", cfg.HWID)
	}
	if cfg.Mode != settings.ModeProxy || cfg.SocksPort != 10808 {
		t.Errorf("unexpected defaults: %+v", cfg)
	}
}

func TestSettingsSetIsPartial(t *testing.T) {
	c := newCore(t)
	before := call(t, c, "settings.get", "")
	var original settings.Settings
	_ = json.Unmarshal(before, &original)

	var updated settings.Settings
	if err := json.Unmarshal(call(t, c, "settings.set", `{"auto_ping":true}`), &updated); err != nil {
		t.Fatal(err)
	}
	if !updated.AutoPing {
		t.Error("auto_ping was not applied")
	}
	if updated.HWID != original.HWID || updated.SocksPort != original.SocksPort {
		t.Error("a partial update overwrote fields the client did not send")
	}
}

func TestAddLinkSelectAndList(t *testing.T) {
	c := newCore(t)
	links := "vless://uuid@de.example.com:443?type=tcp&security=tls#DE\n" +
		"vless://uuid@nl.example.com:443?type=tcp&security=tls#NL"
	payload, _ := json.Marshal(linkRequest{Link: links})
	call(t, c, "server.addLink", string(payload))

	var list struct {
		Servers  []model.Server `json:"servers"`
		Total    int            `json:"total"`
		Pages    int            `json:"pages"`
		Selected string         `json:"selected"`
	}
	if err := json.Unmarshal(call(t, c, "servers.list", `{"page":1,"per_page":1}`), &list); err != nil {
		t.Fatal(err)
	}
	if list.Total != 2 || list.Pages != 2 || len(list.Servers) != 1 {
		t.Fatalf("paging is wrong: %+v", list)
	}
	if list.Selected == "" {
		t.Error("the first added server should have been selected automatically")
	}

	// Selecting the other server must stick.
	var all struct {
		Servers []model.Server `json:"servers"`
	}
	_ = json.Unmarshal(call(t, c, "servers.list", ""), &all)
	target := all.Servers[1].ID
	idPayload, _ := json.Marshal(idRequest{ID: target})
	call(t, c, "server.select", string(idPayload))

	var cfg settings.Settings
	_ = json.Unmarshal(call(t, c, "settings.get", ""), &cfg)
	if cfg.SelectedServerID != target {
		t.Errorf("selection = %q, want %q", cfg.SelectedServerID, target)
	}
}

func TestAddLinkRejectsGarbage(t *testing.T) {
	c := newCore(t)
	var resp Response
	_ = json.Unmarshal([]byte(c.Call("server.addLink", `{"link":"hello"}`)), &resp)
	if resp.OK {
		t.Fatal("a non-link payload must be rejected")
	}
}

func TestTunnelStartWithoutCoreFails(t *testing.T) {
	c := newCore(t)
	payload, _ := json.Marshal(linkRequest{Link: "vless://uuid@de.example.com:443#DE"})
	call(t, c, "server.addLink", string(payload))

	// No engine is registered in a plain unit test build, so the manager must
	// report a clean error rather than panicking.
	var resp Response
	_ = json.Unmarshal([]byte(c.Call("tunnel.start", "")), &resp)
	if resp.OK {
		t.Fatal("tunnel.start succeeded with no core registered")
	}

	var status model.Status
	if err := json.Unmarshal(call(t, c, "tunnel.status", ""), &status); err != nil {
		t.Fatal(err)
	}
	if status.State != model.StateFailed || status.LastError == "" {
		t.Errorf("status = %+v, want a failed state carrying the error", status)
	}
}

// clientCommands are handled on the platform side rather than by the core:
// they touch UI or OS APIs the core has no access to.
var clientCommands = map[string]bool{
	"calls.stats": true,
	"log.export":  true,
}

func TestMenuIsRenderable(t *testing.T) {
	c := newCore(t)
	var screens []settings.Screen
	if err := json.Unmarshal(call(t, c, "menu.get", ""), &screens); err != nil {
		t.Fatal(err)
	}
	if len(screens) < 3 {
		t.Fatalf("got %d screens, want the full menu", len(screens))
	}
	for _, screen := range screens {
		if screen.ID == "" || len(screen.Sections) == 0 {
			t.Errorf("screen %+v is incomplete", screen)
		}
		for _, section := range screen.Sections {
			for _, row := range section.Rows {
				if row.Key == "" || row.Type == "" || row.TitleKey == "" {
					t.Errorf("row %+v on screen %s is missing key/type/title", row, screen.ID)
				}
				if row.Type == settings.RowNav && row.Screen == "" {
					t.Errorf("nav row %s has no target screen", row.Key)
				}
				if row.Type == settings.RowAction && row.Command == "" {
					t.Errorf("action row %s has no command", row.Key)
				}
				// Every command the menu offers must exist in the core, or
				// the platform side would render a dead row. Commands the
				// clients implement themselves are listed in clientCommands.
				if row.Command != "" && handlers[row.Command] == nil && !clientCommands[row.Command] {
					t.Errorf("row %s points at unknown command %q", row.Key, row.Command)
				}
			}
		}
	}
}

func TestRefreshOneSubscriptionNeedsAKnownId(t *testing.T) {
	c := newCore(t)
	var resp Response
	_ = json.Unmarshal([]byte(c.Call("subscription.refresh", `{"id":"nope"}`)), &resp)
	if resp.OK {
		t.Fatal("refreshing an unknown subscription should fail")
	}
	if !strings.Contains(resp.Error, "nope") {
		t.Errorf("error should name the id, got %q", resp.Error)
	}
}
