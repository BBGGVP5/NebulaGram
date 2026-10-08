# Camera, message menu and profile parity implementation plan

> **For agentic workers:** Execute inline in the authorized checkout. Preserve the modified vendor and unrelated files. Optional superpowers execution subskills are unavailable; the user has already requested implementation.

**Goal:** Implement missing functions shown in the October 8 video and screenshots, adapted to NebulaGram, with real consumers and a verified Android build.

**Architecture:** Keep the pinned Telegram source and export ordered patches from the scratch reconstruction. Separate camera capability policy, capture integration, media/message policy and presentation. Reuse NebulaSettingsHero, NebulaCard, NebulaRow and theme colors. Reuse existing functions rather than duplicate their settings.

**Tech Stack:** Android Java, Camera1/Camera2/CameraX, Telegram round-video engines and GL encoder, Python/Javac/Android SDK checks, Gradle CI.

## Evidence and scope

Video: `C:/Users/Danila/Desktop/video_2026-10-08_09-40-33.mp4`, 111.4 seconds; inspected frames in ignored `build/reference-settings-1008`. Camera screenshots add the six enhancement choices and exposure/control placement.

Already present: Saved destination, full reply quotes, disable-next-channel, chat haptics, snow, per-chat wallpaper, bottom tabs, icon packs, switch styles, basic filters, sticker time, double-tap/swipe tools, profile channel/birthday/business/background/emoji visibility, peer ID, archive hiding, deletion authentication, memory and community/support screens.

Missing/incomplete:

- Camera section, backend selection, native old/new round-video engine selection, dual camera, ultrawide start, aspect/quality, supported OIS/EIS/focus/noise/face/bokeh options, exposure placement and centered controls. Reuse Last/Front/Rear/Ask.
- Blur in encoded frames when switching cameras on the legacy recorder, preserving timestamps and frame continuity.
- Chat/media: keyboard-scroll threshold, Instant View preference, microphone routing/stereo/system sounds during recording, voice autoplay, volume-key video behavior, pause and seek interval.
- Messages: size/direct-share/wide-bubble controls, forward date, edited pencil, transcription provider, left action, delete-for-all default, reaction and premium-sticker behavior.
- Message menu: item visibility, compact presentation, unified/automatic scroll, bounded height and blur options, adapted motion and preview.
- Filtering: transliteration, whole words, exceptions and blocked senders.
- Profile: local phone hiding, DC details and a Nebula-themed live preview.
- Privacy: credential preference and a non-destructive biometric test where not already exposed.

## Task 1: Camera policy, settings and native consumers

Create `NebulaCameraSettings.java`, `NebulaCameraPolicy.java`, `NebulaCameraCapabilities.java`, `NebulaCameraFragment.java` in the Android UI overlay. Modify `NebulaSettingsFragment.java` and `NebulaSettingsSearch.java`. Export native hooks for InstantCameraView/Base/2, Camera2Session, CameraSession, RoundVideoCameraController and attachment capture.

- [ ] Write and run pure capability-policy tests for invalid values, unavailable quality, OIS/EIS conflict, initial ultrawide zoom and engine/backend combinations.
- [ ] Use stable bounded backend values AUTO=0, LEGACY=1, CAMERA2=2, CAMERAX=3, SYSTEM=4; aspect AUTO=0, FOUR_THREE=1, SIXTEEN_NINE=2, SQUARE=3; quality 0/480/720/1080/2160. Default to existing behavior.
- [ ] Build the Camera page with the shared animated introduction and grouped operational controls. Display device/backend availability honestly.
- [ ] Expose `InstantCameraViewBase.setUseCamera2Implementation`; apply NebulaRoundCamera facing to the native new engine and use its real output/FPS settings.
- [ ] Apply supported capture options on each camera's worker, without preference reads per frame or losing zoom/torch state. Prefer OIS over simultaneous OIS/EIS requests.
- [ ] Implement a real CameraX surface-provider/session lifecycle; release owned resources, reject late callbacks and handle failed opening. Route System to existing external capture permissions/results. Cached reference CameraX methods are empty and cannot serve as implementation.
- [ ] Wire requested aspect/quality, ultrawide, exposure and button placement into actual capture views; unsupported modes must not submit invalid requests.

## Task 2: Legacy switch blur

Create `NebulaCameraSwitch.java` and `scripts/check-camera-switch.py`; patch InstantCameraView's encoder shader and transition state.

- [ ] Test a bounded transition envelope: zero while idle, finite under rapid switches, decay after the first new frame, reset after close/cancel/reduced animation.
- [ ] Add a dedicated blur uniform and bounded texture samples for outgoing/incoming textures. Preserve native alpha, timestamps and circular output mask; reset outside transitions.
- [ ] Synchronize preview and encoded transitions without per-frame UI bitmap work; finish on usable frames or explicit failure.

## Task 3: Message/media behavior and filters

Create `NebulaMessagePreferences.java`, `NebulaMediaPreferences.java` and grouped controls. Extend NebulaMessageFilter; patch actual ChatActivity, ChatMessageCell, MediaController, PhotoViewer, Browser and recording consumers.

- [ ] Record each new preference's consumer, then add sizing/seek/scroll policy, display flags, action defaults and reaction/sticker controls while preserving native eligibility and permissions.
- [ ] Add supported recording/playback controls with mono fallback and correct sample timing; restore any temporary audio state on finish/cancel/error.
- [ ] Test Unicode/transliteration/word boundaries and account-specific filter exceptions; keep filtering local and reversible.
- [ ] Wire native/Nebula transcription choice with owner, generation and cancellation guards; retain existing text on failure.

## Task 4: Menu and profile presentation

Create NebulaMessageMenuSettings, NebulaMessageMenuFragment and NebulaProfilePreview in the Android UI overlay; modify NebulaSectionFragment and native menu/ProfileActivity consumers.

- [ ] Add menu organization and an interactive Nebula preview. Filter only eligible native actions, preserve dismissal and avoid empty menus. Compact visuals retain accessible hit targets.
- [ ] Wire scroll/height/blur controls and Nebula scale/opacity motion; honor reduced animation. Check RTL, long labels, large fonts and screen edges.
- [ ] Add representative profile preview, local phone hiding and DC information from native metadata; display unavailable information honestly.
- [ ] Extend the existing authentication helper with credential preference and a test that performs no destructive action.

## Task 5: Verification and delivery

- [ ] Extend/generate settings contracts for operational controls, distinguishing Android camera features from iOS support.
- [ ] Reconstruct all ordered patches and run required regression checks plus new policy/lifecycle tests.
- [ ] Compile changed native integrations, complete Android CI and fix failures before delivery.
- [ ] Inspect rendered screens on an available device/emulator. Record real camera/recording checks as unverified when no hardware is connected; mocks/compiler success do not prove device behavior.
- [ ] Commit only task files, publish authorized branches, download the exact APK, verify version/ABI/signature/artifact digest and update USER-CHANGES with actual completed scope.

API references: Android CaptureRequest and CameraX Preview.SurfaceProvider documentation. Implementation must check supported request keys and values before applying camera options.
