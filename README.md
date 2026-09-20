# KamiGram — мод Telegram для Android (как Nekogram), без своего `api_id` / `api_hash`

**KamiGram** — минималистичный клиент: `com.kami.gram`, только arm64-v8a, максимальная экономия
мобильного трафика и максимальная скорость работы. Стикеры и премиум-эмодзи **не загружаются вообще**,
автоскачивание медиа **выключено по умолчанию**, все тяжёлые анимации заменены мгновенными заглушками.

Это **не написание клиента с нуля**. Мы делаем ровно то, что делают Nekogram, NekoX, OwlGram,
Telegram-FOSS: берём официальные открытые исходники Telegram для Android
([DrKLO/Telegram](https://github.com/DrKLO/Telegram)) и **патчим их** — брендинг, свой `package id`,
свои фичи. Затем собираем APK в CI (GitHub Actions) или локально.

> **Главное про ключи.** Свои `api_id` / `api_hash` **не нужны**. В официальных исходниках уже
> вшиты рабочие значения, которые используют все форки:
> ```java
> // TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
> public static int APP_ID = 4;
> public static String APP_HASH = "014b35b6184100b085b0d0572f9b5103";
> ```
> Именно эти значения стоят в [Nekogram](https://github.com/Nekogram/Nekogram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java)
> и [NekoX](https://github.com/NekoX-Dev/NekoX/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java) —
> то есть весь мир собирает моды на официальных встроенных кредах. Если когда-нибудь захочется свои,
> они подставляются в тот же файл (или через `local.properties`).

---

## Как получить APK (2 клика)

1. Открыть **Actions → Build TG Mod APK → Run workflow**.
2. (Опционально) поменять название/`package id`/ABI/ветку исходников — дефолты уже подставлены.
3. Через ~40–70 минут (первая сборка; дальше быстрее за счёт `ccache` и Gradle-кэша) готовый APK будет:
   * в **Artifacts** запуска (`tgmod-apk-*`),
   * в **Releases** (`mod-<версия>-r<N>`) — если включён чекбокс «Создать GitHub Release».

Каждая сборка дополнительно печатает в summary:
`package id`, версию, ABI, размер APK, **SHA-256**, ABI внутри APK и результат проверки подписи.

### Параметры запуска

| Параметр | По умолчанию | Что делает |
|---|---|---|
| `app_name` | `KamiGram` | Название приложения: ярлык, имя уведомлений, UI |
| `app_package` | `com.kami.gram` | `applicationId` — чтобы мод ставился **рядом** с официальным Telegram |
| `version_suffix` | `-mod` | Суффикс версии (будет `12.10.3-mod`) |
| `abis` | `arm64-v8a` | Только arm64 — почти все современные телефоны; APK в 3–4 раза меньше, сборка быстрее |
| `max_economy` | `1` | MAX ECONOMY: power-saver, анимации/автоплей/частицы/blur выключены |
| `no_stickers` | `1` | Стикеры и премиум-эмодзи не загружаются (0 байт трафика) |
| `autodownload_off` | `1` | Автоскачивание медиа выключено по умолчанию |
| `slim_heavy` | `1` | Урезать тяжёлые Lottie-анимации: `1` (крупные) / `all` (все) / `0` |
| `res_configs` | `ru,en` | Какие локали оставить в APK (`all` — все) |
| `tg_ref` | `master` | Ветка/коммит DrKLO/Telegram, от которого собираем |
| `disable_billing` | `0` | `1` — выключить Google Play Billing (в сборке вне Play он всё равно мёртв) |
| `create_release` | `true` | Публиковать APK в Releases |

---

## Что именно патчится

Патчер — [`mod/apply-mod.sh`](mod/apply-mod.sh). Он идемпотентный: повторный запуск ничего не ломает.
Каждый патч проверяется, при критичной ошибке сборка падает, а не выпускает «мод», который на самом
деле стоковый Telegram.

| # | Патч | Зачем |
|---|---|---|
| P0 | Проверка встроенных `APP_ID`/`APP_HASH` | Свои ключи не нужны, но скрипт убеждается, что они на месте |
| P1 | `APP_PACKAGE`, `APP_VERSION_NAME` в `gradle.properties` | Свой `package id` (ставится рядом с оригиналом) и версия с суффиксом |
| P2 | `<string name="AppName">` во всех локалях | Ярлык и системные упоминания = имя мода |
| P3 | `LocaleController.getStringInternal()` | Иначе «облачные» строки Telegram перетирают имя мода в UI обратно на «Telegram» |
| P4 | `google-services.json` (все модули) | Без этого сборка падает: `No matching client found for package name` |
| P5 | `abiFilters` | Собираем только нужные ABI — в разы быстрее |
| P6 | `BuildVars.CHECK_UPDATES = false` | Мод не должен предлагать скачать официальный Telegram APK |
| P7 | `IS_BILLING_UNAVAILABLE` (опция) | Убирает бесполезные покупки вне Google Play |
| P8 | `-DCMAKE_C*_COMPILER_LAUNCHER=ccache` | Кэш C/C++ между сборками: повторный билд в разы быстрее |
| P9 | Свой keystore (опция) | Если нужна своя подпись, а не публичный upstream-ключ |
| P10 | `DownloadController` — пресеты и маски = 0 | **Автоскачивание медиа выключено по умолчанию** (моб./Wi-Fi/роуминг), preload видео/музыки/историй — off |
| P11 | `MediaDataController` — 4 точки блокировки | **Стикеры, маски, премиум-эмодзи, подарочные/TON-стикеры и generic-анимации не загружаются вообще** |
| P12 | `LiteMode.getValue()` → `PRESET_POWER_SAVER` | MAX ECONOMY: анимированные эмодзи и стикеры, автоплей GIF/видео, частицы, blur, кастомные обои — off |
| P13 | `androidResources.localeFilters` | В APK остаются только нужные языки (`ru,en` по умолчанию) → меньше APK |
| P14 | Заглушки Lottie `res/raw/*.json` | 54 тяжёлые анимации (11 МБ исходников) проигрываются за 1 кадр — эффекты премиума и подарков невидимы, APK легче |

После прогона рядом с исходниками появляются:
* `MOD_INFO.txt` — что за мод, из какого коммита, какие патчи применились;
* `mod-changes.patch` — полный diff изменений (его можно посмотреть и загрузить как artifact).

---

## Экономия трафика и скорости — что именно выключено

| Что | Как | Эффект |
|---|---|---|
| Стикеры (обычные, маски, анимированные) | 4 точки блокировки в `MediaDataController`: `loadStickers`, `loadFeaturedStickers`, `loadStickersByEmojiOrName`, `areStickersLoaded` | Наборы стикеров (мегабайты) не запрашиваются и не скачиваются вообще |
| Премиум-эмодзи и анимированные реакции | те же блокировки + power-saver | `.tgs`/`.json` премиум-эмодзи не грузятся: рисуется обычный статичный эмодзи |
| Подарочные и TON-стикеры, generic-анимации, иконки топиков | `loadStickersByEmojiOrName` | Не грузятся |
| Автоскачивание фото/видео/документов/аудио | пресеты и маски `DownloadController` = 0, `globalAutodownloadEnabled = false` | Медиа качается **только** когда вы сами нажали — ни в Wi-Fi, ни в мобильной сети |
| Preload видео, музыки, историй | флаги пресетов = 0 | Ничего не подгружается в фоне |
| Автоплей GIF / видео / видеокружков | `LiteMode.FLAG_AUTOPLAY_*` = off | Трафик не тратится на проигрывание того, что вы не смотрели |
| Анимации интерфейса, частицы, blur, кастомные обои | `LiteMode.getValue() → PRESET_POWER_SAVER` | Меньше CPU, батареи и RAM; UI работает мгновенно |
| Тяжёлые Lottie-эффекты (54 файла, 11 МБ) | заглушки в `res/raw` | Эффекты премиума/подарков проигрываются за 1 кадр, APK легче |
| Лишние языки | `localeFilters` = `ru,en` | Меньше APK; остальные языки приходят «облачными» строками с сервера |

Проверить эффект: Настройки → Данные и хранилище — все галочки автоскачивания будут сняты;
Настройки → Энергосбережение — все тумблеры выключены (это принудительно, мод не даст их включить обратно).

## Установка APK

1. Скачать APK из artifact/Release.
2. Разрешить установку из неизвестных источников.
3. Поставить. Мод живёт **параллельно** с официальным Telegram (свой `package id`), аккаунт тот же —
   просто логинитесь по номеру, как обычно.

**Подпись.** По умолчанию используется публичный upstream-keystore
(`TMessagesProj/config/release.keystore`, пароли `android` / `androidkey` / `android`) — тот же,
что в публичном репозитории DrKLO. Это удобно (сборки обновляются «поверх», ничего не хранить в
секретах), но означает, что подпись не приватная. Нужна своя — передайте
`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` в патчер, и он подменит keystore.

---

## Локальная сборка (без CI)

Требуется ровно то же, что в upstream `Dockerfile`: JDK 17, Android SDK `platforms;android-36`,
`build-tools;36.0.0`, `ndk;27.2.12479018`, `cmake;3.22.1`.

```bash
# 1. Исходники Telegram (с сабмодулями)
git clone --recursive --depth 1 https://github.com/DrKLO/Telegram.git telegram-src

# 2. Применить мод
TG_DIR=./telegram-src \
APP_NAME=KamiGram \
APP_PACKAGE=com.kami.gram \
ABIS=arm64-v8a \
MAX_ECONOMY=1 NO_STICKERS=1 AUTODOWNLOAD_OFF=1 SLIM_HEAVY=1 RES_CONFIGS=ru,en \
bash mod/apply-mod.sh

# 3. Собрать
cd telegram-src
./gradlew :TMessagesProj_App:assembleAfatRelease
# APK: TMessagesProj_App/build/outputs/apk/afat/release/app.apk
```

Секреты для этого **не нужны вообще**: `google-services.json`, keystore и пароли уже лежат в
публичном репозитории Telegram, а сервисные ключи (карты, Sentry) в релизной сборке не требуются.

---

## Как добавить свои фичи

Всё модифицирование — функции вида `P<N>` внутри `mod/apply-mod.sh`. Добавляйте по одной, каждая с
проверкой (`has ... || die ...`), чтобы сборка падала, если upstream поменял код, а не тихо
выпускала стоковый APK.

```bash
# P10. Пример: убрать проверку обновлений в конкретном экране
sed_i 's|old_code|new_code|' "$TG_DIR/TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
has "$TG_DIR/.../LaunchActivity.java" 'new_code' || die "P10: патч не применился"
ok "P10 описание патча"
```

Хорошие кандидаты для следующей итерации (все — чистый клиентский код, без серверной части):

* скрытие «спонсорских» сообщений в каналах;
* скрытие историй / отключение от них в списке чатов;
* свои настройки-тумблеры в Settings (пункт меню → `SettingsActivity`);
* кастомные иконки/темы по умолчанию;
* «сохранить в галерею» без ограничений для self-destruct медиа (спорно с точки зрения приватности).

---

## Дисклеймер

* Это **неофициальная** сборка. Telegram® — торговая марка Telegram FZ-LLC; проект не связан с
  Telegram Messenger LLP.
* Не публикуйте сборку в Google Play и не выдавайте её за официальное приложение.
* Использование форка — на ваш риск: Telegram может ограничить аккаунт за клиенты с изменённым
  кодом (в теории), а обновления мода появляются только когда их соберёте вы.
* Пароли/аккаунты никуда не передаются: мод использует официальные API-креды из исходников.

---

## Файлы в репозитории

| Путь | Что это |
|---|---|
| `.github/workflows/build-tgmod.yml` | CI: клон исходников, патч, сборка, проверка, artifact + release |
| `mod/apply-mod.sh` | Патчер исходников Telegram (P0–P9), идемпотентный, с проверками |
| `fix-images-and-upgrade-lumi (1).zip` | Архив от другого проекта, к моду отношения не имеет |
