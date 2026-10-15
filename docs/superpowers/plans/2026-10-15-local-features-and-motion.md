# Local features and motion implementation plan

> Execute inline under the existing request. Preserve the dirty vendor checkout and use the prepared Android 13.0.2 tree for native checks.

**Goal:** Make existing local translation usable offline, add personal reply templates without Business, and improve motion without increasing continuous rendering work.

**Architecture:** Keep translation in the ML Kit adapter, expose downloaded models in a separate settings screen, and retain explicit cloud/local selection. Reply templates are local and account scoped; choosing one inserts editable text and never sends a message. Reuse existing Android/iOS tool entry points. Improve existing glass animation lifecycle rather than introducing another renderer.

**Tech stack:** Java, ML Kit Translation 17.0.3, Swift/UIKit, existing Nebula forms, executable Java regression harnesses and GitHub Actions.

## Local translation

- [x] In `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaLocalTranslation.java`, retain a translator lease until its ML Kit task completes even if the caller cancels. Coalesce idle cleanup into one scheduled task. Distinguish download errors from translation errors; remove the incorrect alphabet-based Russian/English guess.
- [x] Add `NebulaTranslationModelsFragment.java` in the same directory. List supported languages and downloaded state using `RemoteModelManager.getDownloadedModels(TranslateRemoteModel.class)`; download a selected model on Wi-Fi and show completion/failure. Show English as built in; never instantiate or download an English TranslateRemoteModel. Avoid automatic downloads merely from opening the page.
- [x] Link the screen from `NebulaTranslationFragment.java`; filter local target choices in `NebulaTranslationSettings.java` with `TranslateLanguage.fromLanguageTag(code) != null`.
- [x] Extend `scripts/check-translation-api.py` for real ML Kit API compilation and add `scripts/check-local-translation-lifecycle.py` to exercise cancellation, shared leases, failed preparation and bounded cleanup scheduling.

## Personal reply templates

- [x] Add account-scoped storage and a list/editor under Android overlay UI and `platform/ios/overlay/submodules/SettingsUI/Sources/`. Store only user-created text, limit number/length, allow rename/delete and reuse. Start with plain text, accurately labelled; do not claim formatting preservation.
- [x] Add a templates action to `NebulaMessageToolsFragment.java` and `NebulaMessageToolsController.swift`. Allow saving source text; offer insertion only when the caller supplies a draft callback. Keep existing draft-staleness checks when applying.
- [x] Test account separation, limits, edit/delete, malformed storage and draft insertion behavior. Run existing tool and iOS source registration checks.

## Glass and motion

- [x] Inspect `NebulaTabLens.java` and native blur hot paths. Preserve current user preference for plain header buttons and rounded parent surfaces. Respect reduced-quality mode, avoid idle invalidations and avoid restarting identical press transitions.
- [x] Add behavioral checks for interrupted selection animation, drag takeover, detach/reset and reduced-quality mode; run existing glass edge, render hot-path and home-header checks.

## Delivery

- [x] Run targeted checks, overlay changes into the owned integrated tree, inspect actual Gradle compilation, and fix new upstream API incompatibilities if present.
- [x] Update `docs/USER-CHANGES.md` and the fork audit with implemented versus remaining features and the 13.0.2 source changelog. Commit exact paths, push authorized branches and verify produced artifacts. Do not claim device performance improvements measured without a device.

The broader fork research remains in `docs/FORK-FEATURES-2026-10-15.md`; history of message edits and extra local pins are separate larger projects. iOS already uses the latest published source pin; native Apple offline translation requires a separate implementation rather than pretending the Android ML Kit engine exists there.

## Validation so far

- Runtime Android commit: `bca44b79252aa3b24bc9302f0e770ed3fdf1d100`; iOS changes are already in its parent `cafe1d506311720672101db7eda7c4dac40412ab`. Both authorized branches are pushed.
- All 206 Android patches replay on the official 13.0.2 SHA. All 110 local interface/regression commands passed after redirecting the two source-reading fixtures to the owned clean 13.0.2 clone; the user's dirty vendor tree was not reset. Account-slot C++ validation was syntax-only on Windows; CI executes it on Linux.
- After the final motion cache correction, actual Android SDK helper compilation, lens lifecycle and render hot-path checks pass. The fixture now includes the real lens in SDK compilation so a popup-only overload cannot be mistaken for a generic View API again.
- iOS bootstrap run 38694671199 passed, including the executable Swift template-store test and parsing of the added controllers. This is not a full UIKit/native application typecheck. Full IPA run 38694717642 is separate.
- No device acceptance: the user last reported that the Android phone was unavailable. The test counts describe deterministic behavior checks, not measured on-device frame rates.

## Build acceptance

Android APK 1000547 is built, downloaded and signature/package/version verified; the exact receipt is in the [source update plan](2026-10-15-upstream-and-fork-audit.md#verified-android-delivery). The duplicate main build 1000548 is still running.

- [ ] Finish and inspect full iOS IPA run 38694717642; only bootstrap/Swift parsing and store behavior are confirmed so far.
- [ ] Device QA when hardware is available: rapid tab retargets, glass-off resizing, offline model preparation and translation, template selection/edit/return to draft.
