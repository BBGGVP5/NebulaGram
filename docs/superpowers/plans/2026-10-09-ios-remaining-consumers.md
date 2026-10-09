# Remaining iOS consumers implementation plan

> **For agentic workers:** Execute inline under the user's request to port all remaining portable features. The optional executing-plans skill is unavailable. Track implementation and acceptance separately.

**Goal:** Finish the non-camera Android-to-iOS consumer audit and implement the confirmed gaps without inert settings.

**Architecture:** Reuse native Telegram message eligibility, attributed text, gestures, context menus, account notifications and folder counts. Extend the Foundation contract and SettingsUI overlay; append native patches after 0085 against the pinned iOS tree. Preserve imported values, protected content and native cancellation. Camera/recorder engines remain excluded by the user's earlier scope.

**Tech Stack:** Swift, UIKit, Postbox, TelegramUI/ContextUI, XCTest, ordered patches, macOS Bazel/Xcode CI.

## 1. Baseline and audit
- [x] Download and independently inspect successful IPA run 37897185784 at fffe517fb640d74651bd5d5c2e16986f5ca25855 (revision, digest, arm64, bundle and extension).
- [x] Compare Android settings classes and their native consumers with iOS overlays and patches. Record implemented, native equivalent, excluded and missing separately in platform/ios/PARITY.md. Check beyond the shared catalog: menu controls, home title, notifications, authentication and local tools.
- [x] Reconstruct build/ios-remaining-1009 from vendor's pinned commit plus all 85 patches; commit only the ignored reconstruction baseline for a precise new patch.

## 2. Chat preferences and gestures
- [x] Wire existing transferable keys sticker_time_style, channel_forward_count, inline_math, folder_unmuted_only and swipe_actions in NebulaSettingsStore.swift. Add XCTest for defaults, import preservation, persistence and invalid input.
- [x] Add a bounded Decimal arithmetic parser with no evaluation/runtime execution; test precedence, percentages, decimal separators, division by zero, phone/date suppression and resource limits. Display a non-mutating composer hint.
- [x] Add message/media controls and an ordered swipe editor in SettingsUI. Preserve native Reply as the default; copy keeps entities, tools/translation use existing explicit UI and reject protected content.
- [x] Wire native sticker status layout and channel forward-count metadata. Exclude unrelated message types, preserve native delivery/reaction controls and accessibility.
- [x] Compute unmuted folder badge counts from native unread state without changing folder membership, server filters or read state; refresh after preference/notification changes.

## 3. Message menus
- [x] Add NebulaMessageMenuPreferences.swift and tests for action visibility, immutable Edit/Delete and bounded presentation choices.
- [x] Extend the actual extracted-message preview/settings with action toggles and compact layout. Filter native eligible actions by semantic identity, never translated labels; retain security and permission checks and required actions.
- [x] Apply the same preferences to preview and real context menus, preserving native scrolling, extraction and dismissal ownership.

## 4. Other audit findings
- [x] Implement confirmed portable gaps found in step 1 with native consumers and explicit settings. Do not copy Android-only camera, OS notification suppression, font or volume-button emulation into iOS switches. Document native equivalents precisely instead of silently omitting them.
- [x] Align supported seek intervals with Android while preserving existing iOS values.

## 5. Verification and delivery
- [x] Regenerate mirrors with python platform/ios/tools/generate-overlay.py; run python platform/ios/tools/check-bootstrap.py, python scripts/check-settings-contract.py and native/IPA driver tests.
- [x] Extend bootstrap guards only for integration boundaries; use executable Swift tests for policy/algorithm behavior.
- [x] Commit intended files, push authorized branches, run macOS bootstrap and full ios-ipa.yml. Diagnose actual native compiler errors and verify the final artifact.
- [x] Update the current inventory and this plan with exact evidence, superseding historical pending-source notes. Physical iPhone/simulator acceptance remains unverified unless an actual runtime becomes available.

Self-review: This plan covers identified missing consumers rather than inferring parity from stored/importable keys. Existing cloud sync, rich editor, browser blocking, accounts, native Community and selection toolbar are already implemented. An unsigned IPA is not installation or visual QA evidence.

## Runtime / evidence

- 7728305 + 169b4a7: five missing catalog consumers, arithmetic, ordered gestures, semantic menu controls and seek 25s. macOS bootstrap 37935705936 passed. Native 37935477697 targets 7728305.
- 7d9c483: account notifications, home title, nondestructive authentication test, peer ID format/copy, NebulaLink pull-to-refresh and app display name. 87 patches / 155 native paths, shared contract and driver tests pass locally. Bootstrap 37937153272 and full IPA 37937153786 dispatched.
- Platform differences explicitly recorded in PARITY.md. Camera scope remains excluded. Final artifact verification is recorded below; source completion is not physical acceptance.

- Deepened consumer audit found the old folder title/outline hooks only in the legacy peer-picker container. 0088 connects the actual home HorizontalTabsComponent, adds validated glass/solid/minimal panel style and local bottom placement with reserved insets and current-view reorder/selection forwarding. Source guards and persistence tests added; final macOS compilation passed at 144849c, as recorded below.


## Superseded source checkpoint (fabbf80)

- macOS bootstrap 37939365702 at cfd764d passed **135 XCTest cases**, native patch parsing, Foundation embedding and actual iOS SDK checks. fabbf80 only corrects the bottom spacing when the safe-area inset is zero; bootstrap 37939631877 checks this final source.
- 88 ordered patches / 157 upstream paths. Full IPA **37939638081** was superseded by the final runtime below. Prior IPA runs 37937153786 / 37938072597 / 37939370205 were cancelled because later source fixes superseded them, not reported as successful builds.
- User's non-camera source scope is implemented for the audited shared catalog and local behavior/message/menu/NebulaLink/ID settings. Eight catalog controls are documented native/OS-specific adaptations. All camera/encoder/phone-camera-preview work remains excluded.
- At this superseded checkpoint, full compilation/archive inspection were pending; both are recorded below. Physical/simulator UI testing remains unavailable.


## Final native compilation

- Runtime **144849cd181dc7c2c84c6844cb45a0775a3fdc86** keeps native Reply eligibility for service/call bubbles while restricting new export/AI actions.
- Bootstrap **37940965491** succeeded; all **135 XCTest cases** and SDK/patch checks passed.
- Native integration **37941215011** succeeded (1,873 actions): its real target includes Telegram Lib / TelegramUI / ChatListUI / SettingsUI / NebulaLinkUI / AuthorizationUI / peer and folder modules. This is more than standalone file parsing, but it is not physical-device acceptance.
- Final device IPA **37940972200** succeeded at 144849c: 12.9.2 / build 89, arm64, app.nebulagram, one notification extension. All earlier IPA runs in this turn were superseded.
- Artifact verification caught a separate metadata bug: Bazel generates its own plist and ignored the Xcode display-name change. **ff60327 / 0089** fixes that template and makes IPA validation reject a wrong/missing name. 89 ordered patches / 157 native paths and all eight IPA-driver tests pass; no native binary code changes.
- Verified delivery is the local unsigned derivative `build/artifact-37940972200-branded/NebulaGram-unsigned.ipa`, with only main-app CFBundleDisplayName changed. All 966 other ZIP entries (including executable files) match the original by SHA-256. Original CI digest: `def82095d3fdb704c3e3f64a4f3c77f2f91d463e8a8fc57294d4fbe73927b4f4`; delivery digest: `396433664c46b054e10e65169fc675f82f7eb2071785e29d74e450bc18fc7e28`, size 87,853,502 bytes. Source compilation is 144849c; packaging correction is ff60327. This is not a claim that ff60327 underwent a second full native build.
- Independent inspection passed for source revision, digest, bundle, arm64 device executables, notification extension and NebulaGram display name. Signing and device acceptance remain required.


## Physical iPhone acceptance (not executed)

These checks require a signed installation. Compiler and unit-test results above do not check rendered frames, touch routing or push delivery.

- [ ] Open/close General, NebulaLink, About and Updates repeatedly; verify matching header geometry and emoji replay. With Reduce Motion enabled, verify the intended still-frame alternative.
- [ ] Drag and dismiss sheets, switch AI modes by touch, and dismiss menus interactively. Verify header spacing, material contrast and absence of final-frame jumps or flicker.
- [ ] Open Nebula tools from a new draft and an edited message; apply translation/style/correction to text containing formatting and custom emoji. Check stale-result cancellation and that the accessory never overlaps attachments.
- [ ] Change folder icon/title, outline and glass/solid/minimal panel style. Move tabs top/bottom, swipe between folders, reorder them, enter/leave search and selection; repeat in landscape and with larger text.
- [ ] Compare unmuted folder counters with native folder rules before/after muting and reading messages. Confirm no folder membership changes.
- [ ] Reorder swipe actions; test Reply, rich Copy, Translate and Tools on eligible messages, cancellation and protected messages. Check VoiceOver access to the equivalent native menu actions.
- [ ] Toggle menu action visibility and compact rows; verify preview/real-menu agreement and that required Edit/Delete/Select actions remain reachable where natively allowed.
- [ ] Verify sticker timestamp styles and channel forward counts. Type bounded arithmetic, dates, phone numbers and IME composition; ensure hints never alter text or cover controls.
- [ ] With two accounts and network connectivity, disable one account's notifications and verify foreground/background delivery behavior. Confirm the other account and calls retain their existing policy.
- [ ] Copy user, group and channel IDs in both formats; run the nondestructive authentication check; cancel authentication and verify no chat/history deletion occurs.
- [ ] Pull to refresh NebulaLink overview/subscriptions on success and failure, including repeated pulls. Check spinner cleanup and unchanged connection/server selection.
