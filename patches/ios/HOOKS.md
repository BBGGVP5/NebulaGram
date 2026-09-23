# Реестр хуков — ios

Каждый патч в `patches/ios/` обязан быть описан здесь.
Патч без записи не принимается: см. docs/UPSTREAM.md, раздел 4.

| Патч | Файл апстрима | Якорь | Назначение | Строк |
|---|---|---|---|---|
| 0001-nebula-settings-bootstrap.patch | `submodules/SettingsUI/BUILD` | `deps` | Зависимость нативного экрана от отдельного Foundation-модуля | 1 |
| 0001-nebula-settings-bootstrap.patch | `PeerInfoScreen/Sources/PeerInfoScreen.swift` | `PeerInfoSettingsSection.appearance` | Отдельная секция NebulaGram | 1 |
| 0001-nebula-settings-bootstrap.patch | `PeerInfoScreen/Sources/PeerInfoSettingsItems.swift` | `languageName` | Пункт NebulaGram, уникальный id 9700 | 4 |
| 0001-nebula-settings-bootstrap.patch | `PeerInfoScreen/Sources/PeerInfoScreenSettingsActions.swift` | `case .appearance` | Переход к нативному экрану | 2 |
| 0001-nebula-settings-bootstrap.patch | `ChatListFilterTabContainerNode/BUILD` | `deps` | Зависимость полосы папок от store | 1 |
| 0001-nebula-settings-bootstrap.patch | `ChatListFilterTabContainerNode/Sources/ChatListFilterTabContainerNode.swift` | `updateText`, `updateLayout`, конец `init` | Скрытие счётчиков и их ширины, наблюдение с освобождением токена. Настоящие числа и VoiceOver не меняются | 14 |

Полные пути PeerInfoScreen: `submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/`.
Полные пути ChatListFilterTabContainerNode: `submodules/TelegramUI/Components/ChatList/ChatListFilterTabContainerNode/`.
База: `6ad963e5b62d354da79040f388ae2b9132fb17b8`, Telegram iOS 12.9.2.
Адаптер и сгенерированный модуль находятся в `platform/ios/overlay/`, не копируют
целиком изменённые классы Telegram. Статус: экспериментальная интеграция;
проверка применения/синтаксиса не заменяет сборку Telegram и проверку на iPhone.
# Onboarding and NebulaLink additions (2026-09-09)

- `0002-nebula-onboarding.patch`: AuthorizationUI BUILD + welcome controller and
  phone/code/password display nodes. New views come from the overlay; native
  account/code/password actions are unchanged. Compact layout still hides art.
- `0003-nebulalink-proxy-lifecycle.patch`: TelegramUI shared-context startup and
  two module dependencies. NebulaLink uses Telegram's public proxy preferences,
  not changes to TelegramCore or MTProto session logic.
- `platform/ios/build-patches/0001-explicit-unsigned-profile-embedding.patch` is
  **not** a Telegram source-series patch. The IPA tool applies it only to the
  verified nested rules_apple checkout in a disposable build tree, never vendor.
- `check_onboarding.py` compares native auth handlers to the pinned originals.
  `--swift` also SDK-typechecks the UIKit-only artwork/welcome views. Whole-module
  AuthorizationUI/NebulaLinkUI compilation belongs to the native/IPA build gates.

# Launch without an App Group (2026-09-10)

- `0013-app-group-fallback.patch`: `submodules/TelegramUI/Sources/AppDelegate.swift`.
  Anchors: the container lookup in `application(_:didFinishLaunchingWithOptions:)`
  and `sharedContainerIdentifier` on the background URLSession. 12 lines.

  Upstream needs `group.<bundle id>` and, without it, presents "Error 2" on a
  window it has not filled — a black screen. A sideloaded build normally has
  no such group: a personal Apple team cannot create App Groups at all, and a
  re-signing tool that rewrites the bundle id rarely registers a matching one.
  The lookup now falls back to the application's own Application Support
  container, and the background session only claims the group when it exists.

  Only the main app is patched. The nine other lookups belong to extensions
  (share, notification service/content, widget, Siri, broadcast upload), which
  a build without the group cannot run anyway; they keep failing as before.
  A properly provisioned build never reaches the fallback, so nothing about
  it changes — and data written to the private container does not migrate if
  the group later appears.


# Native tab-bar appearance (2026-09-13)

- `0015-native-tab-bar-appearance.patch`: `GlassBackgroundComponent.swift` and
  `TabBarComponent.swift`. Marks the existing native tab-bar container; its glass
  descendants retain upstream appearance even when Nebula adaptive quality reduces
  other surfaces. No tab/control replacement, custom dimensions, or new icons.
- `0011-bottom-navigation.patch` continues to filter/reorder native controllers,
  retaining selected controller identity. Search, liquid selection, badge and
  gesture implementations remain upstream. The bootstrap test compares the entire
  TabBarComponent against the pin after removing the single marker assignment.
- Other adaptive-glass fallback surfaces now explicitly clip their material.
  Verification on an iPhone (including low-power mode, hidden Contacts, reordered
  tabs, search and dark/light appearance) is still required; patch tests are not a
  UIKit/IPA compilation or device screenshot test.

# Chat entry points (2026-09-18)

- `0016-chat-interaction-entry-points.patch`: the expanded-input button in
  `ChatTextInputPanelNode.swift` follows the native rich-input capability, not
  `isAIEnabled`. The upstream multiline height/empty-text conditions, input-mode
  kill switch, editor handoff, draft synchronization and send validation remain.
- `ChatController.swift`: when no default reaction can be resolved, open the
  existing message context menu rather than silently consuming a double tap.
  Existing reaction eligibility, channel restrictions and paid-reaction flow
  are unchanged. This does not promise reactions in channels that disable them.
- `check-bootstrap.py` applies the full series against the pin and checks these
  anchors alongside the native-tab-bar invariant. iPhone interaction tests and
  an Apple SDK build remain required.

# Profile badges (2026-09-20)

- `0017-profile-badges.patch` adds the NebulaSettingsContract dependency and
  regular/expanded profile title badge views to PeerInfoScreen. Assignments are
  fetched only for opened user profiles (including own profile/settings), not
  groups, topics or Saved Messages. Native Premium/status/verification icons keep
  their layout and interactions; the new badge reserves its own title width.
- `NebulaBadges` reads the existing public HTTPS `/v1/badge/{id}` endpoint. It has
  no admin credentials or exposed server settings. Requests are coalesced, main
  queue delivery is guaranteed, the 256-entry memory cache stores assignments and
  empty results for 24 hours, and failures for 60 seconds. Reopening a profile
  after expiry refetches; there is no background polling or contact enumeration.
- `NebulaProfileBadgeImages.swift` embeds byte-identical Android supporter and
  user-selected Mira PNGs. Regenerate with `generate-badge-artwork.py` when changing
  either artwork; `--check` enforces cross-platform identity. Three other custom
  badge vectors are drawn by UIKit without emoji or SF Symbols substitutions.
- XCTest covers the service/cache contract without network access. Bootstrap
  checks preserve native icon code and SDK-typecheck the UIKit artwork. The full
  IPA build and physical-device checks (long names, avatar expansion, light/dark
  appearance, simultaneous Premium/status badges) remain separate validation.

# Wide posts (2026-09-21)

- `0018-wide-posts.patch` routes the opt-in `wide_posts` preference through
  ChatMessageBubbleItemNode's existing full-width layout for broadcast channels.
  Private chats and groups retain native widths. Share buttons,
  avatars, delivery failures and system message/media limits retain their
  native insets. Sponsored posts retain their native width policy.
- The shared store exposes a default-false editable/transferable boolean.
  Loaded bubble nodes observe changes and request a native message relayout;
  weak captures and SettingsObservation lifetime release subscriptions.
- Android uses the same preference key with cached reads, text cache invalidation
  and native full-width single photo/video/GIF measurement. Albums, stickers
  and round videos keep their existing native sizing paths.
- Geometry/default/import/cache regressions and bootstrap checks run in CI;
  scrolling, media aspect ratio and larger-font visual QA still need devices.
