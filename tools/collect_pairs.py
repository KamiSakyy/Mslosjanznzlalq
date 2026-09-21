#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Собрать найденные в APK пары api_id/api_hash в один JSON.

Использование: python3 tools/collect_pairs.py /tmp/other/*.json > found.json
"""
import json
import sys


def main():
    pairs = []
    for path in sys.argv[1:]:
        try:
            data = json.load(open(path, encoding='utf-8'))
        except Exception:
            continue
        apk = data.get('apk', '')
        for entry in data.get('pairs', []):
            cls, app_id, app_hash = entry[0], entry[1], entry[2]
            if app_id and app_hash:
                pairs.append(['%s %s' % (apk, cls), int(app_id), app_hash])
    json.dump({'pairs': pairs}, sys.stdout, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main()
