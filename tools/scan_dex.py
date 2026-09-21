#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Разбор classes*.dex любого APK: какие api_id/api_hash в него вшиты.

Использование: python3 tools/scan_dex.py /путь/к/распакованному/apk
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
    '8c9dbfe58437d1739540f5d53c72ae4b': 'Plus Messenger',
    '344583e45741c457fe1862106095a5eb': 'Telegram Desktop (example)',
    '8da85b0d5bfe62527e5b244c209159c3': 'Telegram Web',
    '452b0359b988148995f22ff0f4229750': 'Telegram Web K',
    '7245de8e747a0d6fbe11f7cc14fcc0bb': 'Telegram iOS beta',
    '33c45224029d59cb3ad0c16134215aeb': 'Telegram Swift',
    '36722c72256a24c1225de00eb6a1ca74': 'Telegram CLI',
}


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    dex_files = sorted(glob.glob(os.path.join(root, 'classes*.dex')))
    print('dex-файлов: %d' % len(dex_files))
    blob = b''
    for path in dex_files:
        data = open(path, 'rb').read()
        blob += data
        print('%s: %.1f МБ' % (os.path.basename(path), len(data) / 1048576.0))

    print()
    print('== известные api_hash в этом APK ==')
    for needle, name in KNOWN.items():
        if needle.encode() in blob:
            print('НАЙДЕН  %s  (%s)' % (needle, name))

    print()
    print('== все 32-символьные hex-строки (кандидаты api_hash) ==')
    text = blob.decode('latin-1')
    found = sorted(set(re.findall(r'[0-9a-fA-F]{32}', text)))
    for value in found[:200]:
        mark = KNOWN.get(value.lower(), '')
        print('%s  %s' % (value, mark))
    print('всего кандидатов: %d' % len(found))

    print()
    print('== имена, связанные с api_id/версией ==')
    for needle in ['APP_ID', 'APP_HASH', 'BUILD_VERSION_STRING', 'api_id', 'api_hash', 'lang_pack']:
        print('%-22s -> %s' % (needle, 'есть' if needle.encode() in blob else 'нет'))


if __name__ == '__main__':
    main()
