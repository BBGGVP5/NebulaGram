# Appearance controls implementation plan

**Goal:** Make chat previews match live controls, expose appearance choices, and fix icon regressions.

**Architecture:** Keep persistent preferences and reusable preview/rendering helpers in the Android overlay. Add a sequential native patch for Telegram layout, message menus, folders and profile hooks; never edit the vendor baseline. Reuse Telegram's blur, message cell, image receiver and menu actions.

**Tech stack:** Java, Android Canvas/Views, Telegram native UI, XML vectors, Python regression checks, Gradle Java compilation.

## 1. Header and glass
- [x] Replace the incorrect rectangular disabled-header preview with the actual native three-surface layout: back, avatar/title, menu. Retain optional centred title/avatar-right layout.
- [x] Add unread-chat count and iOS count preferences to `NebulaAppearance.java`; paint the count on the real back button and in `NebulaPreview.java`, using account notification totals.
- [x] Add a highlights preference used by glass rendering and previews.

## 2. Controls and icons
- [x] Extend `NebulaSwitch.java` with Material, iOS, One UI and Android shapes, plus on/off samples. Keep row touch targets unchanged.
- [x] Add original iOS-inspired vectors and map only matching semantic controls in `NebulaIcons.java`. Keep native emoji/stickers, file status glyphs and delivery marks intact.
- [x] Restore settings avatar/fallback gear in `GlassTabView.java`; centre static bot/wallet artwork within its native slot.
- [x] Use the short Russian label «Иконки iOS» and description «Заменить иконки на iOS».

## 3. Settings sections and previews
- [x] Split folders, messages, profile, switches and chat menu actions from the existing general sections in `NebulaSectionFragment.java` and `NebulaSettingsFragment.java`.
- [x] Give folders, message and profile choices theme-aware previews with the current account avatar/name. Keep the bottom-tab organizer separate.
- [x] Expose folder counters/title/style/outline, divider removal, message reply presentation and profile presentation with working native hooks.

## 4. Message menu
- [x] Add blur preference and a dependent menu-below-message preference. Use Telegram's existing scrim bitmap generation and animation cleanup.
- [x] Calculate popup/message positions within usable screen bounds, lift only the selected scrim copy, and reset when the menu dismisses.
- [x] Expose optional native chat menu actions without disabling access to chat settings.

## 5. Verification and delivery
- [x] Add behavior checks for preference dependencies, icon exclusions, switch geometry and popup bounds.
- [x] Apply the complete patch series to pristine files and compare with compile sources.
- [x] Compile `:TMessagesProj:compileStandaloneJavaWithJavac` and run affected regression checks.
- [x] Commit and push; verify Android workflow queued or running.

Validation: 648 header measurements, 240 camera-grid cases, 24 status-icon cases, 72 composer layouts, 1080 popup combinations, navigation/icon restoration, palette restoration, folder timing and animation. All 46 patches apply to 42 pristine files. Original vectors rendered and inspected. New APK has not been checked on a phone: ADB currently reports no connected device.

Execution stays in this task. Existing user instructions authorize implementation and publishing the changes to the repository.
