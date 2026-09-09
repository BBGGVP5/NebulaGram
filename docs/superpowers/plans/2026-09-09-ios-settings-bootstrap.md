# iOS Settings Bootstrap Implementation Plan

**Goal:** Pin Telegram iOS, wire a native NebulaGram settings entry, and implement the first presentation preference end-to-end without pretending other controls work.

**Architecture:** Foundation-only shared contract/store plus a small ItemListUI adapter. Telegram hooks only route to the adapter and refresh folder badge presentation. Preserve real unread counts and VoiceOver. Keep iOS status experimental until device acceptance.

**Tech Stack:** Swift/Foundation, UserDefaults, Telegram ItemListUI/SwiftSignalKit, Bazel overlays, Python preparation checks, macOS CI.

- [x] Commit/push verified Android changes; confirm run 34332607529. Shared Swift foundation tests passed in run 34332607395.
- [x] Fetch official Telegram iOS and add gitlink at 6ad963e5b62d354da79040f388ae2b9132fb17b8. Read versions.json: app 12.9.2, Xcode 26.2, Bazel 8.4.2. Do not fetch nested submodules or claim a full build yet.
- [x] Add validated persistent store and observation token to `platform/ios/NebulaSettingsContract`; tests cover defaults, failed writes/imports, reload, unchanged notifications and unknown/unsupported controls.
- [x] Generate a Bazel-compatible embedded catalog and mirrored Swift sources from the same contract. Add overlay BUILD target; keep SPM/macOS tests working.
- [x] Add native `nebulaSettingsController(context:)` in SettingsUI with one functioning switch (`hide_tab_counters`) and an explicit development-status footer, not 63 fake controls.
- [x] Patch PeerInfo settings enum, entry and route plus module deps. Patch badge visibility/width and observe changes in the folder strip; retain count models and accessibility text.
- [ ] Add read-only iOS preparation/checking tool and CI checks. Verify ordered patch application to pinned vendor, generated source parity and Swift tests; typecheck UIKit adapter where possible. Document full Telegram build/device validation separately.
- [ ] Publish iOS source/CI changes without cancelling the already-running Android job if possible; report actual run status and remaining signing/device requirements.
