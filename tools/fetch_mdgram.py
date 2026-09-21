#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Скачать APK MDGram нужной версии с apkpure / apkcombo / aptoide / trashbox.

Использование: python3 tools/fetch_mdgram.py <версия> <куда_сохранить.apk>
Пробует несколько источников по очереди и печатает, что получилось.
"""
import json
import os
import re
import sys
import urllib.request

UA = ('Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) '
      'Chrome/120.0.0.0 Mobile Safari/537.36')
PKG = 'org.telegram.mdgram'
PKG_ALT = 'org.mdgram.mdgram'


def fetch(url, timeout=120, headers=None, binary=False):
    request = urllib.request.Request(url, headers=headers or {'User-Agent': UA})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        data = response.read()
        return data if binary else data.decode('utf-8', 'ignore')


def try_download(url, out, headers=None, min_size=5 * 1024 * 1024):
    try:
        print('пробую: %s' % url[:150])
        data = fetch(url, headers=headers, binary=True)
        if len(data) < min_size or not data.startswith(b'PK'):
            print('  -> не APK (%d байт)' % len(data))
            return False
        with open(out, 'wb') as fh:
            fh.write(data)
        print('  -> СКАЧАНО: %s (%.1f МБ)' % (out, len(data) / 1048576.0))
        return True
    except Exception as e:
        print('  -> ошибка: %s: %s' % (type(e).__name__, str(e)[:120]))
        return False


def apkpure_direct(version, out):
    for pkg in (PKG, PKG_ALT):
        for suffix in ('', '&arch=arm64-v8a'):
            url = 'https://d.apkpure.com/b/APK/%s?version=%s%s' % (pkg, version, suffix)
            if try_download(url, out, headers={'User-Agent': UA, 'Referer': 'https://apkpure.com/'}):
                return True
    return False


def apkpure_page(version, out):
    """Ищем aria-label / data-dt-direct-download на странице версии."""
    for pkg in (PKG, PKG_ALT):
        for page in ('https://apkpure.com/mdgram-messenger/%s/download/%s' % (pkg, version),
                     'https://m.apkpure.com/mdgram-messenger/%s/download/%s' % (pkg, version)):
            try:
                html = fetch(page, headers={'User-Agent': UA})
            except Exception as e:
                print('страница %s: %s' % (page[:80], str(e)[:80]))
                continue
            for pattern in (r'https://d\.apkpure\.com/b/[^"\'\s]+', r'"downloadUrl":"([^"]+)"',
                            r'data-dt-direct-download="([^"]+)"'):
                for match in re.findall(pattern, html):
                    url = match if match.startswith('http') else match.replace('\\/', '/')
                    if try_download(url, out, headers={'User-Agent': UA, 'Referer': page}):
                        return True
    return False


def apkcombo(version, out):
    for url in ('https://apkcombo.com/mdgram-messenger/%s/download/apk' % PKG,
                'https://apkcombo.com/downloader/?package=%s' % PKG,
                'https://apkcombo.com/mdgram-messenger/%s/download/phone-10.5.0-apk' % PKG):
        try:
            html = fetch(url, headers={'User-Agent': UA})
        except Exception as e:
            print('apkcombo %s: %s' % (url[:70], str(e)[:80]))
            continue
        for match in re.findall(r'https://download\.apkcombo\.com/[^"\'\s]+', html):
            if try_download(match, out):
                return True
    return False


def aptoide(version, out):
    try:
        info = json.loads(fetch('https://ws75.aptoide.com/api/7/app/get/package_name=%s' % PKG))
        url = info.get('nodes', {}).get('meta', {}).get('data', {}).get('file', {}).get('path')
        ver = info.get('nodes', {}).get('meta', {}).get('data', {}).get('file', {}).get('vercode')
        print('aptoide: версия %s' % ver)
        if url and try_download(url, out):
            return True
    except Exception as e:
        print('aptoide: %s' % str(e)[:100])
    return False


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else '9.9.3'
    out = sys.argv[2] if len(sys.argv) > 2 else '/tmp/mdgram_993.apk'
    print('=== ищу MDGram %s (%s) ===' % (version, PKG))
    for name, fn in (('apkpure direct', apkpure_direct), ('apkpure page', apkpure_page),
                     ('apkcombo', apkcombo), ('aptoide', aptoide)):
        print()
        print('-- источник: %s' % name)
        try:
            if fn(version, out):
                print('ИТОГ: скачано из %s' % name)
                return 0
        except Exception as e:
            print('  источник упал: %s' % str(e)[:120])
    print('ИТОГ: не удалось скачать MDGram %s' % version)
    return 1


if __name__ == '__main__':
    sys.exit(main())
