# Composer accessories and home header repair

> Execute inline. Preserve the existing vendor checkout and unrelated artwork.

**Goal:** Stable text/button placement, an outlined AI composer shortcut opening a Telegram sheet, and a readable full-glass home header.
**Architecture:** Patch the native composer measurement path, keep one owner for accessory insets, and reuse the native attached-sheet lifecycle. Retain Telegram blur capture and theme providers for the header.
**Tech Stack:** Java, Android Views, Telegram BottomSheet/ActionBar and blur3; ordered Android patches.

- [x] Reproduce the accessory collision in the native measure/layout paths. Apply shortcut margins before measurement, reserve the emoji/gift slot and remove layout-time margin writes; keep recording/editing states native.
- [x] Add a 24dp outlined vector and use a native attached sheet for message tools, preserving cancellation, draft application and language/settings routes.
- [x] Remove inherited title shrinking in the custom home toolbar and draw its complete background with the existing liquid-glass factory.
- [x] Add regression coverage for repeated text/layout changes and header visibility. Run affected existing checks, apply the complete patch series and build the APK.
- [x] Download and validate the exact APK, then record build/device-test boundaries.

## Verified delivery

- Source: f0c8c6b1f4dfab22f5cd555c4a5fd5253693f98a. Android workflow 37108027758 passed all interface/regression checks and the full app build.
- APK: NebulaGram-1.0.0-TG-12.10.5-b1000308-arm64-v8a.apk; package app.nebulagram.messenger, version 1.0.0, build 1000308.
- SHA-256: 29985ef03bf64a8afa9890df3d7081b69a0b7fd286a6ffa3089d5b5e7e62bc9a. Signature verified, certificate matches previous builds. Telegram, language identification and NebulaLink arm64 libraries present.
- GitHub artifact ZIP digest and CRC verified independently after retrying interrupted downloads.
- Regression coverage: 256 trailing-control configurations, 2016 title geometry cases, native pre-measure ownership, bounded native sheet lifecycle, draft suspension, title/stories behavior and existing editor/material/translation checks. Android device pixels and touch behavior were not verified: no device is attached.
- The changes in this follow-up are Android-specific. The previously delivered iOS build 59 is unchanged.
