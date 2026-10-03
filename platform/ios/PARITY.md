# iOS implementation inventory — 2026-10-03

This inventory distinguishes **wired source** from native build and device acceptance. A setting with a validated import format is not necessarily functional on iOS.

## Verification and delivery
- 2026-10-03 full portable-function pass: patches 0062 onward add seamless themed profile banners, borderless material buttons, opt-in glass haptics, native header/menu controls, own-message gestures, avatars, whole bottom-bar visibility with recovery navigation, folder hiding with safe fallback, real icon packs and switch styles, and optional branded authorization screens. Account-scoped behavior preferences cover unknown-contact sounds (foreground and Notification Service Extension), mention scope, forwarding destination, reply quoting, wallpaper selection and device-owner authentication before chat/history deletion. Bootstrap at `b3c82803eee50ccbe96e85cee611fb20a247e74b` passed workflow `37100219621`: 86 Swift tests, all 67 patches, standalone UIKit/Objective-C checks and SVG asset compilation. Full arm64 device IPA workflow `37100225372` passed for the same source. Downloaded build 59 was independently checked: app.nebulagram, iPhoneOS arm64 app and extensions, exactly one Notification Service Extension, manifest revision and SHA-256 `39522e4775e8b1a1c5e3e42d1aaba9b273f9b08ea802d4a6417f0322be734ae8`. The IPA requires user signing; physical-device and APNs acceptance remain unverified. Prior workflow `37096066321` failed on an optional chain in the save-message context menu; patch 0063 fixed it.
- Previous live-translation IPA run `37036563036` failed at an ambiguous `Timer` type in ChatController. Patch 0062 qualifies it as `Foundation.Timer`. No IPA from that run is offered as successful.
- 2026-10-02: native source now includes an active-camera virtual-lens zoom bridge, uncapped supported ranges, fixed quick-stop labels, a 280-point themed ruler and persistent pinch zoom. Requests are coalesced on the capture queue; only preset taps ramp, while dragging applies the latest factor directly. Both settings and support resolve the actual linked NebulaHub community, retain account-scoped metadata and native avatars, and open Telegram's community sheet. Choice dialogs are centered, dimmed, themed and keyboard-aware; message tools have adaptive centered action tiles, searchable result languages with Russian/English shortcuts, speech and local account-scoped tasks. The AI editor uses an opaque Telegram theme and an ordinary native navigation title. Profile appearance and reply appearance now have native consumers. Profile, chat behavior, stories, navigation and appearance are grouped separately.
- Verified checkpoint: full arm64 device IPA at `f352cb58d063525548d487c4a994ce685e86e3d4` passed workflow `37022621208`. Downloaded archive contains the app and Notification Service Extension; SHA-256 `79d1252bec74768e764b95d1d1e84b090c26caa3f2cd33f2d6e9c4d1dc5ed320`. It requires user signing and has not been tested on a physical device. The prior native failure `37016705869` was repaired by removing unreachable camera branches.
- Live AI follow-up: separate account/chat opt-ins for incoming and draft translation, independent language pickers, configurable debounce and optional right composer shortcut. Draft results require Apply; incoming requests exclude protected/secret/outgoing content and use a bounded queue. Bootstrap at `5114b53` passed 76 Swift tests and SDK/patch checks (`37033172470`). These controls are included in the verified b3c8280 build 59 above; the earlier f352cb5 IPA does **not** contain them.
- Historical entries below record earlier revisions. The latest entry supersedes their old camera/community/dialog limitations, but the remaining consumer inventory is still explicit.
- Runtime `f52b857` separates iOS bottom-bar navigation from appearance and moves folder controls beside folder style/counters. The glass preview uses Telegram colors and duplicate integer slider events skip store validation and JSON encoding (the store already deduplicated disk writes). iOS bootstrap `37007327569` passed all 65 Swift tests; native SettingsUI, PeerInfoScreen and folder modules compiled with Xcode 26.2 in `37007329671`. Android additionally separates chat/message/profile pages and adds material presets with deduplicated render revisions: CI `37007327426` passed and APK `1000291` has verified version, libraries and matching signature. These changes do not complete the older pending consumers below or port Android's latest round-video ruler, message-tools grid/language dialog and AI-editor presentation to iOS. A signed IPA and physical-device/FPS validation remain pending.
- Android runtime `c121a86` adds quick Russian/English choices above the remaining language catalog and includes centered Telegram-themed dialogs, centered partial tool rows and the opaque AI editor/header fix (native patch 0163). All 158 native patches are included. Android CI `36978641268` and Settings contract `36978641272` passed; build `1000289` has verified package/version, arm64 Telegram/NebulaLink libraries and matching v1/v2 signing certificate. Physical-device appearance remains unverified; iOS runtime inputs are unchanged.
- Android follow-up runtime `e34049c` stabilizes the native community avatar, uses the canonical peer from the ID cache before username resolution, separates sample rows from folder tabs in the preview, and redesigns the shared sheets/message tools with compact choices and an adaptive icon grid. APK CI `36894085844` and Settings contract `36894085227` passed. Build `1000282` has verified package/version, arm64 Telegram/NebulaLink libraries and v1/v2 signatures matching 1000277. Overlay-only changes preserve the 157 native patches; iOS runtime inputs are unchanged. On-device visual/behavior acceptance remains pending.
- Android uses Telegram 12.10.5. All 157 ordered patches apply. Runtime `4565a76` and patch 0162 cap the expanded ruler at 280dp, align recording/delete/attachment islands and retain the input origin on cancellation. Translation-sheet handoff restores screen coordinates and clears lifted-message ownership with stale/cancel guards. Android About/Support now use the native community card/sheet resolved through `nebulaguard_channel`. APK CI `36881224754`, build `1000277`, passed full regression and application-build checks; downloaded APK has verified v1/v2 signatures matching 1000275 and arm64 Telegram/NebulaLink libraries. Physical-device confirmation remains pending. iOS runtime inputs are unchanged; bootstrap `36881224593` passed.
- iOS uses Telegram 12.9.2. The 59 ordered iOS patches and overlay pass bootstrap checks. All 65 Swift contract tests and simulator SDK artwork checks passed in `36809260468`; the SettingsUI, PeerInfoScreen and folder integrations compiled successfully in native run `36814095192` with Xcode 26.2 for the arm64 simulator. This is compilation evidence, not a signed app or device acceptance. A signed build and physical-device checks remain pending.
- Native iOS hooks now include archive visibility, story auto-archiving, chat interaction vibration, snowflake rendering and an experimental memory screen. These are source integrations, not completed device acceptance.
- These earlier behavior gaps are wired in the 2026-10-03 pass. Smooth fading eases native navigation dim/shadow opacity without changing interactive swipe geometry. Android Predictive Back remains OS-specific.

## Implemented in source outside the presentation catalog
- Native retained-message history, original text/media references, marker, muted styling, per-chat/account clearing. Incoming cached content only; copy protection respected. Secret/expiry retention separately opt-in.
- Archive lifetime choices: unlimited by default, or 1/7/30 days; no fixed entry-count limit. A chosen lifetime prunes copies on deletion updates (not a guaranteed background timer).
- Per-account chat exclusions stop new retention, without silently clearing old copies. Available in the retained-message menu.
- Archive protection routes to native **whole-app passcode / Face ID settings** with existing access authentication, since archive messages remain in ordinary chat history. No claim of a new separate biometrically encrypted message database.
- NebulaLink import/select/connect remains an in-app proxy, not a system VPN.
- Quick-actions widget contains only Settings/NebulaLink links, no account/chat contents, no scheduled background refresh. Commands open screens, do not silently connect or change servers. Requires correctly signed extension packaging and on-device discovery checks.
- Country row/auth artwork fixes; source/bootstrap acceptance is not visual device acceptance.

## Catalog consumer inventory

`Wired` means a native consumer exists, not that the latest IPA has passed device QA. `Pending` keys may survive valid imports but must not be exposed as functioning toggles. Material You is Android-specific.

| Key | Feature | iOS source status | Transfer v1 |
|---|---|---|---|
| `adaptive_chat_header` | chat.header | Wired; native build passed; device QA pending | Yes |
| `avatar_round` | appearance.general | Wired; native build passed; device QA pending | Yes |
| `bottom_bar` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `bottom_bar_contacts` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `bottom_bar_order` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `bottom_bar_profile` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `bottom_bar_settings` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `center_home` | navigation.folders | Wired; native build passed; device QA pending | Yes |
| `centered_chat_header` | chat.header | Wired; native build passed; device QA pending | Yes |
| `compact_bottom_bar` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `custom_avatar_corners` | appearance.general | Wired; native build passed; device QA pending | Local only |
| `disable_next_channel` | chat.messages | Wired; native build passed; device QA pending | Yes |
| `floating_chat_header_v2` | chat.header | Wired; native build passed; device QA pending | Yes |
| `folder_outline` | navigation.folders | Wired; native build passed; device QA pending | Yes |
| `folder_style` | navigation.folders | Wired for three native tab styles; native build passed; device QA pending | Yes |
| `folder_title` | navigation.folders | Wired; native build passed; device QA pending | Yes |
| `glass_blur` | appearance.glass | Wired to native material tiers; native build passed; device QA pending | Local only |
| `glass_custom` | appearance.glass | Wired; native build passed; device QA pending | Local only |
| `glass_depth` | appearance.glass | Wired for custom/legacy glass; native build passed; device QA pending | Local only |
| `glass_depth_enabled` | appearance.glass | Wired for custom/legacy glass; native build passed; device QA pending | Local only |
| `glass_haptic_strength` | appearance.glass | Wired; native build passed; device QA pending | Local only |
| `glass_haptics` | appearance.glass | Wired; native build passed; device QA pending | Local only |
| `glass_highlights` | appearance.glass | Wired for custom/legacy glass; native build passed; device QA pending | Yes |
| `glass_opacity` | appearance.glass | Wired for custom glass; native build passed; device QA pending | Local only |
| `glass_quality` | appearance.glass | Wired; native build passed; device QA pending | Yes |
| `glass_refraction` | appearance.glass | System-owned optical refraction; no public continuous UIKit control | Local only |
| `header_unread` | chat.header | Wired; native build passed; device QA pending | Yes |
| `hide_all_chats` | navigation.folders | Wired; native build passed; device QA pending | Yes |
| `hide_attach_camera` | chat.composer | Wired; native build passed; device QA pending | Yes |
| `hide_dividers` | navigation.folders | Wired in chat-list rows; native build passed; device QA pending | Yes |
| `hide_home_camera` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `hide_home_compose` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `hide_premium_status` | appearance.general | Wired in profile, chat/title, contact and peer rows, reactions, call participants and stories; device QA pending | Yes |
| `hide_search_field` | navigation.folders | Wired; native build passed; device QA pending | Yes |
| `hide_send_as` | chat.composer | Wired; native build passed; device QA pending | Yes |
| `hide_tab_counters` | navigation.folders | Wired; native build passed; device QA pending | Yes |
| `icon_pack` | appearance.general | Wired; native build passed; device QA pending | Yes |
| `ios_composer` | chat.composer | Native iOS default; Android emulation switch is not exposed | Yes |
| `ios_icons` | appearance.general | Native iOS default; Android emulation switch is not exposed | Yes |
| `ios_unread` | chat.header | Native iOS default; Android emulation switch is not exposed | Yes |
| `liquid_animations` | appearance.glass | Wired; native build passed; device QA pending | Yes |
| `login_style` | appearance.general | Wired; native build passed; device QA pending | Yes |
| `material_you` | appearance.general | Platform-specific; not ported | Yes |
| `menu_call` | chat.header | Wired in user avatar menu; native build passed; device QA pending | Yes |
| `menu_mute` | chat.header | Wired in topic avatar menu; native build passed; device QA pending | Yes |
| `menu_search` | chat.header | Wired in peer/topic avatar menus; native build passed; device QA pending | Yes |
| `menu_video` | chat.header | Wired in user avatar menu; native build passed; device QA pending | Yes |
| `message_menu_below` | chat.context_menu | Native lifted-message menu below the message; no emulation switch | Yes |
| `message_menu_blur` | chat.context_menu | Wired; native build passed; device QA pending | Yes |
| `own_double_tap` | chat.messages | Wired; native build passed; device QA pending | Yes |
| `profile_background` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `profile_birthday` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `profile_business` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `profile_channel` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `profile_emoji` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `profile_photo_banner` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `profile_style` | profile.presentation | Wired; native build passed; device QA pending | Yes |
| `reply_background` | chat.messages | Wired; native build passed; device QA pending | Yes |
| `reply_colors` | chat.messages | Wired; native build passed; device QA pending | Yes |
| `reply_emoji` | chat.messages | Wired; native build passed; device QA pending | Yes |
| `seconds_in_time` | chat.messages | Wired; native build passed; device QA pending | Yes |
| `settings_search_history` | settings.search | Wired; native build passed; device QA pending | Local only |
| `show_stories` | stories.visibility | Wired; native build passed; device QA pending | Local only |
| `switch_style` | appearance.general | Wired; native build passed; device QA pending | Yes |
| `tab_labels` | navigation.bottom_bar | Wired; native build passed; device QA pending | Yes |
| `uniform_avatars` | appearance.general | Wired; native build passed; device QA pending | Yes |
| `useSystemBoldFont` | appearance.general | Native UIFont weights; no Android font override | Yes |
| `useSystemEmoji` | appearance.general | Native iOS emoji font; no Android font override | Yes |

## Platform constraints and acceptance
1. Hiding the bottom bar retains a compact native navigation menu with Chats, Profile, Settings, search and Restore. The last custom folder restores All Chats.
2. Native system/liquid/frosted choices, tint, opacity, material-tier blur, highlights, depth, haptics and live preview are wired. UIKit owns continuous optical refraction and blur kernels. Android Material You and Predictive Back are OS features; iOS uses its theme and interactive navigation.
3. iOS already provides native composer layout, font weights, emoji and lifted message menus. Imported Android emulation flags do not create redundant switches.
4. AI adapters use explicitly configured credentials, opt-in incoming/draft translation and cancellation guards. An iOS 26 on-device provider is available only where the OS model is available. No network fallback is implicit.
5. Device acceptance remains: cold launch, Dynamic Type, glass scrolling, camera lens switches, Face ID/passcode cancellation, notification delivery, widgets/Shortcuts and account switching. IPA compilation is distinct from these checks. An unsigned IPA needs signing; APNs requires the appropriate capabilities.

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

## 2026-10-03: visible and sent-message translation

- Both platforms now offer separate per-chat incoming, sent-message and draft translation controls. Sent translation defaults off; source messages remain unchanged. Remote visible translation is bounded to two concurrent requests; local model inference stays serialized. Obsolete visible requests are cancelled and completed draft results are reused for the same provider/language/chat.
- iOS production queue checks and native bootstrap passed at `192d9c3c46944547a6d18912351df53c721de879` (run `37145880639`). Cancellation retains the transport slot until actual request cleanup completes. This is source/test evidence; the full current IPA and device acceptance remain separate checks.
- Android moves to official Telegram 12.10.6 (`f2908b14133bbffbf7ab04f641ecb5bfaf533242`) with 167 ordered patches. iOS remains on the latest published official 12.9.2 (`6ad963e5b62d354da79040f388ae2b9132fb17b8`). The video-based popup motion and independent selection surfaces requested afterward are Android changes; they do not add a new iOS menu implementation.
