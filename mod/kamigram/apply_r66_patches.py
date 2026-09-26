#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sakura r66: патчи поверх исходников Telegram (DrKLO 12.10.3).

Правки этого пакета — по жалобам пользователя:

  1. «название Sakura пропадает, когда прокси подключён» — в шапке главного
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
            'шапка: имя Sakura настоящим текстом (без картинки-логотипа)')


def title_lock():
    actionbar = 'ui/ActionBar/ActionBar.java'
    patch(actionbar, 'KAMIGRAM_TITLE_LOCK',
          '    public void setTitleOverlayText(String title, int titleId, Runnable action) {\n',
          '        /* KAMIGRAM_TITLE_LOCK: на главном экране заголовок (имя Sakura) не подменяется\n'
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
