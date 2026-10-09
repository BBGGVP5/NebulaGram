# Update sheet gestures and animation — October 9

- [x] Reproduce the static rocket on physical build 1000460. The Telegram TGS document is loaded and has a bitmap, but playback remains paused while the sheet translates into view. Child drawing is cached, so onDraw does not observe the final viewport.
- [x] Add a scoped pre-draw listener to re-evaluate actual visibility; invalidate only when playback/fallback visibility changes. Remove it on detach. Retain reduced-motion, focus and decoding guards.
- [x] Use NestedScrollView in both update sheets. Keep Telegram BottomSheet direct swipe disabled, as required by its native nested-scrolling path.
- [x] Add regression coverage for ancestor-only movement without child layout/draw, no redundant invalidations, offscreen pauses and listener cleanup. All 96 checks passed before the emoji addition; the updated emoji fixture passed afterward. Changed production classes compile with the Android SDK.
- [x] On the connected Pixel, instrument the exact 1000460 APK without changing account data: verify three animated reopens, panel translation during drag, and downward dismissal. Screenshots/logs remain under ignored build/update-sheet-video-1009. This diagnostic package is not the release artifact.
- [x] Build the ordinary APK from source in CI and replace the temporary diagnostic installation.

iOS selection/update-introduction port is tracked separately in 2026-10-09-ios-selection-parity.md.

Release validation: Android run 37899392004 succeeded at d9804f22c9719bcceae1cf309c0ba718804e83f7. APK build 1000463, arm64-v8a, SHA-256 15ca81b3f1391cad7eaf2cc5eede5669ab875d80742b7741d1e909761219ca59. The expected signing certificate and native libraries passed independent checks, and the archive contains no diagnostic helper. Installed without deleting data. Repeated the three reopen/animated-rocket/drag-dismiss cycles on the actual CI APK; all passed. Captures and verification JSON: build/artifact-37899392004.

The new ancestor-only transition test was also run against the previous production implementation: it fails there and passes with this fix.
