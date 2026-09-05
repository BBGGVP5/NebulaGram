# Реестр хуков — android

Каждый патч в `patches/android/` описан здесь.
Патч без записи не принимается: см. docs/UPSTREAM.md, раздел 4.

Апстрим: `DrKLO/Telegram`, зафиксирован на 12.10.1 (7038), коммит `62b56a07`.

| Патч | Файл апстрима | Якорь | Назначение | Добавлено / удалено |
|---|---|---|---|---|
| `0001-gradle-link-nebulalink-core.patch` | `TMessagesProj/build.gradle` | dependencies | Подключает Go-ядро | +9 / −0 |
| `0002-application-loader-start-core.patch` | `ApplicationLoader.java` | onCreate | Инициализирует NebulaLink | +2 / −0 |
| `0003-standalone-abi-splits.patch` | `TMessagesProj_AppStandalone/build.gradle` | перед defaultConfig.versionCode | Сохраняет подписанный sideload-пакет и собирает единый APK для всех ABI | +12 / −0 |
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
