#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Скачать APK MDGram нужной версии с разных зеркал.

Использование: python3 tools/fetch_mdgram.py <версия> <куда.apk>
"""
import json
import os
import re
import sys
import urllib.request

UA = ('Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) '
      'Chrome/120.0.0.0 Mobile Safari/537.36')
PKGS = ('org.telegram.mdgram', 'org.mdgram.mdgram')


def fetch(url, timeout=180, headers=None, binary=False):
    request = urllib.request.Request(url, headers=headers or {'User-Agent': UA})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        data = response.read()
        return data if binary else data.decode('utf-8', 'ignore')


def save(url, out, headers=None, min_size=5 * 1024 * 1024):
    try:
        print('  -> %s' % url[:160])
        data = fetch(url, headers=headers, binary=True)
        if len(data) < min_size or not data.startswith(b'PK'):
            print('     не APK (%d байт)' % len(data))
            return False
        open(out, 'wb').write(data)
        print('     СКАЧАНО %.1f МБ -> %s' % (len(data) / 1048576.0, out))
        return True
    except Exception as e:
        print('     ошибка: %s: %s' % (type(e).__name__, str(e)[:110]))
        return False


def trashbox(version, out):
    """trashbox.ru: страница приложения + прямые ссылки на файлы."""
    for pkg in PKGS:
        for url in ('https://trashbox.ru/link/android-mdgram',
                    'https://trashbox.ru/topics/%s' % pkg,
                    'https://trashbox.ru/search/?query=MDGram'):
            try:
                html = fetch(url, headers={'User-Agent': UA})
            except Exception as e:
                print('trashbox %s: %s' % (url[:60], str(e)[:70]))
                continue
            for match in re.findall(r'https?://[^"\'\s]+\.(?:apk|xapk)', html):
                if save(match, out, headers={'User-Agent': UA, 'Referer': url}):
                    return True
    return False


def apkpure(version, out):
    for pkg in PKGS:
        for url in ('https://d.apkpure.com/b/APK/%s?version=%s' % (pkg, version),
                    'https://d.apkpure.com/b/XAPK/%s?version=%s' % (pkg, version),
                    'https://d.cdnpure.com/b/APK/%s?version=%s' % (pkg, version)):
            if save(url, out, headers={'User-Agent': UA, 'Referer': 'https://apkpure.com/'}):
                return True
        for page in ('https://apkpure.com/mdgram-messenger/%s/download/%s' % (pkg, version),
                     'https://m.apkpure.com/mdgram-messenger/%s/download/%s' % (pkg, version),
                     'https://apkpure.net/mdgram-messenger/%s/download/%s' % (pkg, version)):
            try:
                html = fetch(page)
            except Exception as e:
                print('apkpure page %s: %s' % (page[:70], str(e)[:60]))
                continue
            for match in re.findall(r'https://[^"\'\s]*(?:d\.apkpure|cdnpure)[^"\'\s]+', html):
                if save(match, out, headers={'User-Agent': UA, 'Referer': page}):
                    return True
    return False


def apkcombo(version, out):
    for pkg in PKGS:
        for url in ('https://apkcombo.com/mdgram-messenger/%s/download/apk' % pkg,
                    'https://apkcombo.com/mdgram-messenger/%s/old-versions/' % pkg,
                    'https://apkcombo.com/downloader/?package=%s' % pkg):
            try:
                html = fetch(url)
            except Exception as e:
                print('apkcombo %s: %s' % (url[:60], str(e)[:60]))
                continue
            for match in re.findall(r'https://download\.apkcombo\.com/[^"\'\s]+', html):
                if save(match, out):
                    return True
    return False


def aptoide(version, out):
    for pkg in PKGS:
        for url in ('https://ws75.aptoide.com/api/7/app/get/package_name=%s' % pkg,
                    'https://ws75.aptoide.com/api/7/apps/search/query=MDGram/limit=5'):
            try:
                data = json.loads(fetch(url))
            except Exception as e:
                print('aptoide %s: %s' % (url[:60], str(e)[:60]))
                continue
            urls = re.findall(r'https?://[^"\']+\.apk', json.dumps(data))
            for candidate in urls:
                if save(candidate, out):
                    return True
    return False


def archive_org(version, out):
    try:
        data = json.loads(fetch('https://archive.org/advancedsearch.php?q=mdgram&fl%5B%5D=identifier'
                                '&rows=25&output=json'))
        ids = [doc['identifier'] for doc in data.get('response', {}).get('docs', [])]
    except Exception as e:
        print('archive.org: %s' % str(e)[:80])
        return False
    for ident in ids:
        try:
            files = json.loads(fetch('https://archive.org/metadata/%s' % ident)).get('files', [])
        except Exception:
            continue
        for entry in files:
            name = entry.get('name', '')
            if name.lower().endswith(('.apk', '.xapk')):
                url = 'https://archive.org/download/%s/%s' % (ident, name)
                if save(url, out):
                    return True
    return False


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else '9.9.3'
    out = sys.argv[2] if len(sys.argv) > 2 else '/tmp/mdgram_993.apk'
    print('=== MDGram %s: ищу APK ===' % version)
    for name, fn in (('apkpure', apkpure), ('apkcombo', apkcombo), ('trashbox', trashbox),
                     ('aptoide', aptoide), ('archive.org', archive_org)):
        print('-- источник %s' % name)
        try:
            if fn(version, out):
                print('ИТОГ: скачано (%s)' % name)
                return 0
        except Exception as e:
            print('   источник упал: %s' % str(e)[:100])
    print('ИТОГ: не удалось')
    return 1


if __name__ == '__main__':
    sys.exit(main())
