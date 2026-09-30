# Camera module zoom implementation plan

**Goal:** Make round-video zoom use available camera modules and the camera's full reported range.

**Architecture:** Discover Camera2 recording-capable cameras and physical focal lengths once when opening the recorder. Prefer a logical multi-camera; use its zoom ratio for automatic physical-lens transitions. For independently exposed modules, reopen the selected camera through the existing encoder-preserving preview transition. Keep legacy Camera1's actual zoom-ratio table. The UI consumes optical stops rather than inventing lens buttons from digital limits.

**Tech Stack:** Java, Android Camera2/Camera1, Canvas, ordered Telegram source patches.

### 1. Capability catalog and routing

- [ ] Create `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaCameraZoom.java` with immutable camera modules (`id`, `base`, `min`, `max`, `logical`) and a catalog containing `defaultId`, optical stops, and recording range.
- [ ] Normalize focal lengths with `36 * focalLength / sensorWidth`; use the rear module closest to 26mm equivalent as 1×. Ignore modules without SurfaceTexture output. Choose logical camera coverage first; otherwise choose the longest optical focal length that can supply the requested ratio.
- [ ] Test a 0.5×/1×/3× catalog through 60×, overlapping ranges, gaps, and logical-camera priority with an executable Java check.

### 2. Native recorder bridge

- [ ] Add the pinned `CameraSession.java` to the disposable baseline, then add `getZoomFactor`, `getMaxZoomFactor`, and `setZoomFactor` using the camera's percent-based zoom table. Existing normalized `setZoom` remains compatible.
- [ ] Add a preferred-ID overload to `Camera2Session.create`; keep its zoom values local to that camera. Default selection uses the catalog.
- [ ] Route `InstantCameraView.applyNebulaZoom` through catalog selection: `localZoom = globalZoom / module.base`. Use `reinitForNewCamera()` for independent modules, retain the encoder, defer the latest drag value until the new session opens, and fall back to the prior camera on open failure.
- [ ] Start pinch zoom from the current factor and preserve it on release. Keep front/back catalogs separate. Disable concurrent-camera mode only when separate rear IDs must be opened for lens switching.
- [ ] Export the native diff as `patches/android/0155-round-video-camera-modules.patch` using `git diff --binary` in `build/cherrygram-android`.

### 3. Lens pill and scrolling ruler

- [ ] Update `NebulaZoomSlider.setRange` to accept finite positive camera limits without 0.5×/10× caps, preserving the current factor.
- [ ] Add `setCameraStops(float[])`; render one compact circular selection per optical stop. Match the latest video’s small side +/- buttons. Expand on drag/hold to a ruler with constant spacing per octave, labels at optical stops and reported limits, and a fixed central needle.
- [ ] Cancel posted callbacks and the expansion animator on detach. Use current Telegram theme colors and preserve the selected factor while changing the range.

### 4. Validation and publication

- [ ] Run `python scripts/check-camera-zoom.py` and `python scripts/check-playback-speed.py build/cherrygram-android`.
- [ ] Run `python scripts/check-upstream-series.py android --tree vendor/telegram-android --ref dc780e81ed1261c369c27870e8e0999a1eb0b600` and `git diff --check`.
- [ ] Publish exact changed paths to the authorized Git branch, wait for the Android APK build, and inspect its result. Record device-only limits: vendors may hide cameras or let the HAL choose a physical lens according to light/focus; never advertise inaccessible modules.
