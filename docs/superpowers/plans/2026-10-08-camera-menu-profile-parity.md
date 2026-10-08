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
- [x] Use stable bounded backend values AUTO=0, LEGACY=1, CAMERA2=2, CAMERAX=3, SYSTEM=4; aspect AUTO=0, FOUR_THREE=1, SIXTEEN_NINE=2, SQUARE=3; quality 0/480/720/1080/2160. Default to existing behavior.
- [x] Build the Camera page with the shared animated introduction and grouped operational controls. Display device/backend availability honestly.
- [x] Expose `InstantCameraViewBase.setUseCamera2Implementation`; apply NebulaRoundCamera facing to the native new engine and use its real output/FPS settings.
- [x] Apply supported capture options on each camera's worker, without preference reads per frame or losing zoom/torch state. Prefer OIS over simultaneous OIS/EIS requests.
- [x] Implement a real CameraX surface-provider/session lifecycle; release owned resources, reject late callbacks and handle failed opening. Route System to existing external capture permissions/results. Cached reference CameraX methods are empty and cannot serve as implementation.
- [x] Wire requested aspect/quality, ultrawide, exposure and button placement into actual capture views; unsupported modes must not submit invalid requests.

## Task 2: Legacy switch blur

Create `NebulaCameraSwitch.java` and `scripts/check-camera-switch.py`; patch InstantCameraView's encoder shader and transition state.

- [ ] Test a bounded transition envelope: zero while idle, finite under rapid switches, decay after the first new frame, reset after close/cancel/reduced animation.
- [x] Add a dedicated blur uniform and bounded texture samples for outgoing/incoming textures. Preserve native alpha, timestamps and circular output mask; reset outside transitions.
- [ ] Synchronize preview and encoded transitions without per-frame UI bitmap work; finish on usable frames or explicit failure.

## Task 3: Message/media behavior and filters

Create `NebulaMessagePreferences.java`, `NebulaMediaPreferences.java` and grouped controls. Extend NebulaMessageFilter; patch actual ChatActivity, ChatMessageCell, MediaController, PhotoViewer, Browser and recording consumers.

- [ ] Record each new preference's consumer, then add sizing/seek/scroll policy, display flags, action defaults and reaction/sticker controls while preserving native eligibility and permissions.
- [x] Add supported recording/playback controls with mono fallback and correct sample timing; restore any temporary audio state on finish/cancel/error.
- [x] Test Unicode/transliteration/word boundaries and account-specific filter exceptions; keep filtering local and reversible.
- [x] Wire native/Nebula transcription choice with owner, generation and cancellation guards; retain existing text on failure.

## Task 4: Menu and profile presentation

Create NebulaMessageMenuSettings, NebulaMessageMenuFragment and NebulaProfilePreview in the Android UI overlay; modify NebulaSectionFragment and native menu/ProfileActivity consumers.

- [x] Add menu organization and an interactive Nebula preview. Filter only eligible native actions, preserve dismissal and avoid empty menus. Compact visuals retain accessible hit targets.
- [ ] Wire scroll/height/blur controls and Nebula scale/opacity motion; honor reduced animation. Check RTL, long labels, large fonts and screen edges.
- [x] Add representative profile preview, local phone hiding and DC information from native metadata; display unavailable information honestly.
- [x] Extend the existing authentication helper with credential preference and a test that performs no destructive action.

## Task 5: Verification and delivery

- [x] Extend/generate settings contracts for operational controls, distinguishing Android camera features from iOS support.
- [ ] Reconstruct all ordered patches and run required regression checks plus new policy/lifecycle tests.
- [ ] Compile changed native integrations, complete Android CI and fix failures before delivery.
- [ ] Inspect rendered screens on an available device/emulator. Record real camera/recording checks as unverified when no hardware is connected; mocks/compiler success do not prove device behavior.
- [ ] Commit only task files, publish authorized branches, download the exact APK, verify version/ABI/signature/artifact digest and update USER-CHANGES with actual completed scope.

API references: Android CaptureRequest and CameraX Preview.SurfaceProvider documentation. Implementation must check supported request keys and values before applying camera options.

## Implementation evidence (October 8)

- Native changes are exported in `0186-camera-menu-media-controls.patch`; upstream and unrelated vendor edits are preserved. CameraX is pinned to 1.4.2 because 1.5.2 rejects the application's Android 5 minimum SDK during manifest merging. The adapter and native Camera2 bridge compile against the actual Android SDK and CameraX 1.4.2 artifacts.
- Camera: ordinary capture backend/aspect/quality; real old/new round engine; concurrent camera preference and capability check; front/rear/last/ask; ultrawide; exposure; attachment button positions; 360/480 round output; native 30/60 fps; supported capture enhancements. Camera2/CameraX flash and tap focus are connected. CameraX photo EXIF orientation is passed to the viewer. Encoder fallback checks aligned dimensions, frame rate and bitrate separately from capture metadata.
- Legacy blur changes encoded texture sampling during the native crossfade, with zero extra taps at rest. Four actual encoder shader variants compile with glslangValidator. Preview remains the native crossfade; it is not falsely described as a second identical blurred preview implementation.
- Media/message consumers: voice playlist progression, PhotoViewer volume keys/pause/seek, scroll threshold, forwarded date/edited marker/direct-share visibility, reaction and sticker effects, Instant View and eligible delete-for-all defaults. Both round encoders negotiate mono/stereo before AAC configuration. The newer recorder retains its native CAMCORDER source by default.
- Transcription uses the existing cancellable Gemini media request and our tools sheet. It requires a selected audio-capable Gemini service and a downloaded file; it does not silently fall back to another provider or overwrite the original caption.
- Filtering uses literal normalized text, optional transliteration/whole words, native blocked peers and per-account chat exceptions selected through the native peer picker. Filters are reversible.
- Menu settings control eligible actions, compact rows, height and existing Nebula blur/placement; the tappable preview uses native ItemOptions and the current Nebula menu material/motion. Edit/delete remain available and empty optional menus retain one native action. The existing profile preview is reused, with local phone hiding and explicitly labelled photo DC metadata.
- Privacy extends the existing delete-authentication helper, including device-credential preference on supported Android and a non-destructive authentication test.

### Explicit limits and remaining verification

- Device review of 1000411 found that 📷 has no animated entry in the native set and the camera screen lacked a visual sample. Follow-up: use Telegram's animated 🎬, add `NebulaCameraPreview.java` with a phone illustration and an adjacent native engine wheel, as clarified by the user, compile and inspect the updated page on the connected Pixel 9 Pro XL. The illustration does not open hardware; changing the engine wheel persists the chosen backend. Animated 🎬 playback and replay were verified from five device frames on 1000413. The initial landscape preview was rejected by the user and replaced with the phone layout.

- The reference's alternative unified scrolling, independent message auto-scroll and separate system-blur implementation are not new switches in this patch. Nebula's existing bounded placement, native action-list scrolling and blur/motion are reused. This does not claim exact parity with every Cherrygram menu item (for example, its JSON and photo-as-sticker actions).
- Camera capabilities vary by lens and mode. Concurrent recording uses the compatible round engine; bokeh requires supported continuous hardware mode and streaming size. System camera owns its own framing and quality settings. Camera1 exposes only its supported focus/stabilization/exposure controls.
- 92 checks passed on the second source checkpoint; the subsequent encoder fallback has its own passing policy check. The full final CI remains required after the final source commit.
- Physical Pixel 9 Pro XL / Android 17: 1000411 installed as an update with verified package, signer and artifact digest. Settings, Camera2/CameraX preview and still capture, both lenses, and old-round recording/switch were exercised. Found Camera2 JPEG double rotation and a recorder cancellation race at the 60-second boundary (crash-buffer evidence, matching APK DEX instruction). Follow-up uses JPEG EXIF and rejects cancelled/replaced queued recorder delivery; final updated APK verification remains pending. Temporary test preferences must be restored. No assertion of bug-free device behavior is made.

- Choice dialogs: per user correction, selected rows use only a rounded background highlight; no circle or checkmark. Native accessibility checked state, original callback indices, RTL labels, scrolling and dialog buttons remain intact.

### Additional editor corrections requested during device review

- Add a separate AI accessory row for short edits so AI cannot overlap the paperclip, including the multiline resize transition. Keep native composer behavior and editing/apply semantics.
- Separate the sheet title, mode selector and original card with explicit spacing. Add localized style presets and custom instructions, animated mode selection and the existing Nebula glass material sampled from the originating chat window.
- Reuse entity boundary mapping for correction and styling, preserving custom emoji IDs, links and formatting. Keep cancellation and reject stale results after input/account changes.
- Verify actual geometry, Telegram entity serialization, ordered patches, full build and device presentation before marking delivery complete.
