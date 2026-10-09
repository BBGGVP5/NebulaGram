# Update sheet gestures and animation — October 9

- [x] Reproduce the static rocket on physical build 1000460. The Telegram TGS document is loaded and has a bitmap, but playback remains paused while the sheet translates into view. Child drawing is cached, so onDraw does not observe the final viewport.
- [x] Add a scoped pre-draw listener to re-evaluate actual visibility; invalidate only when playback/fallback visibility changes. Remove it on detach. Retain reduced-motion, focus and decoding guards.
- [x] Use NestedScrollView in both update sheets. Keep Telegram BottomSheet direct swipe disabled, as required by its native nested-scrolling path.
- [x] Add regression coverage for ancestor-only movement without child layout/draw, no redundant invalidations, offscreen pauses and listener cleanup. All 96 checks passed before the emoji addition; the updated emoji fixture passed afterward. Changed production classes compile with the Android SDK.
- [x] On the connected Pixel, instrument the exact 1000460 APK without changing account data: verify three animated reopens, panel translation during drag, and downward dismissal. Screenshots/logs remain under ignored build/update-sheet-video-1009. This diagnostic package is not the release artifact.
- [ ] Build the ordinary APK from source in CI and replace the temporary diagnostic installation.

iOS selection/update-introduction port is tracked separately in 2026-10-09-ios-selection-parity.md.
