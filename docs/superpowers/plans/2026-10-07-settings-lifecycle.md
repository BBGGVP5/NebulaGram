# Settings lifecycle and navigation cleanup

**Goal:** Stop emoji disappearing/freezing after page return, provide a sharp shield, complete localized tools/tasks screens, and make the settings hierarchy coherent.

**Architecture:** Own the emoji ImageReceiver directly and reload it for every attachment. Keep a visible emoji fallback until an actual decoded frame exists. Retain account isolation and all existing actions/settings. Execute inline and preserve the modified vendor checkout.

**Tech Stack:** Android Java views, Telegram ImageReceiver/Lottie/video decoder, native emoji artwork, current settings components, executable JVM lifecycle/geometry fixtures and Android CI.

### Emoji lifecycle and sharp fallback

- [x] Replace `NebulaAnimatedEmoji.java`'s nested RLottieImageView with an owned ImageReceiver; bind on attach, clear/reset on detach, restart after layout, and guard posted callbacks by attachment generation.
- [x] Keep fallback until `hasBitmap()` reports a real frame. Draw the receiver even while decoding without presenting the thumbnail. Test actual detach cache clearing, same-size page reentry, pending frame decode, reduced motion and viewport changes.
- [x] Keep emoji visible during loading; remove the temporary vector shield substitute in response to the follow-up request.

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
- [x] Complete a fresh Android CI build after the successful SDK typecheck, pinned patch reconstruction and local regression run.
- [x] Verify APK source/version/certificate and provide it with concise changes and physical-device test limits.

Local verification: 87 checks passed, including receiver-release/page-reentry/first-frame/stale-callback/explicit-replay fixtures, role locale and custom-data preservation, organization guards and 432 tool-grid geometry cases. Profile controls and emoji helpers compile against the Android SDK and native signatures; all 178 pinned patches reconstruct. Shape tests cover 162 rectangular/capsule/rounded sources and clipped final menu positions. C++ slot checks are syntax-only locally and executable in Linux CI. ADB reports no attached device.

### Follow-up from screenshots

- [x] Remove the vector shield substitute; use emoji for the mentioned settings/profile controls.
- [x] Rewind loaded Lottie/video on page resume and recover stopped infinite-loop Lottie without the receiver's one-shot restart path. Keep independent decoder state per view.
- [x] Add the NebulaLink introduction above connection controls.
- [x] Replace glossy profile button gradients with a subdued uniform glass surface, preserving actions and accessibility.
- [x] Morph from the source control's bounds and rounded outline on opening and closing. Make the constrained trajectory meet the exact native final position, including partly offscreen popup padding.
- [x] Verify the new lifecycle/geometry regressions, build and deliver a fresh APK containing these follow-up changes. Build 388 passed for the preceding source only.

Delivery: Android build 389 / versionCode 1000389 succeeded in run 37638923283 from source `837fd8991c4cc7bd19259ff904ee14389a952811`. Settings contract run 37638923250 succeeded. The downloaded arm64-v8a APK has SHA-256 `29f5438057dab71e77280301d97ae4eb3859f851528f33db132a1509a124fecb`, package `app.nebulagram.messenger` and the same verified signing certificate as build 385 (`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`). Native library and bundled icon-pack bytes were checked. Details: `build/qa-apk-ui-replay-837fd89/local-verification.json`. No physical-device validation was possible because ADB had no device. iOS 76 remains the previously delivered build; this follow-up contains Android changes.

### Clarification: emoji applies to large page introductions

- [x] Restore the original small icon rendering in settings rows and profile actions. Remove the automatic emoji mapping and profile replacement hook; retain the requested flat profile surface and menu/lifecycle corrections.
- [x] Use Telegram emoji artwork for large page fallbacks, including the General gear, instead of Android's system font. Keep real Telegram Lottie/video animation and remove the synthetic rocking motion.
- [x] Keep the NebulaLink introduction attached during status/schema rebuilds so updates do not interrupt its animation. Replay on navigation return only.
- [x] Verify the scoped rollback, native emoji loading and stable NebulaLink introduction; build and verify a fresh APK.

Clarification verification: all 87 required checks pass locally after reconstructing 178 patches. Updated fixtures require native Telegram glyph preloading, repaint after `emojiLoaded`, cleanup of global/account observers, cached-view replay, unchanged icon-pack resolution and stable NebulaLink hero identity. Production emoji helpers compile against Android SDK and native APIs. Standard Telegram artwork remains visible when the server has no animation for a given emoji; no synthetic animation substitutes for it.

Clarification delivery: Android 391 / versionCode 1000391 succeeded in run 37643386298 from source `1ed25d66ed45fd5afd25fd85ecae457c0873092b`; Settings contract run 37643386283 also succeeded. APK SHA-256: `f250d722249fcbb91e409512eb2e88d5455f74bec6c58796e7c9036237a1850e`. Package, arm64-v8a/native core, icon pack and the existing signing certificate were verified in `build/qa-apk-native-emoji-1ed25d6/local-verification.json`. No ADB device was connected, so physical-device appearance/playback validation remains unperformed.
