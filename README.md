# MslGram — мод Telegram для Android (как Nekogram), без своего `api_id` / `api_hash`

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
| `app_name` | `MslGram` | Название приложения: ярлык, имя уведомлений, UI |
| `app_package` | `com.kamisakyy.mgram` | `applicationId` — чтобы мод ставился **рядом** с официальным Telegram |
| `version_suffix` | `-mod` | Суффикс версии (будет `12.10.3-mod`) |
| `abis` | `arm64-v8a` | Какие ABI собирать. `arm64-v8a` — почти все современные телефоны и в 4 раза быстрее |
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

После прогона рядом с исходниками появляются:
* `MOD_INFO.txt` — что за мод, из какого коммита, какие патчи применились;
* `mod-changes.patch` — полный diff изменений (его можно посмотреть и загрузить как artifact).

---

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
APP_NAME=MslGram \
APP_PACKAGE=com.kamisakyy.mgram \
ABIS=arm64-v8a \
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
