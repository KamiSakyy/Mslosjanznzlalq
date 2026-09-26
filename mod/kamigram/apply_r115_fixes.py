#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r115: кэш видео/фото/файлов/музыки не очищается сам + звонки через прокси.

1. КЭШ (видео, фото, файлы, музыка) больше не стирается автоматически:
   * единственная автоматическая воронка удаления медиафайлов в Telegram —
     шесть точек FileLoader.deleteFiles, все в MessagesStorage (проверено по
     всему дереву 12.10.3). Ручная очистка кэша (штатные настройки Telegram,
     CacheControlActivity) через них НЕ идёт и продолжает работать;
   * при удалении сообщений (серверные обновления, автоудаление в каналах,
     удаление с другого устройства, ручное удаление сообщения) сами сообщения
     исчезают по протоколу, а медиафайлы ОБЫЧНЫХ сообщений остаются в кэше:
     markMessagesAsDeletedInternal работает в режиме kamigramSurviveMode,
     в котором addFilesToDelete пропускает обычные медиа и по-прежнему
     собирает на удаление файлы сгорающих/одноразовых/секретных медиа
     (TL_message_secret, message.ttl, media.ttl_seconds) — для них всё
     строго как в оригинальном Telegram;
   * задачи локального автоудаления для сообщений с ttl_period (таймер
     автоудаления чатов/каналов) не создаются ни при сохранении сообщений,
     ни при загрузке списка диалогов — именно они стирали музыку/видео/фото
     «сами» (маркер KAMIGRAM_TTL_NO_LOCAL_TASKS_R115).

2. ЗВОНКИ И ВИДЕОЗВОНКИ ЧЕРЕЗ ПРОКСИ (по умолчанию включено, отключается
   в настройках Sakura, «Связь» → «Звонки через прокси»):
   * VoIPService берёт активный SOCKS5-прокси для звонка даже без штатного
     флага «использовать для звонков»;
   * если активный прокси не годится для звонков (MTProto звонки не несёт)
     или выключен — точечно для звонка выбирается SOCKS5 из сохранённого
     списка прокси (KamiGramCallProxy), глобальные настройки не меняются.

Запуск: python3 apply_r115_fixes.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
MS = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java")
VOIP = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/messenger/voip/VoIPService.java")

FILES_MARK = "KAMIGRAM_FILES_SURVIVE_R115"
TASKS_MARK = "KAMIGRAM_TTL_NO_LOCAL_TASKS_R115"
CALLS_MARK = "KAMIGRAM_CALLS_VIA_PROXY_R115"


def die(msg):
    sys.stderr.write("r115: %s\n" % msg)
    sys.exit(1)


def find_method_end(source, open_brace):
    """Индекс закрывающей скобки метода (учитывая строки/символы/комментарии)."""
    depth = 0
    in_str = in_chr = in_line = in_block = False
    i = open_brace
    n = len(source)
    while i < n:
        c = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
        elif in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 1
        elif in_str:
            if c == "\\":
                i += 1
            elif c == '"':
                in_str = False
        elif in_chr:
            if c == "\\":
                i += 1
            elif c == "'":
                in_chr = False
        else:
            if c == "/" and nxt == "/":
                in_line = True
                i += 1
            elif c == "/" and nxt == "*":
                in_block = True
                i += 1
            elif c == '"':
                in_str = True
            elif c == "'":
                in_chr = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    die("не найден конец метода")


def wrap_survive(source, signature):
    """Оборачивает тело метода в kamigramSurviveMode = true / finally false."""
    if source.count(signature) != 1:
        die("сигнатура найдена %d раз: %s" % (source.count(signature), signature[:60]))
    start = source.index(signature)
    brace = source.index("{", start + len(signature) - 1)
    end = find_method_end(source, brace)
    line_start = source.rfind("\n", 0, end) + 1
    source = source[:line_start] + (
        "        } finally {\n"
        "            kamigramSurviveMode = false; /* " + FILES_MARK + " */\n"
        "        }\n"
    ) + source[line_start:]
    source = source[:brace + 1] + (
        "\n        kamigramSurviveMode = true; /* " + FILES_MARK + " */\n"
        "        try {"
    ) + source[brace + 1:]
    return source


def patch_storage():
    source = io.open(MS, encoding="utf-8", errors="replace").read()
    if FILES_MARK in source or TASKS_MARK in source:
        print("r115 storage: уже применено")
        return

    # 1) флаг режима
    anchor = ("    private ArrayList<Long> markMessagesAsDeletedInternal(long dialogId, "
              "ArrayList<Integer> messages, boolean deleteFiles, int mode, int threadMessageId) {\n")
    if source.count(anchor) != 1:
        die("якорь markMessagesAsDeletedInternal(5) найден %d раз" % source.count(anchor))
    source = source.replace(
        anchor,
        "    /* " + FILES_MARK + ": режим «файлы переживают удаление сообщений».\n"
        "       Ставится только на время markMessagesAsDeletedInternal (очередь хранилища\n"
        "       однопоточная). */\n"
        "    private boolean kamigramSurviveMode = false;\n\n" + anchor,
        1,
    )

    # 2) воронка сбора файлов: обычные медиа не собираются на удаление,
    #    сгорающие/одноразовые/секретные — как в оригинале
    collect = ("    private boolean addFilesToDelete(TLRPC.Message message, ArrayList<File> filesToDelete, "
               "ArrayList<Pair<Long, Integer>> ids, ArrayList<String> namesToDelete, boolean forceCache) {\n")
    if source.count(collect) != 1:
        die("якорь addFilesToDelete найден %d раз" % source.count(collect))
    source = source.replace(
        collect,
        collect +
        "        /* " + FILES_MARK + ": медиафайлы обычных сообщений переживают удаление\n"
        "           сообщений (серверные обновления, автоудаление каналов, удаление с другого\n"
        "           устройства) — кэш чистится только штатными настройками Telegram.\n"
        "           Сгорающие/одноразовые/секретные медиа удаляются как в оригинале. */\n"
        "        if (kamigramSurviveMode && message != null) {\n"
        "            final boolean kamigramBurn = message instanceof TLRPC.TL_message_secret\n"
        "                || message.ttl != 0\n"
        "                || (message.media != null && message.media.ttl_seconds != 0);\n"
        "            if (!kamigramBurn) {\n"
        "                return false;\n"
        "            }\n"
        "        }\n",
        1,
    )

    # 3) оба внутренних метода удаления сообщений работают в режиме защиты
    source = wrap_survive(
        source,
        "    private ArrayList<Long> markMessagesAsDeletedInternal(long channelId, int mid, boolean deleteFiles) {\n",
    )
    source = wrap_survive(
        source,
        "    private ArrayList<Long> markMessagesAsDeletedInternal(long dialogId, ArrayList<Integer> messages, boolean deleteFiles, int mode, int threadMessageId) {\n",
    )

    # 4) задачи локального автоудаления (ttl_period) не создаются: именно они
    #    стирали музыку/видео/фото «сами» по таймеру каналов
    task_anchor = "if (message.ttl_period != 0 && message.id > 0) {"
    count = source.count(task_anchor)
    if count != 3:
        die("ожидалось 3 создания ttl-задач, найдено %d" % count)
    source = source.replace(
        task_anchor,
        "if (message.ttl_period != 0 && message.id > 0 && false) { /* " + TASKS_MARK + " */",
    )

    io.open(MS, "w", encoding="utf-8").write(source)
    print("r115 storage: медиафайлы переживают удаления сообщений, ttl-задачи не создаются")


def patch_voip():
    source = io.open(VOIP, encoding="utf-8", errors="replace").read()
    if CALLS_MARK in source:
        print("r115 voip: уже применено")
        return

    old_if = ('\t\t\tif (preferences.getBoolean("proxy_enabled", false) && preferences.getBoolean("proxy_enabled_calls", false)) {\n')
    if source.count(old_if) != 1:
        die("якорь proxy_enabled_calls найден %d раз" % source.count(old_if))
    source = source.replace(
        old_if,
        '\t\t\tif (preferences.getBoolean("proxy_enabled", false) && (preferences.getBoolean("proxy_enabled_calls", false)\n'
        '\t\t\t\t|| org.telegram.messenger.kamigram.KamiGramConfig.callsViaProxy())) { /* ' + CALLS_MARK + ' */\n',
        1,
    )

    anchor = "\t\t\t// encryption key\n"
    if source.count(anchor) != 1:
        die("якорь «// encryption key» найден %d раз" % source.count(anchor))
    source = source.replace(
        anchor,
        '\t\t\t/* ' + CALLS_MARK + ': звонки и видеозвонки всегда через прокси — если активный\n'
        '\t\t\t   прокси не годится для звонков (MTProto) или выключен, точечно берётся\n'
        '\t\t\t   SOCKS5 из сохранённого списка; глобальные настройки не меняются. */\n'
        '\t\t\tif (proxy == null && org.telegram.messenger.kamigram.KamiGramConfig.callsViaProxy()) {\n'
        '\t\t\t\tproxy = org.telegram.messenger.kamigram.KamiGramCallProxy.callProxy();\n'
        '\t\t\t}\n\n' + anchor,
        1,
    )

    io.open(VOIP, "w", encoding="utf-8").write(source)
    print("r115 voip: звонки и видеозвонки идут через прокси (по умолчанию)")


def main():
    patch_storage()
    patch_voip()
    return 0


if __name__ == "__main__":
    sys.exit(main())
