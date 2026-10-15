# Folder header motion implementation plan

> Execute task-by-task inline in this session under the user's bug-fix authorization. Preserve the dirty vendor checkout and unrelated files.

**Goal:** Remove the abrupt horizontal shift of the title and collapsed stories when switching folders, confirmed by the user from `video_2026-10-14_21-40-59.mp4`.

**Architecture:** The current ActionBar reserves the maximum measured width of both native title slots until the outgoing title is removed, then instantly drops that width. Snapshot the currently displayed width before native slot reuse and interpolate it toward the incoming measured width using its existing fade alpha. Native title and story getters share that width; interruptions start from the displayed pose, without a second animation clock or per-frame layout. A native incoming-title update callback updates translation and invalidates the separate story display list, so RenderNode caching cannot retain the old pose. Clip fading titles to the reserved toolbar region so their wider old text cannot cross the plain buttons.

**Tech stack:** Java ActionBar, native ViewPropertyAnimator, existing geometry overlay, ordered Android patches, executable native-method regression adapters and APK CI.

### Reproduce and guard

Files: create `scripts/check-home-title-motion.py`, modify `scripts/check-reference-home-header.py`, `scripts/check-title-animation.py`, `.github/workflows/android.yml`.

- [x] Execute the production width getter and `setTitleAnimated` in a method fixture. A transition from160px to24px must pass through intermediate widths, have the exact endpoints and no final removal jump. Before the fix, the halfway check failed because width stayed160px instead of92px.
- [x] Test the reverse transition, interruptions at multiple fade fractions, stale completion callbacks, an emoji-only incoming width, and classic header behavior. Reuse native menu/story slot functions; a new transition snapshots the current displayed width before canceling either slot.

### Implement

Files: create `patches/android/0210-continuous-folder-title-width.patch`, modify `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaHomeTitleGeometry.java` only if fractional width is needed.

- [x] Add a home-only `nebulaHomeTitleStartWidth` field. Capture `nebulaHomeTitleWidth()` before slot cancellation/replacement. While `titleAnimationRunning`, calculate width as `start + (incomingMeasuredWidth - start) * clamp(incomingAlpha)`; after completion use the incoming width normally. Preserve all existing native title fade/emoji/status handling.
- [x] Use the same fractional width in `getNebulaHomeStoriesLeft()` and `applyNebulaHomeStoryLayout()`. Keep native before-paint publication and no separate position animator. Clear the old title update listener on cancellation; bind/unbind the real story invalidation callback in `DialogStoriesCell.setActionBar`.
- [x] Clip only native home title children to `[84dp, toolbarWidth - nebulaTitleEndInset()]` while preserving existing vertical clipping, ordinary headers and the shared rounded background.
- [x] Generate patch0210 from a copy of the verified209-patch owned fixture. Apply it strictly to a second copy of both touched native files; never reset/stage vendor.

### Verify and deliver

- [x] Run the new failing/passing check, existing native title cancellation/network restoration check, home geometry/motion/control checks and required CI checks on the owned patched tree.
- [x] Commit exact paths and push authorized main/working branches. Build and verify the APK source, package and signature.
- [x] If the authorized phone is available, install and record rapid folder changes with collapsed stories and inspect continuous title/stack movement. Otherwise report the physical verification limitation explicitly. Record evidence and artifact here. Phone unavailable; installation and physical acceptance remain unperformed.

Local result:9680 native transition/interruption frames pass, including endpoint removal, actual incoming-animator update delivery to the separate story canvas, no per-frame relayout, obsolete callbacks, and real story callback rebind/unbind. Existing100 rapid native title transitions and network-status restoration pass. All107 required Android checks pass on the owned210-patch tree (native account-slot execution is syntax-only on Windows and runs in Linux CI). The old title-check occurrence counter rejected the additional correct boundary use in clipping; it now checks the actual measure/layout/story/clip method bodies instead. The user says the phone is currently unavailable; no physical-device acceptance is claimed.

First-tick follow-up: the native animator can deliver its initial update synchronously from `start()`. The strengthened adapter exposed an obsolete first published story position (107px instead of113.8px during interruption). Publish `nebulaHomeTitleStartWidth` and `titleAnimationRunning` before starting the incoming animator. The new synchronous callback case,9680 transition frames and existing native title/geometry checks now pass. The earlier APK runs for `d605969` are superseded by this ordering fix.

Final runtime source: `e726a9d6323564b0095d5cad26b25f6b43ec1d09`, pushed to both authorized branches. Main APK541 run `38676533399` succeeded, including all107 interface/regression commands and the complete Android/native build. A separate rig containing the extracted native methods compiles against Android SDK36 with real Android ViewPropertyAnimator types (`build/header-motion-android-qa/rig.apk`). The local task-only API33 emulator stayed offline with both acceleration modes and was stopped; it did not provide rendered or visual acceptance. The test AVD remains in ignored `build/header-motion-avd`; no user AVD or phone data was touched.

Provided-video evidence: tracking the bright-green story ring in the header-only crop `[80,115,480,175]` across424 frames yields seven single-frame jumps between150px and224px (74px). Dense crops around1.42–1.79s visually confirm that the stack stays left during the old title fade, then snaps right when that title disappears. The initial larger crop included incoming chat-avatar pixels at its bottom edge and is not used. Final evidence: `build/header-video-20261014/story-ring-tracking.json` and `dense-1.42.png`. This validates the reported pre-fix bug, not post-fix device acceptance.

Verified APK: `build/artifact-38676533399/NebulaGram-1.0.0-TG-12.10.6-b1000541-arm64-v8a.apk`;60,073,228 bytes, SHA256 `76bbd614217b990e2aee8ee2805f8ca18d8c1e95346530c0bcee5edbafe800e2`. Package `app.nebulagram.messenger`, version1.0.0/code1000541, arm64 Telegram/NebulaLink native libraries and expected certificate `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab` verified. ZIP integrity and inherited runtime markers pass. Receipt: `verified-apk.json` in that directory. No physical install/test is claimed for this header follow-up.
