# iOS Native Integration Build Implementation Plan

> Execute inline; no subagent/executing-plans skill is available in this workspace.

**Goal:** Compile the Nebula settings adapter together with actual pinned Telegram iOS modules for the arm64 simulator, without signing or release credentials.
**Architecture:** An opt-in CI workflow prepares a fresh upstream checkout, initializes pinned submodules and applies the overlay/ordered patches. A Bazel integration target depends on SettingsUI, PeerInfoScreen and ChatListFilterTabContainerNode. The upstream build configuration classes supply native compiler flags; compile-only fixture values cannot produce a distributable build.
**Tech Stack:** Python unittest, Git, pinned Telegram build-system, Bazel 8.4.2, Xcode 26.2, GitHub macOS runner.

## Files / steps
- [x] Create platform/ios/overlay/submodules/NebulaIntegrationChecks/BUILD as an `ios_build_test` (Apple platform transition) for the three real module targets (not a mocked Swift interface).
- [x] Create platform/ios/tools/native-build.py: prepare into a nonexistent destination only; fetch exact gitlink; initialize recursive submodules; apply sorted patches then collision-checked overlay. Record pin and input hashes. Never reset/clean a user/vendor checkout.
- [x] Build subcommand verifies preparation, macOS/arm64/Xcode pin, uses upstream BazelLocation and BazelCommandLine with debug_sim_arm64 and provisioning disabled. Generate a compile-only build_configuration repository with no real API keys/team/profiles. Emit a success report only after Bazel exits successfully.
- [x] Create platform/ios/tools/test_native_build.py to test path/collision refusal, source fingerprinting, deterministic overlay and fixture values; execute on Windows and in bootstrap CI.
- [x] Add manually dispatched .github/workflows/ios-native.yml: verify Xcode 26.2 availability before fetching large dependencies, prepare RUNNER_TEMP fresh tree, build module aggregate with bounded parallelism, upload logs/result manifest even on failure. No IPA/TestFlight publishing or signing secrets.
- [x] Run existing bootstrap checks and new tests, publish only owned iOS changes, dispatch native workflow, inspect first meaningful result and fix actionable build errors. Report actual compilation status separately from already passing standalone checks.

## Acceptance boundaries
- No settings catalog promotion until full native build and device acceptance.
- Module build checks native API compatibility but does not launch Telegram or exercise Files/iCloud UI.
- If the runner lacks the pinned toolchain/resources or upstream dependencies cannot be fetched, preserve logs and report the concrete limitation; do not silently override versions or pretend an IPA exists.

## Evidence / remaining gate

- Windows: six native preparation tests; pinned patch/overlay check passes.
- macOS bootstrap run [34341127731](https://github.com/BBGGVP5/NebulaGram/actions/runs/34341127731): passed, including 11 Swift tests and the six preparation tests.
- Native preparation and exact Xcode 26.2 preflight passed. Initial checks exposed
  a macOS /var canonicalization assertion and then a missing Apple platform
  transition in the compile target. Replaced the plain filegroup with the pinned
  rules_apple ios_build_test; CPU flags alone were not sufficient under this
  toolchain. No upstream version override or fake UIKit interfaces used.
- Native run [34341129456](https://github.com/BBGGVP5/NebulaGram/actions/runs/34341129456)
  at ab32b65 passed: **native module compilation confirmed**, 1591 build actions,
  1690 seconds. This is not full app packaging or device acceptance.
- Native compiler log inspected: module gate passed. Full app/signing/device and
  notification delivery gates remain separate.
