# Camera Reference and Recording Islands Implementation Plan

> **For agentic workers:** Execute inline in the current authorized checkout, preserving the pinned vendor and unrelated changes.

**Goal:** Adapt the supplied Cherrygram camera examples: separate front/rear presets, compact material with an expanding ruler, smooth camera updates, and a separate recording/delete island.

**Architecture:** Keep the existing recorder and capability discovery. Refine the native Camera2 request lifecycle and generate the next ordered patch. Draw the slider with native theme blur material; reuse distinct existing compositor surfaces for recording controls.

**Tech Stack:** Android Java/Canvas, Camera2 handler, native blurred background drawables, Python/Java regression fixtures.

## Evidence and boundaries

- User video: `https://t.me/CherrygramBetaAPKs/2678`; four reference photos: `https://dropmefiles.com/Jpl9Z`. Read in the browser, no photo/video download required.
- Official Cherrygram `main` commit `cec3075847d933e13014954ed8b776b767760ce8` and `main_Reproducible_Builds` commit `e6eae4330be873723818c7209bb6c97e6b75311f` examined in ignored `build/cherrygram-reference-20261001`. Both publish the older `SlideControlView`; reproducible `CameraXController.setZoom` and most camera methods are empty. The new capsule implementation is not present in these examined branches; do not claim a verbatim port of it.
- Photos show rear shortcuts (including 2×, high zoom and the endpoint), front 1×/2× shortcuts with the active fractional/high zoom replacing the selected label, no separate ± buttons, and an expanded ruler of the same capsule height. Hardware ranges must remain camera-specific.
- Existing Camera2 `setZoom` rebuilds capture requests on the caller and submits twice. Coalesce and reuse the configured builder on its camera handler instead of submitting from the UI thread.

## Tasks

- [x] Commit the already exported scratch native patch 0156 as a local baseline; never write/reset `vendor/telegram-android`.
- [x] Add camera-facing mode and a bounded rear preset policy in `NebulaZoomSlider.java`; remove ± controls, keep selected label readable, and keep the indicator fixed while the ruler moves. Use the recorder's real blurred-background factory/material.
- [x] Add queued Camera2 zoom updates on the camera handler, sanitize values, submit once, reuse the builder and discard queued updates after close. Execute actual patched methods against a fake handler/session with rapid-input and stopped-session scenarios.
- [x] Wire recording/preview controls to `NebulaComposerStyle.java`; draw red-dot/delete material on a separate round surface and leave a gap before the main timer/cancel/preview capsule. Adjust native timer/preview insets without changing native recording gestures or click handlers.
- [ ] Export native changes as patch 0157. Update camera and composer fixtures for the new geometry and front/rear behavior; validate the complete ordered patch series and compile/build the actual Android application.
- [ ] Update user changelog and verification records; publish exact task paths and download/verify the replacement APK. Track physical-device recording/animation checks separately.

The previous support/icon build 1000256 is verified but the user has rejected its zoom behavior. Do not present it as acceptance of this revised request. The icon/support iOS native compile is still running in 36809607587 and can finish independently of Android camera work.

## Additional user steering

- Consolidate the project channel/release/help/source links into one Community destination on Android and iOS, using the existing approved addresses. Examples: https://dropmefiles.com/70eRD.
- Remove Cherrygram naming from build labels, verification output and active validation directory defaults. Keep third-party attribution and source-comparison evidence.
- Local Android API 36 compilation of the slider passed. Actual Camera2 methods passed a 1000-event coalescing fixture, camera-thread-only IPC, builder reuse, reset and close guards. Actual compositor fixtures passed 72 layouts, including recording/preview separation. All 152 ordered Android patches apply to the pin.
- The previous iOS native integration run 36809607587 succeeded for runtime 417a701 (59 patches); it does not validate the new Community changes. A new native run is required.
