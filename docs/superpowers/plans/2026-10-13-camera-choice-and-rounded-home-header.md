# Camera choice and rounded home header implementation plan

> **For agentic workers:** Execute task-by-task in this session. The user's repeated request to continue authorizes inline execution; no delegation or handoff is needed.

**Goal:** Replace the old recording camera dialog and restore the rounded Android home header without separate button backgrounds.

**Architecture:** Keep the native recorder and camera preferences. Present the recording choice in a dedicated themed BottomSheet; keep the generic settings dialog unchanged. Restore the parent-owned rounded header and suppress only the home toolbar's independent material draws.

**Tech Stack:** Android Views, Telegram BottomSheet and blur3, Java/Android SDK, Python regression checks, GitHub Actions.

Reference: `C:/Users/Danila/Desktop/video_2026-10-13_19-46-12.mp4` (4.47 seconds), and `C:/Users/Danila/AppData/Local/Temp/codex-clipboard-0811c7ac-9d96-4f0d-8969-01d6ed1bd61a.png`. Video shows Edit and right actions on separate fills, and a rectangular full-width header. Screenshot shows the generic two-row camera dialog.

### Task 1: Recording camera panel

Files:
- Create `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaRoundCameraSheet.java`.
- Modify `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaRoundCamera.java`.
- Modify `scripts/check-dialogs.py`; extend SDK/widget QA to compile and render the actual new panel.

- [ ] Create a dedicated 28dp rounded sheet, heading and explanation, two 88dp minimum rows with code-drawn phone/lens artwork and two-line labels. Highlight the last camera with a color fill, without a checkmark, radio circle, or stroke. A scrollable body must fit large fonts/landscape. Close is an unfilled 48dp target.
- [ ] Preserve callback order: set selected first, remember facing, dismiss, then start. Dismiss without selection runs cancellation once.

```java
BottomSheet dialog = NebulaRoundCameraSheet.create(context, provider,
        prefs().getBoolean(KEY_LAST, true), which -> {
            selected[0] = true;
            remember(which == 0);
            onSelected.run();
        });
dialog.setOnDismissListener((Runnable) () -> AndroidUtilities.runOnUIThread(() -> {
    if (!selected[0]) onCancel.run();
}));
dialog.show();
```

- [ ] Run `python scripts/check-dialogs.py`, `python scripts/check-recording-taps.py build/android-home-motion-video-final-12106-20261013`, and SDK type checks. Expected: both camera choices start once; close/back/outside/swipe never start recording. Type checks succeed.

### Task 2: Shared header material

Files:
- Create `patches/android/0208-rounded-home-header-with-plain-actions.patch` from an owned fixture after patch 0207.
- Modify `scripts/check-reference-home-header.py` to expect the restored shared surface instead of separate button fills.

- [ ] Restore the existing rounded parent material for all shared headers:

```java
if (top && actionBar != null && actionBar.isNebulaSharedHeaderGlass()) {
    // Existing factory, padding zero and bottom radii 22dp remain unchanged.
}
```

- [ ] Suppress the independent Edit material for the home layout. Include `nebulaHomeLayout` in the plain-controls predicate, retaining search and selection behavior and unrelated chat/profile styling.
- [ ] Run header geometry/motion/controls regression checks on the owned patched tree. Expected: original reversible story path and title geometry still pass; shared header owns its rounded surface; no independent home button fills.

### Task 3: Verification and delivery

- [ ] Copy changed overlay files to the owned Android fixture; run all required Android interface/regression checks once. Do not reset or stage the user's dirty vendor submodule.
- [ ] Render the real camera panel in dark/light/narrow-large-font states in the credential-free CI widget app. Inspect screenshots. This is widget verification, not a physical Telegram camera acceptance test.
- [ ] Commit only these task paths, push the authorized main/working branches, build Android, inspect CI errors if any, and verify the APK package/version/signature/source revision.
- [ ] Record exact checks, CI/source/artifact provenance and physical-device limitations here before delivery.

## Verification receipt

Local implementation is complete. `check-dialogs.py` covers each camera choice, repeated selection, dismissal and missing activity. SDK 37 compiles the real camera panel/controller. The full 106 Android interface/regression commands pass on the owned tree after patch 0208, including 101 frames with no Edit backing and the existing story/title geometry and reversible motion checks. The credential-free QA APK compiles the actual new camera views; CI rendering and release APK verification remain pending.

The previously pending iOS native run `38417177542` reported a recording-send count type mismatch (`Int` versus native `Int32`). Patch 0104 now converts the native chunk count at the payment call; the executable test adapter uses the actual `Int32` signature, and bootstrap includes that upstream declaration so fixture drift cannot hide it. All 105 ordered iOS patches apply locally. New macOS execution/build evidence remains pending.
