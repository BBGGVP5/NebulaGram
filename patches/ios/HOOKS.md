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
