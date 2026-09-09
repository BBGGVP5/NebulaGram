package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestOnboardingImportDoesNotStartFirstServer(t *testing.T) {
	c := newCore(t)
	sub := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("vless://11111111-1111-1111-1111-111111111111@127.0.0.1:1#First\nvless://22222222-2222-2222-2222-222222222222@127.0.0.1:2#Second"))
	}))
	defer sub.Close()
	input, _ := json.Marshal(map[string]string{"input": sub.URL})
	call(t, c, "onboarding.import", string(input))
	var list struct {
		Servers []struct {
			ID string `json:"id"`
		} `json:"servers"`
		Selected string `json:"selected"`
	}
	if err := json.Unmarshal(call(t, c, "servers.list", ""), &list); err != nil {
		t.Fatal(err)
	}
	if len(list.Servers) != 2 {
		t.Fatalf("expected selectable imported endpoints, got %d", len(list.Servers))
	}
	selected, _ := json.Marshal(map[string]string{"id": list.Servers[1].ID})
	call(t, c, "server.select", string(selected))
	var current struct {
		Selected string `json:"selected"`
	}
	_ = json.Unmarshal(call(t, c, "servers.list", ""), &current)
	if current.Selected != list.Servers[1].ID {
		t.Fatal("second server did not remain selected")
	}
	var status struct {
		State string `json:"state"`
	}
	_ = json.Unmarshal(call(t, c, "tunnel.status", ""), &status)
	if status.State == "connected" || status.State == "connecting" {
		t.Fatal("import unexpectedly started the tunnel")
	}
}
