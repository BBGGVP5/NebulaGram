# Drawn menu material, advanced shortcut and status bounds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Derive foreground from the material actually installed on a popup, relocate the shortcut, and position sending/recording indicators next to centered status text.

**Architecture:** Expose the tint used by BlurredBackgroundDrawable and let menu rows resolve the nearest installed material (skipping transparent inner pages). Keep original icon state and preference; move its card to nebulalink.advanced. Fix status drawables which ignore Drawable bounds rather than compensating with arbitrary header margins.

**Tech Stack:** Android Java, Python/JVM regression harnesses, pinned native patches, offline Gradle.

### Menu material
- [x] Add a regression in scripts/check-menu-colors.py: dark material with a light provider must produce white text/icons/subtitle, inverse case black; transparent nested popup inherits outer material. Demonstrate failure on old code.
- [x] Modify NebulaMenuStyle.java to resolve drawnTint(view, provider) from actual blurred or fallback material; use it for all row/text colors. Add getBackgroundTint() in native blur3/drawable/BlurredBackgroundDrawable.java exposing exactly backgroundColor, without recomputing theme.
- [x] Run python scripts/check-menu-colors.py build/final-verify-0904/tree; expect all contrast, material mismatch and native draw tests PASS.

### Settings
- [x] Add SCREEN_ADVANCED = "nebulalink.advanced" to NebulaMenuFragment.java; attach shortcut card only there. Rename Russian/English title to "Кнопка NebulaLink" / "NebulaLink button" in NebulaLinkShortcut.java and search; route search to SCREEN_ADVANCED.
- [x] Update scripts/check-link-shortcut.py to assert home does not own the card and the advanced route/name match. Preserve home_nebulalink preference and shield previews.

### Status positioning
- [x] Inspect SendingFileDrawable, RecordStatusDrawable, RoundStatusDrawable, PlayingGameDrawable draw methods; add missing bounds translations, preserving paths already using bounds.top. Leave TypingDots/ChoosingSticker implementations that already honor bounds alone.
- [x] Add scripts/check-status-bounds.py: execute actual native draw methods with fake Canvas at origin and shifted bounds, asserting identical drawing translated by (left,top), balanced canvas state, and unchanged intrinsic sizes.

### Verify and deliver
- [x] Package native edits as patches/android/0065-drawn-menu-tint-and-status-bounds.patch from 0064 baseline. Verify complete sequential patch series and native/overlay compile-tree equality.
- [x] Run 17 UI/JVM scripts, offline :TMessagesProj:compileStandaloneJavaWithJavac, and git diff --check. Include new status test in Android workflow.
- [x] Commit and push only scoped changes; report device visual validation separately. No physical device was available in the prior turn.

Execution inline; the referenced execution skill is unavailable. Screenshot regressions are evidence that prior mocked color tests were insufficient; do not claim physical reproduction without a device.

## Verification outcome
The added installed-material mismatch test failed on the previous foreground policy. The native coordinate regression failed on all four status drawables before the bounds fixes and passes afterward (48 cases). All 17 scripts pass, with 90 translucent palette cases, 30 nested row cases, source-material/host mismatches, transparent pages, custom account text and source-theme refresh covered. All 60 patches apply sequentially; all 63 reconstructed native Java files and overlay Java match the compile tree. Offline Java/resource compilation succeeded in 2m52s.

`prepare` now preserves an already-installed Nebula menu Material and refreshes it from its own color provider rather than assigning the popup container provider. Row/text contrast reads the same backgroundColor consumed by the renderer. The advanced shortcut route and search use nebulalink.advanced and the persisted preference is unchanged.

Not verified on a physical Android device: screenshot acceptance, OEM rendering, APK packaging. Source/JVM regressions are not a claim that the screenshots were reproduced on hardware.
