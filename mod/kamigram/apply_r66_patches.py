#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram r66: патчи поверх исходников Telegram (DrKLO 12.10.3).

Правки этого пакета — по жалобам пользователя:

  1. «название KamiGram пропадает, когда прокси подключён» — в шапке главного
     экрана имя теперь настоящий ТЕКСТ (раньше это была картинка-логотип
     «Telegram», спрятанная в ImageSpan: её и подменял родной оверлей состояния
     соединения), а сам оверлей на главном экране запрещён полностью.

  2. «постоянно анимация, что что-то скачивается» — иконка менеджера загрузок
     видна всегда, но анимация запускается ТОЛЬКО когда загрузка реально идёт.
     Раньше Telegram запускал её в конструкторе и она крутилась вечно.

  3. «одноразовые и исчезающие фото удаляются, пишет "истёкшая фотография"» —
     при просмотре больше не ставится таймер уничтожения и не создаётся задача
     «удалить после просмотра», медиа не стирается из базы, а серверное удаление
     в личных чатах (там Telegram не сообщает id чата) больше не проходит:
     сообщение остаётся в чате. Как в re-extera-fork/Spy: фото можно смотреть снова.

  4. удалённые сообщения в ЛИЧНЫХ чатах тоже остаются (раньше это работало
     только в каналах: в личных чатах updateDeleteMessages приходит без id чата).

Каждый патч идемпотентен: ищет свой маркер и второй раз ничего не делает.
Если якорь не найден — патч попадает в отчёт, но сборка не падает.
"""

import io
import os
import sys

DONE = []
MISS = []

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('TG_DIR', '.')
APP_NAME = sys.argv[2] if len(sys.argv) > 2 else os.environ.get('APP_NAME', 'Sakura')
JAVA = os.path.join(TG, 'TMessagesProj/src/main/java/org/telegram')

DEL = 'org.telegram.messenger.kamigram.KamiGramDeleted'


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
# 1. ИМЯ KAMIGRAM В ШАПКЕ — НАСТОЯЩИЙ ТЕКСТ + ЗАПРЕТ ОВЕРЛЕЯ СОСТОЯНИЯ
# =============================================================================
def title_text():
    dialogs = 'ui/DialogsActivity.java'
    replace(dialogs, 'KAMIGRAM_TITLE_TEXT',
            '                ssb.setSpan(new ImageSpan(logoDrawable), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);\n',
            '                /* KAMIGRAM_TITLE_TEXT: имя приложения — настоящий ТЕКСТ, а не картинка\n'
            '                   с надписью «Telegram». Текст нельзя «потерять» при смене состояния\n'
            '                   соединения (раньше картинку подменял родной оверлей прокси). */\n',
            'шапка: имя KamiGram настоящим текстом (без картинки-логотипа)')


def title_lock():
    actionbar = 'ui/ActionBar/ActionBar.java'
    patch(actionbar, 'KAMIGRAM_TITLE_LOCK',
          '    public void setTitleOverlayText(String title, int titleId, Runnable action) {\n',
          '        /* KAMIGRAM_TITLE_LOCK: на главном экране заголовок (имя KamiGram) не подменяется\n'
          '           ничем: ни «Подключением к прокси…», ни стрелками, ни состоянием сети. */\n'
          '        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n'
          '            return;\n'
          '        }\n',
          'шапка: на главном экране оверлей состояния соединения запрещён')


# =============================================================================
# 2. МЕНЕДЖЕР ЗАГРУЗОК: АНИМАЦИЯ ТОЛЬКО ПРИ РЕАЛЬНОЙ ЗАГРУЗКЕ
# =============================================================================
def no_fake_downloads():
    icon = 'ui/DownloadProgressIcon.java'
    patch(icon, 'KAMIGRAM_NO_FAKE_DOWNLOAD_FIELD',
          '    private boolean wasDrawn;\n',
          '    private boolean kamigramDownloadAnim; /* KAMIGRAM_NO_FAKE_DOWNLOAD_FIELD */\n',
          'загрузки: служебное поле состояния анимации')

    replace(icon, 'KAMIGRAM_NO_FAKE_DOWNLOAD_START',
            '        downloadImageReceiver.setAutoRepeat(1);\n'
            '        downloadDrawable.setAutoRepeat(1);\n'
            '        downloadDrawable.start();\n',
            '        downloadImageReceiver.setAutoRepeat(1);\n'
            '        downloadDrawable.setAutoRepeat(1);\n'
            '        /* KAMIGRAM_NO_FAKE_DOWNLOAD_START: анимация НЕ запускается сама по себе.\n'
            '           Иконка менеджера загрузок видна всегда, и раньше вместе с ней вечно\n'
            '           крутилось «что-то скачивается», даже когда загрузок не было. Теперь\n'
            '           анимация включается только при реальной загрузке (см. update ниже). */\n'
            '        downloadDrawable.setCurrentFrame(0, false);\n',
            'загрузки: убрано вечное «что-то скачивается» (анимация не стартует сама)')

    patch(icon, 'KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE',
          '        if (currentListeners.size() == 0 && !wasDrawn) {\n',
          '        /* KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE: анимация = признак реальной загрузки */\n'
          '        if (currentListeners.size() > 0) {\n'
          '            if (!kamigramDownloadAnim) {\n'
          '                kamigramDownloadAnim = true;\n'
          '                downloadDrawable.start();\n'
          '            }\n'
          '        } else if (kamigramDownloadAnim) {\n'
          '            kamigramDownloadAnim = false;\n'
          '            downloadDrawable.stop();\n'
          '            downloadDrawable.setCurrentFrame(0, false);\n'
          '        }\n',
          'загрузки: анимация включается/выключается по реальной загрузке', after=False)


# =============================================================================
# 3. ОДНОРАЗОВЫЕ И «ИСЧЕЗАЮЩИЕ» ФОТО: НЕ УНИЧТОЖАЕМ
# =============================================================================
def keep_view_once():
    chat = 'ui/ChatActivity.java'

    # 3.1 подтверждение прочтения без таймера уничтожения (мгновенное открытие)
    replace(chat, 'KAMIGRAM_KEEP_VIEWONCE_READ',
            '            final boolean delete = messageObject.messageOwner.ttl != 0x7FFFFFFF;\n'
            '            final int ttl = messageObject.messageOwner.ttl == 0x7FFFFFFF ? 0 : messageObject.messageOwner.ttl;\n'
            '            messageObject.messageOwner.destroyTime = ttl + getConnectionsManager().getCurrentTime();\n',
            '            /* KAMIGRAM_KEEP_VIEWONCE_READ: одноразовое/«исчезающее» фото сообщаем\n'
            '               прочитанным, но БЕЗ таймера уничтожения: сервер такое фото не удаляет,\n'
            '               оно остаётся в чате и его можно открыть снова. */\n'
            '            final boolean delete = false;\n'
            '            final int ttl = 0;\n',
            'одноразовые фото: нет таймера уничтожения при открытии')

    # 3.2 то же для отложенного подтверждения (когда фото открывают из просмотрщика)
    replace(chat, 'KAMIGRAM_KEEP_VIEWONCE_READ2',
            '            return () -> {\n'
            '                final boolean delete = messageObject.messageOwner.ttl != 0x7FFFFFFF;\n'
            '                final int ttl = messageObject.messageOwner.ttl == 0x7FFFFFFF ? 0 : messageObject.messageOwner.ttl;\n'
            '                messageObject.messageOwner.destroyTime = ttl + getConnectionsManager().getCurrentTime();\n'
            '                messageObject.messageOwner.destroyTimeMillis = ttl * 1000L + getConnectionsManager().getCurrentTimeMillis();\n',
            '            return () -> {\n'
            '                /* KAMIGRAM_KEEP_VIEWONCE_READ2: без таймера уничтожения и без пометки\n'
            '                   «истекло» — фото остаётся доступным в чате. */\n'
            '                final boolean delete = false;\n'
            '                final int ttl = 0;\n',
            'одноразовые фото: нет таймера уничтожения в отложенном подтверждении')

    # 3.3 «удалить после просмотра» — больше ничего не удаляем
    replace(chat, 'KAMIGRAM_KEEP_VIEWONCE_DELETE',
            '    private Runnable sendSecretMediaDelete(MessageObject messageObject) {\n'
            '        if (messageObject == null || messageObject.isOut() || !messageObject.isSecretMedia() || messageObject.messageOwner.ttl != 0x7FFFFFFF) {\n'
            '            return null;\n'
            '        }\n'
            '        final long taskId = getMessagesController().createDeleteShowOnceTask(dialog_id, messageObject.getId());\n'
            '        messageObject.forceExpired = true;\n'
            '        if (messageObject.isOutOwner() || !messageObject.isRoundOnce() && !messageObject.isVoiceOnce()) {\n'
            '            ArrayList<MessageObject> msgs = new ArrayList<>();\n'
            '            msgs.add(messageObject);\n'
            '            updateMessages(msgs, true);\n'
            '        }\n'
            '        return () -> getMessagesController().doDeleteShowOnceTask(taskId, dialog_id, messageObject.getId());\n'
            '    }\n',
            '    private Runnable sendSecretMediaDelete(MessageObject messageObject) {\n'
            '        /* KAMIGRAM_KEEP_VIEWONCE_DELETE: просмотренное одноразовое фото/видео НЕ стираем.\n'
            '           Раньше здесь создавалась задача «удалить после просмотра» и сообщение\n'
            '           помечалось истёкшим — теперь не удаляем и не помечаем. */\n'
            '        return null;\n'
            '    }\n',
            'одноразовые фото: убрана задача «удалить после просмотра»')

    # 3.4 фоновая задача «удалить показанное один раз» — заглушена
    mc = 'messenger/MessagesController.java'
    replace(mc, 'KAMIGRAM_KEEP_VIEWONCE_TASK81',
            '                            final int id = viewerObject.getId();\n'
            '                            mids.remove((Integer) id);\n'
            '                            viewerObject.forceExpired = true;\n'
            '                            final long taskId = createDeleteShowOnceTask(dialogId, id);\n'
            '                            SecretMediaViewer.getInstance().setOnClose(() -> doDeleteShowOnceTask(taskId, dialogId, id));\n'
            '                            getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, viewerObject.messageOwner);\n',
            '                            final int id = viewerObject.getId();\n'
            '                            mids.remove((Integer) id);\n'
            '                            /* KAMIGRAM_KEEP_VIEWONCE_TASK81: фото не удаляем и не помечаем\n'
            '                               истёкшим — оно остаётся в чате. */\n'
            '                            getNotificationCenter().postNotificationName(NotificationCenter.updateMessageMedia, viewerObject.messageOwner);\n',
            'одноразовые фото: фоновая задача удаления больше не создаётся')

    replace(mc, 'KAMIGRAM_KEEP_VIEWONCE_NOOP_TASK',
            '    public void doDeleteShowOnceTask(long taskId, long dialogId, int mid) {\n'
            '        getMessagesStorage().removePendingTask(taskId);\n'
            '        ArrayList<Integer> mids = new ArrayList<>();\n'
            '        mids.add(mid);\n'
            '        getMessagesStorage().emptyMessagesMedia(dialogId, mids);\n'
            '    }\n',
            '    public void doDeleteShowOnceTask(long taskId, long dialogId, int mid) {\n'
            '        /* KAMIGRAM_KEEP_VIEWONCE_TASK: задача «удалить после просмотра» больше ничего\n'
            '           не удаляет — снимаем её с очереди и оставляем фото на месте. */\n'
            '        getMessagesStorage().removePendingTask(taskId);\n'
            '    }\n',
            'одноразовые фото: выполнение задачи удаления обезврежено')

    # 3.5 стирание медиа из базы (путь для «исчезающих» по таймеру) — не стираем
    patch('messenger/MessagesStorage.java', 'KAMIGRAM_KEEP_VIEWONCE_MEDIA',
          '                        message.readAttachPath(data, getUserConfig().clientUserId);\n'
          '                        data.reuse();\n',
          '                        /* KAMIGRAM_KEEP_VIEWONCE_MEDIA: медиа одноразовых и «исчезающих»\n'
          '                           сообщений в базе не стираем — фото остаётся на месте. */\n'
          '                        if (message.ttl > 0 || org.telegram.messenger.MessageObject.isSecretMedia(message)) {\n'
          '                            continue;\n'
          '                        }\n',
          'одноразовые фото: медиа не вычищается из базы')


# =============================================================================
# 4. УДАЛЁННОЕ В ЛИЧНЫХ ЧАТАХ ТОЖЕ ОСТАЁТСЯ
# =============================================================================
def keep_deleted_private():
    patch('messenger/MessagesController.java', 'KAMIGRAM_KEEP_DELETED_PRIVATE',
          '                    /* KAMIGRAM_KEEP_DELETED_PEER: удалённое собеседником остаётся у нас */\n'
          '                    ' + DEL + '.remember(dialogId, arrayList);\n',
          '                    /* KAMIGRAM_KEEP_DELETED_PRIVATE: в личных чатах Telegram присылает\n'
          '                       удаление БЕЗ id чата (0) — запоминаем по номеру сообщения: в личных\n'
          '                       чатах номера уникальны для всего аккаунта. */\n'
          '                    if (dialogId == 0) {\n'
          '                        ' + DEL + '.rememberUnknown(arrayList);\n'
          '                    } else {\n'
          '                        ' + DEL + '.remember(dialogId, arrayList);\n'
          '                    }\n',
          'удалённые: личные чаты тоже удерживают сообщения', after=True)


def main():
    title_text()
    title_lock()
    no_fake_downloads()
    keep_view_once()
    keep_deleted_private()

    print('r66: изменений — %d' % len(DONE))
    for what in DONE:
        print('  ✓ %s' % what)
    if MISS:
        print('r66: пропущено — %d' % len(MISS))
        for what in MISS:
            print('  ! %s' % what)


if __name__ == '__main__':
    main()
