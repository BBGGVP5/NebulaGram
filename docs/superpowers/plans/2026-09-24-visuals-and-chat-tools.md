# Android visual fixes and chat tools implementation plan

**Goal:** Repair video popup composition, bottom navigation geometry and sheet glass; replace schematic onboarding with illustrated feature previews; add settings links and missing chat tools.

**Architecture:** Keep native Telegram media rendering and route menus through its existing blur factory. Apply one sheet material through BottomSheet's background without changing its layout. Keep tools account scoped and use the existing user-configured AI connection.

**Tech Stack:** Java overlays, ordered upstream patches, Android Canvas/bitmap art, native Android speech, app-private local storage and existing encrypted AI credentials.

## Rendering
- [x] In ActionBarMenuItem, use `NebulaMenuBackdrop.attach(...)` only when `subMenuFactory == null`; wire PhotoViewer to its existing video-aware factory.
- [x] Implement a BottomSheet background wrapper preserving original padding and alpha, using `NebulaSheetSurface` for the drawn bounds and original drawable for unsupported devices.
- [x] Remove hardware-layer caching of the live MainTabsLayout backdrop and clear press/lens/width state on detach. Device rendering remains unverified.
- [x] Compile changed upstream sources using `:TMessagesProj:compileStandaloneJavaWithJavac --offline`.

## Welcome
- [x] Replace flat skeleton drawings in NebulaIntroArt with readable miniature native chat/settings previews, floating labels, original astronaut art and dimensional cards.
- [x] Reduce heading size and vertical empty space; use page dots above the primary action and support swipe navigation.
- [x] Keep descriptions limited to implemented features. Check Russian and English and compact heights.

## Settings links
- [x] Add stable section/row routes with `tg://settings/nebula?...`; long press a row to copy, route through LaunchActivity and the existing settings section focus support.
- [x] Reject unknown sections/oversized parameters; navigation must never mutate a setting.

## Chat tools
- [x] Add native text-to-speech with cancel/cleanup and message-menu action.
- [x] Add text translation through configured AI and a result screen; expose provider prerequisites and errors.
- [x] Add account-scoped task list, message-to-task entry, description/completion/reminders.
- [x] Add transcription of explicitly selected downloaded voice media through supported provider; bound upload and cancellation.
- [ ] Add per-chat protection covering direct open, resume, notification previews and search routes.
- [x] Add keyword filtering with visible reveal controls.
- [ ] Audit ten-account Java/native allocations and cloud settings synchronization before enabling either.
- [x] Retain existing icon packs, story options and project support. Keep advertising unchanged.

## Verification and delivery
- [x] Run settings contract/localization checks and patch-apply checks.
- [x] Run focused logic tests and Java compilation. Clearly distinguish compile verification from device verification.
- [ ] Commit only task files, push authorized changes and provide CI link. User will install independently; do not wait for phone.

Remaining: per-chat passwords and automatic cloud sync are not implemented; see docs/ANDROID-CHAT-TOOLS.md. The ten-account singleton check passed independently. No device installation or provider request with a real key was performed.
