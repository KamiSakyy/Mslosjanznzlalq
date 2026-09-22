#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram r54: патчи поверх исходников Telegram (DrKLO 12.10.3).

Главные правки этого пакета:
  * ПРИЗРАК — иконка живёт только в шапке ГЛАВНОГО экрана (рядом с «⋮»),
    в чатах и каналах её больше нет;
  * ИМЯ — в шапке всегда «KamiGram» (не зависит от облачных строк);
  * ЗАГРУЗКИ — иконка родного менеджера загрузок Telegram видна всегда, и
    центр мода открывает именно его;
  * УДАЛЁННЫЕ — сообщения остаются в чате: локальные строки не стираются,
    а из чата не приходит «удалить» (повторное удаление удаляет по-настоящему);
  * ШРИФТ — свой .ttf применяется ко всему экрану (сообщения, каналы, настройки).

Каждый патч идемпотентен: ищет свой маркер и второй раз ничего не делает.
Если якорь не найден — патч попадает в отчёт, но сборка не падает.
"""

import io
import os
import sys

DONE = []
MISS = []

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('TG_DIR', '.')
APP_NAME = sys.argv[2] if len(sys.argv) > 2 else os.environ.get('APP_NAME', 'KamiGram')
JAVA = os.path.join(TG, 'TMessagesProj/src/main/java/org/telegram')

GHOST = 'org.telegram.messenger.kamigram.KamiGramGhost'
DEL = 'org.telegram.messenger.kamigram.KamiGramDeleted'
FONT = 'org.telegram.messenger.kamigram.KamiGramFont'


def path(rel):
    return os.path.join(JAVA, *rel.split('/'))


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, src):
    io.open(p, 'w', encoding='utf-8').write(src)


def patch(rel, marker, anchor, insert, what, after=True):
    p = path(rel)
    try:
        src = read(p)
    except Exception as e:
        MISS.append('%s: %s (%s)' % (rel, e, what))
        return False
    if marker in src:
        return True
    if anchor not in src:
        MISS.append('%s: нет якоря (%s)' % (rel, what))
        return False
    replacement = anchor + insert if after else insert + anchor
    write(p, src.replace(anchor, replacement, 1))
    DONE.append(what)
    return True


def replace(rel, marker, old, new, what):
    p = path(rel)
    try:
        src = read(p)
    except Exception as e:
        MISS.append('%s: %s (%s)' % (rel, e, what))
        return False
    if marker in src:
        return True
    if old not in src:
        MISS.append('%s: нет строки (%s)' % (rel, what))
        return False
    write(p, src.replace(old, new, 1))
    DONE.append(what)
    return True


# =============================================================================
# 1. ИКОНКА ПРИЗРАКА — ТОЛЬКО В ШАПКЕ ГЛАВНОГО ЭКРАНА, РЯДОМ С «⋮»
# =============================================================================
def ghost_header():
    dialogs = 'ui/DialogsActivity.java'
    patch(dialogs, 'KAMIGRAM_GHOST_HEADER',
          '            optionsItem = menu.addItem(4, R.drawable.ic_ab_other);\n',
          '            /* KAMIGRAM_GHOST_HEADER: иконка призрака — рядом с «⋮» в шапке главного\n'
          '               экрана. В чатах и каналах её нет: там она только мешала. */\n'
          '            ' + GHOST + '.addHeaderItem(menu, null);\n',
          'призрак: иконка в шапке главного экрана рядом с «⋮»', after=False)


# =============================================================================
# 2. ИМЯ ПРИЛОЖЕНИЯ В ШАПКЕ — ВСЕГДА KAMIGRAM
# =============================================================================
def app_title():
    replace('ui/DialogsActivity.java', 'KAMIGRAM_APP_TITLE',
            '                SpannableStringBuilder ssb = new SpannableStringBuilder(getString(R.string.AppName));\n',
            '                /* KAMIGRAM_APP_TITLE: имя мода в шапке — всегда своё, без облачных строк */\n'
            '                SpannableStringBuilder ssb = new SpannableStringBuilder("%s");\n' % APP_NAME,
            'шапка главного экрана: имя приложения')


# =============================================================================
# 3. МЕНЕДЖЕР ЗАГРУЗОК: ИКОНКА ВИДНА ВСЕГДА + ОТКРЫТИЕ ИЗ ЦЕНТРА МОДА
# =============================================================================
def downloads():
    dialogs = 'ui/DialogsActivity.java'
    replace(dialogs, 'KAMIGRAM_DOWNLOADS_ICON',
            '        if ((getDownloadController().hasUnviewedDownloads() || showDownloads || (downloadsItem.getVisibility() == View.VISIBLE && downloadsItem.getAlpha() == 1 && !force))) {\n'
            '            downloadsItemVisible = true;\n'
            '        } else {\n'
            '            downloadsItemVisible = false;\n'
            '        }\n',
            '        /* KAMIGRAM_DOWNLOADS_ICON: иконка родного менеджера загрузок Telegram\n'
            '           видна всегда (в оригинале она скрывается, когда загрузок нет) */\n'
            '        downloadsItemVisible = true;\n',
            'загрузки: иконка менеджера загрузок не скрывается')

    patch(dialogs, 'KAMIGRAM_SHOW_DOWNLOADS',
          '    private void updateProxyButton(boolean animated, boolean force) {\n',
          '    /** KamiGram: открыть родной менеджер загрузок Telegram. */\n'
          '    public void kamigramShowDownloads() { /* KAMIGRAM_SHOW_DOWNLOADS */\n'
          '        try {\n'
          '            if (searchViewPager != null) {\n'
          '                searchViewPager.showDownloads();\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n'
          '    }\n\n',
          'загрузки: точка открытия родного менеджера загрузок', after=False)


# =============================================================================
# 4. УДАЛЁННЫЕ СООБЩЕНИЯ ОСТАЮТСЯ В ЧАТЕ
# =============================================================================
def keep_deleted():
    mc = 'messenger/MessagesController.java'

    # 4.1 своё удаление: помечаем и не стираем локальную копию
    patch(mc, 'KAMIGRAM_KEEP_DELETED',
          '        ArrayList<Integer> toSend = null;\n',
          '        /* KAMIGRAM_KEEP_DELETED: удалённые сообщения остаются в чате на устройстве.\n'
          '           На сервер запрос уходит как обычно (у собеседника сообщение удаляется),\n'
          '           а локальная копия остаётся: повторное удаление удаляет по-настоящему. */\n'
          '        ArrayList<Integer> kamigramLocal = messages;\n'
          '        try {\n'
          '            if (messages != null && !messages.isEmpty() && !scheduled && !quickReplies\n'
          '                && !welcomeMessages && mode != ChatActivity.MODE_SAVED) {\n'
          '                ' + DEL + '.beforeDelete(dialogId, messages);\n'
          '                kamigramLocal = ' + DEL + '.filterKept(dialogId, messages);\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'удалённые: пометка «оставить в чате»')

    # 4.2 локальные вызовы работают по «оставленному» списку
    replace(mc, 'KAMIGRAM_KEEP_DELETED_PAINT',
            '                if (channelId == 0) {\n'
            '                    for (int a = 0; a < messages.size(); a++) {\n'
            '                        Integer id = messages.get(a);\n'
            '                        MessageObject obj = dialogMessagesByIds.get(id);\n',
            '                if (channelId == 0) {\n'
            '                    /* KAMIGRAM_KEEP_DELETED_PAINT */\n'
            '                    for (int a = 0; a < kamigramLocal.size(); a++) {\n'
            '                        Integer id = kamigramLocal.get(a);\n'
            '                        MessageObject obj = dialogMessagesByIds.get(id);\n',
            'удалённые: оставленные сообщения не помечаются удалёнными в списке')

    replace(mc, 'KAMIGRAM_KEEP_DELETED_CHANNEL',
            '                    markDialogMessageAsDeleted(dialogId, messages);\n',
            '                    markDialogMessageAsDeleted(dialogId, kamigramLocal); /* KAMIGRAM_KEEP_DELETED_CHANNEL */\n',
            'удалённые: в каналах оставленные сообщения тоже не помечаются')

    replace(mc, 'KAMIGRAM_KEEP_DELETED_DB',
            '                getMessagesStorage().markMessagesAsDeleted(dialogId, messages, true, forAll, 0, topicId);\n'
            '                getMessagesStorage().updateDialogsWithDeletedMessages(dialogId, channelId, messages, null);\n',
            '                getMessagesStorage().markMessagesAsDeleted(dialogId, kamigramLocal, true, forAll, 0, topicId); /* KAMIGRAM_KEEP_DELETED_DB */\n'
            '                getMessagesStorage().updateDialogsWithDeletedMessages(dialogId, channelId, kamigramLocal, null);\n',
            'удалённые: строки в базе Telegram не стираются')

    replace(mc, 'KAMIGRAM_KEEP_DELETED_UI',
            '            getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, messages, channelId, scheduled, false, movedToScheduled, movedToScheduledMessageId);\n',
            '            /* KAMIGRAM_KEEP_DELETED_UI: об «удалении» оставленных сообщений не сообщаем,\n'
            '               поэтому они остаются на экране */\n'
            '            getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, kamigramLocal, channelId, scheduled, false, movedToScheduled, movedToScheduledMessageId);\n',
            'удалённые: сообщение остаётся в открытом чате')

    # 4.3 удалил собеседник (или другое устройство)
    patch(mc, 'KAMIGRAM_KEEP_DELETED_PEER',
          '                    getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, arrayList, -dialogId, false);\n',
          '                    /* KAMIGRAM_KEEP_DELETED_PEER: удалённое собеседником остаётся у нас */\n'
          '                    ' + DEL + '.remember(dialogId, arrayList);\n'
          '                    arrayList = ' + DEL + '.filterKept(dialogId, arrayList);\n',
          'удалённые: чужие удаления тоже сохраняются', after=False)

    # 4.4 последний рубеж — сама база (без переприсваивания параметра: он уходит в лямбду)
    patch('messenger/MessagesStorage.java', 'KAMIGRAM_KEEP_DELETED_STORAGE',
          '    public ArrayList<Long> markMessagesAsDeleted(long dialogId, ArrayList<Integer> messages, boolean useQueue, boolean deleteFiles, int mode, int topicId) {\n',
          '        /* KAMIGRAM_KEEP_DELETED_STORAGE: строки «оставить» не удаляются из базы ни при каком пути */\n'
          '        ArrayList<Integer> kamigramMessages = messages;\n'
          '        try {\n'
          '            if (kamigramMessages != null && !kamigramMessages.isEmpty()) {\n'
          '                kamigramMessages = ' + DEL + '.filterKept(dialogId, kamigramMessages);\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'удалённые: защита на уровне базы Telegram')

    replace('messenger/MessagesStorage.java', 'KAMIGRAM_KEEP_DELETED_STORAGE_USE',
            '        if (messages.isEmpty()) {\n'
            '            return null;\n'
            '        }\n'
            '        if (useQueue) {\n'
            '            storageQueue.postRunnable(() -> markMessagesAsDeletedInternal(dialogId, messages, deleteFiles, mode, topicId));\n'
            '        } else {\n'
            '            return markMessagesAsDeletedInternal(dialogId, messages, deleteFiles, mode, topicId);\n'
            '        }\n',
            '        /* KAMIGRAM_KEEP_DELETED_STORAGE_USE */\n'
            '        if (kamigramMessages.isEmpty()) {\n'
            '            return null;\n'
            '        }\n'
            '        if (useQueue) {\n'
            '            storageQueue.postRunnable(() -> markMessagesAsDeletedInternal(dialogId, kamigramMessages, deleteFiles, mode, topicId));\n'
            '        } else {\n'
            '            return markMessagesAsDeletedInternal(dialogId, kamigramMessages, deleteFiles, mode, topicId);\n'
            '        }\n',
            'удалённые: защита базы работает и в очереди хранилища')


# =============================================================================
# 5. СВОЙ ШРИФТ — ВЕЗДЕ (сообщения, посты каналов, настройки)
# =============================================================================
def font_everywhere():
    patch('ui/ActionBar/BaseFragment.java', 'KAMIGRAM_FONT',
          '    public void onResume() {\n        isPaused = false;\n',
          '        /* KAMIGRAM_FONT: свой шрифт применяется ко всему экрану — сообщения,\n'
          '           посты каналов, настройки и подписи. */\n'
          '        try {\n'
          '            ' + FONT + '.applyToScreen(getFragmentView());\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'шрифт: применяется ко всему экрану при входе')


def main():
    ghost_header()
    app_title()
    downloads()
    keep_deleted()
    font_everywhere()

    print('r54: изменений — %d' % len(DONE))
    for what in DONE:
        print('  ✓ %s' % what)
    if MISS:
        print('r54: пропущено — %d' % len(MISS))
        for what in MISS:
            print('  ! %s' % what)


if __name__ == '__main__':
    main()
