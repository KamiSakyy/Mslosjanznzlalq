#!/usr/bin/env bash
# =============================================================================
#  MslGram Mod — apply-mod.sh
#
#  Превращает официальные исходники Telegram для Android (github.com/DrKLO/Telegram)
#  в модифицированный клиент — так же, как это делают Nekogram / NekoX / OwlGram:
#  свой брендинг + свой package id, при этом используются ОФИЦИАЛЬНЫЕ встроенные
#  APP_ID / APP_HASH из исходников. Свои api_id / api_hash НЕ нужны.
#
#  Скрипт идемпотентный: повторный запуск не портит файлы.
#  Все патчи проверяются, при критичной ошибке скрипт падает с кодом != 0.
#
#  Запуск:
#     TG_DIR=./telegram-src APP_NAME=MslGram APP_PACKAGE=com.example.mgram \
#         bash mod/apply-mod.sh
#
#  Переменные окружения (все опциональны):
#     TG_DIR               путь к склонированным исходникам DrKLO/Telegram
#     APP_NAME             название приложения (launcher label + UI)   [MslGram]
#     APP_PACKAGE          applicationId (свой, чтобы ставилось рядом с Telegram)
#     APP_VERSION_SUFFIX   суффикс версии                               [-mod]
#     ABIS                 какие ABI собирать (урезает время сборки)     [arm64-v8a]
#     BRAND_STRINGS        1 = имя мода во всём UI, 0 = только label     [1]
#     DISABLE_UPDATER      1 = не проверять обновления (нужно для мода)  [1]
#     DISABLE_BILLING      1 = выключить Google Play Billing            [0]
#     USE_CCACHE           1 = кэшировать нативную сборку через ccache  [1]
#     AUTODOWNLOAD_OFF     1 = автоскачивание медиа выключено по умолчанию [1]
#     NO_STICKERS          1 = стикеры/премиум-эмодзи не загружаются вообще  [1]
#     MAX_ECONOMY          1 = принудительный power-saver (анимации/автоплей off) [1]
#     RES_CONFIGS          какие локали оставить в APK ("ru,en" | "all")   [ru,en]
#     SLIM_HEAVY           заглушки тяжёлых Lottie-анимаций: 1 | all | 0   [1]
#     IOS_THEME            1 = iOS-тёмная тема (чёрный, без градиентов/стекла) [1]
#     FLAT_UI              1 = убрать тяжёлый узор чата (плоский фон)      [1]
#     AUTO_PROXY           1 = ссылка на прокси активирует его сразу       [1]
#     DROP_APPINDEXING     1 = вырезать Google App Indexing (меньше APK)   [1]
#     IOS_UI               1 = собственный iOS-интерфейс в коде (табы, шапка) [1]
#     GHOST_MODE           1 = режим «невидимка» (нет «прочитано»/«печатает»/онлайна) [1]
#     NO_RESTRICTIONS      1 = снять запреты защищённого контента       [1]
#     FIX_LOGIN            1 = вход через обычный SMS (без Google Play) [1]
#     SMART_PROXY          1 = прокси из буфера сам вкл/выкл по состоянию связи [1]
#     KEYSTORE_B64         base64 от .jks, если нужна своя подпись       [пусто]
#     KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD — для своей подписи   [пусто]
# =============================================================================

set -Eeuo pipefail

# ------------------------------ параметры -----------------------------------
TG_DIR=${TG_DIR:-telegram-src}
APP_NAME=${APP_NAME:-KamiGram}
APP_PACKAGE=${APP_PACKAGE:-com.kami.gram}
APP_VERSION_SUFFIX=${APP_VERSION_SUFFIX:--mod}
ABIS=${ABIS:-arm64-v8a}
BRAND_STRINGS=${BRAND_STRINGS:-1}
DISABLE_UPDATER=${DISABLE_UPDATER:-1}
DISABLE_BILLING=${DISABLE_BILLING:-0}
USE_CCACHE=${USE_CCACHE:-1}
# экономия трафика / размер
AUTODOWNLOAD_OFF=${AUTODOWNLOAD_OFF:-1}   # автоскачивание медиа выключено по умолчанию
NO_STICKERS=${NO_STICKERS:-1}             # стикеры и премиум-эмодзи не загружаются вообще
MAX_ECONOMY=${MAX_ECONOMY:-0}             # power-saver (0 = анимации и плавность остаются)
RES_CONFIGS=${RES_CONFIGS:-ru,en}         # какие языки оставить в APK (all = все)
SLIM_HEAVY=${SLIM_HEAVY:-1}               # заглушки тяжёлых Lottie-анимаций: 1 | all | 0
PATCH_GS=${PATCH_GS:-1}                   # правка google-services.json под свой applicationId
IOS_THEME=${IOS_THEME:-1}                 # iOS-тёмная тема KamiGram (чистый чёрный, без градиентов)
FLAT_UI=${FLAT_UI:-1}                     # плоский дизайн: убрать тяжёлый узор чата
AUTO_PROXY=${AUTO_PROXY:-1}               # ссылка на прокси активирует его сразу
DROP_APPINDEXING=${DROP_APPINDEXING:-1}   # вырезать Google App Indexing (меньше APK)
IOS_UI=${IOS_UI:-1}                       # НАСТОЯЩИЙ КОД: собственный iOS-интерфейс KamiGram
GHOST_MODE=${GHOST_MODE:-1}               # уникальная функция: режим «невидимка»
NO_RESTRICTIONS=${NO_RESTRICTIONS:-1}     # уникальная функция: снять запреты защищённого контента
FIX_LOGIN=${FIX_LOGIN:-1}                   # фикс входа: обычный SMS вместо Google Play Integrity
SMART_PROXY=${SMART_PROXY:-1}             # прокси из буфера сам включается, мёртвый — сам выключается
BUILD_LEAN=${BUILD_LEAN:-1}               # без debug-инфо в native, heap 5 ГБ (быстрее и легче)
KEYSTORE_B64=${KEYSTORE_B64:-}
KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD:-}
KEY_ALIAS=${KEY_ALIAS:-}
KEY_PASSWORD=${KEY_PASSWORD:-}

PATCHED_LIST=()
SKIPPED_LIST=()

# ------------------------------- helpers ------------------------------------
if [ -t 1 ]; then C_CYAN=$'\033[1;36m'; C_YEL=$'\033[1;33m'; C_RED=$'\033[1;31m'; C_GRN=$'\033[1;32m'; C_OFF=$'\033[0m'; else C_CYAN=; C_YEL=; C_RED=; C_GRN=; C_OFF=; fi
log()  { printf '%s[mod]%s %s\n' "$C_CYAN" "$C_OFF" "$*"; }
ok()   { printf '%s[ ok]%s %s\n' "$C_GRN"  "$C_OFF" "$*"; PATCHED_LIST+=("$*"); }
skip() { printf '%s[skip]%s %s\n' "$C_YEL"  "$C_OFF" "$*"; SKIPPED_LIST+=("$*"); }
warn() { printf '%s[warn]%s %s\n' "$C_YEL"  "$C_OFF" "$*" >&2; }
die()  { printf '%s[fail]%s %s\n' "$C_RED"  "$C_OFF" "$*" >&2; exit 1; }

# sed -i, портируемый между GNU и BSD
sed_i() { sed -i'' -e "$@" 2>/dev/null || sed -i "$@" ; }

# «есть ли строка в файле»
has() { grep -qF -- "$2" "$1" 2>/dev/null; }

# ---------------------------------------------------------------- проверки ---
[ -n "$TG_DIR" ] || die "TG_DIR пуст"
[ -d "$TG_DIR" ] || die "TG_DIR='$TG_DIR' не найден. Сначала склонируйте DrKLO/Telegram."
TG_DIR=$(cd "$TG_DIR" && pwd)

[ -f "$TG_DIR/gradle.properties" ] || die "$TG_DIR не похож на исходники Telegram (нет gradle.properties)"
[ -f "$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java" ] || die "$TG_DIR не похож на исходники Telegram (нет BuildVars.java)"

[[ "$APP_PACKAGE" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]] \
    || die "APP_PACKAGE='$APP_PACKAGE' не валидный applicationId (пример: com.example.mgram)"

# имя без символов, ломающих sed/xml/манифест
[ -n "$APP_NAME" ] || die "APP_NAME пуст"
[ "${#APP_NAME}" -le 40 ] || die "APP_NAME='$APP_NAME' длиннее 40 символов"
for bad in '&' '|' '/' '\\' '<' '>' '"' "'"; do
    case "$APP_NAME" in
        *"$bad"*) die "APP_NAME='$APP_NAME' содержит недопустимый символ: $bad" ;;
    esac
done

# список ABI (a,b  ->  "a", "b")
IFS=',' read -r -a ABI_ARR <<< "$ABIS"
for abi in "${ABI_ARR[@]}"; do
    case "$abi" in
        arm64-v8a|armeabi-v7a|x86|x86_64) ;;
        *) die "ABIS: неизвестный ABI '$abi' (допустимо: arm64-v8a, armeabi-v7a, x86, x86_64)" ;;
    esac
done
ABI_GRADLE=$(printf '"%s", ' "${ABI_ARR[@]}"); ABI_GRADLE=${ABI_GRADLE%, }
ABI_TAG=${ABIS//,/_}; ABI_TAG=${ABI_TAG//-/_}

UPSTREAM_COMMIT=$(git -C "$TG_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
UPSTREAM_DATE=$(git -C "$TG_DIR" log -1 --format=%cs 2>/dev/null || echo "unknown")

log "Исходники Telegram : $TG_DIR (commit $UPSTREAM_COMMIT от $UPSTREAM_DATE)"
log "Мод                : $APP_NAME   package=$APP_PACKAGE   ABI=$ABIS"
echo

# =============================================================================
# P0. Креденшелы Telegram: остаются официальные, вшитые в исходники.
#     Именно так сделано в Nekogram/NekoX (APP_ID = 4, официальный APP_HASH).
#     При желании можно подменить на свои через BuildVars.java / local.properties.
# =============================================================================
BUILDVARS="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
CUR_ID=$(grep -oE 'APP_ID *= *[0-9]+' "$BUILDVARS" | head -1 | grep -oE '[0-9]+' || true)
CUR_HASH=$(grep -oE 'APP_HASH *= *"[a-f0-9]+"' "$BUILDVARS" | head -1 | grep -oE '[a-f0-9]{8,}' || true)
[ -n "$CUR_ID" ] && [ -n "$CUR_HASH" ] \
    || die "не нашёл APP_ID/APP_HASH в BuildVars.java — изменилась структура исходников"
ok "креденшелы Telegram встроены в исходники (APP_ID=$CUR_ID, APP_HASH=${CUR_HASH:0:8}…): свои api_id/api_hash НЕ нужны"

# =============================================================================
# P1. gradle.properties: свой package id + суффикс версии
# =============================================================================
GP="$TG_DIR/gradle.properties"
RAW_VERSION=$(grep -oP '^APP_VERSION_NAME=\K.*' "$GP" || die "APP_VERSION_NAME не найден в gradle.properties")
BASE_VERSION=${RAW_VERSION%"$APP_VERSION_SUFFIX"}   # уже пропатченный вариант откатываем к базовой версии
NEW_VERSION="$BASE_VERSION$APP_VERSION_SUFFIX"

sed_i "s|^APP_PACKAGE=.*$|APP_PACKAGE=$APP_PACKAGE|" "$GP"
sed_i "s|^APP_VERSION_NAME=.*$|APP_VERSION_NAME=$NEW_VERSION|" "$GP"
sed_i "s|^IS_PRIVATE=.*$|IS_PRIVATE=false|" "$GP"

has "$GP" "APP_PACKAGE=$APP_PACKAGE"     || die "P1: не удалось прописать APP_PACKAGE"
has "$GP" "APP_VERSION_NAME=$NEW_VERSION" || die "P1: не удалось прописать APP_VERSION_NAME"
ok "P1 package id + версия: $APP_PACKAGE / $NEW_VERSION (база $BASE_VERSION, code $(grep -oP '^APP_VERSION_CODE=\K.*' "$GP"))"

# =============================================================================
# P2. AppName в ресурсах (launcher label + системные упоминания)
# =============================================================================
mapfile -t STRINGS_FILES < <(find "$TG_DIR/TMessagesProj/src/main/res" -path '*/values*/*' -name 'strings.xml' | sort)
[ "${#STRINGS_FILES[@]}" -gt 0 ] || die "P2: не нашёл resources/strings.xml"
count=0
for f in "${STRINGS_FILES[@]}"; do
    if grep -q 'name="AppName"' "$f"; then
        sed_i "s|<string name=\"AppName\">[^<]*</string>|<string name=\"AppName\">$APP_NAME</string>|g" "$f"
        count=$((count+1))
    fi
done
[ "$count" -gt 0 ] || die "P2: ресурс AppName не найден"
DEF_STRINGS="$TG_DIR/TMessagesProj/src/main/res/values/strings.xml"
has "$DEF_STRINGS" "<string name=\"AppName\">$APP_NAME</string>" || die "P2: AppName не заменился в $DEF_STRINGS"
ok "P2 имя приложения в ресурсах: '$APP_NAME' (файлов: $count)"

# =============================================================================
# P3. Имя мода во всём UI (иначе серверные «облачные» строки вернут 'Telegram')
# =============================================================================
LC="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java"
if [ "$BRAND_STRINGS" = "1" ]; then
    MARKER="/* MSLGRAM_BRAND */"
    if has "$LC" "$MARKER"; then
        sed_i "s|if (res == R.string.AppName) return \"[^\"]*\"; $MARKER|if (res == R.string.AppName) return \"$APP_NAME\"; $MARKER|" "$LC"
        ok "P3 брендинг UI обновлён на '$APP_NAME'"
    else
        ANCHOR='        String value = BuildVars.USE_CLOUD_STRINGS ? localizationExternal.getByResNameOrResId(ApplicationLoader.applicationContext, key, res) : null;'
        has "$LC" "$ANCHOR" || die "P3: не нашёл точку внедрения в LocaleController.java (изменился upstream)"
        python3 - "$LC" "$APP_NAME" "$MARKER" <<'PY'
import sys, io
path, name, marker = sys.argv[1], sys.argv[2], sys.argv[3]
anchor = '        String value = BuildVars.USE_CLOUD_STRINGS ? localizationExternal.getByResNameOrResId(ApplicationLoader.applicationContext, key, res) : null;'
inject = (
    '        if (res == R.string.AppName) return "%s"; %s\n' % (name, marker)
)
src = io.open(path, encoding='utf-8').read()
if marker not in src:
    idx = src.index(anchor)
    src = src[:idx] + inject + src[idx:]
    io.open(path, 'w', encoding='utf-8').write(src)
PY
        has "$LC" "$MARKER" || die "P3: не удалось внедрить брендинг"
        ok "P3 брендинг UI внедрён ('$APP_NAME' возвращается для R.string.AppName)"
    fi
else
    skip "P3 брендинг UI отключён (BRAND_STRINGS=0)"
fi

# =============================================================================
# P4. google-services.json: плагин Google Services применяется и к библиотечному
#     модулю TMessagesProj, где package_name обязан совпадать с его namespace
#     (org.telegram.messenger) — этот файл НЕ трогаем. Переписываем только файлы
#     тех модулей-приложений, которые собираем: их applicationId = $APP_PACKAGE
#     (со суффиксами .beta / .web для соответствующих build types).
# =============================================================================
if [ "$PATCH_GS" = "1" ]; then
    patch_gs_json() { # $1 = файл, $2 = базовый applicationId
        [ -f "$1" ] || return 1
        python3 - "$1" "$2" <<'PY' || return 1
import io, json, sys
path, pkg = sys.argv[1], sys.argv[2]
data = json.load(io.open(path, encoding='utf-8'))
count = 0
for client in data.get('client', []):
    info = client.get('client_info', {}).get('android_client_info', {})
    old = info.get('package_name', '')
    suffix = ''
    if old.endswith('.beta'):
        suffix = '.beta'
    elif old.endswith('.web'):
        suffix = '.web'
    info['package_name'] = pkg + suffix
    count += 1
io.open(path, 'w', encoding='utf-8').write(json.dumps(data, indent=2, ensure_ascii=False) + '\n')
print(count)
PY
    }

    gs_main=$(patch_gs_json "$TG_DIR/TMessagesProj_App/google-services.json" "$APP_PACKAGE" || echo 0)

    # standalone-модуль: applicationId всегда с суффиксом .web → оставляем один клиент с точным id
    GS_STANDALONE="$TG_DIR/TMessagesProj_AppStandalone/google-services.json"
    if [ -f "$GS_STANDALONE" ]; then
        python3 - "$GS_STANDALONE" "$APP_PACKAGE.web" <<'PY' || warn "P4: standalone google-services.json не поправлен"
import io, json, sys
path, pkg = sys.argv[1], sys.argv[2]
data = json.load(io.open(path, encoding='utf-8'))
clients = data.get('client', [])
if clients:
    clients[0]['client_info']['android_client_info']['package_name'] = pkg
    data['client'] = clients[:1]
io.open(path, 'w', encoding='utf-8').write(json.dumps(data, indent=2, ensure_ascii=False) + '\n')
PY
    fi

    [ "${gs_main:-0}" -gt 0 ] || die "P4: не удалось поправить TMessagesProj_App/google-services.json"
    has "$TG_DIR/TMessagesProj_App/google-services.json" "\"package_name\": \"$APP_PACKAGE\"" \
        || die "P4: в TMessagesProj_App/google-services.json нет записи для $APP_PACKAGE"

    # библиотечный модуль должен остаться с upstream-пакетом, иначе
    # :TMessagesProj:processReleaseGoogleServices падает: "No matching client found"
    has "$TG_DIR/TMessagesProj/google-services.json" '"package_name": "org.telegram.messenger"' \
        || die "P4: библиотечный google-services.json изменён — сборка упадёт"
    if grep -q "$APP_PACKAGE" "$TG_DIR/TMessagesProj/google-services.json"; then
        die "P4: в библиотечном google-services.json не должно быть $APP_PACKAGE"
    fi

    ok "P4 google-services.json: app-модули → $APP_PACKAGE(.beta/.web), библиотека оставлена как org.telegram.messenger"
else
    skip "P4 google-services.json не тронут (PATCH_GS=0)"
fi

# =============================================================================
# P5. ABI: собираем только нужные архитектуры (урезает сборку в разы)
# =============================================================================
abi_count=0
while IFS= read -r gf; do
    sed_i "s|abiFilters \"[^\"]*\"\(, \"[^\"]*\"\)*|abiFilters $ABI_GRADLE|g" "$gf"
    grep -qF "abiFilters $ABI_GRADLE" "$gf" && abi_count=$((abi_count+1))
done < <(find "$TG_DIR" -maxdepth 2 -name build.gradle | sort)
[ "$abi_count" -gt 0 ] || die "P5: не нашёл abiFilters ни в одном build.gradle"
ok "P5 ABI ограничены: $ABI_GRADLE (файлов: $abi_count)"

# =============================================================================
# P6. Апдейтер Телеграма: мод не должен предлагать установить официальный APK
# =============================================================================
if [ "$DISABLE_UPDATER" = "1" ]; then
    sed_i 's|public static boolean CHECK_UPDATES = true;|public static boolean CHECK_UPDATES = false;|' "$BUILDVARS"
    has "$BUILDVARS" "CHECK_UPDATES = false" || die "P6: не удалось выключить CHECK_UPDATES"
    ok "P6 проверка обновлений выключена (не тянет официальный Telegram)"
else
    skip "P6 апдейтер оставлен включённым (DISABLE_UPDATER=0)"
fi

# =============================================================================
# P7. (опция) Google Play Billing — в сборке вне Play он всё равно недоступен
# =============================================================================
if [ "$DISABLE_BILLING" = "1" ]; then
    sed_i 's|public static boolean IS_BILLING_UNAVAILABLE = false;|public static boolean IS_BILLING_UNAVAILABLE = true;|' "$BUILDVARS"
    has "$BUILDVARS" "IS_BILLING_UNAVAILABLE = true" || die "P7: не удалось выключить billing"
    ok "P7 Google Play Billing отключён"
else
    skip "P7 billing оставлен как в upstream (DISABLE_BILLING=0)"
fi

# =============================================================================
# P8. ccache для нативной части (сильно ускоряет повторные сборки)
# =============================================================================
if [ "$USE_CCACHE" = "1" ]; then
    cc=0
    while IFS= read -r gf; do
        if grep -q -- "-DANDROID_PLATFORM=android-21" "$gf" && ! grep -q "CMAKE_C_COMPILER_LAUNCHER=ccache" "$gf"; then
            sed_i "s|-DANDROID_PLATFORM=android-21'|-DANDROID_PLATFORM=android-21', '-DCMAKE_C_COMPILER_LAUNCHER=ccache', '-DCMAKE_CXX_COMPILER_LAUNCHER=ccache'|" "$gf"
            grep -q "CMAKE_C_COMPILER_LAUNCHER=ccache" "$gf" && cc=$((cc+1)) || warn "P8: не удалось добавить ccache в $gf"
        fi
    done < <(find "$TG_DIR" -maxdepth 2 -name build.gradle | sort)
    [ "$cc" -gt 0 ] && ok "P8 ccache подключён к native-сборке (файлов: $cc)" || skip "P8 ccache уже подключён или неприменим"
else
    skip "P8 ccache отключён (USE_CCACHE=0)"
fi

# =============================================================================
# P9. (опция) своя подпись вместо публичного upstream-ключа
# =============================================================================
if [ -n "$KEYSTORE_B64" ]; then
    KS="$TG_DIR/TMessagesProj/config/release.keystore"
    printf '%s' "$KEYSTORE_B64" | base64 --decode > "$KS" || die "P9: KEYSTORE_B64 не декодируется"
    [ -s "$KS" ] || die "P9: keystore пустой"
    [ -n "$KEYSTORE_PASSWORD" ] && [ -n "$KEY_ALIAS" ] && [ -n "$KEY_PASSWORD" ] \
        || die "P9: нужны KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD"
    sed_i "s|^RELEASE_STORE_PASSWORD=.*$|RELEASE_STORE_PASSWORD=$KEYSTORE_PASSWORD|" "$GP"
    sed_i "s|^RELEASE_KEY_ALIAS=.*$|RELEASE_KEY_ALIAS=$KEY_ALIAS|" "$GP"
    sed_i "s|^RELEASE_KEY_PASSWORD=.*$|RELEASE_KEY_PASSWORD=$KEY_PASSWORD|" "$GP"
    ok "P9 подпись: используется свой keystore"
else
    skip "P9 подпись: публичный upstream-ключ (android / androidkey) — менять не обязательно"
fi

# =============================================================================
# P10. ЭКОНОМИЯ ТРАФИКА: автоскачивание медиа выключено по умолчанию
#      (фото/видео/документы не докачиваются сами ни в Wi-Fi, ни в мобильной сети)
# =============================================================================
DC="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/DownloadController.java"
if [ "$AUTODOWNLOAD_OFF" = "1" ]; then
    # строки пресетов: mask0_mask1_mask2_mask3_photo_video_doc_audio_preloadVideo_preloadMusic_enabled_lowCallData_bitrate_preloadStories
    sed_i 's#String defaultLow = "[^"]*";#String defaultLow = "0_0_0_0_1048576_512000_512000_524288_0_0_0_1_50_0";#' "$DC"
    sed_i 's#String defaultMedium = "[^"]*";#String defaultMedium = "0_0_0_0_1048576_10485760_1048576_524288_0_0_0_1_100_0";#' "$DC"
    sed_i 's#String defaultHigh = "[^"]*";#String defaultHigh = "0_0_0_0_1048576_15728640_3145728_524288_0_0_0_1_100_0";#' "$DC"
    # старый формат настроек (обновление поверх существующей установки)
    sed_i 's#getInt(key, AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT)#getInt(key, 0)#' "$DC"
    sed_i 's#getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT)#getInt("wifiDownloadMask" + (a == 0 ? "" : a), 0)#' "$DC"
    sed_i 's#getInt("roamingDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO)#getInt("roamingDownloadMask" + (a == 0 ? "" : a), 0)#' "$DC"
    sed_i 's#getBoolean("globalAutodownloadEnabled", true)#getBoolean("globalAutodownloadEnabled", false)#' "$DC"

    has "$DC" 'defaultMedium = "0_0_0_0_' || die "P10: не удалось обнулить defaultMedium"
    has "$DC" 'globalAutodownloadEnabled", false' || die "P10: не удалось выключить globalAutodownloadEnabled"
    grep -q 'AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO' "$DC" && die "P10: остались маски автоскачивания по умолчанию"
    ok "P10 автоскачивание медиа выключено по умолчанию (моб./Wi-Fi/роуминг), preload видео/музыки/историй — off"
else
    skip "P10 автоскачивание оставлено как в upstream (AUTODOWNLOAD_OFF=0)"
fi

# =============================================================================
# P11. СТИКЕРЫ И ПРЕМИУМ-ЭМОДЗИ НЕ ЗАГРУЖАЮТСЯ ВООБЩЕ
#      Блокируем все точки загрузки наборов: обычные, маски, премиум-эмодзи,
#      featured, подарочные/TON-стикеры, generic-анимации, иконки топиков.
# =============================================================================
MDC="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/MediaDataController.java"
if [ "$NO_STICKERS" = "1" ]; then
    python3 - "$MDC" <<'PY' || die "P11: не удалось внедрить блокировку стикеров"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
marker = '/* KAMIGRAM_NO_STICKERS */'
targets = [
    ('public void loadStickers(int type, boolean cache, boolean force, boolean scheduleIfLoading, Utilities.Callback<ArrayList<TLRPC.TL_messages_stickerSet>> onFinish) {',
     '        if (true) { ' + marker + ' if (onFinish != null) onFinish.run(null); return; }'),
    ('public void loadFeaturedStickers(boolean emoji, boolean cache) {',
     '        if (true) { ' + marker + ' return; }'),
    ('public void loadStickersByEmojiOrName(String name, boolean isEmoji, boolean cache) {',
     '        if (true) { ' + marker + ' return; }'),
    ('public boolean areStickersLoaded(int type) {',
     '        if (true) return true; ' + marker),
]
if marker not in src:
    for sig, inject in targets:
        if sig not in src:
            sys.stderr.write('P11: не найден метод: %s\n' % sig)
            sys.exit(1)
        src = src.replace(sig, sig + '\n' + inject, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
PY
    [ "$(grep -c 'KAMIGRAM_NO_STICKERS' "$MDC")" = "4" ] || die "P11: ожидалось 4 точки блокировки"
    ok "P11 загрузка наборов стикеров/масок/премиум-эмодзи/подарков заблокирована (0 байт трафика)"
else
    skip "P11 стикеры оставлены как в upstream (NO_STICKERS=0)"
fi

# =============================================================================
# P12. FLAT & SMOOTH: плоский iOS-вид без «стекла» и размытия, но БЕЗ убийства
#      анимаций и плавности. Гасим только FLAG_LIQUID_GLASS и FLAG_CHAT_BLUR,
#      всё остальное (анимации, частицы) остаётся как в оригинале.
#      MAX_ECONOMY=1 дополнительно включает старый жёсткий power-saver.
# =============================================================================
LM="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/LiteMode.java"
python3 - "$LM" "$MAX_ECONOMY" <<'PY' || die "P12: не удалось настроить LiteMode"
import io, sys
path, max_economy = sys.argv[1], sys.argv[2]
src = io.open(path, encoding='utf-8').read()
marker = '/* KAMIGRAM_FLAT_SMOOTH'
sig = 'public static int getValue(boolean ignorePowerSaving) {'
if marker not in src:
    if sig not in src:
        sys.stderr.write('P12: не найден LiteMode.getValue\n')
        sys.exit(1)
    if max_economy == '1':
        src = src.replace(sig, sig + '\n        if (true) return PRESET_POWER_SAVER; ' + marker + ' hard power saver */', 1)
    else:
        old_tail = '        return value;\n    }\n\n    private static int lastBatteryLevelCached = -1;'
        if old_tail not in src:
            sys.stderr.write('P12: не найдено возвращение value в getValue\n')
            sys.exit(1)
        new_tail = ('        ' + marker + ' iOS flat look + full UI smoothness */\n'
                    '        // UI animations stay on, but the look is flat and traffic-free:\n'
                    '        // liquid glass, blur, custom wallpaper and autoplay are off\n'
                    '        return (value | PRESET_HIGH)\n'
                    '            & ~FLAG_LIQUID_GLASS & ~FLAG_CHAT_BLUR & ~FLAG_CHAT_BACKGROUND\n'
                    '            & ~FLAG_AUTOPLAY_VIDEOS & ~FLAG_AUTOPLAY_GIFS;\n'
                    '    }\n\n    private static int lastBatteryLevelCached = -1;')
        src = src.replace(old_tail, new_tail, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
PY
has "$LM" "KAMIGRAM_FLAT_SMOOTH" || die "P12: маркер не внедрён"
if [ "$MAX_ECONOMY" = "1" ]; then
    ok "P12 power-saver форсирован: анимации/автоплей/частицы/blur — off"
else
    ok "P12 плоский и плавный режим: «стекло» и размытие выключены, анимации и прокрутка работают как в оригинале Tele"
fi

# =============================================================================
# P13. РАЗМЕР APK: оставляем только нужные языки (остальные приходят с сервера)
# =============================================================================
if [ -n "$RES_CONFIGS" ] && [ "$RES_CONFIGS" != "all" ]; then
    IFS=',' read -r -a RC_ARR <<< "$RES_CONFIGS"
    RC_GROOVY=$(printf '"%s", ' "${RC_ARR[@]}"); RC_GROOVY=${RC_GROOVY%, }
    rc_files=0
    while IFS= read -r gf; do
        sed_i "s#localeFilters += \[\"zz\"\]#localeFilters += [\"zz\", $RC_GROOVY]#" "$gf"
        has "$gf" "$RC_GROOVY" && rc_files=$((rc_files+1))
    done < <(grep -rl 'localeFilters += \["zz"\]' "$TG_DIR" --include=build.gradle || true)
    [ "$rc_files" -gt 0 ] || die "P13: не нашёл localeFilters ни в одном build.gradle"
    ok "P13 в APK остаются локали: $RES_CONFIGS (+zz) — файлов: $rc_files"
else
    skip "P13 все локали оставлены (RES_CONFIGS=$RES_CONFIGS)"
fi

# =============================================================================
# P14. РАЗМЕР APK: тяжёлые Lottie-анимации заменяются мгновенной заглушкой.
#      R.raw.* ссылки целы (файлы существуют), «анимация» проигрывается за 1 кадр.
# =============================================================================
RAW="$TG_DIR/TMessagesProj/src/main/res/raw"
if [ "$SLIM_HEAVY" != "0" ] && [ -d "$RAW" ]; then
    before=$(du -sk "$RAW" | cut -f1)
    stubbed=0
    while IFS= read -r f; do
        size=$(wc -c < "$f")
        # заглушаем только настоящие Lottie (в шапке есть маркер "v"), чтобы не сломать
        # mapstyle_night.json (стиль Google Maps) и прочие не-анимационные JSON
        head20=$(head -c 20 "$f" | tr -d '\n')
        case "$head20" in
            *'"v"'*) ;;
            *) continue ;;
        esac
        if [ "$SLIM_HEAVY" = "all" ] || [ "$size" -gt 102400 ]; then
            printf '%s' '{"v":"5.7.4","fr":1,"ip":0,"op":1,"w":1,"h":1,"assets":[],"layers":[]}' > "$f"
            stubbed=$((stubbed+1))
        fi
    done < <(find "$RAW" -name '*.json' | sort)
    [ "$stubbed" -gt 0 ] || die "P14: не нашёл Lottie-анимаций в res/raw"
    after=$(du -sk "$RAW" | cut -f1)
    saved=$(( (before - after) / 1024 ))
    ok "P14 обнулено Lottie-анимаций: $stubbed (res/raw: -${saved} МБ исходников; режим SLIM_HEAVY=$SLIM_HEAVY)"
else
    skip "P14 Lottie-анимации не тронуты (SLIM_HEAVY=$SLIM_HEAVY)"
fi

# =============================================================================
# P15. СКОРОСТЬ СБОРКИ И РАЗМЕР APK: без debug-инфо в нативной части.
#      Убираем -g из C/C++ флагов и снижаем уровень symbol-level, чтобы объектные
#      файлы и .so не раздувались на десятки гигабайт (иначе CI падает по диску).
# =============================================================================
if [ "$BUILD_LEAN" = "1" ]; then
    JNICMAKE="$TG_DIR/TMessagesProj/jni/CMakeLists.txt"
    if [ -f "$JNICMAKE" ]; then
        sed_i 's#set(CMAKE_CXX_FLAGS "-std=c++14 -DANDROID -g")#set(CMAKE_CXX_FLAGS "-std=c++14 -DANDROID")#' "$JNICMAKE"
        sed_i 's#set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -D_LARGEFILE_SOURCE=1 -g -Wno-error=implicit-function-declaration")#set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -D_LARGEFILE_SOURCE=1 -Wno-error=implicit-function-declaration")#' "$JNICMAKE"
        has "$JNICMAKE" 'set(CMAKE_CXX_FLAGS "-std=c++14 -DANDROID")' || die "P15: не удалось убрать -g из CMAKE_CXX_FLAGS"
        ok "P15 нативная сборка без -g (объекты и .so легче, линковка быстрее)"
    else
        warn "P15: не нашёл jni/CMakeLists.txt — пропускаю"
    fi

    dsl=0
    while IFS= read -r gf; do
        sed_i "s#ndk.debugSymbolLevel = 'FULL'#ndk.debugSymbolLevel = 'SYMBOL_TABLE'#g" "$gf"
        grep -q "debugSymbolLevel = 'SYMBOL_TABLE'" "$gf" && dsl=$((dsl+1))
    done < <(find "$TG_DIR" -maxdepth 2 -name build.gradle | sort)
    ok "P15 debugSymbolLevel = SYMBOL_TABLE в $dsl модулях (не пишем гигабайты символов)"

    # Gradle: 8 ГБ heap может не хватить вместе с нативной сборкой на 16 ГБ раннере
    sed_i 's#^org.gradle.jvmargs=.*$#org.gradle.jvmargs=-Xmx5g -XX:MaxMetaspaceSize=1g#' "$GP"
    has "$GP" "org.gradle.jvmargs=-Xmx5g" || die "P15: не удалось ограничить heap Gradle"
    ok "P15 Gradle heap ограничен 5 ГБ (защита от OOM на CI)"
else
    skip "P15 build-lean отключён (BUILD_LEAN=0)"
fi

# =============================================================================
# P16. iOS-СТИЛЬ: тёмная тема KamiGram (чистый чёрный, без градиентов и стекла).
#      Темы Telegram лежат в assets/*.attheme обычным текстом (ключ=значение),
#      поэтому палитра меняется без правки Java: переписываем тему дня ("Blue"
#      → bluebubbles.attheme), тему ночи ("Dark Blue" → darkblue.attheme) и night,
#      чтобы приложение выглядело одинаково тёмно-iOS в любом режиме.
# =============================================================================
if [ "$IOS_THEME" = "1" ]; then
    python3 - "$TG_DIR" <<'PY' || die "P16: не удалось применить iOS-палитру"
import io, os, sys

root = sys.argv[1]
assets = os.path.join(root, 'TMessagesProj/src/main/assets')
base_file = os.path.join(assets, 'darkblue.attheme')

def to_int(hexstr):
    s = hexstr.lstrip('#')
    if len(s) == 8:
        v = int(s, 16)
    else:
        v = 0xff000000 | int(s, 16)
    return v - (1 << 32) if v >= (1 << 31) else v

P = {
    # --- фоны и поверхности ---
    'chat_wallpaper': '#000000',
    'windowBackgroundGray': '#000000',
    'windowBackgroundWhite': '#1C1C1E',
    'actionBarDefault': '#1C1C1E',
    'dialogBackground': '#1C1C1E',
    'dialogBackgroundGray': '#2C2C2E',
    'graySection': '#1C1C1E',
    'key_graySectionText': '#8E8E93',
    'divider': '#38383A',
    'dialogGrayLine': '#38383A',
    'dialogShadowLine': '#00000000',
    'dialogLineProgressBackground': '#3A3A3C',
    'chat_topPanelBackground': '#1C1C1E',
    'chat_topPanelLine': '#38383A',
    'chat_messagePanelBackground': '#1C1C1E',
    'chat_emojiPanelBackground': '#1C1C1E',
    'chat_emojiPanelShadowLine': '#2C2C2E',
    'chat_stickersHintPanel': '#1C1C1E',
    'chats_menuBackground': '#1C1C1E',
    'chats_menuTopBackgroundCats': '#1C1C1E',
    'chats_menuTopShadow': '#00000000',
    'chats_archivePinBackground': '#1C1C1E',
    'actionBarDefaultSubmenuBackground': '#1C1C1E',
    'actionBarDefaultSubmenuSeparator': '#38383A',
    'undo_background': '#2C2C2E',
    'inappPlayerBackground': '#1C1C1E',
    'player_background': '#1C1C1E',
    'sharedMedia_linkPlaceholder': '#1C1C1E',
    # --- тексты ---
    'actionBarDefaultTitle': '#FFFFFF',
    'actionBarDefaultIcon': '#FFFFFF',
    'actionBarDefaultSubtitle': '#8E8E93',
    'actionBarDefaultSearchPlaceholder': '#8E8E93',
    'actionBarTabActiveText': '#FFFFFF',
    'actionBarTabUnactiveText': '#8E8E93',
    'chats_name': '#FFFFFF',
    'chats_message': '#8E8E93',
    'chats_date': '#8E8E93',
    'chats_nameMessage': '#8E8E93',
    'windowBackgroundWhiteBlackText': '#FFFFFF',
    'windowBackgroundWhiteGrayText': '#8E8E93',
    'windowBackgroundWhiteGrayText2': '#8E8E93',
    'windowBackgroundWhiteGrayText3': '#8E8E93',
    'windowBackgroundWhiteGrayText4': '#8E8E93',
    'windowBackgroundWhiteGrayText5': '#8E8E93',
    'windowBackgroundWhiteGrayText6': '#8E8E93',
    'windowBackgroundWhiteGrayText8': '#8E8E93',
    'windowBackgroundWhiteHintText': '#8E8E93',
    'windowBackgroundWhiteGrayIcon': '#8E8E93',
    'windowBackgroundWhiteBlueHeader': '#8E8E93',
    'windowBackgroundWhiteValueText': '#0A84FF',
    'windowBackgroundWhiteLinkText': '#0A84FF',
    'windowBackgroundWhiteLinkSelection': '#330A84FF',
    'dialogTextBlack': '#FFFFFF',
    'dialogTextGray': '#8E8E93',
    'dialogTextGray2': '#8E8E93',
    'dialogTextGray3': '#8E8E93',
    'dialogTextGray4': '#8E8E93',
    'dialogTextHint': '#8E8E93',
    'dialogTextLink': '#0A84FF',
    'dialogTextBlue': '#0A84FF',
    'dialogTextBlue2': '#0A84FF',
    'dialogTextBlue4': '#0A84FF',
    'dialogButton': '#0A84FF',
    'dialogButtonSelector': '#330A84FF',
    'dialogIcon': '#8E8E93',
    'profile_title': '#8E8E93',
    'profile_status': '#8E8E93',
    'profile_actionIcon': '#0A84FF',
    'profile_creatorIcon': '#0A84FF',
    'profile_actionBackground': '#00000000',
    'profile_actionPressedBackground': '#00000000',
    'avatar_subtitleInProfileBlue': '#8E8E93',
    'emptyListPlaceholder': '#8E8E93',
    'fastScrollInactive': '#3A3A3C',
    'contextProgressInner1': '#3A3A3C',
    'contextProgressOuter1': '#0A84FF',
    'text_RedBold': '#FF453A',
    'text_RedRegular': '#FF453A',
    'windowBackgroundWhiteGreenText': '#30D158',
    'windowBackgroundWhiteGreenText2': '#30D158',
    'windowBackgroundWhiteBlueText': '#0A84FF',
    'windowBackgroundWhiteBlueText2': '#0A84FF',
    'windowBackgroundWhiteBlueText3': '#0A84FF',
    'windowBackgroundWhiteBlueText4': '#0A84FF',
    'windowBackgroundWhiteBlueText5': '#0A84FF',
    'windowBackgroundWhiteBlueText7': '#0A84FF',
    'calls_callReceivedGreenIcon': '#30D158',
    # --- чат: пузыри, время, статусы ---
    'chat_inBubble': '#262628',
    'chat_outBubble': '#2B5278',
    'chat_inBubbleSelected': '#2C2C2E',
    'chat_outBubbleSelected': '#33608A',
    'chat_inBubbleShadow': '#00000000',
    'chat_outBubbleShadow': '#00000000',
    'chat_outBubbleGradientSelectedOverlay': '#33FFFFFF',
    'chat_messageTextIn': '#FFFFFF',
    'chat_messageTextOut': '#FFFFFF',
    'chat_messageLinkIn': '#0A84FF',
    'chat_messageLinkOut': '#A8D4FF',
    'chat_serviceBackground': '#CC1C1C1E',
    'chat_serviceBackgroundSelected': '#CC2C2C2E',
    'chat_status': '#8E8E93',
    'chat_inTimeText': '#8E8E93',
    'chat_outTimeText': '#A8C7E8',
    'chat_outTimeSelectedText': '#FFFFFF',
    'chat_inTimeSelectedText': '#8E8E93',
    'chat_inSentClock': '#8E8E93',
    'chat_outSentClock': '#A8C7E8',
    'chat_outSentClockSelected': '#FFFFFF',
    'chat_outSentCheck': '#A8C7E8',
    'chat_outSentCheckSelected': '#FFFFFF',
    'chats_sentCheck': '#0A84FF',
    'chats_sentClock': '#8E8E93',
    'chat_fieldOverlayText': '#0A84FF',
    'chat_messagePanelText': '#FFFFFF',
    'chat_messagePanelHint': '#8E8E93',
    'chat_messagePanelIcons': '#8E8E93',
    'chat_messagePanelSend': '#0A84FF',
    'chat_recordTime': '#FF453A',
    'chat_recordedVoiceDot': '#FF453A',
    'chat_recordVoiceCancel': '#FF453A',
    'chat_recordVoiceCancelSelected': '#FF453A',
    'chat_goDownButton': '#2C2C2E',
    'chat_goDownButtonCounter': '#0A84FF',
    'chat_selectedBackground': '#14FFFFFF',
    'chat_attachActiveTab': '#0A84FF',
    'chat_attachUnactiveTab': '#8E8E93',
    'chat_unreadMessagesStartBackground': '#2C2C2E',
    'chat_unreadMessagesStartText': '#FFFFFF',
    'chat_unreadMessagesStartArrowIcon': '#8E8E93',
    'chat_topPanelTitle': '#FFFFFF',
    'chat_topPanelMessage': '#8E8E93',
    'chat_topPanelClose': '#8E8E93',
    'chat_replyPanelLine': '#38383A',
    'chat_replyPanelIcons': '#8E8E93',
    'chat_replyPanelName': '#0A84FF',
    'chat_addContact': '#0A84FF',
    'chat_inSiteNameText': '#0A84FF',
    'chat_outSiteNameText': '#A8D4FF',
    'chat_inForwardedNameText': '#0A84FF',
    'chat_outForwardedNameText': '#A8D4FF',
    'chat_inReplyNameText': '#0A84FF',
    'chat_outReplyNameText': '#A8D4FF',
    'chat_inVenueInfoText': '#8E8E93',
    'chat_outVenueInfoText': '#A8C7E8',
    'chat_inFileInfoText': '#8E8E93',
    'chat_outFileInfoText': '#A8C7E8',
    'chat_inContactNameText': '#0A84FF',
    'chat_outContactNameText': '#A8D4FF',
    'chat_inAudioPerfomerText': '#8E8E93',
    'chat_outAudioPerfomerText': '#A8C7E8',
    'chat_inAudioTitleText': '#0A84FF',
    'chat_outAudioTitleText': '#A8D4FF',
    'chat_inMenu': '#8E8E93',
    'chat_inMenuSelected': '#FFFFFF',
    'chat_outMenu': '#A8C7E8',
    'chat_outMenuSelected': '#FFFFFF',
    'chat_inViews': '#8E8E93',
    'chat_outViews': '#A8C7E8',
    'chat_inViewsSelected': '#FFFFFF',
    'chat_outViewsSelected': '#FFFFFF',
    'chat_mediaMenu': '#8E8E93',
    'chat_emojiPanelIcon': '#8E8E93',
    'chat_emojiPanelIconSelected': '#0A84FF',
    'chat_emojiPanelEmptyText': '#8E8E93',
    'chat_emojiPanelBadgeBackground': '#0A84FF',
    'chat_emojiPanelTrendingTitle': '#FFFFFF',
    'chat_emojiPanelTrendingDescription': '#8E8E93',
    'chat_emojiPanelBackspace': '#8E8E93',
    'chat_emojiPanelStickerPackSelector': '#3A3A3C',
    # --- переключатели, чекбоксы, списки ---
    'switchTrack': '#3A3A3C',
    'switchTrackChecked': '#30D158',
    'switchTrackBlue': '#3A3A3C',
    'switchTrackBlueChecked': '#0A84FF',
    'switchTrackBlueThumb': '#FFFFFF',
    'switchTrackBlueThumbChecked': '#FFFFFF',
    'switchTrackBlueSelector': '#330A84FF',
    'switchTrackBlueSelectorChecked': '#330A84FF',
    'checkboxSquareBackground': '#0A84FF',
    'checkboxSquareUnchecked': '#8E8E93',
    'checkboxSquareDisabled': '#3A3A3C',
    'radioBackground': '#8E8E93',
    'radioBackgroundChecked': '#0A84FF',
    'windowBackgroundChecked': '#0A84FF',
    'windowBackgroundUnchecked': '#8E8E93',
    'windowBackgroundCheckText': '#FFFFFF',
    'dialogCheckboxSquareUnchecked': '#8E8E93',
    'dialogCheckboxSquareDisabled': '#3A3A3C',
    'dialogRoundCheckBox': '#0A84FF',
    'listSelectorSDK21': '#0FFFFFFF',
    'actionBarDefaultSelector': '#14FFFFFF',
    'actionBarWhiteSelector': '#14FFFFFF',
    'actionBarDefaultArchivedSelector': '#14FFFFFF',
    'actionBarActionModeDefaultSelector': '#14FFFFFF',
    'actionBarActionModeDefaultIcon': '#FFFFFF',
    'actionBarActionModeDefault': '#1C1C1E',
    'actionBarTabSelector': '#330A84FF',
    'profile_tabSelector': '#330A84FF',
    'profile_tabText': '#8E8E93',
    'actionBarDefaultSubmenuItem': '#FFFFFF',
    'actionBarDefaultSubmenuItemIcon': '#8E8E93',
    'chats_menuItemText': '#FFFFFF',
    'chats_menuItemIcon': '#8E8E93',
    'chats_menuPhone': '#8E8E93',
    'chats_menuPhoneCats': '#8E8E93',
    'chats_actionBackground': '#0A84FF',
    'chats_actionMessage': '#8E8E93',
    'chats_unreadCounter': '#0A84FF',
    'chats_unreadCounterMuted': '#3A3A3C',
    'chats_archiveBackground': '#0A84FF',
    'chats_pinnedOverlay': '#0AFFFFFF',
    'chats_tabletSelectedOverlay': '#0AFFFFFF',
    'chats_secretIcon': '#30D158',
    'chats_secretName': '#30D158',
    'chats_verifiedBackground': '#0A84FF',
    'chats_pinnedIcon': '#8E8E93',
    'chats_muteIcon': '#8E8E93',
    'chats_attachMessage': '#0A84FF',
    'chats_draft': '#FF453A',
    'groupcreate_cursor': '#0A84FF',
    'groupcreate_spanBackground': '#3A3A3C',
    'groupcreate_spanText': '#FFFFFF',
    'groupcreate_hintText': '#8E8E93',
    'groupcreate_sectionText': '#8E8E93',
    'featuredStickers_addedIcon': '#0A84FF',
    'sharedMedia_startStopLoadIcon': '#0A84FF',
    'inappPlayerTitle': '#FFFFFF',
    'inappPlayerPerformer': '#8E8E93',
    'inappPlayerPlayPause': '#0A84FF',
    'inappPlayerClose': '#8E8E93',
    'player_time': '#8E8E93',
    'player_actionBarTitle': '#FFFFFF',
    'player_actionBarSubtitle': '#8E8E93',
    'player_actionBarItems': '#8E8E93',
    'player_actionBarSelector': '#14FFFFFF',
    'player_button': '#8E8E93',
    'player_buttonActive': '#0A84FF',
    'player_progress': '#0A84FF',
    'player_progressBackground': '#3A3A3C',
    'key_player_progressCachedBackground': '#3A3A3C',
    # --- без градиентов, без стекла ---
    'chat_BlurAlpha': '#00000000',
    'chat_BlurAlphaSlow': '#00000000',
    'premiumGradientBackground1': '#0A84FF',
    'premiumGradientBackground2': '#0A84FF',
    'premiumGradientBackground3': '#0A84FF',
    'premiumGradientBackground4': '#0A84FF',
    'premiumStarGradient1': '#0A84FF',
    'premiumStarGradient2': '#0A84FF',
    'premiumStartSmallStarsColor': '#0A84FF',
    'stories_circle1': '#0A84FF',
    'stories_circle2': '#0A84FF',
    'stories_circle_dialog1': '#0A84FF',
    'stories_circle_dialog2': '#0A84FF',
    'stories_circle_closeFriends1': '#0A84FF',
    'stories_circle_closeFriends2': '#0A84FF',
    'glass_defaultIcon': '#FFFFFF',
    'glass_defaultText': '#FFFFFF',
    'glass_targetMainTabs': '#00000000',
    'glass_targetMainTopPanel': '#00000000',
}

text = io.open(base_file, encoding='utf-8').read()
out, applied, seen = [], 0, set()
for line in text.split('\n'):
    if '=' in line:
        k = line.split('=', 1)[0]
        if k in P:
            line = k + '=' + str(to_int(P[k]))
            applied += 1
            seen.add(k)
    out.append(line)
missing = [k for k in P if k not in seen]
if applied < 60:
    sys.stderr.write('P16: применилось только %d ключей — палитра не подходит под эту версию темы\n' % applied)
    sys.exit(1)
if missing:
    sys.stderr.write('P16: ключи не найдены (пропущены): %s\n' % ', '.join(sorted(missing)[:25]))
new_theme = '\n'.join(out)
for name in ('bluebubbles.attheme', 'darkblue.attheme', 'night.attheme'):
    io.open(os.path.join(assets, name), 'w', encoding='utf-8').write(new_theme)
print('применено ключей: %d (файлов тем: 3)' % applied)
PY
    grep -q "chat_wallpaper=-16777216" "$TG_DIR/TMessagesProj/src/main/assets/bluebubbles.attheme" \
        || die "P16: тема дня не переписана на чистый чёрный (#000000)"
    ok "P16 iOS-тема KamiGram: фон #000000, поверхности #1C1C1E, акцент iOS #0A84FF, градиенты и стекло — плоские"
else
    skip "P16 iOS-тема не применяется (IOS_THEME=0)"
fi

# =============================================================================
# P17. ПЛОСКИЙ ДИЗАЙН: тяжёлый узор чата (496 КБ) заменяем минимальным SVG
# =============================================================================
PATTERN="$TG_DIR/TMessagesProj/src/main/res/raw/default_pattern.svg"
if [ "$FLAT_UI" = "1" ] && [ -f "$PATTERN" ]; then
    before=$(wc -c < "$PATTERN")
    printf '%s' '<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><rect width="1" height="1" fill="#000000"/></svg>' > "$PATTERN"
    after=$(wc -c < "$PATTERN")
    ok "P17 узор чата заменён минимальным SVG (-$(( (before-after)/1024 )) КБ): фон плоский, без паттерна"
else
    skip "P17 узор чата не тронут (FLAT_UI=$FLAT_UI)"
fi

# =============================================================================
# P18. ПРОКСИ ПО ССЫЛКЕ: вставил ссылку (t.me/proxy, tg://proxy, socks, webproxy)
#      — прокси сразу активируется, без лишних нажатий.
# =============================================================================
AU="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java"
if [ "$AUTO_PROXY" = "1" ]; then
    if ! grep -q "^import android.widget.Toast;" "$AU"; then
        sed_i '0,/^import android.content.SharedPreferences;$/s//import android.content.SharedPreferences;\nimport android.widget.Toast;/' "$AU"
    fi
    python3 - "$AU" <<'PY' || die "P18: не удалось внедрить автоактивацию прокси"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
marker = '/* KAMIGRAM_AUTO_PROXY */'
old = (
    "                final ProxySettings proxySettings = ProxySettings.fromUri(data);\n"
    "                if (proxySettings != null && proxySettings.isValid()) {\n"
    "                    if (invoked) showProxyAlert(activity, proxySettings);\n"
    "                    return true;\n"
    "                }"
)
new = (
    "                final ProxySettings proxySettings = ProxySettings.fromUri(data);\n"
    "                if (proxySettings != null && proxySettings.isValid()) {\n"
    "                    if (invoked) {\n"
    "                        " + marker + "\n"
    "                        try {\n"
    "                            final SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(proxySettings);\n"
    "                            SharedConfig.addProxy(info);\n"
    "                            SharedConfig.currentProxy = info;\n"
    "                            SharedConfig.saveProxyList();\n"
    "                            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();\n"
    "                            editor.putBoolean(\"proxy_enabled\", true);\n"
    "                            proxySettings.toSharedPreferences(editor);\n"
    "                            editor.commit();\n"
    "                            ConnectionsManager.setProxySettings(true, proxySettings);\n"
    "                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);\n"
    "                            Toast.makeText(activity, proxySettings.getAddress() + \":\" + proxySettings.getPort() + \" \\u2014 \\u043f\\u0440\\u043e\\u043a\\u0441\\u0438 \\u0432\\u043a\\u043b\\u044e\\u0447\\u0451\\u043d\", Toast.LENGTH_SHORT).show();\n"
    "                        } catch (Exception e) {\n"
    "                            FileLog.e(e);\n"
    "                            showProxyAlert(activity, proxySettings);\n"
    "                        }\n"
    "                    } else {\n"
    "                        showProxyAlert(activity, proxySettings);\n"
    "                    }\n"
    "                    return true;\n"
    "                }"
)
if marker in src:
    pass
elif old in src:
    io.open(path, 'w', encoding='utf-8').write(src.replace(old, new, 1))
else:
    sys.stderr.write('P18: не найден блок обработки прокси-ссылки (структура изменилась)\n')
    sys.exit(1)
PY
    [ "$(grep -c 'KAMIGRAM_AUTO_PROXY' "$AU")" = "1" ] || die "P18: маркер автоактивации не один"
    ok "P18 ссылка на прокси активирует его сразу (t.me/proxy, tg://proxy, socks, webproxy) с тостом-подтверждением"
else
    skip "P18 автоактивация прокси отключена (AUTO_PROXY=0)"
fi

# =============================================================================
# P19. РАЗМЕР APK: вырезаем Google App Indexing (Firebase) — моду он не нужен
# =============================================================================
LA="$TG_DIR/TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
TGPROJ_GRADLE="$TG_DIR/TMessagesProj/build.gradle"
if [ "$DROP_APPINDEXING" = "1" ]; then
    python3 - "$LA" "$TGPROJ_GRADLE" <<'PY' || die "P19: не удалось вырезать appindexing"
import io, re, sys
la, gradle = sys.argv[1], sys.argv[2]
src = io.open(la, encoding='utf-8').read()
marker = '/* KAMIGRAM_NO_APPINDEXING */'
if marker not in src:
    src = src.replace('import com.google.firebase.appindexing.Action;\n', '', 1)
    src = src.replace('import com.google.firebase.appindexing.FirebaseUserActions;\n', '', 1)
    src = src.replace('import com.google.firebase.appindexing.builders.AssistActionBuilder;\n', '', 1)
    pattern = re.compile(
        r'[ \t]*final Action assistAction = new AssistActionBuilder\(\)\s*.*?FirebaseUserActions\.getInstance\(this\)\.end\(assistAction\);\n',
        re.S)
    src, n = pattern.subn('', src)
    if n == 0:
        sys.stderr.write('P19: блоков AssistActionBuilder не найдено\n')
        sys.exit(1)
    src = src.replace('intent.removeExtra(EXTRA_ACTION_TOKEN);',
                      'intent.removeExtra(EXTRA_ACTION_TOKEN); ' + marker, 1)
    io.open(la, 'w', encoding='utf-8').write(src)

g = io.open(gradle, encoding='utf-8').read()
g = g.replace("    implementation 'com.google.firebase:firebase-appindexing:20.0.0'\n", '')
io.open(gradle, 'w', encoding='utf-8').write(g)
PY
    grep -q "firebase-appindexing" "$TGPROJ_GRADLE" && die "P19: зависимость appindexing осталась" || true
    grep -q "AssistActionBuilder" "$LA" && die "P19: в LaunchActivity остались ссылки на AssistActionBuilder" || true
    ok "P19 Google App Indexing вырезан (AssistActionBuilder, FirebaseUserActions и зависимость удалены)"
else
    skip "P19 appindexing оставлен (DROP_APPINDEXING=0)"
fi

# =============================================================================
# P20. KamiGram iOS UI — НАСТОЯЩИЙ КОД (не тема):
#      свой класс конфигурации мода, плоский нижний таб-бар в стиле iOS
#      (собственные иконки, без «стекла»/размытия и без подложки-пилюли),
#      шеврон «назад» как в iOS вместо стрелки.
# =============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KAMIGRAM_SRC="$SCRIPT_DIR/kamigram"
JAVA_ROOT="$TG_DIR/TMessagesProj/src/main/java"
RES_ROOT="$TG_DIR/TMessagesProj/src/main/res"

if [ "$IOS_UI" = "1" ]; then
    [ -d "$KAMIGRAM_SRC" ] || die "P20: нет папки $KAMIGRAM_SRC с исходниками KamiGram"

    # 1) собственный код мода
    mkdir -p "$JAVA_ROOT/org/telegram/messenger/kamigram" \
             "$JAVA_ROOT/org/telegram/ui/Components/kamigram" \
             "$RES_ROOT/drawable"
    cp -f "$KAMIGRAM_SRC/KamiGramConfig.java" "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramConfig.java"
    for icon in kamigram_tab_chats kamigram_tab_contacts kamigram_tab_calls kamigram_tab_settings; do
        cp -f "$KAMIGRAM_SRC/res/drawable/$icon.xml" "$RES_ROOT/drawable/$icon.xml"
    done

    # 2) iOS-шеврон вместо стрелки «назад»: убираем webp во всех плотностях, кладём вектор
    removed_back=0
    while IFS= read -r f; do
        [ -n "$f" ] || continue
        rm -f "$f"; removed_back=$((removed_back+1))
    done < <(find "$RES_ROOT" -name 'ic_ab_back.webp' 2>/dev/null)
    cp -f "$KAMIGRAM_SRC/res/drawable/ic_ab_back.xml" "$RES_ROOT/drawable/ic_ab_back.xml"

    # 3) фабрика iOS-табов внутри GlassTabView (там есть доступ к приватным полям таба)
    GTV="$JAVA_ROOT/org/telegram/ui/Components/glass/GlassTabView.java"
    python3 - "$GTV" <<'PY' || die "P20: не удалось добавить iOS-фабрику таба"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()

factory_marker = '/* KAMIGRAM_IOS_TAB_FACTORY */'
if factory_marker not in src:
    anchor = '    public static GlassTabView createAvatar(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, @StringRes int stringRes) {'
    if anchor not in src:
        sys.stderr.write('P20: не найден createAvatar в GlassTabView\n')
        sys.exit(1)
    factory = (
        '    ' + factory_marker + '\n'
        '    // KamiGram: iOS-style tab - flat, no glass and no selected pill,\n'
        '    // with its own KamiGram vector icon (tinted like a regular tab).\n'
        '    public static GlassTabView createKamiGramIOSTab(Context context, Theme.ResourcesProvider resourcesProvider, @DrawableRes int iconRes, @StringRes int stringRes) {\n'
        '        GlassTabView tab = new GlassTabView(context);\n'
        '        tab.resourcesProvider = resourcesProvider;\n'
        '        tab.tabAnimation = null;\n'
        '        tab.kamigramIOSTab = true;\n'
        '        tab.textView.setText(LocaleController.getString(stringRes));\n'
        '        tab.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);\n'
        '        tab.imageView.setLayoutParams(LayoutHelper.createFrame(26, 26, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 3, 0, 0));\n'
        '        tab.imageView.setImageResource(iconRes);\n'
        '        tab.colorDefault = 0xff8e8e93;\n'
        '        tab.colorSelected = 0xff0a84ff;\n'
        '        tab.colorSelectedText = 0xff0a84ff;\n'
        '        tab.setSkipDrawSelector(true);\n'
        '        tab.updateColors();\n'
        '        return tab;\n'
        '    }\n\n'
    )
    src = src.replace(anchor, factory + anchor, 1)

fixed_marker = '/* KAMIGRAM_IOS_TAB_FIXED */'
if fixed_marker not in src:
    field_old = '    private boolean skipDrawSelector;\n'
    if field_old not in src:
        sys.stderr.write('P20: не найдено поле skipDrawSelector\n')
        sys.exit(1)
    src = src.replace(field_old, field_old + '    private boolean kamigramIOSTab; ' + fixed_marker + '\n', 1)

    setter_old = '    public void setSkipDrawSelector(boolean skipDrawSelector) {\n'
    if setter_old not in src:
        sys.stderr.write('P20: не найден setSkipDrawSelector\n')
        sys.exit(1)
    src = src.replace(setter_old, setter_old +
                      '        if (kamigramIOSTab) {\n'
                      '            skipDrawSelector = true;\n'
                      '        }\n', 1)

    colors_old = '    public void updateColorsLottie() {\n'
    if colors_old not in src:
        sys.stderr.write('P20: не найден updateColorsLottie\n')
        sys.exit(1)
    src = src.replace(colors_old, colors_old +
                      '        if (kamigramIOSTab) {\n'
                      '            colorDefault = 0xff8e8e93;\n'
                      '            colorSelected = 0xff0a84ff;\n'
                      '            colorSelectedText = 0xff0a84ff;\n'
                      '            updateColors();\n'
                      '            invalidate();\n'
                      '            return;\n'
                      '        }\n', 1)

    io.open(path, 'w', encoding='utf-8').write(src)
PY

    # 4) MainTabsActivity: наши iOS-иконки + нижняя панель на всю ширину (как в iOS)
    MTA="$JAVA_ROOT/org/telegram/ui/MainTabsActivity.java"
    python3 - "$MTA" <<'PYW' || die "P20: не удалось сделать панель на всю ширину"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
marker = '/* KAMIGRAM_FULLWIDTH_TABS'
if marker not in src:
    old = '        tabsView.setMaxWidth(dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));\n'
    if old not in src:
        sys.stderr.write('P20: не найден setMaxWidth таб-бара\n')
        sys.exit(1)
    new = ('        ' + marker + ' bottom bar spans the full width like on iOS */\n'
           '        tabsView.setMaxWidth(Integer.MAX_VALUE);\n')
    io.open(path, 'w', encoding='utf-8').write(src.replace(old, new, 1))
PYW

    MTA="$JAVA_ROOT/org/telegram/ui/MainTabsActivity.java"
    python3 - "$MTA" <<'PY' || die "P20: не удалось переключить MainTabsActivity на iOS-табы"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
marker = '/* KAMIGRAM_IOS_TABS */'
changed = 0

repl = [
    ('GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats)',
     'GlassTabView.createKamiGramIOSTab(context, resourceProvider, R.drawable.kamigram_tab_chats, R.string.MainTabsChats)'),
    ('GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts)',
     'GlassTabView.createKamiGramIOSTab(context, resourceProvider, R.drawable.kamigram_tab_contacts, R.string.MainTabsContacts)'),
    ('GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings)',
     'GlassTabView.createKamiGramIOSTab(context, resourceProvider, R.drawable.kamigram_tab_settings, R.string.Settings)'),
    ('GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CALLS, R.string.MainTabsCalls)',
     'GlassTabView.createKamiGramIOSTab(context, resourceProvider, R.drawable.kamigram_tab_calls, R.string.MainTabsCalls)'),
]
for old, new in repl:
    if old in src:
        src = src.replace(old, new, 1)
        changed += 1

# фон таб-бара не трогаем: штатная отрисовка Telegram остаётся рабочей,
# а плоский вид даёт P12 (стекло и размытие выключены в LiteMode)

if changed == 0:
    sys.stderr.write('P20: структура MainTabsActivity не изменилась\n')
    sys.exit(1)
io.open(path, 'w', encoding='utf-8').write(src)
PY

    [ "$(grep -c 'createKamiGramIOSTab' "$MTA")" = "4" ] || die "P20: ожидалось 4 iOS-таба"
    ok "P20 КОД: свои iOS-иконки табов KamiGram (вектор, палитра iOS #0A84FF/#8E8E93, без подложки-пилюли) + шеврон «назад» как в iOS (заменено webp-стрелок: $removed_back)"
else
    skip "P20 iOS-интерфейс не применяется (IOS_UI=0)"
fi

# =============================================================================
# P21. GHOST MODE — уникальная функция KamiGram: собеседник не видит,
#      что мы читаем сообщения, печатаем и находимся в сети.
# =============================================================================
if [ "$GHOST_MODE" = "1" ]; then
    MC="$JAVA_ROOT/org/telegram/messenger/MessagesController.java"
    CM="$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java"
    python3 - "$MC" "$CM" <<'PY' || die "P21: не удалось включить ghost-режим"
import io, sys
mc_path, cm_path = sys.argv[1], sys.argv[2]
marker = '/* KAMIGRAM_GHOST */'
config = 'org.telegram.messenger.kamigram.KamiGramConfig'

src = io.open(mc_path, encoding='utf-8').read()
if 'KAMIGRAM_GHOST_READ' not in src:
    old = '    private void completeReadTask(ReadTask task) {\n'
    if old not in src:
        sys.stderr.write('P21: не найден completeReadTask\n')
        sys.exit(1)
    src = src.replace(old, old +
                      '        /* KAMIGRAM_GHOST_READ: no read receipts sent */\n'
                      '        if (' + config + '.ghostMode()) {\n'
                      '            return;\n'
                      '        }\n', 1)

    old_typing = '    public boolean sendTyping(long dialogId, long threadMsgId, int action, String emojicon, int classGuid) {\n'
    if old_typing not in src:
        sys.stderr.write('P21: не найден sendTyping\n')
        sys.exit(1)
    src = src.replace(old_typing, old_typing +
                      '        /* KAMIGRAM_GHOST_TYPING: no typing indicator sent */\n'
                      '        if (' + config + '.ghostMode()) {\n'
                      '            return false;\n'
                      '        }\n', 1)
    io.open(mc_path, 'w', encoding='utf-8').write(src)

src = io.open(cm_path, encoding='utf-8').read()
if 'KAMIGRAM_GHOST_STATUS' not in src:
    anchor = '    public int sendRequest(TLObject object, RequestDelegate completionBlock) {\n'
    if anchor not in src:
        sys.stderr.write('P21: не найден sendRequest(TLObject, RequestDelegate)\n')
        sys.exit(1)
    guard = (anchor +
             '        /* KAMIGRAM_GHOST_STATUS: no online/offline sent */\n'
             '        if (object instanceof org.telegram.tgnet.tl.TL_account.updateStatus && ' + config + '.ghostMode()) {\n'
             '            return 0;\n'
             '        }\n')
    src = src.replace(anchor, guard, 1)
    io.open(cm_path, 'w', encoding='utf-8').write(src)
print('ghost-режим внедрён')
PY
    [ "$(grep -c 'KAMIGRAM_GHOST' "$MC")" -ge 2 ] || die "P21: ghost-маркеров в MessagesController меньше двух"
    ok "P21 УНИКАЛЬНАЯ ФУНКЦИЯ: ghost-режим — не уходят «прочитано», «печатает» и статус «в сети» (KamiGramConfig.ghostMode())"
else
    skip "P21 ghost-режим не применяется (GHOST_MODE=0)"
fi

# =============================================================================
# P22. БЕЗ ЗАПРЕТОВ — уникальная функция: защищённый контент можно
#      пересылать, сохранять, копировать и снимать скриншоты.
# =============================================================================
if [ "$NO_RESTRICTIONS" = "1" ]; then
    MC="$JAVA_ROOT/org/telegram/messenger/MessagesController.java"
    MO="$JAVA_ROOT/org/telegram/messenger/MessageObject.java"
    CA="$JAVA_ROOT/org/telegram/ui/ChatActivity.java"
    python3 - "$MC" "$MO" "$CA" <<'PY' || die "P22: не удалось снять ограничения защищённого контента"
import io, sys
mc_path, mo_path, ca_path = sys.argv[1], sys.argv[2], sys.argv[3]
marker = '/* KAMIGRAM_NO_RESTRICTIONS */'
config = 'org.telegram.messenger.kamigram.KamiGramConfig'
changed = []

src = io.open(mc_path, encoding='utf-8').read()
if marker not in src:
    old = '    public boolean isPeerNoForwards(long dialogId) {\n'
    if old not in src:
        sys.stderr.write('P22: не найден isPeerNoForwards\n')
        sys.exit(1)
    src = src.replace(old, old +
                      '        ' + marker + '\n'
                      '        if (' + config + '.noRestrictions()) {\n'
                      '            return false;\n'
                      '        }\n', 1)
    io.open(mc_path, 'w', encoding='utf-8').write(src)
    changed.append('isPeerNoForwards')

src = io.open(mo_path, encoding='utf-8').read()
if marker not in src:
    old = 'return !(messageOwner instanceof TLRPC.TL_message_secret) && !needDrawBluredPreview() && !isLiveLocation() && type != MessageObject.TYPE_PHONE_CALL && !isSponsored() && !messageOwner.noforwards;'
    if old not in src:
        sys.stderr.write('P22: не найден canForwardMessage\n')
        sys.exit(1)
    new = ('return ' + config + '.noRestrictions() || (!(messageOwner instanceof TLRPC.TL_message_secret) && !needDrawBluredPreview() && !isLiveLocation() && type != MessageObject.TYPE_PHONE_CALL && !isSponsored() && !messageOwner.noforwards);')
    src = src.replace(old, marker + '\n        ' + new, 1)
    io.open(mo_path, 'w', encoding='utf-8').write(src)
    changed.append('canForwardMessage')

src = io.open(ca_path, encoding='utf-8').read()
if marker not in src:
    pairs = [
        ('        flagSecure = new FlagSecureReason(getParentActivity().getWindow(), () ->\n'
         '            currentEncryptedChat != null ||\n'
         '            isPeerNoForwards()\n'
         '        );\n',
         '        flagSecure = new FlagSecureReason(getParentActivity().getWindow(), () ->\n'
         '            /* KAMIGRAM_NO_RESTRICTIONS_SCREENSHOT: no screenshot blocking */\n'
         '            !' + config + '.noRestrictions() && (currentEncryptedChat != null || isPeerNoForwards())\n'
         '        );\n'),
        ('            final boolean noforwards = (\n',
         '            final boolean noforwards = !' + config + '.noRestrictions() && (\n'),
        ('            return chatActivity == null || !(\n',
         '            return ' + config + '.noRestrictions() || chatActivity == null || !(\n'),
        ('        if (getParentActivity() == null || getMessagesController().isPeerNoForwards(messageObject.getDialogId()) || (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards)) {\n'
         '            return;\n'
         '        }\n',
         '        ' + marker + '\n'
         '        if (getParentActivity() == null || (!' + config + '.noRestrictions() && (getMessagesController().isPeerNoForwards(messageObject.getDialogId()) || (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards)))) {\n'
         '            return;\n'
         '        }\n'),
    ]
    for old, new in pairs:
        if old in src:
            src = src.replace(old, new, 1)
            changed.append('chat')
    io.open(ca_path, 'w', encoding='utf-8').write(src)

if not changed:
    sys.stderr.write('P22: ни одна точка ограничений не найдена\n')
    sys.exit(1)
print('снято ограничений: ' + ', '.join(sorted(set(changed))))
PY
    ok "P22 УНИКАЛЬНАЯ ФУНКЦИЯ: защищённый контент без ограничений — пересылка, сохранение, копирование и скриншоты разрешены (KamiGramConfig.noRestrictions())"
else
    skip "P22 снятие ограничений не применяется (NO_RESTRICTIONS=0)"
fi

# =============================================================================
# P24. ВХОД В АККАУНТ (ГЛАВНЫЙ ФИКС): Telegram требует проверку Google Play
#      Integrity / Firebase, чтобы отправить SMS. Мод опубликован не в Google
#      Play и подписан другим ключом, поэтому проверка не проходит и может
#      висеть бесконечно — кнопка «Войти» крутится и ничего не происходит.
#      KamiGram просит у сервера обычный SMS-код и не ждёт Google.
# =============================================================================
if [ "$FIX_LOGIN" = "1" ]; then
    LA_LOGIN="$JAVA_ROOT/org/telegram/ui/LoginActivity.java"
    python3 - "$LA_LOGIN" "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" <<'PY' || die "P24: не удалось включить обычный SMS-вход"
import io, sys
login_path, launch_path = sys.argv[1], sys.argv[2]
config = 'org.telegram.messenger.kamigram.KamiGramConfig'
changed = []

src = io.open(login_path, encoding='utf-8').read()
if 'KAMIGRAM_FORCE_SMS' not in src:
    # 1) не объявляем серверу поддержку firebase/app-hash — пусть шлёт обычный SMS
    old_settings = ('            settings.allow_app_hash = settings.allow_firebase = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();\n')
    if old_settings not in src:
        sys.stderr.write('P24: не найдена настройка allow_app_hash в LoginActivity\n')
        sys.exit(1)
    new_settings = ('            /* KAMIGRAM_FORCE_SMS: mod is not in Google Play, so Play Integrity cannot pass */\n'
                    '            final boolean kamigramForceSms = ' + config + '.forceSmsLogin();\n'
                    '            settings.allow_app_hash = settings.allow_firebase = !kamigramForceSms && PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();\n')
    src = src.replace(old_settings, new_settings, 1)

    # 2) если сервер всё равно ответил типом firebase — не ждём Google, сразу просим SMS
    old_firebase = ('        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFirebaseSms && !res.type.verifiedFirebase && !isRequestingFirebaseSms) {\n')
    if old_firebase not in src:
        sys.stderr.write('P24: не найден блок firebase в fillNextCodeParams\n')
        sys.exit(1)
    new_firebase = (old_firebase +
                    '            ' + '/* KAMIGRAM_FORCE_SMS */' + '\n'
                    '            if (' + config + '.forceSmsLogin() && !' + config + '.forceSmsConsumed()) {\n'
                    '                ' + config + '.markForceSmsResent();\n'
                    '                needShowProgress(0);\n'
                    '                isRequestingFirebaseSms = true;\n'
                    '                resendCodeFromSafetyNet(params, res, "KAMIGRAM_FORCE_SMS");\n'
                    '                return;\n'
                    '            }\n')
    src = src.replace(old_firebase, new_firebase, 1)
    io.open(login_path, 'w', encoding='utf-8').write(src)
    changed.append('LoginActivity')

src = io.open(launch_path, encoding='utf-8').read()
if 'KAMIGRAM_FORCE_SMS' not in src:
    old_launch = ('                                req.settings.allow_app_hash = req.settings.allow_firebase = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();\n')
    if old_launch not in src:
        sys.stderr.write('P24: не найден allow_app_hash в LaunchActivity\n')
        sys.exit(1)
    new_launch = ('                                /* KAMIGRAM_FORCE_SMS: plain SMS instead of Google Play Integrity */\n'
                  '                                req.settings.allow_app_hash = req.settings.allow_firebase = !' + config + '.forceSmsLogin() && PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();\n')
    src = src.replace(old_launch, new_launch, 1)
    io.open(launch_path, 'w', encoding='utf-8').write(src)
    changed.append('LaunchActivity')

if not changed:
    sys.stderr.write('P24: ни одна точка не найдена\n')
    sys.exit(1)
print('fix login applied to: ' + ', '.join(changed))
PY
    [ "$(grep -c 'KAMIGRAM_FORCE_SMS' "$LA_LOGIN")" -ge 2 ] || die "P24: маркеров в LoginActivity меньше двух"
    ok "P24 ФИКС ВХОДА: код приходит обычным SMS — мод не ждёт Google Play Integrity/Firebase (из-за этого кнопка «Войти» висела без ответа)"
else
    skip "P24 фикс входа отключён (FIX_LOGIN=0)"
fi

# =============================================================================
# P25. ПРОКСИ БЕЗ РУЧНЫХ ШАГОВ: ссылка в буфере обмена активирует прокси сама,
#      а мёртвый прокси автоматически выключается, чтобы работал VPN/прямое
#      соединение (иначе включённый, но нерабочий прокси блокирует всё).
# =============================================================================
if [ "$SMART_PROXY" = "1" ]; then
    [ -f "$KAMIGRAM_SRC/KamiGramProxyHelper.java" ] || die "P25: нет $KAMIGRAM_SRC/KamiGramProxyHelper.java"
    cp -f "$KAMIGRAM_SRC/KamiGramProxyHelper.java" "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramProxyHelper.java"

    python3 - "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" "$JAVA_ROOT/org/telegram/ui/LoginActivity.java" <<'PY' || die "P25: не удалось внедрить умный прокси"
import io, sys
launch_path, login_path = sys.argv[1], sys.argv[2]

def replace_once(path, old, new, marker):
    src = io.open(path, encoding='utf-8').read()
    if marker in src:
        return False
    if old not in src:
        sys.stderr.write('P25: не найден фрагмент для диагностики: %r\n' % old[:70])
        sys.exit(1)
    io.open(path, 'w', encoding='utf-8').write(src.replace(old, new, 1))
    return True


def insert_after(path, anchor, block, marker, context_expr):
    src = io.open(path, encoding='utf-8').read()
    if marker in src:
        return False
    if anchor not in src:
        sys.stderr.write('P25: не найден якорь: %r\n' % anchor[:60])
        sys.exit(1)
    src = src.replace(anchor, anchor + block.replace('__CTX__', context_expr), 1)
    io.open(path, 'w', encoding='utf-8').write(src)
    return True

resume_block = (
    '        /* ' + 'KAMIGRAM_SMART_PROXY' + ' */\n'
    '        try {\n'
    '            org.telegram.messenger.kamigram.KamiGramProxyHelper.activateFromClipboard(__CTX__);\n'
    '            org.telegram.messenger.kamigram.KamiGramProxyHelper.watchProxy(__CTX__);\n'
    '        } catch (Throwable ignore) {\n'
    '        }\n'
)

done = []
if insert_after(launch_path, '    protected void onResume() {\n        super.onResume();\n', resume_block, 'KAMIGRAM_SMART_PROXY', 'this'):
    done.append('LaunchActivity.onResume')
if insert_after(login_path, '    public void onResume() {\n        super.onResume();\n', resume_block, 'KAMIGRAM_SMART_PROXY', 'getParentActivity()'):
    done.append('LoginActivity.onResume')

# нажатие «Войти»: поднимаем прокси из буфера и предупреждаем, если связи нет
src = io.open(login_path, encoding='utf-8').read()
anchor = '            nextPressed = true;\n'
marker = 'KAMIGRAM_LOGIN_PROXY'
if marker not in src and anchor in src:
    block = ('            ' + '/* ' + marker + ': fix proxy, warn if offline, unfreeze a stuck button */' + '\n'
             '            try {\n'
             '                org.telegram.messenger.kamigram.KamiGramProxyHelper.prepareForLogin(getParentActivity());\n'
             '                final int kamigramState = ConnectionsManager.getInstance(currentAccount).getConnectionState();\n'
             '                if (kamigramState != ConnectionsManager.ConnectionStateConnected && kamigramState != ConnectionsManager.ConnectionStateUpdating) {\n'
             '                    Toast.makeText(getParentActivity(), LocaleController.getString(R.string.WaitingForNetwork), Toast.LENGTH_SHORT).show();\n'
             '                }\n'
             '                AndroidUtilities.runOnUIThread(() -> {\n'
             '                    try {\n'
             '                        if (!nextPressed) {\n'
             '                            return;\n'
             '                        }\n'
             '                        final int st = ConnectionsManager.getInstance(currentAccount).getConnectionState();\n'
             '                        if (st == ConnectionsManager.ConnectionStateConnected || st == ConnectionsManager.ConnectionStateUpdating) {\n'
             '                            return;\n'
             '                        }\n'
             '                        nextPressed = false;\n'
             '                        needHideProgress(true);\n'
             '                        org.telegram.messenger.kamigram.KamiGramProxyHelper.showLoginProblem(getParentActivity(), kamigramLastError, "The code request got no answer within 20 seconds.");\n'
             '                    } catch (Throwable ignore) {\n'
             '                    }\n'
             '                }, 20000);\n'
             '            } catch (Throwable ignore) {\n'
             '            }\n')
    src = src.replace(anchor, block + anchor, 1)
    io.open(login_path, 'w', encoding='utf-8').write(src)
    done.append('LoginActivity.loginButton')

# поле для текста ошибки сервера + запоминание ответа в обработчике sendCode
field_anchor = '    private boolean forceDisableSafetyNet;\n'
replace_once(login_path, field_anchor,
             field_anchor + '    private String kamigramLastError; /* KAMIGRAM_LOGIN_DIAG_FIELD */\n',
             'KAMIGRAM_LOGIN_DIAG_FIELD')
error_anchor = ('            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {\n'
                '                nextPressed = false;\n')
replace_once(login_path, error_anchor,
             error_anchor + '                kamigramLastError = error != null ? (error.text != null ? error.text : "network error") : null; /* KAMIGRAM_LOGIN_DIAG_ERROR */\n',
             'KAMIGRAM_LOGIN_DIAG_ERROR')
done.append('LoginActivity.diagnostics')

if not done:
    sys.stderr.write('P25: ничего не внедрено\n')
    sys.exit(1)
print('smart proxy hook: ' + ', '.join(done))
PY
    [ "$(grep -c 'KAMIGRAM_SMART_PROXY' "$JAVA_ROOT/org/telegram/ui/LoginActivity.java")" = "1" ] || die "P25: хук в LoginActivity не один"
    grep -q 'KAMIGRAM_LOGIN_DIAG_ERROR' "$JAVA_ROOT/org/telegram/ui/LoginActivity.java" || die "P25: диагностика входа не внедрена"
    ok "P25 УМНЫЙ ПРОКСИ: ссылка в буфере обмена включает прокси сама (при запуске, при входе и по кнопке «Войти»), а нерабочий прокси автоматически выключается — VPN и прямое соединение больше не блокируются"
else
    skip "P25 умный прокси отключён (SMART_PROXY=0)"
fi

# =============================================================================
#  Итоги: MOD_INFO.txt + patch-diff для аудита изменений
# =============================================================================
cat > "$TG_DIR/MOD_INFO.txt" <<INFO
MOD_NAME=$APP_NAME
MOD_PACKAGE=$APP_PACKAGE
MOD_VERSION=$NEW_VERSION
MOD_BASE_VERSION=$BASE_VERSION
MOD_ABIS=$ABIS
MOD_MAX_ECONOMY=$MAX_ECONOMY
MOD_NO_STICKERS=$NO_STICKERS
MOD_AUTODOWNLOAD_OFF=$AUTODOWNLOAD_OFF
MOD_RES_CONFIGS=$RES_CONFIGS
MOD_SLIM_HEAVY=$SLIM_HEAVY
MOD_BUILD_LEAN=$BUILD_LEAN
MOD_IOS_THEME=$IOS_THEME
MOD_FLAT_UI=$FLAT_UI
MOD_AUTO_PROXY=$AUTO_PROXY
MOD_DROP_APPINDEXING=$DROP_APPINDEXING
MOD_IOS_UI=$IOS_UI
MOD_GHOST_MODE=$GHOST_MODE
MOD_NO_RESTRICTIONS=$NO_RESTRICTIONS
MOD_FIX_LOGIN=$FIX_LOGIN
MOD_SMART_PROXY=$SMART_PROXY
MOD_BUILD_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)
UPSTREAM_REPO=https://github.com/DrKLO/Telegram
UPSTREAM_COMMIT=$UPSTREAM_COMMIT
UPSTREAM_DATE=$UPSTREAM_DATE
TELEGRAM_API_ID_SOURCE=upstream-buildvars (official built-in, no api_id/api_hash required)
PATCHES:
$(printf ' - %s\n' "${PATCHED_LIST[@]}")
SKIPPED:
$(printf ' - %s\n' "${SKIPPED_LIST[@]:-none}")
INFO

if git -C "$TG_DIR" rev-parse --git-dir >/dev/null 2>&1; then
    git -C "$TG_DIR" diff > "$TG_DIR/mod-changes.patch" || true
fi

echo
log "──────────────────── применённые патчи ────────────────────"
for p in "${PATCHED_LIST[@]}"; do printf '  %s✓%s %s\n' "$C_GRN" "$C_OFF" "$p"; done
for p in "${SKIPPED_LIST[@]:-}"; do [ -n "$p" ] && printf '  %s·%s %s\n' "$C_YEL" "$C_OFF" "$p"; done
log "───────────────────────────────────────────────────────────"
log "MOD_INFO.txt: $TG_DIR/MOD_INFO.txt"
log "Готово. Дальше: cd $TG_DIR && ./gradlew :TMessagesProj_App:assembleAfatRelease"
