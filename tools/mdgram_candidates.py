#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Собрать кандидатов ключей из отчёта разбора MDGram и отдать JSON для проверки.

Использование: python3 tools/mdgram_candidates.py notes/mdgram-smali.txt > candidates.json
"""
import json
import re
import sys


DEFAULT_IDS = [4, 6, 5, 2040, 21724, 8, 2496, 1025907, 17349, 94575, 16623, 2834, 9, 2899]


def main():
    text = ''
    for path in sys.argv[1:] or ['notes/mdgram-smali.txt']:
        try:
            text += open(path, encoding='utf-8', errors='ignore').read()
        except Exception:
            pass

    hashes = sorted(set(m.group(1).lower() for m in re.finditer(r'\b([0-9a-fA-F]{32})\b', text)))
    ids = set(DEFAULT_IDS)
    for match in re.finditer(r'(?:APP_ID|API_ID|api_id|appId)\D{0,24}(\d{3,9})', text):
        try:
            value = int(match.group(1))
        except ValueError:
            continue
        if 1 <= value <= 99999999:
            ids.add(value)

    pairs = []
    for api_id in sorted(ids):
        for api_hash in hashes[:80]:
            pairs.append(['MDGram %s' % api_id, api_id, api_hash])
    json.dump({'pairs': pairs[:150]}, sys.stdout, ensure_ascii=False, indent=2)
    print('', file=sys.stderr)


if __name__ == '__main__':
    main()
