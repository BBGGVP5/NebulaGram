# Android 12.10.6 and Stable Phone Confirmation Implementation Plan

> **For agentic workers:** Execute inline under writing-plans; optional execution plugins are unavailable. Do not delegate, reset, move or delete the dirty vendor checkout.

**Goal:** Provide current Android 12.10.6 sources with all Nebula patches and prevent the phone confirmation card from following the background button down after it opens.

**Architecture:** The committed Android gitlink already points to official 12.10.6 (`f2908b14133bbffbf7ab04f641ecb5bfaf533242`, version code 7112); the local dirty vendor remains at 12.10.4. Prepare a fresh owned checkout from the committed pin, overlay and ordered patches. Native PhoneNumberConfirmView currently follows the floating button on every VerticalPositionAutoAnimator tick and lays out its card above that moving clone. Capture that position before showing the modal, ignore subsequent background-button motion, and constrain it to the visible bounds on genuine viewport changes. Keep blur, confirmation/edit callbacks, accessibility and authentication requests intact.

**Tech Stack:** Python/Git fresh-source preparation, Java/Android native login patch, extracted native-method tests, SDK compile and APK CI.

### 1. Stable confirmation overlay

Files: create `patches/android/0199-phone-confirmation-position.patch` against the current pinned/patched `LoginActivity.java`; create `scripts/check-phone-confirmation.py`; add it to `.github/workflows/android.yml` regression checks.

- [x] Execute the actual native `updateFabPosition` and layout hook with instrumented views. Open at one source-button Y, then move the hidden source by a keyboard height; the modal clone/card must stay at the captured position. Confirm baseline failure before editing.
- [x] Add `private boolean positionLocked;`; before the show animator, call `updateFabPosition(); positionLocked = true;`. Add `if (positionLocked) return;` to the position updater. Clamp the captured clone to actual overlay bounds on layout, keeping the popup on screen; refresh its anchor when width changes for rotation/split view. Do not change blur or the authentication callbacks.
- [x] Test ordinary/RTL widths, keyboard-hide ticks, a narrow/short viewport, width changes and preservation of native confirmation/edit/progress callbacks. Compile the affected extracted hooks against the real Android SDK.

### 2. Reproducible current source tree

Files: create `scripts/prepare-android-tree.py`, `scripts/test-prepare-android-tree.py`; update `scripts/apply-overlay.sh`, `docs/BUILD-ANDROID.md` and Android CI safety checks.

- [x] Prepare only a non-existing owned destination using the committed gitlink, a separate clone, `core.autocrlf=false`, detached pinned checkout and all ordered patches plus overlay. Optionally initialize native submodules. Copy the already-built AAR if present; never inject or copy credentials from the dirty vendor tree.
- [x] Verify `APP_VERSION_NAME=12.10.6` from the pinned source, reject a reused destination or vendor/source path, and record the source pin and patch/input hashes in the prepared tree.
- [x] Before the existing apply-overlay reset/clean, reject an Android checkout whose HEAD differs from the committed gitlink. Print the safe preparation command rather than building the stale local source.
- [x] Test fresh-destination/path/pin/version guards and unchanged old-checkout files, then prepare `build/android-12106-phone-confirmation-current` with the complete current patch series. Update local build instructions and support an explicit prepared-tree argument in `inject-keys.sh`.

### 3. Validation and delivery

- [x] Run source-preparation safety tests, actual phone-modal regression, complete patch application and required Android checks; preserve prior banner/AI/audio changes.
- [ ] Commit exact paths, push the already-authorized main/working refs and build the APK. Independently verify source revision, version, signing certificate, ABI and libraries.
- [ ] Record the artifact and device-validation limit. The phone is currently absent from USB; do not send a real authentication/SMS request to test the overlay.

Evidence: official upstream commit API confirms f2908b14 is `update to 12.10.6 (7112)`. The original native modal failed with `shown confirmation follows the hidden button after keyboard hide`. Patched hooks pass 488 states and compile against Android SDK 37.0. Three actual source-preparation tests pass, including preservation of a dirty old working tree and omission of its private config. The fresh complete checkout at `build/android-12106-phone-confirmation-current` is detached at f2908b14 with version 12.10.6/code 7112 and all 194 patches; no credentials are injected. A cached newer pin was not advertised by the older local clone, so preparation explicitly fetches the public committed pin into the new independent clone. The old vendor HEAD remains c8480176 (12.10.4) with its local modifications intact. The old apply-overlay path now refuses that mismatch before any reset/clean. Banner order/animation/rendering and hot-path cache checks also pass on the complete fresh source.
