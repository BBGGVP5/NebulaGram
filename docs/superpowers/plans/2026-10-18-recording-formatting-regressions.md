# Recording and formatting regressions — implementation plan

**Goal:** Fix reproducible input/recording failures from the 18 October video without changing message transport.

**Architecture:** Keep native formatting and sending; restore the caret only when the toolbar selected a word implicitly. Feed already decoded and trimmed PCM directly to local recognition, retaining the paced pipe and snapshot ownership. Keep WAV packaging for explicit cloud recognition.

**Tech Stack:** Android Java/Editable/ML Kit, Swift native input state, Python regression harnesses.

## Evidence and scope
- Video: 55.95 seconds, Saved Messages. Formatting/keyboard transitions, automatic word selection, multiline blank draft, voice recording and the local transcription status. The user subsequently clarified that recognition itself is working.
- No connected Android device at investigation start. Visual recording cannot identify the recognizer exception or prove a transport delay.
- The local draft pipeline currently decodes Opus → PCM → WAV → MediaCodec(audio/raw); the second decode is unnecessary and depends on a PCM decoder being installed.

## Tasks
- [x] Add a regression that applies a toolbar action at a caret and then types: the existing word must survive. Preserve explicit selections and the native quote-inserted caret. Batch native edits with beginBatchEdit/endBatchEdit in finally.
  Files: platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingToolbar.java; scripts/check-formatting-toolbar.py; tests/android/formatting-widget-qa.py.
  Run: `python scripts/check-formatting-toolbar.py`.
- [x] Add a raw PCM trim path sharing exact bounds/cancellation checks with WAV packaging. Expose an owned-input transcribePcm entry point which does not decode or delete its caller's file. Use it for local drafts; leave WAV for cloud.
  Files: NebulaDraftVoicePolicy.java, NebulaLocalTranscription.java, NebulaDraftVoiceTranscription.java in the same Android overlay UI directory; scripts/check-draft-voice.py, scripts/check-draft-voice-api.py, scripts/check-local-audio.py.
  Test exact trimmed bytes, length limits, cancellation, no header and source preservation.
- [x] Inspect native accessory eligibility and placement during focus, reply, IME and recording transitions; fix demonstrated inconsistencies and execute native integration checks against build/android-1302-integrated-20261015.
- [x] Apply the implicit-selection caret fix to the iOS native callback while preserving explicit selection/link behavior; run iOS patch/bootstrap checks.
- [ ] Run targeted checks, full CI checks and actual widget rendering. Build updated Android and applicable iOS sources, verify artifact identity, and record any remaining device-only checks.

Execution stays in this authorized task; no separate agents or permission handoff.

## User clarification
The user confirms local transcription is working and requests better accuracy, not an availability fix. Priority is the delayed sending clock, missing toolbar animation, insufficient separation from the composer and ambiguous icons. Do not claim the PCM optimization changes model accuracy or fixes an observed recognition failure.

## Implemented changes under validation
- Native quick acknowledgements are allowed during chat-list animations. Structural server updates and other exclusive locks keep their original ordering.
- Android toolbar reveals/hides over 180 ms, reserves an extra 8 dp gap, retains reduced-motion behavior and cancels interrupted transitions. iOS reuses its toolbar with reversible alpha/translation and the same extra separation.
- Implicit word formatting restores the caret; native quote caret movement and explicit selections are retained. IME changes are batched on Android.
- Clear/link/code icons and long-press labels make actions identifiable.
- Prepared PCM goes directly to the paced local stream; cloud packaging remains WAV. No microphone/cloud fallback is added.

## Additional video finding: transcript send
The user explicitly identifies the flying message and broken field when sending a transcript. Native `resetRecordedState()` took the deletion path (`hideRecordedAudioPanel(false)`), whose editor fade starts after 750 ms. The text-flight animator could meanwhile sample this hidden editor.
- Added a send-specific reset overload; the existing trash action keeps its behavior.
- Transfer IME focus before removing the transcript view, cancel obsolete recorder animation, use the 150 ms sent transition and clear the recorder send control.
- Start a text-flight effect only when the actual editor is visible, opaque and laid out and the recorded panel is gone. Otherwise keep native animated row insertion. Voice-flight behavior is unchanged.
- Regression executes 32 editor/recording origin states and validates focus/reset/text order against native source.

## Validation checkpoint
- All 132 Android checks passed across the full local batch and the corrected focused draft test. The initial batch had one test-fixture failure: a send-order assertion searched across multiple methods; the corrected test scopes it to the send callback and passes 442 checks. No production workaround was added for this fixture error.
- CI widget run 39123211567 passed. Dark/light and narrow/large-font formatting screenshots were inspected; the strip is separate from the editor and overflow remains scrollable. These are production widgets with explicit editor/parent adapters, not an authenticated Telegram session.
- Native text-flight eligibility executes 32 combinations of visibility, opacity, layout and recorded-panel state; native quick-ACK admission preserves other exclusive locks and structural event ordering. Toolbar motion executes 1000 interrupted/reversible cycles.
- iOS bootstrap passed 117 ordered patches across 170 paths. Android-specific transcript reset is unnecessary in the current iOS path: it already uses the native sending transition with updateRecordedMediaDeleted(false) and enqueues the explicit text.
- Additional emulator run 39124156836 passed: it checks focus transfer from the real transcript host to an alpha-zero composer and verifies that closing the transcript does not hide the IME. This fixture does not emulate message delivery or the full Telegram recorded-panel animation.
- Full APK 1000579 (39123874816, 2b932c0) succeeded and was downloaded/verified: original certificate, exact version/package, arm64-v8a, 16 KB alignment and Go/Xray library identity. Receipt: build/artifact-39123874816/verification.json. Unsigned IPA 127 (39122976768, e20bc2a) is still building. Subsequent commits change tests only; application sources match these build heads.
- USB phone remains unavailable. Real delivery latency, device animation smoothness and speech recognition accuracy have not been measured.

## Follow-up: reply preview, clipped tiles and pre-bubble latency
- Pixel is connected with APK 1000579. The user clarifies the remaining delay occurs before a bubble appears, not while waiting for a server acknowledgement.
- Reproduced the hidden reply preview in Saved Messages: with the keyboard open, its cancel button overlaps the editor in the UI hierarchy. FrameLayout lays out the preview below accessory padding, then the old translation adds that offset again. Patch 0237 subtracts the actual layout top and reapplies positioning after layout; the regression fails before the fix and passes across reply heights and animation fractions afterward.
- Clip horizontally scrolling toolbar tiles to the inner padding; widget QA captures the scrolled edge in dark, light and narrow configurations.
- Patch 0236 admits quick ACK updates through the separate text-flight notification lock, matching the chat-list lock. This does not claim to fix pre-bubble latency.
- Swiftgram's inspected SGInputToolbar integration hides on lost input focus and has no separate collapse control. Preserve this behavior rather than inventing a button from an uncertain reference.
- Remaining: measure input-to-bubble latency on the connected phone; build/install and verify reply positioning, edge clipping and sending in the full client. Earlier fixture tests do not prove device timing.

## iOS artifact completion
- IPA 127, run 39122976768, completed successfully. Downloaded and verified original e20bc2a source, app.nebulagram, version 13.0/build 127, arm64 executable and notification extension. SHA-256 ce8d5e03b696af68eae645ae7e5544374b613d99a5299cd7283d8f1c356dab26. Receipt: build/ios-ipa-39122976768/verified-ipa.json. User signing is required; no iPhone or push-delivery acceptance was performed.

## Android follow-up artifact completion
- APK 1000580, run 39128931235, source 7f145557ee694941bcd76052d196cb93203d5306, completed successfully. All CI regression checks passed. Verified downloaded package/version, original certificate, arm64 and 16 KB alignment. SHA-256 9bd1659ad68a329d087a2bcc16967f5a0f729260fa3f6d35732a27927de7b43e; receipt build/artifact-39128931235/verification.json.
- Widget QA 39128960936 passed; scrolled dark and narrow/large-font screenshots were inspected. Tiles remain inside the inner gutter.
- The connected Pixel was on 1000579: reproduced hidden reply/cancel; captured two self-message sends (plain and formatted/multiline) without the reported long pre-bubble pause. These samples do not establish a universal latency fix or exact input-to-render timing.
- USB disconnected before the new APK finished. Do not mark 1000580 device-tested. Prepared build/test-reply-accessory-device.py for reply placement, clickable cancellation, multiline editing and keyboard closure when the phone is available; it preserves pre-existing drafts and sends no messages.

## New recording at 17:38 and source/core update
- [x] Review video_2026-10-18_17-38-54.mp4. Native text flight visibly crosses the floating formatting controls. Inspect the native item insertion callback and its composer-clear handshake before editing timers.
- [x] Extend the extracted native regression: with a visible formatting accessory, use ordinary animated row insertion above the accessory; complete the composer-clear handshake when insertion starts even when text-flight eligibility rejects the origin. Preserve voice flights, unobstructed text flights and native structural notification ordering.
- [x] Apply ordered Android patch 0238 and pass affected motion/geometry/responsiveness/draft checks. The first new regression failed against the old production method; all 128 origin states and 64 insertion scenarios pass after the fix. Full APK build remains listed below.
- [x] Query exact official Telegram production branch heads. Android f32ffeec763f0bdbf4bec25b52d654f77bff733d and iOS f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838 already match both gitlinks. No artificial version/source bump.
- [x] Verify Xray 26.10.17 and latest libXray 75ccb623c807a1113a064278e3e544b0049c16a5. Update the actual Xray dependency in runtime/xray and bind (NebulaLink uses its own bindings), refresh required module checksums, and run core/runtime/mobile protocol tests and vet. Core CI 39133245642 passed race tests and produced all platform libraries; the actual Windows C ABI smoke test confirms 26.10.17 and settings persistence. Optional xray_full tests and vet pass too.
- [ ] Build Android and the affected iOS core/application, verify artifact identity and report device-testing limits explicitly.
