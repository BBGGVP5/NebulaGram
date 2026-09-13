# NebulaGram Update Sheet Implementation Plan

**Goal:** Replace Android's plain update alert with a branded, readable bottom sheet and a bundled animated Nova emblem.

**Architecture:** Keep NebulaTelegramUpdates as the trusted source/downloader/verifier. NebulaUpdateSheet observes its state while attached, pins the offered version/document/account, preserves rich release notes and never installs automatically. A bounded scrolling body and separate footer keep controls reachable. A small pure prompt policy implements per-account/version 24-hour postponement; native checks still gate lock screen and foreground state.

**Tech Stack:** Native Telegram BottomSheet, Java Android views, existing Nova vector drawable, Python/Java regression harness.

- [x] Add pure `NebulaUpdatePromptPolicy` with same-release/day, new-release, clock rollback and no-release tests in `scripts/check-update-sheet.py`.
- [x] Add `NebulaUpdateMascot`: existing Nova vector, gentle one-shot scale/rotation, stopped on detach; no network sticker dependency or continuous render loop, static under reduced motion/power mode.
- [x] Add `NebulaUpdateSheet`: theme gradient header, version/date/size, existing `NebulaChangelogView`, download/cancel/install/retry controls, release-post link and tomorrow dismissal. Detach updater/account observers; stop interaction when selected account or release changes. Keep errors visible and never bypass NebulaApkVerifier.
- [x] Wire automatic/manual offers to the sheet. Store prompt time only after presentation; use a weak active-sheet reference to prevent stacking. Manual checks bypass snooze. Existing download progress survives dismissal.
- [x] Verify Java compilation, resource references, executable prompt-policy tests and existing updater/verifier tests. Add the regression check to Android CI. No automatic APK/IPA build dispatch; device visual QA remains separate.

Scope: Android's existing updater. No iOS installer support or external machine translation is implied. The mascot is an original bundled code-native Nova sticker-style emblem rather than a third-party Telegram sticker.

Verification: prompt-policy executable cases PASS; existing release/ABI/reupload/APK verifier harness 62 cases PASS; final native Android Java compilation BUILD SUCCESSFUL in 32s (build/update-sheet-verified-javac.log). Native dialog presentation, typography and real download/installer interaction still require device QA. Reminder means the next eligible app opening after 24h, not an OS notification. Ship with [skip ci] per the preceding commit-only instruction.
