# iOS October 9 interface parity implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Port the latest selection/menu and emoji fixes to native iOS, preserving standard bottom controls unless explicitly enabled.

**Architecture:** Add an ordered patch after 0084 against a reconstructed pinned tree. Keep Telegram selection state, permission checks, forwarding destinations, delete confirmation and native history loading. Settings and UIKit introductions stay in the overlay; cameras remain excluded. Execute inline under the user's existing authorization; the optional execution subskill is unavailable.

**Tech Stack:** Swift, UIKit, TelegramUI, SwiftSignalKit, XCTest, Python patch checks, macOS IPA CI.

## 1. Selection actions and preference
- [x] Add `selection_without_author` (false by default) to `NebulaMessagePreferences.swift` and its settings controller, mirror with `python platform/ios/tools/generate-overlay.py`.
- [x] Test persistence: `XCTAssertFalse(prefs.enabled("selection_without_author")); prefs.set("selection_without_author", true); XCTAssertTrue(restored.enabled("selection_without_author"))`, then restore false.
- [x] Add `NebulaSelectionActions.swift` in TelegramUI's existing source glob. Native bar items call selection of loaded eligible cloud messages (cap 100), rich copy, standard forwarding and confirmed deletion. Keep centered native animated title. Cancel moves to the left for ordinary selection; report/message-options modes retain native controls.
- [x] Keep asynchronously updated native action eligibility scoped to the exact selected IDs; dispose on deselection/deinit and recheck protection at action time. Use the current history view only for bulk selection; do not fetch history.
- [x] Add a separately enabled bottom no-author action via optional `ChatPanelInterfaceInteraction.nebulaForwardWithoutAuthor`; pass `ChatInterfaceForwardOptionsState(hideNames: true, hideCaptions: false, unhideNamesOnCaptionChange: false)` to the native picker. Preserve the explicit options in both same-chat/new-chat draft branches. Normal forwarding continues with nil options.

## 2. History menus and emoji lifecycle
- [x] Add `scrollToStartOfHistory()` before retained-message cleanup in native history/menu routes; preserve its existing cancellation/loading path. Exclude scheduled/secret/custom histories. Keep cleanup confirmation.
- [x] Gate `NebulaAnimatedSettingsEmoji.updatePlayback()` on nonzero laid-out bounds and call it after `layoutSubviews`; retain per-view decoder, page/background/reduced-motion pauses and replay on return. Replace obsolete nodes only after stopping them.
- [x] Add regression guards for entry-point wiring, native permissions, options propagation, opt-in defaults and layout-before-play behavior to `platform/ios/tools/check-bootstrap.py`.

## 3. About/update presentation
- [x] Theme `NebulaBuildInfoController` with AccountContext and the shared animated rocket introduction. Make the existing Updates row open the release channel rather than copy its name. Read installed iOS version/build/architecture; do not expose Android APK installation or pretend to check an iOS feed that does not exist.

## 4. Validation and delivery
- [x] Run `python platform/ios/tools/check-bootstrap.py`, `python scripts/check-settings-contract.py`, `python platform/ios/tools/test_native_build.py`, `python platform/ios/tools/test_ipa_build.py` and diff checks.
- [x] Commit/push intended files, run macOS bootstrap and `ios-ipa.yml`; fix compiler/test failures at the actual revision. Verify artifact revision/digest/bundle/architecture/extensions after success.
- [x] Update `platform/ios/PARITY.md` and `patches/ios/HOOKS.md`. Record no physical iOS runtime is available here; compilation does not prove gesture/visual acceptance.

Android follow-up from the October 9 video: restore nested drag/dismiss in both update sheets and diagnose the static rocket fallback. This does not replace the iOS port.

Validation checkpoint: macOS bootstrap 37897184535 passed at fffe517fb640d74651bd5d5c2e16986f5ca25855, including the new contract tests, native hook parsing and component checks. Full unsigned IPA run 37897185784 is still compiling; no IPA/device acceptance is claimed at this checkpoint. Android-only d9804f2 does not change these iOS runtime inputs.

Verified full IPA 37897185784 at fffe517fb640d74651bd5d5c2e16986f5ca25855: build 84, app.nebulagram, arm64 iPhoneOS and one Notification Service Extension; SHA-256 812aee67e1e45656f6c387c546136a6e7f6b4ad1bb106d53837dbfc31172d994 matches the CI receipt. User signing and physical-device acceptance remain unverified.
