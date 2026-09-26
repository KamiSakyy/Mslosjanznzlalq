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
#     APP_NAME             название приложения (launcher label + UI)   [Sakura]
#     APP_PACKAGE          applicationId (свой, чтобы ставилось рядом с Telegram)
#     APP_VERSION_SUFFIX   суффикс версии                               [пусто]
#     ABIS                 какие ABI собирать (урезает время сборки)     [arm64-v8a]
#     BRAND_STRINGS        1 = имя мода во всём UI, 0 = только label     [1]
#     DISABLE_UPDATER      1 = не проверять обновления (нужно для мода)  [1]
#     DISABLE_BILLING      1 = выключить Google Play Billing            [0]
#     USE_CCACHE           1 = кэшировать нативную сборку через ccache  [1]
#     AUTODOWNLOAD_OFF     1 = автоскачивание медиа выключено по умолчанию [1]
#     NO_STICKERS          1 = блокировать стикеры/премиум-эмодзи (обычные медиа не трогать) [1]
#     MAX_ECONOMY          1 = принудительный power-saver (анимации/автоплей off) [1]
#     RES_CONFIGS          какие локали оставить в APK ("ru,en" | "all")   [ru,en]
#     SLIM_HEAVY           заглушки тяжёлых Lottie-анимаций: 1 | all | 0   [1]
#     IOS_THEME            legacy flag; ignored, only original Telegram themes [0]
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
# r66/r67: имя Sakura в шапке не пропадает при прокси, нет ложной анимации загрузки,
#         одноразовые фото не удаляются, удалённые в личных чатах остаются, папки — белый текст
#         (см. kamigram/apply_r66_patches.py, секция P98 ниже)
TG_DIR=${TG_DIR:-telegram-src}
APP_NAME=${APP_NAME:-Sakura}
APP_PACKAGE=${APP_PACKAGE:-com.kami.gram}
APP_VERSION_SUFFIX=${APP_VERSION_SUFFIX-}   # r106: пусто — версия без слова «mod»
ABIS=${ABIS:-arm64-v8a}
BRAND_STRINGS=${BRAND_STRINGS:-1}
DISABLE_UPDATER=${DISABLE_UPDATER:-1}
DISABLE_BILLING=${DISABLE_BILLING:-0}
USE_CCACHE=${USE_CCACHE:-1}
# экономия трафика / размер
AUTODOWNLOAD_OFF=${AUTODOWNLOAD_OFF:-0}   # r101: автоскачивание как в оригинале (фото/видео/документы грузятся сами)
NO_STICKERS=${NO_STICKERS:-1}             # стикеры и премиум-эмодзи не загружаются вообще
MAX_ECONOMY=${MAX_ECONOMY:-0}             # power-saver (0 = анимации и плавность остаются)
RES_CONFIGS=${RES_CONFIGS:-ru,en}         # какие языки оставить в APK (all = все)
SLIM_HEAVY=${SLIM_HEAVY:-1}               # заглушки тяжёлых Lottie-анимаций: 1 | all | 0
PATCH_GS=${PATCH_GS:-1}                   # правка google-services.json под свой applicationId
IOS_THEME=${IOS_THEME:-0}                 # legacy switch ignored: stock Telegram themes only
FLAT_UI=${FLAT_UI:-0}                     # r101: узор чата остаётся оригинальным (фотообои выглядят как в Telegram)
AUTO_PROXY=${AUTO_PROXY:-1}               # ссылка на прокси активирует его сразу
DROP_APPINDEXING=${DROP_APPINDEXING:-1}   # вырезать Google App Indexing (меньше APK)
IOS_UI=${IOS_UI:-1}                       # НАСТОЯЩИЙ КОД: собственный iOS-интерфейс Sakura
GHOST_MODE=${GHOST_MODE:-1}               # уникальная функция: режим «невидимка»
NO_RESTRICTIONS=${NO_RESTRICTIONS:-1}     # уникальная функция: снять запреты защищённого контента
FIX_LOGIN=${FIX_LOGIN:-1}                   # фикс входа: обычный SMS вместо Google Play Integrity
SMART_PROXY=${SMART_PROXY:-1}             # прокси из буфера сам включается, мёртвый — сам выключается
ZERO_TRAFFIC=${ZERO_TRAFFIC:-1}           # traffic policy: stickers/premium emoji/GIFs only; ordinary media stays native
IOS_DESIGN=${IOS_DESIGN:-1}               # iOS-дизайн Sakura кодом (скругления, пилюля таба)
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
        python3 - "$LC" "$APP_NAME" <<'PY'
import io, re, sys
path, name = sys.argv[1], sys.argv[2]
src = io.open(path, encoding='utf-8').read()
src, count = re.subn(
    r'if \((?:res|stringRes) == R\.string\.AppName\) return "[^"]*"; /\* MSLGRAM_BRAND \*/',
    lambda match: 'if (' + ('stringRes' if 'stringRes' in match.group(0) else 'res') + ' == R.string.AppName) return "' + name + '"; /* MSLGRAM_BRAND */',
    src,
    count=1,
)
if count != 1:
    raise SystemExit('P3: existing branding marker shape not found')
io.open(path, 'w', encoding='utf-8').write(src)
PY
        ok "P3 брендинг UI обновлён на '$APP_NAME'"
    else
        ANCHOR='        String value = BuildVars.USE_CLOUD_STRINGS ? localizationExternal.getByResNameOrResId(ApplicationLoader.applicationContext, key, res) : null;'
        NEW_ANCHOR='    private String getStringV2(String key, @StringRes int stringRes, String fallback) {'
        NEW_ANCHOR_NULL='    private @Nullable String getStringV2(String key, @StringRes int stringRes, String fallback) {'
        if ! has "$LC" "$ANCHOR" && ! has "$LC" "$NEW_ANCHOR" && ! has "$LC" "$NEW_ANCHOR_NULL"; then
            die "P3: не нашёл точку внедрения в LocaleController.java (изменился upstream)"
        fi
        python3 - "$LC" "$APP_NAME" "$MARKER" <<'PY'
import sys, io
path, name, marker = sys.argv[1], sys.argv[2], sys.argv[3]
src = io.open(path, encoding='utf-8').read()
old = '        String value = BuildVars.USE_CLOUD_STRINGS ? localizationExternal.getByResNameOrResId(ApplicationLoader.applicationContext, key, res) : null;'
if old in src:
    anchor = old
else:
    anchor = '    private String getStringInternal(String key, String fallback, int res) {\n'
inject = '        if (res == R.string.AppName) return "%s"; %s\n' % (name, marker)
if marker not in src:
    old_anchor = '        String value = BuildVars.USE_CLOUD_STRINGS ? localizationExternal.getByResNameOrResId(ApplicationLoader.applicationContext, key, res) : null;'
    new_anchors = (
        '    private String getStringV2(String key, @StringRes int stringRes, String fallback) {',
        '    private @Nullable String getStringV2(String key, @StringRes int stringRes, String fallback) {'
    )
    if old_anchor in src:
        inject = '        if (res == R.string.AppName) return "%s"; %s\n' % (name, marker)
        idx = src.index(old_anchor)
        src = src[:idx] + inject + src[idx:]
    else:
        anchor = next((item for item in new_anchors if item in src), None)
        if anchor is None:
            raise SystemExit('P3: LocaleController branding anchor disappeared')
        inject = '        if (stringRes == R.string.AppName) return "%s"; %s\n' % (name, marker)
        idx = src.index(anchor) + len(anchor)
        src = src[:idx] + '\n' + inject + src[idx:]
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
    ok "P11 стикеры и premium-эмодзи блокируются; фото/видео/аудио/голосовые/кружочки/документы не затронуты"
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
        # KAMIGRAM_DEFAULT_MEDIA_POLICY_R101: LiteMode больше не форсится.
        # Прежняя маска гасила FLAG_CHAT_BACKGROUND (фотообои вообще перестали
        # грузиться) и FLAG_AUTOPLAY_* — отсюда жалоба «фотообои не грузят».
        # Размытие/«стекло» переключает сам пользователь в центре Sakura
        # (KamiGramOptimize.apply), а обои и автоплей остаются стоковыми.
        new_tail = ('        ' + marker + ' r101: LiteMode стоковый, фотообои и автоплей не гасятся */\n'
                    '        return value;\n'
                    '    }\n\n    private static int lastBatteryLevelCached = -1;')
        src = src.replace(old_tail, new_tail, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
PY
has "$LM" "KAMIGRAM_FLAT_SMOOTH" || die "P12: маркер не внедрён"
if sed -n '/KAMIGRAM_FLAT_SMOOTH/,/return value;/p' "$LM" | grep -q 'FLAG_CHAT_BACKGROUND'; then
    die "P12: фотообои снова гасятся форсом (FLAG_CHAT_BACKGROUND)"
fi
if [ "$MAX_ECONOMY" = "1" ]; then
    ok "P12 power-saver форсирован (опция): анимации/автоплей/частицы/blur — off"
else
    ok "P12 LiteMode стоковый: фотообои, автоплей видео/GIF и анимации не гасятся; blur — тумблер в центре Sakura"
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
        # CMake changed the initial flag order between Telegram releases;
        # remove debug info from every C/C++ flag assignment rather than
        # depending on one historical line shape.
        sed_i 's/ -g//g' "$JNICMAKE"
        if grep -qE 'CMAKE_(C|CXX)_FLAGS[^\n]*-g' "$JNICMAKE"; then
            die "P15: не удалось убрать -g из C/CXX flags"
        fi
        has "$JNICMAKE" 'CMAKE_CXX_FLAGS' || die "P15: CMAKE_CXX_FLAGS не найден"
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
# =============================================================================
# P16. ТОЛЬКО ОРИГИНАЛЬНЫЕ ТЕМЫ TELEGRAM.
#      Никаких новых .attheme-файлов, регистраций в Theme.java и
#      принудительной перекраски штатных Telegram-тем в итоговой сборке нет.
#      При повторном запуске удаляем только артефакт старого эксперимента.
# =============================================================================
ASSET_ROOT="$TG_DIR/TMessagesProj/src/main/assets"
THEME_JAVA="$TG_DIR/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java"
if [ -f "$ASSET_ROOT/kamigram.attheme" ]; then
    rm -f "$ASSET_ROOT/kamigram.attheme"
fi
if [ -f "$THEME_JAVA" ] && grep -q 'KAMIGRAM_THEME_REGISTRATION' "$THEME_JAVA"; then
    python3 - "$THEME_JAVA" <<'PY'
import io
import re
import sys

path = sys.argv[1]
src = io.open(path, encoding="utf-8").read()
src = re.sub(
    r'\n\s*/\* KAMIGRAM_THEME_REGISTRATION:.*?themesDict\.put\("KamiGram", themeInfo\);\n',
    "\n",
    src,
    count=1,
    flags=re.S,
)
io.open(path, "w", encoding="utf-8").write(src)
PY
fi
[ ! -e "$ASSET_ROOT/kamigram.attheme" ] || die "P16: custom attheme остался в assets"
if [ -f "$THEME_JAVA" ] && grep -q 'KAMIGRAM_THEME_REGISTRATION' "$THEME_JAVA"; then
    die "P16: регистрация custom theme осталась в Theme.java"
fi
if [ "$IOS_THEME" = "1" ]; then
    warn "P16: IOS_THEME устарел и игнорируется; в сборке остаются только оригинальные темы Telegram"
fi
ok "P16 stock themes only: родные темы Telegram сохранены, custom theme не регистрируется и не перекрашивает UI"

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
    "                            org.telegram.messenger.kamigram.KamiGramUi.notify(activity, proxySettings.getAddress() + \":\" + proxySettings.getPort() + \" \\u2014 SakuProxy\");\n"
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
# P20. Sakura iOS UI — НАСТОЯЩИЙ КОД (не тема):
#      свой класс конфигурации мода, плоский нижний таб-бар в стиле iOS
#      (собственные иконки, без «стекла»/размытия и без подложки-пилюли),
#      шеврон «назад» как в iOS вместо стрелки.
# =============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KAMIGRAM_SRC="$SCRIPT_DIR/kamigram"
JAVA_ROOT="$TG_DIR/TMessagesProj/src/main/java"
RES_ROOT="$TG_DIR/TMessagesProj/src/main/res"

# P2A. Переданный пользователем artwork — единая adaptive/round launcher icon.
#      Это существующее изображение из image-search, не генерация. Фон и
#      foreground разделены, pre-O fallback и android:roundIcon получают тот
#      же портрет на всех плотностях.
ARTWORK="$KAMIGRAM_SRC/kamigram_icon_artwork.jpg"
[ -s "$ARTWORK" ] || die "P2A: отсутствует существующий Emilia artwork"
ARTWORK_SHA=$(sha256sum "$ARTWORK" | cut -d' ' -f1)
[ "$ARTWORK_SHA" = "f475503d6adbad57b774d8252e3944b2e594712469f78499c926ea8e5921a7c7" ] \
    || die "P2A: artwork изменён — ожидается проверенный негенерированный источник"
python3 "$KAMIGRAM_SRC/apply_icon_art.py" "$RES_ROOT" "$ARTWORK" \
    || die "P2A: не удалось установить иконку artwork"
[ -f "$RES_ROOT/mipmap-anydpi-v26/ic_launcher.xml" ] || die "P2A: adaptive icon не создан"
has "$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml" "KAMIGRAM_ADAPTIVE_ICON" \
    || die "P2A: manifest не переключён на adaptive icon"
ok "P2A adaptive launcher icon: verified existing Emilia artwork + circular resources for mdpi…xxxhdpi"

if [ "$IOS_UI" = "1" ]; then
    [ -d "$KAMIGRAM_SRC" ] || die "P20: нет папки $KAMIGRAM_SRC с исходниками Sakura"

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
        '    // Sakura: iOS-style tab - flat, no glass and no selected pill,\n'
        '    // with its own Sakura vector icon (tinted like a regular tab).\n'
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
if marker in src or 'KAMIGRAM_IOS_TABS_BG' in src:
    # This patch is already present; keep the apply script idempotent.
    io.open(path, 'w', encoding='utf-8').write(src)
    sys.exit(0)

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
    '        /* KAMIGRAM_IOS_TABS_BG: flat iOS tab bar drawn by Sakura code */\n'
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
    ok "P20 КОД: плоская панель табов рисуется кодом Sakura, а ИКОНКИ ВКЛАДОК — родные Telegram (самодельные убраны)"
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
#      (API_ID_PUBLISHED_FLOOD). Тогда вход не проходит вообще. Sakura несёт
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

# 3) make the selected official key available to the login diagnostics.
#    Older helper revisions already have a text report; newer revisions keep
#    the UI deliberately quiet, so expose the same information as a lazy
#    in-memory method instead of requiring one historical report line.
src = io.open(helper_path, encoding='utf-8').read()
if 'KamiGramAuthKeys.describe()' not in src:
    anchor = '            text.append("SafetyNet key: ")'
    idx = src.find(anchor)
    if idx >= 0:
        line_start = src.rfind(chr(10), 0, idx) + 1
        src = (src[:line_start]
               + '            text.append("Telegram key: " ).append(KamiGramAuthKeys.describe()).append(" / in connection: " );\n'
               + src[line_start:])
    else:
        quiet_anchor = '    /** Show a short actionable login message without exposing technical diagnostics. */'
        if quiet_anchor not in src:
            sys.stderr.write('P26: no diagnostic insertion point in KamiGramProxyHelper\n')
            sys.exit(1)
        method = ('    /** Selected official API key, kept in memory for the login retry explanation. */\n'
                  '    public static String loginKeyDiagnostics() {\n'
                  '        return "Telegram key: " + KamiGramAuthKeys.describe()\n'
                  '            + " / in connection: " + KamiGramAuthKeys.connectionKey();\n'
                  '    }\n\n')
        src = src.replace(quiet_anchor, method + quiet_anchor, 1)
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
             '        /* KAMIGRAM_IOS_ACTIONBAR: flat iOS top bar drawn by Sakura code */\n'
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
    ok "P23 iOS-шапка: плоский фон без «стекла» рисуется кодом Sakura"
else
    skip "P23 плоская шапка не применяется (IOS_UI=0)"
fi

# =============================================================================
# P27. НУЛЕВОЙ ТРАФИК (КОД): стикеры, premium-эмодзи, GIF и реклама Premium
#      отсекаются ДО выхода в сеть - и сами запросы, и файлы (.tgs/.webm/.gif).
#      Фото, видео, аудио, голосовые, кружочки и обычные документы не фильтруем;
#      режим «призрак» по-прежнему локально обрабатывает read/typing/online.
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
        '                FileLog.d("Sakura: запрос не отправлен (экономия трафика) " + object);\n'
        '            }\n'
        '            return;\n'
        '        }\n')
    src = src.replace(anchor, guard, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
print('net filter installed')
PY
    has "$CM_PATH" "KAMIGRAM_NET_FILTER" || die "P27: сетевой фильтр не встал в ConnectionsManager"

    FL_PATH="$JAVA_ROOT/org/telegram/messenger/FileLoader.java"
    python3 - "$FL_PATH" <<'PY' || die "P27: не удалось включить узкий media-фильтр stickers/premium-emoji/GIF"
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
        '        /* ' + mark + ': только stickers, premium-emoji and GIF files are denied */\n'
        '        if (org.telegram.messenger.kamigram.KamiGramNetFilter.blockDownload(document, parentObject)) {\n'
        '            if (BuildVars.LOGS_ENABLED) {\n'
        '                FileLog.d("Sakura: файл не скачивается (экономия трафика) " + document);\n'
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
    ok "P27 НУЛЕВОЙ ТРАФИК: stickers/premium-emoji/GIF requests and files are denied; ordinary photo/video/audio/voice/round/document media stays native; Telegram Premium ads are filtered"
else
    skip "P27 нулевой трафик отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P28. ФУНКЦИИ МОДА В ИНТЕРФЕЙСЕ: «Настройки → Sakura: функции мода» со
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
           '        items.add(SettingCell.Factory.of(90, 0xFFC8A7FF, 0xFF7C5CFF, R.drawable.kamigram_ic_ios_settings, "Sakura", org.telegram.messenger.kamigram.KamiGramConfig.summary())); /* ' + mark + ' */\n')
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
    ok "P28 ФУНКЦИИ МОДА НА ЭКРАНЕ: в настройках появилась строка «Sakura: функции мода» со сводкой состояния и переключателями (призрак, стикеры, эмодзи, истории, iOS-дизайн, Premium-блоки); рекламные блоки Premium/Stars/TON скрыты"
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
#      записываются) — это уникальная функция Sakura; превью ссылок и
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
    python3 - "$LA_PATH" <<'PY' || die "P33: не удалось установить stock-theme compatibility hook"
import io, sys
path = sys.argv[1]
src = io.open(path, encoding='utf-8').read()
mark = 'KAMIGRAM_STOCK_THEME_COMPAT'
anchor = '        /* KAMIGRAM_STORIES_STEALTH: призрак для историй - просмотры не записываются */'
if mark not in src:
    idx = src.find(anchor)
    if idx < 0:
        sys.stderr.write('P33: не найдена точка запуска мода')
        sys.exit(1)
    line_end = src.find('\n', idx) + 1
    src = (src[:line_end]
           + '        /* ' + mark + ': legacy theme hook is a no-op; Telegram owns theme state */\n'
           + '        org.telegram.messenger.kamigram.KamiGramTheme.apply();\n'
           + src[line_end:])
    io.open(path, 'w', encoding='utf-8').write(src)
print('ios colors hooked')
PY
    has "$LA_PATH" "KAMIGRAM_STOCK_THEME_COMPAT" || die "P33: stock-theme compatibility hook не подключился"
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
#      3) кэш: автоматическая очистка отключена, ручная очистка остаётся
#         только в штатном CacheControlActivity Telegram;
#      4) новый центр мода: карточки, акценты, native-экран кэша,
#         ID и ссылки, менеджер загрузок, прокси.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG"
    for f in KamiGramCenter KamiGramCache KamiGramConfig KamiGramSettings KamiGramTweaks KamiGramTraffic KamiGramDeleted KamiGramNetFilter; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P80: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    # No apply_theme_hook.py call here by design: it used to repaint every
    # activity and is incompatible with the stock-theme-only requirement.
    ok "P80: native Telegram theme lifecycle untouched; Sakura helpers copied without recoloring"

    [ -f "$RES_ROOT/drawable/kamigram_ic_ios_settings.xml" ] \
        || die "P80: нет res/drawable/kamigram_ic_ios_settings.xml"
    ok "P80 иконка настроек: iOS-шестерёнка kamigram_ic_ios_settings подключена в меню"
else
    skip "P80 дизайн 2026 отключён (ZERO_TRAFFIC=0)"
fi


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

    for f in KamiGramAds KamiGramVerified KamiGramTextOnly KamiGramUi KamiGramBuiltinProxy KamiGramDialog KamiGramFirstRun KamiGramSelfCheck KamiGramFont KamiGramOptimize KamiGramProxyStatus KamiGramBuild; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P96: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    # номер сборки внутри приложения: в настройках видно, какая версия стоит
    BUILD_NUMBER=${GITHUB_RUN_NUMBER:-local}
    sed_i "s/public static final String NUMBER = \"local\";/public static final String NUMBER = \"r$BUILD_NUMBER\";/" "$KAMI_PKG/KamiGramBuild.java"
    has "$KAMI_PKG/KamiGramBuild.java" "r$BUILD_NUMBER" || die "P96: номер сборки не подставился"
    ok "P96 номер сборки в приложении: r$BUILD_NUMBER (видно в настройках, строка Sakura)"
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
DESIGN_VERSION=Sakura (stock Telegram themes only; no custom attheme or forced recolor)
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
# P34. r54: призрак (иконка только в шапке главного экрана), имя Sakura,
#      видимая иконка родного менеджера загрузок, удалённые сообщения остаются
#      в чате, свой шрифт — везде (сообщения, каналы, настройки).
# =============================================================================
python3 "$KAMIGRAM_SRC/apply_r54_patches.py" "$TG_DIR" "$APP_NAME" || die "P34: патчи r54 не применились"
grep -q 'KAMIGRAM_GHOST_HEADER' "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" || die "P34: иконка призрака не встала в шапку главного экрана"
grep -q 'KAMIGRAM_KEEP_DELETED_STORAGE' "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java" || die "P34: защита удалённых в базе не встала"
grep -q 'KAMIGRAM_FONT' "$JAVA_ROOT/org/telegram/ui/ActionBar/BaseFragment.java" || die "P34: шрифт не применяется ко всему экрану"
ok "P34 r54: призрак в шапке главного экрана, имя Sakura, загрузки всегда видны, удалённые остаются в чате, шрифт везде"

# =============================================================================
# P98. r66 — по жалобам пользователя:
#      1) имя Sakura в шапке не пропадает при подключённом прокси (оверлей
#         состояния соединения на главном экране запрещён, имя — настоящий текст);
#      2) нет ложной анимации «что-то скачивается», когда загрузок нет;
#      3) одноразовые и «исчезающие» фото не удаляются и не стираются из базы;
#      4) удалённые в ЛИЧНЫХ чатах тоже остаются (раньше только в каналах);
#      5) текст папок («Все», «Личные»…) — белый (раньше сливался с фоном).
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    python3 "$KAMIGRAM_SRC/apply_r66_patches.py" "$TG_DIR" "$APP_NAME" || die "P98: патчи r66 не применились"
    has "$JAVA_ROOT/org/telegram/ui/ActionBar/ActionBar.java" "KAMIGRAM_TITLE_LOCK" || die "P98: защита заголовка (прокси) не встала"
    has "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" "KAMIGRAM_TITLE_TEXT" || die "P98: имя Sakura текстом не встало"
    has "$JAVA_ROOT/org/telegram/ui/DownloadProgressIcon.java" "KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE" || die "P98: ложная анимация загрузки не убрана"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_KEEP_VIEWONCE_DELETE" || die "P98: одноразовые фото не защищены"
    has "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java" "KAMIGRAM_KEEP_VIEWONCE_MEDIA" || die "P98: медиа одноразовых не защищено в базе"
    ok "P98 r66: имя Sakura в шапке не пропадает, нет ложной анимации загрузки, одноразовые фото остаются, папки — белый текст"
else
    skip "P98 отключено (ZERO_TRAFFIC=0)"
fi


# =============================================================================
# P99. r68 — по новому списку пользователя:
#      1) призрак: отправка уходит через «Отложенные» (как в AyuGram), чтобы по
#         времени прихода сообщения нельзя было понять, когда мы были в сети;
#      2) авто-архив: чаты со «100+» непрочитанных сами уходят в архив;
#      3) у папок счётчик непрочитанных — нашего цвета, как у выбранной вкладки;
#      4) прокси: моментальное переключение на живой + смена прокси, если фото
#         перестали загружаться;
#      5) одноразовые и самоуничтожающиеся фото больше не исчезают («истёкшая
#         фотография» не появляется);
#      6) имя Sakura в шапке не пропадает, иконка загрузок в покое статичная.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG"
    cp -f "$KAMIGRAM_SRC/KamiGramAutoArchive.java" "$KAMI_PKG/KamiGramAutoArchive.java"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KamiGramAutoArchive" || die "P99: нет класса авто-архива"
    python3 "$KAMIGRAM_SRC/apply_r68_patches.py" "$TG_DIR" "$APP_NAME" || die "P99: патчи r68 не применились"
    # r68 deliberately keeps Telegram's native scheduleDate path unchanged;
    # the old synthetic auto-scheduler markers are no longer expected.
    has "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" "KAMIGRAM_AUTO_ARCHIVE" || die "P99: авто-архив не запускается"
    has "$JAVA_ROOT/org/telegram/ui/Components/FilterTabsView.java" "KAMIGRAM_TAB_UNREAD_COLOR" || die "P99: цвет счётчика у папок не встал"
    has "$JAVA_ROOT/org/telegram/ui/DownloadProgressIcon.java" "KAMIGRAM_NO_FAKE_DOWNLOAD_IDLE" || die "P99: покой иконки загрузок не встал"
    has "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java" "KAMIGRAM_KEEP_VIEWONCE_MEDIA2" || die "P99: медиа одноразовых не защищено"
    ok "P99 r68: native scheduleDate сохранён, авто-архив 100+, счётчик папок нашего цвета, фото не исчезают, иконка загрузок в покое статичная"
else
    skip "P99 отключено (ZERO_TRAFFIC=0)"
fi


# =============================================================================
# P100. r70 — по новому списку пользователя (большой пакет).
#      Overlay/PiP r70 удалён следующим пакетом r76: он больше не копируется
#      и не подключается. Остальные функции r70 остаются.
#      1)  исторический AsuMeo guard (совместимость прошлых патчей): r82
#          превращает его в no-op; developer link остаётся только в настройках;
#      3)  «Подключение…» как в оригинале: нет сети — надпись, есть — пропадает;
#      4)  иконка загрузок анимируется только пока реально идут байты;
#      5)  иконка призрака минималистичная, белая: контур / заполнена;
#      6)  меню сообщения: «Сгореть» (у собеседника) и «Прочитать», сгорающие
#          можно пересылать; «Пересылать без имени»;
#      7)  точечный буст мобильного интернета: нажатое медиа — все потоки,
#          остальные загрузки на паузу; лимиты очередей сняты;
#      8)  премиум разблокирован локально (весь премиум доступен);
#      9)  «Отправлять всегда HD» (по умолчанию вкл);
#      10) чаты — только оригинальные темы Telegram (свои темы/обои не трогаем);
#      11) «Применять Sakura ко всем аккаунтам» (по умолчанию вкл).
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG" "$RES_ROOT/drawable"
    # 1) весь актуальный код мода (r70: ChannelGuard / NetBoost; overlay удалён)
    for f in ThemeHook KamiGramCenter KamiGramCache KamiGramConfig KamiGramSettings KamiGramTweaks KamiGramTraffic KamiGramDeleted KamiGramNetFilter KamiGramGhost KamiGramSpeed KamiGramNetBoost KamiGramChannelGuard KamiGramAutoArchive KamiGramLog KamiGramVideoGestures KamiGramBulkSelector KamiGramDeleteMyMessages KamiGramChatSearch; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P100: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    # 1b) эти три класса вызываются из ChatActivity/PhotoViewer/PipVideoOverlay,
    #     которые патчат apply_video_gestures/apply_bulk_selection/
    #     apply_delete_my_messages. Без копирования javac падает с
    #     «cannot find symbol» ещё до Gradle-сборки APK.
    for f in KamiGramVideoGestures KamiGramBulkSelector KamiGramDeleteMyMessages; do
        has "$KAMI_PKG/$f.java" "class $f" || die "P100: $f не скопирован в дерево Telegram"
    done
    # 2) иконки: минималистичный призрак (контур/заполненный), «сгореть»
    for d in kamigram_ghost kamigram_ghost_on kamigram_burn; do
        cp -f "$KAMIGRAM_SRC/res/drawable/$d.xml" "$RES_ROOT/drawable/$d.xml" || die "P100: нет иконки $d"
    done
    # 3) патчи r70
    python3 "$KAMIGRAM_SRC/apply_r70_patches.py" "$TG_DIR" "$APP_NAME" || die "P100: патчи r70 не применились"
    python3 "$KAMIGRAM_SRC/apply_video_gestures.py" "$TG_DIR" || die "P100: жесты видеоплеера не применились"
    python3 "$KAMIGRAM_SRC/apply_bulk_selection.py" "$TG_DIR" || die "P100: массовый выбор сообщений не применился"
    python3 "$KAMIGRAM_SRC/apply_delete_my_messages.py" "$TG_DIR" || die "P100: удаление моих сообщений не применилось"
    has "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" "KAMIGRAM_CONNECTING_SUBTITLE" || die "P100: «Подключение…» как в оригинале не встало"
    (has "$JAVA_ROOT/org/telegram/ui/ActionBar/ActionBar.java" "KAMIGRAM_TITLE_LOCK_R70" \
        || has "$JAVA_ROOT/org/telegram/ui/ActionBar/ActionBar.java" "KAMIGRAM_TITLE_LOCK") \
        || die "P100: защита имени Sakura не встала"
    has "$JAVA_ROOT/org/telegram/ui/DownloadProgressIcon.java" "KAMIGRAM_DOWNLOAD_ANIM_LIVE" || die "P100: живая анимация загрузок не встала"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_CASE_BURN" || die "P100: «Сгореть/Прочитать» в меню не встало"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_FORWARD_EPHEMERAL" || die "P100: пересылка сгорающих не встала"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_FORWARD_NONAME" || die "P100: пересылка без имени не встала"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoaderPriorityQueue.java" "KAMIGRAM_NET_FOCUS_LOOP" || die "P100: фокус скорости в очередях не встал"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoadOperation.java" "KAMIGRAM_NET_FOCUS_PARAMS" || die "P100: буст потока фокусного файла не встал"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_NET_FOCUS_RECHECK" || die "P100: пересборка очередей (фокус) не встала"
    has "$JAVA_ROOT/org/telegram/tgnet/TLRPC.java" "KAMIGRAM_PREMIUM" || die "P100: локальный премиум не встал"
    has "$JAVA_ROOT/org/telegram/messenger/MediaController.java" "KAMIGRAM_SEND_HD" || die "P100: «всегда HD» не встал"
    has "$KAMI_PKG/KamiGramChannelGuard.java" "KAMIGRAM_CHANNEL_GATE_R81"         || die "P100: no-op ChannelGuard compatibility marker отсутствует"
    ok "P100 r70: overlay/PiP отключён, ChannelGuard оставлен no-op, «Подключение…» оригинальное, живые загрузки, сгореть/прочитать, буст скорости, локальный премиум, всегда HD, ко всем аккаунтам"
else
    skip "P100 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P101. r76 — cleanup и критические исправления по новому запросу:
#      overlay/PiP/пузырёк/разрешение overlay удалены полностью;
#      ghost перенесён из шапки в меню «⋮»;
#      spinner загрузок стартует только после движения байтов;
#      custom-прокси и скрытый каталог SakuProxy независимы;
#      локальные Premium-цвета и фон переживают refresh/restart.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG" "$RES_ROOT/drawable"

    for f in KamiGramConfig KamiGramCenter KamiGramGhost KamiGramChannelGuard KamiGramBuiltinProxy KamiGramProxyPower KamiGramProxyHelper KamiGramPremiumState KamiGramLog; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P101: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    for d in kamigram_ghost kamigram_ghost_on kamigram_burn; do
        [ -f "$KAMIGRAM_SRC/res/drawable/$d.xml" ] || die "P101: нет иконки $d"
        cp -f "$KAMIGRAM_SRC/res/drawable/$d.xml" "$RES_ROOT/drawable/$d.xml"
    done
    # Удаляем артефакты старого r70 даже при повторном применении поверх старого дерева.
    rm -f "$KAMI_PKG/KamiGramFloat.java" "$RES_ROOT/drawable/kamigram_float.xml"

    python3 "$KAMIGRAM_SRC/apply_r76_patches.py" "$TG_DIR" || die "P101: патчи r76 не применились"

    has "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" "KAMIGRAM_GHOST_OVERFLOW_R76" || die "P101: призрак не перенесён в меню «⋮»"
    if grep -R -n -E 'KAMIGRAM_FLOAT|KamiGramFloat|KamiGramGhost\.addHeaderItem' "$JAVA_ROOT" "$RES_ROOT" >/dev/null 2>&1; then
        die "P101: в исходниках осталась функциональность overlay/PiP"
    fi
    has "$JAVA_ROOT/org/telegram/ui/DownloadProgressIcon.java" "KAMIGRAM_DOWNLOAD_STATIC_IDLE_R76" || die "P101: idle-анимация загрузок не исправлена"
    has "$JAVA_ROOT/org/telegram/messenger/SharedConfig.java" "KAMIGRAM_PROXY_CATALOG_R76" || die "P101: каталог SakuProxy не защищён"
    has "$KAMI_PKG/KamiGramBuiltinProxy.java" "KAMIGRAM_PROXY_CATALOG_REENTRANT_R76" || die "P101: каталог SakuProxy зацикливает loadProxyList"
    has "$JAVA_ROOT/org/telegram/messenger/SharedConfig.java" "KAMIGRAM_PROXY_DELETE_GUARD_R76" || die "P101: custom-прокси могут удалить встроенные"
    has "$JAVA_ROOT/org/telegram/ui/ProxyListActivity.java" "KAMIGRAM_PROXY_SCREEN_EMPTY_R76" || die "P101: включение SakuProxy без custom не работает"
    has "$JAVA_ROOT/org/telegram/messenger/UserConfig.java" "KAMIGRAM_PREMIUM_RESTORE_R76" || die "P101: Premium-оформление не восстанавливается"
    has "$JAVA_ROOT/org/telegram/ui/PeerColorActivity.java" "KAMIGRAM_PREMIUM_SAVE_R76" || die "P101: Premium-оформление не сохраняется"
    [ -f "$KAMI_PKG/KamiGramPremiumState.java" ] || die "P101: нет локального Premium-хранилища"
    ok "P101 r76: старая плавающая пузырь-кнопка удалена, призрак в меню «⋮», idle-иконка загрузок статична, custom и SakuProxy независимы с быстрым fallback, Premium-фон сохраняется"
else
    skip "P101 r76 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P102. r77 — по новому запросу пользователя:
#      1) «Настроить прокси >» скрывается только после подтверждённого
#         Connected/Updating и возвращается при потере связи;
#      2) self-destruct media скачиваются, сохраняются и пересылаются как обычные
#         медиа, без FLAG_SECURE;
#      3) «Прочитать» использует иконку глаза;
#      4) открытие файла ускоряет его, но не ставит остальные загрузки на паузу;
#      5) ротация выбирает самый быстрый живой SakuProxy и не трогает live custom;
#      6) очищается только архив: unread > 500, боты блокируются и удаляются,
#         каналы/группы покидаются, личные/контакты защищены.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG"
    for f in KamiGramProxyPower KamiGramBuiltinProxy KamiGramNetFilter KamiGramTextOnly KamiGramAutoArchive; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P102: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    python3 "$KAMIGRAM_SRC/apply_r77_patches.py" "$TG_DIR" || die "P102: патчи r77 не применились"

    has "$JAVA_ROOT/org/telegram/ui/ActionBar/ActionBar.java" "KAMIGRAM_PROXY_OVERLAY_R77" || die "P102: stale proxy overlay не исправлен"
    has "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" "KAMIGRAM_PROXY_REFRESH_HEALTHY_R77" || die "P102: offline refresh заголовка не исправлен"
    has "$KAMI_PKG/KamiGramProxyPower.java" "KAMIGRAM_CONNECTION_HEALTHY_R77" || die "P102: состояние живого proxy не отслеживается"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoaderPriorityQueue.java" "KAMIGRAM_NET_NO_PAUSE_R77" || die "P102: открытие медиа всё ещё ставит очередь на паузу"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_EPHEMERAL_IMAGE_LOAD_R77" || die "P102: ephemeral-фото блокируются text-only"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_EPHEMERAL_ACTIONS_R77" || die "P102: save/share действия ephemeral не добавлены"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_READ_EYE_R77" || die "P102: иконка «Прочитать» не заменена на глаз"
    has "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java" "KAMIGRAM_EPHEMERAL_FORWARD_UPLOAD_R77" || die "P102: ephemeral forwarding не переводится в upload"
    has "$JAVA_ROOT/org/telegram/ui/SecretMediaViewer.java" "KAMIGRAM_SECRET_VIEWER_SECURE_R77" || die "P102: secure flag SecretMediaViewer не условный"
    has "$JAVA_ROOT/org/telegram/ui/PhotoViewer.java" "KAMIGRAM_PHOTO_VIEWER_EPHEMERAL_SCREENSHOT_R77" || die "P102: secure flag PhotoViewer не условный"
    has "$JAVA_ROOT/org/telegram/ui/SecretVoicePlayer.java" "KAMIGRAM_SECRET_VOICE_SCREENSHOT_R77" || die "P102: secure flag SecretVoicePlayer не условный"
    has "$JAVA_ROOT/org/telegram/ui/Cells/ChatMessageCell.java" "KAMIGRAM_EPHEMERAL_CELL_SCREENSHOT_R77" || die "P102: secure flag one-time cell не условный"
    has "$JAVA_ROOT/org/telegram/messenger/ProxyRotationController.java" "KAMIGRAM_PROXY_ROTATION_R77" || die "P102: native proxy rotation не защищён"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KAMIGRAM_ARCHIVE_CLEAN_R77" || die "P102: archive cleaner r77 не скопирован"
    ok "P102 r77: overlay/fallback/queue исправлены, self-destruct media сохраняются и пересылаются, screenshot разрешён, глаз и stock-theme cleanup >500"
else
    skip "P102 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P103. r78 — обычные медиа снова работают как в Telegram:
#      1) video/photo/audio/voice/round/document never stop at the economy gate;
#         only stickers, premium emoji and GIFs remain deny-able;
#      2) the selected proxy route is leased while an ordinary message is sent;
#      3) the old AsuMeo auto-join marker is retained only for compatibility;
#         r82 removes its live gate; no dialog-list sponsor row is injected;
#      4) archive safety explicitly protects private dialogs and contacts;
#      5) every folder tab, including «Все личные», uses the Sakura palette.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    python3 "$KAMIGRAM_SRC/apply_r78_patches.py" "$TG_DIR" || die "P103: патчи r78 не применились"
    if grep -q -E 'Theme\.(applyTheme|setColor)|KAMIGRAM_FOLDER_(SURFACE|SELECTOR|COLORS)_R78' "$KAMIGRAM_SRC/ThemeHook.java" "$KAMIGRAM_SRC/apply_r78_patches.py"; then
        die "P103: forced recolor of native Telegram themes is still enabled"
    fi

    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_PROXY_SEND_GUARD_R78" || die "P103: отправка сообщения не защищена от proxy-ротации"
    has "$KAMI_PKG/KamiGramNetFilter.java" "KAMIGRAM_MEDIA_POLICY_R78" || die "P103: обычные медиа всё ещё проходят старый фильтр"
    has "$KAMI_PKG/KamiGramTextOnly.java" "KAMIGRAM_MEDIA_POLICY_R78" || die "P103: text-only блокирует нажатые медиа"
    has "$KAMI_PKG/KamiGramProxyPower.java" "KAMIGRAM_PROXY_SEND_GUARD_R78" || die "P103: proxy send lease отсутствует"
    has "$KAMI_PKG/KamiGramProxyPower.java" "KAMIGRAM_PROXY_SEND_FINISH_R78" || die "P103: proxy send lease не освобождается после ответа"
    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_PROXY_SEND_FINISH_R78" || die "P103: отправка не освобождает proxy lease"
    has "$KAMI_PKG/KamiGramBuiltinProxy.java" "KAMIGRAM_PROXY_SEND_GUARD_R78" || die "P103: builtin route может переключиться во время отправки"
    has "$KAMI_PKG/KamiGramChannelGuard.java" "KAMIGRAM_AUTO_JOIN_R78" || die "P103: автоматическая подписка AsuMeo отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KAMIGRAM_ARCHIVE_SAFETY_R78" || die "P103: archive safety marker отсутствует"
    ok "P103 r78: обычные медиа не блокируются, GIF/sticker/premium-emoji ограничены, proxy send lease, AsuMeo compatibility marker, archive safety >500; цвета папок оставлены Telegram"
else
    skip "P103 r78 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P104. r80 — instant native Telegram behaviour:
#      1) no Ghost/Scheduled rewrite: text, media and forwards go out now;
#      2) protected channels/chats expose Forward and use copy/upload delivery;
#      3) the old keep-deleted journal is disabled so Delete removes immediately;
#      4) fixed proxy send lease is replaced by a real request-token guard;
#      5) protected/one-time media keep native save/share actions;
#      6) the launcher uses the generated Sakura Emilia + Telegram icon.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    python3 "$KAMIGRAM_SRC/apply_r80_patches.py" "$TG_DIR" || die "P104: патчи r80 не применились"

    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_INSTANT_SEND_R80" || die "P104: отправка всё ещё может уйти в отложенные"
    has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_FORWARD_RESTRICTIONS_R80" || die "P104: меню защищённых сообщений не разблокировано"
    has "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java" "KAMIGRAM_PROTECTED_FORWARD_COPY_R80" || die "P104: защищённая пересылка не использует copy/upload"
    has "$JAVA_ROOT/org/telegram/messenger/MessageObject.java" "KAMIGRAM_FORWARD_RESTRICTIONS_R80_MESSAGE_OBJECT" || die "P104: MessageObject всё ещё запрещает пересылку"
    has "$KAMI_PKG/KamiGramDeleted.java" "KAMIGRAM_NATIVE_DELETE_R80" || die "P104: старый фильтр удаления не отключён"
    has "$KAMI_PKG/KamiGramConfig.java" "KAMIGRAM_INSTANT_SEND_R80" || die "P104: legacy auto-schedule всё ещё может включиться"
    has "$KAMI_PKG/KamiGramCenter.java" "KAMIGRAM_NATIVE_DELETE_R80" || die "P104: legacy keep-deleted control всё ещё показан"
    has "$KAMI_PKG/KamiGramProxyPower.java" "KAMIGRAM_INSTANT_SEND_R80" || die "P104: фиксированный proxy send lease остался активным"
    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_INSTANT_PROXY_ROUTE_R80" || die "P104: route stability не привязана к реальному request token"

    APP_RES_ROOT="$TG_DIR/TMessagesProj_App/src/main/res"
    # P2A already rendered the checked-in Emilia artwork into the actual
    # Telegram resource tree. Mirror every legacy, round, adaptive foreground,
    # and any-density descriptor into the application module so aapt/resource
    # shrinking cannot fall back to Telegram's original launcher.
    for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
        for icon_name in ic_launcher.png ic_launcher_round.png kamigram_icon_foreground.png; do
            [ -f "$RES_ROOT/mipmap-$density/$icon_name" ] \
                || die "P104: generated icon is missing ($density/$icon_name)"
            mkdir -p "$APP_RES_ROOT/mipmap-$density"
            cp -f "$RES_ROOT/mipmap-$density/$icon_name" "$APP_RES_ROOT/mipmap-$density/$icon_name"
        done
    done
    mkdir -p "$APP_RES_ROOT/mipmap-anydpi-v26" "$APP_RES_ROOT/drawable"
    for icon_xml in ic_launcher.xml ic_launcher_round.xml; do
        [ -f "$RES_ROOT/mipmap-anydpi-v26/$icon_xml" ] \
            || die "P104: adaptive descriptor is missing ($icon_xml)"
        cp -f "$RES_ROOT/mipmap-anydpi-v26/$icon_xml" "$APP_RES_ROOT/mipmap-anydpi-v26/$icon_xml"
    done
    [ -f "$RES_ROOT/drawable/kamigram_icon_background.xml" ] \
        || die "P104: adaptive icon background is missing"
    cp -f "$RES_ROOT/drawable/kamigram_icon_background.xml" \
        "$APP_RES_ROOT/drawable/kamigram_icon_background.xml"

    ICON_MANIFEST="$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml"
    ICON_CONFIG_RELEASE="$TG_DIR/TMessagesProj/config/release/AndroidManifest.xml"
    ICON_CONFIG_SDK23="$TG_DIR/TMessagesProj/config/release/AndroidManifest_SDK23.xml"
    ICON_CONFIG_STANDALONE="$TG_DIR/TMessagesProj/config/release/AndroidManifest_standalone.xml"
    python3 - "$ICON_MANIFEST" "$ICON_CONFIG_RELEASE" "$ICON_CONFIG_SDK23" "$ICON_CONFIG_STANDALONE" <<'PYICON' || die "P104: не удалось подключить иконку Sakura"
import io
import os
import re
import sys

main_path = sys.argv[1]
extra_paths = sys.argv[2:]
marker = "KAMIGRAM_LAUNCHER_R80"

def patch_application(source):
    pattern = re.compile(r'(<application\b[^>]*)(>)', re.DOTALL)
    match = pattern.search(source)
    if not match:
        raise RuntimeError("application tag not found")
    tag = match.group(1)
    tag = re.sub(r'\s+android:icon="[^"]*"', '', tag)
    tag = re.sub(r'\s+android:roundIcon="[^"]*"', '', tag)
    tag += '\n        android:icon="@mipmap/ic_launcher_round"'
    tag += '\n        android:roundIcon="@mipmap/ic_launcher_round"'
    return source[:match.start()] + tag + match.group(2) + source[match.end():]

def patch_default_alias(source):
    pattern = re.compile(
        r'(<activity-alias\b(?=[^>]*android:name="org\.telegram\.messenger\.DefaultIcon")[^>]*)(/?>)',
        re.DOTALL,
    )
    match = pattern.search(source)
    if not match:
        raise RuntimeError("DefaultIcon alias not found")
    tag = match.group(1)
    tag = re.sub(r'\s+android:icon="[^"]*"', '', tag)
    tag = re.sub(r'\s+android:roundIcon="[^"]*"', '', tag)
    tag += '\n            android:icon="@mipmap/ic_launcher_round"'
    patched = source[:match.start()] + tag + match.group(2) + source[match.end():]
    if marker not in patched:
        patched = patched.replace('<activity-alias', '        <!-- %s: existing Emilia artwork -->\n        <activity-alias' % marker, 1)
    return patched

source = io.open(main_path, encoding="utf-8").read()
source = patch_application(source)
source = patch_default_alias(source)
io.open(main_path, "w", encoding="utf-8").write(source)

for path in extra_paths:
    if not os.path.isfile(path):
        continue
    text = io.open(path, encoding="utf-8").read()
    text = patch_application(text)
    io.open(path, "w", encoding="utf-8").write(text)
PYICON
    grep -q "KAMIGRAM_LAUNCHER_R80" "$ICON_MANIFEST" || die "P104: иконка не прописалась в DefaultIcon"
    grep -q "@mipmap/ic_launcher_round" "$ICON_CONFIG_SDK23" || die "P104: afat SDK23 manifest не использует фирменную иконку"
    [ -f "$RES_ROOT/mipmap-xxxhdpi/ic_launcher_round.png" ] || die "P104: ресурс фирменной иконки отсутствует"
    [ -f "$APP_RES_ROOT/mipmap-xxxhdpi/ic_launcher_round.png" ] || die "P104: ресурс фирменной иконки не попал в application module"
    ok "P104 r80: мгновенные send/forward/delete, protected copy-upload, save/share protected media, proxy с real request-token stability без artificial lease, фирменная Emilia/Sakura иконка"
else
    skip "P104 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P105. r81 — hidden reliability/speed pass:
#      1) focus download starts without the queue debounce and never pauses an
#         already active background operation;
#      2) ordinary sends use only the native scheduleDate path, with no Ghost /
#         Scheduled rewrite or callback timer;
#      3) proxy request leases are account-safe and released by the exact native
#         token on response, exception, retry, or cancellation;
#      4) the cleaner and its private/contact safety markers are present;
#      5) the former AsuMeo gate remains only as a compatibility marker (r82
#         replaces its live call with a no-op; the link is settings-only);
#      6) Telegram's native theme lifecycle remains the only theme path.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    for f in KamiGramConfig KamiGramCenter ThemeHook KamiGramSpeed KamiGramNetBoost KamiGramNetFilter KamiGramProxyPower KamiGramChannelGuard KamiGramAutoArchive; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P105: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    [ -f "$KAMIGRAM_SRC/apply_r81_patches.py" ] || die "P105: нет apply_r81_patches.py"
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_r81_patches.py" || die "P105: патчи r81 не применились"

    has "$JAVA_ROOT/org/telegram/messenger/FileLoaderPriorityQueue.java" "KAMIGRAM_QUEUE_IMMEDIATE_R81" || die "P105: очередь загрузок всё ещё ждёт debounce"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoaderPriorityQueue.java" "KAMIGRAM_NO_ACTIVE_PAUSE_R81" || die "P105: фокус ставит активные загрузки на паузу"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_QUEUE_RECHECK_IMMEDIATE_R81" || die "P105: focus recheck не мгновенный"
    has "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java" "KAMIGRAM_INSTANT_SEND_R81" || die "P105: live send всё ещё проходит через legacy Ghost hook"
    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_REQUEST_ACCOUNT_R81" || die "P105: proxy lease не привязан к аккаунту"
    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_PROXY_ROUTE_PARSE_FAIL_R81" || die "P105: proxy lease зависает при ошибке парсинга"
    has "$KAMI_PKG/KamiGramChannelGuard.java" "KAMIGRAM_CHANNEL_GATE_R81" || die "P105: compatibility marker AsuMeo отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KAMIGRAM_ARCHIVE_CLEAN_R77" || die "P105: cleaner marker отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KAMIGRAM_ARCHIVE_SAFETY_R78" || die "P105: private/contact safety marker отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KAMIGRAM_ARCHIVE_THRESHOLD_R82" || die "P105: strict >500 cleaner marker отсутствует"
    has "$KAMI_PKG/KamiGramNetFilter.java" "KAMIGRAM_MEDIA_POLICY_R81" || die "P105: media filter может блокировать обычные emoji/media"
    ok "P105 r81: instant downloads/send, account-safe proxy guard, cleaner safety markers, no-op-compatible AsuMeo gate, native Telegram themes only"
else
    skip "P105 r81 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P106. r82 — critical send/gate/cleanup recovery:
#      1) remove the last r81 onRealSend hook and leave Telegram's native
#         SendMessagesHelper scheduleDate/send path untouched;
#      2) let only ordinary message requests bypass the local economy/Ghost
#         gates, while the sticker/premium-emoji/GIF filter remains installed;
#      3) remove the mandatory AsuMeo call and run the >500 cleaner on resume;
#      4) remove the legacy permanent sponsor row from the ordinary dialogs list;
#         the developer channel is exposed only in Sakura settings;
#      5) keep the independent built-in SakuProxy catalog, including akenai.tg.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    [ -f "$KAMIGRAM_SRC/apply_r82_patches.py" ] || die "P106: нет apply_r82_patches.py"
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_r82_patches.py" || die "P106: патчи r82 не применились"

    # Native send/schedule path: no Ghost scheduler, callback, timer, or
    # scheduleDate rewrite can survive in the live message entry points.
    has "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java" "KAMIGRAM_NATIVE_SEND_R82" || die "P106: native send marker отсутствует"
    if grep -q -E 'KamiGramGhost\.(onRealSend|autoScheduleDate)|KAMIGRAM_AUTO_SCHEDULE' "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java" "$JAVA_ROOT/org/telegram/ui/ChatActivity.java"; then
        die "P106: live Ghost/Scheduled hook остался в send/forward path"
    fi
    if grep -q 'sendMessageParams.scheduleDate *=' "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java"; then
        die "P106: SendMessageParams.scheduleDate переписывается"
    fi
    grep -q 'int scheduleDate = sendMessageParams.scheduleDate' "$JAVA_ROOT/org/telegram/messenger/SendMessagesHelper.java" || die "P106: native scheduleDate path не найден"

    # Request gates: the condition must be scoped to the message classifier,
    # not replaced by a global removal of KamiGramNetFilter/Ghost.
    grep -q 'if (!kamigramMessageRequest && !kamigramPushRequest && org.telegram.messenger.kamigram.KamiGramNetFilter.blockRequest(object))' "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" || die "P106: message-only NetFilter bypass отсутствует"
    grep -q 'if (!kamigramMessageRequest && !kamigramPushRequest && org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete))' "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" || die "P106: message-only Ghost bypass отсутствует"
    grep -q 'KamiGramNetFilter.blockDownload(document, parentObject)' "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" || die "P106: sticker/GIF download filter отключён"
    has "$KAMI_PKG/KamiGramNetFilter.java" "KAMIGRAM_MEDIA_POLICY_R78" || die "P106: media policy marker отсутствует"

    # No subscription gate or auto-join is allowed. Auto-cleanup is explicit
    # on resume in addition to its own event/periodic sweep.
    has "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" "KAMIGRAM_CHANNEL_GATE_R82_DISABLED" || die "P106: AsuMeo gate marker отсутствует"
    grep -q 'KamiGramAutoArchive.checkNow(currentAccount)' "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" || die "P106: auto-cleanup on resume отсутствует"
    if grep -q 'KamiGramChannelGuard.check' "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java"; then
        die "P106: LaunchActivity всё ещё вызывает subscription gate"
    fi
    if grep -q -E 'joinChannel|ImportChatInvite|CHANNEL_USERNAME.*join|setCancelable\(false\)' "$KAMI_PKG/KamiGramChannelGuard.java"; then
        die "P106: обязательный auto-join/gate остался в ChannelGuard"
    fi

    # Cleaner: strict >500, normal and archive groups/channels, private users
    # and contacts untouched, and a real inputPeerUser for leaving.
    has "$KAMI_PKG/KamiGramAutoArchive.java" "KAMIGRAM_ARCHIVE_THRESHOLD_R82" || die "P106: strict unread threshold отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "dialog.isFolder" || die "P106: folder safety отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "getInputPeer(selfUser)" || die "P106: TL_inputPeerUser leave peer отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "deleteParticipantFromChat" || die "P106: native leave mechanics отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "ContactsController.getInstance(account).isContact" || die "P106: contacts safety отсутствует"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "!user.bot" || die "P106: private user safety отсутствует"
    if grep -q 'folder_id != ARCHIVE_FOLDER_ID' "$KAMI_PKG/KamiGramAutoArchive.java"; then
        die "P106: cleaner снова ограничен архивом"
    fi

    # Built-in proxy catalog remains independent from user proxy records.
    grep -q 'server=akenai.tg&port=853&secret=ee54ce330e4690cc297d2b031ff3f288b06d742e616b656e61692e636c69636b' "$KAMI_PKG/KamiGramBuiltinProxy.java" || die "P106: akenai.tg SakuProxy отсутствует"
    has "$KAMI_PKG/KamiGramBuiltinProxy.java" "KAMIGRAM_PROXY_CATALOG_R76" || die "P106: built-in proxy catalog marker отсутствует"

    # The old sponsor view must not be copied or injected. The developer link
    # is a settings-only action and the normal dialog adapter remains native.
    if grep -q -E 'KamiGramSponsorCell|KAMIGRAM_ASUMEO_SPONSOR' "$JAVA_ROOT/org/telegram/ui/Adapters/DialogsAdapter.java"; then
        die "P106: legacy sponsor row остался в DialogsAdapter"
    fi
    has "$KAMI_PKG/KamiGramCenter.java" "Разработчик Sakura" || die "P106: developer settings action отсутствует"
    has "$KAMI_PKG/KamiGramChannelGuard.java" "https://t.me/AsuMeo" || die "P106: developer URL отсутствует"

    ok "P106 r82: native Telegram send/schedule path restored, message/push request bypass, stickers/premium-emoji/GIF filter preserved, AsuMeo gate removed, >500 groups/channels cleaner active, akenai.tg SakuProxy catalogued, legacy sponsor row removed"
else
    skip "P106 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P107. БЕЗОБРЫВНЫЕ ФОНОВЫЕ ВИДЕО-ЗАГРУЗКИ:
#       P102-P106 are already reserved by the r77-r82 compatibility passes.
#       This pass layers resumable FileLoader recovery and the Android foreground
#       lifetime on top of the latest native Telegram/send/proxy patches.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG"
    for f in KamiGramDownloadRecovery KamiGramDownloadService; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P107: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done

    # DrKLO moved ProxySettings from org.telegram.proxy to
    # org.telegram.utils.proxy after the pinned 12.10.3 tree. Keep the source
    # files on the modern package and adapt only the copied target classes, so
    # both the pinned workflow ref and current master compile.
    if [ -f "$JAVA_ROOT/org/telegram/utils/proxy/ProxySettings.java" ]; then
        KAMIGRAM_PROXY_SETTINGS_PACKAGE="org.telegram.utils.proxy"
    else
        KAMIGRAM_PROXY_SETTINGS_PACKAGE="org.telegram.proxy"
    fi
    KAMIGRAM_PROXY_SETTINGS_PACKAGE="$KAMIGRAM_PROXY_SETTINGS_PACKAGE" python3 - "$KAMI_PKG" <<'PY' || die "P107: ProxySettings package не согласован с upstream"
import io
import os
import re
import sys

root = sys.argv[1]
package = os.environ["KAMIGRAM_PROXY_SETTINGS_PACKAGE"]
for name in ("KamiGramBuiltinProxy.java", "KamiGramProxyHelper.java", "KamiGramProxyPower.java"):
    path = os.path.join(root, name)
    if not os.path.isfile(path):
        continue
    text = io.open(path, encoding="utf-8").read()
    text = re.sub(
        r"import org\.telegram\.(?:utils\.)?proxy\.ProxySettings;",
        "import %s.ProxySettings;" % package,
        text,
    )
    io.open(path, "w", encoding="utf-8").write(text)
PY
    for f in KamiGramBuiltinProxy KamiGramProxyHelper KamiGramProxyPower; do
        grep -q "import ${KAMIGRAM_PROXY_SETTINGS_PACKAGE}\\.ProxySettings;" "$KAMI_PKG/$f.java" || die "P107: $f использует несовместимый ProxySettings package"
    done
    python3 "$KAMIGRAM_SRC/apply_download_resilience.py" "$TG_DIR" || die "P107: безобрывная загрузка не применилась"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoadOperation.java" "KAMIGRAM_PROXY_REBIND_OPERATION" || die "P107: операция не умеет продолжать загрузку после proxy switch"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_PROXY_RETRY_DELEGATE" || die "P107: retry загрузки после proxy switch не встал"
    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_PROXY_SWITCH_HOOK" || die "P107: proxy switch не переподключает загрузки"
    has "$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml" "KAMIGRAM_DOWNLOAD_SERVICE_MANIFEST" || die "P107: download foreground service не объявлен"
    grep -q 'android.permission.FOREGROUND_SERVICE' "$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml" || die "P107: foreground service permission отсутствует"
    grep -q 'android.permission.FOREGROUND_SERVICE_DATA_SYNC' "$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml" || die "P107: dataSync foreground permission отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramDownloadService.java" "FOREGROUND_MIN_BYTES" || die "P107: порог foreground 10 МБ отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramDownloadService.java" "START_NOT_STICKY" || die "P107: service не должен переживать завершённую загрузку"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramDownloadService.java" "stopForeground(true)" || die "P107: foreground lifetime не освобождается"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramDownloadService.java" "setProgress" || die "P107: в уведомлении нет полосы прогресса"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_DOWNLOAD_SERVICE_OPERATION" || die "P107: сервис не привязан к native operation/size"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_DOWNLOAD_SERVICE_EXISTING_OPERATION_R83" || die "P107: preload-to-download lifecycle не покрыт"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_ACTIVE_DOWNLOADS_API_R83" || die "P107: active FileLoader API отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoader.java" "KAMIGRAM_DOWNLOAD_CANCEL_OPERATION_STATE_R83" || die "P107: native cancel не освобождает lifecycle"
    has "$JAVA_ROOT/org/telegram/messenger/FileLoaderPriorityQueue.java" "KAMIGRAM_MAX_PARALLEL_DOWNLOADS_R83" || die "P107: лимит шести параллельных операций отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramDownloadRecovery.java" "KAMIGRAM_THERMAL_LIMIT_R83" || die "P107: thermal/power-save limit отсутствует"
    ok "P107: native FileLoader продолжает temp/parts после proxy switch, foreground только для >10 МБ с прогрессом и idle-stop, до 6 native операций с thermal/power-save ограничением"
else
    skip "P107 безобрывные фоновые скачивания отключены (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P108. r83 — archive/developer/push cleanup and legacy sponsor removal.
#      This is deliberately the final compatibility pass: it can also strip a
#      sponsor row from a tree produced by an older r82 installer.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    [ -f "$KAMIGRAM_SRC/apply_r83_patches.py" ] || die "P108: нет apply_r83_patches.py"
    TG_DIR="$TG_DIR" python3 "$KAMIGRAM_SRC/apply_r83_patches.py" "$TG_DIR" || die "P108: патчи r83 не применились"
    has "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" "KAMIGRAM_ARCHIVE_HIDE_BUTTON_R83" || die "P108: кнопка «Скрыть архив» отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramCenter.java" "Разработчик Sakura" || die "P108: кнопка разработчика отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramNetFilter.java" "KAMIGRAM_PUSH_SAFE_R83" || die "P108: FCM-safe classifier отсутствует"
    has "$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramGhost.java" "KAMIGRAM_PUSH_GHOST_SAFE_R83" || die "P108: Ghost не пропускает push lifecycle"
    has "$JAVA_ROOT/org/telegram/tgnet/ConnectionsManager.java" "KAMIGRAM_PUSH_NATIVE_BYPASS_R83" || die "P108: push native bypass отсутствует"
    if grep -q -E 'KamiGramSponsorCell|KAMIGRAM_ASUMEO_SPONSOR|Спонсор SakuProxy' "$JAVA_ROOT/org/telegram/ui/Adapters/DialogsAdapter.java"; then
        die "P108: permanent sponsor row всё ещё попадает в основной список"
    fi
    has "$KAMI_PKG/KamiGramProxyPower.java" "KAMIGRAM_PROXY_WATCH_ACTIVE_ONLY_R83" || die "P108: proxy watchdog не ограничен active downloads"
    has "$KAMI_PKG/KamiGramBuiltinProxy.java" "onDownloadActivityChanged" || die "P108: built-in proxy timer не останавливается"
    has "$KAMI_PKG/KamiGramAutoArchive.java" "event/resume driven" || die "P108: auto-archive оставил постоянный timer"
    ok "P108 r83: «Скрыть архив» работает, developer link только в настройках, sponsor row удалён, штатный FCM/push path защищён, proxy/watchdog idle, custom и built-in proxy каталоги независимы"
else
    skip "P108 r83 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P109. r94 — media-cache safety. AutoDeleteMediaTask вызывается из
#      LaunchActivity при старте/resume, поэтому его no-op обязан быть
#      безусловным. Native CacheControlActivity/FileLoader остаётся единственным
#      destructive path и получает полный список выбранных файлов.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    AUTO_DELETE="$JAVA_ROOT/org/telegram/messenger/AutoDeleteMediaTask.java"
    FILE_LOADER="$JAVA_ROOT/org/telegram/messenger/FileLoader.java"
    CACHE_CENTER="$JAVA_ROOT/org/telegram/messenger/kamigram/KamiGramCenter.java"
    MESSAGES_STORAGE="$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java"
    has "$AUTO_DELETE" "KAMIGRAM_CACHE_NO_AUTO_CLEANUP_R94" || die "P109: AutoDeleteMediaTask no-op marker отсутствует"
    if grep -q 'KamiGramCache.keep' "$AUTO_DELETE"; then
        die "P109: AutoDeleteMediaTask всё ещё зависит от галочки Sakura"
    fi
    if grep -q -E 'lastKeepMediaCheckTime|SharedPreferences.*cache_limit|Utilities\.clearDir' "$AUTO_DELETE"; then
        die "P109: автоматическая очистка по сроку/лимиту/sticker cache осталась"
    fi
    if grep -q -E 'KAMIGRAM_PROTECT_DOWNLOAD|KAMIGRAM_PROTECT_TAG|KamiGramCache\.isProtected|KamiGramCache\.protectByName' "$FILE_LOADER"; then
        die "P109: protected-file filter блокирует штатную очистку FileLoader"
    fi
    if grep -q -E 'KamiGramCache\.(clear|clearAll|freeMemory)' "$CACHE_CENTER"; then
        die "P109: центр Sakura содержит собственную очистку кэша"
    fi
    has "$CACHE_CENTER" "openCacheSettings" || die "P109: центр не открывает штатный CacheControlActivity"
    if grep -q 'KAMIGRAM_CLEANUP_SAFE' "$MESSAGES_STORAGE"; then
        die "P109: custom MessagesStorage cleanup guard вмешивается в native lifecycle"
    fi
    ok "P109 r94: media auto-cleanup полностью отключён независимо от настройки, temp/parts/resume не трогаются на startup, очистка оставлена штатному Telegram CacheControlActivity"
else
    skip "P109 r94 отключено (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P111. r101 — БРЕНД SAKURA ВНУТРИ ПРИЛОЖЕНИЯ И «SAKURA КАНАЛ».
#      Пользователь: «внутри приложения название должно быть Sakura, а не
#      Telegram» и «кнопку "Возможности Telegram" переименуй в "Sakura канал",
#      по нажатию открывай канал @AsuMeo».
#      Два слоя:
#        1) strings.xml всех локалей — видимый текст переписан (ссылки, домены,
#           authority и идентификаторы не трогаются);
#        2) KamiGramBranding в LocaleController — облачные языковые пакеты
#           Telegram применяются поверх ресурсов уже после старта, поэтому
#           строка фильтруется и в рантайме.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    mkdir -p "$KAMI_PKG"
    for f in KamiGramBranding KamiGramUploads; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P111: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    python3 "$KAMIGRAM_SRC/apply_branding.py" "$TG_DIR/TMessagesProj/src/main" || die "P111: бренд Sakura не применился"
    has "$JAVA_ROOT/org/telegram/messenger/LocaleController.java" "KamiGramBranding.localize" || die "P111: облачные переводы могут вернуть «Telegram»"
    has "$JAVA_ROOT/org/telegram/ui/SettingsActivity.java" "KamiGramBranding.featuresTitle" || die "P111: строка «Sakura канал» не подставлена"
    has "$JAVA_ROOT/org/telegram/ui/SettingsActivity.java" "KamiGramBranding.openChannelInApp" || die "P111: «Sakura канал» не открывается внутри приложения"
    has "$RES_ROOT/values/strings.xml" '<string name="TelegramFeaturesUrl">https://t.me/AsuMeo</string>' || die "P111: URL канала не заменён"
    if grep -q '>Telegram<' "$RES_ROOT/values/strings.xml"; then
        die "P111: в ресурсах осталось видимое название Telegram"
    fi
    ok "P111 r101: внутри приложения бренд Sakura (ресурсы + облачные переводы), «Sakura канал» открывает https://t.me/AsuMeo"
else
    skip "P111 бренд отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P112. r101 — ОТПРАВКА ФАЙЛОВ/ФОТО/ВИДЕО >10 МБ В ФОНЕ ЧЕРЕЗ СЕРВИС.
#      Пользователь: «отправка файлов, фото, видео и т.д. больше 10 МБ тоже
#      должна идти в фоне через сервис с уведомлением, как загрузка».
#      Все отправки Telegram (фото, видео, кружочки, голосовые, документы)
#      проходят через FileLoader.FileLoaderDelegate, реализация которого живёт
#      в ImageLoader — там и ставится учёт KamiGramUploads. Нативный
#      FileUploadOperation по-прежнему сам режет файл на части, повторяет
#      запросы и умеет resume; сервис только держит процесс живым, показывает
#      прогресс и останавливается сам, когда крупных отправок не осталось.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    for f in KamiGramUploads KamiGramDownloadService; do
        [ -f "$KAMIGRAM_SRC/$f.java" ] || die "P112: нет $KAMIGRAM_SRC/$f.java"
        cp -f "$KAMIGRAM_SRC/$f.java" "$KAMI_PKG/$f.java"
    done
    python3 "$KAMIGRAM_SRC/apply_upload_service.py" "$JAVA_ROOT" || die "P112: учёт крупных отправок не применился"
    has "$JAVA_ROOT/org/telegram/messenger/ImageLoader.java" "KamiGramUploads.onProgress" || die "P112: прогресс отправки не доходит до сервиса"
    has "$KAMI_PKG/KamiGramDownloadService.java" "ensureStartedForLargeUpload" || die "P112: сервис не поднимается для крупных отправок"
    has "$KAMI_PKG/KamiGramDownloadService.java" "stat_sys_upload" || die "P112: у уведомления отправки нет своей иконки"
    has "$KAMI_PKG/KamiGramUploads.java" "FOREGROUND_MIN_BYTES" || die "P112: порог 10 МБ для отправки отсутствует"
    has "$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml" "KAMIGRAM_DOWNLOAD_SERVICE_MANIFEST" || die "P112: foreground service не объявлен"
    ok "P112 r101: отправка фото/видео/файлов >10 МБ идёт в фоне через foreground-сервис с прогрессом и сама останавливается"
else
    skip "P112 фоновая отправка отключена (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P113. r101 — ПРОВЕРКА ПОЛИТИКИ ЗАГРУЗКИ ПО УМОЛЧАНИЮ.
#      По умолчанию НЕ грузятся только стикеры, премиум-эмодзи и истории
#      (истории — свой отдельный тумблер). Фотообои, аватары (включая видео- и
#      эмодзи-аватары), фото, видео, документы, аудио и GIF идут штатно.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    SC="$JAVA_ROOT/org/telegram/messenger/SharedConfig.java"
    DCC="$JAVA_ROOT/org/telegram/messenger/DownloadController.java"
    has "$SC" 'fastWallpaperDisabled", false' || die "P113: фотообои снова отключены по умолчанию"
    has "$SC" 'streamMedia", true' || die "P113: стриминг медиа выключен по умолчанию"
    has "$SC" 'saveStreamMedia", true' || die "P113: стриминговое медиа не сохраняется"
    has "$SC" 'direct_share", true' || die "P113: шеринг снова без аватарок и превью"
    has "$SC" 'inappCamera", true' || die "P113: встроенная камера снова выключена"
    has "$SC" 'keep_media", CacheByChatsController.KEEP_MEDIA_FOREVER' || die "P113: скачанное снова удаляется по сроку"
    has "$KAMI_PKG/KamiGramNetFilter.java" "KAMIGRAM_STORIES_TOGGLE_R101" || die "P113: истории не отключаются отдельным тумблером"
    has "$KAMI_PKG/KamiGramNetFilter.java" "KAMIGRAM_AVATAR_SAFE_R101" || die "P113: фильтр может блокировать аватары и фотообои"
    has "$KAMI_PKG/KamiGramConfig.java" "KAMIGRAM_DEFAULT_MEDIA_POLICY_R101" || die "P113: дефолты медиа-политики не на месте"
    has "$DCC" "KAMIGRAM_NO_STORIES_PRELOAD" || die "P113: предзагрузка историй не подчиняется тумблеру"
    has "$DCC" "KAMIGRAM_NO_GIF_AUTO_R101" || die "P113: GIF блокируются без тумблера"
    if [ "$FLAT_UI" != "1" ]; then
        pattern_size=$(wc -c < "$RES_ROOT/raw/default_pattern.svg" 2>/dev/null || echo 0)
        [ "$pattern_size" -gt 1000 ] || die "P113: узор чата подменён заглушкой — фотообои выглядят сломанными"
    fi
    ok "P113 r101: по умолчанию не грузятся только стикеры, премиум-эмодзи и истории; обои, аватары, фото, видео, GIF — штатно"
else
    skip "P113 проверка медиа-политики отключена (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P114. r101 — ГЛОБАЛЬНЫЙ ПОИСК БЕЗ ОГРАНИЧЕНИЙ + ФИЛЬТРЫ ФОТО/ВИДЕО/GIF.
#      Жалоба: «глобальный поиск не работает, ищет только из существующих;
#      глобальный поиск всегда приоритет, даже 1 буква — сразу результат;
#      добавь фильтры (только фото и т.д., по #), бесконечная лента фото;
#      убери любые ограничения».
#      Причины и лечение:
#        1) TL_contacts_search (глобальный поиск людей/каналов) блокировался
#           фильтром «часто используемые» — убран из блок-листа, и весь поиск
#           внесён в белый список KamiGramNetFilter (не блокируется ничем);
#        2) нативные вкладки «Каналы / Боты / Посты / Публичные посты» и
#           медиа-фильтры прятались при dialogsCount <= 10 и выключенных
#           историях — теперь доступны всегда;
#        3) добавлены отдельные фильтры «Фото», «Видео», «GIF»
#           (бесконечная сетка медиа, поиск по всем каналам);
#        4) лимиты 20 → 100 (searchGlobal/messages) и 20 → 50 (contacts),
#           задержка поиска 300 мс → 0 (отклик с первого символа).
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    python3 "$KAMIGRAM_SRC/apply_search_power.py" "$TG_DIR/TMessagesProj/src/main" || die "P114: глобальный поиск не разблокирован"
    grep -q '"TL_contacts_search"' "$KAMI_PKG/KamiGramNetFilter.java" && die "P114: глобальный поиск людей всё ещё блокируется фильтром"
    has "$KAMI_PKG/KamiGramNetFilter.java" "KAMIGRAM_SEARCH_NO_LIMITS_R101" || die "P114: белый список поиска отсутствует"
    has "$JAVA_ROOT/org/telegram/ui/DialogsActivity.java" "return onlySelect;" || die "P114: вкладки и фильтры поиска всё ещё прячутся"
    has "$JAVA_ROOT/org/telegram/ui/Adapters/FiltersView.java" "TL_inputMessagesFilterPhotos" || die "P114: фильтр «Фото» не добавлен"
    has "$JAVA_ROOT/org/telegram/ui/Components/SearchViewPager.java" "item.filterIndex = 7;" || die "P114: вкладки новых фильтров не добавлены"
    has "$RES_ROOT/values/strings.xml" "SakuraPhotosFilter" || die "P114: нет названий новых фильтров"
    has "$RES_ROOT/values-ru/strings.xml" "SakuraPhotosFilter" || die "P114: нет русских названий фильтров"
    grep -q '}, 300);' "$JAVA_ROOT/org/telegram/ui/Adapters/DialogsSearchAdapter.java" && die "P114: задержка поиска 300 мс не убрана"
    ok "P114 r101: глобальный поиск без ограничений и всегда приоритет (1 символ → сразу результат), вкладки Каналы/Боты/Посты/Публичные посты видны всегда, фильтры Фото/Видео/GIF с бесконечной лентой, лимиты 100/50, отклик мгновенный"
else
    skip "P114 поиск отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P115. r101 — ИКОНКА SAKURA ВЕЗДЕ ВМЕСТО ИКОНКИ TELEGRAM.
#      Жалоба: «иконка приложения должна быть везде, замени оригинальную иконку
#      телеграм на нашу во всех местах». Лаунчер заменён ранее (P2A); здесь
#      заменяются остальные оригинальные телеграм-ассеты: статус-бар
#      (notification), крупная иконка уведомлений/VoIP (ic_launcher_dr),
#      логотип в правилах (logo_middle), самолётик входа (intro_tg_plane),
#      «пригласить в Telegram» (menu_invit_telegram), book_logo, menu_intro,
#      menu_feature_intro. Форматы и размеры файлов сохраняются 1-в-1.
# =============================================================================
if [ -f "$KAMIGRAM_SRC/kamigram_icon_artwork.jpg" ]; then
    python3 "$KAMIGRAM_SRC/apply_icons_everywhere.py" "$KAMIGRAM_SRC/kamigram_icon_artwork.jpg" "$RES_ROOT" || die "P115: иконки не заменены"
    for asset in notification ic_launcher_dr logo_middle intro_tg_plane menu_invit_telegram book_logo menu_intro menu_feature_intro; do
        ls "$RES_ROOT"/drawable*/$asset.* >/dev/null 2>&1 || die "P115: пропал ассет $asset"
    done
    ok "P115 r101: иконка Sakura во всех местах вместо иконки Telegram (статус-бар, уведомления, вход, логотипы, меню)"
else
    skip "P115 нет исходника арта"
fi

# =============================================================================
# P116. r104 — ВИДЕО ПОВЕРХ ПРИЛОЖЕНИЙ: приоритет overlay + запрос разрешения.
#      Жалоба: «видео не работает поверх приложений, только картинка в картинке,
#      куда делось разрешение поверх приложений?». Разбор: PipVideoOverlay
#      переходит в режим «только внутри приложения», если выдано лишь системное
#      PiP-разрешение, а штатный диалог запроса overlay не показывался никогда.
#      Теперь: overlay выдано -> плавающее окно НАД всеми приложениями;
#      не выдано -> один раз показываем системный диалог «поверх других
#      приложений»; в центре Sakura есть пункт «Видео поверх приложений»
#      со ссылкой на системную страницу разрешения и статусом.
#      Кэш (жалоба «перезашёл - кэш сбросился / фейковый сброс»): автоочистка
#      отсутствует безусловно (r94), cleanupInternal и FileLoader.deleteFiles
#      родные, в центре Sakura нет фейковых кнопок очистки - только честный
#      размер и ссылка на родной экран очистки Telegram.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    KAMI_PKG="$JAVA_ROOT/org/telegram/messenger/kamigram"
    python3 "$KAMIGRAM_SRC/apply_r104_fixes.py" "$TG_DIR/TMessagesProj/src/main" || die "P116: overlay-режим видео не починен"
    has "$JAVA_ROOT/org/telegram/ui/PhotoViewer.java" "KAMIGRAM_OVERLAY_POWER_R104" || die "P116: нет overlay-приоритета в PhotoViewer"
    has "$KAMI_PKG/KamiGramCenter.java" "overlayPromptShown" || die "P116: нет флага запроса overlay"
    has "$KAMI_PKG/KamiGramCenter.java" "openOverlaySettings" || die "P116: в центре нет пункта «Видео поверх приложений»"
    has "$JAVA_ROOT/org/telegram/messenger/AutoDeleteMediaTask.java" "KAMIGRAM_CACHE_NO_AUTO_CLEANUP_R94" || die "P116: автоочистка кэша не отключена"
    ! grep -q "KAMIGRAM_CLEANUP_SAFE: чистка базы" "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java" || die "P116: фейковый cleanup guard жив"
    ok "P116 r104: видео поверх приложений работает (overlay-приоритет + запрос разрешения + пункт в центре), кэш не сбрасывается сам и чистится только как в оригинальном TG"
else
    skip "P116 overlay отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P117. r105 — ПЛАВАЮЩЕЕ ОКНО ВМЕСТО СИСТЕМНОГО PiP + БЕЗ ЖЕСТОВ-ПОМЕХ.
#      Жалобы: «при выходе из ТГ всё равно картинка в картинке с интерфейсом,
#      а должно быть только видео поверх приложений»; «убери яркость/громкость
#      и перемотку у видео — невозможно перетаскивать плавающее окно»;
#      «почему просит включить, если уже включено».
#      Лечение: системный PiP пропускается, когда overlay выдано и открыто видео
#      (работает только PipVideoOverlay); модовые зоны яркости/громкости и
#      long-press перемотка удалены из PhotoViewer и PipVideoOverlay; центр
#      Sakura проверяет статус разрешения в момент нажатия.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    python3 "$KAMIGRAM_SRC/apply_r105_fixes.py" "$TG_DIR/TMessagesProj/src/main" || die "P117: плавающее окно/жесты не починены"
    has "$JAVA_ROOT/org/telegram/ui/LaunchActivity.java" "KAMIGRAM_OVERLAY_ONLY_R105" || die "P117: системный PiP не пропускается при overlay"
    ! grep -q "KamiGramVideoGestures.handle" "$JAVA_ROOT/org/telegram/ui/PhotoViewer.java" || die "P117: зоны яркости/громкости живы в PhotoViewer"
    ! grep -q "KamiGramVideoGestures.handle" "$JAVA_ROOT/org/telegram/ui/Components/PipVideoOverlay.java" || die "P117: зоны яркости/громкости живы в плавающем окне"
    ! grep -q "startRewind(videoPlayer" "$JAVA_ROOT/org/telegram/ui/PhotoViewer.java" || die "P117: перемотка долгим нажатием жива"
    ok "P117 r105: при выходе из ТГ — только плавающее окно поверх приложений (без системного PiP), жесты яркости/громкости/перемотки убраны, окно перетаскивается, центр не просит уже выданное"
else
    skip "P117 отключён (ZERO_TRAFFIC=0)"
fi

# =============================================================================
# P118. r106 — разрешение «поверх других окон», сгорающие медиа, чистка настроек.
python3 "$KAMIGRAM_SRC/apply_r106_fixes.py" "$TG_DIR" || die "P118: r106 fixes"
MANIFEST="$TG_DIR/TMessagesProj/src/main/AndroidManifest.xml"
grep -q 'SYSTEM_ALERT_WINDOW' "$MANIFEST" \
    || die "P118: в манифесте нет разрешения «поверх других окон»"
grep -q 'KAMIGRAM_KEEP_TTL_MEDIA_R106' "$JAVA_ROOT/org/telegram/messenger/MessagesController.java" \
    || die "P118: локальный таймер сгорающих медиа не отключён"
for banned in "Запретить скриншоты" "Темы оформления" "Открыть список прокси" \
              "Показать скрытую рекламу" "Рекламные посты" "Реклама и рекомендации" \
              "Кэш сейчас" "Показать архив" "Видео поверх приложений" "Sakura канал"; do
    ! grep -q "$banned" "$CACHE_CENTER" || die "P118: в центре остался пункт «$banned»"
done
grep -q 'сборка k1' "$KAMIGRAM_SRC/KamiGramBuild.java" \
    || die "P118: подпись сборки не k1"
grep -q 'openChannelInApp' "$KAMIGRAM_SRC/KamiGramBranding.java" \
    || die "P118: канал не открывается внутри приложения"
ok "P118 r106: overlay-разрешение, сгорающие медиа, чистка центра"

# P119. r107 — жёсткая защита APK (R8, obfuscation-словари) и фикс массового выбора.
python3 "$KAMIGRAM_SRC/apply_r107_hardening.py" "$TG_DIR" || die "P119: защита APK не применилась"
has "$TG_DIR/TMessagesProj_App/build.gradle" "proguard-sakura.pro" || die "P119: правила Sakura не подключены к release"
grep -q "minifyEnabled true" "$TG_DIR/TMessagesProj_App/build.gradle" || die "P119: R8-минификация отключена"
grep -q "shrinkResources true" "$TG_DIR/TMessagesProj_App/build.gradle" || die "P119: resource shrink отключён"
grep -q "enableR8.fullMode=true" "$TG_DIR/gradle.properties" || die "P119: R8 full mode отключён"
has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_BULK_SELECTION_SHOW" || die "P119: массовый выбор не показывает тулбар"
has "$KAMI_PKG/KamiGramBulkSelector.java" "matchesFilter" || die "P119: в массовом выборе нет фильтров по типам"
ok "P119 r107: R8/обфускация подключены, массовый выбор показывает тулбар выделения"

# P120. r109 — сгорающие медиа можно пересылать/сохранять + «Поиск Sakura» в чате.
python3 "$KAMIGRAM_SRC/apply_r109_fixes.py" "$TG_DIR" || die "P120: блокировки сгорающих медиа не сняты"
python3 "$KAMIGRAM_SRC/apply_chat_search.py" "$TG_DIR" || die "P120: «Поиск Sakura» не добавился"
has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_TTL_MEDIA_R109" || die "P120: меню сгорающих медиа не разблокировано"
has "$JAVA_ROOT/org/telegram/ui/PhotoViewer.java" "KAMIGRAM_TTL_GALLERY_R109" || die "P120: галерея сгорающих медиа не разблокирована"
has "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" "KAMIGRAM_CHAT_SEARCH_ACTION" || die "P120: пункт «Поиск Sakura» не встал"
has "$KAMI_PKG/KamiGramChatSearch.java" "FilteredSearchView" || die "P120: фрагмент поиска не скопирован"
ok "P120 r109: сгорающие медиа пересылаются/сохраняются, серверный «Поиск Sakura» в чате с фильтрами"

# P121. r111 — аудио/музыка кэш больше не очищается автоматически.
#       Закрыты ВСЕ пути создания локальных TTL-задач (createTaskForMid,
#       createTaskForSecretMedia, createTaskForSecretChat, toTask-ветка
#       markMessagesContentAsRead), выключен их исполнитель (getNewTask),
#       а старые задачи из enc_tasks_v4 одноразово удаляются при открытии БД.
#       Очистка кэша — только штатными настройками Telegram (CacheControlActivity).
python3 "$KAMIGRAM_SRC/apply_r111_fixes.py" "$TG_DIR" || die "P121: автоудаление медиа-кэша не выключено"
has "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java" "KAMIGRAM_ENC_TASKS_PURGE_R111" || die "P121: чистка старых enc_tasks не встала"
N111=$(grep -c "KAMIGRAM_TTL_NO_LOCAL_TASKS_R111" "$JAVA_ROOT/org/telegram/messenger/MessagesStorage.java")
[ "$N111" -ge 4 ] || die "P121: закрыты не все пути TTL-задач (найдено $N111 маркеров)"
grep -q "openSearchWithText.*KAMIGRAM_CHAT_SEARCH_ACTION" "$JAVA_ROOT/org/telegram/ui/ChatActivity.java" || die "P121: «Поиск Sakura» не переведён на штатный поиск"
grep -q "KAMIGRAM_PROXY_CATALOG_R111" "$KAMI_PKG/KamiGramBuiltinProxy.java" || die "P121: новые встроенные прокси не добавлены"
grep -q "cardBackground()" "$KAMI_PKG/KamiGramBulkSelector.java" || die "P121: компактный диалог массового выбора не встал"
ok "P121 r111: медиа-кэш не удаляется сам, «Поиск Sakura» открывает штатный серверный поиск, +4 скрытых прокси, компактный диалог массового выбора"

# P110. r95 — статическая проверка символов перед Gradle.
#      javac падал с «cannot find symbol» уже после 15 минут сборки, потому что
#      P100-патчи вставляли вызовы классов Sakura, а сами классы в дерево не
#      копировались. Здесь проверяем, что каждый класс
#      org.telegram.{messenger,ui.Components}.kamigram.*, на который ссылается
#      пропатченное дерево, реально лежит в дереве, и что у каждого
#      статического вызова Kami*.method() есть объявление. Дёшево и до Gradle.
# =============================================================================
if [ "$ZERO_TRAFFIC" = "1" ]; then
    python3 - "$JAVA_ROOT" <<'PYSYM' || die "P110: найдены недостающие символы Sakura — javac упадёт с cannot find symbol"
import collections
import io
import os
import re
import sys

root = sys.argv[1]
pkg_re = re.compile(r'org\.telegram\.(?:messenger|ui\.Components)\.kamigram\.([A-Za-z_][A-Za-z0-9_]*)')
call_re = re.compile(r'\b(Kami[A-Za-z0-9_]*|ThemeHook)\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(')
# Package-private declarations count too, so the modifier list is optional.
decl_re = re.compile(
    r'^[ \t]*(?:(?:public|protected|private|static|final|synchronized|abstract|native|default)\s+)*'
    r'[\w<>\[\],\.\s]+?\b(\w+)\s*\(',
    re.MULTILINE,
)
field_re = re.compile(
    r'^[ \t]*(?:(?:public|protected|private|static|final|volatile|transient)\s+)+'
    r'[\w<>\[\],\.]+\s+(\w+)\s*[=;]',
    re.MULTILINE,
)

refs = collections.defaultdict(set)
present = {}
calls = collections.defaultdict(set)

for dirpath, _dirs, files in os.walk(root):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(dirpath, name)
        source = io.open(path, encoding="utf-8", errors="replace").read()
        if os.sep + "kamigram" in dirpath + os.sep:
            present[name[:-5]] = path
            members = set(decl_re.findall(source)) | set(field_re.findall(source))
            present[name[:-5]] = (path, members)
        for symbol in pkg_re.findall(source):
            refs[symbol].add(path)
        for klass, member in call_re.findall(source):
            calls[(klass, member)].add(path)

problems = []
for symbol in sorted(refs):
    if symbol not in present:
        for path in sorted(refs[symbol]):
            problems.append("класс %s не скопирован в дерево, но используется в %s"
                            % (symbol, os.path.relpath(path, root)))
for (klass, member) in sorted(calls):
    if klass not in present:
        continue
    _path, members = present[klass]
    if member not in members:
        for path in sorted(calls[(klass, member)]):
            problems.append("%s.%s() не объявлен, вызов в %s"
                            % (klass, member, os.path.relpath(path, root)))

for problem in problems[:40]:
    sys.stderr.write("P110: %s\n" % problem)
if problems:
    sys.stderr.write("P110: всего проблем: %d\n" % len(problems))
    sys.exit(1)
print("P110: все классы и статические вызовы Sakura разрешаются в пропатченном дереве")
PYSYM
    ok "P110 r95: статическая проверка символов — классы и вызовы Sakura на месте до запуска Gradle"
else
    skip "P110 r95 отключено (ZERO_TRAFFIC=0)"
fi
