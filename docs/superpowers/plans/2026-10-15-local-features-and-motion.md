# Local features and motion implementation plan

> Execute inline under the existing request. Preserve the dirty vendor checkout and use the prepared Android 13.0.2 tree for native checks.

**Goal:** Make existing local translation usable offline, add personal reply templates without Business, and improve motion without increasing continuous rendering work.

**Architecture:** Keep translation in the ML Kit adapter, expose downloaded models in a separate settings screen, and retain explicit cloud/local selection. Reply templates are local and account scoped; choosing one inserts editable text and never sends a message. Reuse existing Android/iOS tool entry points. Improve existing glass animation lifecycle rather than introducing another renderer.

**Tech stack:** Java, ML Kit Translation 17.0.3, Swift/UIKit, existing Nebula forms, executable Java regression harnesses and GitHub Actions.

## Local translation

- [ ] In `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaLocalTranslation.java`, retain a translator lease until its ML Kit task completes even if the caller cancels. Coalesce idle cleanup into one scheduled task. Distinguish download errors from translation errors; remove the incorrect alphabet-based Russian/English guess.
- [ ] Add `NebulaTranslationModelsFragment.java` in the same directory. List supported languages and downloaded state using `RemoteModelManager.getDownloadedModels(TranslateRemoteModel.class)`; download a selected model on Wi-Fi and show completion/failure. Show English as built in; never instantiate or download an English TranslateRemoteModel. Avoid automatic downloads merely from opening the page.
- [ ] Link the screen from `NebulaTranslationFragment.java`; filter local target choices in `NebulaTranslationSettings.java` with `TranslateLanguage.fromLanguageTag(code) != null`.
- [ ] Extend `scripts/check-translation-api.py` for real ML Kit API compilation and add `scripts/check-local-translation-lifecycle.py` to exercise cancellation, shared leases, failed preparation and bounded cleanup scheduling.

## Personal reply templates

- [ ] Add account-scoped storage and a list/editor under Android overlay UI and `platform/ios/overlay/submodules/SettingsUI/Sources/`. Store only user-created text, limit number/length, allow rename/delete and reuse. Start with plain text, accurately labelled; do not claim formatting preservation.
- [ ] Add a templates action to `NebulaMessageToolsFragment.java` and `NebulaMessageToolsController.swift`. Allow saving source text; offer insertion only when the caller supplies a draft callback. Keep existing draft-staleness checks when applying.
- [ ] Test account separation, limits, edit/delete, malformed storage and draft insertion behavior. Run existing tool and iOS source registration checks.

## Glass and motion

- [ ] Inspect `NebulaTabLens.java` and native blur hot paths. Preserve current user preference for plain header buttons and rounded parent surfaces. Respect reduced-quality mode, avoid idle invalidations and avoid restarting identical press transitions.
- [ ] Add behavioral checks for interrupted selection animation, drag takeover, detach/reset and reduced-quality mode; run existing glass edge, render hot-path and home-header checks.

## Delivery

- [ ] Run targeted checks, overlay changes into the owned integrated tree, inspect actual Gradle compilation, and fix new upstream API incompatibilities if present.
- [ ] Update `docs/USER-CHANGES.md` and the fork audit with implemented versus remaining features and the 13.0.2 source changelog. Commit exact paths, push authorized branches and verify produced artifacts. Do not claim device performance improvements measured without a device.

The broader fork research remains in `docs/FORK-FEATURES-2026-10-15.md`; history of message edits and extra local pins are separate larger projects. iOS already uses the latest published source pin; native Apple offline translation requires a separate implementation rather than pretending the Android ML Kit engine exists there.
