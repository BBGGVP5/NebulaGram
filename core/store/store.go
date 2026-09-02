// Package store persists everything NebulaLink remembers: servers,
// subscriptions and settings.
//
// The whole state is one JSON file written atomically, which keeps the platform
// side trivial — each client only tells the core where its private directory
// is, and never touches the format itself.
package store

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/nebulagram/nebulagram/core/model"
	"github.com/nebulagram/nebulagram/core/settings"
)

// State is the on-disk document.
type State struct {
	Version       int                  `json:"version"`
	Settings      settings.Settings    `json:"settings"`
	Servers       []model.Server       `json:"servers"`
	Subscriptions []model.Subscription `json:"subscriptions"`
}

// CurrentVersion is bumped whenever State needs a migration.
const CurrentVersion = 1

// Store guards State and its file.
type Store struct {
	mu    sync.RWMutex
	path  string
	state State
}

// Open loads the state file, creating a default one when it is missing or
// unreadable. A corrupt file is moved aside rather than deleted, so a user can
// still recover their subscription links from it.
func Open(dir string) (*Store, error) {
	if dir == "" {
		return nil, errors.New("store: empty directory")
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, fmt.Errorf("store: cannot create %s: %w", dir, err)
	}
	s := &Store{path: filepath.Join(dir, "nebulalink.json")}

	data, err := os.ReadFile(s.path)
	switch {
	case errors.Is(err, os.ErrNotExist):
		s.state = defaultState()
		return s, s.save()
	case err != nil:
		return nil, fmt.Errorf("store: cannot read state: %w", err)
	}

	if err := json.Unmarshal(data, &s.state); err != nil {
		_ = os.Rename(s.path, s.path+".corrupt")
		s.state = defaultState()
		return s, s.save()
	}
	s.migrate()
	return s, nil
}

func defaultState() State {
	return State{Version: CurrentVersion, Settings: settings.Default()}
}

func (s *Store) migrate() {
	if s.state.Version == 0 {
		s.state.Version = CurrentVersion
	}
	s.state.Settings.Normalize()
}

// Settings returns a copy of the current settings.
func (s *Store) Settings() settings.Settings {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.state.Settings
}

// UpdateSettings applies mutate under the lock and persists the result.
func (s *Store) UpdateSettings(mutate func(*settings.Settings)) (settings.Settings, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	mutate(&s.state.Settings)
	s.state.Settings.Normalize()
	return s.state.Settings, s.save()
}

// Servers returns a copy of the stored server list.
func (s *Store) Servers() []model.Server {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return append([]model.Server(nil), s.state.Servers...)
}

// Subscriptions returns a copy of the stored subscriptions.
func (s *Store) Subscriptions() []model.Subscription {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return append([]model.Subscription(nil), s.state.Subscriptions...)
}

// Selected returns the currently chosen server, or nil.
func (s *Store) Selected() *model.Server {
	s.mu.RLock()
	defer s.mu.RUnlock()
	id := s.state.Settings.SelectedServerID
	for i := range s.state.Servers {
		if s.state.Servers[i].ID == id {
			server := s.state.Servers[i]
			return &server
		}
	}
	return nil
}

// Select marks a server as the active one.
func (s *Store) Select(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	found := false
	for i := range s.state.Servers {
		if s.state.Servers[i].ID == id {
			found = true
			break
		}
	}
	if !found {
		return fmt.Errorf("store: no server with id %q", id)
	}
	s.state.Settings.SelectedServerID = id
	return s.save()
}

// ReplaceSource swaps every server that came from one source for a fresh list,
// preserving measured latencies for endpoints that survived the refresh. The
// user's selection is kept whenever the same endpoint is still offered.
func (s *Store) ReplaceSource(source string, fresh []model.Server) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	previous := make(map[string]model.Server, len(s.state.Servers))
	kept := make([]model.Server, 0, len(s.state.Servers))
	for _, srv := range s.state.Servers {
		if srv.Source == source {
			previous[srv.ID] = srv
			continue
		}
		kept = append(kept, srv)
	}
	for i := range fresh {
		if old, ok := previous[fresh[i].ID]; ok {
			fresh[i].LatencyMs = old.LatencyMs
			fresh[i].CheckedAt = old.CheckedAt
		}
	}
	s.state.Servers = append(kept, fresh...)
	s.reselectLocked()
	return s.save()
}

// AddServers appends servers, skipping ones already known.
func (s *Store) AddServers(servers []model.Server) (added int, err error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	known := make(map[string]bool, len(s.state.Servers))
	for _, srv := range s.state.Servers {
		known[srv.ID] = true
	}
	for _, srv := range servers {
		if known[srv.ID] {
			continue
		}
		known[srv.ID] = true
		s.state.Servers = append(s.state.Servers, srv)
		added++
	}
	s.reselectLocked()
	return added, s.save()
}

// ClearServers drops every server; subscriptions are kept so the user can
// simply refresh.
func (s *Store) ClearServers() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.state.Servers = nil
	s.state.Settings.SelectedServerID = ""
	return s.save()
}

// PutSubscription inserts or updates a subscription by URL.
func (s *Store) PutSubscription(sub model.Subscription) (model.Subscription, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if sub.ID == "" {
		sub.ID = subscriptionID(sub.URL)
	}
	for i := range s.state.Subscriptions {
		if s.state.Subscriptions[i].ID == sub.ID {
			s.state.Subscriptions[i] = sub
			return sub, s.save()
		}
	}
	s.state.Subscriptions = append(s.state.Subscriptions, sub)
	return sub, s.save()
}

// RemoveSubscription drops a subscription and every server it provided.
func (s *Store) RemoveSubscription(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	var name string
	kept := s.state.Subscriptions[:0]
	for _, sub := range s.state.Subscriptions {
		if sub.ID == id {
			name = sub.Name
			continue
		}
		kept = append(kept, sub)
	}
	s.state.Subscriptions = kept

	if name != "" {
		servers := s.state.Servers[:0]
		for _, srv := range s.state.Servers {
			if srv.Source != name {
				servers = append(servers, srv)
			}
		}
		s.state.Servers = servers
	}
	s.reselectLocked()
	return s.save()
}

// UpdateServerLatency stores probe results.
func (s *Store) UpdateServerLatency(results map[string]int) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now().Unix()
	for i := range s.state.Servers {
		if ms, ok := results[s.state.Servers[i].ID]; ok {
			s.state.Servers[i].LatencyMs = ms
			s.state.Servers[i].CheckedAt = now
		}
	}
	return s.save()
}

// Filtered applies the current search and protocol filter, and sorts by
// latency (measured first, fastest first) then by name.
func (s *Store) Filtered() []model.Server {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := strings.ToLower(strings.TrimSpace(s.state.Settings.SearchQuery))
	protocol := s.state.Settings.ProtocolFilter

	out := make([]model.Server, 0, len(s.state.Servers))
	for _, srv := range s.state.Servers {
		if protocol != "" && string(srv.Protocol) != protocol {
			continue
		}
		if query != "" && !matches(srv, query) {
			continue
		}
		out = append(out, srv)
	}
	sort.SliceStable(out, func(i, j int) bool {
		li, lj := rank(out[i].LatencyMs), rank(out[j].LatencyMs)
		if li != lj {
			return li < lj
		}
		return out[i].Name < out[j].Name
	})
	return out
}

func matches(s model.Server, query string) bool {
	return strings.Contains(strings.ToLower(s.Name), query) ||
		strings.Contains(strings.ToLower(s.Source), query) ||
		strings.Contains(strings.ToLower(s.Address), query)
}

// rank orders measured servers ahead of unmeasured ones, failures last.
func rank(latency int) int {
	switch {
	case latency > 0:
		return latency
	case latency == 0:
		return 1 << 20 // never checked
	default:
		return 1 << 21 // failed
	}
}

// reselectLocked keeps SelectedServerID pointing at something that exists.
func (s *Store) reselectLocked() {
	id := s.state.Settings.SelectedServerID
	for _, srv := range s.state.Servers {
		if srv.ID == id {
			return
		}
	}
	if len(s.state.Servers) > 0 {
		s.state.Settings.SelectedServerID = s.state.Servers[0].ID
		return
	}
	s.state.Settings.SelectedServerID = ""
}

// save writes the state through a temporary file so a crash mid-write cannot
// leave the user with an unreadable state.
func (s *Store) save() error {
	s.state.Version = CurrentVersion
	data, err := json.MarshalIndent(s.state, "", "  ")
	if err != nil {
		return fmt.Errorf("store: cannot encode state: %w", err)
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return fmt.Errorf("store: cannot write state: %w", err)
	}
	if err := os.Rename(tmp, s.path); err != nil {
		return fmt.Errorf("store: cannot replace state: %w", err)
	}
	return nil
}

func subscriptionID(url string) string {
	sum := model.Server{Address: url}.StableID()
	return sum
}
