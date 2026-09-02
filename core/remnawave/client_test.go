package remnawave

import (
	"context"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestFetchBase64ListAndHeaders(t *testing.T) {
	list := "vless://uuid@de.example.com:443?type=tcp&security=tls#DE\n" +
		"vless://uuid@nl.example.com:443?type=tcp&security=tls#NL"

	var got *http.Request
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r
		w.Header().Set("subscription-userinfo", "upload=100; download=900; total=10000; expire=1780000000")
		w.Header().Set("profile-title", "base64:"+base64.StdEncoding.EncodeToString([]byte("Креветка")))
		w.Header().Set("profile-update-interval", "1")
		w.Header().Set("support-url", "https://t.me/support")
		w.Header().Set("announce", "base64:"+base64.StdEncoding.EncodeToString([]byte("Профилактика в пятницу")))
		_, _ = w.Write([]byte(base64.StdEncoding.EncodeToString([]byte(list))))
	}))
	defer srv.Close()

	client := &Client{Device: Device{HWID: "17c2fa58edcfd74d", OS: "Android", OSVersion: "15", Model: "Pixel 9"}}
	res, err := client.Fetch(context.Background(), srv.URL, "Shrimp")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(res.Servers) != 2 {
		t.Fatalf("got %d servers, want 2", len(res.Servers))
	}
	if res.Format != "links" {
		t.Errorf("format = %q, want links", res.Format)
	}
	if got.Header.Get("x-hwid") != "17c2fa58edcfd74d" {
		t.Error("HWID header was not sent")
	}
	if got.Header.Get("x-device-model") != "Pixel 9" {
		t.Error("device model header was not sent")
	}
	if res.Info == nil {
		t.Fatal("subscription info was not parsed")
	}
	if res.Info.Title != "Креветка" {
		t.Errorf("title = %q, want the decoded base64 title", res.Info.Title)
	}
	if res.Info.Used() != 1000 || res.Info.Total != 10000 {
		t.Errorf("traffic = %d/%d, want 1000/10000", res.Info.Used(), res.Info.Total)
	}
	if res.Info.UpdateInterval != 24 {
		t.Errorf("update interval = %d hours, want 24", res.Info.UpdateInterval)
	}
	if res.Info.Announce != "Профилактика в пятницу" {
		t.Errorf("announce = %q", res.Info.Announce)
	}
}

func TestFetchV2RayJSONProfile(t *testing.T) {
	profile := `[{"remarks":"🇩🇪 Germany","outbounds":[
		{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"de.example.com","port":443,
		"users":[{"id":"uuid","flow":"xtls-rprx-vision","encryption":"none"}]}]},
		"streamSettings":{"network":"tcp","security":"reality",
		"realitySettings":{"serverName":"www.microsoft.com","publicKey":"pk","shortId":"01"}}},
		{"tag":"direct","protocol":"freedom"}]}]`

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(profile))
	}))
	defer srv.Close()

	res, err := (&Client{}).Fetch(context.Background(), srv.URL, "Panel")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if res.Format != "v2ray-json" {
		t.Fatalf("format = %q, want v2ray-json", res.Format)
	}
	if len(res.Servers) != 1 {
		t.Fatalf("got %d servers, want 1", len(res.Servers))
	}
	s := res.Servers[0]
	if s.Name != "🇩🇪 Germany" || s.Security != "reality" || s.PublicKey != "pk" {
		t.Errorf("unexpected server: %+v", s)
	}
	if s.Source != "Panel" {
		t.Errorf("source = %q, want Panel", s.Source)
	}
}

func TestFetchRejectsEmptySubscription(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("nothing useful here"))
	}))
	defer srv.Close()

	if _, err := (&Client{}).Fetch(context.Background(), srv.URL, "X"); err == nil {
		t.Fatal("expected an error for a subscription with no servers")
	}
}

func TestFetchReportsPanelError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer srv.Close()

	if _, err := (&Client{}).Fetch(context.Background(), srv.URL, "X"); err == nil {
		t.Fatal("expected an error for a 403 answer")
	}
}
