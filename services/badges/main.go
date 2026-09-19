// Сервер значков NebulaGram.
//
// Одна задача: помнить, кому какой значок выдан, и отвечать про одного
// человека за раз. Списком наружу не отдаёт ничего — выгрузить, кому что
// выдано, через этот API нельзя, и это единственная причина, по которой он
// вообще существует отдельно от опубликованного файла.
//
//	GET    /v1/badge/{id}   → {"badge":"supporter"} — открыто, это и видят все
//	POST   /v1/badge        → {"id":123,"badge":"dev"} — нужен админ-токен
//	DELETE /v1/badge/{id}   → снять значок — нужен админ-токен
//
// Пустой badge в POST равнозначен DELETE.
//
// Про соседство с Remnawave: сервис ничего о ней не знает и ничего в ней не
// трогает. Свой порт, свой файл, свой процесс. Именно поэтому обновление
// Remnawave его не задевает, а он не задевает её.
package main

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Виды значков, которые понимает клиент. Всё остальное отклоняем на входе:
// клиент незнакомый вид всё равно не нарисует, и молча хранить мусор незачем.
var kinds = map[string]bool{
	"supporter": true,
	"dev":       true,
	"tester":    true,
	"star":      true,
	"heart":     true,
}

type store struct {
	mu    sync.RWMutex
	path  string
	items map[string]string
}

func newStore(path string) (*store, error) {
	s := &store{path: path, items: map[string]string{}}
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(data, &s.items); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *store) get(id string) string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.items[id]
}

func (s *store) set(id, kind string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if kind == "" {
		delete(s.items, id)
	} else {
		s.items[id] = kind
	}
	data, err := json.MarshalIndent(s.items, "", "  ")
	if err != nil {
		return err
	}
	// Пишем через временный файл: оборванная запись не должна оставить
	// половину списка вместо списка.
	temp := s.path + ".tmp"
	if err := os.WriteFile(temp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(temp, s.path)
}

type server struct {
	store *store
	token string
}

func (srv *server) authorized(r *http.Request) bool {
	header := r.Header.Get("Authorization")
	const prefix = "Bearer "
	if !strings.HasPrefix(header, prefix) {
		return false
	}
	given := header[len(prefix):]
	// Сравнение без ветвления по длине совпадающего префикса.
	if len(given) != len(srv.token) {
		return false
	}
	var diff byte
	for i := 0; i < len(given); i++ {
		diff |= given[i] ^ srv.token[i]
	}
	return diff == 0
}

func validID(raw string) (string, bool) {
	if raw == "" || len(raw) > 20 {
		return "", false
	}
	value, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || value <= 0 {
		return "", false
	}
	return strconv.FormatInt(value, 10), true
}

func (srv *server) badge(w http.ResponseWriter, r *http.Request) {
	id, ok := validID(strings.TrimPrefix(r.URL.Path, "/v1/badge/"))
	switch {
	case r.Method == http.MethodGet:
		if !ok {
			http.Error(w, "bad id", http.StatusBadRequest)
			return
		}
		// Отвечаем всегда, и когда значка нет: иначе по коду ответа можно было
		// бы перебором составить тот самый список, которого мы не отдаём.
		writeJSON(w, map[string]string{"badge": srv.store.get(id)})
	case r.Method == http.MethodDelete:
		if !srv.authorized(r) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		if !ok {
			http.Error(w, "bad id", http.StatusBadRequest)
			return
		}
		if err := srv.store.set(id, ""); err != nil {
			http.Error(w, "cannot write", http.StatusInternalServerError)
			return
		}
		writeJSON(w, map[string]string{"badge": ""})
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func (srv *server) grant(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if !srv.authorized(r) {
		http.Error(w, "forbidden", http.StatusForbidden)
		return
	}
	var body struct {
		ID    json.Number `json:"id"`
		Badge string      `json:"badge"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&body); err != nil {
		http.Error(w, "bad body", http.StatusBadRequest)
		return
	}
	id, ok := validID(body.ID.String())
	if !ok {
		http.Error(w, "bad id", http.StatusBadRequest)
		return
	}
	if body.Badge != "" && !kinds[body.Badge] {
		http.Error(w, "unknown badge", http.StatusBadRequest)
		return
	}
	if err := srv.store.set(id, body.Badge); err != nil {
		http.Error(w, "cannot write", http.StatusInternalServerError)
		return
	}
	writeJSON(w, map[string]string{"badge": body.Badge})
}

func writeJSON(w http.ResponseWriter, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	// Ответ про одного человека меняется редко; сутки кэша снимают нагрузку и
	// совпадают с тем, сколько его держит клиент.
	w.Header().Set("Cache-Control", "public, max-age=86400")
	_ = json.NewEncoder(w).Encode(value)
}

func main() {
	token := os.Getenv("NEBULA_BADGES_TOKEN")
	if len(token) < 32 {
		log.Fatal("NEBULA_BADGES_TOKEN must be set and at least 32 characters")
	}
	path := os.Getenv("NEBULA_BADGES_FILE")
	if path == "" {
		path = "badges.json"
	}
	if absolute, err := filepath.Abs(path); err == nil {
		path = absolute
	}
	data, err := newStore(path)
	if err != nil {
		log.Fatalf("cannot open %s: %v", path, err)
	}
	address := os.Getenv("NEBULA_BADGES_ADDR")
	if address == "" {
		address = "127.0.0.1:8431"
	}

	srv := &server{store: data, token: token}
	mux := http.NewServeMux()
	mux.HandleFunc("/v1/badge/", srv.badge)
	mux.HandleFunc("/v1/badge", srv.grant)
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte("ok")) })

	log.Printf("badges: listening on %s, file %s", address, path)
	listener := &http.Server{
		Addr:              address,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}
	log.Fatal(listener.ListenAndServe())
}
