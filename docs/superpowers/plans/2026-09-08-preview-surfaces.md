# Splash and nested message menu implementation plan

> **For agentic workers:** Execute inline task-by-task; keep native changes in the next ordered Android patch.

**Goal:** Match the splash mark to the launcher, remove old list clipping from lifted media previews, and retain glass on the reactions/readers page.

**Architecture:** Keep the current live ChatMessageCell renderer and spoiler protections. Scope preview-only drawing state with try/finally, keep one popup material beneath both swipe-back pages, and replace the splash stroke silhouette with explicit rounded vector geometry.

**Tech Stack:** Android Java/VectorDrawable, Python regression harnesses, ordered git patches, Java 21 offline compilation.

### 1. Regression coverage
- [x] Create `scripts/check-preview-surfaces.py`, extracting real preview scope and swipe-page policy methods into Java test doubles.
- [x] Assert preview alpha and drawing flags restore on normal/exception paths; media/content clipping must not use recycler coordinates in preview.
- [x] Assert swipe pages use no second fill and fade old content only in glass mode; explicit background overrides/native mode retain their behavior.
- [x] Assert splash has explicit curved fill, no thick plane stroke, and safe bounds.
- [x] Run against current tree and observe failure before implementation.

### 2. Native preview and swipe-back fixes
- [x] Back up current `Cells/ChatMessageCell.java`, `Components/PopupSwipeBackLayout.java`, and `ChatActivity.java` to `build/preview-surfaces-0908/before`.
- [x] Extend `drawMenuPreview`: save `drawingMenuPreview`, `alphaInternal`, and `skipFrameUpdate`; set preview=true, internal alpha=1 (outer renderer already applies cell alpha), frame skipping=false; restore in finally.
- [x] Guard recycler-based alpha clipping with `!drawingMenuPreview`; do not alter photo visibility, spoilers, downloads, or account state.
- [x] In PopupSwipeBackLayout, skip foreground fill/old-page overlay for Nebula default material; use old-page alpha `1 - transitionProgress` so transparent new page cannot reveal old rows.
- [x] Bound Nebula preview placement below status-bar inset plus 8dp, without changing native menu placement.
- [x] Package native diff as `patches/android/0068-preview-surfaces-and-swipe-pages.patch`; apply after 0067 and compare reconstructed files.

### 2a. Album caption reproduction (confirmed by user)
- [x] Before opening a blurred album menu, reserve RecyclerView layout space for all album rows plus the caption cell, in both scroll directions.
- [x] Wait for layout and post menu creation outside the layout transaction. Keep the reservation until dismissal.
- [x] Release reservation/listener on dismissal, failed open, detached or rebound cell, paused fragment and fragment destruction.
- [x] Exercise deferred creation, native/document bypass, cancellation and cleanup using extracted production methods.

### 3. Splash vector
- [x] Modify `platform/android/overlay/TMessagesProj_AppStandalone/src/main/res/drawable/tg_splash_320.xml`: explicit rounded plane contour matching the launcher geometry; preserve transparent canvas and the two brand trails.
- [x] Remove artificial horizontal translation. Verify vector XML and inspect the locally rendered rounded mark.

### 4. Verification and delivery
- [x] Add regression command to `.github/workflows/android.yml`; run all workflow Python checks against the patched final tree.
- [x] Run `python build/menu-touch-0907/compile-resume.py`; distinguish Java compilation from APK assembly.
- [ ] Check ADB. If available, test photo/caption menus, reactions/readers/back, splash and close/reopen without message mutations. If unavailable, report runtime acceptance as pending.
- [ ] Run git diff checks; commit only task files. Leave design outputs and unrelated changes intact.

Final verification: 22/22 checks; Java compile successful (7m19s); javap confirms final cleanup/receiver changes; splash compiled with AAPT2 and visually inspected via SVG rendering. Device acceptance pending (no ADB devices). Git commit/push pending: index.lock exists and tool policy rejected its removal.
