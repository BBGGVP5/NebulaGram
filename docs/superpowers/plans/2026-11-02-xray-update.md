# Xray 26.10.31 update

**Goal:** Update the shared embedded Xray without changing Telegram or UI patches.
**Architecture:** Pin runtime/xray and bind to official release commit a4a3dff5b20c2faae567b742c172c7b95e75c083 using its Go pseudo-version. Resolve each module independently with GOWORK=off.
**Tech Stack:** Go 1.27.2, Xray-core, existing mobile bindings.

- [x] Verify stable upstream tag v26.10.31 and preserve latest UI fixes at 0cebb5c in a separate branch.
- [x] Run `go get github.com/xtls/xray-core@a4a3dff5b20c2faae567b742c172c7b95e75c083` and `go mod tidy` in runtime/xray and bind with GOWORK=off and GOTOOLCHAIN=auto. Review go.mod/go.sum changes.
- [x] Run `go test ./...` and `go vet ./...` in core, runtime/xray and bind; confirm selected Xray version in both consuming modules.
- [x] Prepared dependency changes and verification record for a dedicated commit.

Verification: go test ./... and go vet ./... passed independently in core, runtime/xray and bind (GOWORK=off, GOTOOLCHAIN=auto). go mod verify passed in runtime/xray and bind. Both resolve v1.260327.1-0.20261031132019-a4a3dff5b20c. APK/AAR/XCFramework were not built during this update.
