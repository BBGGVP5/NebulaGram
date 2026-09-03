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
cd vendor/telegram-android && ./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
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

## Почему именно `assembleAfatStandalone`

У модуля `TMessagesProj_AppStandalone` объявлены типы сборки `debug` и
`standalone`; типа `release` там нет, но Gradle создаёт его сам — **без
подписи**. Собранный так APK Android отказывается ставить со словами «возникла
проблема с файлом приложения». Подпись, минификация и нужный манифест есть
только у `standalone`.

## Push-уведомления

Пуш в Telegram устроен так: приложение получает токен от **вашего** Firebase,
отдаёт его серверам Telegram (`account.registerDevice`, `token_type = 2`), а
Telegram шлёт уведомление через Firebase, используя credentials, которые вы
привязали к своему `api_id`. Само сообщение приходит потом по MTProto — пуш
только будит приложение.

Отсюда следует, что своим Firebase-проектом дело не ограничивается: серверам
Telegram нужно разрешение отправлять через него. Без этого регистрация токена
заканчивается ошибкой `APP_PUSH_APIKEY_MISSING`, а пуши не приходят.

Порядок:

1. **Firebase Console** → создать проект → добавить приложение Android.
   Имя пакета указать **точно** то же, что собирает CI:

   ```
   app.nebulagram.messenger
   ```

   Отличается хоть одним символом — токен не выдастся. Имя задаётся переменной
   `APP_PACKAGE` в `scripts/inject-keys.sh`; если хотите другое, меняйте в обоих
   местах сразу.

2. Скачать **`google-services.json`** и положить в оверлей:

   ```
   platform/android/overlay/TMessagesProj/google-services.json
   platform/android/overlay/TMessagesProj_AppStandalone/google-services.json
   ```

   Оверлей перекрывает файлы Telegram при сборке, патч не нужен. Секретом этот
   файл не является — он и так лежит внутри любого APK.

3. **Firebase Console** → *Project settings* → *Service accounts* →
   *Generate new private key*. Получится второй файл, и вот он **секретный** — в
   репозиторий не кладётся.

4. **my.telegram.org** → ваше приложение → загрузить этот service-account JSON
   в настройки push/FCM. Так серверы Telegram получают право отправлять через
   ваш Firebase.

Пока шаги 1–4 не сделаны, сборка всё равно проходит: скрипт подстановки ключей
дописывает наш пакет в файл Telegram, чтобы плагин Google Services не остановил
сборку. Приложение работает, но пуши не приходят — сообщения появляются, пока
живо соединение.

## Google-сервисы

`google-services.json` в репозитории уже лежит — это конфигурация Telegram для
пуш-уведомлений через FCM. Сборка с ней работает; свои пуши заведёте позже,
заменив файл на конфиг своего проекта Firebase.
