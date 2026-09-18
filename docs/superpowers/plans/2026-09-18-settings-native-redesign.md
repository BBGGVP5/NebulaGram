# Nebula settings: native-style redesign and localization repair

**Goal:** Repair Android 12.10.3 localization and restyle every Nebula settings route using shared components without replacing Telegram navigation or changing preferences.
**Architecture:** Include overlay string files in Telegram's existing binary localization tasks. Own the action-bar/content geometry inside a settings-only root container. Restyle common cards, rows, compact introductions, buttons and field/selection controls; keep native dialogs and all existing routes.
**Tech stack:** Java/Android views, Kotlin Gradle plugin patches, Python source/resource regression tests.

- [x] Add a failing resource coverage test for all overlay `strings*.xml` inputs, both base and translated files.
- [x] Patch `buildSrc/.../TelegramBuildAppPlugin.kt` and `TelegramBuildPlugin.kt` to include `strings*.xml`, not just `strings.xml`; preserve locale grouping, IDs and shrinker pipeline.
- [x] Add `NebulaSettingsLayout.java`: embed the existing action bar and measure content below its actual height. Apply to Settings, Section, Menu, Servers, Subscriptions, Privacy, AI, Design and Updates fragments; do not touch login/onboarding or composer work.
- [x] Restyle `NebulaCard`, `NebulaRow`, `NebulaSettingsHero`, `NebulaButton`: regular readable titles, compact icons, sentence-case headers, modest radii and flat theme-aware surfaces. Retain active state semantics and wrapping text.
- [x] Remove the redundant home marketing banner. Keep the grouped navigation, all settings sections and routes.
- [x] Apply matching spacing and hierarchy to NebulaLink connection, server/subscription lists, AI pages, privacy and appearance controls. Keep Nimbo tests/state flow and user customization intact.
- [x] Run resource and geometry/style source guards, existing regression tests and the full ordered patch application check. No APK/IPA/CI builds; report device QA as unavailable if there is no emulator/phone.

## Scope
The supplied regression screenshots are Android. This pass changes the entire Android Nebula settings surface through shared components; Telegram's own profile/settings page only receives the corrected Nebula label via localization. iOS native navigation is untouched.

## Verification

- Localization test failed on `values/strings_nebula.xml` before patch 0098; after the patch all 10 overlay resource files and core RU/EN labels pass.
- Actual `NebulaSettingsLayout` executed against minimal Android measurement stubs: 36 size/bar-height combinations plus hidden-bar case passed. All nine settings routes use the wrapper with the correct context variable.
- Existing settings-design (600 AI page transitions), NebulaLink layout/state and header-spacing guards passed.
- All 93 Android patches apply to pin `9552e5541e1274b9557c9832b204dbfcaf44b3dc` in a disposable tree.
- Legacy `check-java.py` reports existing wildcard-import false positives; output compared against HEAD has no new findings. It is not a compiler check.
- No device was attached (`adb devices`). No Gradle, APK, IPA or CI build was started for this pass; full runtime/visual verification remains outstanding.
- The unrelated pre-existing `NebulaComposerStyle.java` modification is left untouched.
