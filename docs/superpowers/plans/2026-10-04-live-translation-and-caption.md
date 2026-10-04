# Live translation and caption controls implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Translate all eligible visible messages reliably, give typing priority and automatic reversible replacement, fix overlapping attachment actions, and slightly shorten toolbar bubble animations.

**Architecture:** Keep translation consent per account and dialog. A bounded visible-message scheduler replenishes work on completion; translation requests split long text at safe boundaries and yield the Nano inference slot between chunks. Draft replacement has an independent original-text state and a persistent compact panel. An explicitly selectable ML Kit language-pack engine provides ordinary on-device translation without replacing the selected AI provider.

**Tech Stack:** Java, Android views and SDK, ML Kit Translation 17.0.3 and existing Language ID 17.0.6, existing Gemini Nano Prompt beta4, deterministic JVM doubles, pinned Telegram patch series and GitHub Actions.

---

Execution stays in this authorized chat. No separate agents or execution-choice question. Preserve the user's modified Android vendor checkout.

### Task 1: Translation scheduling and engines

**Files:** `NebulaAutoTranslate.java`, `NebulaTranslationClient.java`, `NebulaTranslationText.java`, `NebulaLocalTranslation.java`, `NebulaNanoInferenceGate.java`, `NebulaNanoAi.java` under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`; `scripts/check-auto-translation.py`, `scripts/check-translation-text.py`, `scripts/check-nano-inference-gate.py`.

- [x] Exercise more than 22 visible messages with two blocked workers; require every eligible message to finish after workers resume. Change the source, model and account while requests wait and reject stale responses.
- [x] Retain already-running work across scrolling, cancel obsolete waiting work, and replenish the bounded worker queue from the current visible snapshot when a request completes. Sort new visible work by source length.
- [x] Split Nano translation input into at most 900 UTF-16 units without separating surrogate pairs; preserve whitespace around chunks. Translate each chunk through the selected engine, then apply the complete result only.
- [x] Prioritize interactive Nano waiters while maintaining one inference owner. Retain existing cancellation and idle-close ownership checks.
- [x] Add a separate `live_translation_local` engine setting. ML Kit detects the source language, prepares the requested language pack, reuses leased clients with idle cleanup, and never falls back to Telegram or silently uploads local text.
- [x] Run `python scripts/check-auto-translation.py`, `python scripts/check-translation-text.py`, `python scripts/check-nano-inference-gate.py`, `python scripts/check-nano-session.py`.

### Task 2: Reversible automatic draft translation

**Files:** `NebulaDraftTranslation.java`, new `NebulaDraftOriginal.java`, `NebulaTranslationSettings.java`, `NebulaTranslationFragment.java`, `NebulaSettingsSearch.java`; `scripts/check-live-translation.py`, new `scripts/check-draft-original.py`.

- [x] Test automatic replacement, appending/prepending text, returning the full original, A→B→A edits, native composing spans, language/model changes and empty/send cleanup.
- [x] Keep original and displayed text separately. Suppress requests created by our replacement; accept results only for the exact unchanged draft and configuration. Preserve ongoing IME composition and cursor selection.
- [x] Replace the tall recreated popup with one compact nonfocusable panel: language/status row, original preview and a 44 dp return-original action; no repeated fade animation on each phase.
- [x] Make automatic replacement the default requested by the user, retain optional preview mode and reduce the default debounce to 150 ms. An explicit stored delay remains configurable.
- [x] Run `python scripts/check-live-translation.py`, `python scripts/check-draft-original.py` and Android SDK typechecking of the actual UI/client classes.

### Task 3: Attachment spacing and menu timing

**Files:** new `patches/android/0177-live-translation-caption.patch`, native `CaptionPhotoViewer.java`, `ChatActivity.java`, `TMessagesProj/build.gradle`; `NebulaMenuStyle.java`, `NebulaMenuReveal.java`, `scripts/check-composer-shortcut.py`.

- [x] Reserve the full native confirmation hit area plus a gap before positioning the AI action; keep text, AI and confirmation rectangles disjoint in compact/multiline/top-caption states and all densities.
- [x] Refresh the visible translation snapshot after translation changes message height. Preserve grouped media and pinned-message handling.
- [x] Shorten toolbar opening from 750 to 650 ms and closing by 15 percent, retaining the verified normalized FlClash spring trajectory and native message-context animation.
- [x] Run `python build/verify-upstream-12106.py`, `python scripts/check-composer-shortcut.py build/android-upstream-12106`, `python scripts/check-menu-reference.py`, `python scripts/check-menu-feedback.py build/android-upstream-12106`, `python scripts/check-menu-api.py`.

### Task 4: Publish and verify the Android release

**Files:** `.github/workflows/android.yml`, `docs/USER-CHANGES.md`, this plan.

- [x] Add new executable checks to CI and document the actual engine choice, first language-pack preparation, automatic replacement and original return.
- [ ] Stage only this task's files, commit and push the final changes to the authorized branch and main. Do not stage `vendor/telegram-android`.
- [ ] Wait for the main Android build, verify its source SHA, artifact digest, package and signing certificate, then provide its APK. Report physical-device testing accurately; ADB currently has no device.
- [ ] Keep the previously verified iOS build 74 available. Do not label Android-specific new behavior as already ported to iOS.
