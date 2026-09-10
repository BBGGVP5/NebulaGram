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

