# Device menu rendering diagnosis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reproduce and fix the black-text popup on the connected device, not only in JVM fakes.

**Architecture:** Inspect the exact release APK, add temporary local-only color diagnostics, preserve its signing identity and app data, then implement only a device-confirmed fix in source and regression tests. Never clear data, uninstall the app, send messages or alter tunnel state.

**Tech Stack:** ADB, release DEX/smali inspection, Android Java, existing Gradle and patch series.

- [x] Confirm installed build/version and reproduce home overflow on Pixel 9 Pro XL / Android 17. Ordinary inversion and high contrast are off; release code includes previous fixes.
- [x] Compare release and local signing certificate before any in-place install. Instrument only numeric menu tint/foreground information, with bounded local logging; no message content.
- [x] Identify actual failing values/call paths. Pixel's `accessibility_force_invert_color_enabled=1` recolors white foreground to black. A temporary per-package exclusion immediately restores white text/icons. The user approved retaining the per-app exclusion; other exclusions were preserved. Ordinary inversion/contrast settings did not identify this newer mode.
- [x] Document the confirmed system-inversion cause, retain the user-approved exclusion, and keep diagnostics out of production code. Do not counter-invert application colors.
- [x] Verify source compilation and tests, install a clean release-equivalent patch for visual acceptance, and compare before/after screenshots.
- [x] Package native changes as 0066 and report verification limits in docs/ANDROID-MENU-RENDERING.md.
- [ ] Commit/push scoped application edits after verification.

Execution inline; the referenced superpowers execution skill is unavailable. Temporary inspection dependencies and diagnostic APKs live in ignored build/menu-runtime-0908. No physical-device result may be inferred from the JVM tests.

## Added requirement: drag the menu content with its surface

- [x] Add `scripts/check-menu-drag.py`: verify positive/negative pulls, zero extent, stable padding, forward/inverse pointer mapping and release identity.
- [x] Extend `NebulaMenuMotion.java` with pure stretch scale/offset helpers; apply the same geometry in `NebulaMenuReveal.clip()` after clipping, and invert the transform for dispatched pointer events.
- [x] Patch `ActionBarPopupWindow.java` to dispatch a transformed event copy and always recycle it. Package as 0066, verify the full patch series, then run the new regression and existing menu tests.
- [x] Compile source, replace diagnostic APK with a clean build, verify menu readability (according to the user's exclusion choice) and content movement on the connected phone. Restore the temporary diagnostic log property.

## Added requirement: smooth folder swipe and translucent popup source

- [x] Add a regression for frame-paced folder animation at 60/90/120 Hz, stalled frames and release continuity.
- [x] Patch `FilterTabsView.java`: schedule once per vsync, update elapsed time, preserve drag-center offset and start release from the dragged position, not the previous tab.
- [x] Remove per-draw velocity stretching in `NebulaTabLens.java`; preserve the shared press/outline geometry.
- [x] Align both `ScrimOptions.makeGlobalBlurBitmaps` overloads: Nebula's own material owns tint, so don't pre-darken its blur source.
- [x] Verify on the phone, restore clean diagnostic-free classes, compile and package the full native patch series.

## Added requirement: Telegram wallpaper in glass settings preview

- [x] Reuse `NebulaWallpaperPreview`'s active-theme wallpaper and center-crop logic in `NebulaGlassSettings.Preview`; preserve the shared drawable's bounds.
- [x] Replace the decorative gradient/light spot with the actual wallpaper, blur only the glass region, cache the small private blur bitmap and release it on detach.
- [x] Test source wiring and preview lifecycle, compile and check the preview on the device without changing the user's settings.

## Final acceptance

See docs/ANDROID-MENU-RENDERING.md for the actual test APK provenance, verified paths and unverified edge cases. The wallpaper toggle was restored off; no account, tunnel or message action was performed. Local Java compilation, 20 regression checks and 61-patch reconstruction passed.
