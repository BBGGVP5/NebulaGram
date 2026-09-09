# Input surface controls implementation plan

**Goal:** Independent home FAB visibility, consistent attachment card material, stable emoji selection and isolated title/avatar touch feedback.
**Architecture:** Preserve native actions, permissions, list sections and ViewPager state. Add preferences in the existing schema/UI. Keep Android changes in ordered patch 0070; preserve patch 0069 and unrelated designs.
**Tech Stack:** Java Android overlays, native Telegram Java, XML strings, Python/Java regression harnesses.

## 1. Home buttons
Files: `NebulaAppearance.java`, `NebulaSettingsSchema.java`, `NebulaSectionFragment.java`, overlay values/values-ru strings and native `DialogsActivity.java`.
- [x] Add `hide_home_camera` and `hide_home_compose` boolean preferences, default false, to schema and settings.
- [x] Apply flags only to ordinary home FAB visibility; selection-mode Done remains available. Move camera down when compose is hidden, preserve native scrolling/search visibility.
- [x] Test all hide/selection combinations and settings schema wiring.

## 2. Emoji selection and background
Files: native `PagerSlidingTabStrip.java`, `EmojiView.java`; retain existing `NebulaTabGesture.java` behavior.
- [x] Use `pager.getCurrentItem()` for settled selection, reset offset on page selection and idle. Test stale callback state; rerun existing release/restriction/cancellation tests.
- [x] Clarified by user: remove the extra colored/black backing strip underneath the bottom controls, not the whole sticker body. Hide that separate view, permit content underneath the glass controls and match the capture base to the emoji panel theme. Retain the wallpaper hook from 0069.

## 3. Attachment section cards
Files: native `RecyclerListView.java`, `ChatAttachAlert.java`; overlay `NebulaSheetSurface.java`.
- [x] Add a scoped section-background override, retaining existing section grouping and fallback.
- [x] Bind only attachment lists to per-card drawables sharing the original-window source; use a distinct drawable for each draw slot and view coordinate mapping. Never capture the sheet recursively.
- [x] Retain native opaque fallback for disabled blur, unsupported API, custom web content and missing source. Test section routing and fallback guards.

## 4. Header touch separation
File: native `ChatAvatarContainer.java`.
- [x] Inspect existing separate title/avatar geometry and retain native avatar story/photo handlers. Shared parent bounce was pressing the avatar visually when the title was touched.
- [x] Apply title bounce to text only, not avatar. Cancel must not open profile; test gesture cancellation and one click per release.

## 5. Verification
- [x] Save native baselines in `build/input-controls-0909/before`; create patch 0070.
- [x] Add focused executable regression check to android.yml: all 25 workflow commands passed, including section pool/frame reuse tests.
- [x] Reconstruct changed files from vendor and 29 touching ordered patches: all 6 native files match.
- [x] Java compilation passed (4m); bytecode checked, including Java 8 synthetic pager setters. No source changes during compilation.
- [x] Check ADB availability: no devices attached. No APK installed or published for this change.
- [ ] Runtime acceptance on Android: hide flags after returning from settings, emoji scrub release/cancel, bottom strip, attachment cards and independent header/avatar presses in light/dark modes.
