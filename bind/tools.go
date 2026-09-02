//go:build tools

package bind

// gomobile generates its glue against golang.org/x/mobile/bind and looks the
// package up inside this module, so the dependency has to be declared even
// though no ordinary build imports it. The build tag keeps it out of the
// binary; `go mod tidy` still sees it and keeps the requirement.
import _ "golang.org/x/mobile/bind"
