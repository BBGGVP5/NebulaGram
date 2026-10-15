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

## Final source and visual verification

### Shield follow-up after APK1000534

User confirmed the shield remains static on Android APK1000534. The native iOS13 emoji search implementation calls `messages.searchCustomEmoji` when its text query is absent; our Android shield code incorrectly always used `messages.searchStickers` with an empty query. Correct the Android emoji-only request, then resolve returned IDs through `AnimatedEmojiDrawable.getDocumentFetcher(account)` for native disk cache/deduplication. Retain exact shield/animated candidate filtering, receiver ownership, stale callback protection and reduced-motion policy. Do not replace the glyph with a synthetic rocking bitmap.

- [x] Add a regression adapter that rejects the empty keyword request and returns `TL_emojiList` IDs. Before the fix, `python scripts/check-settings-emoji.py` failed the request-contract assertion. The corrected production loader passes existing decoder/lifetime checks and new two-stage resolution checks.
- [x] Use `TL_messages_searchCustomEmoji.emoticon = "🛡️"`, `hash=0`; resolve a bounded set of IDs through the native document fetcher, skipping static/unrelated/missing documents, select one animated shield only and retain attachment/account guards.
- [x] Run existing emoji playback/cancel/cache tests plus SDK/native build. Request actual phone connection early; do not claim a live Telegram shield or physical animation test without it.
- [x] Commit exact task files, push authorized branches and rebuild Android. Inspect CI failure logs and verify the APK source/package/signature before delivery.

Follow-up source `dc91134a351328aa56d5451dced5fa51ac2711a0` is pushed to both authorized branches. APK1000536 run `38572301639` is compiling. Phone connection was provided by the user; installed version1000534 confirmed. Actual ad-block settings reproduced the static shield: five unobscured crops over roughly3seconds have zero changed frames (`build/shield-live-before-1000534/report.json`). The earlier sample was obscured by an unrelated popup and is not counted as animation evidence. Power saving is disabled; glass quality UI reads Automatic/full effect active. No settings were changed. New live verification remains pending the updated APK.

Runtime revision: `9747336cab74865a78170f415e91edb909eb74ff`, pushed to both authorized branches. Native header changes from patches0208/0106 are preserved.

Android widget QA `38564461638` passed. Actual production card and modal window rendered in dark/light/narrow font1.3 states; all images inspected. Card is above composer, native dim amount0.55, chosen facing uses color only. Moving the underlying input60dp moves the modal card60dp; repeated selection calls once, close/back/outside never select. Crash log empty. The first test at `38563336602` failed callback completion; observing the underlying composer's window as well as the modal window fixed tracking, confirmed by `38564205028` and the final run.

iOS widget QA `38564466506` passed. Actual UIKit controller rendered in dark/light/XXXL states, all images inspected. Card bottom is8pt above composer, backdrop alpha0.55, close and both choices visible. Moving input60pt moves card60pt; repeated activation selects once, closing and backdrop never select. On narrow screens with large fonts, buttons become vertical so "Фронтальная" fits whole. Normal layouts keep equal side-by-side pills.

Both macOS bootstrap runs `38564460005` and `38564455866` passed, including real UIKit SDK type checking at iOS15 and all107 ordered patches. Full native build `38564568776` succeeded with result `compiled`; APK1000534 (`38564459994`) succeeded and was verified. Unsigned IPA117 (`38564573555`) succeeded and is independently verified below. Retrieval/verification helper: ignored `build/fetch-inline-camera-artifacts.py`.

Initial shield search/cancellation/playback verification used deterministic Telegram API/decoder adapters. Live shield verification followed with APK1000536 below. Physical camera/ASR acceptance is not claimed. A first uncached emoji still needs Telegram connectivity; reduced-motion/system animation preferences remain respected.

### Shield smoothness follow-up

APK1000536 (`38572301639`, source `dc91134a351328aa56d5451dced5fa51ac2711a0`) passed CI/package/signature verification and was installed on the authorized Pixel9 ProXL. The actual Telegram shield loaded and animates; the user reports choppy motion. Asset-only log reports180 decoded TGS frames. Steady app rendering profile:592 frames,2 missed deadlines (0.34%), median6ms/95th10ms, no slow UI-thread frames. This does not indicate sustained UI-thread stalls.

The production ImageLoader treats filters <=90dp as small icons: it halves60fps TGS and caps decoding at160px unless `nolimit` is present. Our72/88dp page heroes hit that path. Use `_pcache_nolimit` for large TGS to retain the source frame rate and cache rasterized frames between loops. Keep compact icons and WebM policy, per-view playheads and reduced-motion/lifecycle guards.

- [x] Binding regression fails before the fix at72dp and passes after it for72/88/112dp, compact48dp and WebM. Existing async lookup/cache/visibility/focus/replay tests pass.
- [x] Rebuild, verify and install the fixed APK. Compare recorded shield movement and steady rendering on the same phone, including reentry; report actual measurements without calling mock results physical evidence.
- [x] Restore temporary asset-only log tag and remove exact task-owned phone recordings.

APK1000538 run `38578229684` succeeded, source `1362f09923c4d6eadf19bf110762ca2f85389503`; all106 interface/regression checks passed. Verified arm64 package/signature and new filter in DEX. SHA256 `ea58650ff88f70bf2ab0c6faec15988ce4e7917d117cdb33bf3498a23254faba`. Installed successfully on the authorized Pixel9ProXL/API37. Only settings navigation/diagnostics were used; no settings values were changed.

Eight-second shield recordings: APK536 had117 captured frames,103 visual changes, median change interval83.07ms. APK538 has479 captured frames,449 visual changes, median16.73ms; after closing/reopening the page it has479 frames,436 visual changes, median16.67ms. Crop covers only the public hero asset; native animation holds are included, so visual changes/second (12.91 before,56.46 after,54.8 on reentry) are not claimed as an exact decoder FPS benchmark. Actual hero crops inspected. New steady rendering profile:700 frames,3 missed deadlines (0.43%), median8ms/95th11ms/99th16ms. Asset-only logs confirm native custom-emoji lookup returned4 IDs and loaded an animated TGS shield with180 frames. Physical evidence: `build/shield-device-538.json` and cropped recordings `build/nebula-shield-fps-538/`, `build/nebula-shield-reentry-538/`.

### iOS package follow-up verified2026-10-15

IPA117 run `38564573555` succeeded at runtime source `9747336cab74865a78170f415e91edb909eb74ff`. Downloaded `build/ios-ipa-38564573555/NebulaGram-unsigned.ipa`;91,370,224 bytes, SHA256 `40badf976c3de9670a0a0ec44dc83c0afc796f56b7dab93f10b26b7101e0f764`. `inspect_archive` independently matches build-result metadata, validates the native arm64 app/package and bundle identity; ZIP integrity passes. Status is `packaged-for-user-signing`: this is an unsigned IPA requiring the user's signing step. No physical iPhone installation or acceptance is claimed. Receipt: `verified-ipa.json` in that directory. This package contains the earlier iOS camera/header port; subsequent Android-only renderer fixes do not change its iOS source.
