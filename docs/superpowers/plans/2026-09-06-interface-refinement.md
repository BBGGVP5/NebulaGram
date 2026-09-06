# Interface refinement implementation plan

**Goal:** Address the supplied device regressions and add the requested appearance, search, portability and AI controls.

**Architecture:** Keep settings and small rendering helpers in the Android overlay. Change Telegram integration points through patch 0052, generated against the existing patch series. Use isolated previews, shared geometry and explicit preference keys. Keep AI credentials in Android Keystore encrypted storage, outside exported preferences.

**Tech stack:** Android Java, Telegram views, Android animation and Storage Access Framework, HTTPS JSON APIs.

- [x] Repair preview measurement, profile/folder samples, tab editor gear, row icons and flags.
- [x] Repair tabs and attachment alignment, popup width and message anchoring.
- [x] Restore theme/header behavior, notification fallbacks, phone spoiler and native switch dimensions.
- [x] Add home title centering, animated capsule/popups, avatar menu and own-message double-tap actions.
- [x] Add icon pack selector and avatar rounding with working previews.
- [x] Add settings export/import, report/restart menu and searchable settings index.
- [x] Add provider/key/model/prompt AI settings and user-initiated text actions.
- [x] Compile Java, verify patch application and run relevant behavior checks.
- [ ] Verify visual behavior on an Android device; no device was connected during this implementation.

Main files: `NebulaSectionFragment`, `NebulaSettingsFragment`, `NebulaControlsPreview`, `NebulaTabsEditor`, `NebulaAppearance`, `NebulaRow`, `NebulaTheme`; native `ChatActivity`, `ChatAvatarContainer`, `ActionBar`, `GlassTabView`, `SettingsActivity`, `NotificationsController`, search adapters and settings cells. New helpers separate settings transfer, AI transport/secrets/UI, animations and avatar geometry. Each feature is checked before integration; final validation records device limitations explicitly.

Validation on 2026-09-06: `:TMessagesProj:compileStandaloneJavaWithJavac` succeeded against the assembled Telegram tree. All 47 patches applied to 51 pristine upstream files, and the resulting Java matched the compilation tree. Navigation, theme restoration, chat layout, native header geometry, popup geometry, folder animation/title timing and AI protocol checks passed. The popup check covers 1,080 screen/message/menu combinations. AI fixtures exercise all four transports, UTF-8, model pagination, parsing, cancellation, redirects and errors; no paid API account was used.

Device follow-up should cover the reported notification-entry rendering issue, first-layout phone spoiler, native switch sizes, popup/reaction spacing and avatar/capsule animation. These were addressed in code but are not visually verified on the user's device. Notification photos have an initials fallback when the image file is unavailable. The icon selector contains the bundled Telegram, iOS Outline and Solar assets; it does not claim to install the external packs pictured in the reference.
