#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Register the separate KamiGram attheme without replacing Telegram themes."""

import io
import os
import sys

MARK = "KAMIGRAM_THEME_REGISTRATION"
ANCHOR = '        themesDict.put("Night", themeInfo);\n'
INSERT = '''
        /* KAMIGRAM_THEME_REGISTRATION: KamiGram is a separate built-in theme.
           Blue/Night/Dark Blue/Day/Arctic remain stock Telegram themes so the
           settings switch can restore them exactly. */
        themeInfo = new ThemeInfo();
        themeInfo.name = "KamiGram";
        themeInfo.assetName = "kamigram.attheme";
        themeInfo.previewBackgroundColor = 0xff0d0b12;
        themeInfo.previewInColor = 0xff1c1724;
        themeInfo.previewOutColor = 0xff2a2138;
        themeInfo.firstAccentIsDefault = true;
        themeInfo.currentAccentId = DEFALT_THEME_ACCENT_ID;
        themeInfo.sortIndex = 5;
        themes.add(themeInfo);
        themesDict.put("KamiGram", themeInfo);
'''


def main():
    if len(sys.argv) != 2:
        print("usage: apply_theme_registration.py Theme.java", file=sys.stderr)
        return 2
    path = sys.argv[1]
    source = io.open(path, encoding="utf-8").read()
    if MARK in source:
        print("P16: тема KamiGram уже зарегистрирована")
        return 0
    if ANCHOR not in source:
        print("P16: не найден якорь Night theme", file=sys.stderr)
        return 1
    source = source.replace(ANCHOR, ANCHOR + INSERT, 1)
    io.open(path, "w", encoding="utf-8").write(source)
    print("P16: отдельная тема KamiGram зарегистрирована")
    return 0


if __name__ == "__main__":
    sys.exit(main())
