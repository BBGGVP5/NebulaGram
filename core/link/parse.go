// Package link parses proxy share links into model.Server values.
//
// Every format below is accepted by the "Add server key" screen and by any
// subscription that returns a plain (or base64) list of links.
package link

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"github.com/nebulagram/nebulagram/core/model"
)

// ErrUnsupported is returned for a scheme NebulaLink cannot run.
var ErrUnsupported = errors.New("nebulalink: unsupported link scheme")

// ParseMany parses a subscription payload: either raw text with one link per
// line, or the whole thing base64-encoded. Unparseable lines are skipped so a
// single bad entry never costs the user the rest of the list.
func ParseMany(payload, source string) []model.Server {
	text := strings.TrimSpace(payload)
	if decoded, ok := decodeBase64(text); ok {
		text = decoded
	}
	var out []model.Server
	seen := make(map[string]bool)
	for _, line := range strings.FieldsFunc(text, isNewline) {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "//") {
			continue
		}
		s, err := Parse(line)
		if err != nil {
			continue
		}
		s.Source = source
		if seen[s.ID] {
			continue
		}
		seen[s.ID] = true
		out = append(out, s)
	}
	return out
}

func isNewline(r rune) bool { return r == '\n' || r == '\r' }

// Parse turns a single share link into a Server.
func Parse(raw string) (model.Server, error) {
	raw = strings.TrimSpace(raw)
	scheme, rest, ok := strings.Cut(raw, "://")
	if !ok {
		return model.Server{}, ErrUnsupported
	}
	var (
		s   model.Server
		err error
	)
	switch strings.ToLower(scheme) {
	case "vless":
		s, err = parseUserInfo(rest, model.VLESS)
	case "trojan":
		s, err = parseUserInfo(rest, model.Trojan)
	case "hysteria2", "hy2":
		s, err = parseUserInfo(rest, model.Hysteria2)
	case "tuic":
		s, err = parseUserInfo(rest, model.TUIC)
	case "ss":
		s, err = parseShadowsocks(rest)
	case "vmess":
		s, err = parseVMess(rest)
	default:
		return model.Server{}, fmt.Errorf("%w: %s", ErrUnsupported, scheme)
	}
	if err != nil {
		return model.Server{}, err
	}
	if s.Address == "" || s.Port == 0 {
		return model.Server{}, errors.New("nebulalink: link has no host:port")
	}
	s.Raw = raw
	s.Flag, s.Country = flagOf(s.Name)
	s.ID = s.StableID()
	if s.Name == "" {
		s.Name = fmt.Sprintf("%s %s:%d", s.Protocol, s.Address, s.Port)
	}
	return s, nil
}

// parseUserInfo handles the scheme://credential@host:port?query#name family.
func parseUserInfo(rest string, p model.Protocol) (model.Server, error) {
	s := model.Server{Protocol: p, Network: "tcp", Security: "none"}

	body, frag, _ := strings.Cut(rest, "#")
	s.Name = unescape(frag)

	body, query, _ := strings.Cut(body, "?")
	cred, hostport, ok := strings.Cut(body, "@")
	if !ok {
		return s, errors.New("nebulalink: link has no credential")
	}
	host, port, err := splitHostPort(hostport)
	if err != nil {
		return s, err
	}
	s.Address, s.Port = host, port

	cred = unescape(cred)
	switch p {
	case model.VLESS:
		s.UUID = cred
	case model.TUIC:
		if u, pw, ok := strings.Cut(cred, ":"); ok {
			s.UUID, s.Password = u, pw
		} else {
			s.UUID = cred
		}
	default: // trojan, hysteria2
		s.Password = cred
	}

	applyQuery(&s, parseQuery(query))
	return s, nil
}

func applyQuery(s *model.Server, q map[string]string) {
	get := func(keys ...string) string {
		for _, k := range keys {
			if v := q[k]; v != "" {
				return v
			}
		}
		return ""
	}
	if v := get("type", "network"); v != "" {
		s.Network = v
	}
	if v := get("security"); v != "" {
		s.Security = v
	}
	if s.Protocol == model.Hysteria2 || s.Protocol == model.TUIC {
		s.Security = "tls"
	}
	if q["pbk"] != "" {
		s.Security = "reality"
	}
	s.Flow = get("flow")
	s.Path = get("path")
	s.Host = get("host")
	s.ServiceName = get("serviceName", "servicename")
	s.Mode = get("mode")
	s.HeaderType = get("headerType", "headertype")
	s.SNI = get("sni", "peer")
	s.ALPN = get("alpn")
	s.Fingerprint = get("fp")
	s.PublicKey = get("pbk")
	s.ShortID = get("sid")
	s.SpiderX = get("spx")
	s.AllowInsecure = truthy(get("allowInsecure", "insecure", "allow_insecure"))
	if s.Protocol == model.TUIC && q["password"] != "" {
		s.Password = q["password"]
	}
}

// vmessJSON is the historical base64-encoded JSON payload of a vmess:// link.
type vmessJSON struct {
	PS   string `json:"ps"`
	Add  string `json:"add"`
	Port any    `json:"port"`
	ID   string `json:"id"`
	Aid  any    `json:"aid"`
	Net  string `json:"net"`
	Type string `json:"type"`
	Host string `json:"host"`
	Path string `json:"path"`
	TLS  string `json:"tls"`
	SNI  string `json:"sni"`
	ALPN string `json:"alpn"`
	FP   string `json:"fp"`
}

func parseVMess(rest string) (model.Server, error) {
	decoded, ok := decodeBase64(rest)
	if !ok {
		return model.Server{}, errors.New("nebulalink: vmess payload is not base64")
	}
	var v vmessJSON
	if err := json.Unmarshal([]byte(decoded), &v); err != nil {
		return model.Server{}, fmt.Errorf("nebulalink: bad vmess json: %w", err)
	}
	s := model.Server{
		Protocol:    model.VMess,
		Name:        v.PS,
		Address:     v.Add,
		Port:        asInt(v.Port),
		UUID:        v.ID,
		AlterID:     asInt(v.Aid),
		Network:     firstNonEmpty(v.Net, "tcp"),
		HeaderType:  v.Type,
		Host:        v.Host,
		Path:        v.Path,
		Security:    firstNonEmpty(v.TLS, "none"),
		SNI:         firstNonEmpty(v.SNI, v.Host),
		ALPN:        v.ALPN,
		Fingerprint: v.FP,
	}
	if s.Network == "grpc" {
		s.ServiceName = v.Path
	}
	return s, nil
}

func parseShadowsocks(rest string) (model.Server, error) {
	s := model.Server{Protocol: model.Shadowsocks, Network: "tcp", Security: "none"}
	body, frag, _ := strings.Cut(rest, "#")
	s.Name = unescape(frag)
	body, query, _ := strings.Cut(body, "?")

	// Two layouts exist: base64(method:password)@host:port, and the fully
	// base64-encoded base64(method:password@host:port).
	if !strings.Contains(body, "@") {
		decoded, ok := decodeBase64(body)
		if !ok {
			return s, errors.New("nebulalink: bad shadowsocks payload")
		}
		body = decoded
	}
	cred, hostport, ok := strings.Cut(body, "@")
	if !ok {
		return s, errors.New("nebulalink: bad shadowsocks payload")
	}
	if decoded, ok := decodeBase64(cred); ok && strings.Contains(decoded, ":") {
		cred = decoded
	}
	method, password, _ := strings.Cut(unescape(cred), ":")
	s.Method, s.Password = method, password

	host, port, err := splitHostPort(hostport)
	if err != nil {
		return s, err
	}
	s.Address, s.Port = host, port
	applyQuery(&s, parseQuery(query))
	return s, nil
}

func splitHostPort(hp string) (string, int, error) {
	hp = strings.TrimSuffix(strings.TrimSpace(hp), "/")
	if i := strings.LastIndex(hp, "]"); i >= 0 { // [ipv6]:port
		host := strings.TrimPrefix(hp[:i], "[")
		port, err := strconv.Atoi(strings.TrimPrefix(hp[i+1:], ":"))
		if err != nil {
			return "", 0, err
		}
		return host, port, nil
	}
	host, portStr, ok := strings.Cut(hp, ":")
	if !ok {
		return "", 0, errors.New("nebulalink: missing port")
	}
	port, err := strconv.Atoi(portStr)
	if err != nil || port <= 0 || port > 65535 {
		return "", 0, fmt.Errorf("nebulalink: bad port %q", portStr)
	}
	return host, port, nil
}

func parseQuery(q string) map[string]string {
	m := make(map[string]string)
	for _, pair := range strings.Split(q, "&") {
		if pair == "" {
			continue
		}
		k, v, _ := strings.Cut(pair, "=")
		m[unescape(k)] = unescape(v)
	}
	return m
}

// unescape resolves percent-encoding without rejecting the stray "%" that
// hand-written links tend to carry.
func unescape(s string) string {
	if !strings.ContainsAny(s, "%+") {
		return s
	}
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		switch {
		case s[i] == '+':
			b.WriteByte(' ')
		case s[i] == '%' && i+2 < len(s):
			if v, err := strconv.ParseUint(s[i+1:i+3], 16, 8); err == nil {
				b.WriteByte(byte(v))
				i += 2
				continue
			}
			b.WriteByte(s[i])
		default:
			b.WriteByte(s[i])
		}
	}
	return b.String()
}

// decodeBase64 accepts both standard and URL-safe alphabets, padded or not.
func decodeBase64(s string) (string, bool) {
	s = strings.Map(dropNewline, strings.TrimSpace(s))
	if s == "" {
		return "", false
	}
	for _, enc := range []*base64.Encoding{
		base64.StdEncoding, base64.RawStdEncoding,
		base64.URLEncoding, base64.RawURLEncoding,
	} {
		if b, err := enc.DecodeString(s); err == nil && isMostlyText(b) {
			return string(b), true
		}
	}
	return "", false
}

func dropNewline(r rune) rune {
	if isNewline(r) {
		return -1
	}
	return r
}

func isMostlyText(b []byte) bool {
	if len(b) == 0 {
		return false
	}
	for _, c := range b {
		if c == 0 {
			return false
		}
	}
	return true
}

// flagOf extracts a leading regional-indicator emoji from a server name and
// derives the ISO country code from it.
func flagOf(name string) (string, string) {
	rs := []rune(strings.TrimSpace(name))
	if len(rs) < 2 {
		return "", ""
	}
	const base = 0x1F1E6
	if rs[0] >= base && rs[0] <= base+25 && rs[1] >= base && rs[1] <= base+25 {
		code := string([]rune{'A' + rs[0] - base, 'A' + rs[1] - base})
		return string(rs[:2]), code
	}
	return "", ""
}

func truthy(v string) bool {
	switch strings.ToLower(v) {
	case "1", "true", "yes", "on":
		return true
	}
	return false
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if v != "" {
			return v
		}
	}
	return ""
}

func asInt(v any) int {
	switch t := v.(type) {
	case float64:
		return int(t)
	case string:
		n, _ := strconv.Atoi(strings.TrimSpace(t))
		return n
	case int:
		return t
	}
	return 0
}
