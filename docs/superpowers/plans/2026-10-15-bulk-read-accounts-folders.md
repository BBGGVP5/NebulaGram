# Bulk read stability, account picker and iOS-style folders

**Goal:** Fix the reported Android connection/updating stall after “Read all chats”, replace the sparse account form with a compact account picker, and make the requested iOS-style folder strip visible in the real chat list.

**Architecture:** Preserve native read receipts/PTS ownership, per-account isolation and existing settings. Investigate the bulk-read request/update path before changing synchronization. Reuse native avatars, the existing folder renderer and captured glass source; verify actual rendered screens as well as isolated fixtures. Execute inline in owned `build/android-1302-integrated-20261015`; do not modify the dirty vendor checkout.

**Tech Stack:** Java/Android UI, Telegram native synchronization, Python/Java executable fixtures, GitHub Actions and the connected Pixel 9 Pro XL.

The user additionally requests a proper About-page heading/brand composition. The later Swiftgram reference clarifies the bottom folder bar: a floating rounded surface, selected capsule and inline unread counter. The user explicitly clarifies that gray is the example's theme, not a required fixed color: use the current theme. Preserve existing folder routing and saved preferences.

## 1. Read-all diagnosis and regression

- [x] Confirm the trigger with the user: the “Read all chats” command, rather than opening chats individually.
- [x] Trace folder/global/archive read-all entry points, queued read acknowledgements, PTS/difference recovery and status presentation. Scoped logcat had no useful network diagnostics. Do not mark the user's unread messages as read merely to reproduce the bug.
- [x] Reproduce stale acknowledgement PTS/SEQ responses incorrectly triggering getDifference. Apply the documented duplicate/overlap rule from https://core.telegram.org/api/updates#pts-checking-and-applying, preserving genuine gap recovery and monotonic state. Filter bulk read/mention RPCs to dialogs that need them, retaining forum/secret/manual-mark semantics.
- [ ] Verify bulk sizes, repeated commands, out-of-order/error callbacks, navigation/account switches and absence of unbounded work on the UI/stage queues.

## 2. Account picker

Files: `NebulaAccountsFragment.java`, a focused native-avatar row if needed, `NebulaAccountList.java` only if presentation projection needs changes, account/privacy checks.

- [x] Replace the large hero and detached management group with a compact title/search/actions hierarchy and actual avatar/name/username/current-account rows.
- [x] Keep persistent IDs, search/sort, account addition and presentation masking. Refresh only for relevant account/profile/privacy changes, and remove observers/cancel callbacks on destruction. Actual avatar binding executes with sparse owners and no photo request while masked; row compiles against the Android SDK.
- [ ] Inspect the actual screen on the phone with normal and masked rows; preserve theme/RTL/large text behavior.

## 3. Real folder strip

Files: `NebulaFolderTabs.java`, `NebulaFolderGlass.java`, `NebulaTabLens.java`, `FilterTabsView.java` / `DialogsActivity.java` ordered patches, `NebulaFoldersPreview.java`, folder behavior/renderer tests.

- [x] Inspect the actual settings on 1000569: the legacy extra-outline preference is on, and the preview shows an accent outline. The existing style label incorrectly implies bottom-only operation. Capture: `build/folders-before-569.png`.
- [x] Make the real strip and preview share a rounded glass surface, theme-owned selected capsule and inline counters; retain top/bottom placement, native folder selection, scrolling and accessibility. A clearly labelled iOS preset applies bottom/glass/no-extra-outline explicitly; ordinary saved preferences are preserved.
- [ ] Verify live preference changes, theme/fallback/reduced motion, swipes and selection without extra outlines or idle redraw work.

## 4. Delivery

### Blocking formatting regression added by the user

The screenshot shows empty native composer bubbles when the optional formatting strip is enabled. The native `drawChild` separator omits the accessory height although bottom padding moves the editor up by that height, clipping all native text/buttons. The native input bubble also includes the accessory height in its own background. Fix both boundaries while keeping the full height in the chat content reserve. Use fixed-width 44-point buttons in the horizontal scroller rather than zero-width weighted children; retain the real EditTextCaption selection/entity actions.

- [x] Execute the real clip/bubble-bound calculations for 48 editor/reply/accessory/AI states and non-zero action widths at four densities.
- [x] Separate the input-island drawable from the accessory height and correct the editor/top-view clipping boundary; use native formatting icons and theme-owned tiles.
- [x] Confirm the new geometry fails against the pre-fix native source with “formatting row clips native text/buttons”.
- [ ] Verify actual typing/selection, multi-line input and repeated keyboard close/open on the phone before claiming the rendered regression fixed.

About composition: keep the native “About NebulaGram” title visible at rest, show the brand and installed app/build version in the hero, and retain support/update/community/component actions. Verify at rest and after scrolling. Android files: `NebulaSectionFragment.java` and `NebulaSettingsLayout.java`; mirror the presentation in the iOS native settings routes where applicable.

- [x] Run focused regressions, 129 fresh Android CI checks, 226 ordered Android patches and the native Java parser (461 actual units). Extend geometry coverage to parent clipping, padding and actual overlay measurement after the real-device failure.
- [ ] Build the complete APK, verify original signing/package identity, install over 1000569 without clearing data, and verify launch plus the changed UI on the connected phone.
- [ ] Record precise evidence and outstanding server/device acceptance. Port shared presentation changes to iOS where applicable, keeping the full IPA build status explicit.

## Follow-up from the actual 1000570 phone check

The user clarifies that the formatting strip belongs **above the input field**. On the Pixel, 1000570 shows the editor again but leaves a blank reserved accessory area: FrameLayout clips children placed in its padding. The isolated clip calculation missed this parent `clipToPadding` boundary. Correct the parent boundary, put the accessory in top padding and explicitly measure its overlay without inflating the native field. Keep the native bubble below it and preserve reply/AI, recording, disabled and keyboard transitions. Extend the executable geometry check to include parent clipping, actual toolbar layout and padding changes; repeat physical typing/selection and cleanup before accepting.

The iOS preset must show its selected state instead of a navigation arrow on an immediate action. Refresh that state after individual folder options change. Put About in the main settings card; mirror the grouping and accessory placement on iOS. The existing iOS 124 source predates this clarification.

## Swiftgram toolbar and send responsiveness follow-up

Reference inspected: `Swiftgram/Telegram-iOS`, commit `cf8b23beaaac4126a396337ac2d5be13f9f76b66`, `Swiftgram/SGInputToolbar/Sources/SGInputToolbar.swift` and the native ChatTextInputPanelNode integration. The return symbol inserts a newline; it is not Undo. The source uses 39-point visuals, 12-point spacing, horizontal scrolling, native formatting callbacks and selects the last word when the selection is empty. Current master has circular glass buttons; the user's supplied reference has rounded square tiles. Implement the requested reference shape, above the composer, using independent Android/UIKit implementations.

- [x] Extend Android native actions with explicit newline insertion, underline, strikethrough and code; select the word at/before the caret without splitting UTF-16 surrogate pairs. Keep newline separate from the IME send action, preserve entities and never replace the entire draft.
- [x] Use 39-point tiles within 44-point touch targets, 12-point visual spacing, an action-group gap, softer theme-derived tile contrast and horizontal overflow. Match the same action order and sizes on iOS.
- [x] Refresh accessory visibility once on keyboard visibility transitions, including an empty composer. Remove repeated child measurement at zero height without changing the native composer minimum. Keep the toolbar height stable when sending clears the draft.
- [x] Inspect the send path for synchronous work: replace the per-message notifications preference disk commit with apply (in-memory preference ordering retained); skip disabled draft-translation snapshots before copying entities. Do not attribute network delivery delays to the toolbar without evidence.
- [x] Execute selection/newline/UTF-16 tests, keyboard transition and measurement checks, send-path ordering checks, Android SDK compilation and native patch replay.
- [ ] Verify the actual rendered strip and keyboard reopening in the full client on the phone; record unavailable device/network acceptance separately.
- [x] Port the source-backed actions and appearance to iOS and compile/check the changed components.
- [ ] Verify complete APK/IPA build artifacts with exact source and verification scope.

Source `98c860f`: all 130 Android commands passed; 227 patches replay on 203 native paths; 463 native/overlay Java units parse. The production toolbar compiles against Android SDK, 324 selection/reversal cases pass, and the new executable fixture covers 1000 IME cycles, zero/final overlay measurement, direct newline edits, immediate preference application and disabled-translation bypass. UIKit components typecheck with the official iOS SDK; the actual Foundation range helper passes word/Cyrillic/CJK/combining/emoji/UTF-16 cases plus 1001 caret edits. Windows bootstrap replay passes all 116 patches/170 native paths, and macOS CI 38852976817 passes the complete bootstrap including Swift fixtures. A WSL `--swift` full bootstrap stops at an older profile-banner fixture's unconditional CoreGraphics import (not available in Linux Swift); targeted formatting Swift checks and Darwin component typecheck succeeded.

Physical 1000571 read-only acceptance: `571-settings-root-acceptance.png` confirms About inside the main settings card; `571-about-acceptance.png` confirms the native title, branded hero and installed version; `571-folder-preset-acceptance.png` confirms selected radio/subtitle instead of an immediate-action chevron. These settings sources are unchanged in 1000572. Masked-account, theme, motion and live unread-bulk operations were not exercised by these screenshots.

Full APK 1000572: https://github.com/BBGGVP5/NebulaGram/actions/runs/38852976850. Full IPA 126: https://github.com/BBGGVP5/NebulaGram/actions/runs/38852996115. Both use `98c860f`; compilation and device acceptance are not implied by dispatch.

## Queued acknowledgement status follow-up

The user clarifies that the bubble is already visible with a clock. Keep network delivery distinct from pre-send UI work. Production `NotificationCenter.checkForExpiredNotifications` schedules a lambda that assigns its callback instead of executing it on the second expiry pass. A late lock can therefore retain delayed notifications and leave a ghost timer after normal completion. Production `AnimationNotificationsLocker.lock` also releases the old token on the newly selected account rather than its owner. Both defects reproduce before modification in `check-animation-notification-lifetime.py`; the fix changes only those two lines, retaining network requests, real acknowledgements, notification ordering and existing expiry intervals.

- [x] Add an executable fixture using the actual locker class and actual expiry/onAnimationFinish methods, virtual UI time, colliding tokens in sparse accounts and 1000 overlapping expiry cases. Verify cancellation after normal animation completion.
- [x] Fix owner-specific release and schedule the actual repeat callback, without bypassing ACKs or server events.
- [x] Run the expanded 131-check Android batch, replay 228 patches on 204 native paths and parse 464 native/overlay Java units.
- [x] Deliver full successor APK 1000576 containing this fix and the visually checked icon/clipping corrections. 1000572 predates the acknowledgement-lifetime correction; 1000574 predates the final icon corrections.
- [ ] Verify visible keyboard behavior and real device acknowledgement status when the phone is connected. USB disappeared again during read-only diagnostics; no test message was sent or existing draft changed.

## Release verification checkpoint, 16 October

Android source `586383d1afd45a47ee45331904441b5ff2cd7505`: APK 1000574 run https://github.com/BBGGVP5/NebulaGram/actions/runs/38855895696 supersedes cancelled intermediate 1000573. The word-selection stress check executes 8,001 UTF-16 units in 8,001 character reads rather than 64,048,005. macOS bootstrap 38855895666 passes all 116 iOS patches and the extended Swift fixture. iOS production files remain identical to `98c860f`; IPA 126 therefore remains the correct complete-build run rather than restarting it for Android-only changes.

The isolated Android widget fixture compiles the actual formatting toolbar, selection helper and native glyph resources with explicit offline editor/theme adapters. Its parent models the native layout contract; it does not establish Telegram entity serialization, real parent acceptance, account behavior or delivery latency. The first render run 38856924335 passed the AI/camera widget cases but stopped before formatting assertions because the emulator's IME did not receive focus. The test-only follow-up taps the actual editor and preserves screenshots/crash diagnostics on failure; it does not modify production behavior or restart APK 1000574. Physical full-client acceptance remains open.

Full APK 1000574 run 38855895696 completed successfully and was downloaded independently. Package `app.nebulagram.messenger`, original signing certificate, API 23 minimum, arm64-only native contents, 16 KB zip alignment and the packaged Go/Xray core were verified. File size: 61,994,314 bytes; SHA-256: `c8eab518701f1b5038d67df1eb86d2ecade46e65e0c31c97c5315979cdb111ed`. Receipt: `build/artifact-38855895696/verification.json`. USB is unavailable; it has not been installed or physically accepted, and no real delivery-latency test was performed.

Widget run 38858245633 passed all real-IME/callback/geometry cases once the isolated Activity requested keyboard focus after window focus. Manual inspection of dark/light/narrow-large screenshots then exposed a separate production presentation defect: RippleDrawable/InsetDrawable replaces the icon padding when its background changes. Restore the glyph inset after the background assignment and clip scrolled tiles to the toolbar's rounded outline; replace the undersized return character with a matching vector. Source `9b8af8b762ce549b44fc80b6dc12287f07d4e7f4` includes those corrections. The SDK/selection and 1,000-cycle responsiveness checks pass. APK 1000575 run 38858944517 and widget follow-up 38858945656 verify this source; 1000574 remains intermediate for presentation.

Widget follow-up 38858945656 passed; dark/light/narrow-large images were manually inspected. Actual icon content remains 20–23 dp after background changes and the row clips to its rounded outline. The Android 1000575 regression stage instead stopped on the old lifecycle fixture's View adapter, which lacked the real Android setPadding API. Source `6b6c6a55bb5d0a0e35357833f8b4b77ca9805793` updates that adapter and checks inset restoration after initial, foreground-only and chip-only palette updates. Production files remain identical to the visually checked `9b8af8b`. Successor APK 1000576: https://github.com/BBGGVP5/NebulaGram/actions/runs/38859533622.

Final APK 1000576 completed successfully, including all 131 CI regressions and full native/R8 compilation. The independently downloaded archive passes package/version/original-signing-certificate, minimum API 23, arm64 libraries, 16 KB zip alignment and Go/Xray-core verification. File: `NebulaGram-1.0.0-TG-13.0.2-b1000576-arm64-v8a.apk`, 61,994,931 bytes; SHA-256 `b6c6b89638eaf9d944dc3fa97a93dd0a1d9cf9fcdcdd903473eebed9d23b9430`. Receipt: `build/artifact-38859533622/verification.json`. The expanded 131-command local batch also passed on this source. The phone remains absent from ADB; final installation/full-client keyboard and real delivery latency remain unverified.
