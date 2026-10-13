# Android reference chat header implementation plan

> Execute inline in this session. The optional Superpowers execution skills are unavailable; no delegation is needed.

**Goal:** Put Edit on the left, Chats in the centre, and Add Story / New Message on the right, with a compact story stack beside the title on collapse.

**Architecture:** Keep Telegram's ActionBar, story recycler, recording and navigation actions. Apply a new ordered native patch to the committed 12.10.6 pin in an owned scratch tree. A small pure geometry helper supplies one shared title/story slot; existing settings retain their explicit choices.

**Tech stack:** Android Java, native liquid glass drawables, ordered Git patches, Python/JDK regression checks, GitHub Actions APK build.

### 1. Shared title and story geometry

Files: `NebulaHomeTitleGeometry.java`, `NebulaDialogsTitle.java`, `NebulaAppearance.java` under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`; native `ActionBar.java` and `Stories/DialogStoriesCell.java` in `patches/android/0201-reference-chat-list-header.patch`.

- [x] Add executable cases for 320–900dp widths, fractional densities, 0–3 stories, optional toolbar actions and continuous collapse; require the full ring footprint to stay outside the title and controls.
- [x] Calculate `prefix = count == 0 ? 0 : footprint + step * (count - 1) + gap` with footprint 34dp, step 16dp and gap 8dp. Centre `titleWidth + prefix * collapse` in the actual free slot, reducing visible stories when space is insufficient.
- [x] Use this result for both native title translation and story recycler placement / hit area. Keep the expanded row and native story rings unchanged.
- [x] Default the Chats title on, preserve stored choices, and prevent the folder name from overriding Chats when that preference is on.

### 2. Native controls and material

Files: native `DialogsActivity.java`, `ActionBar.java` in patch 0201; two vector toolbar icons in the Android overlay.

- [x] Add two 44dp menu actions: `openStoriesRecorder()` and `openWriteContacts()` (with the native frozen-account check). Retain the configured AI shortcut and optional NebulaLink control.
- [x] Keep Edit's existing chat selection path; make long press open the existing home options so those commands remain accessible.
- [x] Hide/disable these actions during search, selection, folder editing and right-side navigation using existing animation factors and appearance settings.
- [x] Restore a separate material behind Edit and the right action group. Exclude the main home toolbar from the parent's whole-header rounded glass; keep community behaviour separate.
- [x] Use normal themed foreground colours and retain the existing folder strip, passcode and downloads controls.

### 3. Verification and delivery

Files: `scripts/check-reference-home-header.py`, existing home title / header guards, `.github/workflows/android.yml`, this plan, `platform/ios/PARITY.md`.

- [x] Run the new production geometry check and existing native title / leading-control / folder animation checks against the pinned patched scratch tree.
- [x] Apply all patches in order to the current pin and run relevant Android UI regression checks. Do not edit the dirty vendor checkout.
- [x] Commit exact implementation paths, push the authorized refs, and verify the resulting APK's source revision, version and signature.
- [x] Download and verify the already successful iOS IPA 110, update completed parity/build records, and report device testing accurately.

Commands: `python scripts/check-reference-home-header.py build/android-home-header-20261013`, `python scripts/check-home-title.py build/android-home-header-20261013`, `python scripts/check-home-header-controls.py build/android-home-header-20261013`. Expected: all geometry/interaction checks pass. Physical appearance still requires a device; absence of a device must be recorded rather than treated as a visual pass.

Local evidence: all 196 patches apply to the committed 12.10.6 pin; patch 0201 also passes strict whitespace/application checks. 117,120 production shared-slot cases, 61 extracted native collapse frames (no repeated measurement), 40 extracted native toolbar states and existing title/folder/material/leading-control checks pass. All 102 runnable Android CI interface checks pass locally; the account-slot C++ check passes contract/syntax locally. CI 38341281598 passes all 103 interface checks, including executable native singleton identities/isolation, and the full application build. The first CI attempt 38340421663 stopped in the old input fixture, which lacked the new header-refresh method; eb3c421 repairs the fixture and checks that lower-control updates also refresh the header. Vector resources compile with Android aapt2.

Final Android APK **1000506**, full build **38341281598** at **eb3c42125b0878d67b47217ccb4ac8f788f783f6**, succeeded. Independently downloaded and verified: `app.nebulagram.messenger`, 1.0.0, arm64 Telegram/NebulaLink libraries, expected signing certificate `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`, **60,064,978 bytes**, SHA-256 `3153358aaa6e9a47a0157813e95c8d02b3c236c91ca6b62ca5a12efaaf8e3fe9`. ZIP integrity, both new toolbar resources and the before-send voice setting marker are verified. Local file: `build/artifact-38341281598/NebulaGram-1.0.0-TG-12.10.6-b1000506-arm64-v8a.apk`; receipt: `verified-apk.json` in that directory. The runtime implementation is ab81898; eb3c421 only repairs its test fixture. Settings contract 38340421629 passed.

Original iOS IPA 110 / run 38297460672 at 3f1f07f is independently verified: 88,036,100 bytes, SHA-256 `9e72d6ce2393af0f43ebecfc8b86c4737402f38a7571d1f35c332f9937f704ee`, arm64 device application and one notification extension. Bootstrap 38297459032 passed 159 XCTest / 5394 geometry cases and real CAF/AAC fragment export. Later bootstrap 38340421830 also passed; Android-only inputs do not require another full IPA. User signing and physical iPhone/Android appearance/recording acceptance remain unverified. ADB has no connected device.
