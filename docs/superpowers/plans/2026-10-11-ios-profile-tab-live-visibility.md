# iOS profile tab visibility and icon Implementation Plan

> **For agentic workers:** Execute inline with the existing writing-plans workflow; optional execution plugins are unavailable.

**Goal:** Hide/show Profile immediately from its setting in both native/default and customized navigation, keep a reachable Settings route, and render Profile as an account avatar/person rather than Settings' gear animation.

**Architecture:** Add an ordered native follow-up patch against the validated 101 input. Keep Telegram's native tab component, theme-selected colors, lens/search/badges and controller identity; make visibility independent of the presentation mode. Reuse the native account-avatar signal for one-account Profile and prevent Settings animation on the person fallback. Check live settings delivery and native controller updates, including hiding the selected Profile and rebuilding on return.

**Tech Stack:** Swift, UIKit, SwiftSignalKit, existing NebulaSettingsContract and ordered native patches.

## 1. Diagnose and specify

- [x] Inspect the actual 101 root/profile/tab source after applying all 97 patches. Found forced `showProfileTab || !showBottomBar` and Settings' animation assigned to the profile fallback. Account-avatar signal currently requires another account, so a single-account Profile falls back.
- [x] Clarify restart behavior: user confirms restarting helps **hide Profile**, not Gemini Nano or its color. Inspect settings notifications and controller refresh in addition to forced visibility.
- [ ] Add regression cases for both presentation modes, all Profile/Settings visibility combinations, settings reachability and live false/true notifications. Tests should execute the same policy used by native root.

## 2. Implement native consumers

Files: canonical/mirrored `NebulaNavigationPolicy.swift`, its tests, `patches/ios/0098-profile-tab-live-visibility.patch`; native `TelegramRootController.swift` and `PeerInfoScreen.swift` consumers; `platform/ios/tools/check-bootstrap.py`.

- [ ] Apply Profile visibility in all modes; retain the Settings recovery route. Hide the selected Profile safely by preserving remaining controller identity or selecting Chats. Inspect whether a deferred native update/on-return refresh is required.
- [ ] Use account avatar for Profile even with one account. When no avatar is ready, use the person symbol without `TabSettings`; Settings remains a gear with its native warning/account behavior.
- [ ] Compare the native tab component's theme color assignments with pinned upstream. Keep intended accent/glass rendering; only fix a proven Nebula regression. The screenshot alone does not establish a color mapping defect.

## 3. Validate and deliver

- [ ] Run ordered patch/shared-contract checks and Swift policy tests; compile the actual native graph and device app. Record exact successful builds and verified archive hashes.
- [ ] Add the new checkpoint to PARITY. A physical iPhone rendering/toggle check remains required unless a reachable signed device is available; do not claim that from compiler tests.

Baseline: native chat run 38112302179 and full IPA 38112304987 succeeded at ff77388. Independently verified original **IPA 101**, 12.9.2/app.nebulagram, arm64 app plus one notification extension, 88,013,148 bytes, SHA-256 `902f27958589c19f1a252b1659ddceabe31d757317d7ead8c42ffff9ba336837`. It predates this tab repair. Android-only composer/error-card revision e9853ca is building in 38115968537 / 1000490; its runtime remains independent of this iOS change.
