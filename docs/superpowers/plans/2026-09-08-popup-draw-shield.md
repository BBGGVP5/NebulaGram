# Popup draw-time palette and NebulaLink shield Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep menu rows readable through the actual Android draw lifecycle and move the shield shortcut setting into NebulaLink.

**Architecture:** Apply the shared palette before display-list recording and at native row drawing, with automatic platform recoloring disabled for this app-managed popup. Share one shield vector between live control and previews. Preserve the existing preference and tunnel actions.

**Tech Stack:** Android Java, XML vector drawable, Python/JVM checks, sequential upstream patches, offline Gradle.

### 1. Lifecycle regression and menu fix
- [x] Extend `scripts/check-menu-colors.py` with actual native integration checks: pre-draw listener registration/removal, `styleRows(this, resourcesProvider)` before row rendering, and `setForceDarkAllowed(false)` guarded by SDK 29.
- [x] Add popup pre-draw palette refresh and row draw-time repair in `ActionBarPopupWindow.java` and `ActionBarMenuSubItem.java`; preserve semantic colors through `NebulaMenuStyle.foreground` and style subtext too.
- [x] Run `python scripts/check-menu-colors.py build/final-verify-0904/tree` (expected PASS after implementation; fails before hooks exist).

### 2. Shield and settings
- [x] Create `platform/android/overlay/TMessagesProj/src/main/res/drawable/nebula_link_shield.xml` as a 24dp outline shield. Use it in `NebulaLinkShortcut` for the home view and settings row; show a check for ON, spinner for CONNECTING, exclamation for ERROR, empty shield for OFF.
- [x] Replace four cramped horizontal labels with a 2x2 preview grid; share `NebulaLinkShortcut.onDraw` so preview matches the live icon and never calls core networking.
- [x] Remove `NebulaLinkShortcut.addSettings(content)` from `NebulaSectionFragment.buildAppearance`; add it to `NebulaMenuFragment.rebuild` only for `SCREEN_HOME`, even if schema has not loaded.
- [x] Route the search entry to `new NebulaMenuFragment()` and update `scripts/check-link-shortcut.py` assertions for location, vector and preview layout.

### 3. Delivery
- [x] Package native deltas as `patches/android/0064-popup-palette-lifecycle.patch` from the 0063 baseline; reconstruct the full series and compare native sources.
- [x] Copy overlay Java/resources and run offline `:TMessagesProj:compileStandaloneJavaWithJavac`, then all 16 UI/JVM checks and `git diff --check`.
- [x] Commit/push only scoped changes. Report that no Android device is attached and device rendering is not verified; do not equate mocked checks with screenshot validation.

Execution stays inline; the referenced superpowers execution skill is unavailable.

## Verification outcome
All 16 regression scripts pass, including execution of the native row dispatch method in JVM view fakes with a dark popup/light row provider and late black color replacement. The integration checks confirm pre-draw listener lifecycle and Force Dark opt-out. All 59 patches apply in order; 59 reconstructed native Java files, overlay Java and the shield resource match the compile tree. Offline Java/resource compilation succeeded.

The screenshot cause is not reproduced on physical hardware: no device or installed emulator system image is available. These changes harden the actual row lifecycle rather than proving an OEM rendering diagnosis. APK packaging and screenshot acceptance remain unverified.
