//go:build cgo

// Command nebulalink-cabi exposes the core through a C ABI, for the desktop
// fork (C++/Qt) and for any other native host.
//
// Build:
//
//	go build -buildmode=c-shared -o libnebulalink.dll ./bind/cabi   # Windows
//	go build -buildmode=c-shared -o libnebulalink.so  ./bind/cabi   # Linux
//	go build -buildmode=c-shared -o libnebulalink.dylib ./bind/cabi # macOS
//
// The header the build emits declares three functions. Every returned string is
// heap-allocated by Go and must be released with NLFree.
package main

/*
#include <stdlib.h>

typedef void (*nl_event_cb)(const char *json);

static void nl_invoke_event_cb(nl_event_cb cb, const char *json) {
    if (cb != NULL) {
        cb(json);
    }
}
*/
import "C"

import (
	"unsafe"

	"github.com/nebulagram/nebulagram/core/api"
)

var core = api.New()

// NLCall runs one core method. The caller owns the returned string and must
// pass it to NLFree.
//
//export NLCall
func NLCall(method *C.char, payload *C.char) *C.char {
	return C.CString(core.Call(C.GoString(method), C.GoString(payload)))
}

// NLSetEventCallback installs a C callback that receives status events as JSON.
// Pass NULL to detach.
//
//export NLSetEventCallback
func NLSetEventCallback(cb C.nl_event_cb) {
	if cb == nil {
		core.SetEventSink(nil)
		return
	}
	core.SetEventSink(func(event string) {
		text := C.CString(event)
		defer C.free(unsafe.Pointer(text))
		C.nl_invoke_event_cb(cb, text)
	})
}

// NLFree releases a string returned by this library.
//
//export NLFree
func NLFree(s *C.char) {
	C.free(unsafe.Pointer(s))
}

func main() {}
