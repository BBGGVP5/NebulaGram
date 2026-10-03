# iOS native parity and device build implementation plan

> **For agentic workers:** Execute inline, task by task, preserving the user's existing vendor and artwork changes. This request authorizes implementation and the final build, without another plan handoff.

**Goal:** Complete the outstanding iOS adaptations requested in this conversation and produce a verified arm64 device IPA.

**Architecture:** Extend actual Telegram camera, community, chat and profile consumers through ordered patches. Shared Nebula screens remain in the SettingsUI overlay and read Telegram presentation colors. Keep platform-specific rendering and native iOS navigation semantics; never expose a stored setting as functional without a consumer.

**Tech Stack:** Swift, UIKit, Telegram/Postbox/SwiftSignalKit, AVFoundation, Bazel, Xcode 26.2.

## 1. Camera and recording

- [x] Patch `submodules/Camera/Sources/Camera.swift` and `CameraDevice.swift`: report the active device's minimum/maximum, neutral factor and virtual-device switch factors on the capture queue. Route absolute displayed factors to that same device, including single-camera front mode. Coalesce drag requests and preserve the final zoom after pinch release.
- [x] Update `platform/ios/overlay/submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/NebulaVideoZoomSlider.swift`: remove independent discovery and hard caps, remove step buttons, use theme colors, fixed preset labels, centered bounded 280-point ruler with aligned labels and a persistent indicator.
- [x] Patch `VideoMessageCameraScreen.swift` to consume camera state and themed material; verify cancellation clears recording controls without moving the normal composer.

## 2. Dialogs, tools and language

- [x] Replace the shared choice sheet with a bounded centered, dimmed, opaque Telegram-themed popup; preserve index callbacks and Dynamic Type scrolling.
- [x] Add a language catalog with Russian/English shortcuts, localized names, native names, search and stable language codes. Use it for AI translation and summary results.
- [x] Add centered adaptive action rows to message tools (three columns when they fit, centered partial rows); route actual text, speech, task and AI actions without implicit network requests.
- [x] Pass the chat theme through all AI entry points and use an opaque editor body/navigation bar with native title layout.

## 3. Native community and settings consumers

- [x] Resolve `nebulaguard_channel` through the current account, observe its linked community and cached metadata, render a stable native avatar/title/count card and open `makeCommunityViewScreen`.
- [x] Replace the old community link directory in both settings and support; keep support before tools and shorten repetitive explanations.
- [x] Repair the navigation page range guard and group remaining profile/chat options according to their actual native consumers.
- [x] Audit older catalog gaps separately; wire valid cross-platform behaviors and record genuinely platform-owned features explicitly. Do not relabel unsupported imported values as implemented.

## 4. Verification and build

- [x] Export new patches from `build/ios-parity-1002` against the 59-patch baseline, leaving vendor unchanged.
- [x] Run `python platform/ios/tools/check-bootstrap.py`, settings contract/design checks and build-tool regression checks. Extend behavior tests for zoom mapping/ranges, language ordering and action grid geometry.
- [x] Publish the exact changes to `codex/camera-controls` and `main`, run macOS Swift tests and the full arm64 IPA workflow; repair compiler failures before delivery.
- [x] Download the IPA, verify manifest/source revision, archive contents, arm64 device binaries and digest. Report actual signing requirements and remaining device-only validation, not simulator compilation as an installable app.

Native tests: 73 passed for f352cb5; the full unsigned device IPA passed 37022621208 and its arm64/app/extension/source/digest were verified. The later live-translation batch passed 76 Swift tests but awaits its final native builds. The catalog distinguishes native iOS defaults (composer/icons/unread) from actual unfinished optional Android consumers; this request does not make those imported flags active without implementation.

## Follow-up: configurable live AI translation (2026-10-02)

- [x] Android: repair translation popup origin (top menu anchor); use scoped animation rather than changing other menus.
- [x] Add independent opt-in switches for composer tools, incoming translation, and draft translation; language pickers (Russian/English first), 0.5/1/2-second debounce, provider shortcut. Defaults off; per-account/per-chat translation preferences.
- [x] Incoming: reuse bounded Android queue; cancel on leaving/disabling, exclude outgoing/protected/secret messages. On iOS route visible IDs through the selected AI service, preserve original text and native translation attributes.
- [x] Draft: debounce requests, cancel/ignore stale responses, show preview with explicit Apply; never auto-send or overwrite a newer draft. Suspend on background/recording/editing protected content.
- [x] iOS: themed settings, composer accessory and equivalent incoming/draft consumers; shared lifecycle tests.
- [ ] Run meaningful queue/state tests, patch validation and Android/iOS native builds. Download and verify final artifacts; distinguish the earlier f352cb5 IPA checkpoint.


## Follow-up: Android profile banner and buttons

- [x] Fade the photograph into the current Telegram page background with a cached, eased bottom gradient.
- [x] Remove the action-button outline. Use one photograph-only backdrop for native blur/refraction, a soft filled highlight and unchanged native press/hit-target behavior.
- [x] Respect glass quality, blur/refraction and power-saving settings; retain a legible fallback on older devices and release the backdrop when detached.
- [x] Verify the final Android APK. No connected device is available for visual checks of scrolling, light/custom themes or glass on physical hardware.


## Complete remaining iOS adaptations — user follow-up

Continue inline. Preserve the published 61-patch baseline in `build/ios-parity-complete-1002`; new native adaptations form patch 0062. Keep feature flags out of the editable UI until their native consumer is wired.

1. Profile: update `PeerInfoHeaderNode.swift` and `PeerInfoHeaderButtonNode.swift` to blend expanded photo banners into the list theme and use a borderless native glass/blur surface with a readable fallback. Respect Reduce Transparency, Reduce Motion and glass quality. Keep native button hit targets and transitions.
2. Inventory: reconcile `platform/ios/PARITY.md` against the actual consumers and the requests in this conversation. Separate Android OS emulation (Material You, predictive back, system fonts/emoji) from portable missing behavior. Resolve the user's requested scope while doing independent profile work.
3. Portable consumers: extend `NebulaSettingsStore.swift`, expose only implemented controls in `NebulaSettingsController.swift`, and patch the owning native components. Verify persistence, bounded enum values, live updates and account isolation where applicable. Regenerate the mirrored contract with `generate-overlay.py`.
4. Validation: apply the full ordered patch series from the pinned iOS revision, execute contract tests and SDK checks in macOS CI, then run the full arm64 IPA workflow. Fix native compiler errors in the same work before declaring completion.
5. Delivery: update the inventory with exact implemented/native-platform/remaining statuses, download the actual final IPA and validate its source revision, app/extension binaries and digest. Preserve the signing and physical-device limitations.


## 2026-10-03 continuation checkpoint

- [x] Export profile material/fade and Foundation.Timer repair as 0062; preserve the vendor checkout.
- [x] Wire account behavior, native chat gestures/header, navigation/avatar/icon packs and switch/login style through 0063–0066 and native SettingsUI routes.
- [x] Update import classification tests now that portable keys have actual consumers; retain Material You as a non-active imported Android value.
- [x] Android workflow 37038627246 passed; APK SHA-256 38afcad7c595e97b85e0518a240eb47fd1dd38ee7155e2b92d9b707ef9e89f78, apksigner verification passed.
- [ ] Complete final iOS ordered-patch, SDK and native IPA checks; the previous translation build 37036563036 failed at ambiguous Timer and is not a deliverable.

- [x] macOS bootstrap 37096044690 passed at 7fa6206: 86 tests, 67 patches, embedded Foundation, UIKit switch, Objective-C icon adapter and SVG resources. Fixed Locale deprecation and UISwitch selector compatibility found by the SDK checks.
- [x] Diagnose full arm64 IPA 37096066321 failure in TelegramUI: remove optional chaining from the non-optional ChatControllerInteraction in patch 0063.
- [ ] Complete replacement full arm64 IPA and verify its exact source revision and archive.

- [x] Android recheck 37095621989 at 33e0f42 passed. Downloaded build 1000304: package app.nebulagram.messenger, arm64-v8a, Telegram and NebulaLink JNI libraries; signature matches prior builds. SHA-256 e171fe4749c6b7fa40a56b187c92c8d6267412e093dda17673b937ddb8a6af3a.
