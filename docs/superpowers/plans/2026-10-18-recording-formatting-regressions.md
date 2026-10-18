# Recording and formatting regressions — implementation plan

**Goal:** Fix reproducible input/recording failures from the 18 October video without changing message transport.

**Architecture:** Keep native formatting and sending; restore the caret only when the toolbar selected a word implicitly. Feed already decoded and trimmed PCM directly to local recognition, retaining the paced pipe and snapshot ownership. Keep WAV packaging for explicit cloud recognition.

**Tech Stack:** Android Java/Editable/ML Kit, Swift native input state, Python regression harnesses.

## Evidence and scope
- Video: 55.95 seconds, Saved Messages. Formatting/keyboard transitions, automatic word selection, multiline blank draft, voice recording and failed local transcription.
- No connected Android device at investigation start. Visual recording cannot identify the recognizer exception or prove a transport delay.
- The local draft pipeline currently decodes Opus → PCM → WAV → MediaCodec(audio/raw); the second decode is unnecessary and depends on a PCM decoder being installed.

## Tasks
- [ ] Add a regression that applies a toolbar action at a caret and then types: the existing word must survive. Preserve explicit selections and the native quote-inserted caret. Batch native edits with beginBatchEdit/endBatchEdit in finally.
  Files: platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingToolbar.java; scripts/check-formatting-toolbar.py; tests/android/formatting-widget-qa.py.
  Run: `python scripts/check-formatting-toolbar.py`.
- [ ] Add a raw PCM trim path sharing exact bounds/cancellation checks with WAV packaging. Expose an owned-input transcribePcm entry point which does not decode or delete its caller's file. Use it for local drafts; leave WAV for cloud.
  Files: NebulaDraftVoicePolicy.java, NebulaLocalTranscription.java, NebulaDraftVoiceTranscription.java in the same Android overlay UI directory; scripts/check-draft-voice.py, scripts/check-draft-voice-api.py, scripts/check-local-audio.py.
  Test exact trimmed bytes, length limits, cancellation, no header and source preservation.
- [ ] Inspect native accessory eligibility and placement during focus, reply, IME and recording transitions; fix demonstrated inconsistencies and execute native integration checks against build/android-1302-integrated-20261015.
- [ ] Apply the implicit-selection caret fix to the iOS native callback while preserving explicit selection/link behavior; run iOS patch/bootstrap checks.
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
