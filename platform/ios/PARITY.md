# iOS implementation inventory — 2026-10-02

This inventory distinguishes **wired source** from native build and device acceptance. A setting with a validated import format is not necessarily functional on iOS.

## Verification and delivery
- Current settings reorganization separates iOS bottom-bar navigation from appearance and moves folder controls beside folder style/counters. The glass preview uses Telegram colors and duplicate integer slider saves are skipped. Android additionally separates chat/message/profile pages and adds material presets with deduplicated render revisions. Bootstrap/overlay checks pass locally; current native compilation and artifact delivery are pending. These changes do not complete the older pending consumers below or port Android's latest round-video ruler, message-tools grid/language dialog and AI-editor presentation to iOS.
- Android runtime `c121a86` adds quick Russian/English choices above the remaining language catalog and includes centered Telegram-themed dialogs, centered partial tool rows and the opaque AI editor/header fix (native patch 0163). All 158 native patches are included. Android CI `36978641268` and Settings contract `36978641272` passed; build `1000289` has verified package/version, arm64 Telegram/NebulaLink libraries and matching v1/v2 signing certificate. Physical-device appearance remains unverified; iOS runtime inputs are unchanged.
- Android follow-up runtime `e34049c` stabilizes the native community avatar, uses the canonical peer from the ID cache before username resolution, separates sample rows from folder tabs in the preview, and redesigns the shared sheets/message tools with compact choices and an adaptive icon grid. APK CI `36894085844` and Settings contract `36894085227` passed. Build `1000282` has verified package/version, arm64 Telegram/NebulaLink libraries and v1/v2 signatures matching 1000277. Overlay-only changes preserve the 157 native patches; iOS runtime inputs are unchanged. On-device visual/behavior acceptance remains pending.
- Android uses Telegram 12.10.5. All 157 ordered patches apply. Runtime `4565a76` and patch 0162 cap the expanded ruler at 280dp, align recording/delete/attachment islands and retain the input origin on cancellation. Translation-sheet handoff restores screen coordinates and clears lifted-message ownership with stale/cancel guards. Android About/Support now use the native community card/sheet resolved through `nebulaguard_channel`. APK CI `36881224754`, build `1000277`, passed full regression and application-build checks; downloaded APK has verified v1/v2 signatures matching 1000275 and arm64 Telegram/NebulaLink libraries. Physical-device confirmation remains pending. iOS runtime inputs are unchanged; bootstrap `36881224593` passed.
- iOS uses Telegram 12.9.2. The 59 ordered iOS patches and overlay pass bootstrap checks. All 65 Swift contract tests and simulator SDK artwork checks passed in `36809260468`; the SettingsUI, PeerInfoScreen and folder integrations compiled successfully in native run `36814095192` with Xcode 26.2 for the arm64 simulator. This is compilation evidence, not a signed app or device acceptance. A signed build and physical-device checks remain pending.
- Native iOS hooks now include archive visibility, story auto-archiving, chat interaction vibration, snowflake rendering and an experimental memory screen. These are source integrations, not completed device acceptance.
- Remaining Cherrygram adaptations on iOS include unknown-contact notification muting, mention suppression, a custom Saved Messages target, whole-reply quoting, per-chat wallpaper gating, deletion biometrics, and separate smooth-fade controls. Android Predictive Back has no iOS equivalent.

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
| `centered_chat_header` | chat.header | Wired; native/device QA pending | Yes |
| `compact_bottom_bar` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `custom_avatar_corners` | appearance.general | Pending native consumer | Local only |
| `disable_next_channel` | chat.messages | Wired; native/device QA pending | Yes |
| `floating_chat_header_v2` | chat.header | Pending native consumer | Yes |
| `folder_outline` | navigation.folders | Wired; native/device QA pending | Yes |
| `folder_style` | navigation.folders | Wired for three native tab styles; native/device QA pending | Yes |
| `folder_title` | navigation.folders | Wired; native/device QA pending | Yes |
| `glass_blur` | appearance.glass | Wired to native material tiers; native/device QA pending | Local only |
| `glass_custom` | appearance.glass | Pending native consumer | Local only |
| `glass_depth` | appearance.glass | Wired for custom/legacy glass; native/device QA pending | Local only |
| `glass_depth_enabled` | appearance.glass | Wired for custom/legacy glass; native/device QA pending | Local only |
| `glass_haptic_strength` | appearance.glass | Pending native consumer | Local only |
| `glass_haptics` | appearance.glass | Pending native consumer | Local only |
| `glass_highlights` | appearance.glass | Wired for custom/legacy glass; native/device QA pending | Yes |
| `glass_opacity` | appearance.glass | Wired for custom glass; native/device QA pending | Local only |
| `glass_quality` | appearance.glass | Wired; native/device QA pending | Yes |
| `glass_refraction` | appearance.glass | Pending native consumer | Local only |
| `header_unread` | chat.header | Pending native consumer | Yes |
| `hide_all_chats` | navigation.folders | Pending native consumer | Yes |
| `hide_attach_camera` | chat.composer | Wired; native/device QA pending | Yes |
| `hide_dividers` | navigation.folders | Wired in chat-list rows; native/device QA pending | Yes |
| `hide_home_camera` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `hide_home_compose` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `hide_premium_status` | appearance.general | Wired in profile, chat-list and chat-title views; other surfaces pending native consumer/device QA | Yes |
| `hide_search_field` | navigation.folders | Wired; native/device QA pending | Yes |
| `hide_send_as` | chat.composer | Wired; native/device QA pending | Yes |
| `hide_tab_counters` | navigation.folders | Wired; native/device QA pending | Yes |
| `icon_pack` | appearance.general | Pending native consumer | Yes |
| `ios_composer` | chat.composer | Pending native consumer | Yes |
| `ios_icons` | appearance.general | Pending native consumer | Yes |
| `ios_unread` | chat.header | Pending native consumer | Yes |
| `liquid_animations` | appearance.glass | Pending native consumer | Yes |
| `login_style` | appearance.general | Pending native consumer | Yes |
| `material_you` | appearance.general | Platform-specific; not ported | Yes |
| `menu_call` | chat.header | Wired in user avatar menu; native/device QA pending | Yes |
| `menu_mute` | chat.header | Wired in topic avatar menu; native/device QA pending | Yes |
| `menu_search` | chat.header | Wired in peer/topic avatar menus; native/device QA pending | Yes |
| `menu_video` | chat.header | Wired in user avatar menu; native/device QA pending | Yes |
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
| `seconds_in_time` | chat.messages | Wired; native/device QA pending | Yes |
| `settings_search_history` | settings.search | Wired; native/device QA pending | Local only |
| `show_stories` | stories.visibility | Wired; native/device QA pending | Local only |
| `switch_style` | appearance.general | Pending native consumer | Yes |
| `tab_labels` | navigation.bottom_bar | Wired; native/device QA pending | Yes |
| `uniform_avatars` | appearance.general | Pending native consumer | Yes |
| `useSystemBoldFont` | appearance.general | Pending native consumer | Yes |
| `useSystemEmoji` | appearance.general | Pending native consumer | Yes |

## Remaining consumer groups
1. Hiding the entire bottom bar still needs a persistent route to Chats, Profile and Settings. Profile/Settings visibility, tab labels, compact width, Contacts visibility and ordering are wired; native/device QA remains pending.
2. Native system/liquid/frosted choices, tint, opacity, material-tier blur, highlights, depth and a shared live preview are wired. Android's continuous blur and refraction have no equivalent public UIKit controls. Further per-surface styling remains pending.
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
- The home title follows the selected folder when enabled. It reads only a matching filter ID during swipes, and the switch updates the native header live. This addition still needs native compilation and device acceptance.
- The native selected-folder pill can show a thin theme-colored outline. The tab node's existing settings observation updates it live; native compilation and device acceptance remain pending.
- NebulaLink is the first destination on the NebulaGram settings entry screen. The settings list now uses Telegram's active presentation theme, including custom themes, instead of a separate forced palette.
- Nebula AI now has a chat list and switches between bounded conversations. Successful turns stay in memory; they persist locally only when AI history is enabled. The model label has a dedicated full-width row. Native build and device behavior remain to be verified.
- Custom iOS glass now uses the regular system glass effect (or a thicker supported blur) and a 30% default theme tint. Existing saved tint choices remain unchanged. The Telegram default material is unaffected; device visual acceptance is pending.
- The native chat-list search row can now be hidden while leaving search activation intact; an active search still owns its field. Round-video zoom now starts as compact 1×/2× presets and expands on hold or drag into a logarithmic ruler scrolling beneath a fixed indicator on Android and iOS. Device visual and gesture checks remain pending.
- The glass highlights switch now controls the custom material rim and legacy highlight container. It is disabled for system Liquid Glass on iOS 26, whose highlights are OS-owned. Native compilation and device visual acceptance remain pending.
- Premium and emoji-status icons can be hidden in profile headers, chat-list rows and chat titles without hiding verification or NebulaGram badges. Open profiles, visible chat rows and chat titles update when the preference changes. Other status surfaces still need their own native consumer.

## 2026-09-30: optical-module zoom remains Android-only

- Android now discovers recording-compatible camera modules and physical focal lengths, prioritizes logical multi-camera zoom, routes separate module transitions without replacing the recorder encoder, and uses Camera1's real zoom-ratio table. Its slider has no fixed 2×/10× ceiling and preserves pinch zoom on release. Device acceptance remains pending.
- This implementation has not been ported to iOS. `NebulaVideoZoomSlider` still queries a wide-angle device separately from the active capture session, caps its range at 10×, and derives buttons from that range. Native iOS needs an active-device/virtual-camera range bridge, constituent-lens stops, correct factor mapping, and the same gesture persistence before module/range parity can be claimed.

## 2026-10-01: support and shared artwork

- Stock Telegram settings use original shared glyphs rendered as native themed rows on both platforms. Developer, tester and heart badges use the same shared vector paths; the exact embedded Nebula logo and Mira PNGs are preserved.
- The iOS support screen explains the existing server-issued supporter badge, copies the current account's Telegram ID, and opens approved project channel/source/issue links. Payment recipients, confirmation contact and badge thresholds still await user configuration. There is no automatic donation verification or invented payment tier.
- The iOS zoom presets now retain a direct 2× shortcut when a larger range is available. This change does not complete the active-camera/module bridge listed above. Android separately adds 2× alongside optical stops, animates preset changes, and closes old modules without blocking the UI thread.
- The Saved Messages title-capsule cancellation fix in Android patch `0156` is Android-specific; no equivalent iOS change is claimed.

## 2026-10-01: camera queue, recording islands and Community

- Android uses a 48dp capsule without separate step buttons, separate front/rear presets, a fixed ruler indicator, the active chat ResourcesProvider and the native blur factory. Camera2 coalesces input on its own handler and reuses the request builder; Camera1 caches its ratio table, ignores redundant zoom indices, and coalesces round-video zoom/flash changes on the camera executor. Requested UI zoom is kept continuous rather than overwritten with Camera1's discrete factor. Lens transitions read one small preview and persist it off the UI thread. Recording/delete controls have independent material surfaces and matching native insets. These Android camera/composer changes have not been ported to iOS.
- Both platforms consolidate project links into one Community destination. Android About and support screens no longer list every link directly; iOS settings and support open the same grouped destination.
- Native iOS run 36814095192 passed for bb4b340, including the new Community changes: SettingsUI, PeerInfoScreen and folder modules compiled with Xcode 26.2 against the Telegram iOS 12.9.2 pin. All 202 published patch/overlay input hashes match its evidence. Subsequent commits through a9dc07d have identical iOS runtime inputs; no signed IPA/device acceptance is claimed.

## 2026-10-01: native community correction pending destination

- The user clarified that Community must be an actual Telegram community peer card, with its avatar, title, linked-chat count and native navigation. The existing four-link directory is an earlier interpretation, not completion of that clarification.
- The user supplied `https://t.me/nebulaguard_channel`. Android follows the channel's actual linked community with native CommunityLinkView/CommunitySheet; metadata, account isolation, channel-to-community updates and request/detach guards pass. Pinned iOS has community peer/cached metadata and native ItemListPeerItem rendering; connection of the iOS native card remains outstanding.
- Android commit 42a34ac changes zoom/theme and support placement only. Its APK/native checks are separate from the unchanged iOS inputs validated by 36814095192. Android now has 154 ordered patches.
