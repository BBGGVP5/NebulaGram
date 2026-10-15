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

- [ ] Run focused regressions, the full Android suite, ordered patch replay and native parser.
- [ ] Build the complete APK, verify original signing/package identity, install over 1000569 without clearing data, and verify launch plus the changed UI on the connected phone.
- [ ] Record precise evidence and outstanding server/device acceptance. Port shared presentation changes to iOS where applicable, keeping the full IPA build status explicit.
