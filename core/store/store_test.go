package store

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/nebulagram/nebulagram/core/model"
)

func serverWithTemplate(name, document string) model.Server {
	s := model.Server{
		Name: name, Protocol: model.VLESS, Address: name + ".example.com", Port: 443,
		UUID: "uuid", Config: document, CoreHint: model.EngineXray, Source: "Panel",
	}
	s.ID = s.StableID()
	return s
}

func TestTemplatesSurviveAReload(t *testing.T) {
	dir := t.TempDir()
	document := `{"outbounds":[{"protocol":"vless","tag":"proxy"}],"routing":{"rules":[]}}`

	first, err := Open(dir)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	if _, err := first.AddServers([]model.Server{serverWithTemplate("fi", document)}); err != nil {
		t.Fatalf("AddServers: %v", err)
	}

	second, err := Open(dir)
	if err != nil {
		t.Fatalf("reopen: %v", err)
	}
	servers := second.Servers()
	if len(servers) != 1 {
		t.Fatalf("got %d servers after reload", len(servers))
	}
	if servers[0].Config != document {
		t.Errorf("template did not survive the reload: %q", servers[0].Config)
	}
	if servers[0].CoreHint != model.EngineXray {
		t.Errorf("core hint = %q", servers[0].CoreHint)
	}
}

func TestIdenticalTemplatesAreStoredOnce(t *testing.T) {
	dir := t.TempDir()
	// A sing-box subscription hands the same document to every server.
	document := strings.Repeat(`{"outbounds":[{"type":"vless","tag":"a"}]}`, 40)

	st, err := Open(dir)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	var servers []model.Server
	for _, name := range []string{"fi", "nl", "de", "se"} {
		servers = append(servers, serverWithTemplate(name, document))
	}
	if _, err := st.AddServers(servers); err != nil {
		t.Fatalf("AddServers: %v", err)
	}

	raw, err := os.ReadFile(filepath.Join(dir, "nebulalink.json"))
	if err != nil {
		t.Fatal(err)
	}
	var onDisk State
	if err := json.Unmarshal(raw, &onDisk); err != nil {
		t.Fatal(err)
	}
	if len(onDisk.Templates) != 1 {
		t.Errorf("stored %d templates for four identical documents", len(onDisk.Templates))
	}
	for _, server := range onDisk.Servers {
		if server.Config != "" {
			t.Error("a template was written inline as well as into the pool")
		}
		if server.ConfigRef == "" {
			t.Error("a server lost its template reference")
		}
	}
	// Four copies of a 1.6 KB document compress into one pooled entry.
	if len(raw) > 4*len(document) {
		t.Errorf("state file is %d bytes for %d bytes of template", len(raw), len(document))
	}
}

func TestUnreadableTemplateDoesNotPoisonTheServer(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "nebulalink.json")
	server := serverWithTemplate("fi", `{"outbounds":[]}`)
	server.Config = ""
	server.ConfigRef = "deadbeef"

	state := State{Version: CurrentVersion, Servers: []model.Server{server},
		Templates: map[string]string{"deadbeef": "not base64 gzip"}}
	data, _ := json.Marshal(state)
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatal(err)
	}

	st, err := Open(dir)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	loaded := st.Servers()[0]
	if loaded.Config != "" || loaded.ConfigRef != "" {
		t.Errorf("a broken template left the server half-loaded: %+v", loaded)
	}
}

func TestCorruptStateIsMovedAside(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "nebulalink.json")
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(dir); err != nil {
		t.Fatalf("Open: %v", err)
	}
	if _, err := os.Stat(path + ".corrupt"); err != nil {
		t.Error("the unreadable file was not kept for recovery")
	}
}

func TestRefreshKeepsSelectionAndLatency(t *testing.T) {
	dir := t.TempDir()
	st, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	fi := serverWithTemplate("fi", `{"outbounds":[{"protocol":"vless"}]}`)
	nl := serverWithTemplate("nl", `{"outbounds":[{"protocol":"vless"}]}`)
	if _, err := st.AddServers([]model.Server{fi, nl}); err != nil {
		t.Fatal(err)
	}
	if err := st.Select(nl.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.UpdateServerLatency(map[string]int{nl.ID: 42}); err != nil {
		t.Fatal(err)
	}

	// The panel renamed the server but the endpoint is the same.
	renamed := nl
	renamed.Name = "🇳🇱 Netherlands · fast"
	if err := st.ReplaceSource("Panel", []model.Server{fi, renamed}); err != nil {
		t.Fatal(err)
	}

	if got := st.Settings().SelectedServerID; got != nl.ID {
		t.Errorf("selection = %q, want it to survive the refresh", got)
	}
	for _, server := range st.Servers() {
		if server.ID == nl.ID && server.LatencyMs != 42 {
			t.Errorf("latency = %d, want the measurement to be carried over", server.LatencyMs)
		}
	}
}
