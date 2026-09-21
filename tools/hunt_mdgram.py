#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Полный разбор APK MDGram и поиск в нём api_id / api_hash.

Запуск (в CI, где есть интернет):
    python3 tools/hunt_mdgram.py --apk /tmp/md/mdgram-9.9.3.apk --out notes/mdgram-hunt.txt

Что делает:
  1. подтверждает личность APK (пакет, версия, число dex, нативные .so, подпись zip);
  2. разбирает apktool -> smali (полная декомпиляция в байткод) и jadx -> java;
  3. читает таблицы строк ВСЕХ dex напрямую (без сторонних библиотек);
  4. ищет api_id/api_hash по: smali, java, строкам dex, всем файлам APK,
     нативным библиотекам (с дампом байтов вокруг найденных строк);
  5. вытаскивает константы, которые передаются в ConnectionsManager.init(...) —
     там лежит api_id, даже если он не лежит в BuildVars;
  6. пишет отчёт и список пар-кандидатов для проверки на живом сервере.
"""
import argparse
import io
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile

HEX32 = re.compile(r'[0-9a-fA-F]{32}')
HEX32_B = re.compile(rb'[0-9a-fA-F]{32}')
APIISH = re.compile(r'(api[_-]?id|api[_-]?hash|APP_ID|APP_HASH|appId|appHash|BuildVars)', re.I)
KNOWN_SAMPLE = '014b35b6184100b085b0d0572f9b5103'   # примерный ключ Telegram (api_id = 4)
INTERESTING = ['8671348b81b95fc603505dfc881b4510']   # найдено в libmdgram.so прошлого разбора

OUT = []


def log(*a):
    line = ' '.join(str(x) for x in a)
    print(line, flush=True)
    OUT.append(line)


def section(title):
    log('')
    log('== %s' % title)


def run(cmd, timeout=3600, cwd=None):
    try:
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           timeout=timeout, cwd=cwd)
        return p.returncode, p.stdout.decode('utf-8', 'ignore')
    except Exception as e:
        return -1, '%s: %s' % (type(e).__name__, e)


def uleb128(data, pos):
    size = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        size |= (b & 0x7F) << shift
        shift += 7
        if not (b & 0x80):
            return size, pos


def dex_strings(data):
    """Все строки из таблицы строк dex-файла (без библиотек)."""
    if data[:4] != b'dex\n':
        return None
    string_ids_size = struct.unpack_from('<I', data, 0x38)[0]
    string_ids_off = struct.unpack_from('<I', data, 0x3C)[0]
    if string_ids_off + 4 * string_ids_size > len(data):
        return None
    out = []
    for i in range(string_ids_size):
        try:
            off = struct.unpack_from('<I', data, string_ids_off + 4 * i)[0]
            _, pos = uleb128(data, off)
            end = data.index(b'\x00', pos)
            out.append(data[pos:end].decode('utf-8', 'ignore'))
        except Exception:
            continue
    return out


def ascii_around(data, pos, before=96, after=96):
    start = max(0, pos - before)
    chunk = data[start:pos + after]
    text = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
    ints = []
    for i in range(0, max(0, len(chunk) - 4)):
        val = struct.unpack_from('<I', chunk, i)[0]
        if 1 <= val <= 999999999:
            ints.append((i - (pos - start), val))
    ints.sort(key=lambda t: (abs(t[0]), t[1]))
    uniq = []
    for _, v in ints:
        if v not in uniq:
            uniq.append(v)
    return text, uniq[:12]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True)
    parser.add_argument('--out', default='notes/mdgram-hunt.txt')
    parser.add_argument('--work', default='/tmp/hunt')
    parser.add_argument('--skip-jadx', action='store_true')
    args = parser.parse_args()

    apk = args.apk
    if not os.path.isfile(apk):
        log('APK не найден: %s' % apk)
        return 1

    import hashlib
    raw = open(apk, 'rb').read()
    log('APK: %s' % apk)
    log('размер: %d байт' % len(raw))
    log('sha256: %s' % hashlib.sha256(raw).hexdigest())

    zf = zipfile.ZipFile(apk)
    names = zf.namelist()
    dexes = sorted(n for n in names if re.match(r'^classes\d*\.dex$', n))
    sos = sorted(n for n in names if n.endswith('.so'))
    log('файлов в APK: %d, dex: %d, нативных .so: %d' % (len(names), len(dexes), len(sos)))
    log('dex: %s' % ', '.join('%s (%.1f МБ)' % (n, zf.getinfo(n).file_size / 1048576.0) for n in dexes))
    log('so : %s' % ', '.join('%s (%.1f МБ)' % (n, zf.getinfo(n).file_size / 1048576.0) for n in sos[:12]))

    os.makedirs(args.work, exist_ok=True)
    smali_dir = os.path.join(args.work, 'apktool')
    java_dir = os.path.join(args.work, 'jadx')
    shutil.rmtree(smali_dir, ignore_errors=True)
    shutil.rmtree(java_dir, ignore_errors=True)

    # ---------------------------------------------------------------- apktool
    section('apktool: полная декомпиляция в smali')
    jar = '/opt/apktool.jar'
    if os.path.isfile(jar):
        rc, out = run(['java', '-jar', jar, 'd', '-f', '-o', smali_dir, apk], timeout=3600)
    else:
        rc, out = run(['apktool', 'd', '-f', '-o', smali_dir, apk], timeout=3600)
    log('apktool rc=%d' % rc)
    log(out[-4000:] if out else '(нет вывода)')
    smali_files = []
    for root, _dirs, files in os.walk(smali_dir):
        for f in files:
            if f.endswith('.smali'):
                smali_files.append(os.path.join(root, f))
    log('smali-файлов: %d' % len(smali_files))
    yml = os.path.join(smali_dir, 'apktool.yml')
    if os.path.isfile(yml):
        txt = io.open(yml, encoding='utf-8', errors='ignore').read()
        for key in ('minSdkVersion', 'targetSdkVersion', 'versionCode', 'versionName'):
            m = re.search(r'%s:\s*(\S+)' % key, txt)
            if m:
                log('apktool.yml %s = %s' % (key, m.group(1)))
    manifest = os.path.join(smali_dir, 'AndroidManifest.xml')
    if os.path.isfile(manifest):
        txt = io.open(manifest, encoding='utf-8', errors='ignore').read()
        m = re.search(r'package="([^"]+)"', txt)
        if m:
            log('ПАКЕТ в манифесте: %s' % m.group(1))

    # ---------------------------------------------------------------- jadx
    if not args.skip_jadx:
        section('jadx: полная декомпиляция в java')
        rc, out = run(['jadx', '--no-res', '-j', '4', '-d', java_dir, apk], timeout=3600)
        log('jadx rc=%d' % rc)
        log(out[-2000:] if out else '(нет вывода)')
        java_files = 0
        javas = []
        for root, _dirs, files in os.walk(java_dir):
            for f in files:
                if f.endswith('.java'):
                    java_files += 1
                    javas.append(os.path.join(root, f))
        log('java-файлов: %d' % java_files)
    else:
        javas = []
        log('jadx пропущен')

    # ---------------------------------------------------------------- dex-строки
    section('таблицы строк dex (читаются напрямую)')
    all_hex = {}
    api_like = set()
    total_strings = 0
    for name in dexes:
        data = zf.read(name)
        strings = dex_strings(data)
        if strings is None:
            log('%s: НЕ dex-файл (магия %r) — возможно, APK защищён упаковщиком' % (name, data[:8]))
            continue
        total_strings += len(strings)
        hexes = [s for s in strings if HEX32.fullmatch(s)]
        log('%s: строк %d, hex-32 %d' % (name, len(strings), len(hexes)))
        for s in hexes:
            all_hex.setdefault(s.lower(), []).append(name)
        for s in strings:
            if APIISH.search(s) and len(s) < 80:
                api_like.add(s)
    log('всего строк во всех dex: %d' % total_strings)
    if all_hex:
        log('hex-32 в dex (%d):' % len(all_hex))
        for s in sorted(all_hex):
            log('   %s   <- %s' % (s, ', '.join(sorted(set(all_hex[s])))))
    else:
        log('hex-32 в dex: НЕТ')
    if api_like:
        log('строки про api/build (%d):' % len(api_like))
        for s in sorted(api_like)[:60]:
            log('   %s' % s)

    # ---------------------------------------------------------------- smali
    section('smali: поиск api_id / api_hash / BuildVars')
    hits = {}
    if smali_files:
        patterns = [re.compile(r'api[_-]?hash', re.I), re.compile(r'api[_-]?id', re.I),
                    re.compile(r'APP_HASH'), re.compile(r'APP_ID'), re.compile(r'BuildVars')]
        for path in smali_files:
            try:
                txt = io.open(path, encoding='utf-8', errors='ignore').read()
            except Exception:
                continue
            for p in patterns:
                if p.search(txt):
                    hits.setdefault(path, []).append(p.pattern)
        log('smali-файлов с упоминанием api/buildvars: %d' % len(hits))
        for path in sorted(hits)[:40]:
            log('   %s  (%s)' % (os.path.relpath(path, smali_dir), ', '.join(sorted(set(hits[path])))))
        for path in sorted(hits)[:8]:
            log('--- %s' % os.path.relpath(path, smali_dir))
            for line in io.open(path, encoding='utf-8', errors='ignore').read().splitlines():
                if APIISH.search(line):
                    log('    %s' % line.strip()[:200])

    # api_id из вызовов init(...) — то, что реально уходит на сервер
    section('smali: константы, которые уходят в ConnectionsManager.init(...)')
    init_consts = {}
    for path in smali_files:
        try:
            txt = io.open(path, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        if 'ConnectionsManager;->init' not in txt and 'ConnectionsManager$' not in txt:
            continue
        lines = txt.splitlines()
        for i, line in enumerate(lines):
            if '->init(' in line and 'ConnectionsManager' in line:
                window = lines[max(0, i - 60):i + 1]
                consts = []
                for w in window:
                    m = re.search(r'const(?:/4|/16|/high16)?\s+[vp]\d+, (?:0x)?([0-9a-fA-F]+)', w)
                    if m:
                        try:
                            val = int(m.group(1), 16)
                        except ValueError:
                            continue
                        if 1 <= val <= 999999999:
                            consts.append(val)
                if consts:
                    key = os.path.relpath(path, smali_dir)
                    init_consts.setdefault(key, []).extend(consts[-4:])
                    log('   %s: %s' % (key, consts[-12:]))
    if not init_consts:
        log('   (вызовы init с числовыми константами не найдены — api_id берётся из поля/строки)')

    # ---------------------------------------------------------------- java
    if javas:
        section('jadx: поиск api_id / api_hash в java')
        found = 0
        for path in javas:
            try:
                txt = io.open(path, encoding='utf-8', errors='ignore').read()
            except Exception:
                continue
            if ('APP_HASH' in txt or 'api_hash' in txt or 'apiHash' in txt
                    or 'APP_ID' in txt or 'apiId' in txt):
                found += 1
                if found <= 25:
                    log('   %s' % os.path.relpath(path, java_dir))
                    for line in txt.splitlines():
                        if APIISH.search(line):
                            log('        %s' % line.strip()[:200])
        log('java-файлов с ключами: %d' % found)

    # ---------------------------------------------------------------- нативные
    section('нативные библиотеки: строки-ключи и байты вокруг них')
    pairs = []
    for name in sos:
        data = zf.read(name)
        found = []
        for m in HEX32_B.finditer(data):
            found.append(m.start())
        if not found:
            continue
        log('%s: hex-32 строк %d' % (name, len(found)))
        for pos in found[:20]:
            token = data[pos:pos + 32].decode('ascii', 'ignore')
            text, ints = ascii_around(data, pos)
            log('   +0x%x %s' % (pos, token))
            log('      вокруг: %s' % text)
            log('      числа рядом: %s' % ', '.join(str(v) for v in ints))
            if token.lower() not in (KNOWN_SAMPLE,) and not token.startswith('0123456789abcdef'):
                for v in ints[:6]:
                    pairs.append({'api_id': v, 'api_hash': token.lower(), 'source': name})

    # ---------------------------------------------------------------- все файлы
    section('поиск известных ключей по всем файлам APK (как байты)')
    for token in [KNOWN_SAMPLE] + INTERESTING:
        places = [m.start() for m in re.finditer(re.escape(token).encode(), raw)]
        places2 = []
        for name in names:
            try:
                data = zf.read(name)
            except Exception:
                continue
            if token.encode() in data:
                places2.append(name)
        log('%s: в сыром APK %d совпадений; в файлах: %s' % (token, len(places), places2 or 'нет'))

    # ---------------------------------------------------------------- упаковщик?
    section('проверка: не защищён ли APK упаковщиком')
    packers = ['libjiagu', 'libDexHelper', 'libSecShell', 'libshell', 'libmobisec',
               'libnesec', 'libbaiduprotect', 'libAPKProtect', 'libexec.so', 'libexecmain',
               'libprotectClass', 'libtup', 'StubApp', 'com.tencent.StubShell']
    for name in names:
        for p in packers:
            if p.lower() in name.lower():
                log('   ПРИЗНАК УПАКОВЩИКА: %s' % name)
    dex_sizes = [zf.getinfo(n).file_size for n in dexes]
    log('   размеры dex: %s' % dex_sizes)
    log('   всего классов в dex (приблизительно): %d' % sum(
        struct.unpack_from('<I', zf.read(n), 0x60)[0] for n in dexes if zf.read(n)[:4] == b'dex\n'))

    # ---------------------------------------------------------------- кандидаты
    seen = set()
    uniq = []
    for p in pairs:
        key = (p['api_id'], p['api_hash'])
        if key in seen:
            continue
        seen.add(key)
        uniq.append(p)
    # формат, который понимает tools/check_keys.py: [название, api_id, api_hash]
    cand = [[p['source'], p['api_id'], p['api_hash']] for p in uniq[:12]]
    cand_path = os.path.splitext(args.out)[0] + '-candidates.json'
    io.open(cand_path, 'w', encoding='utf-8').write(json.dumps(cand, ensure_ascii=False, indent=1))
    log('')
    log('пар-кандидатов для проверки на сервере: %d -> %s' % (len(cand), cand_path))
    for item in cand:
        log('   api_id=%s api_hash=%s (%s)' % (item[1], item[2], item[0]))

    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    io.open(args.out, 'w', encoding='utf-8').write('\n'.join(OUT) + '\n')
    print('отчёт: %s' % args.out)
    return 0


if __name__ == '__main__':
    sys.exit(main())
