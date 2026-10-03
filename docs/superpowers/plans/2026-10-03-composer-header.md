# Composer accessories and home header repair

> Execute inline. Preserve the existing vendor checkout and unrelated artwork.

**Goal:** Stable text/button placement, an outlined AI composer shortcut opening a Telegram sheet, and a readable full-glass home header.
**Architecture:** Patch the native composer measurement path, keep one owner for accessory insets, and reuse the native attached-sheet lifecycle. Retain Telegram blur capture and theme providers for the header.
**Tech Stack:** Java, Android Views, Telegram BottomSheet/ActionBar and blur3; ordered Android patches.

- [ ] Reproduce the accessory collision in the native measure/layout paths. Apply shortcut margins before measurement, reserve the emoji/gift slot and remove layout-time margin writes; keep recording/editing states native.
- [ ] Add a 24dp outlined vector and use a native attached sheet for message tools, preserving cancellation, draft application and language/settings routes.
- [ ] Remove inherited title shrinking in the custom home toolbar and draw its complete background with the existing liquid-glass factory.
- [ ] Add regression coverage for repeated text/layout changes and header visibility. Run affected existing checks, apply the complete patch series and build the APK.
- [ ] Download and validate the exact APK, then record build/device-test boundaries.
