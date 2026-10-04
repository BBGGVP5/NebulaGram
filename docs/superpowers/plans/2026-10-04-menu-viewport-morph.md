# Android menu viewport morph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make Android popup menus form from the tapped control into a moving bubble, following the FlClash reference rather than a fixed-corner zoom.

**Architecture:** Preserve Telegram's popup placement, content hierarchy and touch bounds. Reserve drawing outsets through the public PopupWindow elevation API before showing the window; suppress its elevation shadow and let the existing glass paint its shadow. Remove the geometry clamp, separate movement/growth/content timing, and transfer the source button into the bubble with a bounded snapshot. Unsupported surfaces retain a fade transition.

**Tech Stack:** Android View/Canvas/PopupWindow, Java, production JVM geometry checks, Android SDK API compilation, Telegram patch series, GitHub Actions.

---

Execution is inline: the current developer instructions prohibit proactive delegation. No additional approval is needed for these already requested fixes.

Evidence: the user's 2026-10-04 recording shows the top and right edges staying nearly fixed and tiny labels visible during growth. The cached 2026-10-03 FlClash frames and `lib/widgets/popup.dart` show a source glyph moving to the menu center, with independent surface growth and content reveal. Our `sourceCenter`, `pivot` and `fit` clamps erase that movement. Android's local API 36 framework source confirms that public `PopupWindow.setElevation` reserves surface insets before attachment, without changing the logical window or hit bounds. No hidden API access is needed.

### Task 1: Preserve the drawing path outside the logical menu

Files: create `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMenuViewport.java`; modify `NebulaMenuBubble.java`; create `patches/android/0174-menu-drawing-viewport.patch`; extend `scripts/check-liquid-popup.py` and `scripts/check-menu-api.py`.

- [x] Add regressions for true source coordinates outside the menu and the conservative drawing envelope.
- [x] Run the geometry check and confirm it fails with the existing clamps.
- [x] Reserve outsets before both native show methods; preserve native placement, root/content identity, touch interception and dimensions.
- [x] Suppress only the framework elevation shadow, disable ancestor clipping along the menu path and restore elevation, outline and clipping on detach/reuse.
- [x] Compile against the actual SDK and check the native patch against pinned Telegram sources.

### Task 2: Source-to-menu choreography

Files: create `NebulaMenuSource.java`; modify `NebulaMenuReveal.java`, `NebulaMenuStyle.java`; extend `scripts/check-menu-feedback.py`, `scripts/check-menu-drag.py`, `scripts/check-menu-api.py`.

- [x] Retain the exact anchor center, including negative or out-of-bounds coordinates.
- [x] Separate 750 ms opening clocks into growth, horizontal/vertical travel and content reveal; remove the fit operation and use contain scaling on close.
- [x] Snapshot only small controls, transfer their glyph at native size, crossfade with the menu and restore the original control on detach, cancel or reuse. Never hide message-sized anchors.
- [x] Keep the glass sharp while labels fade through content-only blur; use a fade when drawing outsets cannot be prepared.
- [x] Exercise delayed attachment, interrupted close, pointer inversion, source cleanup, unsupported/reduced modes and reuse.

### Task 3: Validate and deliver

- [x] Run the affected menu, selection, palette and rendering checks; reconstruct and apply the full native patch series with `python build/verify-upstream-12106.py`.
- [x] Record normalized trajectory/contact sheets for the reference and the new implementation, without claiming device verification from mathematical tests.
- [ ] Commit only the intended files, publish the source changes and run the Android build. Keep the existing iOS IPA rebuild under observation because these changes target Android.
- [ ] Download and verify the resulting APK, then provide it with the concrete change and verification limits. Use ADB if the phone is connected; currently no USB device is visible.

Verification: all 169 patches apply to 122 native files from the pinned Telegram revision. API compilation, source/window lifecycle, palette, pointer inversion, selection and hot-path checks pass. Physical screen bounds receive a smooth inward center lead without resizing the native hit area. Framework wrappers created during show are also unclipped and restored. Cached video contact sheets and production-motion CSV/diagram are in `build/menu-video-comparison/`; the diagram is explicitly marked as calculated, not a device recording. ADB and Windows device inventory currently show no phone.
