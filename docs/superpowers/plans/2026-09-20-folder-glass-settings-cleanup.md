# Bottom-folder glass and settings cleanup

**Goal:** Capture real chat content behind bottom folders and simplify native settings while preserving interactive previews.

**Architecture:** Extend Telegram's existing bounded bottom blur capture region to contain the actual folder view. Use a dedicated translucent material for bottom folders and the same material with captured sample rows in their preview. Remove repeated section hero cards; use consistent custom line icons on the existing colored tiles and quiet group labels on Android and iOS.

**Tech stack:** Android Java/vector drawables, ordered Telegram patches, UIKit, native SDK checks.

- [x] Patch `DialogsActivity.blur3_InvalidateBlur` to union the translated folder bounds (with blur margin) into the existing second capture region, including when the main bar is disabled. Exclude bottom folders from top capture height.
- [x] Add `NebulaFolderGlass` and apply it only to bottom glass folders; retain normal top folders, ordinary/minimal styles and reduced-effects fallback.
- [x] Update `NebulaFoldersPreview` to capture its sample chat rows, excluding its own tabs, with matching material and no bitmap readback.
- [x] Remove duplicate hero headers from Android sections/design; reduce AI/privacy intro to plain summary and status. Preserve every existing preview and setting handler.
- [x] Keep colored row icon tiles and replace their glyphs with consistent 24dp custom vectors; simplify matching iOS icon/header presentation.
- [ ] Run patch application, extracted blur-region geometry cases, existing folder/runtime/settings regressions, AAPT2 resource compile and local Android Java compilation where available. Run real UIKit SDK typechecks in macOS CI.
- [ ] Push scoped changes and verify APK and IPA build dispatch. Report source/SDK checks separately from device visual validation.

## Checks so far

All 101 ordered Android patches apply against the pin (the new patch passes strict whitespace checks; older patch whitespace is retained). 432 production blur capture cases, 264 folder geometry cases, 1001 avatar-transition frames, 24 clipping cases, existing settings state/layout checks, and 1080 appearance geometry cases pass. All 17 iOS patches apply. Colored tiles are preserved by explicit presentation guards. `design/settings/icons-preview.png` renders the actual shared glyph paths, not an app screenshot. Native Android compilation and macOS SDK checks are the remaining build gates.
