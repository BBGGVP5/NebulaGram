# Message Tools Refinement Plan

**Goal:** Refine Android and iOS message tools from the latest device feedback, including gestures, composer/caption access, visible live translation and searchable settings.

**Architecture:** Keep native sheet navigation and native composer geometry. Share translation preferences between quick switches and their detailed settings; append search entries without changing existing history identifiers. Extend the media caption integrations only after confirming the requested entry points.

**Tech Stack:** Java/Android Telegram patches and overlays; Swift/UIKit Telegram patches and overlays; existing GitHub build workflows.

- [x] Replace filled tool tiles with compact icon-and-label actions on both platforms; reduce unused sheet space and provide swipe dismissal that respects scrolling.
- [x] Position and animate the composer shortcut with native trailing controls, including multiline expansion and native AI controls.
- [x] Add AI access to the confirmed media attachment/caption entry points, preserving the draft and attachment selection.
- [x] Show incoming and typing translation switches directly in tools, with language/settings access and provider readiness handling.
- [x] Inspect native translation-update animations and add a bounded themed transition where needed, respecting reduced motion.
- [x] Append searchable entries for new AI, translation, glass and profile controls; route results to the relevant settings on Android and iOS.
- [ ] Run relevant regression checks and complete fresh Android/iOS builds; verify both downloaded artifacts before delivery.

The earlier iOS build 37111219096 remains a checkpoint; it does not contain this new feedback.

Implementation: native swipe dismissal with a visible grabber and scroll-edge gating; transparent wrapping action grid; one trailing AI shortcut; caption integrations in ChatAttachAlert, PhotoViewer and AttachmentTextInputPanelNode; explicit incoming/draft switches; native Android translation loading signal and motion-aware preview transitions; appended Android/iOS settings search entries. Caption was the stated working assumption while clarification remained unanswered.

Local validation: 256 composer slot cases; 432 tool grid cases; live-translation cancellation/revision tests; 65 availability cases; 73 settings contracts; iOS bootstrap applies all 69 patches to 119 native paths; native preparation (6) and IPA validation (8) tests pass. Full platform compilation and physical-device acceptance still required.

Apple verification at bbc4373: bootstrap 37115514617 passed 88 tests, 69 patches / 121 native paths, native Swift parsing and SDK checks. The iOS activity registry now ends the native shimmer on cancellation/failure and isolates owners, accounts and namespaces. Full IPA 37115527107 is building. Android build 37115013108 verifies a65bc56; one final grabber clipping correction is pending in patch 0166.

## Follow-up: folder title timing and emoji clipping

User confirmed the title is an emoji-only folder name and the delay occurs during folder switching.

- [x] Update Android title at tab selection / committed swipe start, retaining cancellation and locked-folder guards.
- [x] Animate the active glass toolbar title using native text views, safely interrupt rapid transitions, and preserve full measured emoji height.
- [x] Audit iOS title transitions and invalidate rasterized title when available width changes.
- [ ] Run title/patch regression checks and build current Android and iOS artifacts.

Title regression verification: 2016 geometry cases, 100 rapid native transitions with canceled/late callbacks, and 20 folder commit/cancel transitions pass. Android checkpoint a65bc56 compiled successfully in run 37115013108; latest title changes still require a new build.
