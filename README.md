# NebulaGram

Форк Telegram для Android, iOS и Desktop со встроенным туннелем **NebulaLink**:
подписки Remnawave, ядра Xray и sing-box, свои настройки и свой дизайн.

Два принципа, из которых следует всё остальное:

1. **Общий код — только ядро.** UI Telegram на трёх платформах написан на трёх
   разных языках, шарить его невозможно. Шарится Go-ядро туннеля (Xray и
   sing-box сами написаны на Go) — см. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
2. **Апстрим не редактируется.** Форк — это оверлей из новых файлов плюс серия
   патчей на 1–3 строки. Обновление на новую версию Telegram — смена тега
   сабмодуля — см. [docs/UPSTREAM.md](docs/UPSTREAM.md).

## Состояние

| Компонент | Статус |
|---|---|
| Go-ядро: подписки, парсеры ссылок, конфиги, замеры, состояние, JSON-фасад | готово, покрыто тестами |
| Обёртки над Xray-core / sing-box (`runtime/`) | каркас, подключение ядер |
| Android-клиент (Material 3) | не начат |
| iOS-клиент (Liquid Glass) | не начат |
| Desktop-клиент (форк tdesktop) | не начат |

## Быстрый старт

```bash
cd core && go test ./...          # тесты ядра
bash scripts/build-core.sh android   # nebulalink.aar
bash scripts/build-core.sh desktop   # libnebulalink.{dll,so,dylib} + заголовок
```

Подключение апстримов (делается один раз):

```bash
git submodule add https://github.com/DrKLO/Telegram vendor/telegram-android
git submodule add https://github.com/TelegramMessenger/Telegram-iOS vendor/telegram-ios
git submodule add https://github.com/telegramdesktop/tdesktop vendor/tdesktop
```

## Как клиент разговаривает с ядром

Одна функция на всех платформах:

```
Call("subscription.add", "{\"url\":\"https://panel.example/api/sub/abc\"}")
  -> {"ok":true,"data":{"servers":12,"format":"links", ...}}
```

Список методов — в `core/api/api.go` (карта `handlers`). Экраны настроек
клиенты не верстают вручную: `menu.get` отдаёт декларативное описание всех
экранов, каждый клиент рендерит его нативно (Material 3, Liquid Glass, Qt).
Новая опция добавляется один раз в Go и появляется на трёх платформах.

## Документация

* [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — слои, почему Go, почему JSON-фасад, режимы туннеля.
* [docs/UPSTREAM.md](docs/UPSTREAM.md) — модель форка, реестр хуков, процедура обновления.
* [docs/DESIGN.md](docs/DESIGN.md) — Material 3 на Android, Liquid Glass на iOS, слои риска.

## Лицензия и распространение

Telegram-клиенты распространяются под GPLv2+ (Android, Desktop) и GPLv2
(iOS) — форк обязан оставаться открытым и публиковать исходники. Сборка iOS
подписывается пользователем (Sideloadly/AltStore/TrollStore): NetworkExtension
не используется, туннель работает как локальный SOCKS5, поэтому платный
Apple-аккаунт не нужен.
