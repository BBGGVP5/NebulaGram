package store

import (
	"errors"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/nebulagram/nebulagram/core/model"
)

var billingAmount = regexp.MustCompile(`^[0-9]{1,12}(\.[0-9]{1,4})?$`)
var billingCurrency = regexp.MustCompile(`^[A-Z]{3}$`)

// SetServerDetails replaces the editable fields, including clearing old values.
// A refreshed subscription preserves these fields for the same stable endpoint.
func (s *Store) SetServerDetails(id string, details model.ServerDetails) (model.ServerDetails, error) {
	details.Provider = strings.TrimSpace(details.Provider)
	details.Plan = strings.TrimSpace(details.Plan)
	details.Amount = strings.TrimSpace(details.Amount)
	details.Currency = strings.ToUpper(strings.TrimSpace(details.Currency))
	details.DueDate = strings.TrimSpace(details.DueDate)
	details.Notes = strings.TrimSpace(details.Notes)
	if utf8.RuneCountInString(details.Provider) > 160 || utf8.RuneCountInString(details.Plan) > 160 || utf8.RuneCountInString(details.Notes) > 2000 {
		return model.ServerDetails{}, errors.New("server details: text is too long")
	}
	if details.Amount != "" && !billingAmount.MatchString(details.Amount) {
		return model.ServerDetails{}, errors.New("server details: invalid amount")
	}
	if details.Currency != "" && !billingCurrency.MatchString(details.Currency) {
		return model.ServerDetails{}, errors.New("server details: use a three-letter currency code")
	}
	if details.Amount != "" && details.Currency == "" {
		return model.ServerDetails{}, errors.New("server details: currency is required for an amount")
	}
	if details.DueDate != "" {
		if _, err := time.Parse("2006-01-02", details.DueDate); err != nil {
			return model.ServerDetails{}, errors.New("server details: invalid payment date")
		}
	}
	if details.PeriodDays < 0 || details.PeriodDays > 3660 {
		return model.ServerDetails{}, errors.New("server details: invalid payment period")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	for i := range s.state.Servers {
		if s.state.Servers[i].ID != id {
			continue
		}
		previous := s.state.Servers[i].Details
		s.state.Servers[i].Details = details
		if err := s.save(); err != nil {
			s.state.Servers[i].Details = previous
			return model.ServerDetails{}, err
		}
		return details, nil
	}
	return model.ServerDetails{}, errors.New("server details: server no longer exists")
}
