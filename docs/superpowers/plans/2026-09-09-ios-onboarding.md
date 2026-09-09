# Nebula iOS onboarding and unsigned-build correction

**Goal:** Apply Nebula presentation to welcome/phone/code/2FA while retaining Telegram authentication, and correct the failed unsigned IPA configuration. Initially no push/dispatch; the user later explicitly requested IPA dispatch after these changes.

**Architecture:** New UIKit-only presentation/intro views in the AuthorizationUI overlay; a small ordered patch adapts the pinned native nodes. No changes to TelegramCore authentication, code delivery, passkeys or password recovery. A separately scoped rules_apple patch only skips provisioning embedding for explicit unsigned builds in a fresh build tree.

**Tech stack:** Swift/UIKit, AsyncDisplayKit, pinned Telegram AuthorizationUI, Python regression checks.

### Presentation
- [x] Add `NebulaAuthPresentation.swift`: theme-supplied colors, shared Android comet geometry, non-interactive gradient background and glass field card, RU/EN copy, phone/code/lock artwork.
- [x] Add `NebulaWelcomeController.swift`: scrollable branded headline, real start callback, RU/EN selection, safe-area actions and accessibility sizing. Route to the real optional NebulaLink connection screen before login.
- [x] Add `NebulaAuthNodes.swift`: thin native-node adapters. Add `0002-nebula-onboarding.patch` for splash adapter, title treatment, glass field surfaces and artwork laid out with native keyboard-aware layout. Preserve all credential fields and callbacks, code delivery instructions and recovery actions.
- [x] Extend bootstrap checker with `check_onboarding.py` to verify actual patch integration, unchanged authentication handlers, native accessibility/input contracts and UIKit SDK typecheck on the next requested macOS check. Add AuthorizationUI to the native compile target.

### Unsigned build correction
- [x] Reproduce missing provisioning profile guard in the exact pinned rules_apple implementation. Add a separate patch under `platform/ios/build-patches/`, applied only by `ipa-build.py` to a verified fresh nested checkout.
- [x] Require both `disable_legacy_signing` and `nebula_unsigned_ipa` to skip profile embedding. Preserve signed builds' original failure for missing profiles; never fabricate a profile or disable NSE.
- [x] Add tests for patch scope, apply/idempotence, flag gating and source revision validation. Run 7+ IPA-tooling tests, native tests, overlay and ordered patch checks locally.

### Verification/delivery
- [x] Document current onboarding/locale scope and unchanged auth/push limitations in iOS build notes and HOOKS.md. Mark full native compilation and device visual acceptance pending.
- [x] Review exact diff, keep unrelated Android/design changes. The latest user request authorizes IPA submission; dispatch after local verification, not before.

Failed run evidence: 34345902543, `//Telegram:IntentsExtension`, `partials/provisioning_profile.bzl:41`: device build requires a profile even with `disable_legacy_signing`. Compilation did not start; this is a packaging-rule configuration error, not a Swift failure.


## Expanded NebulaLink scope and evidence

User confirmed proxy mode: use the same Go `bind/mobile`/Xray core, not a new VPN/Network Extension. Added native import/connect/disconnect, selection/pagination, refresh and explicit proxy URL probe; welcome and settings entry; protected state, Keychain proxy lease, native settings ownership guard and ordered proxy transactions; active-app reconnect. Fresh-tree preparation builds both XCFramework slices from pinned module inputs.

Verified locally: 3 ordered patches/13 native paths apply; native handler/privacy guards pass; 6 native-tooling and 8 IPA-tooling tests pass; 6 changed/new Swift overlay files parse with a lightweight Swift grammar (not SDK typechecking); all core Go packages pass and the actual mobile binding subscription→SOCKS5→VLESS→HTTP/stop test passes. The unsigned-profile patch applies to SHA-verified pinned rules_apple and covers six explicit iOS bundle guards. The generated binding ABI was inspected from the existing successful core artifact 33947966610; it is not used as a substitute build input.

Remaining gates: macOS SDK/full module compile, physical-device onboarding layouts, actual Swift/Telegram routing, user-certificate installation, background and APNs delivery. These are not marked verified by local checks. Advanced Android-only provider/settings UI is not fully ported.
