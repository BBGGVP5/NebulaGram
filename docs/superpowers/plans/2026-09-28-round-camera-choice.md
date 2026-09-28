# Round Camera Choice Before Recording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping the round-video camera presents Front/Rear; choosing one starts recording immediately, without requiring a second press.

**Architecture:** Android intercepts only a fresh round-video record press in Ask mode, consumes the original gesture release, and starts Telegram's existing recorder after selection in locked mode. iOS presents a native choice sheet before creating `VideoMessageCameraScreen`; the chosen camera position is passed into its initializer, whose existing camera-ready path starts recording. Cancel leaves recording idle on both platforms.

**Tech Stack:** Java/Telegram Android, Swift/UIKit/Telegram iOS, ordered upstream patches, GitHub Actions.

---

### Task 1: Android record gesture

**Files:**
- Modify: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaRoundCamera.java`
- Create: `patches/android/0135-round-camera-choice-on-record.patch`

- [x] Add `askForRecording(Context, ResourcesProvider, Runnable, Runnable)` to `NebulaRoundCamera`. It remembers the selected position and invokes the recording callback only on choice; dismissal invokes the cancel callback. Ask is the default for users without a saved mode, while saved Front/Rear/Last choices remain honored.
- [x] In `ChatActivityEnterView`'s `audioVideoButtonContainer.onTouchEvent`, after permission-to-send checks and before the delayed recorder runnable, intercept a fresh press when `hasRecordVideo && isInVideoMode()` and mode is Ask.
- [x] Consume release/cancel while the choice is pending. On selection, clear the pending state, run `recordAudioVideoRunnable`, and call `startLockTransition()` only if `recordingAudioVideo` became true. Suppress a stray release from the original gesture. Cancel leaves the recorder idle.

```java
if (hasRecordVideo && isInVideoMode() && app.nebulagram.ui.NebulaRoundCamera.mode() == app.nebulagram.ui.NebulaRoundCamera.ASK) {
    nebulaCameraChoicePending = true;
    app.nebulagram.ui.NebulaRoundCamera.askForRecording(getContext(), resourcesProvider, () -> {
        if (!nebulaCameraChoicePending) return;
        nebulaCameraChoicePending = false;
        if (getWindowToken() == null) return;
        nebulaIgnoreCameraChoiceRelease = true;
        recordAudioVideoRunnable.run();
        if (recordingAudioVideo) {
            startLockTransition();
        }
    }, () -> nebulaCameraChoicePending = false);
    return true;
}
```

- [x] Validate ordered patch application: `python scripts/check-upstream-series.py android --tree vendor/telegram-android --ref c84801762fd5f936c8296ecf09a14a48ebfc4fe4`.
- [ ] Run Android CI. On a device, verify Front, Rear, cancel, permissions, rapid repeated taps, and stopping the locked recording.

### Task 2: iOS native choice sheet

**Files:**
- Create: `patches/ios/0044-round-camera-choice-on-record.patch`

- [x] Extend `ChatControllerImpl.requestVideoRecorder` with an optional chosen-front parameter. On the initial call, present `ActionSheetController` with Front, Rear, Cancel. A choice dismisses the sheet and re-enters `requestVideoRecorder` with the selection; Cancel does nothing.
- [x] Add `initialFrontCamera` to `VideoMessageCameraScreen`'s initializer and store it. Pass the chosen value at the call site. In `Node.init`, replace the hardcoded front position with `controller.initialFrontCamera`; retain the current `setupCamera` callback that starts recording when the selected camera is ready.

```swift
let choose: (Bool) -> Void = { [weak self] front in
    dismissAction()
    Queue.mainQueue().after(0.2, { [weak self] in
        self?.requestVideoRecorder(initialFrontCamera: front)
    })
}
let isFrontPosition = controller.initialFrontCamera
```

- [x] Validate ordered patch application: `python scripts/check-upstream-series.py ios --tree vendor/telegram-ios --ref 6ad963e5b62d354da79040f388ae2b9132fb17b8`.
- [ ] Run bootstrap, native integration, and unsigned IPA builds. On a device, verify Front, Rear, cancel, permission handling, and immediate recording after selection.

### Task 3: Delivery

- [ ] Run `git diff --check`, stage only the files above, commit, and push `codex/ios-feature-parity` without staging unrelated workspace changes.
- [ ] Report Android and iOS build results separately; native build success does not imply device gesture acceptance.
