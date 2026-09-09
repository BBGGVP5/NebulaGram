# Development IPA for user signing

Run **iOS unsigned IPA** (`ios-ipa.yml`) manually on the desired committed branch.
This is a full `//Telegram:Telegram` **release_arm64** device application, not the
compile-only simulator integration target. It contains the current iOS Nebula
settings/store/transfer/folder badge integration, not all Android features.
Most upstream branding, including the launcher artwork/name, is not yet ported.

## Inputs and reproducibility

- The committed `vendor/telegram-ios` gitlink, sorted iOS patches and overlay.
- Xcode 26.2 and Bazel from the pinned upstream `versions.json`; no version bypass.
- Existing repository secrets `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` are required.
  Empty/placeholder values fail before source preparation. Never use the compile-only
  `api_id=0` fixture for an installable build. These app API identifiers become part
  of the client binary by design; they are not user sessions or signing credentials.
- Bundle ID `app.nebulagram`, production APNs environment, build number from CI.
- No Apple credentials, profiles, paid-account enrollment or release publication.

The pinned rules_apple `apple/internal/codesigning_support.bzl` implements the
`disable_legacy_signing` feature as an early exit before signing/profile validation.
The build combines it with upstream `disableProvisioningProfiles`. Do not substitute
simulator architecture or pretend that disabling profiles provides a valid signature.
The optional Watch app is not embedded. The main app's Notification Service Extension
must be present; output checks reject simulator Mach-O binaries, wrong bundle IDs,
embedded profiles, missing NSE, duplicate and unsafe archive entries.

## Output and installation

On success, download **NebulaGram-unsigned-ipa**, containing:

- `NebulaGram-unsigned.ipa` (the native Bazel archive, not a renamed `.app` or library).
- `build-result.json` with source/upstream revisions, configuration, SHA-256 and explicit
  `requires_user_signing=true`, `device_tested=false`, `push_delivery_verified=false`.

There is no automatic installation or public release. A user must sign the app,
embedded extensions and frameworks with their own valid profiles/certificate.
The signing configuration must map the app and extension bundle identifiers, team
and shared App Group consistently; replacing a certificate alone is insufficient.
The unsigned build uses a placeholder team ID, **not** an entitlement to use any
real Apple team. Capabilities must be provisioned for the final user's identifiers.

For notifications follow [NOTIFICATIONS.md](NOTIFICATIONS.md), then run
`check-notification-signing.py` on the signed/extracted app on macOS. Even passing
that prerequisite checker does not verify Telegram's APNs provider configuration or
notification delivery. Physical-device login, background notifications, extension
decryption, transfer UI and upgrade compatibility remain acceptance tests.

CI uploads only a validated IPA and sanitized manifest. It does not upload API
configuration, provisioning data, certificates, full build directories or caches.
The preparation manifest is a shared source-integrity receipt; its compile-only
target metadata is not IPA evidence. Only the device `build-result.json` reports IPA
packaging, and it is written after the actual native build and archive checks pass.

## Tooling regression checks (not app build tests)

```sh
python platform/ios/tools/test_native_build.py
python platform/ios/tools/test_ipa_build.py
```

Native application compilation happens only in the macOS IPA workflow. Windows tests
use synthetic archives and must never be reported as a successful iPhone build.
