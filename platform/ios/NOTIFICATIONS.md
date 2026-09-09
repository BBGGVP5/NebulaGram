# NebulaGram iOS notification delivery

Status: architecture and release gate, **not configured or delivery-tested**.
Audited against Telegram iOS `6ad963e5b62d354da79040f388ae2b9132fb17b8` on 2026-09-09.

## Product requirement: user-certificate signing

**Уведомления должны работать при подписи NebulaGram сертификатом пользователя,
а не только при нашей подписи или установке через TestFlight.**

User-certificate signing is a required supported distribution scenario, not an
optional workaround. Status: **required, not implemented or verified**. Do not
mark notification delivery complete based only on our own signed build.

Implementation must account for the installed signing identity, bundle ID, APNs
environment and App Group access, and establish a matching provider delivery
configuration. A signing certificate alone is not proof that this path works.
Do not restrict delivery merely because the signing team is not ours. Where a
profile lacks required capabilities or provider setup is missing, show the
specific issue and remediation rather than reporting push as working. Do not
request users' signing private keys in chat or include them in settings exports.

Acceptance for this requirement:
- [ ] Install an IPA signed with a user's valid certificate/profile; document
      the signing method and required capabilities without retaining private keys.
- [ ] Verify actual message delivery in background and on the locked screen,
      notification content, taps and the correct account/chat on a physical iPhone.
- [ ] Verify notification delivery after re-signing/reinstalling, token changes
      and supported identity/bundle changes; re-establish matching registration.
- [ ] Verify incoming calls separately; message delivery does not prove VoIP.
- [ ] Test missing capabilities/configuration and expose an actionable diagnostic,
      not a false success state or a promise of support for every certificate.

## Delivery path

Keep Telegram's native pipeline: Telegram -> APNs -> NebulaGram / its Notification
Service Extension. No Nebula relay holding Telegram sessions or message contents.
A successful local build, user permission or an APNs token does NOT prove delivery.

Telegram documents configuring application APNs certificates on its side before
`account.registerDevice`. Confirm provisioning for our own API ID and bundle ID
with Telegram; do not assume an API ID alone enables push or that the Android
FCM configuration applies. Confirm the supported certificate upload/configuration
process in the actual app account before promising background notifications.

## Required release configuration (never put secrets in git or chat)

- Apple Developer team, stable own bundle ID, own Telegram API ID/configuration.
- Main app and extension identifiers/profiles; Push Notifications capability and
  signed `aps-environment` matching the APNs environment.
- App Group `group.<bundle-id>` shared by app and Notification Service Extension;
  validate the installed entitlements, not merely the source plist.
- APNs provider certificate configured for Telegram and our exact App ID. Keep
  signing/provider private keys in the appropriate protected credential storage;
  do not embed them in the app, settings JSON, CI logs or public artifacts.
- Certificate expiry/renewal ownership and a real production delivery check.
- TestFlight / production signing and development sandbox are separate test cases.
  Current upstream selects `appSandbox` using DEBUG. Verify it matches signed
  `aps-environment`; unusual debug/distribution profiles may not match.

## Native code already present upstream

- `submodules/TelegramUI/Sources/AppDelegate.swift`: APNs registration callbacks.
- `submodules/TelegramUI/Sources/SharedAccountContext.swift`:
  `updateNotificationTokensRegistration`, current/all-account registrations,
  unregistering excluded accounts, normal `.aps(encrypt: true)` and `.voip` tokens.
- `submodules/TelegramCore/Sources/TelegramEngine/AccountData/RegisterNotificationToken.swift`:
  APNs type 1, VoIP type 9, `appSandbox`, secret, other account IDs and muted chats.
- `Telegram/NotificationService/Sources/NotificationService.swift`: App Group data,
  notification payload decryption, rendering and time-expiry fallback.
- `Telegram/BUILD`: generated APS/App Group entitlements. Notification filtering
  is enabled only for the official bundle ID, not automatically for NebulaGram.

Important: the upstream registration helper returns true for most RPC errors
(other than TOKEN_WAS_INVALIDATED). A future diagnostics screen must NOT label
that Boolean as confirmed delivery. Record authorization, token availability,
actual registration result and last verified delivery as distinct states; redact
all tokens/payloads/account keys in diagnostics.

## Apple capabilities and behavior

Filtering notifications with `com.apple.developer.usernotifications.filtering`
requires Apple's approval. Do not spoof the official bundle ID or blindly add
that entitlement. For a faithful port, request it and verify the issued profile.
If unavailable, separately audit service-extension suppression/fallback paths:
ordinary push is not inherently impossible, but duplicate/stale/empty-alert
behavior must not be claimed equivalent without testing.

Use PushKit only for genuine incoming calls with the required CallKit handling,
not as a general message/background keep-alive workaround. Audit official-only
VoIP privileges before release. Do not promise polling, silent pushes, fake audio
or a persistent socket as guaranteed delivery when iOS suspends the process.
Focus, notification permissions and OS scheduling remain under the user's control.

## Acceptance checklist (all pending on a signed physical-device build)

- [ ] API/bundle/APNs provider configuration confirmed, no official credentials reused.
- [ ] Installed app + extension signatures, App Group access and environment match.
- [ ] Incoming text, media, mentions and muted chat behavior: foreground,
      background, locked phone; Wi-Fi/mobile switch, reconnect, low-power mode.
- [ ] Notification preview privacy with app/passcode lock; no raw encrypted or
      sensitive fallback payload displayed on service-extension timeout.
- [ ] Tap opens the correct account/chat/message; supported reply actions work.
- [ ] Multiple accounts, active-account-only setting, logout/relogin, token refresh,
      reinstall, messages already read elsewhere and duplicate prevention.
- [ ] Incoming/declined/missed calls through the supported PushKit/CallKit path.
- [ ] TestFlight production APNs and development sandbox both tested; force-quit
      behavior recorded separately, without promising silent background execution.
- [ ] Filtering approval or explicitly validated reduced-capability behavior.

## Sources

- [Telegram push setup and registration](https://core.telegram.org/api/push-updates)
- [Apple APNs registration](https://developer.apple.com/documentation/usernotifications/registering-your-app-with-apns)
- [Apple provider certificates](https://developer.apple.com/documentation/usernotifications/establishing-a-certificate-based-connection-to-apns)
- [Apple notification filtering entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.usernotifications.filtering)
- [Apple background execution limits](https://developer.apple.com/forums/thread/685525)
- [Apple PushKit/CallKit handling](https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit)

## Local user-signing prerequisite checker

On macOS, use the `.app` inside an already extracted IPA (the checker does not
extract archives, re-sign, upload profiles or ask for private keys):

```sh
python platform/ios/tools/check-notification-signing.py \
  --app /absolute/path/Payload/NebulaGram.app --expected-environment production
```

Supply the environment the build registers with Telegram: the current upstream
DEBUG build uses `development`, release uses `production`. The checker compares
it to the **signed** APS entitlement and embedded profile. It accepts user teams
and legacy App ID prefixes, checks signature integrity with macOS codesign,
profile expiry/permissions, App Group sharing and the actual notification service
extension. It reports fixed diagnostic codes instead of profile contents/UDIDs.

`prerequisites-pass` is only local configuration validation; it is NOT evidence
of provider setup, online certificate validity/revocation, device installability
or notification delivery. `delivery_verified` remains false. Missing filtering
is a warning requiring the separate fallback audit above, not blanket rejection
of a user's certificate. Stripped/App Store profiles require a different release
verification path; this command targets re-signed IPA artifacts with embedded
profiles. Physical-device/provider acceptance is still pending.

Tool reference: [Apple provisioning profile structure and signed entitlements](https://developer.apple.com/documentation/technotes/tn3125-inside-code-signing-provisioning-profiles).
