# Official source update and fork feature audit

> **For agentic workers:** Execute inline in this session; preserve the dirty vendor checkout. No delegation or execution-choice approval is needed for the authorized update.

**Goal:** Update to the latest published official Android/iOS source on their production branches, preserve NebulaGram behavior, and identify useful missing fork features with evidence.

**Architecture:** Keep the existing pinned upstream + ordered patches + overlay architecture. Prepare and validate a disposable Android tree before updating the gitlink; never reset or stage the local vendor tree. Research features separately from implementation so the source migration remains reviewable.

**Tech Stack:** Java/Kotlin/C++, Gradle/Android SDK, Swift/UIKit/Bazel, Python regression checks, GitHub Actions.

## Source baseline (checked 2026-10-15)

- Android current pin `f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6); official master `f32ffeec763f0bdbf4bec25b52d654f77bff733d` (13.0.2 / 7175).
- iOS current pin and official master both `f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838` (13.0); no production source bump needed.

## Execution

- [x] Query official branch heads through GitHub and inspect local pins and source preparation.
- [ ] Run `python scripts/check-upstream-series.py android --tree build/android-upstream-1302-20261015 --keep-temp-on-error`; inspect the first incompatible patch and its upstream method before adapting it.
- [ ] Adapt only incompatible `patches/android/*.patch` and affected overlay calls. Preserve new official behavior and all recent home-title/camera/recording hooks. Replay the complete ordered series from a fresh baseline.
- [ ] Change the explicit version guard in `scripts/prepare-android-tree.py` and fixtures in `scripts/test-prepare-android-tree.py` to 13.0.2 / 7175; test rejection of the old baseline and source preservation.
- [ ] Check new native/Gradle dependency requirements; update `.github/workflows/android.yml` only where necessary. Add the vendor gitlink to its push paths so future source-only bumps build.
- [ ] Run the Android workflow regression commands against the prepared tree. Compile the complete app in CI; inspect failures and repair actual API/build incompatibilities.
- [ ] Update the Android gitlink using its exact immutable SHA without staging the dirty vendor working tree. Correct current-version documentation in `docs/UPSTREAM.md`, `docs/USER-CHANGES.md`, and `platform/ios/README.md`.
- [ ] Research official fork repositories/documentation (Forkgram, Nekogram, Cherrygram, exteraGram, Nicegram); compare candidate features against both native overlays, patch series, and settings. Save `docs/FORK-FEATURES-2026-10-15.md` with source links, missing/partial status, platform effort and concrete integration points.
- [ ] Commit exact changed paths, push authorized branches, verify resulting APK metadata/signature and record build status. Physical acceptance remains separate: the latest user report says the phone is unavailable.

## Acceptance

The published build must use the new source pin, retain NebulaGram's signing/package identity, and pass the existing behavior checks including interrupted header fades and pre-send transcription. Research recommendations must exclude features already implemented and distinguish client-side additions from server-controlled capabilities.
