# Sheet First Frame Implementation Plan

> Execute inline; the named superpowers execution skills are not installed in this workspace.

**Goal:** Attachment cards adopt the ready glass source without requiring a touch or scroll.

**Architecture:** Register section views even when the current draw uses fallback. On pre-draw, invalidate registered views only when source readiness or their source-relative position changes; retain the per-frame material pool and native fallback. Do not add a perpetual animation-frame loop or change theme opacity.

**Tech Stack:** Android Java overlay, ordered Telegram patch, Python/Java extracted-method checks.

## 1. Reproduce and implement

Files: `scripts/check-input-controls.py`, `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaSheetSurface.java`, native `Components/ChatAttachAlert.java` persisted as `patches/android/0071-sheet-first-frame.patch`.

- [x] Add an invalidation counter to the View test double. A fallback draw followed by `onPreDraw()` must cause `check(list.invalidations == 1)` without a gesture. Run `python scripts/check-input-controls.py build/final-verify-0904/tree`: it fails on the current implementation.
- [x] Add `trackSection(View)` before the native readiness guard and before the overlay fallback return. Keep weak keys and clear them on host detach.
- [x] Extend each section bucket with `boolean ready; int x, y;`. After source capture (or unavailable-source exit), synchronize those values and invalidate only on readiness/coordinate changes. Invalidate the host once on readiness transitions. Retain same-window/reentrant guards.
- [x] Extend the production-method test to cover: fallback-to-ready without input, unchanged frames with zero extra invalidations, relative position changes, source loss/recovery, no same-window capture, late registered lists, detach and existing material-slot reuse. Inject the actual new methods and bucket definition into the Java harness.

## 2. Verify and document

- [x] Create patch 0071 from the saved native baseline in `build/sheet-first-frame-0909/ChatAttachAlert.before.java`. Apply all touching ordered patches to vendor HEAD and compare the result to the native source.
- [x] Run all 25 workflow regression commands, then `python build/menu-touch-0907/compile-resume.py`. Require `BUILD SUCCESSFUL`; verify compiled readiness/registration calls with javap.
- [x] Check `adb devices`. If absent, explicitly leave real first-open rendering acceptance pending. Document evidence in `docs/ANDROID-MENU-RENDERING.md`; do not imply device validation or publish a build without a user request.

- [ ] Runtime acceptance on Android remains pending: no ADB device attached.

Final Java compilation passed in 2m45s; javap confirmed native registration and conditional section invalidation. No APK sent or installed for this change.
