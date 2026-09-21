#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ПОЛНЫЙ декомпиль APK (jadx) и поиск ключей API.

Использование: python3 tools/full_decompile.py <apk> <куда_декомпилировать> <файл_отчёта>

- распаковывает APK;
- декомпилирует ВСЕ классы через jadx (если jadx есть) и через dex-строки как запасной путь;
- находит в декомпилированном коде APP_ID/APP_HASH/api_id/api_hash;
- собирает строки, похожие на api_hash (32 hex), и числа рядом с ними;
- пишет отчёт и JSON с найденными парами.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

KNOWN = {
    '014b35b6184100b085b0d0572f9b5103': 'Telegram Android / MDGram (публичный)',
    'b18441a1ff607e10a989891a5462e627': 'Telegram Desktop',
    'eb06d4abfb49dc3eeb1aeb98ae0f581e': 'TG for Android (Play)',
    '1c5c96d5edd401b1ed40db3fb5633e2d': 'Public Static Final',
    '3e0cb5efcd52300aec5994fdfc5bdc16': 'Telegram X',
    'a3406de8d171bb422bb6ddf3bbd800e2': 'Nicegram / TDLib',
    '8da85b0d5bfe62527e5b244c209159c3': 'Telegram Web',
    '452b0359b988148995f22ff0f4229750': 'Telegram Web K',
    '7245de8e747a0d6fbe11f7cc14fcc0bb': 'Telegram iOS beta',
    '33c45224029d59cb3ad0c16134215aeb': 'Telegram Swift',
}


def jadx_decompile(apk, out_dir):
    jadx = shutil.which('jadx') or shutil.which('jadx.sh')
    if not jadx:
        for candidate in glob.glob('/opt/jadx/bin/jadx*') + glob.glob(os.path.expanduser('~/jadx/bin/jadx*')):
            jadx = candidate
            break
    if not jadx:
        print('jadx не найден — работаю только по строкам dex')
        return False
    os.makedirs(out_dir, exist_ok=True)
    print('jadx: %s' % jadx)
    for extra in (['--no-res', '--show-bad-code'], ['--no-res'], []):
        try:
            result = subprocess.run([jadx, '-d', out_dir, '-j', '4'] + extra + [apk],
                                    capture_output=True, text=True, timeout=3600)
            print('jadx код возврата: %s' % result.returncode)
            if result.stderr:
                print(result.stderr[-1500:])
            if result.returncode == 0:
                return True
        except subprocess.TimeoutExpired:
            print('jadx превысил время')
        except Exception as e:
            print('jadx ошибка: %s' % e)
    return bool(glob.glob(os.path.join(out_dir, '**', '*.java'), recursive=True))


def scan_java(out_dir, report):
    pairs = []
    hits = []
    files = glob.glob(os.path.join(out_dir, '**', '*.java'), recursive=True)
    print('декомпилированных файлов: %d' % len(files))
    for path in files:
        try:
            text = open(path, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        for match in re.finditer(r'([A-Za-z_]*APP_(?:ID|HASH)|[A-Za-z_]*API_(?:ID|HASH))\s*=\s*'
                                 r'("?[A-Za-z0-9_]{3,60}"?)', text):
            hits.append((os.path.relpath(path, out_dir), match.group(1), match.group(2)))
        ids = [int(m.group(1)) for m in re.finditer(r'(?:api_?id|app_?id)\s*=\s*(\d{3,9})', text, re.I)]
        hashes = [m.group(1).lower() for m in re.finditer(r'"([0-9a-fA-F]{32})"', text)]
        for app_id in set(ids):
            for app_hash in set(hashes):
                pairs.append((os.path.relpath(path, out_dir), app_id, app_hash))
    print()
    print('== найденные присваивания APP_ID/APP_HASH ==')
    for path, name, value in hits[:80]:
        print('%-70s %s = %s' % (path[:70], name, value))
    print()
    print('== пары api_id + строка-хеш в одном классе ==')
    for path, app_id, app_hash in pairs[:60]:
        mark = KNOWN.get(app_hash, '')
        print('%-58s id=%-9s hash=%s %s' % (path[:58], app_id, app_hash, mark))
    return hits, pairs


def scan_dex(apk, report):
    workdir = '/tmp/full_decompile_unpack'
    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(workdir, exist_ok=True)
    with zipfile.ZipFile(apk) as zf:
        zf.extractall(workdir)
    print()
    print('== файлы APK с hex-32 строками ==')
    suspicious = {}
    for path in sorted(glob.glob(os.path.join(workdir, '**', '*'), recursive=True)):
        if not os.path.isfile(path) or os.path.getsize(path) > 200 * 1024 * 1024:
            continue
        try:
            data = open(path, 'rb').read()
        except Exception:
            continue
        text = data.decode('latin-1')
        for needle, name in KNOWN.items():
            if needle in text:
                print('известный %s (%s) в %s' % (needle, name, os.path.relpath(path, workdir)))
        found = set(m.group(0).lower() for m in re.finditer(r'[0-9a-fA-F]{32}', text))
        rel = os.path.relpath(path, workdir)
        if found and (rel.endswith('.dex') or rel.endswith('.so') or 'asset' in rel):
            suspicious[rel] = sorted(found)
    print()
    print('== hex-32 по файлам (dex/so/assets) ==')
    for rel, values in suspicious.items():
        print('%s -> %d значений' % (rel[:70], len(values)))
        for value in values[:20]:
            print('    %s %s' % (value, KNOWN.get(value, '')))
    return suspicious


def main():
    apk = sys.argv[1]
    out_dir = sys.argv[2]
    report_path = sys.argv[3]
    pairs_json = sys.argv[4] if len(sys.argv) > 4 else None

    lines = []

    class Tee:
        def write(self, text):
            lines.append(text)
        def flush(self):
            pass

    original = sys.stdout
    sys.stdout = Tee()
    try:
        print('=== ПОЛНЫЙ ДЕКОМПИЛЬ: %s ===' % os.path.basename(apk))
        print('размер: %d' % os.path.getsize(apk))
        import hashlib
        print('sha256: %s' % hashlib.sha256(open(apk, 'rb').read()).hexdigest())
        jadx_decompile(apk, out_dir)
        hits, pairs = scan_java(out_dir, report_path)
        scan_dex(apk, report_path)
        if pairs_json:
            json.dump({'pairs': [[p[0], p[1], p[2]] for p in pairs]}, open(pairs_json, 'w', encoding='utf-8'),
                      ensure_ascii=False, indent=2)
    finally:
        sys.stdout = original
    with open(report_path, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(lines))
    print('\n'.join(lines[-80:]))
    print()
    print('отчёт: %s' % report_path)


if __name__ == '__main__':
    main()
