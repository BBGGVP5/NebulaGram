# Cross-platform Settings Foundation Implementation Plan

**Goal:** Start a shared Android/iOS contract without pretending the iOS UI is implemented, and decouple chat-header layout from Material You.

**Architecture:** Keep native Telegram UI and small integration patches. Add a reviewed JSON catalog with existing Android keys, defaults/default policies, storage, validation and iOS implementation status. Generate the existing Java transfer allowlist and a standalone Swift contract resource from it; do not migrate preferences or silently expand exports yet. Track actual upstream patch touch points rather than promising conflict-free updates.

**Tech Stack:** JSON/Python code generation, Java Android overlays, Foundation-only Swift package, CI.

## 1. Header regression
- [x] Change the real header-policy test in `scripts/check-chat-layout.py`: for both Material You states, `savedClassic == normal && saved`, `floating == normal && !saved`. Verify failure before the fix.
- [x] Remove palette checks from `NebulaChatStyle.header`; preserve normal/saved guards. Remove palette gating from the matching avatar-menu hook in native ChatActivity, persisted in patch 0072. Hide the separate call icon when the floating header is selected, independent of palette.
- [x] Run native layout and theme tests and Java compilation; preserve pending patch 0071.

## 2. Shared settings contract
- [x] Create `shared/settings/catalog.json` with existing presentation allowlist plus explicit pending glass/haptic/avatar controls; assign feature, native storage, defaults or legacy policy, range, and iOS planned/adapt/unsupported status. Keep networking/secrets in their existing separate core domain.
- [x] Add `scripts/generate-settings-contract.py --check`: validate unique keys, type/default/range/storage/status; deterministically generate the Java allowlist and Swift package catalog resource. Preserve every v1 export key/type.
- [x] Add a Foundation-only package under `platform/ios/NebulaSettingsContract` with catalog decoding and typed value validation. Unsupported/planned entries are never advertised as implemented; do not write defaults on import. Add Swift tests for count/keys, invalid types/ranges, unknown keys, and implementation status.
- [x] Add Python/Java tests for generated drift, schema errors, v1 allowlist compatibility, source getter/default drift and exclusions. Configure a macOS CI job for Swift tests; local Swift is unavailable, so execution remains pending.

## 3. Upstream maintenance
- [x] Add `scripts/report-upstream-surface.py` to list patches and touched upstream files (read-only, no fetch/checkout). Test against pinned Android patches. Record the current iOS baseline as absent rather than reporting compatibility success.
- [x] Add a focused settings-contract workflow and Android pre-build contract check. Document the ownership boundary, current limitations and steps to add a feature or update a pinned upstream. Correct the old claim that overlay code can never break against changed upstream APIs.

## 4. Acceptance
- [x] All Android workflow regression commands pass; generated outputs are clean under `--check`; vendor reconstruction matches patch 0072; Java compile passes. Check ADB without claiming device acceptance if disconnected.
- [x] Record completed foundation and pending iOS build/UI/runtime work. No publication unless requested.

## Results

- 63 catalog entries; all 56 v1 allowlist keys/types preserved; 49 direct Android defaults checked.
- All 26 Android workflow regression commands passed. Java compilation passed in 3m23s and current bytecode was inspected.
- All 67 patches apply in order to pinned Android HEAD (73 touched paths); reconstructed ChatActivity matches the compiled tree.
- Added a read-only sequential applicability checker as well as the surface report. The old mutating sync/watcher flow is documented as still needing replacement.
- ADB: no devices. No APK/IPA published or installed; previous pending 0071 remains intact.
- [ ] Run Swift tests on macOS CI (workflow added, not dispatched).
- [ ] Pin Telegram iOS, integrate the Swift contract with native settings/UI, build and test on iPhone.
