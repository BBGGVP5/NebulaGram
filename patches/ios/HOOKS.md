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
| 0038-folder-title-native.patch | `submodules/ChatListUI/Sources/ChatListController.swift` | `ChatListLocationContext.updateChatList` | Показывает выбранную папку в заголовке главной при совпадении ID с видимым списком; при быстром свайпе не подставляет название прежней папки | 17 |
| 0045-folder-outline.patch | `submodules/TelegramUI/Components/ChatList/ChatListFilterTabContainerNode/Sources/ChatListFilterTabContainerNode.swift` | `update` | Добавляет тонкий контур к выбранной папке в цветах текущей темы, обновляет его через существующее наблюдение настроек | 14 |
| 0046-hide-chat-list-search.patch | `submodules/ChatListUI/Sources/ChatListControllerNode.swift` | `ChatListNavigationBar.search` | Скрывает встроенную строку поиска по настройке, но возвращает её при активном поиске; отдельное действие поиска остаётся доступным | 3 |
| 0047-glass-highlights.patch | `submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/GlassBackgroundComponent.swift` | `nebulaCustom` | Подключает настройку бликов к нашему материалу и старому стеклу; системный Liquid Glass остаётся под управлением iOS | 4 |
| 0048-glass-depth.patch | `submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/GlassBackgroundComponent.swift` | `nebulaCustom` | Управляет тенью нашего стекла и старого материала через общие локальные настройки глубины и выключатель | 6 |
| 0049-glass-opacity.patch | `submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/GlassBackgroundComponent.swift` | `nebulaCustom` | Применяет ползунок плотности к нашему материалу, сохраняя сплошной фон при Reduce Transparency | 1 |
| 0050-glass-blur.patch | `submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/GlassBackgroundComponent.swift` | `NebulaGlassMaterialState` | Передаёт силу размытия в собственный материал; iOS выбирает доступный нативный уровень размытия | 1 |
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

# Profile Premium/status visibility

- `0051-profile-premium-visibility.patch` observes the editable `hide_premium_status`
  setting from an open profile and recomputes its native status icon layout.
- `0052-chat-list-premium-visibility.patch` hides the same status icons in
  chat-list rows and relayouts visible rows when the value changes. Verification
  and NebulaGram assignment badges remain visible.
- `0053-chat-title-premium-visibility.patch` applies the same preference in the
  native legacy and component-based chat titles. Both refresh when the value
  changes. Other surfaces need separate consumers before global iOS parity.

# Folder tab styles

- `0054-folder-style.patch` renders native folder tabs as titles, icons, or
  icons with titles. Custom emoji entities and their UTF-16 offsets are kept
  intact, while VoiceOver reads the original folder name in icons-only mode.
- The existing tab-container observation applies style changes immediately.

# Experimental memory screen

- `0055-memory-warning-root.patch` installs the optional low-memory observer
  when the native root controller starts. The Settings screen samples process
  resident memory only while visible and stores the warning preference locally.

# Cherrygram story archive controls

- `0056-story-auto-archive.patch` listens to the real story-subscription stream
  and applies Telegram's native per-peer hidden-story action for the selected
  user/channel categories. Settings are local to the current account; peers
  are deduplicated for the lifetime of the chat-list controller. Changing the
  setting also processes currently visible subscriptions.
- The Chats screens expose story controls and a separate native archive
  visibility action. Hidden stories and chats retain Telegram's normal recovery
  routes.

# Chat interaction vibration

- `0057-chat-haptics-control.patch` gates native chat-controller, input-panel,
  and swipe-to-reply haptic calls behind a local switch in Chats → Chat behavior. The switch
  does not alter notification vibration or system accessibility feedback.

# Chat snowflakes

- `0058-chat-snowflakes.patch` adds a bounded emitter above the visible chat
  when enabled. It stops on navigation away and under Reduce Motion or Low
  Power Mode, and updates its width with the native chat layout.

# Active-camera zoom and recent UI parity

- `0060-camera-community-tools-profile-parity.patch` adds a Camera capture-queue
  bridge for the active device's neutral/minimum/maximum factors and virtual-lens
  stops. Round recording uses one virtual device so AVFoundation can switch its
  constituent lenses. The video screen reads that device, coalesces input, ramps
  only preset taps, and preserves pinch zoom after release. It keeps Telegram's
  native recorder/cancellation lifecycle.
- Message/draft/home AI entry points carry the current Telegram presentation
  theme. The native camera choice opens the shared centered dimmed popup.
  Message tools route to actual translation/summary requests, speech and local
  tasks; languages use Telegram's catalog with separate Russian/English choices.
- Profile channel, birthday, business details, cover and emoji pattern consumers
  live in PeerInfoScreen/PeerInfoCoverComponent. Photo expansion stays in the
  native gallery path. Reply background, author colors and emoji pattern are
  gated in ChatMessageReplyInfoNode, with chat layout invalidation on changes.
- The glass material cache includes the upstream tint color. Changing a custom
  theme updates the material; unchanged render state retains its effect instance.
- Community loading/rendering lives in the SettingsUI overlay. It follows the
  account's canonical `nebulaguard_channel` peer to its linked community,
  observes native cached peer data, and opens `makeCommunityViewScreen`.
  Native avatar binding is preserved across metadata updates. Non-community
  settings pages do not resolve or subscribe to that metadata.
- These are source hooks. See `platform/ios/PARITY.md` for compilation, packaging
  and physical-device acceptance evidence.


### 0061 — configurable AI translation and composer tools

- ChatController owns visible-chat request lifecycles and updates native translated message attributes through SettingsUI's bounded provider queue. Background/leave cancels work.
- ChatTextInputPanelNode reserves a real 40-point accessory slot; optional tools use the current draft, with stale-draft checks before Apply.
- Native translation language/hide/original actions update the per-chat opt-in. The `nebula-ai` source marker prevents native/Cocoon dispatch for pinned previews and labels the selected-provider mode correctly.
- Message tools and AI settings open the same themed configuration screen. Preferences are independent for incoming/draft languages and never exported with account/chat consent.

### 0072–0081 — October 8 non-camera port

- 0072 carries attributed text through draft/edit/caption and native message tools; strict source equality rejects stale Apply results.
- 0073 attaches NebulaBrowserCore to the ordinary browser WKWebView only; content rules retain ownership through navigation and recompile on preferences.
- 0074 hooks actual message labels/share eligibility/voice queue/profile rows; 0075 enables native rich pasteboard copying.
- 0076 masks presentation clones for account-scoped reversible filters. Original Postbox records, paging, read state and protected-content eligibility are preserved; reveal URLs include a process nonce.
- 0077 uses native gallery seeking and background pause without stopping active Picture in Picture, plus message effect opt-out.
- 0078 raises native account capacity and the actual add-account handler together; existing account authorization remains unchanged.
- 0079 adds a bounded in-memory ZIP entry reader (no extraction) and explicit SettingsUI Svg/ZipArchive dependencies; 0080 uses UUID-scoped imported template icons with native fallback.
- 0081 gates Premium sticker and reaction animation entry points while retaining onHit/completion callbacks and native reaction state.
- Previews, animated settings introductions, named services, roles and glass editor presentation are overlay sources. These patches do not add Android camera backends to iOS.

### 0082 — Instant View and eligible deletion defaults

- ChatController routes inline/external/explicit Instant View links through the native URL handling path when disabled. OpenResolvedUrl handles resolved Instant View pages with the existing browser preference.
- ChatControllerAdminBanUsers initializes the existing `.unsendPersonal` switch from the opt-in preference. Eligibility, confirmation, undo and separate delete-for-me/everyone actions remain native.
- Incomplete legacy AI recovery is an overlay/contract change, not an upstream patch. Opening a draft does not save or select it; credential migration only happens on explicit valid Save.

### 0085 — selection header and forwarding parity

- Adds native header selection actions with eligibility checks and opt-in bottom Without Author action. The optional panel callback avoids changing unrelated panel initializers.
- Carries explicit forwarding options into recipient preview and every direct/draft forwarding path. Ordinary forwarding retains native defaults.
- Moves ordinary selection cancellation to the left, including reply threads; report and message-option states remain native. Appends Go to beginning followed by retained-message cleanup to native history menu routes.


## 0086 — chat preferences, swipe actions and message menu consumers

- Native sticker timestamp positioning/hiding and channel forward-count metadata; no replacement media/reaction layouts.
- Native folder-tab unread pipeline observes `folder_unmuted_only`, disposes its preference observation and selects existing unmuted counts without editing filters.
- Four message item nodes reuse the native swipe recognizer and thresholds; the available order comes from ChatController and actions execute only on `.ended`. ChatController re-fetches and rechecks eligible message data; copy preserves entities and external tools reject protected/secret content. Follow-up overlay uses native `canSetupReply` for Reply.
- Drawing-only arithmetic hint on the native UITextView, with marked-text exclusion and bounds checks.
- Stable semantic ContextMenu action IDs connect real Telegram actions to local visibility choices. Native eligibility is evaluated first; compact rows retain 44-point targets, Edit/Delete/Select stay native. Preview uses the same filter.

## 0087 — account notifications, home title, profile IDs and branding

- Foreground notifications respect the account switch. APS registration filters disabled accounts; token removal follows the native inactive-account path. VoIP calls and native all-account settings are unchanged. Preference observer is disposed with its signal.
- Native root chat-list title responds to the Chats/NebulaGram preference without overriding folder/archive/connection titles.
- Profile ID row delegates numeric formatting to the tested Foundation helper; explicit tap copies. General contains a highlight-only Telegram/Bot API picker.
- Main application Info.plist uses NebulaGram as CFBundleDisplayName. Build 84 was inspected and still had Telegram.
- Related overlays add the nondestructive LocalAuthentication test and pull-to-refresh for both NebulaLink subscription surfaces, with busy and failure cleanup.
- Local ordered-patch/contract/driver checks pass; macOS native/device builds and runtime acceptance are tracked separately in PARITY.md.


## 0088 — actual home folder panel

- `ChatListControllerNode` constructs the current `HorizontalTabsComponent`, not the older peer-picker container. Pass folder title/icon style, outline and glass/solid/minimal panel policy at this call site.
- Optional bottom component uses the existing native tabs/actions; its measured height is reserved in chat-list insets above native bottom controls. Search, selection toolbar and inline-stack layouts retain header placement.
- `ChatListController` obtains interpolation/reordered IDs from the visible component, avoiding stale top-tab ownership.
- `HeaderPanelContainerComponent` and `HorizontalTabsComponent` accept optional presentation parameters with unchanged defaults elsewhere. Solid/minimal style reparents the native content/scroll view, retaining its gesture recognizers and hit tests.
- Settings expose validated `folder_panel_style` and local `folder_tabs_bottom`; store/behavior tests cover persistence and bounds. Physical layout/rotation/reordering remains unverified.


## 0089 — Bazel application display name

The device build consumes `TelegramInfoPlist` in `Telegram/BUILD`, not the Xcode plist changed in 0087. Set the actual generated main-app `CFBundleDisplayName` to NebulaGram. Keep executable/bundle names, identifiers and extension metadata unchanged. IPA validation rejects an incorrect display name before artifact publication. No Swift/native binary code changes.


## 0090 — local Saved Messages labels

One non-expired Saved Messages cloud message can open the label picker. Settings expose the local label browser; AccountContext's native navigation opens the original message. The Foundation index is user-ID scoped and stores only names/message IDs. No Premium flags, server reactions or remote sync are fabricated.


## 0091 — explicit audio transcription choice

An independently persisted audio switch controls the native Nebula recording-menu action. Legacy text-AI visibility is respected until an explicit audio choice is made. Audio setup always offers direct OpenAI/Gemini creation and selects the saved audio profile through a callback without activating it for text chat. Native recording protection/expiration checks remain effective.
