# NebulaLink startup and server rows implementation plan

> Execute inline in this task; keep the Android overlay as the source of truth.

**Goal:** Let users enable connection on app startup and make server latency, selection and country easier to scan.

**Architecture:** Persist `auto_connect` alongside the core settings and expose its switch in NebulaLink → Advanced → Connection, following the user's placement correction. Android reads the saved value after core initialization. Server rows use a separate latency badge and a country emoji in the existing icon slot; the original server data stays intact.

**Tech Stack:** Go settings/store/API, Java Android views, generated English/Russian XML resources.

---

### 1. Startup preference

- [x] Add `AutoConnect bool` (`json:"auto_connect"`, fresh-install default false) in `core/settings/settings.go`.
- [x] Add a `RowSwitch` in the Connection section of the advanced screen in `core/settings/menu.go`, with key `auto_connect`, title `Connect automatically`, and subtitle `Connect to the selected server when Telegram starts`.
- [x] Add Russian translations in `scripts/gen-menu-strings.py`; regenerate using `python scripts/gen-menu-strings.py`.
- [x] Update `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/nebulalink/NebulaLink.java`: use the settings returned by `core.init`, migrate the previous `tunnel_was_connected` preference once, start only when enabled and a selected server exists, and remove a stale managed proxy before startup. Save the owned proxy port when installing it so disabling startup cannot leave Telegram on a dead loopback endpoint. Tunnel status changes must no longer overwrite the user's startup choice.
- [x] Verify the preference and selected server survive a fresh `Core` instance, partial updates, and stopping the tunnel in `core/api/api_test.go`.

### 2. Server row presentation

- [x] Add a focused `NebulaServerLabel.java` helper under the overlay `app/nebulagram/ui`: recognize exactly a pair of regional-indicator code points at the beginning, remove only that prefix and surrounding whitespace, prefer this flag over metadata, and retain an address fallback for empty names.
- [x] Extend `NebulaRow.java` with an untinted emoji icon and measured trailing badge. Reserve badge width plus spacing before measuring the title, support long names, and use theme colours for success, neutral, and failed latency states.
- [x] Update `NebulaServersFragment.java`: protocol and `For connection`/`Connected` below the title; positive latency as `N ms` on the right, unmeasured as `—`, failed as `No reply`. Give the selected row a subtle background and use the existing success styling for the active server.
- [x] Add English/Russian selection and failed-probe strings in overlay `res/values/strings_nebula.xml` and `res/values-ru/strings_nebula.xml`.
- [x] Exercise the label helper with leading/non-leading flags, metadata, supplementary emoji, empty names, and flag-only names using a standalone JVM check.

### 3. Verification

- [x] Run `go test ./core/...` and `python scripts/check-java.py`.
- [x] Compile the modified Android sources/resources using the available SDK and prepared Gradle tree, without resetting the vendor checkout.
- [x] Inspect the final diff with `git diff --check` and report the tests and any device-verification limit. No publishing or release is part of this request.

Validation: core tests and vet passed; 14 standalone JVM label cases passed; Java checks passed. Android sources/resources compiled successfully in the prepared Gradle tree. Device UI and a live server connection were not exercised.
