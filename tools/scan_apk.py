#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Разбор распакованного APK: пакет, версия, ключи API и подпись.

Использование: python3 tools/scan_apk.py <распакованный_apk> [путь_к_apk]
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


def parse_manifest(root):
    path = os.path.join(root, 'AndroidManifest.xml')
    if not os.path.exists(path):
        return {}
    blob = open(path, 'rb').read().decode('utf-16-le', 'ignore') + open(path, 'rb').read().decode('latin-1')
    info = {}
    for key in ('package', 'versionName', 'versionCode'):
        match = re.search(r'%s[^a-zA-Z0-9_]{0,4}([A-Za-z0-9_.]+)' % key, blob)
        if match:
            info[key] = match.group(1)
    return info


def main():
    root = sys.argv[1]
    apk = sys.argv[2] if len(sys.argv) > 2 else root
    print('APK: %s' % os.path.basename(apk))
    print('манифест: %s' % parse_manifest(root))

    dex_files = sorted(glob.glob(os.path.join(root, 'classes*.dex')))
    blob = b''
    for path in dex_files:
        blob += open(path, 'rb').read()
    text = blob.decode('latin-1')

    print()
    print('== известные api_hash ==')
    for needle, name in KNOWN.items():
        if needle in text:
            print('НАЙДЕН  %s  (%s)' % (needle, name))

    print()
    print('== имена, связанные с ключами ==')
    for needle in ('APP_ID', 'APP_HASH', 'API_ID', 'API_HASH', 'BUILD_VERSION_STRING', 'lang_pack'):
        print('%-22s -> %s' % (needle, 'есть' if needle.encode() in blob else 'нет'))

    print()
    print('== строки с mdgram (для опознания версии/сборки) ==')
    seen = set()
    for match in re.findall(r'[ -~]{6,60}', text):
        low = match.lower()
        if 'mdgram' in low and match not in seen:
            seen.add(match)
            if len(seen) > 40:
                break
            print(match)


if __name__ == '__main__':
    main()
