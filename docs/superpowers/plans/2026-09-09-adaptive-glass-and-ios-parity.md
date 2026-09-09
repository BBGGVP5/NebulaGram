# Adaptive glass, archive protection and iOS parity implementation plan

**Goal:** Ship tested Android adaptive glass, then connect native iOS adaptive presentation, archive protection and system entry points without dummy settings.
**Architecture:** Preserve user appearance values; apply a transient resource policy (auto/full/light) at native render consumers. Observe system power/thermal events, never poll settings per frame. Archive protection must not claim encryption of content still visible in native history. iOS commands must route through existing authenticated navigation, not expose private archive data.
**Tech Stack:** Java/Android PowerManager, Swift/UIKit/LocalAuthentication/AppIntents, existing Telegram native modules and GitHub CI.

## Android delivery
- [ ] Add `NebulaGlassPolicy.java`: `reduced = mode == 2 || (mode == 0 && (powerSave || thermalHot || lowRam))`. Mode 1 retains full user settings; mode 0 is default.
- [ ] Add app-lifetime event observer, API-guard Android 29 thermal calls and refresh on resume. Never keep a strong Activity reference or change system display mode.
- [ ] Connect policy to cached glass getters, existing RenderNode blur and refraction. Light mode caps blur and suppresses refraction, preserving readable tint; reversion restores original values.
- [ ] Add mode picker in existing glass settings and transferable bounded integer key `glass_quality` (0...2).
- [ ] Test policy combinations, cached getter preference counts, native blur transitions, all Android regression scripts and Java compilation.
- [ ] Commit only task-owned paths, push and verify an Android CI run for that exact SHA. Do not claim successful APK until CI completes.

## iOS delivery
- [ ] Inventory actual native consumers against canonical catalog; keep unsupported controls visibly pending instead of accepting no-op edits.
- [ ] Apply power/thermal/reduce-transparency policy to shared GlassBackgroundComponent, with main-thread updates and automatic restoration.
- [ ] Add archive retention duration and per-chat exclusions; authenticate sensitive archive management using existing system LocalAuthentication. Do not mislabel a settings-screen lock as protection of inline messages.
- [ ] Reuse native WidgetKit and navigation/shortcut infrastructure for safe app entry points; no unauthenticated message export or server changes.
- [ ] Port settings in independently compiled consumer groups: glass, bottom navigation, chat header, appearance, gestures. Credential-dependent AI/proxy features require separate platform adapters.
- [ ] Run Swift package/bootstrap/native CI; test signed app on iPhone before asserting parity or black-screen resolution.

## Acceptance boundaries
No guarantees of background delivery or permanent cached media. Full parity is not established by generating a settings screen. Preserve unrelated design artifacts and NebulaMenuBackdrop changes.

## Delivery checkpoint
Android adaptive policy, app-lifetime observers, reversible native shader/blur consumers, mode picker and transfer key are implemented. Policy/restore/hot-path tests, 33 Android regressions, Java compilation and 78-patch reproduction passed. Commit 762aafb was dispatched, but its dependency download failed. Commits e483017/ad1f9d9 pin Go tooling and add tested bounded retries; replacement Android run is 34379577262.

On iOS, policy/shared glass consumer, archive retention/exclusions + native whole-app lock route, quick-action commands/widget, Contacts visibility/order are wired. Full native compile and signed-device behavior remain unverified. `platform/ios/PARITY.md` inventories every catalog key, and explicitly lists remaining consumer groups. Shared-store XCTest at 76ce9ea passed after fixing local-only settings validation; the latest tab-order change has another bootstrap run.
