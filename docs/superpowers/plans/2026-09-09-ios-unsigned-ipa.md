# iOS unsigned IPA implementation plan

> Execute task-by-task in this task. No separate agent/task is required.

**Goal:** Dispatch an actual arm64 iPhone/iPad application build, not another simulator/module check.

**Architecture:** Reuse the fresh pinned Telegram checkout and overlay integrity checks. A separate device-build tool uses official Make/Bazel configuration, repository Telegram API secrets, and the pinned rules_apple `disable_legacy_signing` feature. Upload only a validated IPA and sanitized manifest. Keep existing compile-only configuration unusable for login and unchanged.

**Tech stack:** Python 3.11, Xcode 26.2, pinned Bazel 8.4.2/rules_apple, GitHub Actions macOS arm64.

### Task 1: Device build and archive checks
- [x] Extract reusable prepared-tree validation in `platform/ios/tools/native-build.py`.
- [x] Add failing tests in `platform/ios/tools/test_ipa_build.py` for credentials, archive layout, device Mach-O and required notification extension.
- [x] Implement `platform/ios/tools/ipa-build.py`: release_arm64, actual application target, no Apple credentials/signing, validate output before publishing, SHA-256 manifest without secrets.
- [x] Run device tooling tests and existing native preparation tests locally.

### Task 2: CI and dispatch
- [x] Add manual `.github/workflows/ios-ipa.yml`, fail early on missing API secrets/toolchain, prepare fresh tree, build with bounded resources, upload successful artifacts only.
- [x] Add IPA tooling tests to iOS bootstrap CI and document signing/device/push limitations in `platform/ios/BUILD-IPA.md`.
- [ ] Run overlay/bootstrap/static checks, inspect diff and commit only task files, push and dispatch workflow.
- [ ] Confirm real run ID/status and report build link. Do not call a queued/running build or unsigned IPA device-tested; APNs delivery remains a separate gate.

Local evidence: 7 IPA-tooling tests and 6 native-preparation tests pass. Overlay generation check and bootstrap patch/hook check pass against the committed pin. YAML parsed successfully. These are tooling checks, not device compilation/installation evidence.
