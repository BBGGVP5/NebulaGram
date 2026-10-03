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
- iOS bootstrap 37116667340 passed 88 tests and native patch/SDK checks at 272cf2ece06fb2d85e454f2f1064ae1db27512fe. Later commits only affect Android; iOS sources are identical. Full IPA run 37116680665 remains in progress.
- No connected device was available for visual/gesture acceptance testing.

## Follow-up: header controls and Nano live translation

User clarified that individual header button plates (Edit/search/back/menu) should be removed while retaining the shared glass header. Nano feedback refers specifically to missing model update access.

- [x] Remove header control plates, correct home title offset, and remove nested sheet status-bar inset.
- [x] Queue independent Nano entry points, cancel obsolete inference, and expose safe actionable errors.
- [x] Separate AI translation panel text/settings from native Telegram translation and preserve provider-specific result provenance.
- [x] Expose model download/check and AICore update access, with loading feedback for typed text.
- [ ] Verify runtime regressions and full Android/iOS builds.

Follow-up validation: production Nano semaphore exercised with 12 competing callers and cancelled waiters; actual future cancellation and feature-status errors pass. Production incoming queue test covers Telegram cache takeover, foreign overwrite, model switch during inference, loading signals, failure state and manual retry. Title/composer/grid regressions pass; all Android patches apply and the iOS bootstrap validates 122 native paths. Device rendering and real AICore availability remain unverified locally.
