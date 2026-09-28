# iOS Attachment Camera Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android `hide_attach_camera` preference remove the camera tile from the ordinary iOS chat attachment gallery while preserving explicit camera, avatar, and sticker creation routes.

**Architecture:** Keep the transfer key in `NebulaSettingsStore`, expose one switch on the existing Nebula settings screen, and gate only the `.assets(nil, .default)` camera preview in Telegram's `MediaPickerScreen`. The gallery layout already derives its cutout from whether a camera preview was created, so no custom grid geometry is needed.

**Tech Stack:** Swift, UIKit, Telegram iOS Bazel modules, ordered patch files, SwiftPM contract tests.

---

### Task 1: Store and settings

**Files:** `platform/ios/overlay/submodules/NebulaSettingsContract/Sources/NebulaSettingsStore.swift`, `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsController.swift`, `platform/ios/NebulaSettingsContract/Tests/NebulaSettingsContractTests/SettingsStoreTests.swift`

- [x] Add `hide_attach_camera` to `editableKeys` and `public var hideAttachCamera: Bool { boolean("hide_attach_camera", fallback: false) }` to the store.
- [x] Add a stable `navigationToggle` ID and order after `hide_send_as`, and display `Скрыть камеру во вложениях` / `Hide camera in attachments` using `store.hideAttachCamera`.
- [x] Test the false default and a persisted true value with the existing store round-trip test. Run `swift test` on a macOS worker when available; locally run the contract source guards.

### Task 2: Native attachment gallery

**Files:** `patches/ios/0039-hide-attachment-camera.patch`, `submodules/MediaPickerUI/BUILD`, `submodules/MediaPickerUI/Sources/MediaPickerScreen.swift` in the pinned upstream tree.

- [x] Add the `NebulaSettingsContract` Bazel dependency and import it in `MediaPickerScreen.swift`.
- [x] Change only the ordinary gallery branch to `if case .assets(nil, .default) = controller.subject, !NebulaSettingsStore.shared.hideAttachCamera { useLegacyCamera = true }`; leave `.createSticker` and `.createAvatar` camera branches intact.
- [x] Verify the upstream `cameraRect` is nil when no camera view exists, so thumbnails fill the first grid cell, and validate ordered patch application without modifying the vendor checkout.

### Task 3: Integration and delivery

**Files:** `platform/ios/PARITY.md`, `patches/ios/HOOKS.md`

- [x] Mark `hide_attach_camera` as source-wired, document that the in-gallery tile is hidden while explicit camera capture remains available, and record native/device QA as pending until build evidence exists.
- [ ] Run the iOS contract tests, ordered patch check, and native iOS CI build; fix compilation errors before reporting completion.
- [ ] Commit and push only the camera changes. Leave the existing uncommitted home-title work outside this change.
