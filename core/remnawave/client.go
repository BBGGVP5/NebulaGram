// Package remnawave talks to a Remnawave panel's subscription endpoint.
//
// A Remnawave subscription answers with either a base64 list of share links or
// a v2ray-json / sing-box profile, and carries the profile metadata in response
// headers (traffic quota, expiry, announce, support URL). Device-bound
// subscriptions additionally require the HWID headers this client sends.
//
// The same client handles plain (non-Remnawave) subscription URLs: the header
// parsing simply finds nothing and the body is treated as a link list.
package remnawave

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/nebulagram/nebulagram/core/link"
	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/singboxcfg"
	"github.com/nebulagram/nebulagram/core/xraycfg"
)

// Device identifies this installation to a panel that enforces a device limit.
// Every field maps to one request header; an empty field is not sent.
type Device struct {
	HWID      string // x-hwid — stable per installation, user-resettable
	OS        string // x-device-os — "Android", "iOS", "Windows", ...
	OSVersion string // x-ver-os
	Model     string // x-device-model
	UserAgent string // user-agent — panels route format detection off this
}

// Client fetches subscriptions. The zero value is usable; Timeout defaults to
// 20s and UserAgent to the NebulaLink default.
type Client struct {
	HTTP    *http.Client
	Device  Device
	Timeout time.Duration
}

// DefaultUserAgent is what we send when the caller sets none. Panels use the
// UA to decide the payload format, and an unknown UA usually yields the base64
// list, which is the format we prefer.
const DefaultUserAgent = "NebulaLink/1.0"

// CompatibleUserAgent is asked second, and only when asking as ourselves
// produced nothing usable.
//
// Some panels answer an unrecognised client with a stub — one server named
// "client not supported" pointing at 0.0.0.0:1 — instead of the list. The
// name below is sing-box for Android, whose profile format this package
// already parses, so it is what we ask for rather than a browser's name: it
// describes a payload we genuinely support. Pin Settings.UserAgent to keep a
// panel's own identity and skip this entirely.
const CompatibleUserAgent = "SFA/1.11.0"

// Result is one successful subscription fetch.
type Result struct {
	Servers []model.Server
	Info    *model.SubscriptionInfo
	Format  string // "links" | "v2ray-json" | "sing-box"
	Raw     string
}

// Fetch downloads and parses a subscription URL.
//
// The panel is asked as NebulaLink first. If that answers with nothing we can
// connect to — an empty list, or only the stub a panel returns for a client it
// does not know — it is asked once more as a client whose format we support.
// A user-pinned UserAgent is never second-guessed.
func (c *Client) Fetch(ctx context.Context, rawURL, sourceName string) (*Result, error) {
	res, err := c.fetch(ctx, rawURL, sourceName, c.userAgent())
	if err == nil || c.Device.UserAgent != "" {
		return res, err
	}
	retry, retryErr := c.fetch(ctx, rawURL, sourceName, CompatibleUserAgent)
	if retryErr != nil {
		return nil, err // the first answer is the one worth reporting
	}
	return retry, nil
}

func (c *Client) userAgent() string {
	if c.Device.UserAgent != "" {
		return c.Device.UserAgent
	}
	return DefaultUserAgent
}

func (c *Client) fetch(ctx context.Context, rawURL, sourceName, userAgent string) (*Result, error) {
	if strings.TrimSpace(rawURL) == "" {
		return nil, errors.New("remnawave: empty subscription url")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, fmt.Errorf("remnawave: bad url: %w", err)
	}
	c.applyHeaders(req, userAgent)

	resp, err := c.httpClient().Do(req)
	if err != nil {
		return nil, fmt.Errorf("remnawave: request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("remnawave: panel answered %s", resp.Status)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 8<<20))
	if err != nil {
		return nil, fmt.Errorf("remnawave: read failed: %w", err)
	}

	res := &Result{Raw: string(body), Info: infoFromHeaders(resp.Header)}
	res.Servers, res.Format = parseBody(res.Raw, sourceName)
	res.Servers = usable(res.Servers)
	if len(res.Servers) == 0 {
		return nil, errors.New("remnawave: subscription contains no usable servers")
	}
	if res.Info != nil && res.Info.Title != "" && sourceName == "" {
		for i := range res.Servers {
			res.Servers[i].Source = res.Info.Title
		}
	}
	return res, nil
}

// usable drops entries that cannot be connected to. A panel that does not
// recognise the client sends its refusal as a server: a placeholder endpoint
// with a name explaining the refusal, which would otherwise sit in the list
// looking selectable and fail on every attempt.
func usable(servers []model.Server) []model.Server {
	kept := servers[:0]
	for _, s := range servers {
		if placeholder(s) {
			continue
		}
		kept = append(kept, s)
	}
	return kept
}

// Only addresses that can never name an endpoint, and the all-zero identity a
// refusal stub carries. Loopback stays allowed: someone running a local proxy
// is unusual, not impossible, and it is not this package's call to forbid it.
func placeholder(s model.Server) bool {
	switch strings.TrimSpace(s.Address) {
	case "", "0.0.0.0", "::":
		return true
	}
	if s.Port <= 0 || s.Port > 65535 {
		return true
	}
	return s.UUID == "00000000-0000-0000-0000-000000000000"
}

func (c *Client) applyHeaders(req *http.Request, ua string) {
	if ua == "" {
		ua = DefaultUserAgent
	}
	req.Header.Set("User-Agent", ua)
	req.Header.Set("Accept", "*/*")
	setIf(req.Header, "x-hwid", c.Device.HWID)
	setIf(req.Header, "x-device-os", c.Device.OS)
	setIf(req.Header, "x-ver-os", c.Device.OSVersion)
	setIf(req.Header, "x-device-model", c.Device.Model)
}

func setIf(h http.Header, key, value string) {
	if value != "" {
		h.Set(key, value)
	}
}

func (c *Client) httpClient() *http.Client {
	if c.HTTP != nil {
		return c.HTTP
	}
	timeout := c.Timeout
	if timeout <= 0 {
		timeout = 20 * time.Second
	}
	return &http.Client{Timeout: timeout}
}

// parseBody picks the payload shape and returns the servers it yields.
func parseBody(body, source string) ([]model.Server, string) {
	trimmed := strings.TrimSpace(body)
	if strings.HasPrefix(trimmed, "{") || strings.HasPrefix(trimmed, "[") {
		if servers, format, err := parseJSONProfile(trimmed, source); err == nil && len(servers) > 0 {
			return servers, format
		}
	}
	return link.ParseMany(body, source), "links"
}

func parseJSONProfile(body, source string) ([]model.Server, string, error) {
	if servers, err := xraycfg.ParseV2RayJSON([]byte(body)); err == nil && len(servers) > 0 {
		stamp(servers, source)
		return servers, "v2ray-json", nil
	}
	servers, err := singboxcfg.ParseProfile([]byte(body))
	if err != nil {
		return nil, "", err
	}
	stamp(servers, source)
	return servers, "sing-box", nil
}

func stamp(servers []model.Server, source string) {
	if source == "" {
		return
	}
	for i := range servers {
		servers[i].Source = source
	}
}

// infoFromHeaders reads the Remnawave profile headers. Returns nil when the
// response carries none of them (a plain subscription host).
func infoFromHeaders(h http.Header) *model.SubscriptionInfo {
	info := &model.SubscriptionInfo{
		Title:          decodeHeaderText(h.Get("profile-title")),
		SupportURL:     h.Get("support-url"),
		Announce:       decodeHeaderText(h.Get("announce")),
		ProfileWebPage: h.Get("profile-web-page-url"),
	}
	if v, err := strconv.Atoi(h.Get("profile-update-interval")); err == nil {
		info.UpdateInterval = v * 24 // header is in days
	}
	parseUserInfo(h.Get("subscription-userinfo"), info)
	if *info == (model.SubscriptionInfo{}) {
		return nil
	}
	return info
}

// parseUserInfo reads the `upload=..; download=..; total=..; expire=..` header.
func parseUserInfo(value string, info *model.SubscriptionInfo) {
	for _, part := range strings.Split(value, ";") {
		key, val, ok := strings.Cut(strings.TrimSpace(part), "=")
		if !ok {
			continue
		}
		n, err := strconv.ParseInt(strings.TrimSpace(val), 10, 64)
		if err != nil {
			continue
		}
		switch strings.ToLower(strings.TrimSpace(key)) {
		case "upload":
			info.Upload = n
		case "download":
			info.Download = n
		case "total":
			info.Total = n
		case "expire":
			info.Expire = n
		}
	}
}

// decodeHeaderText handles the `base64:<payload>` form panels use to keep
// non-ASCII header values safe — both the profile title and the announcement
// arrive that way, and the announcement is multi-line on top of it.
func decodeHeaderText(v string) string {
	payload, ok := strings.CutPrefix(v, "base64:")
	if !ok {
		return v
	}
	for _, enc := range []*base64.Encoding{
		base64.StdEncoding, base64.RawStdEncoding,
		base64.URLEncoding, base64.RawURLEncoding,
	} {
		if b, err := enc.DecodeString(payload); err == nil {
			return string(b)
		}
	}
	return v
}
