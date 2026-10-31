# Home stories first layout Implementation Plan

> Execute inline; preserve existing Android header fix and unrelated iOS edits.

**Goal:** Synchronize the cached story drawing with the final header geometry after loading/layout.
**Architecture:** Reuse the existing ActionBar title-layout listener, already bound to DialogStoriesCell.invalidate. Notify it after native title positions are laid out, just as title animation ticks do. Do not force per-frame measure/layout or hide stories to mask the defect.
**Tech Stack:** Android Java ordered patches, Python/Java native-fragment regression.

- [x] Extend `scripts/check-home-title-motion.py` to execute the actual post-title layout hook, simulate first measurement and later title/story-count changes, and assert that the story display list receives the final coordinates without waiting for animation.
- [x] Confirm failure on current Telegram 13.1.1 prepared sources.
- [x] Add `patches/android/0250-home-stories-layout-invalidation.patch`: replace `applyNebulaHomeStoryLayout()` with `updateNebulaHomeTitleLayout()` at the end of title positioning in ActionBar.onLayout.
- [x] Document in `patches/android/HOOKS.md`; run native title-motion, shared geometry, home-header motion checks and ordered patch application. Record limits of verification.

## Continuing scope

User also requested additions from Opexgram beta 10. Feature audit started; priorities are pending. Existing evidence includes edge compression, app font selection, local speech and menu/header implementations. No claim of full parity; resume the feature comparison after the reported loading glitch.

## Verification

- Old prepared sources fail the added regression: `Loaded header keeps stale story display list until next animation`.
- Fresh targeted native tree: `build/home-stories-layout-20261031`, reconstructed from pinned Telegram `3de37c8cfb039452be7398909461da6288babf15` with current ordered patches.
- `python scripts/check-home-title-motion.py build/home-stories-layout-20261031`: 36 first/load/restore cases and 9680 fade/interruption frames passed.
- `python scripts/check-reference-home-header.py build/home-stories-layout-20261031`: 117120 geometry cases, 61 collapse frames, 40 action states passed.
- `python scripts/check-home-header-motion.py build/home-stories-layout-20261031`: 240 native reversal cases, 716320 ring trajectory poses, 84 moving-layer and 101 Edit draw cases passed.
- `python scripts/check-native-java-syntax.py build/home-stories-layout-20261031`: 289 native/overlay units parsed; not a full dependency typecheck.
- `python scripts/check-upstream-series.py android --tree vendor/telegram-android --ref 3de37c8cfb039452be7398909461da6288babf15`: all 245 patches apply.
- `git diff --check` passed. No new APK build or phone visual verification for patch 0250.
- Remaining device acceptance: cold load, stories arriving after title, loading/connecting title changes, return from search, folder switch and glass-disabled layout.
- Opexgram comparison retained in `docs/OPEXGRAM-BETA10-AUDIT-2026-10-31.md`; pending user priorities for additions.
