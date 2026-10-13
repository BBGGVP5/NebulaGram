# Draft voice transcription implementation plan

> **For agentic workers:** Execute inline, task by task; optional superpowers execution skills are unavailable. Do not dispatch agents.

**Goal:** Provide an Aa action before sending a locked voice recording, without requiring Telegram Premium, with text/audio choice and invalidation after resume or trim.

**Architecture:** Reuse Nebula's configured on-device or cloud audio recognizer. Pause active audio using the native recorder and wait for its completed preview file; recognize the selected trim region without changing the original recording. Bind results to account/dialog/recording generation and trim endpoints. Choosing text replaces the audio draft only after an explicit action; choosing audio leaves native sending intact.

**Tech Stack:** Android ChatActivityEnterView/MediaController, Java audio decoder and recognizers, cancellation/version policy tests, ordered native patch and full APK build.

### Tasks

- [x] Inspect the current pinned recording/preview/trim callbacks in an owned Android tree; determine the Aa control's native drawing/touch/accessibility position.
- [x] Add a draft-transcription controller and generation policy under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui`. Expose progress, editable transcript, retry, keep-audio and use-text actions. Capture the selected service explicitly and cancel on draft/account/dialog/lifecycle changes.
- [x] Export selected PCM through the existing audio decoder to export only the selected region when needed; preserve limits and the input audio file. Existing local/cloud recognizers process the exported snapshot.
- [x] Create an ordered ChatActivityEnterView patch for Aa in locked recording and preview, native pause/resume, trim invalidation and explicit text application. Add the setting alongside existing audio/transcription controls.
- [x] Execute actual state/callback tests for delayed pause completion, duplicate taps, cancellation, trim/resume, changed account/dialog, stale results and choosing audio/text. Run full Android compilation and verify the signed APK. Port to iOS as explicitly requested: patch `0102-draft-voice-transcription.patch`, a native Aa preview control, `SharedAccountContext` bridge, `NebulaDraftVoiceController.swift` and CAF/AAC selected-fragment exporter. Add the independent before-send toggle and real AVFoundation clip tests.


Implementation paths: `patches/android/0200-draft-voice-transcription.patch`; `NebulaDraftVoicePolicy.java` / `NebulaDraftVoiceTranscription.java`; `scripts/check-draft-voice.py` and `scripts/check-draft-voice-api.py`. Android native policy tests pass 424 cases and the production sheet/decoder typecheck against SDK 37.0 locally / 37.2 in CI. iOS uses Apple speech or the configured cloud service from a paused draft preview; existing native Pause/Resume/Trim remain. Full platform builds passed; physical device acceptance remains pending.

iOS implementation: `patches/ios/0102-draft-voice-transcription.patch`, `NebulaDraftVoicePolicy.swift`, `NebulaDraftVoiceAudio.swift`, `NebulaDraftVoiceController.swift`, native preview resource/generation guards, SharedAccountContext bridge and independent audio setting. Selected frames and preference independence have XCTest coverage; `check_draft_voice.py` executes real CAF/AAC export on macOS. UIKit/native builds passed; device QA remains pending.

Validated Android APK 1000502 / run 38290797256 at f00e40e: signed expected certificate, arm64, 60,061,452 bytes, SHA-256 `547c57417474b31b89b3d50ca8e46f4b55cbd428fa9db00c99b253342dcb9361`. Final iOS bootstrap 38297459032 at 3f1f07f passed 159 tests, 5394 geometry cases and real CAF/AAC selected-region export. Patch 0103 fixes the missing AccountContext dependency. Full IPA 110 / run 38297460672 passed: 88,036,100 bytes, SHA-256 `9e72d6ce2393af0f43ebecfc8b86c4737402f38a7571d1f35c332f9937f704ee`, arm64, user signing required. Physical recording/recognition acceptance remains unverified on both platforms.
