# Android reference chat header implementation plan

> Execute inline in this session. The optional Superpowers execution skills are unavailable; no delegation is needed.

**Goal:** Put Edit on the left, Chats in the centre, and Add Story / New Message on the right, with a compact story stack beside the title on collapse.

**Architecture:** Keep Telegram's ActionBar, story recycler, recording and navigation actions. Apply a new ordered native patch to the committed 12.10.6 pin in an owned scratch tree. A small pure geometry helper supplies one shared title/story slot; existing settings retain their explicit choices.

**Tech stack:** Android Java, native liquid glass drawables, ordered Git patches, Python/JDK regression checks, GitHub Actions APK build.

### 1. Shared title and story geometry

Files: `NebulaHomeTitleGeometry.java`, `NebulaDialogsTitle.java`, `NebulaAppearance.java` under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`; native `ActionBar.java` and `Stories/DialogStoriesCell.java` in `patches/android/0201-reference-chat-list-header.patch`.

- [ ] Add executable cases for 320–900dp widths, fractional densities, 0–3 stories, optional toolbar actions and continuous collapse; require the full ring footprint to stay outside the title and controls.
- [ ] Calculate `prefix = count == 0 ? 0 : footprint + step * (count - 1) + gap` with footprint 34dp, step 16dp and gap 8dp. Centre `titleWidth + prefix * collapse` in the actual free slot, reducing visible stories when space is insufficient.
- [ ] Use this result for both native title translation and story recycler placement / hit area. Keep the expanded row and native story rings unchanged.
- [ ] Default the Chats title on, preserve stored choices, and prevent the folder name from overriding Chats when that preference is on.

### 2. Native controls and material

Files: native `DialogsActivity.java`, `ActionBar.java` in patch 0201; two vector toolbar icons in the Android overlay.

- [ ] Add two 44dp menu actions: `openStoriesRecorder()` and `openWriteContacts()` (with the native frozen-account check). Retain the configured AI shortcut and optional NebulaLink control.
- [ ] Keep Edit's existing chat selection path; make long press open the existing home options so those commands remain accessible.
- [ ] Hide/disable these actions during search, selection, folder editing and right-side navigation using existing animation factors and appearance settings.
- [ ] Restore a separate material behind Edit and the right action group. Exclude the main home toolbar from the parent's whole-header rounded glass; keep community behaviour separate.
- [ ] Use normal themed foreground colours and retain the existing folder strip, passcode and downloads controls.

### 3. Verification and delivery

Files: `scripts/check-reference-home-header.py`, existing home title / header guards, `.github/workflows/android.yml`, this plan, `platform/ios/PARITY.md`.

- [ ] Run the new production geometry check and existing native title / leading-control / folder animation checks against the pinned patched scratch tree.
- [ ] Apply all patches in order to the current pin and run relevant Android UI regression checks. Do not edit the dirty vendor checkout.
- [ ] Commit exact implementation paths, push the authorized refs, and verify the resulting APK's source revision, version and signature.
- [ ] Download and verify the already successful iOS IPA 110, update completed parity/build records, and report device testing accurately.

Commands: `python scripts/check-reference-home-header.py build/android-home-header-20261013`, `python scripts/check-home-title.py build/android-home-header-20261013`, `python scripts/check-home-header-controls.py build/android-home-header-20261013`. Expected: all geometry/interaction checks pass. Physical appearance still requires a device; absence of a device must be recorded rather than treated as a visual pass.
