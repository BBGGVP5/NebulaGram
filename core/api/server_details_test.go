package api

import (
	"encoding/json"
	"github.com/nebulagram/nebulagram/core/model"
	"testing"
)

func TestServerDetailsAPI(t *testing.T) {
	c := newCore(t)
	call(t, c, "server.addLink", `{"link":"vless://uuid@example.com:443#Example"}`)
	var listing struct {
		Servers []model.Server `json:"servers"`
	}
	if err := json.Unmarshal(call(t, c, "servers.list", `{}`), &listing); err != nil || len(listing.Servers) != 1 {
		t.Fatalf("server listing: %v", err)
	}
	payload, _ := json.Marshal(map[string]any{"id": listing.Servers[0].ID, "details": model.ServerDetails{Provider: "My provider", Amount: "10.50", Currency: "eur", DueDate: "2026-10-01"}})
	call(t, c, "server.details", string(payload))
	json.Unmarshal(call(t, c, "servers.list", `{}`), &listing)
	if got := listing.Servers[0].Details; got.Provider != "My provider" || got.Currency != "EUR" || got.Amount != "10.50" {
		t.Fatalf("billing not exposed: %+v", got)
	}
}
