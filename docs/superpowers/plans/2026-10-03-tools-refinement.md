# Message Tools Refinement Plan

**Goal:** Refine Android and iOS message tools from the latest device feedback, including gestures, composer/caption access, visible live translation and searchable settings.

**Architecture:** Keep native sheet navigation and native composer geometry. Share translation preferences between quick switches and their detailed settings; append search entries without changing existing history identifiers. Keep media caption integrations in the existing native caption controls.

**Tech Stack:** Java/Android Telegram patches and overlays; Swift/UIKit Telegram patches and overlays; existing GitHub build workflows.

- [x] Replace filled tool tiles with compact icon-and-label actions on both platforms; reduce unused sheet space and provide swipe dismissal that respects scrolling.
- [x] Position and animate the composer shortcut with native trailing controls, including multiline expansion and native AI controls.
- [x] Add AI access to media attachment/caption entry points, preserving the draft and attachment selection.
- [x] Show incoming and typing translation switches directly in tools, with language/settings access and provider readiness handling.
- [x] Inspect native translation-update animations and add a bounded themed transition where needed, respecting reduced motion.
- [x] Append searchable entries for new AI, translation, glass and profile controls; route results to the relevant settings on Android and iOS.
- [ ] Run relevant regression checks and complete fresh Android/iOS builds; verify both downloaded artifacts before delivery.

The earlier iOS build 37111219096 remains a checkpoint; it does not contain this new feedback.

Implementation: native swipe dismissal with a visible grabber and scroll-edge gating; transparent wrapping action grid; one trailing AI shortcut; caption integrations in ChatAttachAlert, PhotoViewer and AttachmentTextInputPanelNode; explicit incoming/draft switches; native Android translation loading signal and motion-aware preview transitions; appended Android/iOS settings search entries. Caption was the stated working assumption while clarification remained unanswered.

Local validation: 256 composer slot cases; 432 tool grid cases; live-translation cancellation/revision tests; 65 availability cases; 73 settings contracts; iOS bootstrap applies all 69 patches to 119 native paths; native preparation (6) and IPA validation (8) tests pass. Full platform compilation and physical-device acceptance are tracked below.

Apple verification at bbc4373: bootstrap 37115514617 passed 88 tests, 69 patches / 121 native paths, native Swift parsing and SDK checks. The iOS activity registry now ends the native shimmer on cancellation/failure and isolates owners, accounts and namespaces. IPA 37115527107 was superseded by the title follow-up; Android checkpoint 37115013108 verified a65bc56. The final grabber clipping correction is included in 272cf2e and later.

## Follow-up: folder title timing and emoji clipping

User confirmed the title is an emoji-only folder name and the delay occurs during folder switching.

- [x] Update Android title at tab selection / committed swipe start, retaining cancellation and locked-folder guards.
- [x] Animate the active glass toolbar title using native text views, safely interrupt rapid transitions, and preserve full measured emoji height.
- [x] Audit iOS title transitions and invalidate rasterized title when available width changes.
- [ ] Run title/patch regression checks and build current Android and iOS artifacts.

Title regression verification: 2016 geometry cases, 100 rapid native transitions with canceled/late callbacks, and 20 folder commit/cancel transitions pass. The title comparator also distinguishes custom emoji document IDs/ranges with identical fallback text. A native transition test covers selecting another folder while the network status title is fading out.

## Build verification

- Android run 37117828589 succeeded at 2f0971d713c774df35fe3ad8f79ea6d2ae4483de. Downloaded artifact 11272985291, archive digest checked, APK signature and package/version verified, ZIP integrity and arm64-v8a Go/Telegram libraries checked.
- APK: `build/qa-apk-tools-2f0971d/NebulaGram-1.0.0-TG-12.10.5-b1000320-arm64-v8a.apk`.
- APK SHA-256: `4f11527d8f83feb07a3df00a121434e802037f042887b7db4ccc7772d291e193`.
- iOS bootstrap 37116667340 passed 88 tests and native patch/SDK checks at 272cf2ece06fb2d85e454f2f1064ae1db27512fe. Later commits only affect Android; iOS sources are identical. Full IPA run 37116680665 succeeded; build 65 downloaded and verified at `build/qa-ipa-tools-272cf2e/NebulaGram-unsigned.ipa` (SHA-256 `7879f2c7ae6ad6cca654df5db7c03bfeaac529bdb7d7af4c1fa4d23a214237f5`). This is the title checkpoint, superseded by the Nano/header follow-up below.
- No connected device was available for visual/gesture acceptance testing.

## Follow-up: header controls and Nano live translation

User clarified that individual header button plates (Edit/search/back/menu) should be removed while retaining the shared glass header. Nano feedback refers specifically to missing model update access.

- [x] Remove header control plates, correct home title offset, and remove nested sheet status-bar inset.
- [x] Queue independent Nano entry points, cancel obsolete inference, and expose safe actionable errors.
- [x] Separate AI translation panel text/settings from native Telegram translation and preserve provider-specific result provenance.
- [x] Expose model download/check and AICore update access, with loading feedback for typed text.
- [ ] Verify runtime regressions and full Android/iOS builds.

Follow-up validation: production Nano semaphore exercised with 12 competing callers and cancelled waiters; actual future cancellation and feature-status errors pass. Production incoming queue test covers Telegram cache takeover, foreign overwrite, model switch during inference, loading signals, failure state and manual retry. Title/composer/grid regressions pass; all Android patches apply and the iOS bootstrap validates 122 native paths. Device rendering and real AICore availability remain unverified locally.

Follow-up CI: iOS bootstrap 37121180639 passed all 88 contract tests, native parsing and SDK checks at c935913. Android runs 37121180636 and 37121498745 exposed obsolete test doubles/old capsule expectations; the protocol fixture and native material regression now match the requested behavior (12,928 material/touch-bound cases). Runtime sources are unchanged after c935913; commits ad8a7bf/de19528 only update tests. Full builds are Android 37121794346 (de19528) and iOS 37121194099 (c935913).

Android follow-up run 37121794346 succeeded. Verified artifact 11274376888, APK package `app.nebulagram.messenger`, version 1.0.0 / 1000328, arm64-v8a libraries, archive digest and existing signing certificate. File: `build/qa-apk-tools-de19528/NebulaGram-1.0.0-TG-12.10.5-b1000328-arm64-v8a.apk`; SHA-256 `79cde5076c4f22a1d33fe79d1016e9ae1521cfd18042f51f9d2e663594a5d7a1`. Shared iOS runtime sources match c935913; IPA run 37121194099 is still compiling.

### Connected-device correction (Pixel 9 Pro XL, Android 17)

The installed b1000328 reproduced a split path: a local AI chat generated a Russian answer, while incoming translation failed before inference. `NebulaAutoTranslate` requested `NebulaAiSecrets.read(4)`; the production credential store only accepts providers 0–3. Skip credentials for Nano as the working chat/draft paths already do. Tightened the incoming regression double to enforce the real credential-store boundary; it failed before this fix and passes afterward.

Model availability no longer clears unrelated translation failures or claims that inference is verified. The Android AI provider uses the shared sheet-aware header, and adjacent global settings cards have spacing. Both platforms preserve account/chat scope through tools → provider → translation and AI editor → provider navigation. APK and IPA build/device verification pending for this correction.

### Device-only cancellation crash

b1000329 was installed over b1000328 without clearing data. Requests reached Nano but cancellation exposed a second issue: Android DropBox captured `NoSuchMethodError` on the ML Kit executor. Inspection of the shipped DEX found the missing-method stub in the coroutine cancellation callback. The published Prompt beta4 bytecode invokes interface-static `kotlinx.coroutines.Job.cancel$default`; its POM incorrectly requests coroutines 1.7.3. Confirmed directly with javap and Google ML Kit issue https://github.com/googlesamples/mlkit/issues/1068.

Pin the coroutines 1.11.0 BOM and Android runtime, and run a Gradle check against the resolved release dependencies which creates a Job and invokes the exact cancellation entry point. The check succeeds on 1.11.0 and rejects 1.10.2. Temporarily switched AI off on the connected device while replacing the test APK; restore the original enabled state after installation. b1000329 is not a release candidate.

The release-runtime verification script now skips included/buildSrc builds and selects only external Kotlin JAR artifacts, avoiding AGP library variant ambiguity. Both cases were reproduced with a local Gradle fixture before the final build. Re-ran incoming, Nano state and live-translation regressions successfully. Patch 0168 initializes the native title font before folder emoji spans are created; previously the first span could retain Paint's small default metrics. Physical-device visual verification remains pending.

Android source 5266eed is building in run 37126829572; latest iOS source 7824aba is building in run 37124130901. The older iOS checkpoint c935913 / build 67 was downloaded and verified (SHA-256 `60658adb35304d20b6128d6e4e82efd5fc2d52421301d79dace22eb95bffd3f9`), but does not contain the subsequent chat-scope correction. USB disconnected before the corrected Android artifact was available; the device still has b1000329 with global AI temporarily disabled. Do not report replacement installation or restored AI until confirmed.

Android run 37126829572 succeeded at 5266eed. Verified b1000336 / artifact 11276055769: package/version, existing signing certificate, arm64 native libraries, ZIP integrity and artifact digest. APK `build/qa-apk-tools-5266eed/NebulaGram-1.0.0-TG-12.10.5-b1000336-arm64-v8a.apk`, SHA-256 `b247729bdce3d4d49d88c7dcf815983c8c02f66dc3ad91b424dc143ed4c18630`. CI confirmed the exact coroutine cancellation entry point executes successfully. Compared shipped DEX files against b1000329: both cancellation stubs from the crashing synthetic Runnable and all three other ML Kit Prompt missing-method stubs are absent in b1000336. Unrelated pre-existing Google vision/clearcut stubs remain unchanged. The corrected APK has not yet been installed because USB remains disconnected.

iOS run 37124130901 succeeded at 7824aba. Verified build 68 / artifact 11276046324: artifact digest, source manifest, arm64 application and notification extension, and IPA integrity. Latest IPA: `build/qa-ipa-tools-7824aba/NebulaGram-unsigned.ipa`, SHA-256 `b1a863adaa04c91ec7a9432648bc022f646608f388b004943d108ac4eca78841`. User signing is required; iPhone runtime and push delivery are not verified. Android-only commits after 7824aba do not alter these iOS sources. Physical Android acceptance and restoration of the temporarily disabled AI switch remain pending USB reconnection.

## Follow-up: readable sheets, selection glass and translation latency

- [x] Give Android sheet surfaces a separate dense, theme-derived tint and minimum blur (`NebulaMenuStyle`, `NebulaSheetSurface`); retain opaque fallback and accessibility settings.
- [x] Extend the existing ActionBar material across all selection controls, interpolating the shared bounds during transitions; keep normal header buttons free of individual plates. Add a native patch after 0168 and validate selection geometry.
- [x] Reuse the serialized Nano model between adjacent requests, validate status once per retained session, discard on cancellation/error/configuration change, and close after 30 seconds idle. Test reuse, failure cleanup, configuration changes and idle/active races with production methods and fake service futures.
- [x] Brand incoming/draft progress and details as Nebula AI on Android/iOS while retaining actual provider details in settings. Reuse an ephemeral URLSession on iOS for connection pooling, without sharing model conversation state or enabling cookies/cache.
- [ ] Run targeted runtime/native checks and full builds. Check Android on USB only if it reconnects; do not claim measured latency or visual acceptance without device evidence. iOS already uses opaque deletion sections and native glass selection controls; preserve those native behaviors.

Follow-up local validation: production Nano generation/session methods reuse one model and one status check across adjacent prompts; model changes, failure/cancellation cleanup, stale idle callbacks and active/idle races pass. Native selection bounds cover both controls at the final animation frame (404 transition cases), with 12,928 existing material/touch-bound cases passing. Menu colors, section slot/fallback lifecycle, incoming/draft cancellation and gate serialization checks pass. Android run 37133693710 targets 963f67f; iOS bootstrap 37133726615 and IPA 37133727460 target 2738cff (only patch context differs). Device remains absent from ADB.

Android run 37133693710 stopped on an obsolete Saved Messages test expectation that excluded selection buttons from the material bounds. Updated the test to assert interpolation across all controls while retaining the original search bounds; 404 production draw-branch cases pass. Replacement run 37133999273 succeeded at 59e8f56. Verified APK b1000339 / artifact 11277887675, existing certificate, package/version, ZIP digest/integrity and arm64 native libraries. Path: `build/qa-apk-tools-59e8f56/NebulaGram-1.0.0-TG-12.10.5-b1000339-arm64-v8a.apk`; SHA-256 `1b55753b4772c057ef4a60192b92ee8209f389e6cb64beddce9cd9b8467cf082`. Production session reuse tests and the exact Nano coroutine cancellation call passed in CI. No physical-device timing or visual acceptance is claimed: USB remains disconnected.

iOS run 37133727460 succeeded at 2738cff. Verified build 69 / artifact 11279331532, source manifest, archive digest/integrity, arm64 application and notification extension. IPA: `build/qa-ipa-tools-2738cff/NebulaGram-unsigned.ipa`; SHA-256 `b36a4a8492cbdbb8b6a1cd9c0590a39afc4212d90287b5c451e9eed0eb301908`. Requires user signing; iPhone runtime and push delivery remain unverified.

## Follow-up: consistent community header

- [x] Connect the community DialogsActivity to the existing shared header material; preserve back/avatar/search/menu, title clearance and search/selection transitions. Remove the independent NebulaLink shield plate.
- [x] Apply the same header to the separate CommunitySheet pages, sampling only their list content with bounded capture and an opaque fallback.
- [x] Audit iOS CommunityViewScreen: it already uses the common ChatListNavigationBar/ChatListHeaderComponent in both sheet and fullscreen modes. Preserve that native implementation.
- [x] Verify native patch application and relevant geometry/material checks; build and validate a fresh Android artifact. Perform physical-device checks only if USB reconnects.

Local verification: all 165 Android patches apply to 122 paths from the repository's actual upstream pin `dc780e81ed1261c369c27870e8e0999a1eb0b600`. All three edited native sources match the reconstructed build tree. Production community header capture passes 648 API/position/power/software/fallback cases. Existing title, selection, Saved Messages, composer, editor, style, home controls and community-card regressions pass. iOS implementation already uses the shared native navigation component; this Android correction does not change iOS sources.

Android run 37141729976 succeeded at `dfef4ea1961b8c2c4ac493df04c8d4a072db3200`; Settings contract 37141729929 also passed. CI passed the community capture checks and complete interface suite, executed the exact Nano cancellation call, and compiled/signed the native application. Downloaded artifact 11281380952: ZIP/digest and APK integrity, package/version, arm64 libraries and existing signing certificate verified. APK: `build/qa-apk-tools-dfef4ea/NebulaGram-1.0.0-TG-12.10.5-b1000342-arm64-v8a.apk`; SHA-256 `4032bc23ac6841df3e2ae899a731174557af79f01c608be5385fb11a83875ad8`. Latest iOS remains verified build 69 above: iOS sources, patches, shared settings, build workflow and upstream pin have no diff from 2738cff to dfef4ea. ADB and Windows PnP do not show the phone, so installation and physical rendering/gesture acceptance are unverified.

## Follow-up: visible translation, sent messages and community spacing

Goal: reduce avoidable live-translation latency, independently enable translation of sent messages, and separate the first community row from its glass header. User clarified that the header, glass and spacing need correction, specifically the first row touching the header.

Architecture: retain only current visible message jobs; bound remote translation concurrency to two while keeping device inference serialized. Sent-message consent stays per account/chat, off by default, and uses the chat display language. Draft translation remains a separate opt-in with explicit Apply. Preserve protected-message exclusions and immutable original text. Use current Telegram native glass geometry and theme, and move the composer shortcut four points toward the trailing controls without reducing its hit area.

Tech stack: Android Java/native patch 0171, iOS Swift/native patch 0071, shared Swift preferences, production queue and geometry regression harnesses.

- [x] Update `NebulaAutoTranslate`, `NebulaTranslationSettings`, `NebulaTranslationFragment` and tools/settings search; test outgoing-only consent, revocation, cancellation and bounded concurrency.
- [x] Update Swift translation preferences/live controller/tools and native chat timer/display routing, preserving independent directions and original text; verify shared contract and native patch preparation.
- [x] Reduce the default draft pause to 300 ms while retaining user-configured delays; cache completed draft results scoped by language/provider/chat.
- [x] Fix CommunitySheet spacer/material corners and trailing composer spacing; extend native geometry tests and check all patch applications.
- [ ] Build Android, queue iOS from the new exact revision, and verify available artifacts. Check hardware only when actually connected.

Local validation: all 166 Android patches and 71 iOS patches apply to their actual pinned upstream sources. Production Android queue checks pass for outgoing-only consent, immutable text, remote concurrency, visible cancellation, edits and revocation. Community material/corner capture passes 648 cases, with first-row clearance on all three pages; composer passes 256 states using the actual two-dp gap. Shared Swift tests now cover sent consent and 300-ms default; a production Swift queue/transport harness is wired to macOS CI. No phone is present in ADB; device timing and rendering remain unverified.

## Follow-up: Android popup motion and upstream refresh

User supplied a video of a compact glass surface expanding from its menu button, requested the correct corner from the first frame, and clarified that selection actions need their own surfaces separate from the title.

- [x] Update the common Android popup reveal: resolve the actual anchor before the first visible frame, morph from button dimensions, add bounded spring settling and reverse dismissal, and retain affine touch mapping and reduced-motion fallback.
- [x] Separate the selection count and action controls in the shared header material; preserve native hit areas and selection/search transitions.
- [x] Capture the community sheet's parent-window backdrop without sampling its own window, and test source alignment, exclusions and fallback.
- [x] Advance the Android Telegram base from 12.10.5 to official 12.10.6, adapt native patch context, and verify the ordered series with consistent text line endings. iOS remains at the latest official 12.9.2.
- [ ] Run the affected production geometry/motion/lifecycle checks, publish both source trees, build Android and queue a fresh iOS build with the translation fixes above.

Keep menu effects bounded to the popup, use Telegram theme colors, and release animation effects/listeners on cancellation and detach. No new full-screen per-frame bitmap capture.

Local verification: 167 ordered patches apply to official Android 12.10.6; 100 interrupted anchor/corner cycles and spring/focus cleanup pass; 16,968 independent selection action cases and 404 counter/search transitions pass; 669 community source/alignment/API/fallback cases pass. Existing menu palette, affine drag, native anchor wiring, composer and title regressions pass. Fixed a nondeterministic translation fixture by waiting for transport entry before testing cancellation; runtime behavior is unchanged by that fixture correction. Apple bootstrap and production queue harness already passed at `192d9c3` before this Android-only follow-up.
