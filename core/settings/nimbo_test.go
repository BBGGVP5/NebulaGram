package settings

import "testing"

func TestNimboDefaultAndLegacyMethods(t *testing.T) {
	if Default().PingType != PingNimbo {
		t.Fatal("wrong default")
	}
	for _, method := range []PingType{"", "invalid", PingNimbo, PingTCP, PingHTTP, PingURL} {
		s := Settings{PingType: method}
		s.Normalize()
		want := method
		if method == "" || method == "invalid" {
			want = PingNimbo
		}
		if s.PingType != want {
			t.Fatalf("%s -> %s", method, s.PingType)
		}
	}
	found := false
	for _, screen := range Menu() {
		for _, section := range screen.Sections {
			for _, row := range section.Rows {
				if row.Key == "ping_type" {
					found = true
					if len(row.Options) != 4 || row.Options[0].Value != "nimbo" || row.Options[1].Value != "tcp" || row.Options[0].TitleKey != "nl_ping_nimbo" {
						t.Fatalf("wrong menu: %+v", row.Options)
					}
				}
			}
		}
	}
	if !found {
		t.Fatal("missing menu")
	}
}
