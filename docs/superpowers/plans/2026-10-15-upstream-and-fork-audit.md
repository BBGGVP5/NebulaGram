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
- [x] Run `python scripts/check-upstream-series.py android --tree build/android-upstream-1302-20261015 --keep-temp-on-error`; inspect the first incompatible patch and its upstream method before adapting it.
- [x] Adapt only incompatible `patches/android/*.patch` and affected overlay calls. Preserve new official behavior and all recent home-title/camera/recording hooks. Replay the complete ordered series from a fresh baseline.
- [x] Change the explicit version guard in `scripts/prepare-android-tree.py` and fixtures in `scripts/test-prepare-android-tree.py` to 13.0.2 / 7175; test rejection of the old baseline and source preservation.
- [x] Check new native/Gradle dependency requirements; update `.github/workflows/android.yml` only where necessary. Add the vendor gitlink to its push paths so future source-only bumps build.
- [x] Run the Android workflow regression commands against the prepared tree. Compile the complete app in CI; inspect failures and repair actual API/build incompatibilities.
- [x] Update the Android gitlink using its exact immutable SHA without staging the dirty vendor working tree. Correct current-version documentation in `docs/UPSTREAM.md`, `docs/USER-CHANGES.md`, and `platform/ios/README.md`.
- [x] Research official fork repositories/documentation (Forkgram, Nekogram, Cherrygram, exteraGram, Nicegram); compare candidate features against both native overlays, patch series, and settings. Save `docs/FORK-FEATURES-2026-10-15.md` with source links, missing/partial status, platform effort and concrete integration points.
- [x] Commit exact changed paths, push authorized branches, verify resulting APK metadata/signature and record build status. Physical acceptance remains separate: the latest user report says the phone is unavailable.

## Acceptance

The published build must use the new source pin, retain NebulaGram's signing/package identity, and pass the existing behavior checks including interrupted header fades and pre-send transcription. Research recommendations must exclude features already implemented and distinguish client-side additions from server-controlled capabilities.

## First full build result

Run 38691766619 passed the regression suite and reached Java compilation. Telegram 13.0.2 removed `InstantCameraViewBase.isUsingCamera2Implementation` and `setUseCamera2Implementation`; the overlay camera screen now uses `SharedSettings.roundVideoCamera2Enabled` and the same AppGlobalConfig fallback as the native factory. A new full build is required.

## Verified Android delivery

- Full Android run [38696111687](https://github.com/BBGGVP5/NebulaGram/actions/runs/38696111687) succeeded for runtime commit `bca44b79252aa3b24bc9302f0e770ed3fdf1d100`. All 110 workflow regression checks, native/Java compilation, APK version and ABI checks passed.
- Downloaded artifact: `build/artifact-38696111687/NebulaGram-1.0.0-TG-13.0.2-b1000547-arm64-v8a.apk`, 62,280,675 bytes.
- Independently read package `app.nebulagram.messenger`, versionCode `1000547`, versionName `1.0.0`, minSDK 23, ABI `arm64-v8a`.
- SHA-256: `3c1be7930189b7609a24377543d3ba32b44e1b20baae60000f7e6ddc61ec9be1`.
- `apksigner verify --print-certs` exited 0. Certificate SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab` matches the prior builds. Detailed verification receipt is next to the APK in the ignored build directory.
- Duplicate main-branch build [1000548](https://github.com/BBGGVP5/NebulaGram/actions/runs/38696112595) was still running when this receipt was recorded; it uses the identical runtime commit.
- iOS already had the latest published 13.0 source. New template code passed bootstrap/Swift checks; full unsigned IPA [118](https://github.com/BBGGVP5/NebulaGram/actions/runs/38694717642) remains in progress. No new IPA is claimed as verified.
- Physical device acceptance is pending; the user reported the Android phone unavailable.


## iOS 118 receipt, completed later on 15 October

Run 38694717642 succeeded. Downloaded `build/ios-ipa-38694717642/NebulaGram-unsigned.ipa`, 91,376,788 bytes, SHA-256 `d03fef40e3e748eed5a2b378ea6eabb1a5138f3f9be9d73e9748b6dd54a9bc48`. The local checksum matches the CI build receipt. Info.plist confirms app.nebulagram / 13.0 / 118, minimum iOS 15.0 and the Speech usage description. Source is cafe1d5: this IPA predates the formatting toolbar and October 15 core refresh. It remains unsigned and has not been tested on an iPhone. IPA 120 is a separate full build for the newer changes.
