<div align="center">

<img src="design/icon/icon-1024.png" width="120" alt="NebulaGram">

# NebulaGram

**Форк Telegram со встроенным туннелем.** Ваши серверы, ваши ключи,
без сторонних релеев и без второго приложения.

[![License](https://img.shields.io/badge/license-GPL--2.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12.10.1-3DDC84)](docs/BUILD-ANDROID.md)
[![Core](https://img.shields.io/badge/Xray-v1.260327-6E56CF)](runtime/xray)
[![Open source](https://img.shields.io/badge/open%20source-yes-brightgreen)](#лицензия-и-открытость)

</div>

---

## Что это

Обычный Telegram, в который встроен туннель **NebulaLink**: подписки Remnawave,
ядра Xray и sing-box, свои настройки и свой дизайн. Не нужно держать рядом
второе приложение и переключать VPN руками — мессенджер сам ходит через ваш
сервер.

* **Подписки Remnawave** — вставили ссылку, получили список серверов. Учитывается
  лимит устройств: клиент шлёт HWID-заголовки, как это делает панель.
* **Шесть протоколов** — VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC.
  Reality, XHTTP, gRPC, WebSocket и HTTPUpgrade поверх них.
* **Свой конфиг у каждого сервера.** Если панель отдаёт не ссылки, а готовые
  конфиги Xray или sing-box — они сохраняются целиком и запускаются как
  написаны, вместе с маршрутизацией, DNS и фрагментацией. Свой конфиг можно
  вставить руками.
* **Material You на Android** — палитра берётся из ваших обоев.
* **Звонки через ваш сервер**, без сторонних релеев.

## Платформы

| Клиент | Основа | Состояние |
|---|---|---|
| Android | форк `DrKLO/Telegram` 12.10.1 | экраны и ядро готовы, сборка в CI |
| iOS | форк `Telegram-iOS`, Swift + Liquid Glass | в планах |
| Desktop | форк `tdesktop`, C++/Qt | в планах |

Ядро туннеля — общее для всех трёх, на Go: и Xray-core, и sing-box написаны на
Go, поэтому один модуль даёт `.aar` для Android, `.xcframework` для iOS и
`.dll/.so/.dylib` для десктопа.

## Скачать

Сборки лежат в [Releases](../../releases) — по одному файлу на архитектуру:

| Файл | Кому |
|---|---|
| `NebulaGram-arm64-v8a.apk` | практически все современные телефоны |
| `NebulaGram-armeabi-v7a.apk` | старые 32-битные устройства |
| `NebulaGram-x86_64.apk` | эмуляторы, часть Chromebook |

Ставится сайдлоадом: разрешите установку из неизвестных источников и откройте
файл. Если не уверены, какой брать, — берите `arm64-v8a`.

## Как это не ломается при обновлениях Telegram

Главная проблема форков мессенджера в том, что через год они отстают от
апстрима на недели работы. Здесь другое устройство:

> Исходники Telegram лежат **чистым сабмодулем и не редактируются**. Форк — это
> оверлей из новых файлов плюс серия патчей на считанные строки.

Весь след NebulaGram в коде Telegram — **31 вставленная строка в трёх файлах** и
ни одной изменённой:

| Файл | Зачем | Строк |
|---|---|---|
| `TMessagesProj/build.gradle` | подключить ядро | 1 |
| `ApplicationLoader.java` | запустить ядро | 2 |
| `TMessagesProj_AppStandalone/build.gradle` | APK на каждую архитектуру | 28 |

Прокси включается через штатный `ConnectionsManager.setProxySettings`, а звонки
— через настройку, которую `VoIPService` читает сам. В сетевой стек и в звонки
не внесено ни одной правки: именно это обычно и гниёт в форках.

Обновление на новую версию Telegram — одна команда:

```bash
bash scripts/sync-upstream.sh android
```

Ночной workflow проверяет патчи на свежем апстриме и заводит issue в день
выхода новой версии, а не когда вы сели обновляться.

## Сборка

```bash
bash scripts/build-core.sh android      # ядро -> nebulalink.aar
bash scripts/apply-overlay.sh android   # оверлей + патчи
TELEGRAM_APP_ID=... TELEGRAM_APP_HASH=... bash scripts/inject-keys.sh android
cd vendor/telegram-android && ./gradlew :TMessagesProj_AppStandalone:assembleAfatRelease
```

Нужен свой ключ Telegram с [my.telegram.org](https://my.telegram.org) — тот, что
лежит в исходниках Telegram, зарезервирован за официальным клиентом и войти в
аккаунт не даст. Подробности в [docs/BUILD-ANDROID.md](docs/BUILD-ANDROID.md).

Проверить ядро без сборки клиента:

```bash
cd bind && go run ./cmd/nldiag -url "ссылка_на_подписку" -connect
```

## Документация

| Документ | О чём |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | слои, почему Go, почему JSON-фасад, режимы туннеля |
| [docs/UPSTREAM.md](docs/UPSTREAM.md) | модель форка, реестр хуков, процедура обновления |
| [docs/DESIGN.md](docs/DESIGN.md) | Material 3, Liquid Glass, слои риска |
| [docs/BUILD-ANDROID.md](docs/BUILD-ANDROID.md) | ключи, подпись, сборка |

## Лицензия и открытость

NebulaGram распространяется под **GPL v2** — той же лицензией, что и Telegram
для Android, форком которого он является. Это значит:

* **исходники открыты полностью** — всё, что попадает в APK, лежит в этом
  репозитории;
* любой может собрать свою сборку из этих исходников и проверить, что она
  совпадает с выложенной;
* производные работы обязаны оставаться под GPL v2 и публиковать исходники.

Текст лицензии — в [LICENSE](LICENSE).

Что касается приватности: ключи от ваших серверов и подписка хранятся только на
устройстве, в приватном каталоге приложения. Никакой телеметрии и аналитики
проект не добавляет — трафик идёт на ваш сервер и в Telegram, и больше никуда.

## Оговорка

Неофициальный форк. Проект не связан с Telegram Messenger Inc., не поддерживается
и не одобряется ею. Названия Telegram и логотипы Telegram принадлежат их
владельцам; NebulaGram использует собственные название и знак.
