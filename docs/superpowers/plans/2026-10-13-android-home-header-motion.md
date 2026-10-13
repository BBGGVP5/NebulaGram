# Android home header motion and border follow-up

> Execute inline; preserve the dirty vendor checkout and all unrelated user files.

**Goal:** Remove outlines from home buttons and make title/story motion share the same scroll frame, with smooth reversals and no delayed title position.

**Architecture:** Add ordered patch 0202 over the verified 12.10.6 / patch 0201 sources in an owned fixture. Keep native story artwork, selection and menus. One reversible scroll fraction drives two smooth phases: shrink/gather full rings below the toolbar, then raise the complete stack into its reserved title slot. The classic header retains its existing animation. Use fractional coordinates for the shared slot and publish its state before drawing instead of from the story draw pass.

**Stack:** Java ActionBar / DialogsActivity / DialogStoriesCell, geometry helper, executable native-method fixtures, full Android APK CI.

- [x] Prepare `build/android-home-motion-20261013` from the verified patched source paths; baseline only the three relevant native Java files and glass drawable.
- [x] Add regression cases for scroll reversals, unchanged geometry across paused frames, synchronization before paint, fractional coordinates, and home button stroke width zero.
- [x] Disable both stroke widths on the home Edit and right action material before drawing, including selection. Keep their fill, blur and native touch targets. Rebuild cached paths only when widths change and skip zero-width strokes in software and hardware draw paths, avoiding Android's zero-width hairline.
- [x] Publish title/story progress in the native collapse update; stop triggering header mutation in `DialogStoriesCell.dispatchDraw`. Retain the missing/hidden-story reset.
- [x] Use one home scroll progress for item size/position and title geometry, bypassing the threshold's long overshoot. Preserve classic behavior; direct scroll tracking adds no independent animation when motion is reduced. Remove the rectangular ring mask after proving safe full-ring trajectories. Align the transition and mini-list centers and horizontal spacing, and treat the last visible ring as last when other stories are hidden by toolbar width.
- [x] Apply the final patch strictly, run affected regression/SDK checks and full APK build, verify its source/package/signature/resources and record device QA honestly.
- [x] Review the supplied video if a valid source becomes available; track this separately from static code and compilation checks.

Reference retrieval: the DropMeFiles page reports that it cannot process this video. Both its normal and per-file download returned the identical 22,669,070-byte MP4-shaped file with the first 12,582,912 bytes zeroed and no `ftyp`, `moov` or `mdat` header; SHA-256 `07c2c56c73fb04af8036d26cb6a27fb00e1b571373b27cfb4b8d3e631de80215`. FFmpeg cannot decode it. No visual finding is inferred from this damaged file. A text question requests an original local path or another valid copy while independent code work continues.

Local validation: all 197 ordered patches apply to the committed Telegram 12.10.6 pin in `build/android-home-motion-12106-20261013`; the vendor checkout is preserved. All 104 interface/regression commands pass locally (103 runnable checks, account-slot native contract/syntax with the Android compiler). New tests execute 240 native collapse/reversal/paused-frame states, 101 Edit material frames and actual software/hardware stroke helpers, including zero-width hairline bypass and unchanged-width cache reuse. Existing 117,120 shared-slot geometry cases, native toolbar/title checks and selection/material checks pass. These tests validate state and draw calls, not device pixels or visual equivalence to iOS.

Follow-up reference: the replacement link returned a valid original MP4, 22,669,070 bytes, SHA-256 `ef581c7869be083ebad7a74ac91f3534cbe4792593782b86ed1b360a2655505d`, 1008×2244, 12.33 seconds. Overview frames and 30 fps crops of 2.25–4.3 and 7.7–8.6 seconds show rings sliced beside the title during both expansion and collapse. The preliminary APK run 38347234276 at 903acac was cancelled to include this concrete issue. The revised trajectory passes 358,160 full-ring pose cases across densities, widths, toolbar actions, title widths and overscroll; zero-alpha rows publish no phantom stack. Final source is prepared in `build/android-home-motion-final-12106-20261013`, with old 0202 strictly reversed and the final 0202 strictly applied over the same preceding 196 patches.

Latest steering: `home_chats_title` defaults to false on both Android and iOS, so the default folder uses NebulaGram. Explicit saved title choices and optional custom-folder titles are preserved. Existing iOS persistence tests now cover the branded default and saved Chats choice.

Final local validation: all 104 Android interface/regression commands pass again against `build/android-home-motion-final-12106-20261013` after the video-driven trajectory and default-title changes. The count includes the native account-slot contract/syntax check; its executable runtime check runs in Linux CI. `DialogsActivity` updates stories before `super.dispatchDraw`, so the publisher runs before the sibling header is painted. No physical device is currently connected.

Final delivery: Android build **38348999631**, APK **1000510**, source **fee4600010c13f54a305a4992985ecf66eee8a9d**, succeeds including all 104 interface/regression checks and the full app/native build. Independently downloaded APK: `build/artifact-38348999631/NebulaGram-1.0.0-TG-12.10.6-b1000510-arm64-v8a.apk`, **60,065,426 bytes**, SHA-256 **d08aa10fdb9da7c12e84762adf63b6ec75ef76b482b7f819b02056cee6650523**. Package `app.nebulagram.messenger`, version 1.0.0 / code 1000510, arm64 Telegram and NebulaLink libraries, expected signing certificate `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`, ZIP integrity and toolbar/voice/chat runtime markers verified. Receipt: `verified-apk.json` in that directory. Device acceptance is not claimed.

iOS bootstrap **38348999690** and settings contract **38348999584** succeed at the same source revision. All 159 XCTest cases pass, including the branded default and persistence of an explicit Chats choice. All 103 ordered iOS patches apply, and the embedded Foundation module compiles and runs. This follow-up changes only the iOS title preference fallback; no new full IPA or physical iPhone test is included.

## Follow-up after device feedback

The user reports a remaining button outline and another mismatch in opening stories. They explicitly say the unwanted motion is not the gather stage. A pending text clarification asks which motion and which buttons. Do not change the trajectory on an unsupported assumption.

The native `topPanel` provider still supplies a light shadow in dark mode (`0x04FFFFFF`); both software and RenderNode draw paths draw it independently of stroke width. Configure the home header material with zero stroke colors, zero widths and zero shadow color at creation, preserving the native background/refraction. Other headers keep their existing provider.

- [x] Prepare the verified 1000510 native baseline in `build/android-home-contours-20261013` without changing vendor.
- [x] Add `topPanelButtons(resourcesProvider)` delegating to a private `topPanel(resourcesProvider, true)` builder. The existing `topPanel(resourcesProvider)` delegates with false. Set stroke/shadow colors and widths to zero only for the buttons variant.
- [x] Pass `BlurredBackgroundProviderImpl.topPanelButtons(resourceProvider)` to the home `DialogsActivity.setupGlass` call.
- [x] Execute the production provider and builder in both themes and blur settings; assert transparent contours, identical fill, and unchanged ordinary provider. Run existing header checks and the contour APK build (38355079181 / 1000512 succeeds at b0e8a687; final motion follow-up below).
- [ ] Apply the user's specific motion correction once clarified and test that transition before building the final combined follow-up.

The production provider methods and production builder/interfaces pass 1,000 live theme/blur/density states through `scripts/check-home-button-contours.py`. Fill matches the ordinary provider, the home contour colors/widths remain zero, and ordinary header strokes/shadows stay unchanged. This is provider/draw-path evidence, not physical screenshot acceptance.

All 198 ordered patches apply in `build/android-home-contours-12106-20261013`, with the dirty vendor checkout preserved. All 105 Android interface/regression commands pass locally (native account-slot execution is syntax-only locally and executable in Linux CI). The user's next video link displays an upload-error deletion page; no frames from that replacement have been seen. A pending text question requests a local original path. The trajectory remains unchanged in this contour correction because the user identified another, as yet unspecified motion issue.

The next recording is valid: 22,264,574 bytes, SHA-256 `e15b4b9b51deb291f15c43093ef226f0dd9d8b472ae813d128776df9704ebdf0`, 1008×2244, 12.71 seconds. Dense crops show the detached stack below the title on expansion and the bottom of the rings sliced on repeated gestures (around 6.65–7.0 seconds). The native right-menu `saveLayer` / rectangular clip still limits drawing to the moving story view's 81 dp height. The earlier ring safety test did not cover this remaining layer.

- [x] Preserve the horizontal gathering and shared title slot, replace the piecewise vertical stop with a continuous reversible path whose endpoints match both native renderers.
- [x] Bypass the old right-menu layer in the home header, where the safe reserved slot already limits the selected rings. Keep the classic mask and its fade unchanged.
- [x] Test the continuous path with native row geometry, overscroll, narrow toolbars and both endpoint renderers; execute the actual mask condition with moving view bounds.
- [ ] Run all interface checks and a combined final APK build; record artifact verification separately from physical device acceptance.

Patch 0204 and the overlay helper apply after the preceding 198 patches in `build/android-home-motion-video-final-12106-20261013`. All 105 interface checks pass; the strengthened motion check passes again after correcting its landscape/overscroll fixture to use native Edit bounds (8..76 dp, 44 dp height) and the row's `storiesOverscroll/2` offset. It covers 716,320 full-ring poses for 48/56 dp toolbars and 84 frames extending outside the old moving layer. The vertical fraction is `smooth(progress²)`, with no threshold or intermediate stop; horizontal gathering is unchanged. No Android device appears in `adb devices`.
