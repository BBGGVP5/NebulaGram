# iOS implementation inventory — 2026-09-09

This inventory distinguishes **wired source** from native build and device acceptance. A setting with a validated import format is not necessarily functional on iOS.

## Verification and delivery
- Android adaptive glass/native retention Java compilation and 33 regression scripts passed. All 78 ordered Android patches reproduce the native compile sources.
- Android run 34377867372 failed in dependency transport at sum.golang.org, before Java compilation. The build now uses bind/go.mod-pinned tools, matching Go version and bounded transport retries without disabling checksum validation. Run 34379577262 is the replacement; success/APK availability must be checked separately.
- iOS shared-store tests and bootstrap run 34378968444 passed at 76ce9ea. This includes real macOS XCTest for encrypted metadata persistence, custom emoji, retention policy, resource policy, account isolation and local-versus-transfer settings.
- Latest native UIKit/Telegram/widget/AppIntent compilation and physical-device checks remain pending. Do not claim full Android parity or a fix for the unverified startup black screen.

## Implemented in source outside the presentation catalog
- Native retained-message history, original text/media references, marker, muted styling, per-chat/account clearing. Incoming cached content only; copy protection respected. Secret/expiry retention separately opt-in.
- Archive lifetime choices: 1/7/30 days, bounded 500 entries; pruning on deletion updates (not a guaranteed background timer).
- Per-account chat exclusions stop new retention, without silently clearing old copies. Available in the retained-message menu.
- Archive protection routes to native **whole-app passcode / Face ID settings** with existing access authentication, since archive messages remain in ordinary chat history. No claim of a new separate biometrically encrypted message database.
- NebulaLink import/select/connect remains an in-app proxy, not a system VPN.
- Quick-actions widget contains only Settings/NebulaLink links, no account/chat contents, no scheduled background refresh. Commands open screens, do not silently connect or change servers. Requires correctly signed extension packaging and on-device discovery checks.
- Country row/auth artwork fixes; source/bootstrap acceptance is not visual device acceptance.

## Catalog consumer inventory

`Wired` means a native consumer exists, not that the latest IPA has passed device QA. `Pending` keys may survive valid imports but must not be exposed as functioning toggles. Material You is Android-specific.

| Key | Feature | iOS source status | Transfer v1 |
|---|---|---|---|
| `adaptive_chat_header` | chat.header | Pending native consumer | Yes |
| `avatar_round` | appearance.general | Pending native consumer | Yes |
| `bottom_bar` | navigation.bottom_bar | Pending native consumer | Yes |
| `bottom_bar_contacts` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `bottom_bar_order` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `bottom_bar_profile` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `bottom_bar_settings` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `center_home` | navigation.folders | Pending native consumer | Yes |
| `centered_chat_header` | chat.header | Pending native consumer | Yes |
| `compact_bottom_bar` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `custom_avatar_corners` | appearance.general | Pending native consumer | Local only |
| `disable_next_channel` | chat.messages | Pending native consumer | Yes |
| `floating_chat_header_v2` | chat.header | Pending native consumer | Yes |
| `folder_outline` | navigation.folders | Pending native consumer | Yes |
| `folder_style` | navigation.folders | Pending native consumer | Yes |
| `folder_title` | navigation.folders | Pending native consumer | Yes |
| `glass_blur` | appearance.glass | Pending native consumer | Local only |
| `glass_custom` | appearance.glass | Pending native consumer | Local only |
| `glass_haptic_strength` | appearance.glass | Pending native consumer | Local only |
| `glass_haptics` | appearance.glass | Pending native consumer | Local only |
| `glass_highlights` | appearance.glass | Pending native consumer | Yes |
| `glass_opacity` | appearance.glass | Pending native consumer | Local only |
| `glass_quality` | appearance.glass | Wired; native/device QA pending | Yes |
| `glass_refraction` | appearance.glass | Pending native consumer | Local only |
| `header_unread` | chat.header | Pending native consumer | Yes |
| `hide_all_chats` | navigation.folders | Pending native consumer | Yes |
| `hide_attach_camera` | chat.composer | Pending native consumer | Yes |
| `hide_dividers` | navigation.folders | Pending native consumer | Yes |
| `hide_home_camera` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `hide_home_compose` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `hide_premium_status` | appearance.general | Pending native consumer | Yes |
| `hide_search_field` | navigation.folders | Pending native consumer | Yes |
| `hide_send_as` | chat.composer | Pending native consumer | Yes |
| `hide_tab_counters` | navigation.folders | Wired; native/device QA pending | Yes |
| `icon_pack` | appearance.general | Pending native consumer | Yes |
| `ios_composer` | chat.composer | Pending native consumer | Yes |
| `ios_icons` | appearance.general | Pending native consumer | Yes |
| `ios_unread` | chat.header | Pending native consumer | Yes |
| `liquid_animations` | appearance.glass | Pending native consumer | Yes |
| `login_style` | appearance.general | Pending native consumer | Yes |
| `material_you` | appearance.general | Platform-specific; not ported | Yes |
| `menu_call` | chat.header | Pending native consumer | Yes |
| `menu_mute` | chat.header | Pending native consumer | Yes |
| `menu_search` | chat.header | Pending native consumer | Yes |
| `menu_video` | chat.header | Pending native consumer | Yes |
| `message_menu_below` | chat.context_menu | Pending native consumer | Yes |
| `message_menu_blur` | chat.context_menu | Pending native consumer | Yes |
| `own_double_tap` | chat.messages | Pending native consumer | Yes |
| `profile_background` | profile.presentation | Pending native consumer | Yes |
| `profile_birthday` | profile.presentation | Pending native consumer | Yes |
| `profile_business` | profile.presentation | Pending native consumer | Yes |
| `profile_channel` | profile.presentation | Pending native consumer | Yes |
| `profile_emoji` | profile.presentation | Pending native consumer | Yes |
| `profile_photo_banner` | profile.presentation | Pending native consumer | Yes |
| `profile_style` | profile.presentation | Pending native consumer | Yes |
| `reply_background` | chat.messages | Pending native consumer | Yes |
| `reply_colors` | chat.messages | Pending native consumer | Yes |
| `reply_emoji` | chat.messages | Pending native consumer | Yes |
| `seconds_in_time` | chat.messages | Pending native consumer | Yes |
| `settings_search_history` | settings.search | Wired; native/device QA pending | Local only |
| `show_stories` | stories.visibility | Wired; native/device QA pending | Local only |
| `switch_style` | appearance.general | Pending native consumer | Yes |
| `tab_labels` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `uniform_avatars` | appearance.general | Pending native consumer | Yes |
| `useSystemBoldFont` | appearance.general | Pending native consumer | Yes |
| `useSystemEmoji` | appearance.general | Pending native consumer | Yes |

## Remaining consumer groups
1. Hiding the entire bottom bar still needs a persistent route to Chats, Profile and Settings. Profile/Settings visibility, tab labels, compact width, Contacts visibility and ordering are wired; native/device QA remains pending.
2. Per-surface glass styling and custom highlights remain pending. Native system/liquid/frosted choices, tint and a shared live preview are now wired; Android blur/refraction sliders have no equivalent public UIKit controls.
3. Chat header positioning, typography, gestures, emoji/media picker styling and message-menu polish.
4. AI and credential-dependent integrations need their own secure platform adapters; imported appearance values do not implement them.
5. Signed-device acceptance: cold launch, authentication, keyboard/Dynamic Type country layout, rapid folder swipes, permission/signing-dependent notifications, widgets/Shortcuts, media cache eviction and retained-message unread behavior.

## 2026-09-21: wide posts

`wide_posts` is wired to a settings switch and the native bubble full-width path, defaults to false, and supports import/export and live layout invalidation. Swift store tests cover persistence, reset, invalid types and change notifications. Native IPA compilation and device layout acceptance are tracked separately.

## 2026-09-27: iOS parity work in progress

- The pinned iOS source remains Telegram iOS 12.9.2. The public upstream source currently reports that version; Android's 12.10.5 baseline is not an iOS version.
- Nebula AI has native HTTPS adapters for OpenAI, Claude, Gemini and a custom OpenAI-compatible endpoint, plus an iOS 26 Foundation Models path when the system model is available. A local model never falls back to the network. Message translate/summarize/tools and draft proofreading routes open an explicit request editor; local history is opt-in and bounded. Native compilation and device checks are pending.
- The primary iOS app icon now uses the original Nebula mark. Sixteen bundled alternate icons have iPhone/iPad metadata and a native rounded-square picker. Fresh iOS installs use Nebula blue day/night accents, while a saved theme choice remains untouched. Build details read the installed bundle version/build, architecture and the pinned iOS source version/revision. Device acceptance remains pending.
- Round-video recording now has a native zoom slider using the selected camera's available zoom range (capped at 8×); pinch zoom remains available. Native/device checks are pending.
- `fragment_transition_style` is wired to Telegram's native navigation transition: standard, ease-in-out system style and shorter Spring. Reduce Motion disables the animation. Interactive navigation remains in the upstream controller. Native/device checks are pending.
- Profile badges have a subtle pulse/glow, a tap-to-details route and the actual assigned artwork in the details sheet; reduced motion is respected. Physical-device visual checks are pending.
- The native Saved Messages chat is not inserted or forced as a new Nebula row. A per-account, per-chat PIN/password verifier and native chat gate are wired in source. The verifier and retry counter stay in a ThisDeviceOnly Keychain item, outside settings transfer; notification-preview/device acceptance is pending. The app-wide Telegram passcode remains available.
- The five-page welcome tour has native back/skip/language controls and horizontal swipes. A dedicated Russian/English tour-language screen feeds the native authorization locale route. Other Telegram languages remain available after sign-in; compact-screen visual QA is pending.
- The remaining Android-only visual switches are still pending native iOS consumers.

## 2026-09-27: recent Android changes adapted to iOS

- Remote deletion capture now includes messages already cached in Saved Messages, even though they are outgoing. The local deletion path is unchanged. Separate per-account retention switches cover private chats, groups, channels, bots and Saved Messages. No recovery of content missing from the local Postbox is promised.
- The archive caches immutable membership snapshots, skips already retained messages, throttles TTL scans and coalesces encrypted writes on its own worker. The first archive read still decrypts the existing file; full persistence uses an atomic encrypted snapshot. Write failures are recorded and keep the in-memory snapshot; device performance and process-termination testing remain pending.
- Native choice sheets now cover providers, AI actions, transitions, initial tab, retention period, message marker and glass quality. They use trailing checkmarks, wrapping Dynamic Type labels and iPad form-sheet presentation. Text entry and destructive confirmations retain native alert controls.
- A dedicated glass screen uses the same GlassBackgroundView as real surfaces. It offers default/system liquid/frosted materials, live tint and a draggable preview, with power/thermal/accessibility status. Liquid Glass requires iOS 26; older devices fall back to supported blur. The native tab bar keeps Telegram's original material. `ios_glass_style` and `ios_glass_tint` are local-only keys, excluded from Android transfer.
- Apple local AI reports whether hardware is unsupported, Apple Intelligence is disabled or the model is preparing. It refreshes on foregrounding. Gemini Nano remains Android-only; local requests never silently switch to a remote provider.
- The native round-video zoom build error was corrected: the selected wide-angle device uses the public maximum zoom API. The AI settings screen also no longer indexes beyond its section-icon array.
- Local ordered-patch validation, settings contract/design guards and build-preparation tests passed. macOS Swift tests, native compilation/IPA and physical-device visual/performance acceptance are tracked separately. This batch does not complete the remaining Android-only visual switches listed above.
