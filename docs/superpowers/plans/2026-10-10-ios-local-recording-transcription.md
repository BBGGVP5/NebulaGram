# iOS local recording transcription implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining portable audio gap with explicitly selected Apple on-device transcription of existing Telegram voice notes and round videos.

**Architecture:** Preserve the existing recording menu, transcription switch, cloud service choices and result tools. Use Telegram's existing software decoder for OGG/MP4, bounded private PCM, and sequential file recognition requests with `supportsOnDeviceRecognition` checked and `requiresOnDeviceRecognition = true`. No implicit cloud fallback, Google Nano label, microphone recording or camera port. Available languages and permission belong to Apple Speech; only complete results become transcript actions.

**Tech Stack:** Swift 5, UIKit, Speech, AVFoundation/CoreMedia, UniversalMediaPlayer, Foundation contract/XCTest, ordered Bazel plist patch.

---

The writing-plans skill was applied earlier in this task. Execution continues inline: its optional execution plugins are not installed. The previously audited shared catalog and non-camera consumers are already compiled in IPA 92. This plan addresses the confirmed new Android audio capability's native iOS analogue, not historical superseded inventory entries.

## Task 1: Explicit local choice and bounded policy

Files: `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaAudioPreferences.swift`, new `NebulaLocalAudioPolicy.swift`, new `Tests/NebulaSettingsContractTests/NebulaLocalAudioPolicyTests.swift`.

- [x] Add persisted `apple-device` transcription choice and validated locale independently of speech/text services. Never resolve this ID to a cloud connection. Test reopening and unchanged speech/text choices.
- [x] Test and implement integer frame ranges at 48 kHz: maximum 600 seconds, chunks of 55 seconds, exact contiguous coverage with a bounded final remainder. Reject zero, negative and excessive duration. Example assertion: `XCTAssertEqual(try NebulaLocalAudioPolicy.ranges(frameCount: 2_640_001).map(\.count), [2_640_000, 1])`.
- [x] Enforce complete transcript aggregation with a 100,000 UTF-16 limit and reject empty/incomplete recognition. Keep transient partial text separate from completed output.

## Task 2: Actual local recognition

Files: new `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaLocalTranscription.swift`, new `NebulaRecordingPCM.swift`.

- [x] Decode the downloaded recording off the main queue using `SoftwareAudioSource.readSampleBuffer()`. Its native decoder emits mono 48 kHz PCM16. Validate the actual format and input size; write private CAF under a UUID directory, bounded to 600 seconds. Check cancellation and a 120-second decode deadline; reject implausibly incomplete duration. Delete the directory on every outcome.
- [x] Request Speech authorization only after explicit Transcribe. Separate denial, unavailable local locale, invalid recording, no speech and timeout errors. Show safe RU/EN messages without raw recording paths/provider diagnostics.
- [x] Use sequential <=55-second CAF requests. Before each request require a recognizer with `supportsOnDeviceRecognition == true`; set `requiresOnDeviceRecognition = true`, `shouldReportPartialResults = true`. Each request has a 90-second deadline, a single terminal callback, main-queue cancellation and task cleanup. Publish aggregate output only after every chunk finalizes; empty silence chunks may finish, but an entirely empty result is an error.
- [x] Add an SDK typecheck of the real Speech adapter in bootstrap (native decoder is checked in the real SettingsUI graph). Use a small fake request driver to exercise final/error/deadline/cancellation ownership if its API can be factored without mirroring implementation.

## Task 3: Settings, recording screen, permission packaging

Files: `NebulaAudioSettingsController.swift`, `NebulaAudioTranscriptionController.swift`, new `patches/ios/0092-local-speech-permission.patch`, `patches/ios/HOOKS.md`.

- [x] Include `Apple · on device` beside Not selected/OpenAI/Gemini. Local row chooses among `SFSpeechRecognizer.supportedLocales()` with clear local availability, and displays recognition permission/status. Selecting does not prompt or send audio. Enable the existing switch after explicit service selection.
- [x] Capture local/cloud mode and locale at start. Download through the existing native MediaBox flow, then invoke the selected decoder/recognizer. Local mode shows progress and partial text as status only; protected/secret/expired recordings remain ineligible. Cancel on background/disappear and prevent stale callbacks.
- [x] Distinguish the local footer from cloud upload disclosure; result translation, summary and speech continue to use separately selected tools. No change to the source recording.
- [x] Update the existing Bazel `Telegram/BUILD` Speech purpose string and add `NSSpeechRecognitionUsageDescription` to the Xcode main plist. Explain existing selected recordings and on-device-only use. Verify ordered application rather than editing the vendor checkout.

## Additional audit finding: looping header emoji

- [x] `NebulaAnimatedSettingsEmoji.swift` still used `.once`, whereas current Android uses indefinite playback. Change the native sticker playback to `.loop`, retaining foreground/window/page visibility, return-to-page rewind and Reduce Motion pausing.
- [x] Pass AccountContext from the real message context menu (`0094`) and swipe handler into Message tools. Pass it onward to Speech and Tasks; pass the existing recording context to Speech. These destinations previously fell back to system emoji because their hero received no context. Preserve source-compatible optional initializers and shared 88-point header geometry.
- [x] Remove the remaining selection checkmark from Glass style rows. Apply the same accent highlight and selected accessibility trait as other choice screens, after the shared theme finisher so it cannot overwrite the highlight. Task completion checkmarks keep their action meaning.
- [ ] Verify actual on-device pack resolution, continuous visible playback, pause while covered/backgrounded and still-frame Reduce Motion. Compiler checks cannot establish rendered animation.

## Additional audit finding: native transcription button

- [x] Route the native voice/round-video button to Nebula while the independent switch is on; make the existing button visible on eligible recordings without relying on Premium trial availability. Preserve native behavior when switched off and native preview/view-once/layout guards.
- [x] Add `0095` AccountContext eligibility/presentation bridge instead of introducing a SettingsUI/recording-node import cycle. The context menu and explicit Transcribe action share the same positive-cloud/size/media/protection/secret/retained-content gate.
- [x] Add an executable fixture using the production eligibility function and actual size constant. Exercise voice/round/video, maximum size and all excluded message states; add it to bootstrap.
- [x] Notify actual switch changes and refresh visible recording rows through the existing owned chat observer list, plus on returning to the chat (`0096`). Collect IDs before requesting relayout. Test exactly one enable/disable notification, with no notification for repeated values or unrelated audio choices.
- [ ] Verify the physical inline button, configuration changes, switch-off return to native behavior and absence of overlapping/double presentations on iPhone.

## Task 4: Compile, artifact and inventory

- [ ] Generate contract mirrors; run ordered bootstrap, 78-setting checker and native/IPA driver tests locally. macOS bootstrap must execute new XCTest policy/persistence assertions and typecheck Speech against the pinned SDK.
- [ ] Commit intended overlay/patch/contract/plan files only, push the authorized branches and dispatch full iOS IPA/native integration. Diagnose actual compiler errors before reporting success. Keep Android/vendored/unrelated files untouched.
- [ ] Inspect the successful original IPA for source revision, bundle, display name, permission key, arm64 app/extension and digest. Record build status accurately in `platform/ios/PARITY.md` and `docs/PREMIUM-CLIENT-FEATURES.md`.
- [ ] Keep physical acceptance pending: offline OGG/round-video recognition, Russian/English local model readiness, >=1-minute recordings, silence, Stop/background/reopen, denial, unavailable locale and protected recordings. No iPhone or simulator runtime is currently available.

Primary Apple references: [local-only requirement](https://developer.apple.com/documentation/speech/sfspeechrecognitionrequest/requiresondevicerecognition), [availability](https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition), [authorization and plist](https://developer.apple.com/documentation/speech/asking-permission-to-use-speech-recognition), [recognizer duration guidance](https://developer.apple.com/documentation/speech/sfspeechrecognizer).


## Compilation checkpoint

- Runtime `f86671e` added local audio and ordinary video eligibility. Ordered local checks apply 93 patches / 157 native paths and 44 contract sources; 78 settings and 6 native/8 IPA driver cases pass.
- Bootstrap 38049460756 passed all **148 XCTest cases** and the actual Speech callback/cancellation fixture, then caught a deprecated macOS locale property in the embedded contract. `b1c34b48716b3d5404d333c6b1fdd7af93e8efbd` uses preferred language identifiers and adds early CoreMedia/AVFoundation decoder API typechecking.
- Superseded f86671e native/IPA runs 38049461543 / 38049464050 were cancelled for that runtime repair. Current bootstrap **38049746389**, native **38049747343**, IPA **38049749580** target b1c34b4; their final results are pending. No artifact or device-success claim is made at this checkpoint.
