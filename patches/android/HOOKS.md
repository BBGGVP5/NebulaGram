# Реестр хуков — android

Каждый патч в `patches/android/` описан здесь.
Патч без записи не принимается: см. docs/UPSTREAM.md, раздел 4.

Апстрим: `DrKLO/Telegram`, зафиксирован на 12.10.1 (7038), коммит `62b56a07`.

| Патч | Файл апстрима | Якорь | Назначение | Строк |
|---|---|---|---|---|
| `0001-gradle-link-nebulalink-core.patch` | `TMessagesProj/build.gradle` | первая строка блока `dependencies {` | подключает `libs/nebulalink.aar` — Go-ядро | 1 |
| `0002-application-loader-start-core.patch` | `TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java` | сразу после `super.onCreate();` в `onCreate()` | запускает ядро при старте приложения | 2 |
| `0003-standalone-abi-splits.patch` | `TMessagesProj_AppStandalone/build.gradle` | перед строкой `defaultConfig.versionCode = ...` | по одному APK на архитектуру вместо одного «жирного» | 28 |

Итого **31 вставленная строка**, ни одной изменённой.

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
