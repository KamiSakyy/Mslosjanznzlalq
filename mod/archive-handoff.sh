#!/usr/bin/env bash
# =============================================================================
#  KamiGram — архивирование сборок в папке handoff/
#
#  ПРАВИЛО: ничего не удаляем. Каждая сборка сохраняется отдельным файлом,
#  старые версии APK и исходников остаются навсегда.
#
#  Что раскладывается:
#    handoff/apk/KamiGram-<версия>-r<номер>.apk   — каждая сборка
#    handoff/apk/KamiGram-latest.apk              — копия последней (удобная ссылка)
#    handoff/sources/MOD_INFO-<версия>-r<номер>.txt    — из какого коммита собрано, какие патчи
#    handoff/sources/changes-<версия>-r<номер>.patch  — полный git diff против исходников Telegram
#    handoff/sources/SHA256SUMS-<версия>-r<номер>.txt — контрольные суммы
#    handoff/SHA256SUMS.txt                       — общий список сумм всех версий
#    handoff/INDEX.md                             — таблица всех версий
#
#  Запуск:
#    ARCHIVE_DIR=handoff bash mod/archive-handoff.sh <apk> <version> <run> [mod_info] [patch]
#    ARCHIVE_DIR=handoff PUSH=1 bash mod/archive-handoff.sh <apk> <version> <run> [mod_info] [patch]
# =============================================================================
set -Eeuo pipefail

APK_IN=${1:?укажи путь к APK}
VERSION=${2:?укажи версию, например 12.10.3-mod}
RUN=${3:?укажи номер сборки}
MOD_INFO=${4:-}
PATCH=${5:-}

ARCHIVE_DIR=${ARCHIVE_DIR:-handoff}
PUSH=${PUSH:-0}

[ -f "$APK_IN" ] || { echo "нет файла APK: $APK_IN" >&2; exit 1; }

APK_DIR="$ARCHIVE_DIR/apk"
SRC_DIR="$ARCHIVE_DIR/sources"
mkdir -p "$APK_DIR" "$SRC_DIR"

APK_NAME="KamiGram-$VERSION-r$RUN.apk"
APK_OUT="$APK_DIR/$APK_NAME"

if [ -f "$APK_OUT" ]; then
    echo "[архив] $APK_NAME уже есть — не перезаписываю (историю не меняем)"
else
    cp "$APK_IN" "$APK_OUT"
    echo "[архив] сохранён $APK_NAME ($(du -h "$APK_OUT" | cut -f1))"
fi

# latest — всегда копия самой свежей успешной сборки
cp "$APK_IN" "$APK_DIR/KamiGram-latest.apk"

# исходники: MOD_INFO (что изменено и от какого коммита) + полный diff
if [ -n "$MOD_INFO" ] && [ -f "$MOD_INFO" ]; then
    cp "$MOD_INFO" "$SRC_DIR/MOD_INFO-$VERSION-r$RUN.txt"
    echo "[архив] сохранён MOD_INFO-$VERSION-r$RUN.txt"
fi
if [ -n "$PATCH" ] && [ -f "$PATCH" ]; then
    # полный diff сжимаем: 18 МБ текста → ~2 МБ, в git не раздувает репозиторий
    gzip -9 -c "$PATCH" > "$SRC_DIR/changes-$VERSION-r$RUN.patch.gz"
    echo "[архив] сохранён changes-$VERSION-r$RUN.patch.gz ($(du -h "$SRC_DIR/changes-$VERSION-r$RUN.patch.gz" | cut -f1))"
    # компактная сводка: какие файлы менялись и насколько
    grep -E '^ .* \| ' "$PATCH" > "$SRC_DIR/changes-$VERSION-r$RUN.stat.txt" 2>/dev/null || true
    grep -E 'files? changed' "$PATCH" >> "$SRC_DIR/changes-$VERSION-r$RUN.stat.txt" 2>/dev/null || true
fi

# суммы по этой версии
( cd "$APK_DIR" && sha256sum "$APK_NAME" KamiGram-latest.apk > "$SRC_DIR/SHA256SUMS-$VERSION-r$RUN.txt" ) 2>/dev/null || true

# общий список сумм: пересобираем из всех файлов, ничего не теряя
( cd "$APK_DIR" && sha256sum *.apk 2>/dev/null | sort -k2 ) > "$ARCHIVE_DIR/SHA256SUMS.txt" || true

# индекс всех версий
python3 - "$ARCHIVE_DIR" <<'PY'
import io, os, sys, hashlib, datetime

root = sys.argv[1]
apk_dir = os.path.join(root, 'apk')
src_dir = os.path.join(root, 'sources')

def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()

rows = []
if os.path.isdir(apk_dir):
    for name in sorted(os.listdir(apk_dir)):
        if not name.endswith('.apk') or name == 'KamiGram-latest.apk':
            continue
        p = os.path.join(apk_dir, name)
        size = os.path.getsize(p) / 1048576
        rows.append((name, size, sha(p)))

sources = sorted(os.listdir(src_dir)) if os.path.isdir(src_dir) else []
legacy = sorted(n for n in os.listdir(root) if n.endswith('.apk'))

out = []
out.append('# KamiGram — архив сборок\n')
out.append('Все версии APK и исходников сохраняются **навсегда**, ничего не удаляется.\n')
out.append('\n## Версии APK\n')
out.append('| файл | размер | sha256 |')
out.append('|---|---|---|')
for name, size, digest in rows:
    out.append('| `apk/%s` | %.1f МБ | `%s…` |' % (name, size, digest[:16]))
if legacy:
    out.append('\n### Ранние сборки (до введения версионирования)\n')
    for n in legacy:
        p = os.path.join(root, n)
        out.append('| `%s` | %.1f МБ | `%s…` |' % (n, os.path.getsize(p) / 1048576, sha(p)))

out.append('\n## Исходники версий\n')
out.append('Каждая запись = `MOD_INFO` (версия, коммит Telegram, список патчей) + `changes-*.patch` (полный diff).')
out.append('Восстановить исходники: `git clone --recursive https://github.com/DrKLO/Telegram` нужного коммита, затем `git apply changes-*.patch`.\n')
out.append('| файл | размер |')
out.append('|---|---|')
for n in sources:
    p = os.path.join(src_dir, n)
    out.append('| `sources/%s` | %.1f КБ |' % (n, os.path.getsize(p) / 1024))

out.append('\n## Скачать\n')
out.append('```')
out.append('https://github.com/KamiSakyy/Mslosjanznzlalq/raw/<ветка>/handoff/apk/KamiGram-latest.apk')
out.append('```')
out.append('\nОбновлено: %s UTC' % datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M'))

io.open(os.path.join(root, 'INDEX.md'), 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print('[архив] INDEX.md обновлён: версий APK — %d, файлов исходников — %d' % (len(rows), len(sources)))
PY

if [ "$PUSH" = "1" ]; then
    git add -f "$ARCHIVE_DIR"
    if git diff --cached --quiet; then
        echo "[архив] новых файлов нет — коммит не нужен"
        exit 0
    fi
    git -c user.name="arena-ai-coding-agent[bot]" \
        -c user.email="arena-ai-coding-agent[bot]@users.noreply.github.com" \
        commit -m "chore(handoff): архив $VERSION (сборка #$RUN) [skip ci]"
    for i in 1 2 3; do
        if git push origin "HEAD:${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}"; then
            echo "[архив] запушено (попытка $i)"
            exit 0
        fi
        git pull --rebase --autostash origin "${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}" || true
    done
    echo "[архив] не удалось запушить за 3 попытки" >&2
    exit 1
fi
