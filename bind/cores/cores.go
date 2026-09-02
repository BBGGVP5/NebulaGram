// Package cores links the tunnel engines into a build.
//
// The core module knows nothing about Xray or sing-box; the engines register
// themselves here, so a build can leave one out (a smaller binary) without any
// other package noticing.
package cores

import (
	xray "github.com/nebulagram/nebulagram/runtime/xray"
)

// Register wires every engine this build ships into the tunnel manager.
func Register() {
	xray.Register()
}
