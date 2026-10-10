# Audio setup, settings long press and chat header corrections

> **For agentic workers:** Execute inline under the user's request. Preserve unrelated vendor/design changes. Track native compilation and device acceptance separately.

**Goal:** Make audio setup discoverable, preserve custom long-press actions, expose Nebula transcription without a Telegram Premium upsell, and restore the chat back/avatar material surfaces.

**Architecture:** Settings link binding supplies a fallback listener through NebulaRow instead of replacing feature listeners. Audio choices list saved compatible connections and direct Create OpenAI/Create Gemini actions, with a scoped editor completion callback that selects only the audio connection. Transcription replacement uses the existing native Android hook and a visible toggle in Audio & voices. Restore only chat back/avatar drawable calls; preserve home/shared-header and selection geometry.

**Tech Stack:** Android Java, native Telegram patches, Swift/UIKit for matching audio-setup flow, executable Java listener/route fixtures, XCTest and native CI.

- [x] Identify the screenshot causes in SettingsLinks.bind, saved-only audio choices and native ActionBar draw guards.
- [x] Add fallback long-press binding and run actual listener-state tests: custom before/after bind, repeated rebind, null removal, fresh rows.
- [x] Add visible transcription enable switch and instructions in Audio & voices, both-platform setup entries, direct supported-provider creation with default audio models and a save callback; keep text-service selection unchanged. Empty lists must offer creation, not just Not selected.
- [x] Route the Android native transcription button directly to Nebula when enabled; protected/secret/expired messages retain their exclusions. Expose a separate message context-menu action for discoverability. iOS follows its existing native message-menu entry with an explicit audio toggle.
- [x] Reconstruct the pinned ActionBar, append a small ordered patch restoring back/avatar material drawing with existing bounds/alpha; run actual branch/draw tests and preserve home/selection contracts.
- [ ] Run targeted fixtures, existing audio protocols/services/settings-link checks and all affected ordered patches. Compile final Android/iOS sources, inspect artifacts and update evidence.

The user previously reported the phone unavailable; no device acceptance is assumed. No provider request is required to test setup/routing. Native transcript generation still uses the user's configured OpenAI/Gemini key.


## Source and targeted checks

- Production row listener methods pass repeated binding/custom-before/custom-after/removal tests. Existing settings-root checks pass 36 geometry combinations after configuring the local JDK runtime for the test process.
- Actual audio chooser callbacks pass: Nano-only/empty lists offer OpenAI/Gemini creation, opening setup does not enable anything, explicit enable completes after selecting/saving a compatible service, clearing selection disables replacement, and speech selection is independent.
- Native ActionBar branches pass normal chat, full selection, search, returning from selection and home/shared-header tests; the original bounds and alpha helpers are retained. Android 188 ordered patches apply; iOS 91 patches / 157 paths and 43 mirrored sources pass.
- Existing 42 audio protocol/transport and 20 label assertions plus AI services pass. Added a Swift test for the explicit audio switch and legacy text-AI preference fallback; macOS/native compilation remains pending.


## Build follow-up

- Source 77d7efa: macOS bootstrap 38030935745 passed **144 XCTest cases**. Full device IPA 38030936135 is still packaging.
- Android 38030935744 stopped in the regression suite before compilation: the older avatar-material test still expected its drawable suppressed. Its test contract now expects the restored normal avatar surface and verifies the existing selection/search fade and touch bounds: **12,928 cases**, 101 search/return pairs, 404 Saved title material frames and native selection/footer tests pass locally. No native/runtime source changes were needed for this follow-up.


## Verified Android delivery

- Android run **38033372792** succeeded at **15569c4127660ea5ff7bac552f31bf13a4cb3c21**; this revision changes only the updated regression contract/document after native source 77d7efa. Full interface/regression suite, Java/native compilation and APK packaging pass.
- Independently checked **1.0.0 / 1000474**, package app.nebulagram.messenger, arm64-v8a, Telegram native library and NebulaLink libgojni, signed with the same certificate as prior releases (`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`). Audio setup labels/choices appear in the compiled DEX.
- File `build/artifact-38033372792/NebulaGram-1.0.0-TG-12.10.6-b1000474-arm64-v8a.apk`: **59,741,513 bytes**, SHA-256 `296b2c861affd1059ff159b6ab293811f90ee0d8a034d53d0bcbe7b61a8f198e`. Artifact retrieval verifies successful run and expected commit; no local APK rewrite.
- Physical-device checks remain unavailable under the user's prior response. A successful build is not live-provider/gesture/visual acceptance.


- IPA 38030936135 failed at the new AudioSettingsController: missing Display import for NebulaSwitchControl and `String.isEmpty()` called as a function. Both source errors were fixed before the next device build; the failed run is not a delivered IPA. APK 1000474 remains verified, since Android runtime is unchanged by this iOS repair.
