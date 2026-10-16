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

- [ ] Extend Android native actions with explicit newline insertion, underline, strikethrough and code; select the word at/before the caret without splitting UTF-16 surrogate pairs. Keep newline separate from the IME send action, preserve entities and never replace the entire draft.
- [ ] Use 39-point tiles within 44-point touch targets, 12-point visual spacing, an action-group gap, softer theme-derived tile contrast and horizontal overflow. Match the same action order and sizes on iOS.
- [ ] Refresh accessory visibility once on keyboard visibility transitions, including an empty composer. Remove repeated child measurement at zero height without changing the native composer minimum. Keep the toolbar height stable when sending clears the draft.
- [ ] Inspect the send path for synchronous work: replace the per-message notifications preference disk commit with apply (in-memory preference ordering retained); skip disabled draft-translation snapshots before copying entities. Do not attribute network delivery delays to the toolbar without evidence.
- [ ] Execute selection/newline/UTF-16 tests, keyboard transition and measurement checks, send-path ordering checks, Android SDK compilation and native patch replay. Verify the actual rendered strip and keyboard reopening on the connected phone; record any unavailable device/network acceptance separately.
- [ ] Port the source-backed actions and appearance to iOS, compile/check the changed components, then publish complete APK/IPA build runs with exact source and verification scope.
