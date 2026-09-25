#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sakura r68 source patches.

The native Telegram send path is intentionally left untouched. In particular,
normal outgoing messages never receive a synthetic date and are not converted
to scheduled/ghost messages. Only the independent UI, archive, download-idle,
media-retention and deleted-message hooks below are installed.
"""

import io
import os
import sys

DONE = []
MISS = []
TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
APP_NAME = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("APP_NAME", "Sakura")
JAVA = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram")
DEL = "org.telegram.messenger.kamigram.KamiGramDeleted"


def path(rel):
    return os.path.join(JAVA, *rel.split("/"))


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, src):
    io.open(p, "w", encoding="utf-8").write(src)


def patch(rel, marker, anchor, insert, what, after=True):
    p = path(rel)
    try:
        src = read(p)
    except Exception as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in src:
        return True
    if anchor not in src:
        MISS.append("%s: нет якоря (%s)" % (rel, what))
        return False
    replacement = anchor + insert if after else insert + anchor
    write(p, src.replace(anchor, replacement, 1))
    DONE.append(what)
    return True


def replace(rel, marker, old, new, what):
    p = path(rel)
    try:
        src = read(p)
    except Exception as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in src:
        return True
    if old not in src:
        MISS.append("%s: нет строки (%s)" % (rel, what))
        return False
    write(p, src.replace(old, new, 1))
    DONE.append(what)
    return True


def native_send():
    """Document the deliberate no-op without changing SendMessagesHelper."""
    DONE.append("native Telegram send path preserved; no synthetic schedule date")


def auto_archive():
    patch(
        "ui/LaunchActivity.java",
        "KAMIGRAM_AUTO_ARCHIVE",
        "        org.telegram.messenger.kamigram.KamiGramProxyPower.init(); // KAMIGRAM_PROXY_POWER\n",
        "        org.telegram.messenger.kamigram.KamiGramAutoArchive.init(); // KAMIGRAM_AUTO_ARCHIVE\n",
        "авто-архив: наблюдение за списком чатов включено",
    )


def folder_counter():
    replace(
        "ui/Components/FilterTabsView.java",
        "KAMIGRAM_TAB_UNREAD_COLOR",
        "                unreadKey = Theme.key_chats_tabUnreadUnactiveBackground;\n"
        "                unreadOtherKey = Theme.key_chats_tabUnreadActiveBackground;\n",
        "                /* KAMIGRAM_TAB_UNREAD_COLOR: у НЕвыбранной папки счётчик\n"
        "                   непрочитанных рисуется теми же цветами, что у выбранной. */\n"
        "                unreadKey = Theme.key_chats_tabUnreadActiveBackground;\n"
        "                unreadOtherKey = Theme.key_chats_tabUnreadUnactiveBackground;\n",
        "папки: счётчик непрочитанных у невыбранной вкладки — нашего цвета",
    )


def downloads_idle():
    icon = "ui/DownloadProgressIcon.java"
    patch(
        icon,
        "KAMIGRAM_NO_FAKE_DOWNLOAD_IDLE",
        "        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(8);\n"
        "        float r = AndroidUtilities.dp(1f);\n",
        "        /* KAMIGRAM_NO_FAKE_DOWNLOAD_IDLE: idle — спокойная целая иконка,\n"
        "           без точки и без ложной анимации. Движение разрешено только\n"
        "           пока реально меняется число байтов. */\n"
        "        final boolean kamigramIdle = currentListeners.isEmpty() && !hasUnviewedDownloads;\n"
        "        if (kamigramIdle) {\n"
        "            if (kamigramDownloadAnim) {\n"
        "                kamigramDownloadAnim = false;\n"
        "                downloadDrawable.stop();\n"
        "                downloadDrawable.setCurrentFrame(0, false);\n"
        "            }\n"
        "        } else if (!kamigramDownloadAnim && progress != 1f) {\n"
        "            kamigramDownloadAnim = true;\n"
        "            downloadDrawable.start();\n"
        "        }\n",
        "загрузки: в покое иконка статичная, без точки и анимации",
        after=False,
    )
    replace(
        icon,
        "KAMIGRAM_NO_FAKE_DOWNLOAD_BAR",
        "        AndroidUtilities.rectTmp.set(startPadding, cy - r, getMeasuredWidth() - startPadding, cy + r);\n"
        "        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint2);\n\n"
        "        AndroidUtilities.rectTmp.set(startPadding, cy - r, startPadding + width * currentProgress, cy + r);\n"
        "        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);\n",
        "        /* KAMIGRAM_NO_FAKE_DOWNLOAD_BAR: полоска только при реальной загрузке. */\n"
        "        if (!kamigramIdle) {\n"
        "            AndroidUtilities.rectTmp.set(startPadding, cy - r, getMeasuredWidth() - startPadding, cy + r);\n"
        "            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint2);\n\n"
        "            AndroidUtilities.rectTmp.set(startPadding, cy - r, startPadding + width * currentProgress, cy + r);\n"
        "            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);\n"
        "        }\n",
        "загрузки: полоска прогресса только при реальной загрузке",
    )
    replace(
        icon,
        "KAMIGRAM_NO_FAKE_DOWNLOAD_LOOP",
        "            if (downloadDrawable.getCurrentFrame() == 0) {\n"
        "                downloadCompleteDrawable.setCurrentFrame(0, false);\n"
        "                downloadCompleteDrawable.start();\n"
        "                showCompletedIcon = true;\n"
        "            }\n",
        "            if (downloadDrawable.getCurrentFrame() == 0) {\n"
        "                /* KAMIGRAM_NO_FAKE_DOWNLOAD_LOOP: галочка проигрывается один раз. */\n"
        "                downloadCompleteDrawable.setAutoRepeat(0);\n"
        "                downloadCompleteDrawable.setCurrentFrame(0, false);\n"
        "                downloadCompleteDrawable.start();\n"
        "                showCompletedIcon = true;\n"
        "            }\n",
        "загрузки: «галочка» не зацикливается",
    )


def title_lock():
    replace(
        "ui/ActionBar/ActionBar.java",
        "KAMIGRAM_TITLE_REFRESH",
        "        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n"
        "            return;\n"
        "        }\n",
        "        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n"
        "            /* KAMIGRAM_TITLE_REFRESH: главный экран не подменяет заголовок. */\n"
        "            org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();\n"
        "            return;\n"
        "        }\n",
        "шапка: имя Sakura возвращается, если его подменили",
    )


def keep_viewonce_media():
    storage = "messenger/MessagesStorage.java"
    replace(
        storage,
        "KAMIGRAM_KEEP_VIEWONCE_MEDIA2",
        "    public void emptyMessagesMedia(long dialogId, ArrayList<Integer> mids) {\n"
        "        storageQueue.postRunnable(() -> {\n"
        "            SQLiteCursor cursor = null;\n",
        "    public void emptyMessagesMedia(long dialogId, ArrayList<Integer> mids) {\n"
        "        /* KAMIGRAM_KEEP_VIEWONCE_MEDIA2: не превращаем сохранённое медиа\n"
        "           защищённых/одноразовых сообщений в photoEmpty. */\n"
        "        if (org.telegram.messenger.kamigram.KamiGramConfig.keepExpiredMedia()) {\n"
        "            return;\n"
        "        }\n"
        "        storageQueue.postRunnable(() -> {\n"
        "            SQLiteCursor cursor = null;\n",
        "одноразовые фото: «истёкшая фотография» больше не появляется",
    )
    replace(
        storage,
        "KAMIGRAM_KEEP_VIEWONCE_MEDIA3",
        "                if (arrayList != null) {\n"
        "                    emptyMessagesMedia(dialogId, arrayList);\n"
        "                }\n",
        "                /* KAMIGRAM_KEEP_VIEWONCE_MEDIA3: медиа остаётся локально. */\n",
        "самоуничтожающиеся фото: медиа не вычищается после прочтения",
    )


def keep_deleted_private():
    patch(
        "messenger/MessagesController.java",
        "KAMIGRAM_KEEP_DELETED_PRIVATE",
        "                    /* KAMIGRAM_KEEP_DELETED_PEER: удалённое собеседником остаётся у нас */\n"
        "                    " + DEL + ".remember(dialogId, arrayList);\n",
        "                    /* KAMIGRAM_KEEP_DELETED_PRIVATE: личные чаты присылают\n"
        "                       удаление без id чата — храним по номеру сообщения. */\n"
        "                    if (dialogId == 0) {\n"
        "                        " + DEL + ".rememberUnknown(arrayList);\n"
        "                    } else {\n"
        "                        " + DEL + ".remember(dialogId, arrayList);\n"
        "                    }\n",
        "удалённые: личные чаты тоже удерживают сообщения",
    )


def main():
    native_send()
    auto_archive()
    folder_counter()
    downloads_idle()
    title_lock()
    keep_viewonce_media()
    keep_deleted_private()
    print("r68: изменений — %d" % len(DONE))
    for what in DONE:
        print("  ✓ %s" % what)
    if MISS:
        print("r68: пропущено — %d" % len(MISS))
        for what in MISS:
            print("  ! %s" % what)
    return 0


if __name__ == "__main__":
    sys.exit(main())
