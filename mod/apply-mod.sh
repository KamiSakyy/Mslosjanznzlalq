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
    # ФОТО теперь скачиваются сами — по нажатию открываются мгновенно; видео и
    # документы по-прежнему только по нажатию (трафик не тратится зря).
    # Поля: mask0..mask3_photo_video_doc_audio_preloadVideo_preloadMusic_enabled_lowCallData_bitrate_preloadStories
    sed_i 's#String defaultLow = "[^"]*";#String defaultLow = "0_0_0_0_1048576_512000_512000_524288_0_0_0_1_50_0";#' "$DC"
    sed_i 's#String defaultMedium = "[^"]*";#String defaultMedium = "0_0_0_0_1048576_1048576_1048576_524288_0_0_0_1_100_0";#' "$DC"
    sed_i 's#String defaultHigh = "[^"]*";#String defaultHigh = "0_0_0_0_1048576_1048576_1048576_524288_0_0_0_1_100_0";#' "$DC"
    # старый формат настроек (обновление поверх существующей установки) — только фото
    sed_i 's#getInt(key, AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT)#getInt(key, AUTODOWNLOAD_TYPE_PHOTO)#' "$DC"
    sed_i 's#getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT)#getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO)#' "$DC"
    sed_i 's#getInt("roamingDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO)#getInt("roamingDownloadMask" + (a == 0 ? "" : a), 0)#' "$DC"

    has "$DC" 'defaultMedium = "0_0_0_0_' || die "P10: не удалось обнулить defaultMedium"
    grep -q 'AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT)' "$DC" && die "P10: остались маски автоскачивания по умолчанию"
    ok "P10 фото скачиваются сами (открытие мгновенное), видео/документы — по нажатию; preload видео/музыки/историй off"
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
     '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.stickersBlocked()) { ' + marker + ' if (onFinish != null) onFinish.run(null); return; }'),
    ('public void loadFeaturedStickers(boolean emoji, boolean cache) {',
     '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.stickersBlocked()) { ' + marker + ' return; }'),
    ('public void loadStickersByEmojiOrName(String name, boolean isEmoji, boolean cache) {',
     '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.stickersBlocked()) { ' + marker + ' return; }'),
    ('public boolean areStickersLoaded(int type) {',
     '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.stickersBlocked()) return true; ' + marker),
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
    ok "P11 стикеры и премиум-эмодзи грузятся как обычно; отключаются только тумблером в центре мода"
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
# P16. ТЕМА YORU (2026). Берём РОДНУЮ тёмную тему Telegram (assets/night.attheme
#      — там автор Telegram согласовал каждый текст со своим фоном) и переписываем
#      ТОЛЬКО цвета из mod/kamigram/apply_theme_pro.py на палитру приложения Yoru
#      (yoru-android: Ui.BG/CARD/SURFACE/PURPLE/TEXT/MUTED/LINE):
#      фон #0D0B12, карточки #1C1724, поверхности #15111C, обводки #352A43,
#      текст #F7F0FF и приглушённый #A99BB8, облака #1C1724 (вход) и #2A2138 (исход),
#      акцент и переключатели — фиолетовый Yoru #C8A7FF.
#      Никакого чёрного #000000, никакого «стекла», никаких градиентов.
#      Тот же набор уходит в bluebubbles.attheme и darkblue.attheme, поэтому
#      даже светлая системная тема приложения выглядит тёмной — чёрный
#      текст на чёрном фоне физически невозможен.
# =============================================================================
if [ "$IOS_THEME" = "1" ]; then
    KAMIGRAM_PY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/kamigram"
    python3 "$KAMIGRAM_PY/apply_theme_pro.py" "$TG_DIR/TMessagesProj/src/main/assets" \
        || die "P16: не удалось применить iOS-палитру"
    for theme_name in bluebubbles.attheme darkblue.attheme night.attheme; do
        theme_file="$TG_DIR/TMessagesProj/src/main/assets/$theme_name"
        grep -q '^windowBackgroundWhiteBlackText=-528129' "$theme_file" || die "P16: $theme_name — основной текст не #F7F0FF"
        grep -q '^windowBackgroundWhite=-14936284' "$theme_file"  || die "P16: $theme_name — поверхность не #1C1724 (Yoru CARD)"
        grep -q '^windowBackgroundGray=-15922414' "$theme_file"   || die "P16: $theme_name — фон не #0D0B12 (Yoru BG)"
        grep -q '^actionBarDefaultTitle=-528129' "$theme_file"    || die "P16: $theme_name — заголовок шапки не #F7F0FF"
        grep -q '^chat_outBubble=-14016200' "$theme_file"         || die "P16: $theme_name — исходящее облако не #2A2138"
        grep -q '^switchTrackChecked=-3627009' "$theme_file"      || die "P16: $theme_name — переключатель не #C8A7FF"
        grep -qE '^windowBackgroundGray=-16777216' "$theme_file" && die "P16: $theme_name — остался чёрный фон #000000"
    done
    ok "P16 ТЕМА YORU: фон #0D0B12, карточки #1C1724, текст #F7F0FF, акцент #C8A7FF (из yoru-android)"
else
    skip "P16 тема Yoru не применяется (IOS_THEME=0)"
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
    "                            org.telegram.messenger.kamigram.KamiGramUi.notify(activity, proxySettings.getAddress() + \":\" + proxySettings.getPort() + \" \\u2014 KamiProxy\");\n"
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

    # 2) стрелка «назад» остаётся РОДНОЙ Telegram (самодельный шеврон убран:
    #    пользователь просил вернуть иконки Telegram)
    removed_back=0
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

# ИКОНКИ ВКЛАДОК — РОДНЫЕ TELEGRAM (никаких самодельных).
# Раньше мод подменял иконки нижнего таб-бара на свои векторы, и это выглядело
# плохо («не поменял иконку настроек», «верни иконки ТГ»). Теперь иконки
# остаются телеграмовскими (createMainTab), а от мода тут только фон панели.
repl = []
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

    [ "$(grep -c 'createMainTab' "$MTA")" -ge 4 ] || die "P20: родные вкладки Telegram не найдены"
    grep -q 'createKamiGramIOSTab' "$MTA" && die "P20: самодельные иконки вкладок вернулись"
    ok "P20 КОД: плоская панель табов рисуется кодом KamiGram, а ИКОНКИ ВКЛАДОК — родные Telegram (самодельные убраны)"
else
    skip "P20 iOS-интерфейс не применяется (IOS_UI=0)"
fi

# =============================================================================
# P21. GHOST MODE (по образцу AyuGram, ветка rewrite): запросы не выбрасываются
#      наугад — перехват идёт в ОДНОЙ точке (ConnectionsManager.sendRequestInternal)
#      по конкретным типам запросов. «Прочитано» не уходит на сервер, но приложение
#      получает пустой ответ, поэтому непрочитанные чистятся ЛОКАЛЬНО и счётчики
#      работают как обычно (в прежней версии запрос молча пропадал — из-за этого
#      призрак выглядел нерабочим).
# =============================================================================
if [ "$GHOST_MODE" = "1" ]; then
    CM="$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java"
    python3 - "$CM" <<'PYPATCH' || die "P21: не удалось включить ghost-режим"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_GHOST_HOOK'
anchor = ('    private void sendRequestInternal(TLObject object, RequestDelegate onComplete, RequestDelegateTimestamp onCompleteTimestamp, '
          'QuickAckDelegate onQuickAck, WriteToSocketDelegate onWriteToSocket, int flags, int datacenterId, int connectionType, '
          'boolean immediate, int requestToken) {\n')
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P21: не найден sendRequestInternal\n')
        sys.exit(1)
    guard = (anchor +
        '        /* ' + mark + ': призрак — запрос обработан локально и в сеть не уходит */\n'
        '        if (org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete)) {\n'
        '            return;\n'
        '        }\n')
    src = src.replace(anchor, guard, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('ghost hook installed')
PYPATCH
    has "$CM" "KAMIGRAM_GHOST_HOOK" || die "P21: ghost-хук не встал в ConnectionsManager"
    has "$CM" "KamiGramGhost.interceptRequest" || die "P21: вызова KamiGramGhost.interceptRequest нет"
    ok "P21 GHOST (AyuGram): призрак — «печатает», «в сети», «прочитано» и «просмотрено» не уходят на сервер; локально всё работает как обычно"
else
    skip "P21 ghost-режим не применяется (GHOST_MODE=0)"
fi

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
    for f in KamiGramConfig KamiGramNetFilter KamiGramSettings KamiGramTheme; do
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
           '        items.add(SettingCell.Factory.of(90, 0xFFC8A7FF, 0xFF7C5CFF, R.drawable.kamigram_ic_ios_settings, "KamiGram", org.telegram.messenger.kamigram.KamiGramConfig.summary())); /* ' + mark + ' */\n')
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
# P30. iOS-ДИЗАЙН КОДОМ 3.0: время на медиа — iOS-пилюля, заголовки жирнее и
#      крупнее, карточки настроек как в iOS. Всё через один переключатель
#      iOS-дизайна, поэтому включается и выключается на ходу.
# =============================================================================
if [ "$IOS_DESIGN" = "1" ]; then
    CMC_PATH="$JAVA_ROOT/org/telegram/ui/Cells/ChatMessageCell.java"
    python3 - "$CMC_PATH" <<'PY' || die "P30: не удалось сделать iOS-пилюли времени"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_PILL_TIME'
pill = ('org.telegram.messenger.kamigram.KamiGramConfig.iosDesign() ? rect.height() / 2f : dp(4)')
count = 0
for old in ('canvas.drawRoundRect(rect, dp(4), dp(4), timeBackgroundPaint);',
            'canvas.drawRoundRect(rect, dp(4), dp(4), getThemedPaint(Theme.key_paint_chatTimeBackground));'):
    while old in src:
        new = old.replace('dp(4), dp(4)', pill + ', ' + pill)
        src = src.replace(old, new, 1)
        count += 1
if count == 0 and mark not in src:
    sys.stderr.write('P30: не найдены пилюли времени на медиа\n')
    sys.exit(1)
if mark not in src:
    src = src.replace('import org.telegram.messenger.AndroidUtilities;',
                      'import org.telegram.messenger.AndroidUtilities;\n/* ' + mark + ': время на фото/видео - iOS-пилюля вместо скруглённого прямоугольника */', 1)
io.open(path, 'w', encoding='utf-8').write(src)
print('ios time pills: %d' % count)
PY
    has "$CMC_PATH" "KAMIGRAM_IOS_PILL_TIME" || die "P30: iOS-пилюли времени не применились"

    AB_PATH="$JAVA_ROOT/org/telegram/ui/ActionBar/ActionBar.java"
    python3 - "$AB_PATH" <<'PY' || die "P30: не удалось сделать iOS-заголовки"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_TITLE'
anchor = ('        titleTextView[i].setEmojiColor(titleTextView[i].getTextColor());\n'
          '        titleTextView[i].setTypeface(AndroidUtilities.bold());\n')
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P30: не найден заголовок в ActionBar\n')
        sys.exit(1)
    add = (anchor +
           '        if (org.telegram.messenger.kamigram.KamiGramConfig.iosDesign()) { /* ' + mark + ': iOS-заголовок */\n'
           '            titleTextView[i].setTextSize(AndroidUtilities.isTablet() ? 20 : 18);\n'
           '        }\n')
    src = src.replace(anchor, add, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios titles applied')
PY
    has "$AB_PATH" "KAMIGRAM_IOS_TITLE" || die "P30: iOS-заголовки не применились"

    SA_PATH="$JAVA_ROOT/org/telegram/ui/SettingsActivity.java"
    python3 - "$SA_PATH" <<'PY' || die "P30: не удалось сделать iOS-карточки настроек"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_CARD'
old = '                final float r = dp(10);\n'
if mark not in src:
    if old not in src:
        sys.stderr.write('P30: не найден радиус карточки настроек\n')
        sys.exit(1)
    new = ('                /* ' + mark + ': карточки настроек как в iOS */\n'
           '                final float r = dp(org.telegram.messenger.kamigram.KamiGramConfig.iosDesign() ? 14 : 10);\n')
    src = src.replace(old, new, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios cards applied')
PY
    has "$SA_PATH" "KAMIGRAM_IOS_CARD" || die "P30: iOS-карточки не применились"
    ok "P30 iOS-ДИЗАЙН 3.0: время на фото/видео — iOS-пилюля, заголовки жирные и крупнее, карточки настроек с iOS-скруглением 14"
else
    skip "P30 iOS-дизайн 3.0 отключён (IOS_DESIGN=0)"
fi

# =============================================================================
# P31. ПРИЗРАК ДЛЯ ИСТОРИЙ + ЭКОНОМИЯ НА ПРЕВЬЮ ССЫЛОК:
#      при запуске включается серверная «невидимка» историй (просмотры не
#      записываются) — это уникальная функция KamiGram; превью ссылок и
#      веб-страницы больше не подгружаются (мегабайты картинок).
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    AK_DIR="$JAVA_ROOT/org/telegram/messenger/kamigram"
    [ -f "$KAMIGRAM_SRC/KamiGramGhost.java" ] || die "P31: нет $KAMIGRAM_SRC/KamiGramGhost.java"
    cp -f "$KAMIGRAM_SRC/KamiGramGhost.java" "$AK_DIR/KamiGramGhost.java"

    LA_PATH="$JAVA_ROOT/org/telegram/ui/LaunchActivity.java"
    python3 - "$LA_PATH" <<'PY' || die "P31: не удалось включить призрак для историй"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_STORIES_STEALTH'
anchor = '        currentAccount = UserConfig.selectedAccount;\n'
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P31: не найдена инициализация аккаунта в LaunchActivity\n')
        sys.exit(1)
    insert = (anchor +
              '        /* ' + mark + ': призрак для историй - просмотры не записываются */\n'
              '        org.telegram.messenger.kamigram.KamiGramGhost.onAppStarted(currentAccount);\n')
    src = src.replace(anchor, insert, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('stories stealth installed')
PY
    has "$LA_PATH" "KAMIGRAM_STORIES_STEALTH" || die "P31: призрак для историй не встал"
    python3 - "$LA_PATH" <<'PY' || die "P33: не удалось применить iOS-цвета кодом"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_COLORS'
anchor = '        /* KAMIGRAM_STORIES_STEALTH: призрак для историй - просмотры не записываются */'
if mark not in src:
    idx = src.find(anchor)
    if idx < 0:
        sys.stderr.write('P33: не найдена точка запуска мода')
        sys.exit(1)
    line_end = src.find('\n', idx) + 1
    src = (src[:line_end]
           + '        /* ' + mark + ': iOS-цвета KamiGram задаются кодом при каждом запуске */\n'
           + '        org.telegram.messenger.kamigram.KamiGramTheme.apply();\n'
           + src[line_end:])
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios colors hooked')
PY
    has "$LA_PATH" "KAMIGRAM_IOS_COLORS" || die "P33: iOS-цвета не подключились"
    ok "P31 ПРИЗРАК ДЛЯ ИСТОРИЙ: при запуске мод включает серверную невидимку историй (просмотры не записываются), превью ссылок и веб-страницы не подгружаются"
else
    skip "P31 призрак для историй отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P32. iOS-ДИЗАЙН 4.0 КОДОМ: разделители списка чатов с iOS-отступом,
#      служебные сообщения и пилюли времени — стадион (полное скругление).
# =============================================================================
if [ "$IOS_DESIGN" = "1" ]; then
    DC_PATH="$JAVA_ROOT/org/telegram/ui/Cells/DialogCell.java"
    python3 - "$DC_PATH" <<'PY' || die "P32: не удалось сдвинуть разделители списка чатов"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_DIVIDER'
old = '                left = dp(messagePaddingStart);\n'
if mark not in src:
    if old not in src:
        sys.stderr.write('P32: не найден отступ разделителя в DialogCell\n')
        sys.exit(1)
    new = ('                /* ' + mark + ': iOS-разделитель с отступом под текст */\n'
           '                left = dp(messagePaddingStart) + (org.telegram.messenger.kamigram.KamiGramConfig.iosDesign() ? dp(8) : 0);\n')
    src = src.replace(old, new, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios divider inset')
PY
    has "$DC_PATH" "KAMIGRAM_IOS_DIVIDER" || die "P32: разделители не сдвинулись"

    CMC_PATH="$JAVA_ROOT/org/telegram/ui/Cells/ChatMessageCell.java"
    python3 - "$CMC_PATH" <<'PY' || die "P32: не удалось сделать служебные сообщения iOS-пилюлей"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_IOS_STADIUM'
anchor = '    public void drawServiceBackground(Canvas canvas, RectF rect, float radius, float alpha) {\n'
if mark not in src:
    if anchor not in src:
        sys.stderr.write('P32: не найден drawServiceBackground\n')
        sys.exit(1)
    add = (anchor +
           '        /* ' + mark + ': iOS-стадион для служебных сообщений и пилюль времени */\n'
           '        if (org.telegram.messenger.kamigram.KamiGramConfig.iosDesign() && rect.height() < dp(48)) {\n'
           '            radius = rect.height() / 2f;\n'
           '        }\n')
    src = src.replace(anchor, add, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios stadium applied')
PY
    has "$CMC_PATH" "KAMIGRAM_IOS_STADIUM" || die "P32: iOS-стадион не применился"
    ok "P32 iOS-ДИЗАЙН 4.0: разделители списка чатов с iOS-отступом, служебные сообщения и пилюли времени — полностью скруглённые (стадион)"
else
    skip "P32 iOS-дизайн 4.0 отключён (IOS_DESIGN=0)"
fi

# =============================================================================
# P50. БОЛЬШОЙ ПАКЕТ УЛУЧШЕНИЙ: отсечки трафика, экономия батареи, чистка
#      интерфейса, iOS-цвета и мелочи дизайна. Каждое улучшение проверяется по
#      маркеру, отчёт пишется в MOD_FEATURES.txt, при неполном пакете — ошибка.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    python3 "$KAMIGRAM_SRC/apply_extra_patches.py" || die "P50: пакет улучшений применился не полностью"
    [ -f "$TG_DIR/MOD_FEATURES.txt" ] || die "P50: нет отчёта MOD_FEATURES.txt"
    FEATURES_COUNT=$(grep -c . "$TG_DIR/MOD_FEATURES.txt" || true)
    ok "P50 ПАКЕТ УЛУЧШЕНИЙ: применено пунктов — $FEATURES_COUNT (список: MOD_FEATURES.txt)"
else
    skip "P50 пакет улучшений отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P60. PRO-ПАКЕТ (большое обновление 2026): мощный прокси-движок с моментальным
#      переключением, кнопка прокси в шапке, показ ID, ускорение загрузок,
#      сохранность скачанного в кэше, отсечка GIF, «не спрашивать разрешения»,
#      iOS-графит + индиго и иконки кодом. Отчёт: MOD_PRO_FEATURES.txt.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    KAMI_UI="$JAVA_ROOT/org/telegram/ui/Components/kamigram"
    mkdir -p "$KAMI_PKG" "$KAMI_UI"
    for f in KamiGramProxyPower KamiGramProxyButton KamiGramIds KamiGramSpeed KamiGramCache KamiGramConfig KamiGramSettings; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P60: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    [ -f "$KAMIGRAM_SRC/KamiGramIcons.java" ] || die "P60: нет $KAMIGRAM_SRC/KamiGramIcons.java"
    cp -f "$KAMIGRAM_SRC/KamiGramIcons.java" "$KAMI_UI/KamiGramIcons.java"
    for icon in kamigram_ic_ios_settings kamigram_ic_proxy kamigram_ghost; do
        [ -f "$KAMIGRAM_SRC/res/drawable/$icon.xml" ] || die "P60: нет иконки $icon.xml"
        cp -f "$KAMIGRAM_SRC/res/drawable/$icon.xml" "$RES_ROOT/drawable/$icon.xml"
    done
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_pro_patches.py" || die "P60: PRO-пакет применился не полностью"
    [ -f "$TG_DIR/MOD_PRO_FEATURES.txt" ] || die "P60: нет отчёта MOD_PRO_FEATURES.txt"
    PRO_COUNT=$(grep -c . "$TG_DIR/MOD_PRO_FEATURES.txt" || true)
    ok "P60 PRO-ПАКЕТ: применено пунктов — $PRO_COUNT (отчёт: MOD_PRO_FEATURES.txt)"
else
    skip "P60 PRO-пакет отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P70. MORE-ПАКЕТ: отсечки трафика (имена сверяются с исходниками Telegram),
#      умный менеджер загрузок, экономия по умолчанию, мелочи интерфейса.
#      Отчёт: MOD_MORE_FEATURES.txt.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_more_patches.py" || die "P70: MORE-пакет применился не полностью"
    [ -f "$TG_DIR/MOD_MORE_FEATURES.txt" ] || die "P70: нет отчёта MOD_MORE_FEATURES.txt"
    MORE_COUNT=$(grep -c . "$TG_DIR/MOD_MORE_FEATURES.txt" || true)
    ok "P70 MORE-ПАКЕТ: применено пунктов — $MORE_COUNT (отчёт: MOD_MORE_FEATURES.txt)"
else
    skip "P70 MORE-пакет отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P80. ЕДИНАЯ ТОЧКА ДИЗАЙНА 2026 (исправление прошлой сборки):
#      1) на каждом экране держится тёмная iOS-тема и применяются акценты
#         (ThemeHook.apply через Application.ActivityLifecycleCallbacks) —
#         поэтому больше нет «чёрного текста на чёрном фоне» и пропавших
#         названий чатов: палитру берём из assets, а код трогает только акценты;
#      2) иконка настроек — iOS-шестерёнка (настоящий ресурс, а не Drawable);
#      3) кэш: обычная очистка работает всегда, защита скачанного — по галочке;
#      4) новый центр мода: карточки, акценты, размеры кэша по категориям,
#         ID и ссылки, менеджер загрузок, прокси.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG"
    for f in ThemeHook KamiGramCenter KamiGramCache KamiGramConfig KamiGramSettings KamiGramTweaks KamiGramTraffic KamiGramDeleted KamiGramNetFilter; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P80: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done

    APP_LOADER="$JAVA_ROOT/org/telegram/messenger/ApplicationLoader.java"
    python3 "$KAMIGRAM_SRC/apply_theme_hook.py" "$APP_LOADER" || die "P80: не удалось подключить хук темы"
    grep -q "KAMIGRAM_THEME_HOOK" "$APP_LOADER" || die "P80: хук темы не найден в ApplicationLoader"
    ok "P80 единый дизайн: тёмная iOS-тема и акценты применяются на каждом экране"

    [ -f "$RES_ROOT/drawable/kamigram_ic_ios_settings.xml" ] \
        || die "P80: нет res/drawable/kamigram_ic_ios_settings.xml"
    ok "P80 иконка настроек: iOS-шестерёнка kamigram_ic_ios_settings подключена в меню"

else
    skip "P80 дизайн 2026 отключён (ZERO_TRAFFIC=0)"
fi


# =============================================================================
# P90. ВТОРОЙ БОЛЬШОЙ ПАКЕТ 2026: отправка по Enter из центра мода, плоские
#      вкладки без «стекла», применение твиков Telegram при старте (размер
#      текста, Enter, скрытие текста уведомлений, фон чата, счётчик трафика).
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_v2_patches.py" || die "P90: второй пакет применился не полностью"
    [ -f "$TG_DIR/MOD_P90_FEATURES.txt" ] || die "P90: нет отчёта MOD_P90_FEATURES.txt"
    P90_COUNT=$(grep -c . "$TG_DIR/MOD_P90_FEATURES.txt" || true)
    ok "P90 ВТОРОЙ ПАКЕТ 2026: применено пунктов — $P90_COUNT (отчёт: MOD_P90_FEATURES.txt)"
    for f in KamiGramTweaks KamiGramTraffic; do
        [ -f "$JAVA_ROOT/org/telegram/messenger/kamigram/$f.java" ] || die "P90: не скопирован $f.java"
    done
    ok "P90 классы мода на месте: KamiGramTweaks / KamiGramTraffic"

    # P95: правки по замечаниям пользователя — галочка своим каналам, ID под @,
    # фильтр рекламы, режим «только текст», журнал удалённых, свой статус в профиле
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_r43_patches.py" || die "P95: правки применились не полностью"
    [ -f "$TG_DIR/MOD_FEATURES_r43.txt" ] || die "P95: нет отчёта MOD_FEATURES_r43.txt"
    ok "P95 ПРАВКИ 2026: галочка моим каналам, ID под @username, фильтр рекламы, режим «только текст», журнал удалённых"

    # P97: пакет правок R50 — все замечания пользователя одним пакетом:
    # иконка призрака рядом с «тремя точками», честный онлайн, ID между
    # описанием и @username, имя приложения + статус прокси, свой .ttf-шрифт,
    # оптимизация и плавность, «Избранное» без скрепки.
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_r50_patches.py" || die "P97: правки R50 применились не полностью"
    [ -f "$TG_DIR/MOD_R50_FEATURES.txt" ] || die "P97: нет отчёта MOD_R50_FEATURES.txt"
    R50_COUNT=$(grep -c ' | ' "$TG_DIR/MOD_R50_FEATURES.txt" || true)
    ok "P97 ПРАВКИ R50: применено пунктов — $R50_COUNT (отчёт: MOD_R50_FEATURES.txt)"
else
    skip "P90 второй пакет отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P96. АККАУНТЫ: лимит расширен с 4 до 10 (переключатель аккаунтов в меню
#      и на экране входа), плюс копирование новых классов мода.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    UC="$JAVA_ROOT/org/telegram/messenger/UserConfig.java"
    if grep -q 'MAX_ACCOUNT_COUNT = 4;' "$UC"; then
        sed_i 's/MAX_ACCOUNT_COUNT = 4;/MAX_ACCOUNT_COUNT = 10;/' "$UC"
    fi
    grep -q 'MAX_ACCOUNT_COUNT = 10;' "$UC" || die "P96: не удалось расширить лимит аккаунтов"
    ok "P96 АККАУНТЫ: лимит расширен с 4 до 10 (можно держать 10 аккаунтов)"

    for f in KamiGramAds KamiGramVerified KamiGramTextOnly KamiGramUi KamiGramBuiltinProxy KamiGramDialog KamiGramFirstRun KamiGramSelfCheck KamiGramFont KamiGramOptimize KamiGramProxyStatus; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P96: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    ok "P96 новые классы мода на месте: реклама, галочка, «только текст», интерфейс, встроенные прокси, шрифт, оптимизация, статус прокси в шапке"
else
    skip "P96 отключено (ZERO_TRAFFIC=0)"
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
DESIGN_VERSION=KamiGram iOS 2026.2 (graphite, accent #0A84FF, no glass)
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

# =============================================================================
# P33. r54: призрак (иконка только в шапке главного экрана), имя KamiGram,
#      видимая иконка родного менеджера загрузок, удалённые сообщения остаются
#      в чате, свой шрифт — везде (сообщения, каналы, настройки).
# =============================================================================
python3 "$KAMIGRAM_SRC/apply_r54_patches.py" "$TG_DIR" "$APP_NAME" || die "P33: патчи r54 не применились"
grep -q 'KAMIGRAM_GHOST_HEADER' "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" || die "P33: иконка призрака не встала в шапку главного экрана"
grep -q 'KAMIGRAM_KEEP_DELETED_STORAGE' "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java" || die "P33: защита удалённых в базе не встала"
grep -q 'KAMIGRAM_FONT' "$JAVA_ROOT/org/telegram/ui/ActionBar/BaseFragment.java" || die "P33: шрифт не применяется ко всему экрану"
ok "P33 r54: призрак в шапке главного экрана, имя KamiGram, загрузки всегда видны, удалённые остаются в чате, шрифт везде"
