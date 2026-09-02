package xraycfg

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/nebulagram/nebulagram/core/model"
)

// v2rayProfile is one entry of a `v2ray-json` subscription: a whole Xray config
// whose display name lives in "remarks".
type v2rayProfile struct {
	Remarks   string            `json:"remarks"`
	Outbounds []json.RawMessage `json:"outbounds"`
}

// profileDocument keeps the untouched JSON of one profile alongside the fields
// we read, because the profile is what actually gets run.
type profileDocument struct {
	profile v2rayProfile
	raw     json.RawMessage
}

// ParseV2RayJSON reads the v2ray-json subscription format: an array of configs,
// a single config, or a bare array of outbounds. Entries we cannot represent
// are skipped rather than failing the whole subscription.
func ParseV2RayJSON(data []byte) ([]model.Server, error) {
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "" {
		return nil, errors.New("xraycfg: empty profile")
	}

	documents, err := splitProfiles(data, trimmed[0] == '[')
	if err != nil {
		return nil, err
	}

	var servers []model.Server
	for _, doc := range documents {
		for _, raw := range doc.profile.Outbounds {
			s, err := ParseOutbound(raw)
			if err != nil {
				continue
			}
			if doc.profile.Remarks != "" {
				s.Name = doc.profile.Remarks
			}
			if s.Name == "" {
				s.Name = fmt.Sprintf("%s %s:%d", s.Protocol, s.Address, s.Port)
			}
			// The panel may ship routing, DNS or fragmentation with the
			// profile, so the whole document is what we run later; the fields
			// above only drive the list UI and the latency probe.
			s.Config = string(doc.raw)
			s.CoreHint = model.EngineXray
			s.ID = s.StableID()
			servers = append(servers, s)
			break // one proxy outbound per profile; the rest are direct/block
		}
	}
	if len(servers) == 0 {
		return nil, errors.New("xraycfg: profile has no proxy outbound")
	}
	return servers, nil
}

// splitProfiles reads the three shapes of the v2ray-json format: an array of
// configs, a single config, or a bare array of outbounds.
func splitProfiles(data []byte, isArray bool) ([]profileDocument, error) {
	if !isArray {
		var single v2rayProfile
		if err := json.Unmarshal(data, &single); err != nil {
			return nil, err
		}
		return []profileDocument{{profile: single, raw: data}}, nil
	}

	var rawProfiles []json.RawMessage
	if err := json.Unmarshal(data, &rawProfiles); err != nil {
		return nil, err
	}
	documents := make([]profileDocument, 0, len(rawProfiles))
	bareOutbounds := make([]json.RawMessage, 0, len(rawProfiles))
	for _, raw := range rawProfiles {
		var profile v2rayProfile
		if err := json.Unmarshal(raw, &profile); err != nil {
			continue
		}
		if len(profile.Outbounds) == 0 {
			bareOutbounds = append(bareOutbounds, raw)
			continue
		}
		documents = append(documents, profileDocument{profile: profile, raw: raw})
	}
	if len(documents) == 0 && len(bareOutbounds) > 0 {
		// The array held outbounds rather than configs; wrap them into one
		// document so they still become servers.
		wrapped, err := json.Marshal(map[string]any{"outbounds": bareOutbounds})
		if err != nil {
			return nil, err
		}
		return []profileDocument{{
			profile: v2rayProfile{Outbounds: bareOutbounds},
			raw:     wrapped,
		}}, nil
	}
	return documents, nil
}

// rawOutbound mirrors the parts of an Xray outbound NebulaLink understands.
type rawOutbound struct {
	Tag      string `json:"tag"`
	Protocol string `json:"protocol"`
	Settings struct {
		Vnext []struct {
			Address string `json:"address"`
			Port    int    `json:"port"`
			Users   []struct {
				ID      string `json:"id"`
				Flow    string `json:"flow"`
				AlterID int    `json:"alterId"`
			} `json:"users"`
		} `json:"vnext"`
		Servers []struct {
			Address  string `json:"address"`
			Port     int    `json:"port"`
			Password string `json:"password"`
			Method   string `json:"method"`
		} `json:"servers"`
	} `json:"settings"`
	StreamSettings struct {
		Network  string `json:"network"`
		Security string `json:"security"`
		TLS      struct {
			ServerName    string   `json:"serverName"`
			Fingerprint   string   `json:"fingerprint"`
			ALPN          []string `json:"alpn"`
			AllowInsecure bool     `json:"allowInsecure"`
		} `json:"tlsSettings"`
		Reality struct {
			ServerName  string `json:"serverName"`
			PublicKey   string `json:"publicKey"`
			ShortID     string `json:"shortId"`
			SpiderX     string `json:"spiderX"`
			Fingerprint string `json:"fingerprint"`
		} `json:"realitySettings"`
		WS struct {
			Path string `json:"path"`
			Host string `json:"host"`
		} `json:"wsSettings"`
		HTTPUpgrade struct {
			Path string `json:"path"`
			Host string `json:"host"`
		} `json:"httpupgradeSettings"`
		XHTTP struct {
			Path string `json:"path"`
			Host string `json:"host"`
			Mode string `json:"mode"`
		} `json:"xhttpSettings"`
		GRPC struct {
			ServiceName string `json:"serviceName"`
			MultiMode   bool   `json:"multiMode"`
		} `json:"grpcSettings"`
	} `json:"streamSettings"`
}

// ParseOutbound converts a single Xray outbound into a Server.
func ParseOutbound(raw json.RawMessage) (model.Server, error) {
	var o rawOutbound
	if err := json.Unmarshal(raw, &o); err != nil {
		return model.Server{}, err
	}
	s := model.Server{Protocol: model.Protocol(o.Protocol)}
	switch s.Protocol {
	case model.VLESS, model.VMess:
		if len(o.Settings.Vnext) == 0 || len(o.Settings.Vnext[0].Users) == 0 {
			return model.Server{}, errors.New("xraycfg: outbound has no vnext user")
		}
		v := o.Settings.Vnext[0]
		s.Address, s.Port = v.Address, v.Port
		s.UUID, s.Flow, s.AlterID = v.Users[0].ID, v.Users[0].Flow, v.Users[0].AlterID
	case model.Trojan, model.Shadowsocks:
		if len(o.Settings.Servers) == 0 {
			return model.Server{}, errors.New("xraycfg: outbound has no server")
		}
		v := o.Settings.Servers[0]
		s.Address, s.Port = v.Address, v.Port
		s.Password, s.Method = v.Password, v.Method
	default:
		return model.Server{}, fmt.Errorf("xraycfg: outbound protocol %q is not a proxy", o.Protocol)
	}

	st := o.StreamSettings
	s.Network = orDefault(st.Network, "tcp")
	s.Security = orDefault(st.Security, "none")
	switch s.Network {
	case "ws":
		s.Path, s.Host = st.WS.Path, st.WS.Host
	case "httpupgrade":
		s.Path, s.Host = st.HTTPUpgrade.Path, st.HTTPUpgrade.Host
	case "xhttp", "splithttp":
		s.Network = "xhttp"
		s.Path, s.Host, s.Mode = st.XHTTP.Path, st.XHTTP.Host, st.XHTTP.Mode
	case "grpc":
		s.ServiceName = st.GRPC.ServiceName
		if st.GRPC.MultiMode {
			s.Mode = "multi"
		}
	}
	switch s.Security {
	case "tls":
		s.SNI = st.TLS.ServerName
		s.Fingerprint = st.TLS.Fingerprint
		s.ALPN = strings.Join(st.TLS.ALPN, ",")
		s.AllowInsecure = st.TLS.AllowInsecure
	case "reality":
		s.SNI = st.Reality.ServerName
		s.PublicKey = st.Reality.PublicKey
		s.ShortID = st.Reality.ShortID
		s.SpiderX = st.Reality.SpiderX
		s.Fingerprint = st.Reality.Fingerprint
	}
	if s.Address == "" || s.Port == 0 {
		return model.Server{}, errors.New("xraycfg: outbound has no host:port")
	}
	return s, nil
}
