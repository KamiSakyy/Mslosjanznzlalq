#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Полный разбор APK на ключи: dex, assets, ресурсы, нативные библиотеки.

Использование: python3 tools/scan_apk.py <распакованный_apk> [имя_apk]
Ищет известные api_hash, все 32-символьные hex-строки и подозрительные api_id.
"""
import glob
import os
import re
import sys


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


def iter_files(root):
    for base, _dirs, files in os.walk(root):
        for name in files:
            yield os.path.join(base, name)


def main():
    root = sys.argv[1]
    apk = sys.argv[2] if len(sys.argv) > 2 else root

    print('APK: %s' % (apk if os.path.isfile(apk) else os.path.basename(apk)))
    if os.path.isfile(apk):
        print('размер: %d байт' % os.path.getsize(apk))
        print('sha256: %s' % __import__('hashlib').sha256(open(apk, 'rb').read()).hexdigest())

    per_file_hashes = {}
    all_hashes = {}
    for path in iter_files(root):
        try:
            if os.path.getsize(path) > 200 * 1024 * 1024:
                continue
            data = open(path, 'rb').read()
        except Exception:
            continue
        found = set(re.findall(rb'[0-9a-fA-F]{32}', data))
        text = data.decode('latin-1')
        for needle in KNOWN:
            if needle in text:
                print('НАЙДЕН известный api_hash %s (%s) в %s'
                      % (needle, KNOWN[needle], os.path.relpath(path, root)))
        for value in found:
            key = value.decode('latin-1').lower()
            all_hashes.setdefault(key, set()).add(os.path.relpath(path, root))
            per_file_hashes.setdefault(os.path.relpath(path, root), set()).add(key)

    print()
    print('== файлы, где встречаются hex-32 (dex/so/assets) ==')
    for path, values in sorted(per_file_hashes.items()):
        print('%-60s %d строк' % (path[:60], len(values)))

    print()
    print('== все hex-32 значения, кроме известных криптоконстант ==')
    interesting = [h for h in sorted(all_hashes)
                   if h not in KNOWN and not h.startswith('a16a09e6')]
    for value in interesting[:120]:
        print('%s   %s' % (value, ', '.join(sorted(all_hashes[value]))[:70]))

    print()
    print('== имена классов/строк про ключи ==')
    for needle in ('APP_ID', 'APP_HASH', 'API_ID', 'API_HASH', 'HASH', 'api_id', 'api_hash',
                   'BUILD_VERSION_STRING', 'lang_pack'):
        hits = [p for p in per_file_hashes if needle.lower() in p.lower()]
        blob_hits = 0
        for path in iter_files(root):
            if path.endswith('.dex'):
                try:
                    if needle.encode() in open(path, 'rb').read():
                        blob_hits += 1
                except Exception:
                    pass
        print('%-22s dex-файлов со строкой: %d' % (needle, blob_hits))


if __name__ == '__main__':
    main()
