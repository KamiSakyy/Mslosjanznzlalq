#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Вытащить api_id/api_hash из готового APK другого клиента.

Использование: python3 tools/extract_keys.py <apk> <out.json>
Пишет JSON: {"apk": ..., "pairs": [[name, api_id, api_hash], ...], "hashes": [...]}
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile


def raw_hashes(dex_blob):
    text = dex_blob.decode('latin-1')
    return sorted(set(re.findall(r'[0-9a-fA-F]{32}', text)))


def jadx_class(apk, class_name, workdir):
    """Деkompilирует один класс и возвращает текст (если jadx есть)."""
    jadx = shutil.which('jadx')
    if not jadx:
        return None
    out = os.path.join(workdir, class_name.replace('.', '_'))
    os.makedirs(out, exist_ok=True)
    try:
        subprocess.run([jadx, '--single-class', class_name, '-d', out, apk],
                       check=False, capture_output=True, timeout=900)
    except Exception:
        return None
    parts = []
    for path in glob.glob(os.path.join(out, '**', '*.java'), recursive=True):
        try:
            parts.append(open(path, encoding='utf-8', errors='ignore').read())
        except Exception:
            pass
    return '\n'.join(parts) if parts else None


def parse_java(text):
    """Ищем APP_ID / API_ID / APP_HASH / API_HASH в декомпилированном классе."""
    pairs = []
    if not text:
        return pairs
    app_id = None
    app_hash = None
    m = re.search(r'(?:APP_ID|API_ID)\s*=\s*(\d{3,9})\s*;', text)
    if m:
        app_id = int(m.group(1))
    m = re.search(r'(?:APP_HASH|API_HASH)\s*=\s*"([0-9a-fA-F]{32})"', text)
    if m:
        app_hash = m.group(1).lower()
    if app_id or app_hash:
        pairs.append((app_id, app_hash))
    return pairs


def main():
    apk = sys.argv[1]
    out_json = sys.argv[2] if len(sys.argv) > 2 else None
    workdir = tempfile.mkdtemp(prefix='extract_keys_')
    result = {'apk': os.path.basename(apk), 'size': os.path.getsize(apk), 'pairs': [], 'hashes': []}

    with zipfile.ZipFile(apk) as zf:
        zf.extractall(workdir)
    blob = b''
    for path in sorted(glob.glob(os.path.join(workdir, 'classes*.dex'))):
        blob += open(path, 'rb').read()
    result['hashes'] = raw_hashes(blob)

    for class_name in ['org.telegram.messenger.BuildConfig',
                       'org.telegram.messenger.BuildVars',
                       'org.telegram.messenger.ApplicationLoader']:
        text = jadx_class(apk, class_name, workdir)
        if jadx_class.__doc__ and text:
            result.setdefault('jadx_classes', []).append(class_name)
        for app_id, app_hash in parse_java(text):
            result['pairs'].append([class_name, app_id, app_hash])

    print(json.dumps(result, ensure_ascii=False, indent=2))
    if out_json:
        with open(out_json, 'w', encoding='utf-8') as fh:
            json.dump(result, fh, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
