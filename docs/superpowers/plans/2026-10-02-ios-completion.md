# iOS native parity and device build implementation plan

> **For agentic workers:** Execute inline, task by task, preserving the user's existing vendor and artwork changes. This request authorizes implementation and the final build, without another plan handoff.

**Goal:** Complete the outstanding iOS adaptations requested in this conversation and produce a verified arm64 device IPA.

**Architecture:** Extend actual Telegram camera, community, chat and profile consumers through ordered patches. Shared Nebula screens remain in the SettingsUI overlay and read Telegram presentation colors. Keep platform-specific rendering and native iOS navigation semantics; never expose a stored setting as functional without a consumer.

**Tech Stack:** Swift, UIKit, Telegram/Postbox/SwiftSignalKit, AVFoundation, Bazel, Xcode 26.2.

## 1. Camera and recording

- [ ] Patch `submodules/Camera/Sources/Camera.swift` and `CameraDevice.swift`: report the active device's minimum/maximum, neutral factor and virtual-device switch factors on the capture queue. Route absolute displayed factors to that same device, including single-camera front mode. Coalesce drag requests and preserve the final zoom after pinch release.
- [ ] Update `platform/ios/overlay/submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/NebulaVideoZoomSlider.swift`: remove independent discovery and hard caps, remove step buttons, use theme colors, fixed preset labels, centered bounded 280-point ruler with aligned labels and a persistent indicator.
- [ ] Patch `VideoMessageCameraScreen.swift` to consume camera state and themed material; verify cancellation clears recording controls without moving the normal composer.

## 2. Dialogs, tools and language

- [ ] Replace the shared choice sheet with a bounded centered, dimmed, opaque Telegram-themed popup; preserve index callbacks and Dynamic Type scrolling.
- [ ] Add a language catalog with Russian/English shortcuts, localized names, native names, search and stable language codes. Use it for AI translation and summary results.
- [ ] Add centered adaptive action rows to message tools (three columns when they fit, centered partial rows); route actual text, speech, task and AI actions without implicit network requests.
- [ ] Pass the chat theme through all AI entry points and use an opaque editor body/navigation bar with native title layout.

## 3. Native community and settings consumers

- [ ] Resolve `nebulaguard_channel` through the current account, observe its linked community and cached metadata, render a stable native avatar/title/count card and open `makeCommunityViewScreen`.
- [ ] Replace the old community link directory in both settings and support; keep support before tools and shorten repetitive explanations.
- [ ] Repair the navigation page range guard and group remaining profile/chat options according to their actual native consumers.
- [ ] Audit older catalog gaps separately; wire valid cross-platform behaviors and record genuinely platform-owned features explicitly. Do not relabel unsupported imported values as implemented.

## 4. Verification and build

- [ ] Export new patches from `build/ios-parity-1002` against the 59-patch baseline, leaving vendor unchanged.
- [ ] Run `python platform/ios/tools/check-bootstrap.py`, settings contract/design checks and build-tool regression checks. Extend behavior tests for zoom mapping/ranges, language ordering and action grid geometry.
- [ ] Publish the exact changes to `codex/camera-controls` and `main`, run macOS Swift tests and the full arm64 IPA workflow; repair compiler failures before delivery.
- [ ] Download the IPA, verify manifest/source revision, archive contents, arm64 device binaries and digest. Report actual signing requirements and remaining device-only validation, not simulator compilation as an installable app.
