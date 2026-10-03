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
