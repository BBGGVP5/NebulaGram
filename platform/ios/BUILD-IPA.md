# Development IPA for user signing

Run **iOS unsigned IPA** (`ios-ipa.yml`) manually on the desired committed branch.
This is a full `//Telegram:Telegram` **release_arm64** device application, not the
compile-only simulator integration target. It contains the current iOS Nebula
settings/store/transfer/folder badge integration, Nebula welcome/phone/code/2FA
presentation, and the NebulaLink proxy bridge. Not all Android features are ported.
Upstream launcher artwork/name is still pending separate branding work.

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
`disable_legacy_signing` feature as an early exit before signing. Run 34345902543
revealed a separate unconditional provisioning-embedding guard in `ios_rules.bzl`.
The device tool now applies a SHA/pin-checked patch **only in the fresh build tree**:
profile embedding is skipped only when both `nebula_unsigned_ipa` and
`disable_legacy_signing` are explicitly set. Signed builds retain their guards.
The build combines these with upstream `disableProvisioningProfiles`. Do not substitute
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

## NebulaLink and onboarding

The application builds `NebulaLink.xcframework` from this checkout's `bind/mobile`
and Xray runtime using the x/mobile version in `bind/go.mod`, not `@latest` or a
downloaded runtime. The generated headers are checked against `NebulalinkCall`.
Go and framework file digests join the fresh-tree integrity receipt.

Welcome has RU/EN selection, the same comet geometry as Android, and an optional
NebulaLink step before Telegram's unchanged localization/authentication callbacks.
Phone/code/2FA use native fields, transport-dependent code instructions, autofill,
country selection, passkey and recovery handlers. Only presentation is adapted.
Recovery, registration and email-specific screens beyond those nodes retain upstream
presentation. Device/keyboard/VoiceOver acceptance remains pending.

NebulaLink is an **in-process loopback SOCKS5 proxy**, not a system VPN and not a
Network Extension. The native client supports subscription/key import, server
selection with pagination, connect/disconnect, refresh and explicit URL probes.
It uses public Telegram proxy preferences; accounts/sessions are not exported.
Only an owned proxy lease is restored on stop/relaunch, preserving unrelated proxy
choices. Previous proxy credentials are kept in device-only Keychain, not preferences
export. Core subscription state is in protected, backup-excluded app storage.

The last requested connection is resumed when the main application becomes active.
This does not grant background execution: when iOS suspends the app, its Go proxy
is suspended too. Notification extensions do not share that in-process listener.
APNs delivery remains separate. The UI says the local proxy is enabled, not that
end-to-end connectivity or notifications have been verified. The explicit connection
test requests `https://telegram.org` through the running proxy.

All protocol support comes from the actual linked engines (currently Xray), exactly
as in the Android binding; the UI does not claim to enable an unlinked engine.
Advanced Android-only NebulaLink settings/provider views remain separate port work.

Local end-to-end core evidence: `go test ./mobile -run TestMobileSubscriptionSOCKSRouteAndStop -count=1 -v`
in `bind` successfully imports a local subscription and transfers HTTP through the
real SOCKS5 → VLESS/Xray → local HTTP chain, then verifies listener shutdown. This
proves the shared binding/runtime path, **not** Swift routing or iPhone background
behavior. macOS SDK/native build and signed-device verification are still required.
