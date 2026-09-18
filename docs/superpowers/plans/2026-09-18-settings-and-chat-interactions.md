# Settings and chat interactions implementation plan

> Execute inline, preserving the unrelated in-progress Nimbo changes. No CI dispatch.

**Goal:** Make double taps predictable, expose the native rich editor, and give NebulaLink and the settings landing pages a clearer hierarchy on Android and iOS.

**Architecture:** Small ordered upstream patches for Telegram interactions; native overlay views for NebulaGram settings. Keep Telegram's iOS tab bar unchanged. Do not bypass server posting/reaction permissions or introduce another editor.

**Tech Stack:** Android Java/XML, iOS Swift/UIKit/ItemListUI, Python regression checks.

## 1. Chat interactions
- [x] Add `NebulaMessageActions.java`: a pure action resolver. Channel posts use native reaction handling; an unavailable edit or empty copy falls back to reaction; explicit Nothing stays disabled.
- [x] Test all actions, channel posts, non-owned messages and unavailable operations using javac/java assertions.
- [x] Add `patches/android/0092-chat-interaction-entry-points.patch`: use the resolver in both gesture eligibility and dispatch; unavailable channel quick reaction opens the native context menu, not a fake successful reaction.
- [x] Inspect both native rich-editor entry points. Keep server capability checks; improve discoverability without overlaying the send control.
- [x] Add `patches/ios/0016-chat-interaction-entry-points.patch`: decouple the native expanded editor from the unrelated AI compose flag; retain multiline space constraints and native draft handling. A missing default reaction opens the native reaction menu instead of silently returning.
- [x] Extend bootstrap/source guards to cover the new hooks, including unchanged native iOS tab-bar behavior.

## 2. Settings and NebulaLink
- [x] Android `NebulaConnectionCard.java`: lightweight themed status gradient, wrapping state/server text, accessible status, busy button state. No continuous blur or polling added.
- [x] Android `NebulaMenuFragment.java`: clearly labelled Connection and Servers/service groups, consistent card spacing and a concise Telegram-only traffic explanation. Retain schema dispatch and all Nimbo controls.
- [x] Android `NebulaSettingsFragment.java`: compact overview hero and purpose-based grouping using existing rows/routes.
- [x] iOS `NebulaLinkController.swift`: responsive connection overview header with one primary connect/disconnect action; retain subscription import, page-scoped probes, server selection and existing request lifecycle. Size header with Auto Layout for Dynamic Type; do not expose credentials.
- [x] iOS `NebulaSettingsController.swift`: reorder existing native entries into Connection/tools, Appearance/navigation, Privacy/history and transfer sections; preserve all callbacks and stable identity.

## 3. Verification and delivery
- [x] Run the interaction tests, settings-design checks and iOS bootstrap patch/overlay check.
- [x] Apply Android patch to disposable `build/final-verify-0904/tree`, sync only changed overlays and compile `:TMessagesProj:compileStandaloneJavaWithJavac` with Java 21.
- [x] Review `git diff --check` for our files, preserving unrelated dirty work. Report local checks separately from unavailable iOS SDK/device tests. Do not launch APK/IPA workflows.

## Verification outcome

- `scripts/check-chat-interactions.py`: Java gesture policy executed successfully; native integration guards passed.
- `scripts/check-nebulalink-layout.py`: layout/lifecycle, Nimbo row routing and 19 settings identity/order checks passed.
- `scripts/check-settings-design.py`: existing 600 AI page transitions and settings presentation guards passed.
- `platform/ios/tools/check-bootstrap.py`: 16 patches / 37 upstream paths applied; native tab bar, onboarding, expanded input and quick reaction guards passed.
- `scripts/check-upstream-series.py android --tree vendor/telegram-android`: all 87 ordered patches applied to the pin.
- Android `:TMessagesProj:compileStandaloneJavaWithJavac`: BUILD SUCCESSFUL, 3m 8s. Log: `build/settings-interactions-0918-javac.log`. Initial compile found a stale disposable resource set (the pre-existing Nimbo `nl_ping_estimate` string); syncing current overlay resources resolved it without changing the canonical resources.
- `git diff --check`: passed for changed tracked files. Unrelated Nimbo work remains in place. No commit, push or CI workflow dispatch in this task.
- `adb devices`: no attached devices. No Swift compiler/Apple SDK on this Windows host. UIKit compilation, device screenshots and end-to-end gesture tests remain unverified.

## Behavioral boundaries

- Android rich editor is now before attachment bots, while Telegram's capability gate remains. The multiline composer button keeps its original spacing rules.
- iOS expanded input no longer depends on AI availability; the native rich-input mode/kill switch and multiline layout remain in effect.
- A missing/unavailable quick reaction leads to native choices, not an invented reaction or a permission bypass. Channel posts do not inherit Android's personal-outgoing-message edit/copy action.
- Redesign covers the settings landing pages and NebulaLink, not a replacement of Telegram's native iOS navigation.
