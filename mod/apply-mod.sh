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
MAX_ECONOMY=${MAX_ECONOMY:-1}             # принудительный power-saver (все анимации/автоплей выкл)
RES_CONFIGS=${RES_CONFIGS:-ru,en}         # какие языки оставить в APK (all = все)
SLIM_HEAVY=${SLIM_HEAVY:-1}               # заглушки тяжёлых Lottie-анимаций: 1 | all | 0
PATCH_GS=${PATCH_GS:-1}                   # правка google-services.json под свой applicationId
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
# P12. MAX ECONOMY: принудительный power-saver — никаких анимаций, автоплея,
#      частиц, блюра и кастомных обоев. Меньше трафика, меньше CPU, меньше RAM.
# =============================================================================
LM="$TG_DIR/TMessagesProj/src/main/java/org/telegram/messenger/LiteMode.java"
if [ "$MAX_ECONOMY" = "1" ]; then
    python3 - "$LM" <<'PY' || die "P12: не удалось включить power-saver"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
marker = '/* KAMIGRAM_MAX_ECONOMY */'
sig = 'public static int getValue(boolean ignorePowerSaving) {'
if marker not in src:
    if sig not in src:
        sys.stderr.write('P12: не найден LiteMode.getValue\n')
        sys.exit(1)
    src = src.replace(sig, sig + '\n        if (true) return PRESET_POWER_SAVER; ' + marker, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
PY
    has "$LM" "KAMIGRAM_MAX_ECONOMY" || die "P12: маркер не внедрён"
    ok "P12 power-saver принудительно: animated emoji/стикеры, автоплей GIF/видео, частицы, blur, обои — off"
else
    skip "P12 power-saver не форсируется (MAX_ECONOMY=0)"
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
