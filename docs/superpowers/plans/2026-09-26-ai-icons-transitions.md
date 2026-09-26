# AI, App Icons, and Transitions Implementation Plan

> **For agentic workers:** Execute inline in this task, keeping each subsystem independently buildable and validated.

**Goal:** Add Gemini Nano on-device AI, a choice of remote AI services without Telegram Premium gating, AI conversation history, a first-party launcher icon picker, and selectable fragment transition styles.

**Architecture:** Keep Telegram source changes in ordered patches and NebulaGram behavior in the Android overlay. Extend the current provider/client abstraction for Nano, keep history local and user-controlled, reuse Telegram's launcher aliases and artwork in a NebulaGram-owned picker, and make transition selection a small setting consumed by `ActionBarLayout`.

**Tech Stack:** Java Android overlay, Telegram Android 12.10.5 patch series, ML Kit GenAI Prompt API, Android SharedPreferences/Android Keystore, Python regression checks.

---

## Repository map

- `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAiClient.java` — remote AI transport and model calls.
- `NebulaAiAvailability.java`, `NebulaAiFragment.java`, `NebulaAiSecrets.java`, and `NebulaMessageToolsFragment.java` — AI setup, capability state, tools, and entry points.
- New `NebulaAiHistory.java` — opt-in conversation persistence, listing, and clearing.
- `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaSettingsFragment.java` and `NebulaSettingsLinks.java` — first-party settings routes.
- New `NebulaIconPickerFragment.java` — NebulaGram-owned grid backed by existing `LauncherIconController.LauncherIcon` aliases and bundled images.
- `patches/android/` — ordered Android source changes, including dependency declaration and Telegram AI hooks.
- `NebulaSettingsSearch.java` and the settings schema/check scripts — expose and validate settings.
- `ActionBarLayout.java` in Telegram source via an ordered patch — apply transition preference without changing swipe-back gestures.

## Task 1: Add Gemini Nano as a local AI service

- [ ] Add ML Kit GenAI Prompt dependency through a new ordered Android patch; do not edit the Telegram vendor checkout directly.
- [ ] Extend provider selection with Nano and a model configuration for stable/preview plus default/fast preference.
- [ ] Check runtime availability on a worker thread; expose unavailable, downloadable, downloading, and ready states.
- [ ] Offer the documented one-time AICore model download and run inference entirely on device. Never require or send an API key for Nano.
- [ ] Keep unavailable devices on the existing remote providers with a clear status.
- [ ] Verify through a focused check that Nano requests cannot reach `NebulaAiClient.request()` or the network transport.

## Task 2: Add AI history and Telegram-feature replacement paths

- [ ] Add an opt-in local conversation history setting, history screen, message count, and clear action.
- [ ] Persist only the user's prompts and generated text locally; provide a visible history-off state and do not sync history.
- [ ] Reuse the active service for rewrite/stylize/proofread, summaries, and translation. Keep output preview/editable before applying it.
- [ ] Patch Telegram 12.10.5's AI editor/summary entry points so NebulaGram's selected service is used without Telegram Premium gates, while retaining Telegram behavior when Nebula AI is disabled or unavailable.
- [ ] Add coverage for enabled/disabled routing, no-Premium routing, history toggle, and clearing.

## Task 3: Add the NebulaGram launcher icon picker

- [ ] Add a settings row opening a dedicated grid screen with icon previews, labels, selected state, and a persistent bottom action.
- [ ] Use existing launcher icon aliases and original bundled NebulaGram artwork; keep all NebulaGram icons available without Premium.
- [ ] Apply selection through `LauncherIconController.setIcon` and return to settings without rebuilding the app.
- [ ] Validate that every selectable enum entry has a manifest alias and preview resource.

## Task 4: Add selectable transition animations

- [ ] Add Standard, AOSP/system, and Spring options to NebulaGram settings with a persisted stable enum.
- [ ] Apply the selected style at Telegram fragment transition boundaries while preserving the current transition when disabled, during swipe-back, and under reduced-motion settings.
- [ ] Validate open/close behavior and ensure interactive preview or swipe-back does not become stuck.

## Task 5: Validate and deliver

- [ ] Run `python scripts/check-upstream-series.py android --tree vendor/telegram-android --ref origin/master` to ensure patches apply to the pinned 12.10.5 source.
- [ ] Add and run focused regression checks for Nano routing, history privacy, icon alias completeness, and transition preference behavior.
- [ ] Run the existing settings, AI/message-tools, launcher-icon, and Android regression checks.
- [ ] Commit the overlay and patch changes, push them to the requested Android build, and report the CI status without waiting for the APK if it is still building.

## Verified API boundary

Use the official [ML Kit Prompt API setup and Java guide](https://developers.google.com/ml-kit/genai/prompt/android/get-started), [model selection guide](https://developers.google.com/ml-kit/genai/prompt/android/select-model), and [Gemini Nano Prompt API overview](https://developers.google.com/ml-kit/genai/prompt/android). The documented API requires Android 8.0/API 26+, checks `AVAILABLE`/`DOWNLOADABLE`/`DOWNLOADING`/`UNAVAILABLE`, and lets the app select stable/preview and fast preference where supported. Treat the feature as device-dependent and beta; do not claim support when `checkStatus()` reports unavailable.
