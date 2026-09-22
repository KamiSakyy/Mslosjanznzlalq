#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram P95 (правки по замечаниям): галочка своим каналам, ID под @,
фильтр рекламы, режим «только текст», свой статус в профиле при призраке,
журнал удалённых сообщений, встроенные прокси в общий запуск.

Каждый патч ищет СВОЙ маркер и второй раз ничего не делает (идемпотентно).
Если якорь не найден, патч просто пропускается и попадает в отчёт — сборка
не падает (важно: одна неподходящая строка не должна ломать весь релиз).
"""

import io
import os
import sys

DONE = []
SKIPPED = []

TG_DIR = os.environ.get('TG_DIR', '.')
JAVA = os.path.join(TG_DIR, 'TMessagesProj/src/main/java/org/telegram')

CFG = 'org.telegram.messenger.kamigram.KamiGramConfig'
VER = 'org.telegram.messenger.kamigram.KamiGramVerified'
ADS = 'org.telegram.messenger.kamigram.KamiGramAds'
TXTONLY = 'org.telegram.messenger.kamigram.KamiGramTextOnly'
UI = 'org.telegram.messenger.kamigram.KamiGramUi'
BPROXY = 'org.telegram.messenger.kamigram.KamiGramBuiltinProxy'
DEL = 'org.telegram.messenger.kamigram.KamiGramDeleted'


def path(*parts):
    return os.path.join(JAVA, *parts)


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, src):
    io.open(p, 'w', encoding='utf-8').write(src)


def patch(file_name, marker, anchor, insert, what, after=True):
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        SKIPPED.append('%s: %s (%s)' % (file_name, e, what))
        return False
    if marker in src:
        return True
    if anchor not in src:
        SKIPPED.append('%s: нет строки-якоря (%s)' % (file_name, what))
        return False
    replacement = anchor + insert if after else insert + anchor
    write(p, src.replace(anchor, replacement, 1))
    DONE.append((what, file_name.split('/')[-1]))
    return True


def replace_once(file_name, marker, old, new, what):
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        SKIPPED.append('%s: %s (%s)' % (file_name, e, what))
        return False
    if marker in src:
        return True
    if old not in src:
        SKIPPED.append('%s: нет строки (%s)' % (file_name, what))
        return False
    write(p, src.replace(old, new, 1))
    DONE.append((what, file_name.split('/')[-1]))
    return True


# =============================================================================
# 1. ОФИЦИАЛЬНАЯ ГАЛОЧКА МОИМ КАНАЛАМ (рисует сам Telegram)
# =============================================================================

def verified():
    p = path('messenger', 'MessagesController.java')
    try:
        src = read(p)
    except Exception as e:
        SKIPPED.append('MessagesController: %s' % e)
        return
    if 'KAMIGRAM_VERIFIED' in src:
        return
    anchor = '    public void putChat(final TLRPC.Chat chat, boolean fromCache) {\n'
    if anchor not in src:
        SKIPPED.append('MessagesController: не найден putChat (галочка)')
        return
    insert = ('        /* KAMIGRAM_VERIFIED: галочка Telegram моим каналам — только на этом устройстве */\n'
              '        try {\n'
              '            ' + VER + '.apply(chat);\n'
              '        } catch (Throwable kamigramIgnore) {\n'
              '        }\n')
    src = src.replace(anchor, anchor + insert, 1)
    write(p, src)
    DONE.append(('официальная галочка моим каналам (@AsuMeo, @AsunaYukki)', 'MessagesController'))


# =============================================================================
# 2. ID ПОД @USERNAME В ПРОФИЛЕ (а не только в «трёх точках»)
# =============================================================================

def id_under_username():
    p = path('ui', 'ProfileActivity.java')
    try:
        src = read(p)
    except Exception as e:
        SKIPPED.append('ProfileActivity: %s' % e)
        return
    if 'KAMIGRAM_ID_UNDER_USERNAME' in src:
        return
    anchor = """            if (userId == UserConfig.getInstance(currentAccount).clientUserId) {
                onlineTextView[2].setText(LocaleController.getString(R.string.FallbackTooltip));"""
    if anchor not in src:
        SKIPPED.append('ProfileActivity: не найдено место статуса (ID под @)')
        return
    insert = """            /* KAMIGRAM_ID_UNDER_USERNAME: ID виден под @username, как в MdGram/Nekogram */
            try {
                if (org.telegram.messenger.kamigram.KamiGramConfig.showIds() && newString2 != null) {
                    newString2 = newString2 + "  ·  id " + user.id;
                }
            } catch (Throwable kamigramIgnore) {
            }
"""
    src = src.replace(anchor, insert + anchor, 1)
    write(p, src)
    DONE.append(('ID показывается под @username в профиле', 'ProfileActivity'))


# =============================================================================
# 3. ФИЛЬТР РЕКЛАМНЫХ ПОСТОВ (сообщение не показывается совсем)
# =============================================================================

def ads_filter():
    ok = False
    # 3.1 чат: реклама выкидывается ДО попадания в список сообщений.
    #     Раньше правка стояла ниже по коду и реклама всё равно успевала
    #     добавиться в список — теперь фильтр стоит в самом начале разбора,
    #     рядом с другими проверками «continue».
    ok |= patch('ui/ChatActivity.java', 'KAMIGRAM_ADS_FILTER',
                "            if (canAnimateMessage) {\n"
                "                obj = needAnimateToMessage;\n"
                "                animatingMessageObjects.add(obj);\n"
                "                needAnimateToMessage = null;\n"
                "            }\n",
                "            /* KAMIGRAM_ADS_FILTER: рекламный пост не попадает в список чата */\n"
                "            try {\n"
                "                if (org.telegram.messenger.kamigram.KamiGramAds.hide(obj)) {\n"
                "                    org.telegram.messenger.kamigram.KamiGramAds.countHidden(obj.caption != null\n"
                "                        ? obj.caption.toString()\n"
                "                        : (obj.messageText != null ? obj.messageText.toString() : \"\"));\n"
                "                    continue;\n"
                "                }\n"
                "            } catch (Throwable kamigramIgnore) {\n"
                "            }\n",
                'рекламные посты не показываются в чате (фильтр по словам)')
    return ok


# =============================================================================
# 4. РЕЖИМ «ТОЛЬКО ТЕКСТ»: медиа только по нажатию
# =============================================================================

def text_only():
    # 4.1 файлы (видео, аудио, документы, стикеры)
    patch('messenger/FileLoader.java', 'KAMIGRAM_TEXT_ONLY_FILE',
          '    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {\n',
          '        /* KAMIGRAM_TEXT_ONLY_FILE: режим «только текст» — медиа качается лишь по нажатию */\n'
          '        try {\n'
          '            if (' + TXTONLY + '.blockDocument(document, parentObject,\n'
          '                    document != null ? document.size : 0)) {\n'
          '                return;\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'режим «только текст»: медиа не скачивается само')
    # 4.2 картинки интерфейса (в том числе аватарки) — единственная точка входа
    #     в FileLoader, через неё идут все изображения: аватарки, превью фото,
    #     картинки в списках и в шапке профиля
    patch('messenger/FileLoader.java', 'KAMIGRAM_TEXT_ONLY_IMAGE',
          '    public void loadFile(ImageLocation imageLocation, Object parentObject, String ext, int priority, int cacheType) {\n',
          '        /* KAMIGRAM_TEXT_ONLY_IMAGE: режим «только текст» — картинки и аватарки не грузятся */\n'
          '        try {\n'
          '            if (' + TXTONLY + '.blockImage(imageLocation)) {\n'
          '                return;\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'режим «только текст»: картинки и аватарки не грузятся')


# =============================================================================
# 5. ЖУРНАЛ УДАЛЁННЫХ СООБЩЕНИЙ
# =============================================================================

def deleted_log():
    """Журнал удалённых: тексты берём там, где они точно есть — в ChatActivity."""
    patch('ui/ChatActivity.java', 'KAMIGRAM_KEEP_DELETED',
          '    private void createDeleteMessagesAlert(final MessageObject finalSelectedObject, final MessageObject.GroupedMessages finalSelectedGroup, boolean hideDimAfter) {\n',
          '        /* KAMIGRAM_KEEP_DELETED: текст удаляемых сообщений остаётся в журнале на устройстве */\n'
          '        try {\n'
          '            if (' + CFG + '.keepDeleted()) {\n'
          '                if (finalSelectedObject != null) {\n'
          '                    ' + DEL + '.log(getDialogId(), finalSelectedObject.messageText);\n'
          '                }\n'
          '                for (int kamigramSlot = 0; kamigramSlot < 2; kamigramSlot++) {\n'
          '                    for (int kamigramIndex = 0; kamigramIndex < selectedMessagesIds[kamigramSlot].size(); kamigramIndex++) {\n'
          '                        final MessageObject kamigramObject = messagesDict[kamigramSlot].get(selectedMessagesIds[kamigramSlot].keyAt(kamigramIndex));\n'
          '                        if (kamigramObject != null) {\n'
          '                            ' + DEL + '.log(getDialogId(), kamigramObject.messageText);\n'
          '                        }\n'
          '                    }\n'
          '                }\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'журнал удалённых сообщений (текст остаётся на устройстве)')


# =============================================================================
# 6. СВОЙ СТАТУС В ПРОФИЛЕ ПРИ ПРИЗРАКЕ
# =============================================================================

def ghost_own_status():
    """При призраке в профиле — родное «был(а) в 12:00» вместо вечного «в сети»."""
    patch('messenger/LocaleController.java', 'KAMIGRAM_GHOST_OWN_STATUS',
          '    public static String formatUserStatus(int currentAccount, TLRPC.User user, boolean[] isOnline, boolean[] madeShorter) {\n',
          '        /* KAMIGRAM_GHOST_OWN_STATUS: при призраке у себя показываем реальное время захода */\n'
          '        try {\n'
          '            if (user != null && currentAccount >= 0\n'
          '                && user.id == UserConfig.getInstance(currentAccount).getClientUserId()\n'
          '                && org.telegram.messenger.kamigram.KamiGramConfig.ghostMode()) {\n'
          '                final long kamigramLastOnline = org.telegram.messenger.kamigram.KamiGramGhost.lastOnlineTime();\n'
          '                if (kamigramLastOnline > 0) {\n'
          '                    if (isOnline != null) {\n'
          '                        isOnline[0] = false;\n'
          '                    }\n'
          '                    return formatDateOnline(kamigramLastOnline, madeShorter);\n'
          '                }\n'
          '            }\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'в профиле при призраке видно реальное время захода, а не «в сети»')

    # профиль рисует статус своим кодом — правим и там (родной виджет Telegram
    # показывает строку «был(а) в 12:00», это не своя надпись)
    patch('ui/ProfileActivity.java', 'KAMIGRAM_GHOST_OWN_STATUS2',
          '                    onlineTextView[3].setText(LocaleController.getString(R.string.Online));\n',
          '                    onlineTextView[3].setText(/* KAMIGRAM_GHOST_OWN_STATUS2 */'
          ' org.telegram.messenger.kamigram.KamiGramGhost.ownStatusText());\n',
          'в профиле строка статуса — родная строка Telegram с реальным временем')


# =============================================================================
# 7. ВСТРОЕННЫЕ ПРОКСИ: запуск вместе с приложением
# =============================================================================

def builtin_proxy_boot():
    patch('ui/LaunchActivity.java', 'KAMIGRAM_BUILTIN_PROXY',
          '        org.telegram.messenger.kamigram.KamiGramGhost.onAppStarted(currentAccount);\n',
          '        ' + BPROXY + '.init(this); // KAMIGRAM_BUILTIN_PROXY\n',
          'встроенные прокси включаются сразу при запуске')


# =============================================================================
# 8. ВСТРОЕННЫЕ ПРОКСИ СКРЫТЫ В СПИСКЕ ПРОКСИ TELEGRAM
# =============================================================================

def hide_builtin_from_list():
    patch('ui/ProxyListActivity.java', 'KAMIGRAM_HIDE_BUILTIN',
          '            proxyList.clear();\n            proxyList.addAll(SharedConfig.proxyList);\n',
          '            /* KAMIGRAM_HIDE_BUILTIN: встроенные прокси сборки скрыты от пользователя */\n'
          '            try {\n'
          '                final java.util.ArrayList<SharedConfig.ProxyInfo> kamigramVisible = new java.util.ArrayList<>();\n'
          '                for (int kamigramIndex = 0; kamigramIndex < SharedConfig.proxyList.size(); kamigramIndex++) {\n'
          '                    final SharedConfig.ProxyInfo kamigramInfo = SharedConfig.proxyList.get(kamigramIndex);\n'
          '                    if (!' + BPROXY + '.isBuiltIn(kamigramInfo)) {\n'
          '                        kamigramVisible.add(kamigramInfo);\n'
          '                    }\n'
          '                }\n'
          '                SharedConfig.proxyList.clear();\n'
          '                SharedConfig.proxyList.addAll(kamigramVisible);\n'
          '            } catch (Throwable kamigramIgnore) {\n'
          '            }\n',
          'встроенные прокси не видны в списке прокси Telegram')


# =============================================================================
# 9. ОТПРАВКА БЕЗ ВЫХОДА В СЕТЬ (призрак): сообщение уходит отложенным
# =============================================================================

def ghost_silent_send():
    """Призрак: сообщение уходит отложенным, поэтому «в сети» не появляется.

    Раньше правка стояла внутри sendMessageInternal и переписывала его
    параметры — а они используются в лямбде, из-за чего код не компилировался
    («must be final or effectively final»). Теперь дата отправки подставляется
    в МЕСТАХ ВЫЗОВА: обычно это 0 (отправить сразу), при призраке — «сейчас + 5 с».
    """
    chat = 'ui/Components/ChatActivityEnterView.java'
    try:
        src = read(path(*chat.split('/')))
    except Exception as e:
        SKIPPED.append('%s: %s (призрак: тихая отправка)' % (chat, e))
        return False
    if 'KAMIGRAM_GHOST_SEND' in src:
        return True
    call = 'org.telegram.messenger.kamigram.KamiGramGhost.sendDate(0)'
    before = src
    # медиа и «оплаченные» отправки
    src = src.replace('sendMessageInternal(true, 0, 0, payStars, false)',
                      'sendMessageInternal(true, %s, 0, payStars, false) /* KAMIGRAM_GHOST_SEND */' % call)
    # отправка «без звука» и с превью
    src = src.replace('sendMessageInternal(false, 0, 0, 0, true)',
                      'sendMessageInternal(false, %s, 0, 0, true) /* KAMIGRAM_GHOST_SEND */' % call)
    # обычная отправка текста (кнопка отправки и Enter)
    src = src.replace('return sendMessageInternal(true, 0, 0, 0, true);',
                      'return sendMessageInternal(true, %s, 0, 0, true); /* KAMIGRAM_GHOST_SEND */' % call)
    if src == before:
        SKIPPED.append('%s: нет мест вызова отправки (призрак: тихая отправка)' % chat)
        return False
    write(path(*chat.split('/')), src)
    DONE.append(('призрак: сообщения уходят отложенно (без отметки «в сети»)', 'ChatActivityEnterView.java'))
    return True


# =============================================================================
# 10. РЕЖИМ «ТОЛЬКО ТЕКСТ»: медиа грузится только по нажатию
# =============================================================================

def text_only_taps():
    patch('ui/Cells/ChatMessageCell.java', 'KAMIGRAM_MANUAL_TAP',
          '    private void didPressButton(boolean animated, boolean video) {\n',
          '        /* KAMIGRAM_MANUAL_TAP: пользователь сам открыл медиа — разрешаем загрузку */\n'
          '        try {\n'
          '            ' + UI + '.markManual();\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'медиа начинает грузиться, когда пользователь нажал на него')
    patch('ui/Cells/ChatMessageCell.java', 'KAMIGRAM_MANUAL_TAP2',
          '    private void didPressMiniButton(boolean animated) {\n',
          '        /* KAMIGRAM_MANUAL_TAP2: тап по превью — медиа грузим */\n'
          '        try {\n'
          '            ' + UI + '.markManual();\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'тап по фото/видео разрешает загрузку (режим «только текст»)')


def main():
    verified()
    id_under_username()
    ads_filter()
    text_only()
    deleted_log()
    ghost_own_status()
    builtin_proxy_boot()
    hide_builtin_from_list()
    ghost_silent_send()
    text_only_taps()

    lines = ['=== P95: правки по замечаниям (%d пунктов) ===' % len(DONE)]
    for i, (what, where) in enumerate(DONE, 1):
        lines.append('%3d. %s — %s' % (i, what, where))
    if SKIPPED:
        lines.append('')
        lines.append('не применилось (%d):' % len(SKIPPED))
        for s in SKIPPED:
            lines.append('  - ' + s)
    io.open(os.path.join(TG_DIR, 'MOD_FEATURES_r43.txt'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print('KamiGram P95: применено %d пунктов, пропущено %d' % (len(DONE), len(SKIPPED)))
    for s in SKIPPED[:8]:
        print('  · ' + s)
    return 0


if __name__ == '__main__':
    sys.exit(main())
