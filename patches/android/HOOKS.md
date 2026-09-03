# Реестр хуков — android

Каждый патч в `patches/android/` описан здесь.
Патч без записи не принимается: см. docs/UPSTREAM.md, раздел 4.

Апстрим: `DrKLO/Telegram`, зафиксирован на 12.10.1 (7038), коммит `62b56a07`.

| Патч | Файл апстрима | Якорь | Назначение | Строк |
|---|---|---|---|---|
| `0001-gradle-link-nebulalink-core.patch` | `TMessagesProj/build.gradle` | первая строка блока `dependencies {` | подключает `libs/nebulalink.aar` — Go-ядро | 1 |
| `0002-application-loader-start-core.patch` | `TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java` | сразу после `super.onCreate();` в `onCreate()` | запускает ядро при старте приложения | 2 |
| `0003-standalone-abi-splits.patch` | `TMessagesProj_AppStandalone/build.gradle` | перед строкой `defaultConfig.versionCode = ...` и после блока `applicationVariants.all` апстрима | по одному APK на архитектуру; список архитектур задаётся параметром `-PnebulaAbis` | 40 |

| `0004-launch-show-welcome.patch` | `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java` | перед `return new IntroActivity();` | показывает наш экран приветствия, пока он не пройден | 3 |
| `0005-proxy-screen-nebulalink-entry.patch` | `TMessagesProj/src/main/java/org/telegram/ui/ProxyListActivity.java` | поле рядов, начало `updateRows`, обработчик клика, `getItemViewType`, привязка ячейки | пункт входа в NebulaLink на экране прокси | 13 |

| `0006-login-typography.patch` | `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | после сборки массива `views` со слайдами входа | отдаёт готовые вью нашему оформителю | 2 |

Итого **74 вставленные строки**, ни одной изменённой.

Про экраны входа отдельно. Полностью своя вёрстка там невозможна без риска
сломать вход: `PhoneView` и `LoginActivitySmsView` — внутренние классы
`LoginActivity`, работающие с его приватным состоянием, а завершение
авторизации `onAuthSuccess` приватно и заканчивается `needFinishActivity` на
самом фрагменте, то есть снаружи не вызывается. Поэтому хук отдаёт уже
собранные вью оформителю, который правит их по типу элемента, не касаясь ни
логики, ни приватных полей. Если апстрим перестроит разметку, оформитель
найдёт меньше элементов, и экран останется штатным.

Про выбор экрана прокси для точки входа: экран настроек `ProfileActivity` —
17 тысяч строк и меняется каждый релиз, `ProxyListActivity` — тысяча и меняется
редко. Тематически это то же самое: NebulaLink и есть поставщик прокси.

Про приветствие: вставка проверяет `NebulaIntroFragment.shouldShow()`, поэтому
после первого прохода управление возвращается штатному интро Telegram, и наш
код перестаёт участвовать в этом пути вообще.

Про третий патч: ядро Xray добавляет ~11 МБ нативного кода на каждую
архитектуру, и в общем APK пользователь качал бы четыре копии ради одной.
Патч только вставляет блоки — существующий `applicationVariants.all` апстрима
не трогается, рядом добавляется второй, который даёт каждому файлу своё имя и
свой versionCode (иначе апдейтеры отвергнут два APK с одинаковым кодом).

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
