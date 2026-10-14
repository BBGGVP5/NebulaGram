# Inline camera choice and animated shield implementation plan

> Execute inline in this session. The user authorized implementation, iOS parity and builds; no delegation or separate approval is needed.

**Goal:** Match the supplied voice-to-text card for camera selection above the composer, dim the chat, and resolve a real animated Telegram shield.

**Architecture:** Keep native camera permission/recording callbacks. Android presents a transparent modal anchored to the live composer; iOS presents an over-full-screen UIKit controller with a live input-frame provider. Existing emoji renderers keep their own decoders, with native Telegram emoji search when the standard packs have no animated shield.

**Tech stack:** Android Java/View/Dialog, UIKit/Swift, Telegram image/sticker engines, existing CI widget fixtures.

## Android camera

Files: `NebulaRoundCamera.java`, `NebulaRoundCameraSheet.java`, new `NebulaRoundCameraPanelHost.java` in `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`; `patches/android/0209-inline-camera-card.patch`.

- [ ] Replace stacked phone rows/handle with compact icon/title/close and equal Front/Rear pill buttons using the AI panel palette; selected choice uses color only.
- [ ] Pass the real composer View at the recording hook. Position the card 8dp above its live screen bounds, cap height to available space and follow insets/layout. Use native modal dim amount 0.55; outside/back/close cancel, detached anchor cancels.
- [ ] Preserve facing preferences and ensure repeated choice starts recording once. Dismiss the modal before invoking the recorder callback.
- [ ] Extend `scripts/check-dialogs.py`, SDK typecheck and Android widget QA to cover the modal host, live anchor, dim configuration, both choices and cancellation. Run `python scripts/check-dialogs.py` and `python scripts/check-draft-voice-api.py`.

## Shield

Files: `NebulaAnimatedEmoji.java`, `scripts/check-settings-emoji.py`, `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaAnimatedSettingsEmoji.swift`.

- [ ] Retain animated RestrictedEmoji/native-pack priority; if no shield animation exists there, request native Telegram custom emoji search for the exact shield and select only animated TGS/WebM. Never substitute motion on a static bitmap.
- [ ] Cancel obsolete searches on emoji change/detach, ignore stale callbacks and cache successful Android documents per account. iOS uses the engine's cached search and disposable lifecycle.
- [ ] Execute delayed-result, mismatched/static-result, account, detach and reattach checks in `python scripts/check-settings-emoji.py`.

## iOS camera parity

Files: `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaRoundCameraController.swift`, `patches/ios/0107-inline-camera-card.patch`, bootstrap and camera widget QA tools.

- [ ] Replace pageSheet with overFullScreen presentation, a 0.55 black backdrop and compact rounded card. Supply native input-panel frame; follow it while visible, respecting safe area and Dynamic Type.
- [ ] Keep dismissal-before-recording, duplicate selection protection, backdrop/close/escape cancellation and no camera permission work inside the presentation controller.
- [ ] Update widget fixture to supply a real composer frame and verify card bottom above input, dim backdrop, visible choices, repeated choice and cancellation. Run all ordered-patch/bootstrap checks and render light/dark/large-font states in owned CI simulators.

## Deliver

- [ ] Review exact diff and run the existing recording/header regressions on the owned Android tree; preserve dirty vendor work.
- [ ] Commit only task paths and push authorized branches. Run Android APK, UIKit widget QA and native iOS/IPA workflows on the final source; inspect failures rather than assuming success.
- [ ] Record artifacts/source revision and checks. Prior IPA115 is verified at `build/ios-ipa-38433368728/verified-ipa.json`; it predates this new design.

Local checks: all 106 Android CI interface/regression commands pass on the owned 12.10.6 tree after patch0209. Actual compact content/modal host compile against SDK37; credential-free widget APK builds. Real controller callback checks cover repeated choice, close/back/outside and detached composer. Emoji executable checks resolve delayed animated shield results while skipping static/unrelated documents and rejecting obsolete callbacks; account-scoped reentry is cached. iOS bootstrap applies all107 patches to164 upstream paths; macOS SDK/module and visual checks follow in CI.
