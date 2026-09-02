package link

import (
	"encoding/base64"
	"testing"

	"github.com/nebulagram/nebulagram/core/model"
)

func TestParseVLESSReality(t *testing.T) {
	raw := "vless://11111111-2222-3333-4444-555555555555@de.example.com:443" +
		"?type=xhttp&security=reality&pbk=abcdef&fp=chrome&sni=www.microsoft.com" +
		"&sid=0123&path=%2Fdownload&mode=auto&flow=xtls-rprx-vision" +
		"#%F0%9F%87%A9%F0%9F%87%AA%20VLESS%20%C2%B7%20Germany"

	s, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if s.Protocol != model.VLESS {
		t.Errorf("protocol = %q, want vless", s.Protocol)
	}
	if s.Address != "de.example.com" || s.Port != 443 {
		t.Errorf("endpoint = %s:%d, want de.example.com:443", s.Address, s.Port)
	}
	if s.Security != "reality" || s.PublicKey != "abcdef" || s.ShortID != "0123" {
		t.Errorf("reality parameters not parsed: %+v", s)
	}
	if s.Network != "xhttp" || s.Path != "/download" {
		t.Errorf("transport = %s %s, want xhttp /download", s.Network, s.Path)
	}
	if s.Flow != "xtls-rprx-vision" {
		t.Errorf("flow = %q", s.Flow)
	}
	if s.Country != "DE" || s.Flag == "" {
		t.Errorf("flag/country = %q/%q, want a German flag", s.Flag, s.Country)
	}
	if s.Name != "🇩🇪 VLESS · Germany" {
		t.Errorf("name = %q", s.Name)
	}
}

func TestParseStableIDIgnoresName(t *testing.T) {
	base := "vless://uuid@host.example:443?type=ws&path=/x"
	first, err := Parse(base + "#Berlin")
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	renamed, err := Parse(base + "#Berlin%20%E2%80%94%20fast")
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if first.ID != renamed.ID {
		t.Errorf("renaming a server changed its id: %s != %s", first.ID, renamed.ID)
	}
}

func TestParseVMess(t *testing.T) {
	payload := `{"v":"2","ps":"Tokyo","add":"jp.example.com","port":"8443",
		"id":"aaaa-bbbb","aid":"0","net":"ws","path":"/ray","host":"cdn.example.com","tls":"tls"}`
	raw := "vmess://" + base64.StdEncoding.EncodeToString([]byte(payload))

	s, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if s.Port != 8443 || s.Network != "ws" || s.Path != "/ray" {
		t.Errorf("unexpected vmess parse: %+v", s)
	}
	if s.SNI != "cdn.example.com" {
		t.Errorf("sni = %q, want the ws host", s.SNI)
	}
}

func TestParseShadowsocksBothLayouts(t *testing.T) {
	inner := base64.StdEncoding.EncodeToString([]byte("aes-256-gcm:secret"))
	split, err := Parse("ss://" + inner + "@ss.example.com:8388#SS")
	if err != nil {
		t.Fatalf("split layout: %v", err)
	}
	whole := base64.StdEncoding.EncodeToString([]byte("aes-256-gcm:secret@ss.example.com:8388"))
	joined, err := Parse("ss://" + whole + "#SS")
	if err != nil {
		t.Fatalf("joined layout: %v", err)
	}
	if split.ID != joined.ID {
		t.Errorf("the two shadowsocks layouts produced different ids")
	}
	if split.Method != "aes-256-gcm" || split.Password != "secret" {
		t.Errorf("credentials = %q/%q", split.Method, split.Password)
	}
}

func TestParseHysteria2UsesSingBox(t *testing.T) {
	s, err := Parse("hysteria2://pass@hy.example.com:443?sni=hy.example.com&insecure=1#HY")
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if s.Engine() != model.EngineSingBox {
		t.Errorf("engine = %q, want sing-box", s.Engine())
	}
	if !s.AllowInsecure {
		t.Error("insecure=1 was not honoured")
	}
}

func TestParseManySkipsGarbage(t *testing.T) {
	list := "vless://u@a.example:443#A\nnot-a-link\n\nss://" +
		base64.StdEncoding.EncodeToString([]byte("aes-256-gcm:p")) + "@b.example:8388#B\n"
	servers := ParseMany(base64.StdEncoding.EncodeToString([]byte(list)), "Shrimp")
	if len(servers) != 2 {
		t.Fatalf("got %d servers, want 2", len(servers))
	}
	for _, s := range servers {
		if s.Source != "Shrimp" {
			t.Errorf("source = %q, want Shrimp", s.Source)
		}
	}
}

func TestParseRejectsUnknownScheme(t *testing.T) {
	if _, err := Parse("wireguard://x@y:51820"); err == nil {
		t.Fatal("expected an error for an unsupported scheme")
	}
}
