# NebulaGram iOS notification delivery

Status: architecture and release gate, **not configured or delivery-tested**.
Audited against Telegram iOS `6ad963e5b62d354da79040f388ae2b9132fb17b8` on 2026-09-09.

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
