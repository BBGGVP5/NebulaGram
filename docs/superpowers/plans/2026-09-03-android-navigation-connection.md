# Android navigation and connection fixes

**Goal:** Fix duplicate navigation and repeated call permission prompts, show the active NebulaLink server, preserve subscription order by default, and provide an optional side panel.

**Architecture:** Keep Android behavior in `platform/android/overlay`, and integrate it using reproducible patches against the pinned Telegram revision. Reuse Telegram's main tabs. Keep server ordering in the Go store, before pagination. Execute the changes in this session.

**Tech Stack:** Java, Android views, SharedPreferences, Go, git patches.

### 1. Server order

- [x] Add a persisted `server_sort` preference (`default` or `latency`) in `core/settings/settings.go`.
- [x] Make `Store.Filtered` in `core/store/store.go` retain stored subscription order unless latency ordering is explicitly selected. Stable latency ordering must retain ties and place unmeasured and failed servers last.
- [x] Add regression coverage for order before/after probing, filtering, persistence, and explicit sorting in `core/store/store_test.go`.
- [x] Remove Java's unconditional sorting in `NebulaServersFragment.java`; add a sort selector that writes the preference through `settings.set`, then reloads `servers.list`.

### 2. Connection presentation

- [x] In `NebulaLink.java`, retain current tunnel status on the UI thread and notify attached views. Use the canonical `SharedConfig.addProxy` result; remove the managed entry before publishing disconnect notifications.
- [x] Add a `NebulaLinkRow.java` in NebulaGram Settings that shows NebulaLink, connected server name, and a semantic green accent. Keep disconnected and connecting states distinct.
- [x] Replace patch `0005` with managed-proxy presentation: hide the service endpoint and controls while connected, explain where to manage the tunnel, and leave no NebulaLink entry on the proxy screen. Restore ordinary proxy management when the tunnel is not active.
- [x] Update `NebulaConnectionCard.java`, `NebulaRow.java`, and `NebulaTheme.java` to show green connected state, respond to status changes, and clear stale server text.
- [x] Distinguish selected and connected servers in `NebulaServersFragment.java` and avoid duplicated flag prefixes.

### 3. Navigation

- [x] Replace the duplicate view implementation in `NebulaBottomBar.java` with preferences applied to `MainTabsActivity` and `MainTabsLayout`. Reuse native click handlers, counters, avatars, and animations.
- [x] Replace patch `0008` with hooks for native tabs and an optional side-panel button on the main dialogs screen. Apply preferences when returning from settings.
- [x] Add `NebulaSidePanel.java`: a dismissible panel aligned to the start edge, with chats, contacts, settings, profile, saved messages, and NebulaLink destinations. Use the fragment's account for destinations.
- [x] Add the side-panel toggle to `NebulaSettingsFragment.java`. Ensure that disabling either navigation surface leaves a way to reach settings.

### 4. Call permission prompts

- [x] Add patch `0009` saving the existing `askedAboutFSILockscreen` preference when the user opens Android's permission settings successfully; cover the analogous MIUI path.
- [x] Keep Android's actual permission check and the existing negative-answer behavior.

### 5. Verification and documentation

- [x] Update Russian and English strings, README ordering description, and `patches/android/HOOKS.md`.
- [x] Run `go test ./...` in `core`.
- [x] Apply the complete Android patch series in an isolated temporary git index against the pinned upstream, preserving the existing vendor build tree.
- [x] Compile Android Java/resources if the installed SDK and build dependencies permit it. Report any concrete build blocker.
- [x] Run `git diff --check` and review the resulting diff.
- [ ] Device checks: one navigation bar; tab toggles and side panel after returning from settings; permission request followed by return/restart; default server order after probing; active server name and green status; clean disconnect presentation.

## Validation results

- `go test ./...` in `core`: PASS, including store order/persistence and API sorting before pagination.
- All 10 Android patches apply to the pinned upstream and reverse-check against the isolated build tree.
- Russian and English string resources parse correctly; all new keys exist in both locales.
- `git diff --check`: PASS.
- Build workspace: `build/android-review/tree`; final build output: `build/android-review/gradle-final.log`.
- Device verification is pending: `adb devices` returned no connected devices.
- Final `:TMessagesProj:compileStandaloneJavaWithJavac`: BUILD SUCCESSFUL (24 seconds), using JDK 17 and the pinned Gradle/Android dependencies. This verifies Java and Android resources; it does not assemble or install an APK.
- All final Java overlay sources and changed string resources match the successful build inputs.

## Follow-up: settings entry and notification identity

- [x] Rename the client entry and custom screen to «Настройки NebulaGram» / «NebulaGram Settings».
- [x] Place the entry first in the main settings list, with its description; follow native settings navigation on tablets.
- [x] Override the notification drawable with the existing NebulaGram silhouette as a white 24dp vector on transparency.
- [x] Inspect silhouette previews at 24, 36 and 48px with light and dark tints.
- [x] Compile the final Java and Android resources: BUILD SUCCESSFUL in 29 seconds (`build/android-review/gradle-notification-settings.log`).

The notification icon and updated settings entry have not been installed on a device.

## Follow-up: move NebulaLink into custom settings

- [x] Show the live connection row in NebulaGram Settings, with the connected server and green accent.
- [x] Remove the entry from ProxyListActivity; keep the service endpoint hidden and restore manual proxy controls after disconnecting.
- [x] Verify the full patch series with `git apply --cached --3way`, as used by CI.
- [x] Compile final Java and resources: BUILD SUCCESSFUL in 1m 39s (`build/android-review/gradle-relocation.log`).
