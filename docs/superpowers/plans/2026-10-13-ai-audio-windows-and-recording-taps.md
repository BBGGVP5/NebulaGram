# AI/audio windows and recording taps implementation plan

> Execute inline, task by task. Preserve the dirty vendor checkout and unrelated user files. No subagents.

**Goal:** A tap during round-video recording finishes the current recording instead of switching media modes; voice-to-text before sending offers a reversible audio/text choice; AI and audio tools have a coherent, compact modern interface on Android and iOS, using their latest published upstream source bases.

**Architecture:** Extend the verified Telegram 12.10.6 / 199-patch baseline in owned fixtures. Correct native recording state and touch routing first. Share a themed AI/audio presentation toolkit with code-native line artwork, readable headings, compact provider/status controls, editable result surfaces and one clear primary action. Keep recognition providers, audio snapshot/generation gates and native payment/scheduling/send handling.

**Tech stack:** Java Android Views, native ChatActivityEnterView/BottomSheet, Android SDK executable fixtures, a read-only emulator for visual verification, full signed APK CI.

## Evidence and design direction

The supplied ZIP contains two screenshots of NebulaGram: the native voice preview and a separate black transcription form. The latter has a low-contrast title, large unstructured vertical gaps and two equally dominant primary buttons. The original audio should remain available while the user reviews text.

Primary design reference: https://design.google/library/expressive-material-design-google-research and https://m3.material.io/styles/motion. Use shape, containment, readable labels and restrained motion to guide the action; adapt to Nebula's existing theme. Artwork is drawn as intentional vector paths, not a generated stock image. The official DrKLO source repository currently publishes 12.10.6; do not claim an exact source-code comparison with the unpublished 13.0.3 beta. Use the user's stated Aa/audio-or-text/resume/trim workflow as the functional target.

## Recording touch repair

**Files:** `patches/android/0205-recording-tap-state.patch`, `scripts/check-recording-taps.py`, `.github/workflows/android.yml`.

- [x] Copy the production ChatActivityEnterView from `build/android-home-motion-video-final-12106-20261013` into an owned baseline fixture.
- [x] Execute the actual anonymous `audioVideoButtonContainer.onTouchEvent` and `startLockTransition` methods in a deterministic scheduler fixture. Reproduce active unlocked and auto-locked video taps switching the mode, including stale start flags.
- [x] Establish `sendButtonVisible = true` before starting the lock animation. Route active recording through the existing send branch and cancel any queued start. Consume its matching release/cancel so it cannot send twice or toggle modes:

```java
if (recordingAudioVideo || recordCircle.isSendButtonVisible()) {
    nebulaRecordActionTap = true;
    AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable);
    recordAudioVideoRunnableStarted = false;
    // Continue through the existing native send/payment/schedule branch.
}
```

- [x] Keep idle short-tap switching and hold/slide-to-cancel behavior. All 22 native touch/lock scenarios pass, including payment confirmation and release after UI reset.

## Draft voice preview and tool presentation

**Files:** `NebulaDraftVoiceTranscription.java`, a new `NebulaAiPanelUi.java`, native ChatActivityEnterView follow-up patch, `NebulaMessageToolsFragment.java`, `NebulaAiSheet.java`, `NebulaSpeechFragment.java`, `NebulaAiChatView.java`; existing draft/audio/UI API fixtures.

- [x] Resolve placement: the user explicitly chose an inline preview above the voice panel with audio/text choice and the native send action. Snapshot validation must occur before converting the voice draft; closing or selecting audio preserves it.
- [x] Implement scoped Android presentation helpers: high-contrast header, vector voice/Nebula motif, provider controls, rounded editor and compact retry action.
- [x] Replace the Android draft form with an inline editable preview and reversible voice/text choice. Closing or changing the recording preserves the original voice; recognition starts only through Aa/retry.
- [x] Apply the Android presentation to incoming transcription, text tools, AI chat sheet and speech preview; retain provider callbacks.
- [x] SDK typecheck and 438 draft ownership/send cases pass. Confirmations capture the reviewed text and draft; stale or duplicated confirmations cannot send it.

## Verification and delivery

- [x] Upgrade the published iOS pin to 13.0 (`f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838`), rebase all 103 patches, and update Xcode to 26.6 and the exact rules_apple build pin. Ordered patch/bootstrap checks pass locally and on GitHub; native integration compilation is running. Android master remains 12.10.6 / `f2908b14133bbffbf7ab04f641ecb5bfaf533242`; 13.0.3 beta sources are not published there.
- [x] Implement the iOS inline preview, common native payment/scheduling/send action and compact AI/audio presentation. Preserve draft/trim/account/service ownership, reviewed text, original voice and native message transforms. Native compilation and device acceptance remain pending.

- [ ] Apply the complete ordered patch series to the committed 12.10.6 pin in a fresh owned tree; verify vendor HEAD/status are preserved.
- [ ] Render the production new views in a QA activity on a read-only emulator: light/dark, narrow/normal width, larger font, loading/error/result and keyboard-visible states. Inspect saved screenshots and fix clipping/spacing. Label this separately from real-device ASR acceptance.
- [x] All 106 Android interface/regression checks pass against the owned pinned tree; native C++ account slots were syntax-checked locally and remain runtime-tested in Linux CI.
- [x] Android and iOS source upgrade are committed and pushed to main/working branch.
- [x] Signed APK 1000516, run 38397138020, downloaded and verified: source 98d6d76759adc6b5464ed748e4ba8eb84606d146, certificate a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab, arm64-v8a, SHA256 6c23db2a960c394f18d9b3b587ff70aaed91cacd550108b183e8595452987761. No physical device test; the read-only emulator did not boot.
- [ ] Run the actual new iOS recording-send callback fixture, UIKit/native compilation and full unsigned IPA build; inspect and repair any compiler failures before claiming success.
