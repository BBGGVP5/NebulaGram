package store

import (
	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
	"testing"
)

func TestLatencyProvenanceSurvivesReloadRefreshAndZeroSort(t *testing.T) {
	dir := t.TempDir()
	s, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	s.AddServers([]model.Server{{ID: "unknown", Source: "s"}, {ID: "zero", Source: "s"}, {ID: "positive", Source: "s"}})
	s.UpdateServerLatencyMethod(map[string]int{"zero": 0, "positive": 330}, "nimbo", 123)
	s.ReplaceSource("s", []model.Server{{ID: "unknown", Source: "s"}, {ID: "zero", Source: "s"}, {ID: "positive", Source: "s"}})
	s, err = Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	s.UpdateSettings(func(s *settings.Settings) { s.ServerSort = "latency"; s.PingType = settings.PingTCP })
	got := s.Filtered()
	if got[0].ID != "zero" || got[0].CheckedAt != 123 || got[0].LatencyMethod != "nimbo" || got[1].LatencyMs != 330 || got[2].LatencyMethod != "" {
		t.Fatalf("lost provenance/order: %+v", got)
	}
	s.UpdateServerLatency(map[string]int{"zero": 11})
	if s.Filtered()[0].LatencyMethod != "" {
		t.Fatal("legacy write inherited stale provenance")
	}
}
