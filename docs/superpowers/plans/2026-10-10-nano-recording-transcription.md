# On-device recording transcription implementation plan

> **For agentic workers:** Execute inline under the user's request. Keep the completed audio/setup/header fixes. No separate tasks or agent delegation. iOS has no Gemini Nano/AICore runtime; preserve the pending native IPA from the preceding correction.

**Goal:** Add explicit on-device transcription of saved voice/video recordings through ML Kit Speech Recognition's Gemini Nano Advanced mode, plus a distinctly labelled local Basic mode.

**Architecture:** Pin official `com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`. Use the published Java-accessible suspend/Flow signatures via cancellable coroutine runBlocking on worker threads. Check model availability and expose explicit download. Decode recording audio with MediaExtractor/MediaCodec, resample/mix to raw 16 kHz mono PCM, and stream it through a paced pipe. Keep native protected/secret/account checks, reuse transcript translation/summary actions, and never silently switch local work to cloud.

**Tech Stack:** Java/Android MediaCodec, ParcelFileDescriptor pipe, Kotlin coroutine Java interop, ML Kit/AICore, deterministic JVM fixtures, real Android/ML Kit SDK compile checks and Android CI.

## Verified primary API constraints

- [x] Fetch current Google documentation and the exact official SDK AAR/POM. Advanced uses on-device Gemini and is documented for Pixel 10/11; Basic is a separate on-device recognizer on Android 12+. FeatureStatus remains authoritative for available/downloadable/downloading/unavailable models.
- [x] Verify actual public signatures with javap: SpeechRecognition.getClient(options), suspend checkStatus/stopRecognition, Flow download/startRecognition, Final/Partial/Completed/Error response types and AudioSource.fromPfd.
- [x] Add pure PCM resampling, pacing and transcript accumulation tests: mono/stereo/downmix, 8/16/44.1/48 kHz, chunk continuity, no header, bounded duration/text, partial replacement, final-only result, cancellation and feeder errors.
- [x] Implement bounded OGG/MP4 audio decode (14 MB input, 10 min decoded maximum), strict 16-bit PCM conversion and cleanup. A real-time 16 kHz mono pipe is required; passing a file descriptor at full speed is unsupported by this alpha SDK.
- [x] Add local recognition session with captured mode/locale, status/download APIs, shared Nano inference gate, cancellation/deadlines, explicit unavailable state and no HTTP/API-key access. Do not infer audio availability from the text Nano model status. Limit Advanced selection to documented devices if the preferred-mode SDK can otherwise silently use Basic.
- [x] Add Gemini Nano and local Android recognition to transcription choices (never TTS); expose model readiness/download and recognition language. Integrate native recording button/menu path and partial transcript progress; preserve original/translation/summary/speech actions.
- [x] Pin dependency as an ordered Gradle patch, add fixture and real-SDK compilation tests to CI, run relevant existing local/cloud audio setup tests, then native APK build/download/signature/version verification.
- [x] Update feature docs and exact build evidence. Phone remains unavailable under the user's prior response; live AICore inference/audio quality is not claimed.

Sources: https://developers.google.com/ml-kit/genai/speech-recognition/android (updated 2026-10-07); official AAR SHA-256 `380d00a0dd15ea7a6618f57405708e1ec34fc8eb7df5e45cd3fa4190a27bf410`.


## Source verification checkpoint

- 122 executable PCM/resampling/downmix/chunk-boundary/pacing/cancel/partial-final transcript/device-policy assertions pass. Tests include 600-second bounds and a blocked-pipe clock; no catch-up data burst is allowed.
- The production Java suspend/Flow bridge executes with actual Kotlin/coroutine runtime: synchronous status, response Flow, timeout and thread interruption/cancellation cleanup pass. Production media decoder and ML Kit recognizer compile against Android SDK 36 and the exact official alpha1 AAR (verified digest).
- Shared Nano inference gate, existing cloud audio/labels (42 + 20 assertions), settings-link ownership, local/remote setup callbacks and native header tests pass. All **189 ordered Android patches** apply to the pinned 12.10.6 tree, leaving vendor untouched. No physical/live-model acceptance is inferred.


## Android integration repair

- Run 38035984143 stopped at a shadowed `text` helper in the download UI callback and the speech AAR's minSdk 26 versus the app's existing minSdk 21. Qualified the callback helper and appended a narrow speech-package override beside the existing guarded ML Kit packages in both manifests.
- The new recognizer rejects API 21–30 before constructing the SDK client. The AAR has no provider/service/receiver startup components. Actual callback compilation/background-state tests and actual platform guard tests pass; this preserves support for the rest of the app on old Android versions rather than invoking an unavailable speech API.
- Resampling reuses one frame ByteBuffer instead of allocating a wrapper for every decoded sample frame. All 122 PCM/timing assertions and real coroutine/SDK checks still pass. The failed build has no deliverable APK.


## Verified Nano APK

- Final Android run **38037535453** succeeded at **d3a475c573ba7bc21c750396e73fcc500033cc1b**: full interface/regression suite, real SDK/coroutine checks, Java/native application compile, manifest merging, R8 and APK checks passed. Run 38035984143 failed and is not a delivered build; duplicate branch runs were cancelled.
- Independently verified **1.0.0 / 1000478**, app.nebulagram.messenger, arm64-v8a with Telegram and NebulaLink native libraries. Signing certificate remains `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`. Local speech SDK, model/language controls and original audio setup strings are present in the compiled DEX.
- File `build/artifact-38037535453/NebulaGram-1.0.0-TG-12.10.6-b1000478-arm64-v8a.apk`: **60,047,953 bytes**, SHA-256 `23f61fe0bdd0a7a2598916598abcc3039b984ef1a17662ef101710db8637261b`. Successful run/revision receipt verified; no local APK rewrite. **190 ordered patches**, including the platform guard.
- Earlier custom long-press, audio enable/setup and chat back/avatar material corrections are included. Physical-device/live AICore model and speech-quality acceptance remain unverified under the user's phone-unavailable response.
- The parallel iOS repair build **38034906505 succeeded** at 9710eae and its unmodified IPA **build 92** was independently verified (SHA-256 `f07dbcc8694e7725a4b38c8e2661a2fa72ee84e18f44c108e228ac7060d61149`); all iOS/patch/core/bind/runtime inputs remain identical through d3a475c. Nano/AICore is Android-only and is not presented as an iOS provider.
