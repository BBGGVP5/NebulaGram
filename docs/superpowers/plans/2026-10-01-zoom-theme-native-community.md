# Zoom Theme and Native Community Implementation Plan

> **For agentic workers:** Execute inline in the authorized checkout; preserve unrelated vendor and artwork changes.

**Goal:** Use the active chat theme, larger stable zoom controls and Telegram's actual community card; place project support before Tools.

**Architecture:** Pass the recorder's existing ResourcesProvider into the zoom view. Keep requested UI zoom continuous while native camera parameters remain quantized. Reuse CommunityLinkView2 and resolve the user's real community peer, with its actual title, avatar and linked-chat count. Remove the link-directory route. Preserve the existing camera session and encoder.

**Tech Stack:** Android Java/Canvas, Telegram native UI/MTProto, Swift/UIKit, Python Java fixtures.

## Task 1: themed geometry and continuous input

### Follow-up: recording control spacing and cancellation

- [ ] Align major ruler ticks with the exact values in `rulerMarks`, skip nearby minor ticks and place labels below their matching marks. Extend the actual Canvas fixture to verify every visible label has a tick at its x-coordinate, including 5x and fractional optical stops.
- [ ] Raise the 96dp zoom host bottom margin from 104dp to 116dp, leaving another 12dp between the ruler and camera/flash island. Increase the recording timer left margin from 22dp to 30dp.
- [ ] Let the recording/preview island exclusively own the attachment position until its native panel is hidden. `NebulaAttachmentButton.refreshStyle()` must hide the paperclip while preserving its native animation properties, then restore it when cancellation completes. Test recording, fading, preview and restored composer states at all existing densities and widths.
- [ ] Export the native delta after committing patch 0159 as a scratch baseline; validate the ordered Android series, publish the changes and verify the new APK.

Files: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaZoomSlider.java`, scratch `build/android-validation/.../InstantCameraView.java`, `scripts/check-camera-zoom.py`.

- [x] Read accent/text/background via `Theme.getColor(key, resourcesProvider)` with the provider supplied to the constructor by InstantCameraView.
- [x] Increase capsule height to 48dp, selected radius to 21dp, segment spacing to 46dp and font sizes to 14/12dp; preserve endpoints and hit regions. The view uses available screen width, with a 96dp host to keep its shadow inside bounds.
- [x] Keep animation/drag current values in the slider: do not overwrite them with Camera1's quantized `getZoomFactor()` after each callback. Actual native callback fixtures retain 1.6x in the UI while a legacy camera reports 2x, and 99 increasing fractional inputs remain continuous. Pinch callbacks also update the requested UI value. Duplicate values and unchanged accessibility labels do not generate repeated view updates.

## Task 2: remove UI-thread work during lens changes

Files: scratch `InstantCameraView.java`, ordered patch `0159` and existing camera recorder fixture.

- [x] Commit scratch patch 0158 as a local baseline with explicit repository identity before making native changes.
- [x] Capture only one 50x50 texture bitmap with `textureView.getBitmap(50, 50)`, blur the thumbnail, capture a final local bitmap and enqueue JPEG file writing on `Utilities.globalQueue`. Never read changing `lastBitmap` from the deferred writer. Actual method fixtures check small captures, no UI-thread file/JPEG writes, separate captured thumbnails and black-frame rejection.
- [x] Export only these native changes and constructor/callback changes in the next ordered patch; check series application. Patch 0159 applies as part of the complete 154-patch Android series.

## Task 3: actual community and support order

Files: Android settings/about/support and a native community component; iOS settings/support; existing feature/build guards.

- [x] Read the example at https://dropmefiles.com/wDzf5 in the browser. It shows Telegram's real community peer card, not a directory of project links.
- [ ] Connect the real community URL supplied by the user; no invented username or chat count. Required information is pending in the async question asking for NebulaHub's @username or t.me link. Local `NebulaCommunityCard.java` and `scripts/check-community-card.py` are prepared but have no published caller yet. The actual lifecycle methods pass account isolation, old-response/detach guards and server metadata updates; unknown count is never presented as zero.
- [ ] Reuse native `CommunityLinkView2(context, resourcesProvider)` on Android. Resolve the community peer and load ChatFull; update title/avatar/count and open Telegram's community sheet. A stale/detached view must not update itself after a pending request.
- [ ] Remove `new NebulaSupportFragment(true)` and the four-link directory. Existing external source/update information remains outside the community card when appropriate.
- [x] Move the Support card before the Tools header on Android.
- [ ] Update iOS support/community navigation to open the actual Telegram destination, with current presentation colors. Pinned iOS has community peers, metadata and native ItemListPeerItem rendering; no iOS source change or new native build is claimed in this zoom-only commit.

## Task 4: validation and delivery

- [x] Run `python scripts/check-camera-zoom.py build/android-validation`, camera discovery/request/recorder fixtures, API 36 overlay compilation and full 154-patch validation. Actual onDraw/preset/ruler methods also pass two distinct chat palettes across compact, expanding and expanded states, plus null-provider fallback. Published runtime 42a34ac passes Settings contract CI 36827014417 and Android APK CI 36827014454. Duplicate branch APK CI 36827014540 was cancelled.
- [x] Compile actual Android. iOS source is unchanged in this runtime. No physical-device smoothness or visual acceptance is claimed from source fixtures.
- [x] Publish zoom/support-placement task paths, download the APK and verify package, version, signer and included native libraries. Record device-only visual/performance checks separately.

Verified artifact: `build/qa-apk-zoom-theme-42a34ac/NebulaGram-1.0.0-TG-12.10.5-b1000265-arm64-v8a.apk` (52,488,958 bytes), package `app.nebulagram.messenger`, version 1.0.0/code 1000265, target/compile SDK 36, arm64-v8a. Telegram and NebulaLink libraries are present. v1/v2 signatures pass with one signer; certificate SHA-256 matches build 1000264 (`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`). APK SHA-256: `CCDA187FF5FCEDEA08F0F8C453963AD6108769B1967320260D3CE8102D3BD48E`.

Remaining required information: community URL. The unconnected local card adapter is kept outside the published runtime pending that answer. It is not included in the APK above; the old directory route has not been replaced yet.
