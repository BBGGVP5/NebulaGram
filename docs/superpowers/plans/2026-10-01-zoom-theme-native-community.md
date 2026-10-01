# Zoom Theme and Native Community Implementation Plan

> **For agentic workers:** Execute inline in the authorized checkout; preserve unrelated vendor and artwork changes.

**Goal:** Use the active chat theme, larger stable zoom controls and Telegram's actual community card; place project support before Tools.

**Architecture:** Pass the recorder's existing ResourcesProvider into the zoom view. Keep requested UI zoom continuous while native camera parameters remain quantized. Reuse CommunityLinkView2 and resolve the user's real community peer, with its actual title, avatar and linked-chat count. Remove the link-directory route. Preserve the existing camera session and encoder.

**Tech Stack:** Android Java/Canvas, Telegram native UI/MTProto, Swift/UIKit, Python Java fixtures.

## Task 1: themed geometry and continuous input

### Follow-up: immediate quick presets and editor restoration

- [x] Execute the actual compact-button touch handler in a Java fixture: a 2x tap must send the final value once on release, without creating a zoom animator or emitting 1.1x/1.2x frames; rapid 1x/30x taps must replace old animated work. Drag and long press must retain the expanded ruler without committing a preset on release or cancellation.
- [x] Route compact quick buttons through `selectPreset(float)` with an immediate `setCurrent(target)` and callback. Keep `animateZoom(float)` for taps on the expanded ruler.
- [x] Prevent `drawMessageEditText` from drawing the native cursor/placeholder while `ownsRecordingIsland()` is true. Restore the editor when the native recording/preview panel closes, without clearing its text or changing its animation alpha. Run the actual draw-wrapper fixture during recording, cancellation, preview, hidden/faded panels and normal multiline editing.
- [x] Export native patch 0161 after the committed 0160 scratch baseline, check ordered application and existing camera/composer regressions, publish and verify the replacement Android APK.

Verification: the actual compact touch-handler fixture failed before the change (2x tap still submitted 1x at animation start). It now submits final targets once, cancels stale animated frames, and retains long press/drag/cancel behavior. Additional hit checks cover compact and collapsing geometry at 0/50/90% expansion. The actual editor wrapper fixture failed before the guard because it drew the cursor/hint during the delete island; recording/preview/fade suppression and normal multiline/native fallback rendering now pass. Returning false while hidden preserves the ViewGroup animation-frame contract. All 156 ordered Android patches apply. Runtime commit `dc45d3d` is published on main and codex/camera-controls. Android build `36870625795` (1000275) passed the full regression suite, application compile and package/ABI checks; duplicate builds were cancelled. Downloaded arm64 APK is 52,489,048 bytes, SHA-256 `C3B28F4DF314A6E008D9BFB49D2EF081C46471FAE76CE9234301DA7AE6E5B0CA`. Its v1/v2 signatures verify; certificate SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab` matches 1000269, allowing an in-place update. Physical-device confirmation remains pending. iOS runtime inputs are unchanged.

### Follow-up: recording control spacing and cancellation

- [x] Align major ruler ticks with the exact values in `rulerMarks`, skip nearby minor ticks and place labels below their matching marks. Extend the actual Canvas fixture to verify every visible label has a tick at its x-coordinate, including 5x and fractional optical stops.
- [x] Raise the 96dp zoom host bottom margin from 104dp to 116dp, leaving another 12dp between the ruler and camera/flash island. Increase the recording timer left margin from 22dp to 30dp.
- [x] Let the recording/preview island exclusively own the attachment position until its native panel is hidden. `NebulaAttachmentButton.refreshStyle()` must hide the paperclip while preserving its native animation properties, then restore it when cancellation completes. Test recording, fading, preview and restored composer states at all existing densities and widths.
- [x] Export the native delta after committing patch 0159 as a scratch baseline; validate the ordered Android series, publish the changes and verify the new APK.

Verification: the label-position fixture first failed on the old tick grid; the attachment transition fixture first failed on the visible paperclip beneath the delete island. Updated fixtures pass across three densities and screen widths, with exact fractional optical marks, vertical label clearance, nearby minor-tick suppression and the actual native `isRecordingStateChanged` restoration hook. Camera routing/worker/recorder checks, 72 composer layouts, native header/grid checks and all 155 ordered patches pass. Runtime sources are published at `895b6dd`; Android build `36863241349` (1000269) passed its full interface/regression, application compile, version, ABI and library checks. Downloaded arm64 APK is 52,489,432 bytes, SHA-256 `FBE43F9C8AD8C23AE6F0BE7153EA71BCB5538CDD49480E309999F4F6A25EF5CD`; v1/v2 signatures verify and certificate SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab` matches 1000265. Its higher build code supports updating in place. Physical-device confirmation of the follow-up fixes remains pending. iOS bootstrap `36863060384` passed; iOS runtime files were unchanged.

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
- [x] Connect the user-supplied `nebulaguard_channel`; resolve its actual linked community with current-account metadata and lifecycle guards. The native card is wired into Android About and Support. Unknown counts stay generic until Telegram supplies full metadata.
- [x] Reuse native `CommunityLinkView(context, resourcesProvider)` on Android. Resolve the community peer and load ChatFull; update title/avatar/count and open Telegram's community sheet. A stale/detached view must not update itself after a pending request.
- [x] Remove `new NebulaSupportFragment(true)` and the four-link directory. Existing external source/update information remains outside the community card when appropriate.
- [x] Move the Support card before the Tools header on Android.
- [ ] Update iOS support/community navigation to open the actual Telegram destination, with current presentation colors. Pinned iOS has community peers, metadata and native ItemListPeerItem rendering; no iOS source change or new native build is claimed in this zoom-only commit.

## Task 4: validation and delivery

- [x] Run `python scripts/check-camera-zoom.py build/android-validation`, camera discovery/request/recorder fixtures, API 36 overlay compilation and full 154-patch validation. Actual onDraw/preset/ruler methods also pass two distinct chat palettes across compact, expanding and expanded states, plus null-provider fallback. Published runtime 42a34ac passes Settings contract CI 36827014417 and Android APK CI 36827014454. Duplicate branch APK CI 36827014540 was cancelled.
- [x] Compile actual Android. iOS source is unchanged in this runtime. No physical-device smoothness or visual acceptance is claimed from source fixtures.
- [x] Publish zoom/support-placement task paths, download the APK and verify package, version, signer and included native libraries. Record device-only visual/performance checks separately.

Verified artifact: `build/qa-apk-zoom-theme-42a34ac/NebulaGram-1.0.0-TG-12.10.5-b1000265-arm64-v8a.apk` (52,488,958 bytes), package `app.nebulagram.messenger`, version 1.0.0/code 1000265, target/compile SDK 36, arm64-v8a. Telegram and NebulaLink libraries are present. v1/v2 signatures pass with one signer; certificate SHA-256 matches build 1000264 (`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`). APK SHA-256: `CCDA187FF5FCEDEA08F0F8C453963AD6108769B1967320260D3CE8102D3BD48E`.

The user supplied `https://t.me/nebulaguard_channel` on 2026-10-01. Android now resolves this channel, follows its actual `linked_community_id` from the resolved/full peer cache and opens the native `CommunitySheet`. `CommunityLinkView` matches the submitted profile card, including its stacked avatar and bold title. Counts are supplied by the community's full metadata, never copied from the screenshot. The directory was removed from Android About and Support. iOS native card integration remains a separate outstanding task.

## Follow-up: bounded ruler and stable attachment island

Files: `NebulaZoomSlider.java`, `NebulaComposerStyle.java`, scratch `ChatActivityEnterView.java`, `scripts/check-camera-zoom.py`, `scripts/check-chat-layout.py`.

- [x] Add a failing geometry assertion `b.right - b.left <= r.dp(280)` for the fully expanded ruler; retain the complete device range and fixed center cursor. Change `fullWidth()` to `Math.max(dp(48), Math.min(dp(280), getWidth() - dp(32)))`.
- [x] Assert the recording and preview circle starts at the normal attachment's `margin` and the main capsule starts at `margin + dp(44) + dp(6)` before and after cancellation. Change `drawRecording` to `int circleLeft = left;`.
- [x] Align the native 28dp recording dot with the normal 44dp attachment circle using `recordTimeContainer.setPadding(dp(nebulaRecordingIslands ? 8 : 13), 0, 0, 0)` during measurement. Retain the 30dp timer margin, giving the same 16dp inset from the main capsule. Test the actual conditional padding at each existing density.
- [x] Inspect cancellation ownership and the submitted translation/backdrop screenshot; fix only runtime causes supported by source evidence. Inspect the lower navigation's selected compact mode before changing it.
- [x] Run `python scripts/check-camera-zoom.py build/android-validation`, `python scripts/check-chat-layout.py`, API 36 compilation and the complete ordered patch check. Export the native delta as patch 0162 from scratch baseline 31cb6cb, publish the exact changed paths and verify an Android APK.
- [x] Replace the directory with `NebulaCommunityCard` and `CommunitySheet` when the user supplies the real community username/link. Load title, avatar, chat membership/count from Telegram; do not infer a public username or server count from a screenshot.

Implemented follow-up: ruler width is capped at 280dp; recording/preview/normal circle and capsule origins agree in all 72 layout cases; native dot padding aligns its center with the 44dp attachment island while retaining the timer's 16dp inset. All camera routing, touch, palette, API 36 overlay compile, community metadata/account/lifecycle and native menu/layout fixtures pass. The translation report reproduces with the lifted-message mode: the final full-screen fade previously remained within that message's transformed clip. Native drawing now restores screen coordinates first, and fade completion clears lift ownership/bounds with cancellation and stale-generation guards. `scripts/check-translation-lift.py` executes the actual native fade method and backdrop fragment. The compact lower panel and hidden labels shown in the screenshots are existing user-controlled preferences; no unrequested preference reset is applied. Runtime/device verification follows publication.

Verified delivery: runtime `4565a76881da61e9d43d8c9da6423a7595915198` is published on main and codex/camera-controls. Android CI `36881224754` passed the full regression suite, application compilation, package/version and library checks. Build 1000277 downloaded to `build/qa-apk-community-4565a76/NebulaGram-1.0.0-TG-12.10.5-b1000277-arm64-v8a.apk`; size 52,489,562 bytes; SHA-256 `4232123AA475C957B35EB6EF80B6499903862D9EC0177CF17D804B5654DA2D46`. Package/version fields verify; arm64 Telegram and NebulaLink libraries are present. v1/v2 signatures verify with certificate SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`, matching 1000275. Physical-device confirmation of positioning, cancellation and live community metadata remains pending. iOS runtime files are unchanged; bootstrap `36881224593` passed. Scratch exported native baseline is 310b142.

## Follow-up: avatar lifecycle and navigation preview bounds

Files: Android overlay `NebulaCommunityCard.java`, `NebulaFoldersPreview.java`, `NebulaSectionFragment.java`; `scripts/check-community-card.py`, `scripts/check-navigation-preview.py`, `.github/workflows/android.yml`.

- [x] Extend the community fixture: repeated metadata notifications must preserve `card.sets`; a recreated card must display the cached peer before the resolve callback; changed photo IDs must bind once; errors must retain cached metadata. Run `python scripts/check-community-card.py` and observe failure before implementation.
- [x] Set the native avatar receiver's account explicitly. On attachment, use `controller.getUserOrChat(username)` immediately, then resolve fresh metadata. Snapshot peer/photo IDs; call `card.setChat` only when they change. Update title/count separately with `DialogObject.getShortName` and `LocaleController.formatPluralString`. Request missing community full info once per attached lifecycle with `loadFullChat(id, guid, false)`.
- [x] Reserve separate sample and tabs rectangles: sample height 136dp, tabs height `NebulaFolderTabs.HEIGHT_DP`, inset/gap 8dp, total 210dp. Place sample at 8dp for bottom tabs, 66dp for top tabs. Translate the blur capture by the sample's actual position. Add 10dp separation before the folders preview.
- [x] Execute actual preview refresh/measurement/capture methods in a Java layout fixture at several densities, both placements and repeated toggles; verify child bounds and captured coordinates. Run this fixture in Android CI and run existing folder glass/runtime/design checks locally.
- [ ] Commit exact task paths, publish source, compile Android in CI, download and verify the new APK's version, libraries and signature. Record physical-device flicker/overlap confirmation separately.

## Follow-up: compact sheets and message workspace

Files: Android overlay `NebulaDialog.java`, `NebulaMessageToolsFragment.java`, new `NebulaToolGrid.java`, `NebulaAutoTranslate.java`.

- [x] Replace the repeated full-width cancel footer with a labelled close control in the sheet header. Use 20sp headings, grouped 52dp choice rows and compact editors/actions with at least 48dp touch targets. Preserve selection/accessibility and every existing callback; keep actual footer height reserved by the content scroll measurement.
- [x] Put secondary and primary actions beside each other with equal weights and wrapped labels. Keep an explicit negative action when it has a callback; header dismissal must invoke the configured negative callback exactly once.
- [x] Put message actions in an adaptive grid: normally three cells per row, fewer at narrow widths or large fonts. Each cell has a 24dp icon above a wrapping label, a minimum 88dp height and a theme-colored ripple. Measure each row by its tallest child, including large text.
- [x] Keep a bounded editable source, a compact language field, one result card with a copy action, and a stop action shown only during work. Preserve translation, summary, transcription, speech, task creation, cancellation and account/lifecycle behavior. Put auto-translation, filter and provider settings under a collapsed options group.
- [ ] Shorten the auto-translation explanation while retaining the fact that visible messages go to the configured AI provider; offer disabling only when translation is enabled. Compile Android and run native/AI/lifecycle and grid/footer layout checks before delivery.
