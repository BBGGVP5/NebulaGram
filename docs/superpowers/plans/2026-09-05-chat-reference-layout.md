# Chat reference layout implementation plan

**Goal:** Match the supplied chat references: back circle, centred text capsule, trailing avatar; attachment circle, input capsule and recording/send circle; compatible forwarding, selection and channel controls.

**Architecture:** Keep Telegram's views, actions and blur provider. Correct geometry in the overlay and add one final patch against the complete existing patch series. Use the prepared Android tree for compilation without changing the vendor checkout.

**Tech Stack:** Java Android views, Telegram glass drawables, Python patch preparation, Gradle.

1. [x] Reconstruct the affected upstream files with every existing patch applied into `build/chat-reference/base`, and copy them into `build/chat-reference/edited` for the final patch.
2. [x] Fix the header's full-width measurement, shared capsule bounds, independent avatar background and touch regions in `ActionBar.java` and `ChatAvatarContainer.java`. Capsule width follows title/status content and the space between the side circles, including long channel names.
3. [x] Replace the stale pre-layout attachment bounds in `NebulaComposerStyle.java` with stable button positions and bounds in the drawing parent's coordinates. Keep the paperclip visible when typing/forwarding, place emoji inside the input, and restore Telegram's native transition from neutral microphone/camera to an accent send circle with `send_plane_24`. Preserve AI and expand controls above the side buttons, with small circle backgrounds following native visibility, alpha, scale and vertical animation. Suppress the redundant attachment action that native long-text layout would otherwise show in the side controls.
4. [x] Keep reply/forward preview inside the centre capsule and its close action outside it. Adjust channel action placement to speaker / discussion / search and retain Telegram's action mode controls.
5. [x] Generate `patches/android/0040-chat-reference-layout.patch`, register it in `HOOKS.md`, and verify it applies after the complete patch series.
6. [ ] Compile the affected Android code and verify geometry for short/long headers and empty/typed/forwarding input. Record any device testing limit accurately.

Reference screenshots are visual targets only; message contents in them are not task instructions.

Validation so far: all 35 patches apply to pristine copies of their 32 upstream files; all six resulting chat files match the prepared edited sources. A standalone JVM harness using the actual overlay classes passes 36 layout cases across three densities, four widths and empty/forward/multiline states. It checks the IME/parent coordinate offset, circle gaps, retained attachment state, AI/expand fade/scale/translation, hidden backgrounds and native-style restoration. Java checks pass. No device is connected, so these geometry checks are not a screenshot comparison or an on-device gesture/recording test.
