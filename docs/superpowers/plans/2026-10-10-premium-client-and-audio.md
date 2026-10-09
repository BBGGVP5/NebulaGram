# Premium-style client tools and audio implementation plan

> **For agentic workers:** Execute inline under the user's authorization. The optional superpowers execution skills are unavailable; do not create separate chats. Track source, build and physical acceptance separately.

**Goal:** Add useful client implementations from the Premium feature list and complete voice transcription, translation and natural speech generation on Android and iOS.

**Architecture:** Keep Telegram's real entitlement and server limits intact. Reuse named AI services and protected Keychain/Keystore credentials, but capture a distinct audio configuration before every request. Use bounded cancellable transports, explicit remote actions and local playback/export; no automatic sending. Add account-scoped local Saved Messages labels with native message navigation.

**Tech Stack:** Java/Android, Swift/UIKit/Foundation/AVFoundation, JSON, HTTPS fixtures, XCTest, ordered Telegram patches, GitHub native builds.

## 1. Audit and acceptance boundaries

- [x] Inspect existing transcription, TTS, named services and current native hooks. Existing Gemini transcription and default device TTS need expansion. Android rebuild 37953877464 succeeded; it predates this feature batch.
- [x] Verify audio wire formats against primary documentation: OpenAI `/audio/transcriptions` supports OGG and MP4 multipart, `/audio/speech` supports WAV output and built-in voices; Gemini Interactions supports text/audio content and generated audio, with model-specific WAV/PCM responses.
- [ ] Record the screenshot feature inventory in `docs/PREMIUM-CLIENT-FEATURES.md`: implemented client analogues, additions in this batch and server-dependent features. 4 GB uploads, cloud speed quotas, actual Premium badge/status, Business server settings, Stories quotas/incognito, reaction/emoji sending entitlements, paid-message collection and others cannot be granted by a client preference.

## 2. Audio protocol and preferences

**Files:** `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAudioProtocol.java`, `NebulaAudioClient.java`, `NebulaAudioPreferences.java`; `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaAudioProtocol.swift`, `NebulaAudioPreferences.swift`; existing iOS `NebulaAudioService.swift`; Java `tests/android/AudioProtocolCheck.java`, runner `scripts/check-audio-protocol.py`, Swift `NebulaAudioProtocolTests.swift`.

- [ ] Add pure request/response tests: binary multipart preserves source bytes and supplies `recording.ogg` / `recording.mp4`, `response_format=json`, valid model; Gemini incomplete/error/thought-only replies rejected; OpenAI JSON transcript bounded; malformed Base64/audio MIME rejected; raw signed-16 PCM wraps a correct WAV header; WAV passes unchanged; text/speed/size bounds enforced.
- [ ] Add separate transcription/speech service IDs and model settings. Missing/deleted selection never falls back to another provider. Use existing named service secrets; default models are editable independently of the text model. Support OpenAI and Gemini; device TTS is a separate explicit speech choice.
- [ ] Implement at most 14 MB media input, 100,000 UTF-16 transcript, 4,000 UTF-16 speech text, 20 MB decoded audio, bounded JSON/body downloads, disabled redirects, ephemeral/cookie-free sessions and cancellation. Preserve recording bytes and MIME; do not export secret/protected messages.
- [ ] Add OpenAI multipart transcription and WAV speech; Gemini transcription and TTS Interactions payload/parsing. Accept documented output variants without reading thought/tool/input content. Validate audio before playback and delete request temporary files on cancellation/error.
- [ ] Run `python scripts/check-audio-protocol.py`; add it to Android CI. Generate iOS mirrors with `python platform/ios/tools/generate-overlay.py` and run XCTest in macOS bootstrap.

## 3. Audio user interface and integration

**Files:** new Android `NebulaAudioSettingsFragment.java`, `NebulaSpeechFragment.java`; existing `NebulaAiSettingsFragment.java`, `NebulaMediaControls.java`, `NebulaMessageToolsFragment.java`, `NebulaTranscription.java`. New iOS `NebulaAudioSettingsController.swift`, `NebulaSpeechController.swift`; existing `NebulaAiController.swift`, `NebulaMessageToolsController.swift`, `NebulaAudioTranscriptionController.swift`.

- [ ] Add a themed Audio & voices screen: transcription/speech service, independent model ID, voice picker, style and speed. Voice selection uses existing highlight-only choices. Include installed system voices, prioritizing enhanced/premium quality where available; label cloud audio as AI-generated and show destination before request.
- [ ] Add a speech screen from text/result: generate/listen/stop/share audio, edit text and voice settings. Capture text/provider/configuration on tap, prevent stale playback after changes, stop on background/dismiss, and share only an explicit completed file. UIKit popovers are anchored on iPad; Android uses FileProvider.
- [ ] Extend recording tools: original transcript remains available; explicit translation into the result language, summary and speech act on that transcript. Preserve original chat message. Existing transcript button uses the configured audio service, independent of the text chat provider. Recheck account/message eligibility before remote work.
- [ ] Verify actual speech/transcription navigation on both platforms with ordered patch guards; avoid duplicate Premium upsell interception and retain native Telegram path when Nebula is disabled.

## 4. Client Premium analogue: local Saved Messages labels

**Files:** Android `NebulaSavedTags.java`, `NebulaSavedTagsFragment.java`, patch `0192-saved-message-labels.patch`; iOS contract `NebulaSavedTags.swift` and tests, UIKit `NebulaSavedTagsController.swift`, patch `0090-saved-message-labels.patch`; shared settings entry screens.

- [ ] Implement user-ID scoped label/message-ID metadata only, bounded names/count/storage, normalized duplicates and explicit rename/delete. No fabricated cloud reaction/tag attributes; metadata stays local and outside settings transfer.
- [ ] Offer Labels in eligible Saved Messages context menus; assign/remove labels. Add a searchable label browser in Chats settings with message-ID navigation and remove-link action. Do not snapshot message text/media into another database. Deleted/unavailable messages report a useful state.
- [ ] Test isolation across account slot reuse, Unicode names, duplicate labels, rename/removal, malformed persisted metadata and limits. Use native navigation to Saved Messages and exact message ID.

## 5. Verification and delivery

- [ ] Run Java protocol/preferences tests and relevant existing AI tests; local iOS mirror, contract and all ordered patches.
- [ ] Compile the Android overlay against pinned native Telegram through CI. Run macOS XCTest and native iOS integration; fix actual compiler errors. Push only intended files to authorized branches.
- [ ] Build/download/verify final Android APK and native iOS IPA where needed; inspect revision, signatures/arm64 metadata and branding. Report physical-device and live-provider acceptance honestly; deterministic transport fixtures do not prove a paid provider key has access.
- [ ] Update the feature inventory with real evidence. Physical checks: repeated open/dismiss, cancel/background, protected recordings, changing provider during work, transcript translation then speech, missing voice, offline/error states, large text/recording limits, audio sharing, account switch and Saved label navigation.

## Primary sources

- https://telegram.org/faq_premium/
- https://developers.openai.com/api/reference/resources/audio/subresources/transcriptions/methods/create
- https://developers.openai.com/api/docs/guides/text-to-speech
- https://ai.google.dev/gemini-api/docs/audio
- https://ai.google.dev/gemini-api/docs/speech-generation

Self-review: the request asks for feasible Premium functions and audio tools, not fake server entitlements. Existing translation, local tasks/checklists, icon packs, account count, folder controls, editor/style/formatting and displayed effects are reused. This batch adds independent audio selection and local Saved labels. Existing native camera scope is unchanged.


## Audio checkpoint

- Added pure Java audio formats and actual HTTPS fixture transport; 42 assertions pass. Existing AI protocol/services and 78-setting contract checks pass.
- Added independent audio service/model/voice/style/speed choices, Android speech preview/export, iOS speech preview/export, and transcript actions on both platforms. Local mirror/ordered iOS guards pass; native compilation and UI/provider acceptance are still required.
- Provider references were fetched on October 10. Gemini speech defaults to documented `gemini-3.8-flash-tts`; audio model IDs remain editable. OpenAI defaults to `gpt-4o-mini-transcribe` and `gpt-4o-mini-tts`, with WAV output.


## Saved labels source checkpoint

- Added account/user-ID scoped metadata stores, native Saved message-menu entries, highlight-only assignment and searchable label browsers with native message navigation on both platforms. Java metadata has 20 passing assertions; Swift has three equivalent XCTest cases.
- Current ordered validation: 187 Android patches apply to pinned 12.10.6 without touching vendor; 90 iOS patches / 157 native paths apply. Audio Swift bootstrap passed at e7503f6 (140 tests); Saved label additions still need the latest native build.
