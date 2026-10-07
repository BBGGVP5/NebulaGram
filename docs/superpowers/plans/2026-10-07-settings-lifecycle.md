# Settings lifecycle and navigation cleanup

**Goal:** Stop emoji disappearing/freezing after page return, provide a sharp shield, complete localized tools/tasks screens, and make the settings hierarchy coherent.

**Architecture:** Own the emoji ImageReceiver directly and reload it for every attachment. Keep a visible fallback until an actual decoded frame exists. Use a scalable shield drawable rather than enlarging a small emoji-sheet tile. Retain account isolation and all existing actions/settings. Execute inline and preserve the modified vendor checkout.

**Tech Stack:** Android Java views, Telegram ImageReceiver/Lottie/video decoder, vector Canvas paths, current settings components, executable JVM lifecycle fixtures and Android CI.

### Emoji lifecycle and sharp fallback

- [x] Replace `NebulaAnimatedEmoji.java`'s nested RLottieImageView with an owned ImageReceiver; bind on attach, clear/reset on detach, restart after layout, and guard posted callbacks by attachment generation.
- [x] Keep fallback until `hasBitmap()` reports a real frame. Draw the receiver even while decoding without presenting the thumbnail. Test actual detach cache clearing, same-size page reentry, pending frame decode, reduced motion and viewport changes.
- [x] Add `NebulaShieldDrawable.java` with normalized vector paths and gradient paints for the silver/red shield. Use it as the large shield fallback; preserve other native/system glyphs and server animations.

### Layout, localization and tool presentation

- [x] Add localized preset-role display names/descriptions without changing IDs, stored custom roles or request prompts. Use the same labels in AI settings and role selection.
- [x] Add top emoji introductions to tasks, task editor and full-page tools. Use grouped action rows with emoji on the tools page; retain a compact responsive grid for the message sheet.
- [x] Add a flat grouped-field helper to the service/role editor to remove stacked rounded outlines; use separate subdued model-list action and primary Save.

### Settings navigation

- [x] Put chat appearance, messages, profile, AI, translator, text tools and tasks inside one Chat and tools category, with headings separating settings from tools.
- [x] Move browser configuration into General. Keep About as the final root row and place support/donation inside About with updates/build/community information.
- [x] Retain direct settings search routes and privacy/account-specific behavior.

### Verification and delivery

- [x] Extend executable emoji fixtures with real image-release behavior, first-frame availability and reattachment; add role localization/persistence checks and settings hierarchy guards.
- [ ] Complete a fresh Android CI build after the successful SDK typecheck, pinned patch reconstruction and local regression run.
- [ ] Verify APK source/version/certificate and provide it with concise changes and physical-device test limits.

Local verification: 86 checks passed, including receiver-release/page-reentry/first-frame/stale-callback fixtures, role locale and custom-data preservation, organization guards and 432 tool-grid geometry cases. Fourteen helpers compile against the Android SDK and native signatures; all 177 pinned patches reconstruct. C++ slot checks are syntax-only locally and executable in Linux CI. ADB reports no attached device.
