# Реестр хуков — android

Каждый патч в `patches/android/` описан здесь.
Патч без записи не принимается: см. docs/UPSTREAM.md, раздел 4.

Апстрим: `DrKLO/Telegram`, зафиксирован на 12.10.1 (7038), коммит `62b56a07`.

| Патч | Файл апстрима | Якорь | Назначение | Добавлено / удалено |
|---|---|---|---|---|
| `0001-gradle-link-nebulalink-core.patch` | `TMessagesProj/build.gradle` | dependencies | Подключает Go-ядро | +9 / −0 |
| `0002-application-loader-start-core.patch` | `ApplicationLoader.java` | onCreate | Инициализирует NebulaLink | +2 / −0 |
| `0003-standalone-abi-splits.patch` | `TMessagesProj_AppStandalone/build.gradle` | перед defaultConfig.versionCode | Сохраняет подписанный sideload-пакет, собирает единый APK для всех ABI и держит снятие ресурсов в паре с минификацией | +17 / −0 |
| `0004-launch-show-welcome.patch` | `LaunchActivity.java` | перед return new IntroActivity() | Показывает приветствие NebulaGram | +3 / −0 |
| `0005-hide-managed-proxy.patch` | `ProxyListActivity.java` | updateRows; ListAdapter | Скрывает служебный прокси и его настройки при активном туннеле | +30 / −2 |
| `0006-login-typography.patch` | `LoginActivity.java; OutlineTextContainerView.java; CodeFieldContainer.java` | создание экранов и нижней кнопки | Оформляет номер, код и пароль поверх нативной логики Telegram | +50 / −15 |
| `0007-settings-nebulagram-entry.patch` | `ProfileActivity.java` | строки рядом с languageRow | Вход в NebulaGram из прежних настроек | +11 / −2 |
| `0008-dialogs-bottom-bar.patch` | `MainTabsActivity.java; DialogsActivity.java` | onResume; `canParentTabsSlide`; checkUi_callTabVisible; checkUi_tabsPosition; checkUi_fadeView; checkUi_menuItems | Настраивает штатные вкладки, запуск свайпа на списке чатов, вход в боковую панель и отступы | +27 / −6 |
| `0009-call-permission-prompt.patch` | `DialogsActivity.java` | после успешного startActivity в запросах разрешения | Запоминает переход в настройки полноэкранных звонков Android и экрана блокировки MIUI | +3 / −0 |
| `0010-main-settings-nebulagram-entry.patch` | `SettingsActivity.java` | fillItems; onClick | Первый пункт «Настройки NebulaGram» в новой вкладке настроек | +4 / −0 |
| `0011-chat-chrome.patch` | `ChatActivity.java; ChatActivityEnterView.java; ChatInputViewsContainer.java; ChatAvatarContainer.java; ActionBar.java` | создание/измерение поля ввода; фон action bar; размещение аватара и заголовка | Добавляет переключаемые iOS-панель сообщения и центрированную шапку чата на штатном Liquid Glass | +159 / −3 |
| `0026-ios-chat-chrome-repair.patch` | `ChatActivity.java; ChatActivityEnterView.java; ChatAvatarContainer.java; ActionBar.java` | `headerItem`; `onLayout`; `setNebulaCenteredTitle`; `setNebulaFloatingChatHeader` | Переносит знак «Избранного» в правое меню, не даёт iOS-шапке дублировать его рядом с заголовком и раскладывает «Отправить как» рядом с полем | +26 / −6 |
| `0013-info-pages.patch` | `ProfileActivity.java; ChatEditActivity.java; ChatUsersActivity.java; ThemeActivity.java; SharedMediaLayout.java; SectionsScrollView.java; ProfileActionsView.java` | создание секций и карточек действий | Обновляет экраны информации и редактирования чата без замены адаптеров и обработчиков | +77 / −38 |
| `0014-main-tabs-swipe.patch` | `MainTabsActivity.java` | `canScrollForward`; `canScrollBackward` | Не даёт жесту открыть отключённую вкладку нижней панели | +5 / −2 |
| `0034-profile-row.patch` | `SettingsActivity.java` | `fillItems`; обработчик `0x4e43` | Добавляет вход в профиль при скрытой вкладке; иконка `settings_account` на бирюзово-зелёном градиенте как в Cherrygram | +15 / −0 |
| `0040-chat-reference-layout.patch` | `ActionBar.java; ChatAvatarContainer.java; ChatActivityEnterView.java; ChatInputViewsContainer.java; ChatActivityChannelButtonsLayout.java; ChatActivity.java` | измерение шапки; `dispatchDraw`; создание скрепки; `onLayout`; `updateColors`; `updateBottomOverlay` | Центрирует динамическую плашку и отделяет аватар; разделяет панель ввода, сохраняет скрепку, AI и разворачивание, возвращает акцентную отправку с анимацией Telegram; размещает пересылку и действия канала | +108 / −50 |
| `0041-chat-rendering-fixes.patch` | `ChatActivity.java; ChatAvatarContainer.java; ChatActivityEnterView.java; ChatAttachAlertPhotoLayout.java` | создание фонов; onMeasure/onLayout; checkActionBar; адаптер и декорация галереи | Независимые RenderNode-фоны, измерение текста по ширине капсулы, компактное меню бота и полное скрытие плитки камеры | +94 / −52 |
| `0042-header-profile-folder-colors.patch` | `ActionBar.java; SimpleTextView.java; ChatAvatarContainer.java; ChatActivity.java; DialogsActivity.java; DialogStoriesCell.java; ProfileActivity.java` | измерение/рисование; ThemeDelegate; завершение свайпа; фон профиля | Центрирует премиум-эмодзи, сохраняет скрытие меню, исправляет контраст, выводит названия папок штатным текстом и продлевает баннер под действия | +45 / −18 |
| `0043-package-selected-abis.patch` | `TMessagesProj_AppStandalone/build.gradle` | afat.ndk.abiFilters | Применяет общий список архитектур и к нативным библиотекам из AAR-зависимостей при упаковке APK | +3 / −1 |
| `0044-tab-avatar-alignment.patch` | `GlassTabView.java` | createAvatar; nebulaApplyLabel | Применяет положение значка без подписи к видимой аватарке вкладки, включая превью навигации | +2 / −2 |
| `0045-navigation-settings.patch` | 7 файлов навигации и настроек | MainTabsLayout, MainTabsActivity, ViewPagerFixed, ViewPagerActivity, DialogsActivity, SettingsActivity, GlassTabView | Компактная панель, жест от левого края, нативные меню удержания, группы настроек и скрытый номер | +147 / −72 |
| `0046-theme-header-followup.patch` | DialogStoriesCell, ActionBar, ChatAvatarContainer, ChatActivity | Заголовки папок и топиков | Плавная смена папок, исчезновение статуса, общая геометрия и настройки пилюли, шапка топиков | +12 / −10 |
| `0047-ios-icons-subtitle.patch` | ApplicationLoader, LaunchActivity, BackDrawable, GlassTabView, ChatActivityEnterViewAnimatedIconView, ChatAvatarContainer | Загрузка иконок и вторичная строка шапки | Переключаемые контурные значки, стрелка iOS, компактный приглушённый статус | +49 / −2 |
| `0048-message-panel-spacing.patch` | ChatMessageCell, ChatActivityTopPanelLayout | getExtraTextX; checkBoundsAndClipping | Отступы текста внутри сообщений и скругление верхней панели темы | +4 / −4 |

Java-файлы находятся в `TMessagesProj/src/main/java/org/telegram/ui/`,
кроме `ApplicationLoader.java` — он находится в `org/telegram/messenger/`.

**Самые уязвимые патчи — 0011 и 0013.** `ChatActivity` и `ProfileActivity`
крупные и часто меняются в апстриме. Ночная проверка поймает поломку в день
выхода новой версии; при обновлении надо перенести только вызовы оформителя,
ориентируясь на соседние штатные блоки, а не копировать в них собственную
логику Telegram.

Логика новых экранов остаётся в оверлее. Патч навигации управляет штатными
вкладками Telegram: в DialogsActivity больше не вставляется вторая панель.
Боковая панель включается в «Настройки → Настройки NebulaGram → Навигация» и открывается
кнопкой меню на основном экране чатов. Скрытие нижней панели включает боковую,
чтобы сохранить доступ к настройкам. Отключение боковой панели возвращает все
нижние вкладки. Когда нижняя панель включена, горизонтальный жест на списке
чатов открывает только соседнюю включённую вкладку; поиск, шторки и режим
редактирования папок его блокируют. Изменения применяются при возврате в чаты.

Строка NebulaLink находится в «Настройки → Настройки NebulaGram» и подписана
фактически подключённым сервером из `tunnel.status`, с зелёным акцентом подключения.
В настройках прокси перехода в NebulaLink нет. При активном туннеле вместо
служебного адреса и переключателей показано пояснение, где управлять подключением.
При отключении служебный SOCKS-прокси удаляется перед обновлением списка.
Выбранный для следующего подключения сервер и действующий сервер различаются.

Патч разрешений сохраняет существующие флаги только после успешного открытия
системных настроек. Проверка разрешения Android сохраняется. См.
[Android 14: full-screen intents](https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi).

Про экраны входа отдельно. `PhoneView` и `LoginActivitySmsView` остаются
внутренними классами `LoginActivity`, поэтому оформление получает уже
собранные поля, OTP, пароль, кнопку и клавиатуру. Оно меняет только вёрстку:
все слушатели, SMS-retriever, автозаполнение, повторная отправка, восстановление
пароля и завершение авторизации остаются в Telegram. Хук включается лишь для
обычного входа и поддерживаемых шагов; остальные сценарии используют штатный
экран. Если апстрим перестроит разметку, это ограничит внешний вид, а не
доступ к авторизации.

Панель сообщения не подменяет `ChatActivityEnterView`: новая отрисовка
создаёт независимые штатные размытые фоны для скрепки, поля ввода и правой
кнопки. Каждый фон получает собственный RenderNode через фабрику Telegram;
его границы и прозрачность сохраняются до воспроизведения кадра.
AI, разворачивание и закрытие пересылки получают отдельные малые круги,
которые следуют видимости и анимациям исходных кнопок. При наборе текста
скрепка остаётся слева, а штатная анимация меняет микрофон на акцентную
отправку с самолётиком. Короткое нажатие на микрофон всё ещё переключает видео,
а удержание, отмена и блокировка записи остаются в исходном обработчике.
Стиль пропускает запись, редактирование и расширенный редактор;
переключатель в «Настройках NebulaGram» возвращает стандартный вид.

Шапка чата не рисует непрозрачный прямоугольник поверх Telegram. Включённый
стиль меняет размещение штатного `ChatAvatarContainer`: заголовок остаётся по
центру внутри настоящей стеклянной капсулы Telegram, а аватар — отдельной
кнопкой справа. На выключенном стиле исходные размеры и отступы контейнера
восстанавливаются.

Карточки информации также используют исходные списки, действия, роли,
переходы к медиа и проверки прав. В оверлее находятся только фон, заголовок
и акценты; на экранах чата и настроек можно вернуть стандартный вид отдельными
переключателями.

Оформление строки NebulaLink и её подписка на состояние остаются в оверлее
`NebulaLinkRow`; добавление пунктов внутри «Настроек NebulaGram» не требует
дополнительных изменений в Telegram.

Про приветствие: вставка проверяет `NebulaIntroFragment.shouldShow()`, поэтому
после первого прохода управление возвращается штатному интро Telegram, и наш
код перестаёт участвовать в этом пути вообще.

Про третий патч: приложение выпускается одним APK. На обычный push он
содержит arm64-v8a, при ручном запуске — все четыре ABI в универсальном файле.
Один список `NEBULA_ABIS` задаёт архитектуры Telegram и ядра NebulaLink. Патч только сохраняет
идентификатор NebulaGram и настраиваемую минификацию для подписанного
`standalone`-варианта; существующие `applicationVariants.all` апстрима не
меняются.

## Чего здесь намеренно нет

* **Сеть.** Прокси включается через `ConnectionsManager.setProxySettings` и
  `SharedConfig.ProxyInfo` — публичный путь, которым пользуется штатный экран
  прокси. Патчей в `ConnectionsManager` или tgnet нет и не будет.
* **Звонки.** `VoIPService` сам читает настройку `proxy_enabled_calls`, поэтому
  «звонки через NebulaLink» — это запись в SharedPreferences, а не правка кода.
* **Экраны.** Наши фрагменты живут в оверлее (`app.nebulagram.*`) и не
  пересекаются с файлами Telegram.

Класс `app.nebulagram.nebulalink.NebulaLink` вызывается по полному имени, чтобы
не трогать блок импортов апстрима: это экономит ещё одну строку патча и одно
место возможного конфликта.

## Значок уведомлений

`platform/android/overlay/TMessagesProj/src/main/res/drawable-anydpi-v21/notification.xml`
содержит белый силуэт `design/icon/mark.svg` на прозрачном фоне. Он заменяет
`R.drawable.notification`, используемый для сообщений и их групповых уведомлений.
Плотность `anydpi` имеет приоритет над растровыми `notification.webp` апстрима;
патчи к `NotificationsController` для этого не нужны. См.
[Android: альтернативные ресурсы](https://developer.android.com/guide/topics/resources/providing-resources).

## Меню главного экрана и иконки

Патч `0049-main-menu-navigation.patch` заменяет боковую панель меню в трёх
точках. `NebulaMainMenu` добавляет канал, архив, звонки и QR-коды;
`NebulaBottomBar.settingsInOverflow` сохраняет доступ к настройкам, если
нижняя панель или вкладка настроек скрыта либо её место занимают звонки.
Настройка старой боковой панели больше не используется.

Набор Solar подключён через `NebulaIcons` и `NebulaIconResources` с сохранением
переключателя. Галочки доставки/прочтения не подменяются. `GlassTabView`
использует штатные анимированные значки Telegram; логотип настроек остаётся
NebulaGram. Атрибуция иконок включена в `assets/nebula-icon-notices.txt`.

## Время смены названия папки

Патч `0050-folder-title-timing.patch` обновляет обе шапки при подготовке
следующей страницы в `switchToCurrentSelectedMode(true)`, до завершения
свайпа. При отмене жеста исходный заголовок возвращается вместе со списком;
заблокированная папка не меняет название. Обычное обновление без анимации
сохраняет прежнее поведение, включая удаление папок.

`scripts/check-folder-title-timing.py` выполняет методы из собранного
`DialogsActivity` с моделью страниц и проверяет обновление заголовка до
загрузки списка. Проверка воспроизводит ошибку на версии до исправления.


## Настройки оформления и соответствие превью

`0051-appearance-controls.patch` связывает переключатели с нативными компонентами:
шапка с аватаром слева или отдельно справа, счётчик на кнопке назад, отражения
стекла, альтернативные тумблеры, стили вкладок папок, видимость полей профиля,
оформление ответов и действия меню чата. При скрытии «Все чаты» выбирается
первая незаблокированная папка, логический идентификатор общей ленты сохраняется.

Меню сообщения использует существующий scrim/blur Telegram. Настройка расположения
под сообщением действует только с размытием; масштабирование и сдвиг относятся
к выделенному слою, а не к ячейкам списка. Альбомы перемещаются группой. Во
вложенных контейнерах остаётся нативное позиционирование с учётом их координат.

Таб настроек использует фотографию аккаунта, когда она есть, иначе штатную
шестерёнку. Для ботов-вложений скрытие подписей применяется к BackupImageView.
`NebulaIcons` заменяет только именованные кнопки собственными векторами;
статусы файлов, галочки и анимированные кнопки эмодзи/стикеров остаются нативными.

Проверки: `check-appearance-controls.py` (границы меню и исключения иконок),
`check-chat-layout.py`, `check-chat-native.py`, `check-navigation.py`; полная
компиляция Java и применение всей последовательности патчей к чистой базе.


## Стеклянные меню iOS и переходы

`0059-ios-menu-and-navigation.patch` использует `NebulaMenuStyle`: скругление 24 dp,
общая палитра поверхности и текста, штатный blur3 и преломление при доступном
Liquid Glass. Стиль применяется при включённом iOS-поле ввода или плавающей
шапке; движение дополнительно зависит от «Жидких анимаций». При выключенном
размытии используется непрозрачная читаемая поверхность.

Раскрытие меню имеет один аниматор масштаба и прозрачности на полноразмерном
фоне. Переходы вложенных меню завершаются от текущего положения жеста.
Нажатие текстовой пилюли открывает профиль обычным переходом, аватар сохраняет
свой переход. Поиск учитывает собственную высоту и при скрытом поисковом слоте.
Отступ под альбомом рассчитывается по границам пузырей, а запас панели реакций
в нижнем расположении сокращён до 6 dp.

## Продолжение меню и панели — 0062

`0062-menu-touch-and-panel-state.patch` сохраняет восстановленные изменения
14 нативных файлов Telegram. Overlay остаётся источником классов NebulaGram;
правки в подготовленном Gradle-дереве не являются отдельным источником истины.

- Меню проверяет контраст при отрисовке, включая TextView/SimpleTextView,
  и передаёт касания общему раскрытию без перехвата штатного выбора.
- Материал раскрывается из сохранённой точки; повторное открытие и detach
  сбрасывают старую геометрию и пружины.
- Меню сообщения использует общий аниматор; исходное сообщение/альбом скрыто,
  а нижнее расположение не зависит от включённого размытия.
- Ширина кнопки назад зависит от фактического непрочитанного счётчика;
  его обновление также пересчитывает отступ аватара «Избранного».
- Нижняя панель и её вкладки получают ширину из одного перехода; перетаскивание
  линзы папок выбирает папку при отпускании и сбрасывается при detach.
- Параметры стекла подключены к материалу меню и EmojiView, сила отклика
  ограничена безопасным диапазоном параметров Android API.

Проверки: `check-menu-state.py <tree>`, `check-menu-colors.py`,
`check-appearance-controls.py <tree>` и остальные проверки Android workflow.
Полная Java-компиляция с ресурсами выполнена локально; APK и устройство
на этом этапе не проверялись.

Иконки записи видеокружка `input_video` и `input_video_pressed` исключены из
подмены `NebulaIcons`: все наборы используют стандартные ресурсы Telegram.
`check-navigation.py` проверяет оба состояния во всех комбинациях активного
набора/превью, включая вложенные обёртки ресурсов. Видеозвонки не затронуты.

## 0071 — первый кадр стеклянных секций

- `ChatAttachAlert.java`, `drawNebulaSection`: одна строка регистрации списка
  перед проверкой готовности источника. Логика условной перерисовки находится
  в оверлее `NebulaSheetSurface`, проверяется `check-input-controls.py`.

## 0072 — компоновка шапки независимо от Material You

- `ChatActivity.java`, `avatarContainer.onAvatarClick`: удалена зависимость
  меню аватара от палитры; сохранены проверки режима чата/поиска/preview.
- `ChatActivity.java`, создание `audioCallIconItem`: отдельная кнопка звонка
  зависит от отключённой плавающей шапки, а не от источника цветов.
- Оверлей `NebulaChatStyle.header` выбирает normal/saved-компоновку независимо
  от Material You. Проверки: `check-chat-layout.py`, `check-input-controls.py`.

## 0073 — границы длинного текста в шапке

- `ChatAvatarContainer.java`, `drawChild`: ограничение только текста и его
  анимационных копий фактической областью основной строки, включая классическую
  шапку. Аватар, таймеры и остальные дочерние элементы не обрезаются.
- Сохраняются прокрутка/градиент длинного названия и отдельный bounce плавающей
  пилюли. Проверка: `check-header-text-clip.py` (15 сценариев записываемого Canvas),
  `check-chat-native.py`; визуальную проверку устройства тесты не заменяют.

## 0074 — источник стекла панели эмодзи и области касания

- `ChatActivity.java`, `createNebulaEmojiBackground`: источник стекла чата
  вместо фабрики плоского слоя обоев. Захват остаётся `contentView::drawList`,
  сама панель не попадает в свой фон; используются штатные ограничения blur/LiteMode.
- `EmojiView.java`, конструктор: прозрачный режим выбирается до создания детей,
  включая фоны поиска и категорий. Внутренний захват под нижними кнопками рисует
  этот же внешний фон, а не непрозрачную цветную заливку.
- `PagerSlidingTabStrip.java`: отдельные полные ячейки попадания `hitBounds`,
  не меняющие геометрию рисуемого пузыря. Оверлей `NebulaTabGesture` допускает
  небольшой вертикальный дрейф при отпускании уже начатого горизонтального жеста.
  Обычные клики, запреты вкладок, отмена и мультитач сохраняют прежнее поведение.
- Проверки: `check-emoji-panel.py`, `check-discussion-input.py`,
  `check-input-controls.py`, Java-компиляция; тестирование на телефоне обязательно.

### 0075 — search history, story strip and pager selection
- ProfileActivity.SearchAdapter: clear/reload recent settings idempotently; suppress loading and new writes while history is disabled. Existing updateSearchSettings notification refreshes all open adapters.
- DialogsActivity.updateStoriesVisibility: gate both full strip and self-only stories; server story state is untouched.
- MainTabsActivity/MainTabsLayout: feed current/next view and actual pager fraction to the liquid lens; draw directly without a second 280ms tween, snap on completion/cancel. Native pointer drag retains priority.
- General settings: independent history show/save, confirmed clear, and chat-list story strip visibility controls. New search entries appended to preserve positional history IDs.
- Validation: scripts/check-search-stories-pager.py, contract checks and Java compilation. Device visual acceptance remains separate.

### 0076 — adaptive centered title without stories
- ActionBar measurement: measure native text/spans and status drawable in the full usable interval before selecting the centered slot. Do not mirror a large right menu into unused left space.
- ActionBar layout: clamp that slot between the existing start inset and real menu boundary; include title margin and the stories title-container translation. Both animated title views use the same calculation.
- NebulaHomeTitleGeometry keeps short titles centered, shifts only when necessary, and leaves native ellipsis for genuinely oversized titles. Center-home preference, stories gate, search/selection and chat-header behavior stay intact.
- Validation: scripts/check-home-title.py (2016 production geometry combinations + native hooks), story/pager/style checks, ordered patch application and Java compilation; device rendering remains a separate check.

### 0085 — original NebulaGram launcher choices
- `LauncherIconController.LauncherIcon`: six original palettes and localized titles; stable component keys preserve the installed selection across updates. Original artwork does not require Telegram Premium.
- Main and standalone manifests: alias icon/roundIcon resources match the native selector's backgrounds and foregrounds. Default standalone application icon matches the blue variant.
- `scripts/gen-launcher-variants.py`: deterministic assets derived from the existing NebulaGram mark, five legacy/foreground densities, adaptive and monochrome layers. Launcher shape masking remains the system's responsibility.
- `scripts/check-launcher-icons.py`: actual controller compiled against a fake PackageManager; all 36 selection transitions, choice preservation, all-disabled recovery and free availability. Checks manifest/preview agreement, localized titles, dimensions and palette uniqueness. Launcher cache refresh and OEM visual behavior still require device testing.

### 0086 — заголовок чата растёт под действие собеседника
- `ChatAvatarContainer.measureNebulaHeaderWidth`: меряем обе подписи, а не первую существующую. «Печатает» и «выбирает стикер» живут на обычной подписи вместе со своим значком, спокойный статус — на анимированной; учёт только одной оставлял капсулу узкой, и строка обрезалась с двух сторон.
- `ChatAvatarContainer.setTypingAnimation`: в конце `checkActionBar(true)`. Ширина капсулы кэшируется в `nebulaHeaderWidth`, а действие собеседника приходит и уходит само, без смены заголовка, — пересчитать её было некому.
- Ширина может только вырасти относительно прежнего расчёта, так что правило «капсула облегает содержимое» не меняется.
- Проверка: применение всей цепочки из семнадцати патчей по этому файлу; поведение на устройстве — отдельно.

### 0087 — снятие ограничений отправителя по выбору пользователя
- `FlagSecureReason.isSecuredNow`: единственное место, где выставляется `FLAG_SECURE`. При включённой настройке окно не помечается защищённым — снимки экрана и запись снова работают.
- `MessagesController.isChatNoForwards(TLRPC.Chat)` и `isUserNoForwards(TLRPC.UserFull)`: через них проходят запреты пересылки, копирования и сохранения на уровне чата и собеседника.
- Флаг отправителя не трогается: сервер шлёт его как раньше, и о снятии ограничения собеседник не узнаёт. Меняется только то, что этот клиент разрешает делать с уже полученным содержимым.
- Выключено по умолчанию, включается в «Конфиденциальность» через отдельное подтверждение. `NebulaContentProtection` держит значение в памяти: `enabled()` спрашивают из `updateWindowSecure` и из проверок пересылки.
- Запреты на уровне отдельного сообщения (`message.noforwards`) проверяются по коду напрямую в десятках мест и этим патчем не охвачены.
- Проверка: применение цепочки патчей по обоим файлам; поведение на устройстве — отдельно.

### 0088 — «Скопировать ID» в меню профиля
- `ProfileActivity`: константа пункта меню `nebula_copy_id = 9701`, сам пункт рядом с «Скопировать ссылку» и его обработчик. Три вставки, ни одна строка апстрима не переписана.
- Значение форматирует `NebulaIds` по настройке «Формат ID» из общего раздела. У человека форматы совпадают; канал и супергруппа в Bot API получают приставку −100, обычная группа — знак минуса. Канал от группы отличаем через `ChatObject.isChannel`.
- Проверка: применение всей цепочки патчей по `ProfileActivity.java`; копирование на устройстве — отдельно.

### 0093 — своё имя у подключения через NebulaLink
- `LaunchActivity.updateCurrentConnectionState`: при соединении через собственный туннель заголовок берёт `NebulaConnection.connectingKey/connectingId`, и нажатие ведёт в экран NebulaLink, а не в чужой список прокси.
- `ActionBar.setTitleOverlayText` и `DialogStoriesCell.setTitleOverlayText`: подпись «настроить прокси» показывается только для настоящего прокси — решает `NebulaConnection.offersProxySettings(titleId)`.
- `ActionBar.onLayout`: контейнер подписи-оверлея встаёт по той же геометрии, что и заголовок (`NebulaHomeTitleGeometry.left`). С центрированным заголовком подпись оставалась у левого края и висела в углу сама по себе.
- `ChatAvatarContainer` и `ProfileActivity`: те же слова в шапке чата и в профиле.
- `NebulaConnection.throughLink()` спрашивает `NebulaLink.isRoutingThroughTunnel()`; настоящий прокси пользователя ничем не затронут.
- Проверка: применение всей цепочки по каждому файлу; поведение на устройстве — отдельно.

### 0094 — папки сохранений называются NebulaGram
- `ImageLoader.createMediaPaths`, `MediaController.saveFile`/`saveFileInternal`, `SharedConfig.checkSaveToGalleryFiles`, `AndroidUtilities`, `FilesMigrationService`: каждое место, где имя каталога было записано строкой, зовёт `NebulaFolders.dir(parent, "Telegram")`.
- `NebulaFolders` переименовывает: «Telegram» → «NebulaGram», «Telegram Images» → «NebulaGram Images» и так далее. Существующий каталог со старым именем переносится переименованием в пределах того же родителя, если нового ещё нет; ничего не копируется, и прежние сохранения не разъезжаются по двум папкам.
- Относительный родитель — это путь для MediaStore (`RELATIVE_PATH`), а не место на диске: там файловая система не трогается.
- Проверка: применение цепочки, компиляция `:TMessagesProj:compileStandaloneJavaWithJavac`; проверка на устройстве с реальными разрешениями — отдельно.

### 0095 — стекло у панели выделения текста
- `FloatingToolbar.createContentContainer`: без внешней фабрики размытия панель получает `NebulaMenuBackdrop.attachFlat` — тот же снимок окна-источника, что и остальные всплывающие меню.
- Именно `attachFlat`, а не `attach`: `View.setBackground` спрашивает у фона его отступы и применяет их к самому виду. Обычным меню это на руку, а панель выделения считает ширину и высоту заранее и про такой отступ не знает — строка получалась узкой и обрезанной, а всплывающий список рисовался с пустотой вместо пунктов.
- `STYLE_BLACK` (поверх фотографии) остаётся плоским: под панелью там не обои, а сама фотография.
- Проверка: применение цепочки; рендер и ширина на устройстве — отдельно.
### 0096 — капсула шапки в режиме выделения и цвет статуса
- `ActionBar.showActionMode`/`hideActionMode`: плавающая шапка чата гасится вместе с заголовком. Она такой же ребёнок панели, и «Выбрано 1» ложилось прямо на название канала.
- `ActionBar.draw`: ветка центрированной капсулы больше не требует нулевого коэффициента режима выделения — ширина и левый край линейно переходят между капсулой и полной панелью. Раньше всё затухание рисовалась полная ширина и в последний кадр схлопывалась в капсулу: «раздувает шапку и потом обратно в пилюлю».
- `ChatAvatarContainer.nebulaHeaderColor`: цвет надписей в шапке приводится `NebulaChatColors` сразу при создании вида и при каждом обновлении подписи, а не только в `updateColors`. При входе в чат статус успевал мигнуть белым.
- `ChatAvatarContainer.getVisualWidth`: учитывает и анимированную подпись — как и расчёт капсулы.
- Проверка: применение цепочки по обоим файлам, компиляция; анимация на устройстве — отдельно.

### 0097 — выделение удалённых и камера кружка
- `ChatMessageCell.drawInternal`: способ выделения берётся из `NebulaDeletedStyle.mark()` — не выделять, приглушить (как было) или подложить цвет. Цвет накладывается `PorterDuff.SRC_ATOP` поверх нарисованного, поэтому окрашивается пузырь сообщения, а не строка целиком. Кисть кэшируется по паре «способ + цвет».
- `InstantCameraView.resetCameraFrom`: камера кружка выбирается настройкой `NebulaRoundCamera` — как в прошлый раз, фронтальная или основная. Camera1 всегда начинал с фронтальной, Camera2 — с прошлой, и повлиять было нельзя.
- `InstantCameraView.switchCamera`: разворот запоминается, для режима «как в прошлый раз».
- Где сохранять удалённое (личные чаты, группы, каналы, боты) решает `NebulaDeletedStyle.retains` внутри `NebulaDeletedArchive.retain` — это оверлей, патча не требует.
- Проверка: применение цепочки, компиляция; отрисовка и камера на устройстве — отдельно.

### Апстрим 12.10.3 (7089)

- `SharedConfig.ProxyInfo` больше не хранит адрес и порт полями: всё лежит в
  `ProxySettings`, а `ConnectionsManager.setProxySettings` принимает этот объект
  вместо шести аргументов. `NebulaLink` переписан под новый вид; раскладку по
  ключам настроек теперь делает сам `ProxySettings.toSharedPreferences`, а не мы.
- `LocaleController.getStringInternal` берёт облачную строку через
  `localizationExternal`. Наш короткий ответ для `AppName` встал перед ним —
  смысл прежний: имя форка не должно приходить из чужого языкового пакета.
- `TMessagesProj_AppStandalone` включает `shrinkResources` у сборки standalone.
  Снятие ресурсов требует включённого R8, поэтому диагностическая сборка
  `-PnebulaMinify=false` падала при настройке; теперь флаги идут в паре.
- У Telegram появился одиннадцатый сабмодуль — `TMessagesProj_Modules/media`.
  `settings.gradle` применяет его `core_settings.gradle` при настройке, так что
  без него проект не конфигурируется вовсе.

### Settings localization and layout (2026-09-18)

- `0098-settings-localization-inputs.patch` extends the existing buildSrc string
  inputs from `strings.xml` to `strings*.xml` for both default/translated binary
  dictionaries and language discovery. Nebula strings remain in separate overlay
  XML files; no fallback masks `LOC_ERR`, and the native stable-ID/shrinker path
  is retained. `scripts/check-settings-localization.py` checks every overlay file.
- All nine Nebula settings fragment types use `NebulaSettingsLayout`: the existing
  ActionBar is embedded once, and the body is measured below its actual height.
  Login, onboarding, Telegram's profile/settings page and the composer are not
  wrapped. A JVM layout-contract test covers narrow/wide/short/tall sizes and a
  hidden bar; this is not an Android device rendering test.
- Shared cards/rows/introductions/buttons use compact native-style spacing,
  SP-sized text, sentence-case section headers and theme-aware surfaces. Existing
  routes, preference keys, switch-style choices and server probe behavior remain.

### 0099 — «Спрашивать» у кружка, значок поддержавшего и беззвучные в счётчиках
- `ChatActivityEnterView`: короткое нажатие, переключающее голос/кружок, — единственное место, где у пользователя можно что-то спросить. Запись кружка начинается в то же мгновение, когда палец ложится на кнопку, и вопрос там съел бы жест. При переходе в режим кружка и режиме «Спрашивать» `NebulaRoundCamera.ask` спрашивает камеру; ответ применяется к ближайшей записи.
- `ProfileActivity.updateProfileData`: имя проходит через `NebulaDonation.decorate`. Значок дописывается эмодзи-спаном, а не занимает место справа от имени: там живут отметка проверенного аккаунта и эмодзи-статус Telegram. Значок ставится только на собственный профиль и только при включённой настройке; сервер о нём не знает.
- `MessagesStorage.calcUnreadCounters`: при включённой настройке признак `DIALOG_FILTER_FLAG_EXCLUDE_MUTED` снимается на время подсчёта. Состав папки не меняется — меняется одно число на вкладке.
- `MessagesStorage.nebulaRecountFilters`: пересчёт по требованию. Счётчики считаются один раз и лежат готовыми, поэтому без него переключатель выглядел бы мёртвым до следующего события от сервера.
- Проверка: применение всей цепочки, компиляция `:TMessagesProj_AppStandalone:compileAfatStandaloneJavaWithJavac`; жест, значок и счётчики на устройстве — отдельно.

### 0100 — вкладки папок снизу
- `DialogsActivity`: панель вкладок добавляется с `Gravity.BOTTOM`, когда включена настройка. Снизу до неё достаёт большой палец — это и есть вся причина.
- Стопка верхних панелей: внизу вкладки из неё выходят. Их высота больше не двигает ни поиск, ни истории, ни список, а сама панель поднимается ровно на нижнюю панель NebulaGram, если та включена.
- `getTopOffset`: высота вкладок не считается, когда они внизу.
- `additionNavigationBarHeight` и `additionFloatingButtonOffset`: к тому, что резервирует нижняя панель, прибавляется `NebulaFolderTabs.reserved()`. Без этого последний чат оказался бы под вкладками, а кнопка записи — на них.
- Место панели выбирается при разметке списка чатов, поэтому настройка применяется при следующем его открытии; строка настройки об этом говорит.
- Проверка: применение всей цепочки, компиляция `:TMessagesProj_AppStandalone:compileAfatStandaloneJavaWithJavac`; поведение при прокрутке, свайпе между папками и вместе с нижней панелью — на устройстве отдельно.

### 0101 — положение папок и плавность фона аватарки
- `DialogsActivity.onLayout`: нижние вкладки получают координату от нижнего края с учётом системной навигации и главной панели. Высота шапки к ней больше не прибавляется. Верхние вкладки сохраняют верхнюю стопку.
- Нижние вкладки не оставляют отступ сверху списка; место снизу резервируется только для видимых вкладок, с учётом анимации поиска. Обновление работает через существующий слушатель настройки и при возвращении на экран.
- `ActionBar`: круг аватарки меняет прозрачность вместе с поиском, режимом выделения и самой аватаркой вместо включения на последнем кадре.
- Overlay: интерактивное превью показывает положение папок; значки профиля используют пять собственных векторов и `ReplacementSpan`. Адрес сервиса и токен убраны из обычных настроек.
- `scripts/check-settings-runtime.py <patched-tree>` исполняет проверки геометрии, резервирования отступов, анимации и выравнивания значков. Проверки JVM не заменяют проверку жестов и отрисовки на телефоне.
# Bottom-folder live glass (2026-09-20)

- `0106-bottom-folder-live-glass.patch` extends DialogsActivity's existing second
  blur capture region to the actual translated folder bounds, with a 48dp margin.
  This applies with or without the main navigation bar. Only the existing chat
  list capture is used; the glass never captures itself. Bottom folders no longer
  inflate the top blur area.
- `NebulaFolderGlass` supplies a translucent tint and edge highlights specifically
  for bottom folders. Top tabs retain their original material; ordinary/minimal
  styles and reduced-effects fallbacks remain available.
- The settings folder preview records its sample chat content into a RenderNode,
  updating on content/size changes rather than running a perpetual capture loop.
  No screenshot/bitmap readback or additional screen-wide blur source is added.
- `scripts/check-folder-glass.py` exercises the production capture block for 432
  combinations, including hidden bars, hidden tabs, display density, and translated
  tabs. Full device rendering/scrolling remains a separate acceptance check.

### 0109 — папки в окне пересылки

- В выборе получателя нижние вкладки остаются снизу, но поднимаются над полем
  комментария. Отступ списка учитывает высоту вкладок и их новый нижний зазор,
  чтобы последний чат не оказывался под панелью.
