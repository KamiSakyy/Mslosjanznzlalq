#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r114: медиафайлы (аудио/музыка/видео/фото) больше не удаляются НИКАКИМИ
автоматическими потоками — кэш чистится только штатными настройками Telegram.

Глубокая проверка после r111/r113 нашла оставшиеся источники:

  1. СЕРВЕРНЫЕ удаления: updateDeleteMessages/updateDeleteHistory (автоудаление
     по таймеру канала/чата, чистка истории админом, удаление с другого
     устройства) идут через MessagesStorage.markMessagesAsDeletedInternal ->
     FileLoader.deleteFiles, МИНУЯ защиту KEEP_DELETED (она стояла только в
     MessagesController.deleteMessages). Файлы аудио/музыки стирались именно так.
  2. putMessages: при замене сообщения старая медиа-копия удалялась, а для
     каждого сообщения с ttl_period задача автоудаления ЗАПИСЫВАЛАСЬ ЗАНОВО
     (putMessages и putMessagesInternal) — r111 закрыл три других пути, но не эти.

Правки:
  * ВСЕ шесть точек getFileLoader().deleteFiles(...) в MessagesStorage
    выполняются только если пользователь сам выключил «Удалённые сообщения
    остаются» (keepDeleted, по умолчанию ВКЛЮЧЕНО). Удаление самих сообщений
    и синхронизация с сервером не затрагиваются — исчезают только стирания
    файлов; файлы живут до ручной очистки в штатных настройках кэша.
  * Создание задач автоудаления в putMessages/putMessagesInternal/
    putDialogsInternal выключено (маркер KAMIGRAM_TTL_NO_LOCAL_TASKS_R114).

Запуск: python3 apply_r114_fixes.py <TG_DIR>
"""
import io
import os
import re
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
PATH = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java")

FILES_MARK = "KAMIGRAM_FILES_SURVIVE_R114"
TASKS_MARK = "KAMIGRAM_TTL_NO_LOCAL_TASKS_R114"


def main():
    source = io.open(PATH, encoding="utf-8", errors="replace").read()
    if FILES_MARK in source and TASKS_MARK in source:
        print("r114 files survive: уже применено")
        return 0

    # 1) все точки deleteFiles(filesToDelete, ...) — только при выключенном keepDeleted
    pattern = re.compile(
        r"^([ \t]+)getFileLoader\(\)\.deleteFiles\(filesToDelete, (0|messagesOnly)\);$",
        re.MULTILINE,
    )
    found = pattern.findall(source)
    if len(found) != 6:
        print("r114: ожидалось 6 точек deleteFiles, найдено %d" % len(found), file=sys.stderr)
        return 1

    def guard(match):
        indent = match.group(1)
        arg = match.group(2)
        return (
            indent + "/* " + FILES_MARK + ": медиафайлы переживают любое удаление сообщений —\n"
            + indent + "   кэш чистится только штатными настройками Telegram. */\n"
            + indent + "if (!org.telegram.messenger.kamigram.KamiGramConfig.keepDeleted()) {\n"
            + indent + "    getFileLoader().deleteFiles(filesToDelete, " + arg + ");\n"
            + indent + "}"
        )

    source = pattern.sub(guard, source)

    # 2) putMessages/putMessagesInternal/putDialogsInternal: задачи автоудаления не создаются
    task_anchor = "if (message.ttl_period != 0 && message.id > 0) {"
    count = source.count(task_anchor)
    if count != 3:
        print("r114: ожидалось 3 создания ttl-задач, найдено %d" % count, file=sys.stderr)
        return 1
    source = source.replace(
        task_anchor,
        "if (message.ttl_period != 0 && message.id > 0 && false) { /* " + TASKS_MARK + " */",
    )

    io.open(PATH, "w", encoding="utf-8").write(source)
    print("r114 files survive: медиафайлы не удаляются автоматически, ttl-задачи в putMessages выключены")
    return 0


if __name__ == "__main__":
    sys.exit(main())
