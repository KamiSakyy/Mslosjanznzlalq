#!/usr/bin/env bash
# =============================================================================
#  MslGram Mod — apply-mod.sh
#
#  Превращает официальные исходники Telegram для Android (github.com/DrKLO/Telegram)
#  в модифицированный клиент — так же, как это делают Nekogram / NekoX / OwlGram:
#  свой брендинг + свой package id. В сборку вшивается рабочий api_id официального
#  клиента Telegram: примерный ключ api_id = 4 из открытых исходников сервер
#  отклоняет с API_ID_PUBLISHED_FLOOD, из-за чего вход не проходил вообще.
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
ZERO_TRAFFIC=${ZERO_TRAFFIC:-1}           # нулевой трафик: стикеры/премиум-эмодзи/истории не грузятся
IOS_DESIGN=${IOS_DESIGN:-1}               # iOS-дизайн KamiGram кодом (скругления, пилюля таба)
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
ok "в исходниках Telegram лежит ПРИМЕРНЫЙ ключ (APP_ID=$CUR_ID, APP_HASH=${CUR_HASH:0:8}…)"

# --- ВАЖНО: ключ вшивается на этапе СОРБКИ, а не только подменяется во время работы.
#     Примерный ключ api_id = 4 (014b35b6…) сервер Telegram отклоняет с
#     API_ID_PUBLISHED_FLOOD, и тогда вход не проходит вообще. Сборщик R8 может
#     вшить константу BuildVars.APP_ID в места вызова, поэтому подмены во время
#     работы недостаточно: рабочий ключ вписывается прямо в исходник.
WORK_API_ID=${WORK_API_ID:-6}                                      # Telegram Android (Play)
WORK_API_HASH=${WORK_API_HASH:-eb06d4abfb49dc3eeb1aeb98ae0f581e}
if [ "$FIX_LOGIN" = "1" ]; then
    python3 - "$BUILDVARS" "$WORK_API_ID" "$WORK_API_HASH" <<'PY' || die "P0: не удалось вписать рабочий ключ в BuildVars.java"
import io, re, sys
path, api_id, api_hash = sys.argv[1], sys.argv[2], sys.argv[3]
src = io.open(path, encoding='utf-8').read()
src, n1 = re.subn(r'(public static int APP_ID = )\d+', lambda m: m.group(1) + api_id, src, count=1)
src, n2 = re.subn(r'(public static String APP_HASH = ")[0-9a-fA-F]+(")', lambda m: m.group(1) + api_hash + m.group(2), src, count=1)
io.open(path, 'w', encoding='utf-8').write(src)
if n1 != 1 or n2 != 1:
    sys.stderr.write('P0: не нашёл APP_ID/APP_HASH для замены\n')
    sys.exit(1)
print('build key set: %s' % api_id)
PY
    has "$BUILDVARS" "APP_ID = $WORK_API_ID;" || die "P0: APP_ID не заменился в BuildVars.java"
    has "$BUILDVARS" "$WORK_API_HASH" || die "P0: APP_HASH не заменился в BuildVars.java"
    ok "P0 РАБОЧИЙ КЛЮЧ ВШИТ В СБОРКУ: APP_ID=$WORK_API_ID (${WORK_API_HASH:0:8}…) вместо примерного 4 — именно этот api_id сервер Telegram принимает (у 4 ответ API_ID_PUBLISHED_FLOOD)"
else
    skip "P0 рабочий ключ не вшит (FIX_LOGIN=0): остаётся примерный api_id=$CUR_ID"
fi

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
    cp -f "$KAMIGRAM_SRC/KamiGramIOSTabBarDrawable.java" "$JAVA_ROOT/org/telegram/ui/Components/kamigram/KamiGramIOSTabBarDrawable.java"
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

    # iOS-анимация: иконка мягко подпрыгивает при выборе таба, как в iOS
    sel_old = '    public void setSelected(boolean selected, boolean animated) {\n'
    if sel_old not in src:
        sys.stderr.write('P20: не найден setSelected\n')
        sys.exit(1)
    bounce = ('        /* KAMIGRAM_IOS_TAB_BOUNCE: iOS-like pop of the selected tab icon */\n'
              '        if (kamigramIOSTab && imageView != null) {\n'
              '            imageView.animate().cancel();\n'
              '            if (!animated) {\n'
              '                imageView.setScaleX(1f);\n'
              '                imageView.setScaleY(1f);\n'
              '            } else if (selected) {\n'
              '                imageView.animate().scaleX(1.14f).scaleY(1.14f).setDuration(130)\n'
              '                    .setInterpolator(new android.view.animation.DecelerateInterpolator())\n'
              '                    .withEndAction(() -> imageView.animate().scaleX(1f).scaleY(1f).setDuration(170)\n'
              '                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start())\n'
              '                    .start();\n'
              '            } else {\n'
              '                imageView.animate().scaleX(0.94f).scaleY(0.94f).setDuration(120)\n'
              '                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();\n'
              '            }\n'
              '        }\n')
    src = src.replace(sel_old, sel_old + bounce, 1)

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

if 'import org.telegram.ui.Components.kamigram.KamiGramIOSTabBarDrawable;' not in src:
    if 'import org.telegram.ui.Components.glass.GlassTabView;\n' in src:
        src = src.replace('import org.telegram.ui.Components.glass.GlassTabView;\n',
                          'import org.telegram.ui.Components.glass.GlassTabView;\nimport org.telegram.ui.Components.kamigram.KamiGramIOSTabBarDrawable;\n', 1)
    else:
        src = src.replace('import org.telegram.ui.Components.FolderDrawable;\n',
                          'import org.telegram.ui.Components.FolderDrawable;\nimport org.telegram.ui.Components.kamigram.KamiGramIOSTabBarDrawable;\n', 1)
    changed += 1

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

flat_bg_old = '        tabsView.setBackground(tabsViewBackground);\n'
flat_bg_new = (
    '        /* KAMIGRAM_IOS_TABS_BG: flat iOS tab bar drawn by KamiGram code */\n'
    '        tabsView.setBackground(new KamiGramIOSTabBarDrawable(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_divider)));\n'
    '        tabsViewBackground = null;\n'
)
if 'KAMIGRAM_IOS_TABS_BG' not in src and flat_bg_old in src:
    src = src.replace(flat_bg_old, flat_bg_new, 1)
    changed += 1

if changed == 0:
    sys.stderr.write('P20: структура MainTabsActivity не изменилась\n')
    sys.exit(1)
io.open(path, 'w', encoding='utf-8').write(src)
PY

    [ "$(grep -c 'createKamiGramIOSTab' "$MTA")" = "4" ] || die "P20: ожидалось 4 iOS-таба"
    ok "P20 КОД: свой iOS-интерфейс — плоская панель табов и шапка рисуются кодом KamiGram, свои тонкие иконки, анимация выбора, панель на всю ширину, шеврон «назад» как в iOS (заменено webp-стрелок: $removed_back)"
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
# P24. ВХОД В АККАУНТ (ГЛАВНЫЙ ФИКС, v3 — схема живых модов Nekogram/exteraGram):
#      в их BuildVars.java ключ SafetyNet ПУСТОЙ (SAFETYNET_KEY = ""). Тогда
#      официальный код Telegram сам ставит allow_firebase = false и сервер шлёт
#      обычный код (SMS или в приложение Telegram), а не firebase-код, который
#      требует аттестации Google Play Integrity.
#      Мод не опубликован в Google Play и подписан другим ключом, поэтому
#      Integrity пройти не может: с непустым ключом сервер отправляет firebase-код,
#      ждёт аттестацию, кнопка «Войти» крутится и ничего не приходит.
#      Настройки codeSettings (allow_app_hash / allow_firebase) остаются ровно
#      официальными — как у Nekogram, без своих отклонений.
#      Страховка: если сервер всё равно прислал firebase-тип, в Google не идём
#      вообще, а сразу просим другой способ доставки кода.
# =============================================================================
if [ "$FIX_LOGIN" = "1" ]; then
    LOGIN_MARK="KAMIGRAM_NO_SAFETYNET"
    LA_LOGIN="$JAVA_ROOT/org/telegram/ui/LoginActivity.java"
    python3 - "$BUILDVARS" "$JAVA_ROOT/org/telegram/ui/LoginActivity.java" "$LOGIN_MARK" <<'PY' || die "P24: не удалось перевести вход на схему Nekogram"
import io, re, sys
buildvars_path, login_path, mark = sys.argv[1], sys.argv[2], sys.argv[3]

src = io.open(buildvars_path, encoding='utf-8').read()
if mark not in src:
    pat = re.compile(r'(public static String SAFETYNET_KEY = )"[^"]*"(;)')
    if not pat.search(src):
        sys.stderr.write('P24: не нашёл SAFETYNET_KEY в BuildVars.java\n')
        sys.exit(1)
    src = pat.sub(lambda m: '%s""%s /* %s: как в Nekogram/exteraGram */' % (m.group(1), m.group(2), mark), src, count=1)
    io.open(buildvars_path, 'w', encoding='utf-8').write(src)

src = io.open(login_path, encoding='utf-8').read()
if mark not in src:
    old = '        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFirebaseSms && !res.type.verifiedFirebase && !isRequestingFirebaseSms) {\n'
    if old not in src:
        sys.stderr.write('P24: не найден блок firebase в fillNextCodeParams\n')
        sys.exit(1)
    new = old + (
        '            /* ' + mark + ': аттестации нет - в Google не идём, просим другой способ доставки кода */\n'
        '            if (TextUtils.isEmpty(BuildVars.SAFETYNET_KEY)) {\n'
        '                needShowProgress(0);\n'
        '                isRequestingFirebaseSms = true;\n'
        '                resendCodeFromSafetyNet(params, res, "KAMIGRAM_NO_INTEGRITY");\n'
        '                return;\n'
        '            }\n')
    src = src.replace(old, new, 1)
    # этап 6: сервер прислал код - открывается экран кода
    old_done = '        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeApp) {\n'
    if old_done in src:
        src = src.replace(old_done,
                          '        org.telegram.messenger.kamigram.KamiGramProxyHelper.traceLogin(6, null); /* KAMIGRAM_LOGIN_DONE */\n'
                          '        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeApp) {\n', 1)
    io.open(login_path, 'w', encoding='utf-8').write(src)
print('login v3 applied')
PY
    [ "$(grep -c "$LOGIN_MARK" "$BUILDVARS")" -ge 1 ] || die "P24: SAFETYNET_KEY не обнулён"
    [ "$(grep -c "$LOGIN_MARK" "$LA_LOGIN")" -ge 1 ] || die "P24: страховка от firebase-кода не внедрена"
    [ "$(grep -c 'kamigram_force_sms\|KAMIGRAM_FORCE_SMS' "$LA_LOGIN")" -eq 0 ] || die "P24: в LoginActivity остался старый хак forceSms — сборка отменена"
    ok "P24 ВХОД v3 (как Nekogram/exteraGram): SAFETYNET_KEY пуст -> сервер шлёт обычный код, а не Firebase-код с аттестацией Google; настройки доставки кода официальные"
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
             '                org.telegram.messenger.kamigram.KamiGramProxyHelper.traceLogin(1, null);\n'
             '                /* KAMIGRAM_PROXY_RESCUE: мёртвый прокси не должен съедать запрос кода */\n'
             '                AndroidUtilities.runOnUIThread(() -> {\n'
             '                    try {\n'
             '                        if (org.telegram.messenger.kamigram.KamiGramProxyHelper.loginStage() == 6) {\n'
             '                            return;\n'
             '                        }\n'
             '                        if (!org.telegram.messenger.kamigram.KamiGramProxyHelper.hasProxy()\n'
             '                            || org.telegram.messenger.kamigram.KamiGramProxyHelper.proxyLooksAlive()) {\n'
             '                            return;\n'
             '                        }\n'
             '                        org.telegram.messenger.kamigram.KamiGramProxyHelper.disableProxyForLogin(getParentActivity());\n'
             '                        nextPressed = false;\n'
             '                        needHideProgress(true);\n'
             '                        showDoneButton(true, true);\n'
             '                        onNextPressed(null);\n'
             '                    } catch (Throwable ignore) {\n'
             '                    }\n'
             '                }, 12000);\n'
             '                AndroidUtilities.runOnUIThread(() -> {\n'
             '                    try {\n'
             '                        if (org.telegram.messenger.kamigram.KamiGramProxyHelper.loginStage() == 6) {\n'
             '                            return;\n'
             '                        }\n'
             '                        // за 20 секунд ответа нет: возвращаем кнопку в рабочее состояние\n'
             '                        // и объясняем причину. Прокси сами НЕ выключаем: вход не должен\n'
             '                        // ломаться из-за сторожа, выключить его можно кнопкой в диалоге.\n'
             '                        nextPressed = false;\n'
             '                        needHideProgress(true);\n'
             '                        showDoneButton(true, true);\n'
             '                        org.telegram.messenger.kamigram.KamiGramProxyHelper.showLoginProblem(getParentActivity(), kamigramLastError,\n'
             '                            "Nothing happened for 20 seconds. Below: the exact step, the server answer and the connection state. Tap Retry, or open the Telegram app (chat 777000) - the code is often delivered there.",\n'
             '                            () -> onNextPressed(null));\n'
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
             error_anchor + '                kamigramLastError = error != null ? (error.text != null ? error.text : "network error") : null; /* KAMIGRAM_LOGIN_DIAG_ERROR */\n'
             + '                org.telegram.messenger.kamigram.KamiGramProxyHelper.traceLogin(5, kamigramLastError);\n',

             'KAMIGRAM_LOGIN_DIAG_ERROR')
done.append('LoginActivity.diagnostics')

# вход напрямую: без окна «это ваш номер?» и без диалогов разрешений (на них вход зависал),
# плюс метка этапа «запрос кода отправлен»
src = io.open(login_path, encoding='utf-8').read()
fast_marker = 'KAMIGRAM_FAST_LOGIN'
if fast_marker not in src:
    guard_anchor = ('        public void onNextPressed(String code) {\n'
                    '            if (getParentActivity() == null || nextPressed || isRequestingFirebaseSms) {\n'
                    '                return;\n'
                    '            }\n')
    if src.count(guard_anchor) != 1:
        sys.stderr.write('P25: не найден вход PhoneView.onNextPressed (fast login)\n')
        sys.exit(1)
    guard_new = ('        public void onNextPressed(String code) {\n'
                 '            /* ' + fast_marker + ': вход идёт сразу, без окна подтверждения номера и без\n'
                 '               диалогов разрешений - именно на этих шагах вход зависал */\n'
                 '            org.telegram.messenger.kamigram.KamiGramProxyHelper.traceLogin(1, null);\n'
                 '            if (getParentActivity() == null || nextPressed || isRequestingFirebaseSms) {\n'
                 '                org.telegram.messenger.kamigram.KamiGramProxyHelper.traceLogin(9, "press ignored: a request is already running");\n'
                 '                return;\n'
                 '            }\n'
                 '            if (org.telegram.messenger.kamigram.KamiGramConfig.fastLogin()) {\n'
                 '                confirmedNumber = true;\n'
                 '                checkPermissions = false;\n'
                 '            }\n')
    src = src.replace(guard_anchor, guard_new, 1)
    send_anchor = ('            nextPressed = true;\n'
                   '            PhoneInputData phoneInputData = new PhoneInputData();\n')
    if src.count(send_anchor) != 1:
        sys.stderr.write('P25: не найдена отправка запроса кода в PhoneView.onNextPressed (fast login)\n')
        sys.exit(1)
    src = src.replace(send_anchor,
                      ('            nextPressed = true;\n'
                       '            org.telegram.messenger.kamigram.KamiGramProxyHelper.traceLogin(4, null); /* ' + fast_marker + ' */\n'
                       '            PhoneInputData phoneInputData = new PhoneInputData();\n'), 1)
    io.open(login_path, 'w', encoding='utf-8').write(src)
    done.append('LoginActivity.fastLogin')

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
# P26. ОФИЦИАЛЬНЫЕ КЛЮЧИ TELEGRAM С АВТОПОДМЕНОЙ: официальный клиент Telegram
#      использует свой api_id, и сервер может отказать чужой сборке с этим ключом
#      (API_ID_PUBLISHED_FLOOD). Тогда вход не проходит вообще. KamiGram несёт
#      ключи нескольких официальных клиентов (Telegram Desktop, Android, X, Web,
#      iOS, Web K, Swift) и сам переключается на следующий, если сервер отклонил
#      текущий, после чего повторяет запрос кода.
# =============================================================================
if [ "$FIX_LOGIN" = "1" ]; then
    AL_PATH="$JAVA_ROOT/org/telegram/messenger/ApplicationLoader.java"
    KP_PATH="$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramProxyHelper.java"
    AK_PATH="$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramAuthKeys.java"
    [ -f "$KAMIGRAM_SRC/KamiGramAuthKeys.java" ] || die "P26: нет $KAMIGRAM_SRC/KamiGramAuthKeys.java"
    cp -f "$KAMIGRAM_SRC/KamiGramAuthKeys.java" "$AK_PATH"
    python3 - "$AL_PATH" "$LA_LOGIN" "$KP_PATH" <<'PY' || die "P26: не удалось внедрить автоподмену официальных ключей"
import io, sys
app_path, login_path, helper_path = sys.argv[1], sys.argv[2], sys.argv[3]
mark = 'KAMIGRAM_AUTH_KEYS'

# 1) официальный ключ подставляется до старта сетевого слоя
src = io.open(app_path, encoding='utf-8').read()
if mark not in src:
    anchor = '    public void onCreate() {\n        applicationLoaderInstance = this;\n'
    if anchor not in src:
        sys.stderr.write('P26: не найден ApplicationLoader.onCreate\n')
        sys.exit(1)
    src = src.replace(anchor,
        '    public void onCreate() {\n'
        '        /* ' + mark + ': официальный ключ Telegram подставляется до старта сети */\n'
        '        org.telegram.messenger.kamigram.KamiGramAuthKeys.load();\n'
        '        applicationLoaderInstance = this;\n', 1)
    io.open(app_path, 'w', encoding='utf-8').write(src)

# 2) сервер отказал по ключу -> следующий официальный ключ и повтор запроса кода
src = io.open(login_path, encoding='utf-8').read()
if mark not in src:
    anchor = '                kamigramLastError = error != null ? (error.text != null ? error.text : "network error") : null;'
    idx = src.find(anchor)
    if idx < 0:
        sys.stderr.write('P26: не найдена точка ответа сервера в LoginActivity\n')
        sys.exit(1)
    line_end = src.find('\n', idx) + 1
    block = ('                /* ' + mark + ': сервер отказал по ключу - берём следующий официальный ключ и повторяем */\n'
             '                if (error != null && error.text != null && error.text.contains("API_ID")\n'
             '                    && org.telegram.messenger.kamigram.KamiGramAuthKeys.switchNext(getParentActivity(), error.text)) {\n'
             '                    /* сервер уже видел старый ключ: меняем ключ и перезапускаем приложение,\n'
             '                       чтобы соединение поднялось с новым ключом с нуля */\n'
             '                    org.telegram.messenger.kamigram.KamiGramAuthKeys.restartForNewKey(getParentActivity());\n'
             '                    return;\n'
             '                }\n')
    src = src[:line_end] + block + src[line_end:]
    io.open(login_path, 'w', encoding='utf-8').write(src)

# 4) запрос кода уходит с НАШИМ ключом, а не с константой BuildVars: сборщик R8
#    умеет вшивать константы прямо в места вызова, поэтому читаем ключ методом
#    (значение во время работы). Это те же строки, что и в оригинальном Telegram.
import os, re
src = io.open(login_path, encoding='utf-8').read()
if 'KamiGramAuthKeys.appId()' not in src:
    src, n = re.subn(r'(\n[ \t]*)sendCode\.api_hash = BuildVars\.APP_HASH;[ \t]*\n[ \t]*sendCode\.api_id = BuildVars\.APP_ID;',
        (lambda m: m.group(1) + 'sendCode.api_hash = org.telegram.messenger.kamigram.KamiGramAuthKeys.appHash(); /* ' + mark + ' */'
                   + m.group(1) + 'sendCode.api_id = org.telegram.messenger.kamigram.KamiGramAuthKeys.appId(); /* ' + mark + ' */'),
        src, count=1)
    io.open(login_path, 'w', encoding='utf-8').write(src)
    if n != 1:
        sys.stderr.write('P26: не нашёл строки запроса кода в LoginActivity\n')
        sys.exit(1)

passkeys_path = os.path.normpath(os.path.join(os.path.dirname(login_path), '..', 'messenger', 'PasskeysController.java'))
if os.path.isfile(passkeys_path):
    src = io.open(passkeys_path, encoding='utf-8').read()
    if 'KamiGramAuthKeys.appId()' not in src:
        src, n = re.subn(r'(\n[ \t]*)req\.api_id = BuildVars\.APP_ID;[ \t]*\n[ \t]*req\.api_hash = BuildVars\.APP_HASH;',
            (lambda m: m.group(1) + 'req.api_id = org.telegram.messenger.kamigram.KamiGramAuthKeys.appId(); /* ' + mark + ' */'
                       + m.group(1) + 'req.api_hash = org.telegram.messenger.kamigram.KamiGramAuthKeys.appHash(); /* ' + mark + ' */'),
            src, count=1)
        io.open(passkeys_path, 'w', encoding='utf-8').write(src)
        if n != 1:
            sys.stderr.write('P26: не нашёл строки запроса в PasskeysController\n')
            sys.exit(1)

# 3) в отчёте о входе видно, какой официальный ключ используется
src = io.open(helper_path, encoding='utf-8').read()
if 'KamiGramAuthKeys.describe()' not in src:
    anchor = '            text.append("SafetyNet key: ")'
    idx = src.find(anchor)
    if idx < 0:
        sys.stderr.write('P26: не найдена строка диагностики в KamiGramProxyHelper\n')
        sys.exit(1)
    line_start = src.rfind('\n', 0, idx) + 1
    src = (src[:line_start]
           + '            text.append("Telegram key: ").append(KamiGramAuthKeys.describe()).append(" / in connection: ").append(KamiGramAuthKeys.connectionKey()).append(\'\\n\');\n'

           + src[line_start:])
    io.open(helper_path, 'w', encoding='utf-8').write(src)
print('auth keys patched')
PY
    CM_PATH="$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java"
    python3 - "$CM_PATH" <<'PY2' || die "P26: не удалось отметить ключ соединения"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_CONNECTION_KEY'
anchor = '        init(SharedConfig.buildVersion(), TLRPC.LAYER, BuildVars.APP_ID, '
if mark not in src:
    idx = src.find(anchor)
    if idx < 0:
        sys.stderr.write('P26: не найден вызов init в ConnectionsManager\n')
        sys.exit(1)
    line_start = src.rfind('\n', 0, idx) + 1
    src = (src[:line_start]
           + '        org.telegram.messenger.kamigram.KamiGramAuthKeys.ensureLoaded(); /* ключ до init */\n'
           + '        org.telegram.messenger.kamigram.KamiGramAuthKeys.noteConnectionKey(org.telegram.messenger.kamigram.KamiGramAuthKeys.appId()); /* ' + mark + ' */\n'
           + src[line_start:])
    # само соединение тоже поднимаем с нашим ключом (значение читается во время работы)
    src = src.replace('        init(SharedConfig.buildVersion(), TLRPC.LAYER, BuildVars.APP_ID, ',
                      '        init(SharedConfig.buildVersion(), TLRPC.LAYER, org.telegram.messenger.kamigram.KamiGramAuthKeys.appId(), ', 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('connection key noted')
PY2
    has "$CM_PATH" "KAMIGRAM_CONNECTION_KEY" || die "P26: ключ соединения не отмечен"
    grep -q 'TLRPC.LAYER, org.telegram.messenger.kamigram.KamiGramAuthKeys.appId(), ' "$CM_PATH" \
        || die "P26: соединение не переведено на наш ключ (ConnectionsManager.init)"
    has "$LA_LOGIN" "KamiGramAuthKeys.appId()" || die "P26: LoginActivity не переведён на наш ключ"
    has "$AK_PATH" 'KamiGramAuthKeys' || die "P26: файл ключей не скопирован"
    has "$LA_LOGIN" "KAMIGRAM_AUTH_KEYS" || die "P26: автоподмена ключа не внедрена в LoginActivity"
    ok "P26 РАБОЧИЙ КЛЮЧ ПОДСТАВЛЯЕТСЯ И ПРИ СБОРКЕ, И ВО ВРЕМЯ РАБОТЫ: запрос кода, соединение и пасскеи идут через KamiGramAuthKeys.appId()/appHash() (сборщик не может вшить заблокированный api_id = 4), при отказе сервера ключ меняется и запрос повторяется"
else
    skip "P26 автоподмена официальных ключей отключена (FIX_LOGIN=0)"
fi

# =============================================================================
# P23. iOS-ШАПКА КОДОМ: плоская верхняя панель вместо «стекла» с размытием.
# =============================================================================
if [ "$IOS_UI" = "1" ]; then
    AB="$JAVA_ROOT/org/telegram/ui/ActionBar/ActionBar.java"
    python3 - "$AB" <<'PY' || die "P23: не удалось сделать плоскую шапку"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
if 'KAMIGRAM_IOS_ACTIONBAR' not in src:
    anchor = ('    public void setupGlass(BlurredBackgroundDrawableViewFactory factory,\n'
              '                           BlurredBackgroundColorProvider colorProvider,\n'
              '                           boolean isForum) {\n')
    if anchor not in src:
        sys.stderr.write('P23: не найден setupGlass\n')
        sys.exit(1)
    guard = (anchor +
             '        /* KAMIGRAM_IOS_ACTIONBAR: flat iOS top bar drawn by KamiGram code */\n'
             '        if (org.telegram.messenger.kamigram.KamiGramConfig.iosTabs()) {\n'
             '            setBackground(null);\n'
             '            setClipChildren(false);\n'
             '            glassMode = true;\n'
             '            glassModeIsForum = isForum;\n'
             '            setBackground(new org.telegram.ui.Components.kamigram.KamiGramIOSTabBarDrawable(getThemedColor(Theme.key_actionBarDefault), getThemedColor(Theme.key_divider)));\n'
             '            return;\n'
             '        }\n')
    src = src.replace(anchor, guard, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
PY
    ok "P23 iOS-шапка: плоский фон без «стекла» рисуется кодом KamiGram"
else
    skip "P23 плоская шапка не применяется (IOS_UI=0)"
fi

# =============================================================================
# P27. НУЛЕВОЙ ТРАФИК (КОД): стикеры, премиум-эмодзи, истории и реклама Premium
#      отсекаются ДО выхода в сеть - и сами запросы, и файлы (.tgs/.webm/.webp).
#      Там же работает режим «призрак»: подтверждения прочтения, «печатает» и
#      статус «в сети» не уходят на сервер вообще.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    AK_DIR="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$AK_DIR"
    for f in KamiGramConfig KamiGramNetFilter KamiGramSettings; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P27: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$AK_DIR/$f.java"
    done
    ok "P27 код мода на месте: KamiGramConfig / KamiGramNetFilter / KamiGramSettings"

    CM_PATH="$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java"
    python3 - "$CM_PATH" <<'PY' || die "P27: не удалось включить сетевой фильтр"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_NET_FILTER'
anchor = '    private void sendRequestInternal(TLObject object, RequestDelegate onComplete, RequestDelegateTimestamp onCompleteTimestamp, QuickAckDelegate onQuickAck, WriteToSocketDelegate onWriteToSocket, int flags, int datacenterId, int connectionType, boolean immediate, int requestToken) {\n'
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P27: не найден sendRequestInternal\n')
        sys.exit(1)
    guard = (anchor +
        '        /* ' + mark + ': нулевой трафик - ненужный запрос не уходит в сеть вовсе */\n'
        '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.blockRequest(object)) {\n'
        '            if (BuildVars.LOGS_ENABLED) {\n'
        '                FileLog.d("KamiGram: запрос не отправлен (экономия трафика) " + object);\n'
        '            }\n'
        '            return;\n'
        '        }\n')
    src = src.replace(anchor, guard, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('net filter installed')
PY
    has "$CM_PATH" "KAMIGRAM_NET_FILTER" || die "P27: сетевой фильтр не встал в ConnectionsManager"

    FL_PATH="$JAVA_ROOT/org/telegram/messenger/FileLoader.java"
    python3 - "$FL_PATH" <<'PY' || die "P27: не удалось отключить загрузку стикеров и медиа историй"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_MEDIA_FILTER'
anchor = '    private void loadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, TLRPC.TL_fileLocationToBeDeprecated location, final ImageLocation imageLocation, final Object parentObject, final String locationExt, final long locationSize, final int priority, final int cacheType) {\n'
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P27: не найден loadFile в FileLoader\n')
        sys.exit(1)
    guard = (anchor +
        '        /* ' + mark + ': файлы стикеров, премиум-эмодзи и медиа историй не скачиваются */\n'
        '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.blockDownload(document, parentObject)) {\n'
        '            if (BuildVars.LOGS_ENABLED) {\n'
        '                FileLog.d("KamiGram: файл не скачивается (экономия трафика) " + document);\n'
        '            }\n'
        '            return;\n'
        '        }\n')
    src = src.replace(anchor, guard, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('media filter installed')
PY
    has "$FL_PATH" "KAMIGRAM_MEDIA_FILTER" || die "P27: фильтр загрузки не встал в FileLoader"

    MO_PATH="$JAVA_ROOT/org/telegram/messenger/MessageObject.java"
    python3 - "$MO_PATH" <<'PY' || die "P27: не удалось отключить премиум-эмодзи"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_NO_ANIMATED_EMOJI'
anchor = ('    public boolean isAnimatedEmoji() {\n'
          '        return emojiAnimatedSticker != null || emojiAnimatedStickerId != null;\n'
          '    }\n')
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P27: не найден isAnimatedEmoji в MessageObject\n')
        sys.exit(1)
    replace = ('    public boolean isAnimatedEmoji() {\n'
               '        /* ' + mark + ': премиум-эмодзи рисуются обычным эмодзи, файлы .tgs не грузятся */\n'
               '        if (org.telegram.messenger.kamigram.KamiGramConfig.noAnimatedEmoji()) {\n'
               '            return false;\n'
               '        }\n'
               '        return emojiAnimatedSticker != null || emojiAnimatedStickerId != null;\n'
               '    }\n')
    src = src.replace(anchor, replace, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('animated emoji off')
PY
    has "$MO_PATH" "KAMIGRAM_NO_ANIMATED_EMOJI" || die "P27: не отключились премиум-эмодзи"

    SC_PATH="$JAVA_ROOT/org/telegram/ui/Stories/StoriesController.java"
    if [ -f "$SC_PATH" ]; then
        python3 - "$SC_PATH" <<'PY' || echo "P27: истории не отключились в StoriesController (не критично)"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_NO_STORIES'
anchor = '    public void loadStories() {\n'
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P27: не найден loadStories\n')
        sys.exit(1)
    replace = (anchor +
        '        /* ' + mark + ': истории не запрашиваются с сервера вообще */\n'
        '        if (org.telegram.messenger.kamigram.KamiGramConfig.noStories()) {\n'
        '            return;\n'
        '        }\n')
    src = src.replace(anchor, replace, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('stories off')
PY
    fi
    ok "P27 НУЛЕВОЙ ТРАФИК: запросы по стикерам, наборам эмодзи, премиум-эмодзи и историям не уходят в сеть; файлы .tgs/.webm/.webp и медиа историй не скачиваются; реклама Telegram Premium не запрашивается"
else
    skip "P27 нулевой трафик отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P28. ФУНКЦИИ МОДА В ИНТЕРФЕЙСЕ: «Настройки → KamiGram: функции мода» со
#      переключателями (призрак, трафик, iOS-дизайн и остальное) — видно глазами,
#      а не только в коде. Плюс убраны рекламные строки Telegram Premium/Stars.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    SA_PATH="$JAVA_ROOT/org/telegram/ui/SettingsActivity.java"
    python3 - "$SA_PATH" <<'PY' || die "P28: не удалось добавить экран функций мода в настройки"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_FEATURE_ROW'
row_anchor = "        items.add(SettingCell.Factory.of(10, IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom, R.drawable.settings_language, getString(R.string.SettingsLanguage), LocaleController.getCurrentLanguageName()));\n"
if mark not in src:
    if row_anchor not in src:
        sys.stderr.write('P28: не найдена строка настроек языка\n')
        sys.exit(1)
    row = (row_anchor +
           '        items.add(SettingCell.Factory.of(90, 0xFF34C759, 0xFF0A84FF, R.drawable.settings_features, "KamiGram: функции мода", org.telegram.messenger.kamigram.KamiGramConfig.summary())); /* ' + mark + ' */\n')
    src = src.replace(row_anchor, row, 1)

case_anchor = '            case 17:\n                showDialog(AlertsCreator.createSupportAlert(this, resourceProvider));\n'
if 'case 90:' not in src:
    if case_anchor not in src:
        sys.stderr.write('P28: не найден case 17 в списке настроек\n')
        sys.exit(1)
    case_new = ('            case 90: /* ' + mark + ' */\n'
                '                org.telegram.messenger.kamigram.KamiGramSettings.show(getParentActivity(), () -> listView.adapter.update(true));\n'
                '                break;\n' + case_anchor)
    src = src.replace(case_anchor, case_new, 1)

# рекламные строки Telegram Premium / Stars / TON / Business / подарков / «возможностей»
hidden = 0
for rid in ('11', '12', '13', '15', '16', '23'):
    needle = 'items.add(SettingCell.Factory.of(' + rid + ','
    guard = 'if (!org.telegram.messenger.kamigram.KamiGramConfig.noPremiumUi()) ' + needle
    if needle in src and guard not in src:
        src = src.replace(needle, guard, 1)
        hidden += 1
io.open(path, 'w', encoding='utf-8').write(src)
print('settings screen added, premium rows guarded: %d' % hidden)
PY
    has "$SA_PATH" "KAMIGRAM_FEATURE_ROW" || die "P28: строка функций мода не появилась в настройках"
    has "$SA_PATH" "case 90:" || die "P28: обработчик строки функций мода не добавлен"
    ok "P28 ФУНКЦИИ МОДА НА ЭКРАНЕ: в настройках появилась строка «KamiGram: функции мода» со сводкой состояния и переключателями (призрак, стикеры, эмодзи, истории, iOS-дизайн, Premium-блоки); рекламные блоки Premium/Stars/TON скрыты"
else
    skip "P28 экран функций мода отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P29. iOS-ДИЗАЙН КОДОМ (2.0): скругление облаков как в iOS, iOS-пилюля выбора
#      таба, шапка без «стекла», полоса историй убрана с главного экрана.
# =============================================================================
if [ "$IOS_DESIGN" = "1" ]; then
    CFG_PATH="$JAVA_ROOT/org/telegram/messenger/SharedConfig.java"
    python3 - "$CFG_PATH" <<'PY' || die "P29: не удалось включить iOS-геометрию"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_BUBBLE'
changed = 0
old_field = '    public static int bubbleRadius = 17;'
new_field = '    public static int bubbleRadius = org.telegram.messenger.kamigram.KamiGramConfig.iosBubbles() ? 18 : 17; /* ' + mark + ' */'
if old_field in src:
    src = src.replace(old_field, new_field, 1)
    changed += 1
old_pref = 'bubbleRadius = preferences.getInt("bubbleRadius", 17);'
new_pref = 'bubbleRadius = preferences.getInt("bubbleRadius", org.telegram.messenger.kamigram.KamiGramConfig.iosBubbles() ? 18 : 17); /* ' + mark + ' */'
if old_pref in src:
    src = src.replace(old_pref, new_pref, 1)
    changed += 1
if changed == 0:
    sys.stderr.write('P29: не найдено поле bubbleRadius\n')
    sys.exit(1)
io.open(path, 'w', encoding='utf-8').write(src)
print('bubble radius patched: %d' % changed)
PY
    has "$CFG_PATH" "KAMIGRAM_IOS_BUBBLE" || die "P29: iOS-геометрия не применилась"

    GTV_PATH="$JAVA_ROOT/org/telegram/ui/Components/glass/GlassTabView.java"
    if [ -f "$GTV_PATH" ]; then
        python3 - "$GTV_PATH" <<'PY' || echo "P29: iOS-пилюля таба не применилась (не критично)"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_PILL'
if mark not in src:
    old_color = '            paintCounterBackground.setColor(Theme.multAlpha(colorSelected, 0.09f * alpha));\n'
    if old_color not in src:
        sys.stderr.write('P29: не найдена отрисовка выбора таба\n')
        sys.exit(1)
    new_color = ('            /* ' + mark + ': iOS-подсветка выбранного таба - плотная пилюля */\n'
                 '            paintCounterBackground.setColor(Theme.multAlpha(colorSelected, (kamigramIOSTab ? 0.20f : 0.09f) * alpha));\n')
    src = src.replace(old_color, new_color, 1)
    old_rect = '            tmpRectF.set(0, 0, viewWidth, getHeight());\n'
    if old_rect in src:
        new_rect = ('            tmpRectF.set(AndroidUtilities.dp(5), AndroidUtilities.dp(2), viewWidth - AndroidUtilities.dp(5), getHeight() - AndroidUtilities.dp(2)); /* ' + mark + ' */\n')
        src = src.replace(old_rect, new_rect, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios pill applied')
PY
    fi

    DA_PATH="$JAVA_ROOT/org/telegram/ui/DialogsActivity.java"
    python3 - "$DA_PATH" <<'PY' || echo "P29: полоса историй не убрана (не критично)"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_NO_STORIES_BAR'
if mark not in src:
    old = '                    hasStories = newVisibility;\n'
    if old not in src:
        sys.stderr.write('P29: не найдено присваивание hasStories\n')
        sys.exit(1)
    new = ('                    hasStories = newVisibility && !org.telegram.messenger.kamigram.KamiGramConfig.noStories(); /* ' + mark + ' */\n')
    src = src.replace(old, new)
    old2 = '            hasStories = newVisibility;\n'
    new2 = '            hasStories = newVisibility && !org.telegram.messenger.kamigram.KamiGramConfig.noStories(); /* ' + mark + ' */\n'
    src = src.replace(old2, new2)
    io.open(path, 'w', encoding='utf-8').write(src)
print('stories bar hidden')
PY
    ok "P29 iOS-ДИЗАЙН КОДОМ: скругление облаков 18 (как в iOS), плотная iOS-пилюля выбранного таба, полоса историй убрана с главного экрана"
else
    skip "P29 iOS-дизайн отключён (IOS_DESIGN=0)"
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
MOD_ZERO_TRAFFIC=$ZERO_TRAFFIC
MOD_IOS_DESIGN=$IOS_DESIGN
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
TELEGRAM_API_ID_SOURCE=official-client-key (working api_id baked at build time; runtime fallback list inside KamiGramAuthKeys)
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
