# Menu resume verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the interrupted menu/back-counter changes without losing native Telegram edits on the next clean build.

**Architecture:** Preserve existing overlay edits. Compare the saved native pre-edit files against a fresh application of patches 0001–0061, export the reviewed delta as 0062, then compile exactly the reproduced sources. Do not include the unrelated motion-film assets in the code commit.

**Tech Stack:** Android Java, Python source checks/JVM tests, Git patch series, Gradle.

---

### Task 1: Recover and verify

**Files:** `build/menu-touch-0907/before/`, `build/final-verify-0904/tree/`, `patches/android/`.

- [x] Reconstruct touched upstream files in `build/menu-resume-0907/series` using `git show HEAD:<path>` from the pinned Telegram submodule and sequential `git apply`.
- [x] Compare the reconstructed files against the pre-edit backup; stop packaging on any mismatch.
- [x] Run the existing checks, including `python scripts/check-menu-colors.py`, and repair test doubles only where the real Android API additions require them.

### Task 2: Complete the shared menu path

**Files:** `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMenuStyle.java`, `NebulaMenuReveal.java`, `NebulaHeaderCounter.java`; new `patches/android/0062-menu-touch-and-panel-state.patch`.

- [x] Verify back width uses the live unread count in all native callers and shrinks when zero.
- [x] Check popup touch/close lifecycle, message-menu anchors and draw-time contrast; remove per-frame allocations where possible without losing theme refresh.
- [x] Export unified native diffs from the verified baseline, not the pristine upstream diff (which would duplicate earlier patches).
- [x] Apply 0062 to the reconstructed tree and compare every patched native file with the compile tree.

### Task 3: Validate and deliver

**Files:** `scripts/check-menu-state.py`, `.github/workflows/android.yml`, this plan.

- [x] Add runnable regression coverage for shrinking counter, interrupted width transitions, anchor reuse and native integration.
- [x] Run `python scripts/check-java.py` and all local UI/JVM regression checks against the reconstructed tree.
- [x] Copy verified native sources and current overlay into the existing configured Gradle tree; run `:TMessagesProj:compileStandaloneJavaWithJavac` offline.
- [x] Run `git diff --check` and review staged changes; staged overlay and clean reconstructed native sources match the successful compile tree.

Delivery: commit this verified continuation in Russian and push without waiting for external CI; record the actual commit/push outcome in the final response.

Execution continues inline: the referenced superpowers execution skills are not installed. The larger earlier plan remains the backlog for requirements not covered by this recovery.

### Added request: native video-message recording icon

- [x] Add regression assertions for `input_video` and `input_video_pressed` across all active/preview packs and nested resource wrappers; observe the failure with the old mapping.
- [x] Remove only these two substitutions from `NebulaIcons.java`; retain video-call icons and microphone mappings. Run navigation and appearance checks successfully.

Validation (2026-09-08): all 14 Android workflow regression scripts passed locally. The existing Java heuristic reports baseline wildcard-import warnings; a per-file comparison against HEAD found no new unresolved names or duplicate locals. Native source reconstruction and overlay source comparison both match the configured compile tree. The new width constraint is included in 0062.

Final compilation passed, including the restored video-message icons: `BUILD SUCCESSFUL in 22s`, 24 tasks (2 executed, 22 up-to-date). APK assembly, installation and device rendering were not run.
