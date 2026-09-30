package store

import (
	"github.com/nebulagram/nebulagram/core/model"
	"testing"
)

func TestBillingSurvivesSubscriptionRefreshAndReopen(t *testing.T) {
	dir := t.TempDir()
	st, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	server := serverWithTemplate("old name", "")
	if _, err = st.AddServers([]model.Server{server}); err != nil {
		t.Fatal(err)
	}
	want := model.ServerDetails{Provider: "Example provider", Plan: "Basic", Amount: "12.50", Currency: "USD", DueDate: "2026-10-01", PeriodDays: 30, Notes: "One endpoint"}
	if _, err = st.SetServerDetails(server.ID, want); err != nil {
		t.Fatal(err)
	}
	if err = st.Select(server.ID); err != nil {
		t.Fatal(err)
	}
	server.Name = "renamed by panel"
	if err = st.ReplaceSource(server.Source, []model.Server{server}); err != nil {
		t.Fatal(err)
	}
	reopened, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	if got := reopened.Selected(); got == nil || got.Details != want || got.Name != server.Name {
		t.Fatalf("lost local fields: %+v", got)
	}
	if _, err = reopened.SetServerDetails(server.ID, model.ServerDetails{}); err != nil {
		t.Fatal(err)
	}
	if got := reopened.Servers()[0].Details; got != (model.ServerDetails{}) {
		t.Fatal("cannot clear fields")
	}
}

func TestInvalidBillingDoesNotReplaceExistingFields(t *testing.T) {
	st, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	server := serverWithTemplate("server", "")
	st.AddServers([]model.Server{server})
	want := model.ServerDetails{Provider: "Saved"}
	if _, err = st.SetServerDetails(server.ID, want); err != nil {
		t.Fatal(err)
	}
	for _, invalid := range []model.ServerDetails{
		{Amount: "-1", Currency: "USD"}, {Amount: "NaN", Currency: "USD"}, {Amount: "5"},
		{Amount: "5", Currency: "US"}, {DueDate: "2026-02-30"}, {PeriodDays: -1}, {PeriodDays: 3661},
	} {
		if _, err = st.SetServerDetails(server.ID, invalid); err == nil {
			t.Fatalf("accepted invalid fields: %+v", invalid)
		}
		if st.Servers()[0].Details != want {
			t.Fatal("invalid update mutated server")
		}
	}
	if _, err = st.SetServerDetails("missing", want); err == nil {
		t.Fatal("accepted absent server")
	}
}
