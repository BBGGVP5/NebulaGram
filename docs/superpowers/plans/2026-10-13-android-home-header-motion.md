# Android home header motion and border follow-up

> Execute inline; preserve the dirty vendor checkout and all unrelated user files.

**Goal:** Remove outlines from home buttons and make title/story motion share the same scroll frame, with smooth reversals and no delayed title position.

**Architecture:** Add ordered patch 0202 over the verified 12.10.6 / patch 0201 sources in an owned fixture. Keep native story artwork, selection and menus. Home motion consumes the current scroll fraction; the classic header retains its existing animation. Use fractional coordinates for the shared slot and publish its state before drawing instead of from the story draw pass.

**Stack:** Java ActionBar / DialogsActivity / DialogStoriesCell, geometry helper, executable native-method fixtures, full Android APK CI.

- [x] Prepare `build/android-home-motion-20261013` from the verified patched source paths; baseline only the three relevant native Java files and glass drawable.
- [x] Add regression cases for scroll reversals, unchanged geometry across paused frames, synchronization before paint, fractional coordinates, and home button stroke width zero.
- [x] Disable both stroke widths on the home Edit and right action material before drawing, including selection. Keep their fill, blur and native touch targets. Rebuild cached paths only when widths change and skip zero-width strokes in software and hardware draw paths, avoiding Android's zero-width hairline.
- [x] Publish title/story progress in the native collapse update; stop triggering header mutation in `DialogStoriesCell.dispatchDraw`. Retain the missing/hidden-story reset.
- [x] Use one home scroll progress for item size/position and title geometry, bypassing the threshold's long overshoot. Preserve classic behavior; direct scroll tracking adds no independent animation when motion is reduced.
- [ ] Apply the final patch strictly, run affected regression/SDK checks and full APK build, verify its source/package/signature/resources and record device QA honestly.
- [ ] Review the supplied video if a valid source becomes available; track this separately from static code and compilation checks.

Reference retrieval: the DropMeFiles page reports that it cannot process this video. Both its normal and per-file download returned the identical 22,669,070-byte MP4-shaped file with the first 12,582,912 bytes zeroed and no `ftyp`, `moov` or `mdat` header; SHA-256 `07c2c56c73fb04af8036d26cb6a27fb00e1b571373b27cfb4b8d3e631de80215`. FFmpeg cannot decode it. No visual finding is inferred from this damaged file. A text question requests an original local path or another valid copy while independent code work continues.

Local validation: all 197 ordered patches apply to the committed Telegram 12.10.6 pin in `build/android-home-motion-12106-20261013`; the vendor checkout is preserved. All 104 interface/regression commands pass locally (103 runnable checks, account-slot native contract/syntax with the Android compiler). New tests execute 240 native collapse/reversal/paused-frame states, 101 Edit material frames and actual software/hardware stroke helpers, including zero-width hairline bypass and unchanged-width cache reuse. Existing 117,120 shared-slot geometry cases, native toolbar/title checks and selection/material checks pass. These tests validate state and draw calls, not device pixels or visual equivalence to iOS.
