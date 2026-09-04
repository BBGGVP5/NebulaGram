# iOS Chat Chrome Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the optional iOS chat header and composer render as separate, stable controls in every supported chat.

**Architecture:** Keep Telegram's existing controls and blur drawable. Extend the existing overlay and the final Android patch so Saved Messages uses its existing action slot for the bookmark icon, while the composer draws three non-overlapping blur surfaces from one authoritative bounds calculation.

**Tech Stack:** Java, Telegram Android UI, overlay source, quilt-style Android patches, Gradle javac verification.

---

### Task 1: Correct Saved Messages header placement

**Files:**
- Modify: `patches/android/0026-ios-chat-chrome-repair.patch`
- Modify: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaChatStyle.java`

- [x] Replace the Saved Messages three-dot action icon with a sized Saved Messages avatar drawable.
- [x] Hide the duplicate title-area avatar only for Saved Messages while preserving the search action and menu behavior.
- [x] Restore the original avatar/menu layout when the optional header style is disabled.

### Task 2: Make composer islands mutually exclusive

**Files:**
- Modify: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaComposerStyle.java`

- [x] Draw the left attachment/send-as island, centre editor island, and right microphone/video island from clamped, shared bounds.
- [x] Suppress the upstream full-width background whenever the optional composer is active.
- [x] Keep Telegram's original button listeners and microphone-to-video state transitions.

### Task 3: Verify patched sources

**Files:**
- Test: `build/final-verify-0904/tree/TMessagesProj`

- [x] Apply the new patch to the verification tree.
- [x] Run `:TMessagesProj:compileStandaloneJavaWithJavac` using the project Android SDK/JDK configuration.
- [ ] Inspect patch application and commit the verified changes before pushing `main`.
