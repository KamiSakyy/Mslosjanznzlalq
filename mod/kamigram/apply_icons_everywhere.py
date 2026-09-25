#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KAMIGRAM_ICON_EVERYWHERE_R101 — иконка Sakura вместо иконки Telegram ВЕЗДЕ.

Жалоба пользователя: «иконка приложения должна быть везде, замени оригинальную
иконку телеграм на нашу иконку во всех местах». Лаунчер-иконка уже заменена
(P2A), но в приложении оставались оригинальные телеграм-ассеты:

  * notification.webp        — силуэт-самолётик в статус-баре и уведомлениях;
  * ic_launcher_dr.webp      — крупная иконка уведомлений / VoIP / auth.xml;
  * logo_middle              — логотип Telegram в экране правил (TermsOfServiceView);
  * intro_tg_plane.webp      — самолётик в анимации входа (IntroActivity, GL);
  * menu_invit_telegram.webp — иконка «Пригласить в Telegram» в меню чата;
  * book_logo.webp           — книжка-логотип Telegram (MediaDataController);
  * menu_intro.webp          — иконка пункта меню ботов;
  * menu_feature_intro.webp  — иконка пункта Premium-возможностей.

Никакой генерации арта: берётся проверенный исходник
mod/kamigram/kamigram_icon_artwork.jpg (существующее изображение Эмилии),
ImageMagick делает из него варианты нужных размеров и форматов.

Правила замены:
  * формат и пиксельные размеры каждого файла сохраняются 1-в-1 (webp/png),
    чтобы AAPT2 и код ничего не заметили;
  * цветные ассеты — круглая иконка с артом (как лаунчер);
  * notification — БЕЛЫЙ силуэт на прозрачном фоне (требование статус-бара:
    альфа-маска из яркости арта, белые зоны = непрозрачные).

Запуск: python3 apply_icons_everywhere.py <artwork.jpg> <путь до res>
"""

import os
import subprocess
import sys
import tempfile

MARKER = "KAMIGRAM_ICON_EVERYWHERE_R101"

# имя ассета -> режим замены
TARGETS = {
    "notification": "silhouette",
    "ic_launcher_dr": "color",
    "logo_middle": "color",
    "intro_tg_plane": "color",
    "menu_invit_telegram": "color",
    "book_logo": "color",
    "menu_intro": "color",
    "menu_feature_intro": "color",
}


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.PIPE).strip()


def main(argv):
    if len(argv) != 3:
        sys.stderr.write("usage: apply_icons_everywhere.py <artwork> <res>\n")
        return 2
    artwork, res = argv[1], argv[2]
    if not os.path.isfile(artwork) or not os.path.isdir(res):
        sys.stderr.write("ICONS: нет арта или res\n")
        return 2

    work = tempfile.mkdtemp(prefix="sakura_icons_")
    square = os.path.join(work, "square.png")
    run("convert", artwork, "-auto-orient", "-gravity", "North",
        "-resize", "512x512^", "-extent", "512x512", square)

    circle_cache = {}
    sil_cache = {}

    def circle(side):
        path = circle_cache.get(side)
        if path:
            return path
        path = os.path.join(work, "circle_%d.png" % side)
        sized = os.path.join(work, "sized_%d.png" % side)
        mask = os.path.join(work, "mask_%d.png" % side)
        run("convert", square, "-resize", "%dx%d^" % (side, side),
            "-gravity", "center", "-extent", "%dx%d" % (side, side), sized)
        center = side // 2
        run("convert", "-size", "%dx%d" % (side, side), "xc:none", "-fill", "white",
            "-draw", "circle %d,%d %d,0" % (center, center, center), mask)
        run("convert", sized, mask, "-alpha", "off", "-compose", "CopyOpacity",
            "-composite", path)
        circle_cache[side] = path
        return path

    def silhouette(side):
        path = sil_cache.get(side)
        if path:
            return path
        alpha = os.path.join(work, "alpha_%d.png" % side)
        white = os.path.join(work, "white_%d.png" % side)
        masked = os.path.join(work, "silm_%d.png" % side)
        path = os.path.join(work, "sil_%d.png" % side)
        run("convert", square, "-resize", "%dx%d^" % (side, side),
            "-gravity", "center", "-extent", "%dx%d" % (side, side),
            "-colorspace", "Gray", "-auto-level", "-level", "35%,65%", alpha)
        run("convert", "-size", "%dx%d" % (side, side), "xc:white", white)
        run("convert", white, alpha, "-alpha", "off", "-compose", "CopyOpacity",
            "-composite", masked)
        center = side // 2
        mask = os.path.join(work, "smask_%d.png" % side)
        run("convert", "-size", "%dx%d" % (side, side), "xc:none", "-fill", "white",
            "-draw", "circle %d,%d %d,0" % (center, center, center), mask)
        run("convert", masked, mask, "-alpha", "on", "-compose", "DstIn",
            "-composite", path)
        sil_cache[side] = path
        return path

    replaced = 0
    failed = []
    for folder in sorted(os.listdir(res)):
        if not folder.startswith("drawable"):
            continue
        for name, mode in TARGETS.items():
            for entry in sorted(os.listdir(os.path.join(res, folder))):
                if not entry.startswith(name + "."):
                    continue
                target = os.path.join(res, folder, entry)
                width, height, fmt = output("identify", "-format", "%w %h %m", target).split()
                width, height = int(width), int(height)
                side = min(width, height)
                if side < 8:
                    failed.append("%s: слишком маленький" % target)
                    continue
                base = silhouette(side) if mode == "silhouette" else circle(side)
                staged = os.path.join(work, "stage.png")
                if width == height:
                    run("convert", base, staged)
                else:
                    run("convert", "-size", "%dx%d" % (width, height), "xc:none",
                        base, "-gravity", "center", "-composite", staged)
                if fmt.lower() == "png" or entry.endswith(".png"):
                    run("convert", staged, target)
                else:
                    run("convert", staged, "-quality", "92", target)
                replaced += 1

    if failed:
        for item in failed:
            sys.stderr.write("ICONS: ✗ %s\n" % item)
        return 1
    if replaced == 0:
        sys.stderr.write("ICONS: ✗ ни один ассет не заменён\n")
        return 1
    print("ICONS: %s: заменено ассетов: %d (иконка Sakura везде вместо Telegram)" % (MARKER, replaced))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
