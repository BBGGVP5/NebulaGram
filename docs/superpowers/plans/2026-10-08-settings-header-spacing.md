# Consistent settings introductions implementation plan

> **For agentic workers:** Execute this plan inline, task by task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Align the large emoji, heading, description and surrounding spacing on Android settings pages, including NebulaLink.

**Architecture:** Keep `NebulaSettingsHero` as the shared introduction. Use one emoji canvas and typography, reserve two description lines without truncating longer text, and remove the extra section-label top gap immediately after an introduction. Retain native emoji playback and title-collapse anchors.

**Tech Stack:** Java, Android Views, Telegram ImageReceiver, existing Python/Javac checks and Android CI.

---

### Task 1: Shared geometry

Files under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`:

- [x] `NebulaSettingsHero.java`: name the 88/72 dp emoji sizes and 24/15 sp typography; use 12 dp emoji-to-title, 8 dp title-to-description, 16 dp bottom padding. Apply `setIncludeFontPadding(false)` to labels and `explanation.setMinLines(2)` with top/center gravity. Keep text height unlimited for large fonts and long translations.
- [x] `NebulaAnimatedEmoji.java`: for page emoji (`size >= 72`), remove fallback-only inset so the native static and animated drawables use the same canvas. Keep the existing small-icon treatment and receiver lifecycle.
- [x] `NebulaCard.java`: use a private header TextView whose measurement applies 0 dp top padding when its preceding sibling is `NebulaSettingsHero`, otherwise retain 18 dp. Keep 16 dp horizontal and 8 dp bottom padding. This gives NebulaLink the same gap from introduction to first visible setting content.
- [x] `NebulaAiFragment.java`: align the outer introduction's top padding with other pages, changing 8 dp to 12 dp.

### Task 2: Validate and deliver

- [x] Reconstruct the pinned Android source: `python build/verify-upstream-12106.py`.
- [x] Run existing settings/emoji/AI layout checks via `python build/run-extera-regressions.py`, with JDK and NDK environment configured. Run `python build/check-extera-sdk.py`, including the changed card class. Require successful compiler output and no failed regression checks.
- [x] Review `git diff --check` and the scoped diff; preserve the modified vendor checkout and unrelated files.
- [x] Document the change in `docs/USER-CHANGES.md`, commit only the scoped files, push the authorized branches and complete Android CI.
- [x] Download the exact successful APK, verify package, version, ABI, certificate and icon-pack asset; report physical-device validation separately.

No new implementation-mirroring tests are needed for this spacing adjustment. Existing native replay, title, settings, form and full-build checks remain the validation basis.

Delivery evidence: Android 1000404, source `0b0e9a94b5cffa8883572f5bfd59f084d87e53c7`, [CI run 37738790996](https://github.com/BBGGVP5/NebulaGram/actions/runs/37738790996) succeeded. All 88 local regression checks passed; native Telegram entity serialization and formatted translation checks passed. Downloaded APK verified for package, version, arm64 ABI, signing certificate and bundled icon pack. SHA-256: `344840848a3f8ffe80895052d7b180fb96f3389daa959747b1e649876b696fe3`. Physical-device UI verification remains unavailable.
