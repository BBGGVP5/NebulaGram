# Сборка Android

## Что нужно один раз: свой API-ключ Telegram

В репозитории Telegram лежит его собственный ключ (`APP_ID = 4`). Он
зарезервирован за официальным клиентом: сборка с ним компилируется, но войти в
аккаунт не даст. Нужен свой.

1. Откройте **https://my.telegram.org** с любого устройства.
2. Введите номер телефона в международном формате (`+7...`) и нажмите *Next*.
3. Код подтверждения придёт **в сам Telegram**, в чат Telegram Service
   Notifications, а не по SMS. Введите его.
4. На главной странице выберите **API development tools**.
5. Заполните форму:
   * **App title** — например `NebulaGram`;
   * **Short name** — латиницей, 5–32 символа, например `nebulagram`;
   * **URL** — можно оставить пустым;
   * **Platform** — `Android`;
   * **Description** — пара слов, например `Telegram fork with a built-in tunnel`.
6. Нажмите *Create application*. На следующей странице будут:
   * **App api_id** — число;
   * **App api_hash** — 32 шестнадцатеричных символа.

### Если форма отдаёт «ERROR»

Известная особенность my.telegram.org, не ваша ошибка. Что обычно помогает:
смена браузера, отключение блокировщиков, вход без VPN (или наоборот, с VPN,
если провайдер режет домен), пауза в несколько минут и повтор. Аккаунту должно
быть больше нескольких дней.

### Что важно знать про ключ

* Приложение создаётся **один раз на аккаунт**; потом страница показывает уже
  созданное, а не форму.
* Ключ привязан к вашему аккаунту и по условиям Telegram отвечаете за него вы:
  при массовых нарушениях его отзывают, поэтому в публичный репозиторий он не
  выкладывается.
* В APK ключ всё равно попадает — секретом в криптографическом смысле он не
  является, но и публиковать его отдельно смысла нет.

## Как ключ попадает в сборку

Никак не через git. Дерево `vendor/telegram-android` — артефакт, ключ
подставляется в него прямо перед сборкой:

Сабмодули тянутся рекурсивно: у самого Telegram их десять — ffmpeg, libvpx,
dav1d, opus и другие, — и без них сборка не конфигурируется.

```bash
git submodule update --init --depth 1 vendor/telegram-android
git -C vendor/telegram-android submodule update --init --depth 1 --recursive

bash scripts/build-core.sh android          # nebulalink.aar
bash scripts/apply-overlay.sh android       # оверлей + патчи + aar
TELEGRAM_APP_ID=1234567 \
TELEGRAM_APP_HASH=ваш_хеш \
  bash scripts/inject-keys.sh android
cd vendor/telegram-android && ./gradlew :TMessagesProj_AppStandalone:assembleAfatRelease
```

`inject-keys.sh` заодно меняет `APP_PACKAGE` на `app.nebulagram.messenger`:
форк не должен занимать имя пакета официального клиента, иначе два приложения
не встанут рядом. Переопределяется переменной `APP_PACKAGE`.

В CI это два секрета репозитория — `TELEGRAM_APP_ID` и `TELEGRAM_APP_HASH`
(*Settings → Secrets and variables → Actions → New repository secret*).

## Подпись

Отдельно ничего заводить не нужно: Telegram кладёт в репозиторий отладочный
keystore `TMessagesProj/config/release.keystore` с паролем `android` и алиасом
`androidkey` (`gradle.properties`). Для своих сборок и sideload этого хватает.

Свой ключ — когда захотите, чтобы обновления ставились поверх ваших же сборок:

```bash
keytool -genkeypair -v -keystore nebulagram.keystore \
  -alias nebulagram -keyalg RSA -keysize 4096 -validity 10000
```

Дальше положите файл в `TMessagesProj/config/release.keystore` (шагом сборки,
не коммитом) и передайте `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` через
`-P` или `gradle.properties`. **Потеряете keystore — обновлять установленные
сборки станет нечем**, придётся переустанавливать с потерей данных.

## Google-сервисы

`google-services.json` в репозитории уже лежит — это конфигурация Telegram для
пуш-уведомлений через FCM. Сборка с ней работает; свои пуши заведёте позже,
заменив файл на конфиг своего проекта Firebase.
