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
| `bottom_bar_profile` | navigation.bottom_bar | Pending native consumer | Yes |
| `bottom_bar_settings` | navigation.bottom_bar | Pending native consumer | Yes |
| `center_home` | navigation.folders | Pending native consumer | Yes |
| `centered_chat_header` | chat.header | Pending native consumer | Yes |
| `compact_bottom_bar` | navigation.bottom_bar | Pending native consumer | Yes |
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
| `hide_home_camera` | navigation.bottom_bar | Pending native consumer | Yes |
| `hide_home_compose` | navigation.bottom_bar | Pending native consumer | Yes |
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
| `tab_labels` | navigation.bottom_bar | Pending native consumer | Yes |
| `uniform_avatars` | appearance.general | Pending native consumer | Yes |
| `useSystemBoldFont` | appearance.general | Pending native consumer | Yes |
| `useSystemEmoji` | appearance.general | Pending native consumer | Yes |

## Remaining consumer groups
1. Separate Profile tab, Settings avatar/gear behavior, tab labels, compact/hide-whole-bar modes and fallback navigation. Contacts visibility and ordering are already wired, preserving selected controller identity.
2. Native glass custom tint/highlights and per-surface styling. Android blur/refraction sliders cannot simply be mapped to undocumented UIKit properties; supported native adaptations require explicit UI semantics.
3. Chat header positioning, typography, gestures, emoji/media picker styling and message-menu polish.
4. AI and credential-dependent integrations need their own secure platform adapters; imported appearance values do not implement them.
5. Signed-device acceptance: cold launch, authentication, keyboard/Dynamic Type country layout, rapid folder swipes, permission/signing-dependent notifications, widgets/Shortcuts, media cache eviction and retained-message unread behavior.
