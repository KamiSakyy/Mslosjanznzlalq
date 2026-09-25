#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KAMIGRAM_OVERLAY_POWER_R104 — видео поверх приложений работает и просит разрешение.

Жалоба пользователя (r103): «видео не работает поверх приложений, работает
только картинка в картинке, куда делось разрешение поверх приложений?».

Разбор 12.10.3:
  * плавающее окно поверх ВСЕХ приложений — это PipVideoOverlay (window type
    SYSTEM_ALERT_WINDOW), требует разрешение «Поверх всех окон»;
  * PipVideoOverlay.showInternal() включает inAppOnly, если выдано только
    системное PiP-разрешение (PipPermissions.PIP_GRANTED_PIP) — тогда окошко
    живёт ТОЛЬКО внутри Telegram, «поверх приложений» не работает;
  * штатный диалог запроса overlay-разрешения показывался лишь когда НЕ было
    ни одного pip-разрешения (PhotoViewer.checkInlinePermissions), а системное
    PiP у пользователя есть — поэтому диалог не появлялся НИКОГДА:
    разрешение «пропало» из опыта пользователя.

Лечение:
  * checkInlinePermissions(): overlay выдано -> плавающее окно поверх всех
    приложений; не выдано -> один раз за запуск показываем штатный системный
    диалог «Разрешить поверх других приложений» и до выдачи работаем как
    раньше (in-app / системное PiP);
  * в центре Sakura есть пункт «Видео поверх приложений», который открывает
    системную страницу разрешения (KamiGramCenter.openOverlaySettings).

Запуск: python3 apply_r104_fixes.py <путь до TMessagesProj/src/main>
"""

import io
import os
import sys

MARKER = "KAMIGRAM_OVERLAY_POWER_R104"


def read(path):
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def main(argv):
    if len(argv) != 2:
        sys.stderr.write("usage: apply_r104_fixes.py <TMessagesProj/src/main>\n")
        return 2
    java_root = os.path.join(argv[1], "java")
    path = os.path.join(java_root, "org", "telegram", "ui", "PhotoViewer.java")
    if not os.path.isfile(path):
        sys.stderr.write("R104: нет PhotoViewer.java\n")
        return 2

    old = (
        "    private boolean checkInlinePermissions() {\n"
        "        if (parentActivity == null) {\n"
        "            return false;\n"
        "        }\n"
        "        if (Build.VERSION.SDK_INT < 23 || PipUtils.checkAnyPipPermissions(parentActivity)) {\n"
        "            return true;\n"
        "        } else {\n"
        "            AlertsCreator.createDrawOverlayPermissionDialog(parentActivity, null, true).show();\n"
        "        }\n"
        "        return false;\n"
        "    }\n"
    )
    new = (
        "    private boolean checkInlinePermissions() {\n"
        "        if (parentActivity == null) {\n"
        "            return false;\n"
        "        }\n"
        "        if (Build.VERSION.SDK_INT < 23) {\n"
        "            return true;\n"
        "        }\n"
        "        /* %s: «видео поверх приложений» — основной режим плавающего\n"
        "           окна. Разрешение «поверх других приложений» выдано — окно живёт\n"
        "           над всеми приложениями. Не выдано — один раз за запуск показываем\n"
        "           штатный системный диалог запроса (раньше он не появлялся никогда,\n"
        "           если системное PiP уже выдано), а до выдачи работаем как прежде. */\n"
        "        if (android.provider.Settings.canDrawOverlays(parentActivity)) {\n"
        "            org.telegram.messenger.ApplicationLoader.canDrawOverlays = true;\n"
        "            return true;\n"
        "        }\n"
        "        if (!org.telegram.messenger.kamigram.KamiGramCenter.overlayPromptShown) {\n"
        "            org.telegram.messenger.kamigram.KamiGramCenter.overlayPromptShown = true;\n"
        "            AlertsCreator.createDrawOverlayPermissionDialog(parentActivity, null, true).show();\n"
        "        }\n"
        "        return PipUtils.checkAnyPipPermissions(parentActivity);\n"
        "    }\n" % MARKER
    )

    source = read(path)
    if MARKER in source:
        print("R104: уже применено")
    elif old not in source:
        sys.stderr.write("R104: не найден якорь checkInlinePermissions\n")
        return 1
    else:
        write(path, source.replace(old, new, 1))
        print("R104: ✓ overlay-приоритет и запрос разрешения «поверх приложений»")

    center_path = os.path.join(java_root, "org", "telegram", "messenger", "kamigram", "KamiGramCenter.java")
    if not os.path.isfile(center_path):
        sys.stderr.write("R104: нет KamiGramCenter.java для флага overlay\n")
        return 1
    fsrc = read(center_path)
    if "overlayPromptShown" in fsrc:
        print("R104: флаг overlay уже в KamiGramCenter")
        return 0
    anchor = "public final class KamiGramCenter {"
    if anchor not in fsrc:
        anchor = "public class KamiGramCenter {"
    if anchor not in fsrc:
        sys.stderr.write("R104: не найден класс KamiGramCenter\n")
        return 1
    fsrc = fsrc.replace(
        anchor,
        anchor + "\n\n    /** r104: диалог запроса overlay-разрешения показан за этот запуск. */\n"
                 "    public static boolean overlayPromptShown;\n",
        1,
    )
    write(center_path, fsrc)
    print("R104: ✓ флаг запроса overlay в KamiGramCenter")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
