# Settings, Header, and Round Video Polish Implementation Plan

> **For agentic workers:** Keep the Android and iOS changes in the same task so each visual control has working behavior on both platforms. Steps use checkbox syntax for tracking.

**Goal:** Make NebulaGram settings easier to scan and repair the reported Android home header, profile accent, and round-video interactions without changing the selected background or theme.

**Architecture:** Preserve the existing settings model and theme providers. Reuse the shared hand-drawn settings glyph source for Android vectors and iOS paths. Keep Android upstream edits as numbered patches, with custom views in the overlay. Only expose settings whose native behavior is connected.

**Tech Stack:** Android Java and vector drawables, Telegram Android patch series, UIKit/Swift settings overlay, Python contract checks.

---

### Task 1: Settings inventory and presentation

**Files:** `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaSettingsFragment.java`, `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsController.swift`, `design/settings/icons.json`, `scripts/generate-settings-icons.py`.

- [ ] Compare the Cherrygram references against actual NebulaGram settings and their runtime consumers. Keep camera configuration out of this parity pass.
- [ ] Consolidate top-level groups into short labels and compact explanations. Use the generated glyphs, preserving existing `NebulaTheme` and iOS presentation theme backgrounds.
- [ ] Add only missing settings with a native read/write consumer on both platforms; update `shared/settings/catalog.json` and regenerate the contract when a new portable value is needed.
- [ ] Run `python scripts/generate-settings-icons.py --check`, `python scripts/check-settings-design.py`, and iOS Settings contract tests.

### Task 2: Android home and profile

**Files:** `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaDialogsTitle.java`, `NebulaFolderTitleView.java`, `NebulaProfileArt.java`, `NebulaLinkShortcut.java`, `patches/android/0132-home-header-ios-layout.patch`.

- [ ] Make `NebulaDialogsTitle.apply` choose one title transition path per change and re-layout the centered title on the same frame as the swipe.
- [ ] Ensure search, link, and overflow each own only their visible touch bounds; use the existing glass factory for the whole home action group.
- [ ] Resolve profile accent from the app theme provider, including photo banners; keep the selected cover photo and background intact.
- [ ] Verify rapid folder swipes, search taps, and profile collapse on device or emulator, then run Android compilation.

### Task 3: Round-video input and zoom

**Files:** `patches/android/0135-round-camera-choice-on-record.patch`, `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaRoundCamera.java`, `NebulaZoomSlider.java`, `patches/android/0123-round-video-zoom-slider.patch`, iOS video-message overlay if the same defect exists there.

- [ ] Move the `ASK` camera dialog from pointer-down to the long-press runnable; only a selected camera starts recording.
- [ ] Preserve a stop action on the same recording control after locking; dismissing the dialog must leave recording idle.
- [ ] Replace hard-coded pink slider colors with theme colors and keep the draggable ruler visible long enough to adjust it. Respect each device's maximum zoom.
- [ ] Check Android patch application and compile, then exercise start/cancel/stop and 1×/2×/ruler interactions.

### Task 4: Final verification

- [ ] Run the repository's focused settings, layout, and patch checks.
- [ ] Build Android and the iOS settings contract. Run full platform builds when host/tooling permits.
- [ ] Inspect the diff for theme regressions and unrelated work before committing.
