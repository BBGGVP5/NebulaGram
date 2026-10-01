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
- [x] Export native changes as patches 0157/0158. Update camera and composer fixtures for the new geometry and front/rear behavior; validate the complete ordered patch series and build the final Android application.
- [x] Update user changelog and verification records; publish exact task paths and download/verify the replacement APK. Track physical-device recording/animation checks separately.

The previous support/icon build 1000256 is verified but the user has rejected its zoom behavior. Do not present it as acceptance of this revised request.

## Additional user steering

- Consolidate the project channel/release/help/source links into one Community destination on Android and iOS, using the existing approved addresses. Examples: https://dropmefiles.com/70eRD.
- Remove Cherrygram naming from build labels, verification output and active validation directory defaults. Keep third-party attribution and source-comparison evidence.
- Local Android API 36 compilation of the slider passed. Actual Camera2 methods passed a 1000-event coalescing fixture, camera-thread-only IPC, builder reuse, reset and close guards. Actual compositor fixtures passed 72 layouts, including recording/preview separation. All 153 ordered Android patches apply to the pin.
- Native iOS integration run 36814095192 succeeded for runtime bb4b340, including the new Community changes (59 patches). It compiled SettingsUI, PeerInfoScreen and folder modules with Xcode 26.2 against the iOS 12.9.2 pin. This is compile-only evidence, not a signed IPA or device acceptance.

## Device acceptance checklist

- [ ] Front: only supported 1×/2× quick buttons; dragging can still reach the front camera’s actual maximum.
- [ ] Rear: recording-compatible optical stops, direct 2× and actual maximum; no separate step buttons; no clipped labels at minimum/maximum.
- [ ] Hold/drag expands the same-height capsule, ruler moves beneath the fixed indicator, pinch survives release/collapse.
- [ ] Rapid preset taps and facing changes during animation open the last requested module and discard the old facing animation.
- [ ] Audio and round-video recording: red dot, deletion animation and preview trash have an independent round surface, with a visible gap before timer/cancel/preview. Native delete/slide/lock/send actions remain functional.
- [ ] Community routes and back/close behavior work on both platforms, using the current Telegram theme.

CI: final Android 36816035280 (a9dc07d) passed, settings contract 36816035240 passed, iOS bootstrap 36814081247 passed, native iOS 36814095192 passed. Native iOS inputs are bb4b340; subsequent commits through a9dc07d only change Android/runtime checks and have identical iOS runtime inputs. All 202 published iOS patch/overlay input hashes match the native build evidence.

Downloaded final Android artifact: `build/qa-apk-camera-a9dc07d/NebulaGram-1.0.0-TG-12.10.5-b1000264-arm64-v8a.apk` (52,488,666 bytes). AAPT confirms `app.nebulagram.messenger`, version code `1000264`, version `1.0.0`, target/compile SDK 36 and arm64-v8a. Telegram and NebulaLink native libraries are present. APK signatures v1/v2 verify, one signer; the certificate matches verified build 1000256. SHA-256: `807D0825A64EB4F4833684BF06C7563BE7C0B445A04BA432C793D47DE8EE5E64`. This permits an in-place update; physical-device install, camera recording and animation acceptance remain unperformed.

## Legacy camera follow-up

Camera1 round-video zoom and flash changes now share the existing single camera executor and coalesce to the current state. No camera parameter writes occur on the UI thread for these recording controls. Actual patched setters passed a fake-worker fixture for rapid zoom, shared torch updates, redundant indices, close guards and preserved photo behavior. Exported in ordered patch 0158. The prior Android CI was superseded before delivery.
