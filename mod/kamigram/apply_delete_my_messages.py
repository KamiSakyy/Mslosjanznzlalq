#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Add an instant, native-path "delete my messages" chat action."""

import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
PATH = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")


def main():
    source = io.open(PATH, encoding="utf-8").read()
    if "KAMIGRAM_DELETE_MY_MESSAGES" in source:
        print("delete my messages: already applied")
        return 0

    constant_anchor = "    private final static int chat_menu_topic_create = 73;\n"
    if constant_anchor not in source:
        print("delete: menu constant anchor not found", file=sys.stderr)
        return 1
    source = source.replace(
        constant_anchor,
        constant_anchor + "    private final static int kamigram_delete_my_messages = 76; /* KAMIGRAM_DELETE_MY_MESSAGES */\n",
        1,
    )

    menu_anchor = "            headerItem.lazilyAddSubItem(kamigram_bulk_select, R.drawable.msg_select, \"Массовый выбор\"); /* KAMIGRAM_BULK_SELECTION_MENU */\n"
    if menu_anchor not in source:
        print("delete: bulk menu anchor not found", file=sys.stderr)
        return 1
    source = source.replace(
        menu_anchor,
        menu_anchor + "            headerItem.lazilyAddSubItem(kamigram_delete_my_messages, R.drawable.msg_delete, \"Удалить мои сообщения\"); /* KAMIGRAM_DELETE_MY_MESSAGES_MENU */\n",
        1,
    )

    click_anchor = "                } else if (id == kamigram_bulk_select) {\n"
    if click_anchor not in source:
        print("delete: action-bar anchor not found", file=sys.stderr)
        return 1
    action = """                } else if (id == kamigram_delete_my_messages) {
                    org.telegram.messenger.kamigram.KamiGramDeleteMyMessages.delete(getParentActivity(), currentAccount,
                        getDialogId(), threadMessageId, chatMode, messages); /* KAMIGRAM_DELETE_MY_MESSAGES_ACTION */
"""
    source = source.replace(click_anchor, action + click_anchor, 1)

    method_anchor = "    private void kamigramSelectMessages(int requested, org.telegram.messenger.kamigram.KamiGramBulkSelector.Filter filter) {\n"
    if method_anchor not in source:
        print("delete: selection method anchor not found", file=sys.stderr)
        return 1
    # Keep the deletion operation in the helper; the ChatActivity callback is
    # intentionally one native call so it cannot alter Telegram's send path.
    source = source.replace(method_anchor, method_anchor, 1)
    io.open(PATH, "w", encoding="utf-8").write(source)
    print("delete my messages: applied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
