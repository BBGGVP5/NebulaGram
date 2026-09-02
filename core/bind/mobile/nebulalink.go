// Package nebulalink is the gomobile-facing binding.
//
// gomobile only exports a narrow set of types, so everything crosses the border
// as a string: Android gets NebulaLink.call(method, payloadJson) and iOS gets
// NebulaLinkCall(method:payload:) from the very same code.
//
// Build:
//
//	gomobile bind -target=android -androidapi 21 -o nebulalink.aar  ./bind/mobile
//	gomobile bind -target=ios                      -o NebulaLink.xcframework ./bind/mobile
package nebulalink

import (
	"github.com/nebulagram/nebulagram/core/api"
)

var core = api.New()

// EventSink receives asynchronous events as JSON. Implemented on the platform
// side (a Java interface / an Objective-C protocol).
type EventSink interface {
	OnEvent(json string)
}

// Call runs one core method and returns the JSON response envelope.
func Call(method, payload string) string {
	return core.Call(method, payload)
}

// SetEventSink installs the listener that receives tunnel status updates.
// Passing nil detaches the current listener.
func SetEventSink(sink EventSink) {
	if sink == nil {
		core.SetEventSink(nil)
		return
	}
	core.SetEventSink(sink.OnEvent)
}

// Version returns the core version, for the About screen.
func Version() string { return api.Version }

// NewHWID mints a fresh device identifier, for the "reset HWID" action.
func NewHWID() string { return api.NewHWID() }
