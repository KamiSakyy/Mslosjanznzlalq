#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram r68: патчи поверх исходников Telegram (DrKLO 12.10.3).

Правки этого пакета — по новому списку пользователя:

  1. «Призрак: отправка должна идти через запланированные сообщения, как в AyuGram,
     чтобы время захода не выдавалось» — обычная отправка (текст, фото, файл,
     пересылка) при включённом призраке переводится в «Отложенные» ровно как в
     AyuGram4A (SendMessagesHelper: scheduleDate = currentTime + 10 + 1, +10 для
     фото, +15 для документа). Сообщение уходит по расписанию, поэтому по времени
     его прихода нельзя понять, когда мы были в сети.

  2. «Чаты, где непрочитанных больше 100, автоматически отправлять в архив» —
     в фоне работает {@code KamiGramAutoArchive}: смотрит список чатов и уводит
     «100+» в архив родным методом Telegram (MessagesController.addDialogToFolder).

  3. «У папок счётчик непрочитанных должен быть нашего цвета, как у выбранной» —
     счётчик НЕвыбранной вкладки берёт те же цвета, что и выбранная (раньше был
     почти невидим на тёмной панели).

  4. «Прокси: моментально переключать на живой», «если фото не грузится —
     пробовать другой живой прокси» — движок прокси в фоне следит за текущим
     прокси (каждые 2.5 с), а сбои загрузки файлов/фото сразу отправляют его на
     повторную проверку и, если сбои повторяются, — на другой живой прокси
     (см. KamiGramProxyPower.java).

  5. «Одноразовые и самоуничтожающиеся фото не должны исчезать» — медиа этих
     сообщений больше не вычищается из базы (MessagesStorage.emptyMessagesMedia
     и пометка «просмотрено»), а серверные удаления в личных чатах (Telegram
     присылает их без id чата) теперь тоже удерживаются в чате.

  6. «Имя KamiGram пропадает, когда прокси подключён» и «постоянная анимация» —
     заголовок главного экрана дополнительно защищён от подмены (оверлей состояния
     соединения на нём полностью запрещён, а имя возвращается на место), иконка
     загрузок в покое статичная и без полоски прогресса, а «галочка» после
     загрузки больше не крутится по кругу.

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
# 1. ПРИЗРАК: ОТПРАВКА ЧЕРЕЗ «ОТЛОЖЕННЫЕ» (как в AyuGram)
# =============================================================================
def auto_schedule():
    helper = 'messenger/SendMessagesHelper.java'

    # 1.1 основной путь отправки: текст, фото, файл, голосовое, кружок
    replace(helper, 'KAMIGRAM_AUTO_SCHEDULE',
            '        if (sendMessageParams != null && sendMessageParams.scheduleDate == 0) {\n'
            '            org.telegram.messenger.kamigram.KamiGramGhost.onRealSend(currentAccount);\n'
            '        }\n',
            '        /* KAMIGRAM_AUTO_SCHEDULE: при включённом призраке обычная отправка уходит\n'
            '           через «Отложенные» — точно как в AyuGram (schedule_date = текущее время\n'
            '           + 10 + 1 секунда, плюс запас на загрузку фото/файла). Сообщение\n'
            '           доставляется не в момент нажатия, поэтому по времени прихода нельзя\n'
            '           понять, когда мы были в сети. Отложенное сообщение при этом не «палит»\n'
            '           заход: пакетов «в сети» мы не отправляем. */\n'
            '        if (sendMessageParams != null) {\n'
            '            sendMessageParams.scheduleDate = ' + GHOST + '.autoScheduleDate(\n'
            '                sendMessageParams.scheduleDate,\n'
            '                sendMessageParams.peer,\n'
            '                sendMessageParams.photo != null,\n'
            '                sendMessageParams.document != null);\n'
            '        }\n'
            '        if (sendMessageParams != null && sendMessageParams.scheduleDate == 0) {\n'
            '            org.telegram.messenger.kamigram.KamiGramGhost.onRealSend(currentAccount);\n'
            '        }\n',
            'призрак: обычная отправка уходит отложкой (текст, фото, файл)')

    # 1.2 пересылки и медиа-пакеты (тонкая обёртка без лямбд)
    replace(helper, 'KAMIGRAM_AUTO_SCHEDULE_FWD',
            '    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg, int video_timestamp, long payStars) {\n'
            '        return sendMessage(messages, peer, forwardFromMyName, hideCaption, notify, scheduleDate, 0, replyToTopMsg, video_timestamp, payStars, 0, null);\n'
            '    }\n',
            '    public int sendMessage(ArrayList<MessageObject> messages, final long peer, boolean forwardFromMyName, boolean hideCaption, boolean notify, int scheduleDate, MessageObject replyToTopMsg, int video_timestamp, long payStars) {\n'
            '        /* KAMIGRAM_AUTO_SCHEDULE_FWD: пересылки и медиа-пакеты при призраке тоже\n'
            '           уходят отложкой (как forwardMessages в AyuGram). */\n'
            '        scheduleDate = ' + GHOST + '.autoScheduleDate(scheduleDate, peer, messages);\n'
            '        return sendMessage(messages, peer, forwardFromMyName, hideCaption, notify, scheduleDate, 0, replyToTopMsg, video_timestamp, payStars, 0, null);\n'
            '    }\n',
            'призрак: пересылки и медиа-пакеты уходят отложкой')

    # 1.3 прямые вызовы основного метода из чата (там scheduleDate задаётся литералом)
    chat = 'ui/ChatActivity.java'
    replace(chat, 'KAMIGRAM_AUTO_SCHEDULE_PHOTOS',
            '            getSendMessagesHelper().sendMessage(fmessages, dialog_id, false, false, true, 0, 0, null, -1, payStars, getSendMonoForumPeerId(), getSendMessageSuggestionParams());\n',
            '            getSendMessagesHelper().sendMessage(fmessages, dialog_id, false, false, true,\n'
            '                ' + GHOST + '.autoScheduleDate(0, dialog_id, fmessages), /* KAMIGRAM_AUTO_SCHEDULE_PHOTOS */\n'
            '                0, null, -1, payStars, getSendMonoForumPeerId(), getSendMessageSuggestionParams());\n',
            'призрак: отправка выбранных фото уходит отложкой')

    replace(chat, 'KAMIGRAM_AUTO_SCHEDULE_SEND',
            '                    getSendMessagesHelper().sendMessage(fmessages, did, false, false, notify, scheduleDate, scheduleRepeatPeriod, null, -1, price == null ? 0 : price, getSendMonoForumPeerId(), getSendMessageSuggestionParams());\n'
            '                }\n',
            '                    /* KAMIGRAM_AUTO_SCHEDULE_SEND: при призраке — отложкой */\n'
            '                    getSendMessagesHelper().sendMessage(fmessages, did, false, false, notify,\n'
            '                        ' + GHOST + '.autoScheduleDate(scheduleDate, did, fmessages),\n'
            '                        scheduleRepeatPeriod, null, -1, price == null ? 0 : price, getSendMonoForumPeerId(), getSendMessageSuggestionParams());\n'
            '                }\n',
            'призрак: рассылка по выбранным чатам уходит отложкой')

    # 1.4 пересылка: интерфейс больше не ждёт «отправлено» (сообщение ушло в отложенные)
    replace(chat, 'KAMIGRAM_AUTO_SCHEDULE_FORWARD',
            '        int result = getSendMessagesHelper().sendMessage(arrayList, dialog_id, fromMyName, hideCaption, notify, scheduleDate, 0, getThreadMessage(), -1, payStars, getSendMonoForumPeerId(), getSendMessageSuggestionParams());\n'
            '        AlertsCreator.showSendMediaAlert(result, this, themeDelegate);\n'
            '        if (result != 0) {\n',
            '        int result = getSendMessagesHelper().sendMessage(arrayList, dialog_id, fromMyName, hideCaption, notify,\n'
            '            ' + GHOST + '.autoScheduleDate(scheduleDate, dialog_id, arrayList),\n'
            '            0, getThreadMessage(), -1, payStars, getSendMonoForumPeerId(), getSendMessageSuggestionParams());\n'
            '        AlertsCreator.showSendMediaAlert(result, this, themeDelegate);\n'
            '        /* KAMIGRAM_AUTO_SCHEDULE_FORWARD: при авто-отложке сообщение ушло в\n'
            '           «Отложенные» — окно пересылки всё равно закрываем (как AyuGram). */\n'
            '        if (result != 0 || ' + GHOST + '.consumeAutoScheduled()) {\n',
            'призрак: пересылка закрывает окно и без «отправлено»')


# =============================================================================
# 2. АВТО-АРХИВ: ПРОВЕРКА СПИСКА ЧАТОВ ИЗ ПРИЛОЖЕНИЯ
# =============================================================================
def auto_archive():
    patch('ui/LaunchActivity.java', 'KAMIGRAM_AUTO_ARCHIVE',
          '        org.telegram.messenger.kamigram.KamiGramProxyPower.init(); // KAMIGRAM_PROXY_POWER\n',
          '        org.telegram.messenger.kamigram.KamiGramAutoArchive.init(); // KAMIGRAM_AUTO_ARCHIVE\n',
          'авто-архив: наблюдение за списком чатов включено')


# =============================================================================
# 3. ПАПКИ: СЧЁТЧИК НЕПРОЧИТАННЫХ — КАК У ВЫБРАННОЙ ВКЛАДКИ
# =============================================================================
def folder_counter():
    tabs = 'ui/Components/FilterTabsView.java'
    replace(tabs, 'KAMIGRAM_TAB_UNREAD_COLOR',
            '                unreadKey = Theme.key_chats_tabUnreadUnactiveBackground;\n'
            '                unreadOtherKey = Theme.key_chats_tabUnreadActiveBackground;\n',
            '                /* KAMIGRAM_TAB_UNREAD_COLOR: у НЕвыбранной папки счётчик\n'
            '                   непрочитанных рисуется теми же цветами, что у выбранной\n'
            '                   («нашего цвета»). Раньше он брал тусклый цвет и на тёмной\n'
            '                   панели числа было почти не видно. */\n'
            '                unreadKey = Theme.key_chats_tabUnreadActiveBackground;\n'
            '                unreadOtherKey = Theme.key_chats_tabUnreadUnactiveBackground;\n',
            'папки: счётчик непрочитанных у невыбранной вкладки — нашего цвета')


# =============================================================================
# 4. ИКОНКА ЗАГРУЗОК: БЕЗ «ПОСТОЯННОЙ АНИМАЦИИ»
# =============================================================================
def downloads_idle():
    icon = 'ui/DownloadProgressIcon.java'

    patch(icon, 'KAMIGRAM_NO_FAKE_DOWNLOAD_IDLE',
          '        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(8);\n'
          '        float r = AndroidUtilities.dp(1f);\n',
          '        /* KAMIGRAM_NO_FAKE_DOWNLOAD_IDLE: когда загрузок нет, иконка в шапке —\n'
          '           просто спокойная картинка: ни полоски прогресса, ни крутящейся\n'
          '           стрелки. Иконка при этом видна всегда (менеджер загрузок под рукой). */\n'
          '        final boolean kamigramIdle = currentListeners.isEmpty() && !hasUnviewedDownloads;\n'
          '        if (kamigramIdle) {\n'
          '            if (kamigramDownloadAnim) {\n'
          '                kamigramDownloadAnim = false;\n'
          '                downloadDrawable.stop();\n'
          '                downloadDrawable.setCurrentFrame(0, false);\n'
          '            }\n'
          '        } else if (!kamigramDownloadAnim && progress != 1f) {\n'
          '            kamigramDownloadAnim = true;\n'
          '            downloadDrawable.start();\n'
          '        }\n',
          'загрузки: в покое иконка статичная, без анимации', after=False)

    replace(icon, 'KAMIGRAM_NO_FAKE_DOWNLOAD_BAR',
            '        AndroidUtilities.rectTmp.set(startPadding, cy - r, getMeasuredWidth() - startPadding, cy + r);\n'
            '        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint2);\n'
            '\n'
            '        AndroidUtilities.rectTmp.set(startPadding, cy - r, startPadding + width * currentProgress, cy + r);\n'
            '        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);\n',
            '        /* KAMIGRAM_NO_FAKE_DOWNLOAD_BAR: полоску прогресса рисуем только когда\n'
            '           что-то реально скачивается. */\n'
            '        if (!kamigramIdle) {\n'
            '            AndroidUtilities.rectTmp.set(startPadding, cy - r, getMeasuredWidth() - startPadding, cy + r);\n'
            '            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint2);\n'
            '\n'
            '            AndroidUtilities.rectTmp.set(startPadding, cy - r, startPadding + width * currentProgress, cy + r);\n'
            '            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);\n'
            '        }\n',
            'загрузки: полоска прогресса только при реальной загрузке')

    replace(icon, 'KAMIGRAM_NO_FAKE_DOWNLOAD_LOOP',
            '            if (downloadDrawable.getCurrentFrame() == 0) {\n'
            '                downloadCompleteDrawable.setCurrentFrame(0, false);\n'
            '                downloadCompleteDrawable.start();\n'
            '                showCompletedIcon = true;\n'
            '            }\n',
            '            if (downloadDrawable.getCurrentFrame() == 0) {\n'
            '                /* KAMIGRAM_NO_FAKE_DOWNLOAD_LOOP: «галочка» после загрузки\n'
            '                   проигрывается ОДИН раз и замирает (раньше крутилась вечно). */\n'
            '                downloadCompleteDrawable.setAutoRepeat(0);\n'
            '                downloadCompleteDrawable.setCurrentFrame(0, false);\n'
            '                downloadCompleteDrawable.start();\n'
            '                showCompletedIcon = true;\n'
            '            }\n',
            'загрузки: «галочка» не зацикливается')


# =============================================================================
# 5. ИМЯ KAMIGRAM В ШАПКЕ: ЗАЩИТА ОТ ПОДМЕНЫ + ВОЗВРАТ НА МЕСТО
# =============================================================================
def title_lock():
    bar = 'ui/ActionBar/ActionBar.java'
    replace(bar, 'KAMIGRAM_TITLE_REFRESH',
            '        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n'
            '            return;\n'
            '        }\n',
            '        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n'
            '            /* KAMIGRAM_TITLE_REFRESH: главный экран — заголовок (имя KamiGram)\n'
            '               не подменяется ничем. Если его всё же кто-то тронул — вернуть. */\n'
            '            org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();\n'
            '            return;\n'
            '        }\n',
            'шапка: имя KamiGram возвращается, если его подменили')


# =============================================================================
# 6. ОДНОРАЗОВЫЕ И «САМОУНИЧТОЖАЮЩИЕСЯ» ФОТО: НЕ ИСЧЕЗАЮТ
# =============================================================================
def keep_viewonce_media():
    storage = 'messenger/MessagesStorage.java'

    # 6.1 «истёкшая фотография»: медиа больше не превращаем в пустышку
    replace(storage, 'KAMIGRAM_KEEP_VIEWONCE_MEDIA2',
            '    public void emptyMessagesMedia(long dialogId, ArrayList<Integer> mids) {\n'
            '        storageQueue.postRunnable(() -> {\n'
            '            SQLiteCursor cursor = null;\n',
            '    public void emptyMessagesMedia(long dialogId, ArrayList<Integer> mids) {\n'
            '        /* KAMIGRAM_KEEP_VIEWONCE_MEDIA2: это метод, который превращал медиа\n'
            '           одноразовых и «исчезающих» сообщений в пустышку (photoEmpty) — после\n'
            '           него в чате появлялась «истёкшая фотография». Больше ничего не\n'
            '           стираем: сообщение и медиа остаются в базе и в чате. */\n'
            '        if (org.telegram.messenger.kamigram.KamiGramConfig.keepExpiredMedia()) {\n'
            '            return;\n'
            '        }\n'
            '        storageQueue.postRunnable(() -> {\n'
            '            SQLiteCursor cursor = null;\n',
            'одноразовые фото: «истёкшая фотография» больше не появляется')

    # 6.2 отметка «посмотрел» больше не вычищает медиа у сообщений с таймером
    replace(storage, 'KAMIGRAM_KEEP_VIEWONCE_MEDIA3',
            '                if (arrayList != null) {\n'
            '                    emptyMessagesMedia(dialogId, arrayList);\n'
            '                }\n',
            '                /* KAMIGRAM_KEEP_VIEWONCE_MEDIA3: раньше здесь у «исчезающих»\n'
            '                   сообщений вычищалось медиа сразу после отметки «прочитано».\n'
            '                   Теперь медиа остаётся на месте. */\n',
            'самоуничтожающиеся фото: медиа не вычищается после прочтения')


def keep_deleted_private():
    patch('messenger/MessagesController.java', 'KAMIGRAM_KEEP_DELETED_PRIVATE',
          '                    /* KAMIGRAM_KEEP_DELETED_PEER: удалённое собеседником остаётся у нас */\n'
          '                    ' + DEL + '.remember(dialogId, arrayList);\n',
          '                    /* KAMIGRAM_KEEP_DELETED_PRIVATE: в личных чатах Telegram присылает\n'
          '                       удаление БЕЗ id чата (0) — запоминаем по номеру сообщения: в\n'
          '                       личных чатах номера уникальны для всего аккаунта. */\n'
          '                    if (dialogId == 0) {\n'
          '                        ' + DEL + '.rememberUnknown(arrayList);\n'
          '                    } else {\n'
          '                        ' + DEL + '.remember(dialogId, arrayList);\n'
          '                    }\n',
          'удалённые: личные чаты тоже удерживают сообщения')


def main():
    auto_schedule()
    auto_archive()
    folder_counter()
    downloads_idle()
    title_lock()
    keep_viewonce_media()
    keep_deleted_private()

    print('r68: изменений — %d' % len(DONE))
    for what in DONE:
        print('  ✓ %s' % what)
    if MISS:
        print('r68: пропущено — %d' % len(MISS))
        for what in MISS:
            print('  ! %s' % what)


if __name__ == '__main__':
    main()
