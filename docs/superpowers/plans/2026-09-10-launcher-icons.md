# NebulaGram Launcher Icons Implementation Plan

> **For agentic workers:** Execute the steps inline; preserve unrelated workspace changes.

**Goal:** Replace Telegram artwork in Android Chat Settings → App Icon with six NebulaGram variants, including the installed launcher icon.

**Architecture:** Reuse existing activity-alias identities and LauncherIconController, so upgrades retain the selection. Generate legacy and adaptive resources from the existing NebulaGram mark. The selector and manifest reference the same palette/foreground, with dedicated localized names; these original assets do not require Premium.

**Tech Stack:** Android resources/Java, Python/Pillow, Gradle.

---

- [x] Add `scripts/check-launcher-icons.py`: check alias/preview resource agreement, all density sizes, adaptive monochrome, six distinct palettes, RU/EN labels and execute the actual controller using a fake PackageManager through every transition and recovery.
- [x] Add `scripts/gen-launcher-variants.py`: reuse `scripts/gen-icons.py` geometry; generate six square 48dp legacy icons and 108dp foregrounds, adaptive backgrounds/monochrome, and a local contact sheet for visual review. No launcher mask baked into legacy artwork.
- [x] Add `patches/android/0085-nebula-launcher-icons.patch`: replace enum resources/titles and point main/standalone manifest aliases at the new icons. Retain every component key and native switch behavior.
- [x] Add the regression command to `.github/workflows/android.yml` after icon identity checks.
- [x] Apply the new patch to the prepared verification tree, run `python scripts/check-launcher-icons.py build/final-verify-0904/tree`, compile `:TMessagesProj:compileStandaloneJavaWithJavac` and merge standalone resources/manifest. Inspect the contact sheet. Report device testing separately.

Scope: Android's existing Chat Settings selector. No arbitrary image import and no iOS icon picker in this change.

Validation so far: native-controller regression passes all 36 transitions and recovery; patch applies to pristine pinned files; all 75 generated assets reproduce byte-for-byte; visual contact sheet and foreground safe zones checked. Device/launcher-cache testing is not available locally.

Final verification: Gradle `:TMessagesProj:compileStandaloneJavaWithJavac :TMessagesProj_AppStandalone:processAfatStandaloneResources` completed successfully (15m 9s), with the verification-only argument `-PAPP_PACKAGE=app.nebulagram.messenger` matching the existing local Firebase fixture. Log: `build/launcher-icons-compile.log`. Checked all six aliases and enabled defaults in the actual merged standalone manifest. No APK/CI build dispatched.
