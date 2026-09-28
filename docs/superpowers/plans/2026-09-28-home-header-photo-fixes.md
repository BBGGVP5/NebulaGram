# Home Header Photo Fixes Implementation Plan

**Goal:** Correct the Android header and popup defects visible in the three supplied screenshots; default the home title to NebulaGram with an optional Chats label.
**Architecture:** Keep the existing native ActionBar and blur source. Position its leading control during native layout using the current status-bar policy. Make menu dividers contribute zero intrinsic width, allowing action rows to determine popup placement. Keep the label preference local to Android appearance, like the existing home glass preference.
**Tech Stack:** Java overlays, ordered Android patches, Python/Java regression checks, Gradle CI.

## Tasks

- [x] Reproduce the popup-width defect with the production divider: under AT_MOST it must contribute zero width; under EXACTLY it must fill the width set by the menu's second pass.
- [x] Add `ActionBar.setNebulaHomeLeadingView(View)` and lay out that child at `additionalTop + (getCurrentActionBarHeight() - child.getMeasuredHeight()) / 2` on every native layout. Exclude it from the generic child layout loop. Remove the creation-time status-bar margin.
- [x] Remove the shield's nested circle only when its owning ActionBar supplies the unified home capsule; preserve status tint, checkmark, loading/error states and standalone settings previews.
- [x] Replace the remaining home-menu `io.addGap()` calls with the shared content divider and remove the fixed `-64 dp` menu translation so the popup appears below its button.
- [x] Add `home_chats_title` default false, expose a Russian/English appearance toggle, and select the home label independently of glass. Preserve folder labels and update on returning to Home.
- [x] Run title, search/layout, shortcut, selection and divider checks against the full patched source. Apply the ordered patch series from the pinned upstream without modifying the dirty vendor checkout.
- [ ] Build the Android APK in CI. Attempt native visual verification on an available emulator; report the actual verification limits and do not equate successful compilation with visual acceptance.

## Regression expectations

The leading control stays inside the toolbar after status-bar occupation changes, at portrait/landscape toolbar heights and across densities. Search/selection continue to hide the leading button. Adding a separator cannot change a popup's natural width or right anchor. The default and opted-in title are independent of glass, and selecting a named folder retains that name.
