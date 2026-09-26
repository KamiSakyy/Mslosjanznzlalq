#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r112: иконка загрузки снова открывает «Загрузки», а не «Фото».

Причина: P114 (усиленный глобальный поиск) вставляет вкладки «Фото», «Видео»
и «GIF» в начало списка вкладок SearchViewPager — сразу после «Медиа».
Штатный showDownloads() выбирает вкладку «Загрузки» по ЖЁСТКОМУ индексу
(setPosition(5)), а после вставки трёх вкладок «Загрузки» съехали на индекс
8, и по индексу 5 теперь открывается «Фото».

Исправление (маркер KAMIGRAM_DOWNLOADS_TAB_R112): showDownloads() ищет
вкладку типа DOWNLOADS_TYPE динамически; запасной вариант — прежний индекс.

Запуск: python3 apply_r112_fixes.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
PATH = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/ui/Components/SearchViewPager.java")

MARK = "KAMIGRAM_DOWNLOADS_TAB_R112"


def main():
    source = io.open(PATH, encoding="utf-8", errors="replace").read()
    if MARK in source:
        print("r112 downloads tab: уже применено")
        return 0

    old = (
        "    public void showDownloads() {\n"
        "        setPosition((expandedPublicPosts ? 1 : 0) + 5);\n"
        "    }\n"
    )
    if source.count(old) != 1:
        print("r112: якорь showDownloads найден %d раз" % source.count(old), file=sys.stderr)
        return 1

    new = (
        "    public void showDownloads() {\n"
        "        /* " + MARK + ": вкладка «Загрузки» ищется по типу, а не по\n"
        "           жёсткому индексу — вкладки «Фото»/«Видео»/«GIF» сдвинули её\n"
        "           позицию, и иконка загрузки открывала «Фото». */\n"
        "        int downloadsPosition = -1;\n"
        "        for (int i = 0; i < viewPagerAdapter.items.size(); i++) {\n"
        "            if (viewPagerAdapter.items.get(i).type == ViewPagerAdapter.DOWNLOADS_TYPE) {\n"
        "                downloadsPosition = i;\n"
        "                break;\n"
        "            }\n"
        "        }\n"
        "        if (downloadsPosition >= 0) {\n"
        "            setPosition(downloadsPosition);\n"
        "        } else {\n"
        "            setPosition((expandedPublicPosts ? 1 : 0) + 5);\n"
        "        }\n"
        "    }\n"
    )
    source = source.replace(old, new, 1)
    io.open(PATH, "w", encoding="utf-8").write(source)
    print("r112 downloads tab: иконка загрузки открывает «Загрузки»")
    return 0


if __name__ == "__main__":
    sys.exit(main())
