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
| 0039-hide-attachment-camera.patch | `submodules/MediaPickerUI/Sources/MediaPickerScreen.swift` | `MediaPickerScreenImpl.Node.init` | Убирает камеру из первой ячейки обычной галереи вложений, сохраняя явную съёмку и создание аватара/стикера | 3 |
| 0040-chat-menu-search.patch | `submodules/TelegramUI/Sources/ChatController.swift` | `Conversation_Search` в меню аватара | Настройка поиска только в меню собеседника и темы, без отключения штатного поиска по сообщениям | 4 |
| 0041-topic-mute-menu.patch | `submodules/TelegramUI/Sources/ChatController.swift` | Меню аватара темы | Показывает существующее нативное действие отключения уведомлений темы по настройке `menu_mute` | 4 |
| 0042-call-menu.patch | `submodules/TelegramUI/Sources/ChatController.swift` | Меню аватара собеседника | Голосовой и видеозвонок обычному пользователю через штатный вызов Telegram по настройкам `menu_call`/`menu_video` | 20+ |
| 0043-hide-chat-dividers.patch | `submodules/ChatListUI/Sources/Node/ChatListItem.swift` | Раскладка ячейки чата | Скрывает штатную линию между чатами по настройке `hide_dividers` | 2 |
| 0019-profile-badge-interaction.patch | `PeerInfoScreen/Sources/PeerInfoHeaderNode.swift` | `nebulaBadgeTapped`, `updateNebulaBadge` | Анимация значка профиля, карточка с «Подробнее» и нативное окно с описанием; учитывает Reduce Motion и язык интерфейса | 50+ |
| 0020-ai-message-actions.patch | `TelegramUI/Sources/ChatInterfaceStateContextMenus.swift` | `Conversation_ContextMenuTranslate` | Направляет перевод в выбранный Nebula AI, добавляет пересказ и инструменты для разрешённого одиночного текста | 40+ |
| 0021-ai-draft-action.patch | `TelegramUI/Sources/ChatController.swift` | `Conversation_Search` | Добавляет ИИ-действие для черновика и вставляет подтверждённый результат обратно в поле ввода | 20+ |
| 0022-alternate-app-icons.patch | `Telegram/BUILD` | `alternate_icon_folders` | Включает 16 Nebula `.alticon` в пакет iPhone/iPad | 16 |
| 0022-alternate-app-icons.patch | `Telegram/Telegram-iOS/AlternateIcons.plist`, `AlternateIcons-iPad.plist` | `CFBundleAlternateIcons` | Регистрирует только реально включённые варианты для системного выбора иконки | 96+ |
| 0023-native-transition-style.patch | `submodules/Display/BUILD`, `Source/Navigation/NavigationController.swift` | `setViewControllers` | Переключает системную кривую анимации для переходов по настройке, учитывает Reduce Motion и сохраняет нативные жесты | 12 |
| 0024-chat-lock-gate.patch | `submodules/TelegramUI/Sources/ChatController.swift` | меню чата, `viewWillAppear`, `viewWillDisappear` | Настройка PIN/пароля и непрозрачный экран блокировки до показа защищённого чата и при уходе приложения в фон | 20 |
| 0025-welcome-language.patch | `submodules/AuthorizationUI/Sources/AuthorizationSequenceSplashController.swift` | `languageChanged` | Открывает собственный экран выбора языка обзора, сохраняя штатную локализацию при переходе к входу | 8 |
| 0026-badge-details-artwork.patch | `submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/Sources/PeerInfoHeaderNode.swift` | `nebulaBadgeTapped` | Показывает реальный значок в окне «Подробнее»; анимация учитывает Reduce Motion | 2 |
| 0027-badge-init-order.patch | `submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/Sources/PeerInfoHeaderNode.swift` | `init` | Подключает обработчик нажатия после `super.init()`, как требует Swift | 2 |
| 0028-primary-nebula-icon.patch | `Telegram/BUILD` | `composer_icon_folders` | Делает фирменную иконку NebulaGram основным ресурсом iOS до первого запуска | 1 |
| 0029-default-nebula-theme.patch | `submodules/TelegramUIPreferences/Sources/PresentationThemeSettings.swift` | `defaultSettings` | Использует фирменный синий акцент при первом запуске, сохраняя выбор темы существующих пользователей | 1 |
| 0030-round-video-zoom-slider.patch | `submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/VideoMessageCameraScreen.swift` | `Node` | Добавляет нативный ползунок зума с пределом активной камеры к записи круглого видео | 7 |

| 0032-ai-home-chat.patch | `submodules/ChatListUI/Sources/ChatListController.swift`, `submodules/TelegramUI/Sources/TelegramRootController.swift`, `submodules/TelegramUI/Components/ChatListHeaderComponent/Sources/NavigationButtonComponent.swift` | `storyButton`, `addRootControllers`, icon rendering | Опциональная кнопка ИИ на месте камеры со штатным стеклом, живое обновление выбора и открытие UIKit-чата через уведомление, ограниченное текущим root controller | 30+ |
| 0033-navigation-parity.patch | `TelegramRootController.swift`, `PeerInfoScreen.swift`, `PeerInfoHeaderNavigationButton*.swift`, `ChatListController.swift`, `TabBarContollerNode.swift`, `TabBarComponent.swift` и Bazel `BUILD` | штатные контроллеры вкладок, кнопки шапки и компонент нижней панели | Отдельная вкладка профиля, скрытие профиля/настроек с запасным переходом, подписи и компактная ширина нижней панели, скрытие камеры и создания чата на главной; изменения применяются без перезапуска | 60+ |
| 0034-hide-send-as.patch | `ChatTextInputPanelNode.swift`, `ChatTextInputPanelNode/BUILD` | `hasSendAsButton`, `displaySendAsAvatarButton`, `requestLayout` | Скрывает аватар «Отправить от имени» в обоих проходах измерения и отрисовки, освобождает место в поле ввода и обновляет панель без смены отправителя | 15 |
| 0035-chat-title-alignment.patch | `ChatTitleView.swift`, `ChatTitleView/BUILD` | `titleFrame`, `activityFrame`, `requestUpdate` | Переключает положение заголовка и подзаголовка чата между штатным центром и выравниванием влево; обновляет открытый чат при смене настройки | 15 |
| 0036-next-channel-prompt.patch | `TelegramUI/Sources/ChatController.swift` | `updateNextChannelToReadVisibility`, `updateChatPresentationInterfaceState` | Отключает предложение перейти к следующему каналу без изменения счётчиков; пересчитывает открытый чат при смене навигации и формата времени | 15 |
| 0037-message-time-seconds.patch | `StringForMessageTimestampStatus.swift`, `ChatMessageDateAndStatusNode/BUILD` | `stringForMessageTimestamp` | Передаёт параметр `withSeconds` штатному форматтеру для обычных, отредактированных и импортированных сообщений | 6 |

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
