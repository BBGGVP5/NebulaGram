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
// UA to decide the payload format, and an unknown UA yields the base64 list,
// which is exactly the format we prefer.
const DefaultUserAgent = "NebulaLink/1.0"

// Result is one successful subscription fetch.
type Result struct {
	Servers []model.Server
	Info    *model.SubscriptionInfo
	Format  string // "links" | "v2ray-json" | "sing-box"
	Raw     string
}

// Fetch downloads and parses a subscription URL.
func (c *Client) Fetch(ctx context.Context, rawURL, sourceName string) (*Result, error) {
	if strings.TrimSpace(rawURL) == "" {
		return nil, errors.New("remnawave: empty subscription url")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, fmt.Errorf("remnawave: bad url: %w", err)
	}
	c.applyHeaders(req)

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

func (c *Client) applyHeaders(req *http.Request) {
	ua := c.Device.UserAgent
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
		Title:          decodeHeaderTitle(h.Get("profile-title")),
		SupportURL:     h.Get("support-url"),
		Announce:       h.Get("announce"),
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

// decodeHeaderTitle handles the `base64:<payload>` form panels use to keep
// non-ASCII titles header-safe.
func decodeHeaderTitle(v string) string {
	payload, ok := strings.CutPrefix(v, "base64:")
	if !ok {
		return v
	}
	for _, enc := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding} {
		if b, err := enc.DecodeString(payload); err == nil {
			return string(b)
		}
	}
	return v
}
