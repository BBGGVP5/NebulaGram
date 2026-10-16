# NebulaGram client experience implementation plan

**Goal:** Deliver the requested native formatting toolbar, cohesive Liquid Glass folders, scalable accounts, clearer settings and a redesigned sign-in / NebulaLink experience on Android and iOS.

**Architecture:** Extend existing native editing and authorization controllers, preserving Telegram entities, authentication state and account isolation. Use shared preference contracts and the existing glass renderer. Account scaling requires changing storage and native registries; raising a constant is not an unlimited-account implementation.

**Tech Stack:** Java/Android Canvas, Swift/UIKit, Telegram native C++ networking, shared JSON settings contract, Python/Java/Swift regression checks and GitHub Actions.

Execution is inline in the existing workspace. Preserve the dirty Android vendor checkout and work against owned source trees. Each phase must be independently buildable. Check boxes describe verified work, not intentions.

Current checkpoint (16 October): the implementation phases below are present on both platforms. The provider startup crash is fixed in APK 1000569 and its successor 1000571 passes startup on Pixel 9 Pro XL / Android 17 without clearing data. Physical 1000571 inspection confirms the integrated About row, branded About title/version and selected iOS-folder preset; it also exposed an empty-draft IME accessory refresh bug. Source `98c860f` corrects that refresh, matches source-backed Swiftgram formatting actions/spacing and removes synchronous preference disk writes on send plus disabled-translation draft copies. All 130 Android commands, 227 ordered patches and 463 parsed native/overlay Java units pass. Full APK 1000572 is tracked in run 38852976850. iOS 125 (source `801baa9`, run 38784611001) is fully built and independently archive/hash/manifest verified; the later formatting update is ported, passes all 116 patches, real UIKit SDK checks and macOS bootstrap 38852976817, and is building as full IPA 126 in run 38852996115. iPhone/push, VoiceOver/TalkBack, authentication and broader rendered motion acceptance remain separate. See the focused bulk-read/accounts/folders plan for exact latest device/build receipts. Earlier checkpoints below are historical.

## 1. Research and acceptance criteria

- [x] Compare Swiftgram, Cherrygram, iMe, Ghostgram and TGExtra primary sources with `docs/FORK-FEATURES-2026-10-15.md`; record exact repositories and distinguish existing features, additions and unverified claims.
- [x] Define and implement formatting acceptance paths: separate default-off toggle, native entity actions, keyboard accessory layout, recording/non-text visibility and draft ownership. Device IME/undo acceptance is recorded below.
- [x] Define and implement folder paths: shared rounded material/selected capsule, compact counters, top/bottom placement, RTL, reduced motion, theme and fallback.

## 2. Formatting toolbar

Files: new Android `NebulaFormattingToolbar.java`; native `ChatActivityEnterView.java` patch; Android `NebulaSectionFragment.java`, `NebulaSettingsSearch.java`; shared `settings/catalog.json`; iOS SettingsUI and native `ChatTextInputPanelNode.swift` integration.

- [x] Implement Android toolbar using `EditTextCaption` formatting actions, not Markdown text rewriting.
- [x] Integrate measured keyboard space and lifecycle; preserve reply/edit previews and native recording controls. Source/selection checks pass; physical keyboard transitions remain below.
- [x] Register preference, settings/search entry and platform contract.
- [x] Implement equivalent native iOS selection actions and accessory layout.
- [x] Execute empty/collapsed/reversed selection, links, emoji UTF-16 ranges, theme and preference fixtures (324 Android selection cases); compile production UIKit toolbar against the real SDK. Native keyboard/IME acceptance remains below.

## 3. Folders and motion

Files: `NebulaFolderGlass.java`, `NebulaFolderTabs.java`, `NebulaTabLens.java`, native `FilterTabsView.java` / `DialogsActivity.java`, iOS folder components.

- [x] Inspect existing glass mode and fix the real visual gaps against the supplied reference; avoid adding a duplicate style.
- [x] Reuse shared blur capture and selected capsule; cache render resources and avoid idle animation.
- [x] Execute folder/motion geometry, interruption, reduce-motion and listener lifetime fixtures, including 1,000 recreation cycles. Rendered scroll/swipe acceptance remains below.

## 4. Accounts without an artificial client cap

Files: Android `UserConfig`, controller singleton holders, account iteration and notifications, native `ConnectionsManager` registry / JNI, iOS account management.

- [x] Inventory fixed arrays, bitmasks, native special slots, notification IDs and persisted account indices on both platforms.
- [x] Design persistent account IDs and a dynamic registry with lazy controller creation; preserve existing account directories and keys.
- [x] Migrate fixed arrays and native slots consistently, including the AppStandalone SMS manager; remove the UI gate after catalog/iteration migration.
- [x] Implement account search/order and explicit resource policy: native notification behavior retained, controller handles kept for process lifetime, retired IDs never reassigned.
- [x] Test 128/256 synthetic identities, restart/removal, failed allocation, concurrent lookup, notification routing and migration; no real credentials in fixtures. Full Android compilation passed; real login/push acceptance remains below.

## 5. Settings structure and performance

Files: Android `NebulaSettingsFragment`, `NebulaSettingsHubFragment`, `NebulaSettingsSearch`, `NebulaSectionFragment`; iOS `NebulaSettingsController`, `NebulaSettingsSearch`; shared settings contract.

- [x] Organize into Appearance, Chats and composer, AI and audio, Navigation and folders, Accounts, Privacy, Connection and About; retain legacy deep links while grouping the visible routes.
- [x] Add direct access to frequently used options, searchable labels and current-value summaries; preserve deep links and exported settings. Android's shared dispatcher executes 38 destinations.
- [x] Debounce search, cancel pending work on detach and avoid rebuilding unchanged results / stale theme caches.
- [x] Execute preference/route, migration, dependency, live refresh, search and cache fixtures. Full screen-reader/locale layout acceptance remains below.

## 6. Welcome, NebulaLink and authorization redesign

Files: Android `NebulaIntroFragment`, `NebulaIntroArt`, `NebulaOnboardingLayout`, `NebulaConnectFragment`, `NebulaLoginStyle`, `NebulaAuthArt`; iOS AuthorizationUI `NebulaWelcomeController`, matching native phone/code/password presentation adapters.

- [x] Implement the common rounded surfaces, typography and spacing; remove outlined/floating Material presentation in the styled flows while retaining native validation controls.
- [x] Recompose theme-aware product scenes in code; remove the 1.25 MB decorative bitmap.
- [x] Redesign NebulaLink import/connect with explicit optional skip, clipboard import, server state, errors/retry and a stable primary action.
- [x] Redesign phone/country, confirmation, code/resend and password/recovery presentation while preserving native validation, timers, autofill and authentication requests.
- [x] Implement measured safe areas, keyboard bounds, scalable artwork/type and reduce motion; retain the keyboard while editing. Production request/artwork checks and 488 confirmation cases pass; rendered acceptance remains below.
- [x] Port visual changes to native iOS layout/interactions; compile three production presentation components with xtool/SDK 27.2 and package the unsigned onboarding probe.

## 7. Additional fork features

- [x] Implement message/file details, explicit link cleanup, edit-history storage, local pins and chat-list privacy presentation as separate tested units; keep existing local translation/templates/filters/mention controls. Native builds for the final batch are tracked below.
- [x] Assess encrypted session backup separately in `docs/SESSION-BACKUP-ASSESSMENT-2026-10-15.md`: native key boundaries, reinstall/logout/revocation and device tests. No session-export UI or authorization data in ordinary settings backups.
- [x] Keep per-feature switches and precise descriptions, including privacy scope and history/pin limits; no fabricated server Premium status.

## 8. Verification and delivery

- [x] Run focused executable checks and all 126 Android regression commands; replay 222 Android / 114 iOS patches. The final startup regression also reproduces the original failure before applying the fix.
- [ ] Compile real Android APK and full iOS IPA, inspect failed jobs and fix integration errors. Android 1000570 and full IPA 123 passed compilation/archive checks; latest UI builds 1000571 / 125 are compiling.
- [x] Verify APK version, signature and package; publish through the authorized branch/build workflow. Final Android 1000569 / run 38768946384 verified: original certificate, API 23/arm64, 16-KB alignment, Go 1.27.2 and Xray 701af60772cd.
- [x] Record available artifact links and distinguish source checks, full compilation and device QA. Android 1000570 startup is tested on hardware; full IPA 123 compilation/archive checks passed. Latest UI builds and broader physical UI/authentication acceptance remain separate.
- [x] Update `docs/USER-CHANGES.md`, fork audit and this checklist with actual completed work and remaining items.

## 9. NebulaLink runtime and dependency refresh

Files: `runtime/xray/go.mod`, `bind/go.mod`, `core/go.mod`, related sums/build scripts, Android and iOS dependency manifests.

- [x] Inventory exact runtime (Xray-core vs libXray wrapper), Go/toolchain/mobile binding, transport engines, native libraries and UI dependencies; identify latest stable releases from primary sources.
- [x] Update compatible dependency groups and reconcile APIs with reproducible pins. Sixteen Android library updates and core/mobile bindings passed; incompatible major/payment/push/toolchain changes are explicitly listed in the dependency assessment rather than silently bumped.
- [x] Run core/bind/runtime tests and race checks where supported; validate subscriptions, malformed configurations, protocol routing, DNS, cancellation and repeated start/stop.
- [ ] Build both mobile bindings and full applications; test VPN consent, import, connection, reconnect and server switching on hardware when available.
- [x] Record before/after versions and release notes; report any blocked or intentionally unadopted upgrade explicitly.

## Remaining acceptance (requires the stated environment)

- [x] Final Android APK 1000569 / 38768946384: full compilation, download, independent receipt verification and five device launch checks complete.
- [x] Full iOS IPA for the privacy/link-protection source batch: build 123 / run 38758514021, source 7c36630, downloaded and independently verified. Requires user signing; physical iPhone/push delivery is untested. Subsequent UI changes are in build 125, which is still compiling.
- [ ] Android/iOS physical UI acceptance: native IME/undo/keyboard, recording, large text/RTL, interrupted folder/header motion and screen-reader output.
- [ ] Signed physical authentication/push, more than ten real account logins, native VPN consent/connect/reconnect/server switching. Synthetic account/native tests and five binding builds do not replace these checks.


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


## Account/settings execution checkpoint

### Android startup regression discovered on device

Pixel 9 Pro XL running 1000568 immediately crashes in `NotificationImageProvider.onCreate`: providers run before `Application.onCreate`, but the new account enumeration needs the application context. Fix native provider registration rather than publishing a temporary empty catalogue that could destroy legacy migration. Post registration to the main Looper, use `AccountDirectory.observe` with actual sparse IDs, and cancel/close subscriptions on the same Looper at shutdown.

- [x] Execute the unmodified provider against a null application context and reproduce the same NPE.
- [x] Execute the fixed production provider/catalogue: pre-application startup, legacy sparse migration, later account allocation/retirement, queued changes and cancellation before/after registration.
- [x] Replay all 222 patches and run all 126 Android regression commands with the new startup check registered in CI; parse 460 native/overlay Java units.
- [x] Build corrected APK 1000569, verify identity/signature, update the attached phone without clearing app data, and confirm three cold starts, background resume and warm start without a new Java/native DropBox crash. Receipt: `build/artifact-38768946384/device-startup/verification.json`; settings screenshot inspected. This does not close multi-account authentication/push or other physical feature acceptance.

Android persistent IDs and all account enumeration have been migrated in patches 0218-0221. Notification tags and PendingIntent destinations include full account/dialog identities. The independent catalogue, native instance and notification fixtures pass for 128/256 synthetic accounts; native parser checks the actual patched sources. Full APK rebuild and real authentication/push remain open.

Both settings roots now expose AI/audio separately from chat presentation, plus searchable account switching and ordering. Android copied links and search share a single destination dispatcher; 36 routes, invalid destinations, row focus and account ownership execute in regression tests. The iOS settings overlay and new controller pass Swift parsing and the full ordered bootstrap/source-registration check; native module typechecking and the complete IPA build remain separate checks. No session credentials are included in settings export.


## Welcome and authorization presentation checkpoint

The welcome pages now place measured copy before scalable product scenes. Android's scene palette follows the Telegram surface/text/accent and replaces the 1.25 MB bundled decorative bitmap with a drawn message note. Native phone/code/password headers use a consistent heading/step hierarchy; password has its own lock artwork and code artwork no longer creates a rotating dashed path effect on every frame. NebulaLink explicitly labels setup optional and retains clipboard/import/retry/native-state ownership.

iOS mirrors the editorial hierarchy, actual localized scene labels and password illustration. Its native phone/code/password nodes use Dynamic Type heading metrics and the theme's primary text color; authentication handlers remain unchanged. Onboarding shows an explicit Skip navigation action and removes the overview-card border.

Verification: Android SDK compilation of the real artwork; existing production request/animation and 488 phone-confirmation scenarios pass. All 110 iOS patches and native handler/source-registration checks pass. The three production UIKit presentation components also compiled and packaged under xtool with iOS SDK 27.2, in a separate unsigned component probe (not a full Telegram client). The component-probe receipt is `build/xtool-probe/onboarding-verification.json`. Full application builds and rendered device acceptance remain separate.

## Message tools and presentation lifetime checkpoint

Both message tool controllers now expose explicit plain-text link cleanup and loaded-message/file details. Java and Swift execute the same 149 cleanup fixtures, including signed/authentication URLs, malformed escaping, Unicode whitespace, pathological punctuation and idempotence. Metadata fixtures check exact 64-bit identity/size, Unicode filenames, missing fields and 2038 timestamps. No implicit paste rewrite, domain mirror or network lookup was introduced.

Android folder changes now notify every attached root and detach only that root's listener. Executable tests cover simultaneous roots, removal during notification and 1,000 recreation cycles. The formatting toolbar cache also invalidates for foreground-only theme changes while retaining its surface through 1,000 idle layouts. Native keyboard and rendered motion acceptance remain open.

All 111 iOS patches replay and registration checks pass. The actual Java parser accepts 453 touched/native overlay units. Full application dependency typechecking and builds are tracked separately; edit history, local pins and presentation privacy are still the following phase.

## Local edit history checkpoint

The account-owned edit-history implementation is now present on both platforms; see [its focused plan](2026-10-15-local-message-history.md). Android captures the prior TL row before default-message replacement. iOS captures text and exact Codable entities in the same Postbox transaction as `.EditMessage`, using collection 126 (outside the pinned upstream collection list). Both are opt-in and bounded to 32 previous versions / 1,000 recently edited messages. No network request, Premium status substitution or automatic restore/send is added. Native account database removal also removes history.

Production adapter tests execute opt-in, text-only and formatting edits, no-op/stale updates, protected/expiring/service exclusions, bounds, account isolation and per-message/all clearing. The actual Android schema/pruning statements execute in SQLite; iOS versions are encoded/decoded through the Codable item-cache fixture. Native bootstrap passes all 112 patches. Full APK/IPA dependency compilation and device acceptance remain open.

## Local chat pins checkpoint

The independent local order is now implemented on both platforms; see [the pin plan](2026-10-15-local-chat-pins.md). Native server pins remain first. Android comparators use cached ranks, leaving native pin flags and server folder lists unchanged. Cold restoration is a bounded read-only peer-dialog hydration; existing DB rows and already loaded dialogs are protected from stale hydration overwrites. iOS projects native+local IDs into list views, retains root archive/additional entries, restricts root pins to their actual group and removes local-only peers from native remote reorder payloads. Empty/disabled scopes use the original view directly; projection membership is deduplicated before rebuilding views.

Both settings UIs expose disable/clear and exact scope/limits; search now includes local pins and edit history. Executable tests cover persistent/sparse IDs, immutable snapshots, account/folder isolation, failure/limits, native-first ordering, actual Android comparators (including transitivity) and the actual iOS view adapter/native predicate with synchronous signal fixtures. These are not rendered scroll/drag/device tests. All 220 Android and 113 iOS patches replay. The preceding full Android regression batch passed 123 commands; new pin checks are additionally registered in CI.
