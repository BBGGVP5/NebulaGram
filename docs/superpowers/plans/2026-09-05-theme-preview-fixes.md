# Theme and settings previews implementation plan

**Goal:** Restore colors immediately when Material You is disabled, keep the switch readable, and show the real Telegram composer controls and bottom tabs in settings.

**Architecture:** Keep changes in the Android overlay. Rebuild settings content atomically when the palette changes, preserve accent backups per Telegram theme/accent, and use native GlassTabView/MainTabsLayout rather than canvas approximations. Composer preview uses the same native icons, glass providers and 44dp/6dp geometry as the real composer without starting a chat or opening the keyboard.

**Tech Stack:** Android Java, Telegram native views, SharedPreferences, Gradle.

- [x] `NebulaTheme.java`: capture the dynamic-palette choice in each palette instance; key accent backups with `theme.getKey()` and `accent.id`, migrate the existing backup and restore all modified accents on disable. Reset the apply throttle on an explicit toggle.
- [x] `NebulaSectionFragment.java` and `NebulaSettingsFragment.java`: rebuild card content and repaint the root/action bar together after a palette change, preserve scroll offset and refresh when returning to settings.
- [x] `NebulaSwitch.java`: use `onSurfaceVariant()` for the off thumb and outline rather than the low-contrast surface outline.
- [x] `NebulaTabsEditor.java`: embed the same `MainTabsLayout` and `GlassTabView` factories as `MainTabsActivity`, native glass background, selected Chats pill and native avatar. Keep tap/drag organization and compute hit targets from actual child bounds.
- [x] `NebulaComposerPreview.java`: use native attachment/microphone/emoji controls, real localized message hint and native glass surfaces. Select three separate 44dp surfaces with 6dp gaps or the unified native panel from the existing preference.
- [x] Add a focused accent regression harness exercising enable/disable, restart and theme switching. Run it plus Java compilation in `build/final-verify-0904/tree`, then push once as BBGGVP5. Confirm the APK workflow starts; the user downloads the artifact themselves.

No device is attached; compilation and source-level regression checks do not establish pixel-perfect rendering on a phone.

Validation: the previous controller fails the new restoration regression; the updated controller passes 26 checks. All 39 Android patches apply to 33 pristine upstream files. The final prepared Android tree compiled successfully in 4m 29s. Native tab avatar positioning now targets BackupImageView when labels are hidden.
