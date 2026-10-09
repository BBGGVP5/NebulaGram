# Remaining iOS consumers implementation plan

> **For agentic workers:** Execute inline under the user's request to port all remaining portable features. The optional executing-plans skill is unavailable. Track implementation and acceptance separately.

**Goal:** Finish the non-camera Android-to-iOS consumer audit and implement the confirmed gaps without inert settings.

**Architecture:** Reuse native Telegram message eligibility, attributed text, gestures, context menus, account notifications and folder counts. Extend the Foundation contract and SettingsUI overlay; append native patches after 0085 against the pinned iOS tree. Preserve imported values, protected content and native cancellation. Camera/recorder engines remain excluded by the user's earlier scope.

**Tech Stack:** Swift, UIKit, Postbox, TelegramUI/ContextUI, XCTest, ordered patches, macOS Bazel/Xcode CI.

## 1. Baseline and audit
- [ ] Download and independently inspect successful IPA run 37897185784 at fffe517fb640d74651bd5d5c2e16986f5ca25855 (revision, digest, arm64, bundle and extension).
- [ ] Compare Android settings classes and their native consumers with iOS overlays and patches. Record implemented, native equivalent, excluded and missing separately in platform/ios/PARITY.md. Check beyond the shared catalog: menu controls, home title, notifications, authentication and local tools.
- [ ] Reconstruct build/ios-remaining-1009 from vendor's pinned commit plus all 85 patches; commit only the ignored reconstruction baseline for a precise new patch.

## 2. Chat preferences and gestures
- [ ] Wire existing transferable keys sticker_time_style, channel_forward_count, inline_math, folder_unmuted_only and swipe_actions in NebulaSettingsStore.swift. Add XCTest for defaults, import preservation, persistence and invalid input.
- [ ] Add a bounded Decimal arithmetic parser with no evaluation/runtime execution; test precedence, percentages, decimal separators, division by zero, phone/date suppression and resource limits. Display a non-mutating composer hint.
- [ ] Add message/media controls and an ordered swipe editor in SettingsUI. Preserve native Reply as the default; copy keeps entities, tools/translation use existing explicit UI and reject protected content.
- [ ] Wire native sticker status layout and channel forward-count metadata. Exclude unrelated message types, preserve native delivery/reaction controls and accessibility.
- [ ] Compute unmuted folder badge counts from native unread state without changing folder membership, server filters or read state; refresh after preference/notification changes.

## 3. Message menus
- [ ] Add NebulaMessageMenuPreferences.swift and tests for action visibility, immutable Edit/Delete and bounded presentation choices.
- [ ] Extend the actual extracted-message preview/settings with action toggles and compact layout. Filter native eligible actions by semantic identity, never translated labels; retain security and permission checks and required actions.
- [ ] Apply the same preferences to preview and real context menus, preserving native scrolling, extraction and dismissal ownership.

## 4. Other audit findings
- [ ] Implement confirmed portable gaps found in step 1 with native consumers and explicit settings. Do not copy Android-only camera, OS notification suppression, font or volume-button emulation into iOS switches. Document native equivalents precisely instead of silently omitting them.
- [ ] Align supported seek intervals with Android while preserving existing iOS values.

## 5. Verification and delivery
- [ ] Regenerate mirrors with python platform/ios/tools/generate-overlay.py; run python platform/ios/tools/check-bootstrap.py, python scripts/check-settings-contract.py and native/IPA driver tests.
- [ ] Extend bootstrap guards only for integration boundaries; use executable Swift tests for policy/algorithm behavior.
- [ ] Commit intended files, push authorized branches, run macOS bootstrap and full ios-ipa.yml. Diagnose actual native compiler errors and verify the final artifact.
- [ ] Update the inventory and earlier plans with exact evidence. Physical iPhone/simulator acceptance remains unverified unless an actual runtime becomes available.

Self-review: This plan covers identified missing consumers rather than inferring parity from stored/importable keys. Existing cloud sync, rich editor, browser blocking, accounts, native Community and selection toolbar are already implemented. An unsigned IPA is not installation or visual QA evidence.
