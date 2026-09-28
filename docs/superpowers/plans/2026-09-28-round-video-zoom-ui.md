# Round Video Zoom Control Refresh

**Goal:** Replace the basic Android and iOS round-video sliders with the supplied compact 1×/2× control that expands into a marked zoom ruler.

**Scope:** Preserve each platform's camera-limited zoom callback and existing pinch gesture. Do not alter the main screen header.

- [x] Android: compact presets, animated expansion, marked ruler and touch tracking.
- [x] iOS: matching native UIKit control, Reduce Motion support, marked ruler and touch tracking.
- [x] Validate both ordered patch series.
- [ ] Run Android/iOS compilation.
- [ ] Check recording, camera flipping, zoom limits and accessibility on devices.
