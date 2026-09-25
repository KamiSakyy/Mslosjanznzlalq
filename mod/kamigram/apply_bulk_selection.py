#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Wire the native ChatActivity selection mode to 10/50/custom choices."""

import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
PATH = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")
DONE = []


def main():
    source = io.open(PATH, encoding="utf-8").read()
    if "KAMIGRAM_BULK_SELECTION" not in source:
        # Keep Telegram's normal selection data structures, only lift the
        # artificial 100-message ceiling so a custom count is meaningful.
        source = source.replace(
            "selectedMessagesIds[0].size() + selectedMessagesIds[1].size() >= 100)",
            "selectedMessagesIds[0].size() + selectedMessagesIds[1].size() >= 10000) /* KAMIGRAM_BULK_SELECTION_LIMIT */",
        )

        constants = "    private final static int chat_menu_topic_create = 73;\n"
        if constants not in source:
            print("bulk: menu constant anchor not found", file=sys.stderr)
            return 1
        source = source.replace(
            constants,
            constants + "    private final static int kamigram_bulk_select = 75; /* KAMIGRAM_BULK_SELECTION */\n",
            1,
        )

        menu_anchor = "            headerItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));\n"
        if menu_anchor not in source:
            print("bulk: header menu anchor not found", file=sys.stderr)
            return 1
        source = source.replace(
            menu_anchor,
            menu_anchor
            + "            headerItem.lazilyAddSubItem(kamigram_bulk_select, R.drawable.msg_select, \"Массовый выбор\"); /* KAMIGRAM_BULK_SELECTION_MENU */\n",
            1,
        )

        click_anchor = "                } else if (id == view_as_topics) {\n"
        if click_anchor not in source:
            print("bulk: action-bar callback anchor not found", file=sys.stderr)
            return 1
        action = """                } else if (id == kamigram_bulk_select) {
                    org.telegram.messenger.kamigram.KamiGramBulkSelector.showCountDialog(getParentActivity(),
                        count -> kamigramSelectMessages(count)); /* KAMIGRAM_BULK_SELECTION_ACTION */
"""
        source = source.replace(click_anchor, action + click_anchor, 1)

        method_anchor = "    private void processRowSelect(View view, boolean outside, float touchX, float touchY) {\n"
        if method_anchor not in source:
            print("bulk: processRowSelect anchor not found", file=sys.stderr)
            return 1
        method = """    private void kamigramSelectMessages(int requested) {
        final java.util.ArrayList<MessageObject> picked =
            org.telegram.messenger.kamigram.KamiGramBulkSelector.collect(messages, requested);
        if (picked.isEmpty()) {
            return;
        }
        /* KAMIGRAM_BULK_SELECTION_SHOW: тулбар выделения ПОКАЗЫВАЕТСЯ той же
           последовательностью, что и при обычном долгом нажатии. */
        if (!actionBar.isActionModeShowed()) {
            createActionMode();
            final org.telegram.ui.ActionBar.ActionBarMenu actionMode = actionBar.createActionMode();
            if (actionMode == null) {
                return;
            }
            actionMode.setItemVisibility(delete, View.VISIBLE);
            if (actionsButtonsLayout != null) {
                actionsButtonsLayout.bringToFront();
            }
            bottomViewsVisibilityController.setViewVisible(MESSAGE_ACTION_CONTAINER, true, true);
            actionBar.showActionMode(true, null, null, null, null, null, 0);
            if (getParentActivity() instanceof LaunchActivity) {
                ((LaunchActivity) getParentActivity()).hideVisibleActionMode();
            }
            closeMenu();
            chatLayoutManager.setCanScrollVertically(true);
            updatePinnedMessageView(true);
        }
        for (int i = 0; i < picked.size(); i++) {
            addToSelectedMessages(picked.get(i), false, i == picked.size() - 1);
        }
        updateActionModeTitle();
        updateVisibleRows();
    }

"""
        source = source.replace(method_anchor, method + method_anchor, 1)
        io.open(PATH, "w", encoding="utf-8").write(source)
        DONE.append("ChatActivity bulk menu/action/selection")
    print("bulk selection: %d patches" % len(DONE))
    return 0


if __name__ == "__main__":
    sys.exit(main())
