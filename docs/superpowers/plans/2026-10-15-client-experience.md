# NebulaGram client experience implementation plan

**Goal:** Deliver the requested native formatting toolbar, cohesive Liquid Glass folders, scalable accounts, clearer settings and a redesigned sign-in / NebulaLink experience on Android and iOS.

**Architecture:** Extend existing native editing and authorization controllers, preserving Telegram entities, authentication state and account isolation. Use shared preference contracts and the existing glass renderer. Account scaling requires changing storage and native registries; raising a constant is not an unlimited-account implementation.

**Tech Stack:** Java/Android Canvas, Swift/UIKit, Telegram native C++ networking, shared JSON settings contract, Python/Java/Swift regression checks and GitHub Actions.

Execution is inline in the existing workspace. Preserve the dirty Android vendor checkout and work against owned source trees. Each phase must be independently buildable. Check boxes describe verified work, not intentions.

## 1. Research and acceptance criteria

- [x] Compare Swiftgram, Cherrygram, iMe, Ghostgram and TGExtra primary sources with `docs/FORK-FEATURES-2026-10-15.md`; record exact repositories and distinguish existing features, additions and unverified claims.
- [ ] Formatting: separate toggle, default off; bar between input and keyboard; native bold/italic/mono/quote/spoiler/link/clear; preserve selection, entities, undo, IME and draft ownership; hide during recording and non-text modes.
- [ ] Folders: one rounded glass strip and sliding selected capsule, compact counters, no rectangular individual tiles; support top/bottom placement, scrolling, RTL, reduced motion, light/dark themes and blur fallback.

## 2. Formatting toolbar

Files: new Android `NebulaFormattingToolbar.java`; native `ChatActivityEnterView.java` patch; Android `NebulaSectionFragment.java`, `NebulaSettingsSearch.java`; shared `settings/catalog.json`; iOS SettingsUI and native `ChatTextInputPanelNode.swift` integration.

- [x] Implement Android toolbar using `EditTextCaption` formatting actions, not Markdown text rewriting.
- [ ] Integrate measured keyboard space and lifecycle; preserve reply/edit previews and native recording controls.
- [x] Register preference, settings/search entry and platform contract.
- [x] Implement equivalent native iOS selection actions and accessory layout.
- [ ] Check empty/collapsed/reversed selection, links, emoji UTF-16 ranges, RTL, keyboard transitions, recording, theme and preference changes.

## 3. Folders and motion

Files: `NebulaFolderGlass.java`, `NebulaFolderTabs.java`, `NebulaTabLens.java`, native `FilterTabsView.java` / `DialogsActivity.java`, iOS folder components.

- [x] Inspect existing glass mode and fix the real visual gaps against the supplied reference; avoid adding a duplicate style.
- [ ] Reuse shared blur capture and selected capsule; cache render resources and avoid idle animation.
- [ ] Test interrupted swipe/tap, long labels, unread badges, narrow width, font scale, reorder, detach and system reduce motion.

## 4. Accounts without an artificial client cap

Files: Android `UserConfig`, controller singleton holders, account iteration and notifications, native `ConnectionsManager` registry / JNI, iOS account management.

- [ ] Inventory fixed arrays, bitmasks, native special slots, notification IDs and persisted account indices on both platforms.
- [ ] Design persistent account IDs and a dynamic registry with lazy controller creation; preserve existing account directories and keys.
- [ ] Migrate fixed arrays and native slots consistently; do not advertise an increased constant as unlimited.
- [ ] Implement account search/order and explicit resource policy for inactive accounts without silently breaking notifications.
- [ ] Test more than ten accounts using synthetic identities, restart/removal/reuse, concurrent lookup, notification routing and migration; no real accounts or credentials in fixtures.

## 5. Settings structure and performance

Files: Android `NebulaSettingsFragment`, `NebulaSettingsHubFragment`, `NebulaSettingsSearch`, `NebulaSectionFragment`; iOS `NebulaSettingsController`, `NebulaSettingsSearch`; shared settings contract.

- [ ] Organize into Appearance, Chats and composer, AI and audio, Navigation and folders, Accounts, Privacy, Connection and About; remove duplicate paths and clarify dependent controls.
- [ ] Add direct access to frequently used options, searchable labels and current-value summaries; preserve deep links and exported settings.
- [x] Debounce search, cancel pending work on detach and avoid rebuilding unchanged results / stale theme caches.
- [ ] Test preference round trips, migration, dependencies, live updates, search/deep links, accessibility and locale changes.

## 6. Welcome, NebulaLink and authorization redesign

Files: Android `NebulaIntroFragment`, `NebulaIntroArt`, `NebulaOnboardingLayout`, `NebulaConnectFragment`, `NebulaLoginStyle`, `NebulaAuthArt`; iOS AuthorizationUI `NebulaWelcomeController`, matching native phone/code/password presentation adapters.

- [ ] Create common visual language: calm rounded surfaces, clear typography and generous spacing; remove leftover outlined Material controls, floating labels and floating action buttons in styled flows.
- [ ] Recompose welcome illustrations around real product actions, consistent drawing style and theme-aware contrast; reuse code-native art instead of loading large decorative bitmaps.
- [ ] Redesign NebulaLink import/connect with explicit optional skip, clipboard import, server state, errors/retry and a stable primary action.
- [ ] Redesign phone/country, confirmation, code/resend and password/recovery while preserving native validation, timers, autofill and authentication requests.
- [ ] Respect keyboard/safe areas, small screens, landscape, large text and reduce motion; never hide the keyboard merely because a phone-length guess was reached.
- [ ] Port visual changes to iOS using native layout and interactions, not Android-specific widgets.

## 7. Additional fork features

- [ ] Use the audit to implement missing message/file details, explicit link cleanup, edit-history storage, local pins and privacy presentation as separate tested units; keep already implemented local translation, templates, filters and mention controls.
- [ ] Assess encrypted local session backup separately: document keychain/keystore and logout semantics before exposing any export or restore UI. Do not copy authorization data into ordinary settings backups.
- [ ] Keep per-feature switches and precise descriptions; client-only features must work without a fabricated server Premium status.

## 8. Verification and delivery

- [ ] Run focused executable regression tests for each phase and full ordered patch replay on pinned upstream sources.
- [ ] Compile real Android APK and full iOS IPA, inspect failed jobs and fix integration errors.
- [x] Verify APK version, signature and package; publish through the existing authorized branch/build workflow. Android 1000554 / run 38713620156 verified.
- [ ] Record exact artifact links and distinguish source checks, full compilation and device QA. Device testing remains pending while the user's phone is unavailable.
- [ ] Update `docs/USER-CHANGES.md`, fork audit and this checklist with actual completed work and remaining items.

## 9. NebulaLink runtime and dependency refresh

Files: `runtime/xray/go.mod`, `bind/go.mod`, `core/go.mod`, related sums/build scripts, Android and iOS dependency manifests.

- [x] Inventory exact runtime (Xray-core vs libXray wrapper), Go/toolchain/mobile binding, transport engines, native libraries and UI dependencies; identify latest stable releases from primary sources.
- [ ] Update compatible versions in isolated dependency groups and reconcile API changes; retain reproducible pins. Major/toolchain changes require successful target-platform builds, not blanket version replacement.
- [x] Run core/bind/runtime tests and race checks where supported; validate subscriptions, malformed configurations, protocol routing, DNS, cancellation and repeated start/stop.
- [ ] Build both mobile bindings and full applications; test VPN consent, import, connection, reconnect and server switching on hardware when available.
- [x] Record before/after versions and release notes; report any blocked or intentionally unadopted upgrade explicitly.


## Checkpoint: 15 October, commits 0d88126 / c987ee0 and following batch

- Formatting bars are implemented on both platforms, default off; 324 Android selection checks and SDK compilation pass. Native keyboard/device QA remains open.
- 111 Android regression commands passed locally. The first CI run failed in SDK-name parsing, repaired by c987ee0; full APK rebuild is in progress.
- 0d88126 iOS bootstrap and settings tests passed. Full IPA is running; this is not a full UI acceptance result.
- NebulaLink CI 38704456335 passed all three race-test jobs and all five mobile/desktop binding builds.
- Android library patch 0214 updates 16 compatible dependencies; complete version inventory is saved separately. Major toolchain/payment/push migrations remain open.
- iOS patch 0109 removes count gates from profile, search, logout and deactivation routes using the existing dynamic AccountManager records. Android still has fixed Java/JNI/native registries; its unlimited-account migration is not implemented.
- Welcome/auth work so far removes field outlines/floating labels, adds explicit clipboard import, preserves keyboard editing and caches illustration resources. The full visual redesign remains open.

- Onboarding regression tests now execute the actual import/list/connect methods with delayed callback delivery: skip, return, retry, duplicate submissions, destruction and editing. Artwork checks exercise attach, empty bounds, repeated layout, hide/resume, reduced motion and detach. Android SDK compilation of the artwork also passes. These are behavioral checks, not a rendered UI acceptance result.

- Android 1000554 successfully compiled; downloaded package, original signature, minSDK 23, arm64 ABI, ZIP alignment and embedded Go/Xray/mobile versions verified. iOS full native 38708491103 subsequently passed; downloaded IPA 120 verified (requires signing). The emulator failed to boot (offline); physical-device/rendered UI acceptance remains open.

## Checkpoint: verified Android 1000555 / iOS 120 and dynamic registries

- Android run 38716266186 passed and its APK signature, version, ABI, alignment and embedded core were verified. Full iOS run 38708491103 passed; IPA 120 integrity, bundle ID and version verified. Device UI/authentication/push testing remains open.
- Account registry migration is tracked in [its focused implementation plan](2026-10-15-dynamic-accounts.md). Patch 0216 removes fixed singleton arrays in 35 Java controllers and the native networking switch, with per-thread JNI environments. Java/native concurrent tests pass for 128 synthetic accounts. Persisted account allocation, screen-state arrays and notification namespaces still require migration before removing the Android ten-account UI gate.
