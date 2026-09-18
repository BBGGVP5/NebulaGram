# NebulaGram iOS Nimbo Ping rebuild plan

**Goal:** Build and verify a real unsigned arm64 iOS IPA from Jason's selectively reviewed Nimbo Ping commit, including freshly rebuilt Go bindings, without changing Nimbo or publishing a release.

**Architecture:** Reuse .github/workflows/ios-ipa.yml. It prepares a fresh Telegram iOS tree, applies committed patches/overlay, invokes prepare-link.py to build pinned gomobile/gobind and NebulaLink.xcframework from checkout bind/core/runtime, then builds //Telegram:Telegram release_arm64 with Xcode26.2. Existing archive validation checks device Mach-O, bundle IDs, notification extension and absent provisioning profiles.

**Scope:** Pipeline inspection, existing workflow dispatch, bounded foreground monitoring, artifact download/verification, this plan and a rebuild report. Jason owns all selective source integration and Android. Preserve unrelated dirty files; no reset/clean, public release, new credentials, or Nimbo changes/dispatches.

- [x] Read ios-ipa/ios-native workflows, BUILD-IPA.md, native-build.py, prepare-link.py and ipa-build.py. Confirm no reuse of old framework: fresh tree and explicit refusal to overwrite existing framework; source/framework hashes checked before application build.
- [x] Inspect prior successful IPA34763450207/e4422990d21e637a291b70c48fcf990fd6edb928 (85minute build, real arm64 artifact). Existing unsigned path needs repository Telegram API configuration, not Apple signing credentials. Do not display any secret values.
- [ ] Run python platform/ios/tools/test_native_build.py and test_ipa_build.py locally; these are tool contracts, not an iPhone build.
- [ ] Receive exact reviewed/pushed sourceSHA and branch from Jason; verify git commit contents include Go probe and Swift UI/provenance integration and inspect existing workflow at that commit. Do not dispatch a dirty working tree or an unrelated branch tip.
- [ ] Dispatch gh workflow run ios-ipa.yml --repo BBGGVP5/NebulaGram --ref reviewedRef; record returned runURL and verify headSha equals reviewedSHA. No second workflow is needed for compile-only native modules; IPA compiles actual app.
- [ ] Monitor via bounded foreground gh run watch, no automations. Inspect actionable failures without logging API configuration. Coordinate source corrections with Jason; no public publication.
- [ ] Download only validated NebulaGram-unsigned-ipa artifact to a new build/ios-nimbo-ping output directory. Verify manifest sourceSHA/buildnumber/hash; inspect IPA plist version, app+NSE arm64 iOS Mach-O and absent profiles. Verify fresh bind/core/runtime build in CI evidence and inspect linked native ping strings/build metadata where available.
- [ ] Save sanitized report with exact artifact path, SHA256, version/build/architecture, included ping evidence, runURL and explicit signing/device/APNs limitations; send main/Jason handoff.

Signing limitations: unsigned app and embedded extensions/frameworks require user re-signing with valid matching bundle/team/AppGroup provisioning. Existing CI does not need Apple certificates, but unsigned IPA cannot be directly installed. Physical-device login/proxy suspension/APNs and all-per-server live network behavior are not proved by compilation alone.
