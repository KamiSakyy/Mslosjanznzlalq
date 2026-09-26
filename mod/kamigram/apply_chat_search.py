#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r109: «Поиск Sakura» в меню чата — серверный поиск по всему каналу/чату
с фильтрами по типам (фото/видео/GIF/медиа/ссылки/файлы/музыка/голосовые).

Точка входа — пункт меню «⋮» рядом с «Массовым выбором» (якоря ставит
apply_bulk_selection.py, поэтому этот патчер запускается ПОСЛЕ него).
r113: пункт снова открывает фрагмент KamiGramChatSearch — расширенный
поиск с фильтрами по типам (все/фото/видео/GIF/медиа/ссылки/файлы/музыка/
голосовые) на родном FilteredSearchView: messages.search с peer чата
находит все сообщения на сервере. В r110/r111 фрагмент не открывался
не из-за своей ошибки, а из-за коллизии id 76 с пунктом «Удалить мои
сообщения» (пункт полностью убран в r112). Добавлена страховка: если
фрагмент по любой причине не открылся — вызывается штатный поиск в чате.

Запуск: python3 apply_chat_search.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
PATH = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")


def main():
    source = io.open(PATH, encoding="utf-8").read()
    if "KAMIGRAM_CHAT_SEARCH" in source:
        print("chat search: уже применено")
        return 0

    # 1) константа пункта меню — сразу за «Массовым выбором»
    const_anchor = "    private final static int kamigram_bulk_select = 75; /* KAMIGRAM_BULK_SELECTION */\n"
    if const_anchor not in source:
        print("chat search: нет якоря константы массового выбора", file=sys.stderr)
        return 1
    source = source.replace(
        const_anchor,
        const_anchor + "    private final static int kamigram_chat_search = 76; /* KAMIGRAM_CHAT_SEARCH */\n",
        1,
    )

    # 2) сам пункт в шапке чата
    menu_anchor = ("            headerItem.lazilyAddSubItem(kamigram_bulk_select, "
                   "R.drawable.msg_select, \"Массовый выбор\"); /* KAMIGRAM_BULK_SELECTION_MENU */\n")
    if menu_anchor not in source:
        print("chat search: нет якоря меню массового выбора", file=sys.stderr)
        return 1
    source = source.replace(
        menu_anchor,
        menu_anchor + ("            headerItem.lazilyAddSubItem(kamigram_chat_search, "
                       "R.drawable.msg_search, \"Поиск Sakura\"); /* KAMIGRAM_CHAT_SEARCH_MENU */\n"),
        1,
    )

    # 3) действие: открыть фрагмент серверного поиска по этому чату
    click_anchor = "                } else if (id == kamigram_bulk_select) {\n"
    if click_anchor not in source:
        print("chat search: нет якоря действия массового выбора", file=sys.stderr)
        return 1
    action = """                } else if (id == kamigram_chat_search) {
                    /* r113: настоящий расширенный поиск по чату/каналу — фрагмент
                       с фильтрами по типам (все/фото/видео/GIF/медиа/ссылки/файлы/
                       музыка/голосовые) поверх штатного серверного messages.search:
                       находятся абсолютно все сообщения на сервере, а не только
                       локально загруженные. В r110/r111 фрагмент не открывался из-за
                       коллизии id 76 с «Удалить мои сообщения» (убраны в r112).
                       Страховка: если фрагмент не открылся — штатный поиск в чате. */
                    boolean kamigramSearchOpened = false;
                    try {
                        Bundle kamigramSearchArgs = new Bundle();
                        kamigramSearchArgs.putLong("dialog_id", getDialogId());
                        kamigramSearchArgs.putLong("topic_id", getTopicId());
                        kamigramSearchOpened = presentFragment(
                            new org.telegram.messenger.kamigram.KamiGramChatSearch(kamigramSearchArgs));
                    } catch (Throwable kamigramSearchError) {
                        kamigramSearchOpened = false;
                    }
                    if (!kamigramSearchOpened) {
                        openSearchWithText(isSupportedTags() ? "" : null);
                    } /* KAMIGRAM_CHAT_SEARCH_ACTION */
"""
    source = source.replace(click_anchor, action + click_anchor, 1)

    io.open(PATH, "w", encoding="utf-8").write(source)
    print("chat search: пункт меню и действие добавлены")
    return 0


if __name__ == "__main__":
    sys.exit(main())
