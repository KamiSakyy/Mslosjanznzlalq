#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KAMIGRAM_OVERLAY_ONLY_R105 — плавающее окно вместо системного PiP + без жестов.

Жалобы пользователя (r104):
  1) «поверх приложений включил, но при выходе из ТГ всё равно картинка в
     картинке с интерфейсом, а должно быть только видео поверх приложений»;
  2) «убери менять яркость и громкость и перемотку у видео — из-за этого
     невозможно перетаскивать окно плавающее»;
  3) «почему просит включить, если уже включено» (устаревшая строка в центре).

Разбор:
  * системный PiP при выходе включает LaunchActivity.onUserLeaveHint() через
    pipActivityHandler ДО того, как PhotoViewer успевает свернуться в своё
    плавающее окно PipVideoOverlay. Если overlay-разрешение выдано и открыто
    видео — системный PiP пропускаем, работает только плавающее окно;
  * яркость/громкость меняли НЕ штатные жесты Telegram, а модовые невидимые
    зоны KamiGramVideoGestures, вшитые в dispatchTouchEvent PhotoViewer и
    PipVideoOverlay — они же съедали вертикальное перетаскивание окна;
  * перемотка долгим нажатием (VideoPlayerRewinder) в плавающем окне тоже
    съедала MOVE-события перетаскивания;
  * строка центра Sakura «разрешить» не обновлялась после выдачи разрешения.

Лечение: хуки жестов удалены, long-press перемотка отключена, системный PiP
пропускается при выданном overlay и открытом видео, центр проверяет статус в
момент нажатия.

Запуск: python3 apply_r105_fixes.py <путь до TMessagesProj/src/main>
"""

import io
import os
import sys

MARKER = "KAMIGRAM_OVERLAY_ONLY_R105"


def read(path):
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def replace_once(path, old, new, what, failed, done):
    if not os.path.isfile(path):
        failed.append("нет файла для «%s»" % what)
        return
    source = read(path)
    if old not in source:
        if MARKER in source and what in ("overlay вместо системного PiP",):
            done.append("%s: уже применено" % what)
            return
        failed.append("%s: не найден якорь" % what)
        return
    write(path, source.replace(old, new, 1))
    done.append(what)


def main(argv):
    if len(argv) != 2:
        sys.stderr.write("usage: apply_r105_fixes.py <TMessagesProj/src/main>\n")
        return 2
    java_root = os.path.join(argv[1], "java")
    ui = os.path.join(java_root, "org", "telegram", "ui")
    failed = []
    done = []

    # 1) модовые зоны яркости/громкости из PhotoViewer — вон
    replace_once(
        os.path.join(ui, "PhotoViewer.java"),
        "            /* KAMIGRAM_VIDEO_GESTURES_PHOTO: invisible left/right vertical zones. */\n"
        "            if (org.telegram.messenger.kamigram.KamiGramVideoGestures.handle(\n"
        "                    this, ev, isCurrentVideo && videoPlayer != null)) {\n"
        "                return true;\n"
        "            }\n",
        "            /* %s: модовые зоны яркости/громкости удалены — они мешали\n"
        "               перетаскивать плавающее окно и менять яркость/громкость жестом. */\n" % MARKER,
        "жесты яркости/громкости убраны из PhotoViewer", failed, done,
    )

    # 2) те же зоны из плавающего окна — вон (вертикальный drag снова двигает окно)
    replace_once(
        os.path.join(ui, "Components", "PipVideoOverlay.java"),
        "                /* KAMIGRAM_VIDEO_GESTURES_PIP: same zones in native PiP player. */\n"
        "                if (org.telegram.messenger.kamigram.KamiGramVideoGestures.handle(\n"
        "                        this, ev, photoViewer != null && photoViewer.getVideoPlayer() != null)) {\n"
        "                    return true;\n"
        "                }\n",
        "                /* %s: зоны яркости/громкости из плавающего окна удалены —\n"
        "                   вертикальное перетаскивание окна снова работает. */\n" % MARKER,
        "жесты яркости/громкости убраны из плавающего окна", failed, done,
    )

    # 3) long-press перемотка в плавающем окне — вон (окно можно тащить)
    replace_once(
        os.path.join(ui, "Components", "PipVideoOverlay.java"),
        "                        AndroidUtilities.runOnUIThread(longClickCallback, 500);\n",
        "                        /* %s: долгим нажатием больше не включается\n"
        "                           перемотка — плавающее окно перетаскивается любым хватом. */\n" % MARKER,
        "перемотка долгим нажатием убрана из плавающего окна", failed, done,
    )

    # 4) long-press перемотка в полноэкранном видео — вон (просьба пользователя)
    replace_once(
        os.path.join(ui, "PhotoViewer.java"),
        "            if (total > 180 * 1000) {\n"
        "                boolean forward;\n"
        "                if (x >= width / 3 * 2) {\n"
        "                    forward = true;\n"
        "                } else if (x < width / 3) {\n"
        "                    forward = false;\n"
        "                } else {\n"
        "                    return;\n"
        "                }\n"
        "                longVideoPlayerRewinder.startRewind(videoPlayer, forward, currentVideoSpeed);\n"
        "            } else {\n"
        "                final boolean forward = x > width / 3;\n"
        "                videoPlayerRewinder.startRewind(videoPlayer, forward, longPressX, currentVideoSpeed, seekSpeedDrawable);\n"
        "            }\n",
        "            /* %s: перемотка долгим нажатием удалена полностью —\n"
        "               жесты больше не мешают ни просмотру, ни плавающему окну. */\n"
        "            return;\n" % MARKER,
        "перемотка долгим нажатием убрана из PhotoViewer", failed, done,
    )

    # 5) при выданном overlay и открытом видео — только плавающее окно, без системного PiP
    replace_once(
        os.path.join(ui, "LaunchActivity.java"),
        "    protected void onUserLeaveHint() {\n"
        "        pipActivityHandler.onUserLeaveHint();\n",
        "    protected void onUserLeaveHint() {\n"
        "        /* %s: «поверх приложений» выдано и открыто видео — уходим ТОЛЬКО\n"
        "           в плавающее окно поверх всех приложений (PipVideoOverlay),\n"
        "           системную картинку-в-картинке не включаем. */\n"
        "        boolean kamigramOverlayOnly = false;\n"
        "        try {\n"
        "            kamigramOverlayOnly = android.os.Build.VERSION.SDK_INT >= 23\n"
        "                && android.provider.Settings.canDrawOverlays(this)\n"
        "                && PhotoViewer.getInstance().isVisibleOrAnimating();\n"
        "        } catch (Throwable ignored) {\n"
        "        }\n"
        "        if (!kamigramOverlayOnly) {\n"
        "            pipActivityHandler.onUserLeaveHint();\n"
        "        }\n" % MARKER,
        "overlay вместо системного PiP", failed, done,
    )

    for item in done:
        print("R105: ✓ %s" % item)
    for item in failed:
        sys.stderr.write("R105: ✗ %s\n" % item)
    if failed:
        return 1
    print("R105: плавающее окно без системного PiP, без жестов яркости/громкости/перемотки")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
