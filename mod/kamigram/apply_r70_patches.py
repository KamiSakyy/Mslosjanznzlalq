#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram r70: патчи поверх исходников Telegram (DrKLO 12.10.3).

Правки этого пакета — по списку пользователя:

  5.  Подписка на канал t.me/AsuMeo обязательна: проверка — только ПОСЛЕ входа
      в аккаунт; без подписки открывается красивое окно, которое невозможно
      закрыть; кнопка «Подписаться» сама подписывает через API (channels.joinChannel)
      и сама открывает канал. Отпишешься — попросим снова. (KamiGramChannelGuard)

  6.  Подпись «Подключение…» как в оригинальном Telegram: нет интернета —
      надпись в шапке, соединение успешное — надпись пропадает. (раньше её
      убирали совсем — пользователю стало непонятно, есть сеть или нет)

  7.  «Пересылать без имени» (центр KamiGram): пересылки всегда без имени
      отправителя.

  8.  Улучшение мобильного интернета: ТОЧЕЧНОЕ ускорение — когда пользователь
      нажал на фото/файл, все остальные загрузки ставятся на паузу, а нажатое
      медиа качается со всеми потоками (12) и крупным блоком (512 КБ).
      Искусственные ограничения Telegram (лимиты очередей) подняты.
      (KamiGramNetBoost)

  9.  В меню сообщения добавлены «Сгореть» и «Прочитать» (для сгорающих и
      по таймеру): «Сгореть» — сообщение уничтожается у собеседника, а у нас
      локально остаётся (наши защиты от удаления); «Прочитать» — пометить
      прочитанным без сгорания. Сгорающие и по таймеру теперь можно пересылать
      (как в AyuGram: снят только клиентский запрет).

  10. Иконка загрузок анимируется ТОЛЬКО пока реально идут байты: загрузка
      замерла (очередь/пауза) — анимация гасится сама через 3 секунды.

  11. Иконка призрака — минималистичная, наш белый цвет: выключен — тонкий
      контур, включён — заполненная. (камigram/res/drawable/kamigram_ghost*.xml)

  12/13. Чаты — наша тема Yoru (цвета сообщений и фона) при ЛЮБОЙ системной
      настройке: все ВСТРОЕННЫЕ темы (Night, Blue, Dark Blue, Arctic, Day)
      переписаны на палитру Yoru. СВОИ темы и обои пользователя — не трогаем.

  14. Премиум разблокирован локально: свой аккаунт всегда считается
      premium (все премиум-функции доступны в приложении).

  15. «Отправлять всегда HD» (включено по умолчанию): фото всегда уходят в
      максимальном качестве (4096, JPEG 99).

  16. «Применять KamiGram ко всем аккаунтам» (включено по умолчанию): можно
      выключить — тогда настройки мода хранятся у каждого аккаунта отдельно.

  1.  (МАСШТАБНОЕ) «Поверх приложений»: иконка рядом с призраком. Тап —
      приложение сворачивается в PiP-окно поверх ЛЮБОГО приложения (интерактивное,
      всё кликается, размер — ручкой системы); после выхода из PiP — маленький
      летающий круглешок (перетаскивается, тап — открыть ТГ). Разрешение
      «Поверх других приложений» запрашиваем сами (манифест уже разрешён).
      (KamiGramFloat)

Каждый патч идемпотентен: ищет свой маркер и второй раз ничего не делает.
Если якорь не найден — патч попадает в отчёт, но сборка не падает.
"""

import io
import os
import re
import sys

DONE = []
MISS = []

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('TG_DIR', '.')
APP_NAME = sys.argv[2] if len(sys.argv) > 2 else os.environ.get('APP_NAME', 'KamiGram')
JAVA = os.path.join(TG, 'TMessagesProj/src/main/java/org/telegram')

GHOST = 'org.telegram.messenger.kamigram.KamiGramGhost'
GUARD = 'org.telegram.messenger.kamigram.KamiGramChannelGuard'
FLOAT = 'org.telegram.messenger.kamigram.KamiGramFloat'
BOOST = 'org.telegram.messenger.kamigram.KamiGramNetBoost'
SPEED = 'org.telegram.messenger.kamigram.KamiGramSpeed'
CFG = 'org.telegram.messenger.kamigram.KamiGramConfig'
UI = 'org.telegram.messenger.kamigram.KamiGramUi'


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


def replace_all(rel, marker, old, new, what):
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
    count = src.count(old)
    write(p, src.replace(old, new))
    DONE.append(what + ' (x%d)' % count)
    return True


# =============================================================================
# 6. «ПОДКЛЮЧЕНИЕ…» — как в оригинальном Telegram
# =============================================================================
def connecting_subtitle():
    act = 'ui/LaunchActivity.java'

    replace(act, 'KAMIGRAM_CONNECTING_SUBTITLE',
            '        /* KAMIGRAM_PROXY_TITLE_OVERLAY: убираем надписи «Подключение…» полностью.\n'
            '           Раньше Telegram подменял название приложения строкой «Подключение к прокси…»,\n'
            '           и она висела даже после подключения. Теперь название всегда на месте,\n'
            '           никакого текста состояния рядом нет. */\n'
            '        actionBarLayout.setTitleOverlayText(null, 0, null);',
            '        /* KAMIGRAM_CONNECTING_SUBTITLE (r70): как в оригинальном Telegram —\n'
            '           нет интернета: в шапке «Подключение…» / «Ожидание сети»;\n'
            '           соединение успешное: title == null и надпись пропадает.\n'
            '           Имя KamiGram на главном экране защищено отдельно\n'
            '           (KAMIGRAM_TITLE_LOCK_R70 в ActionBar). */\n'
            '        actionBarLayout.setTitleOverlayText(title, titleId, action);',
            'шапка: «Подключение…» при отсутствии сети, как в оригинале')

    replace('ui/ActionBar/ActionBar.java', 'KAMIGRAM_TITLE_LOCK_R70',
            '    public void setTitleOverlayText(String title, int titleId, Runnable action) {\n'
            '        /* KAMIGRAM_TITLE_LOCK: на главном экране заголовок (имя KamiGram) не подменяется\n'
            '           ничем: ни «Подключением к прокси…», ни стрелками, ни состоянием сети. */\n'
            '        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n'
            '            /* KAMIGRAM_TITLE_REFRESH: главный экран — заголовок (имя KamiGram)\n'
            '               не подменяется ничем. Если его всё же кто-то тронул — вернуть. */\n'
            '            org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();\n'
            '            return;\n'
            '        }',
            '    public void setTitleOverlayText(String title, int titleId, Runnable action) {\n'
            '        /* KAMIGRAM_TITLE_LOCK_R70: на главном экране — как в оригинальном\n'
            '           Telegram: при разрыве сети (title != null) показываем «Подключение…»,\n'
            '           а когда соединение есть (title == null) — имя KamiGram на месте. */\n'
            '        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {\n'
            '            if (title == null) {\n'
            '                /* соединение есть — держим имя KamiGram, ничего не подменяем */\n'
            '                org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();\n'
            '                return;\n'
            '            }\n'
            '            /* нет сети — показываем оригинальную подпись «Подключение…» */\n'
            '        }',
            'шапка: KamiGram-имя не пропадает, «Подключение…» — только без сети')


# =============================================================================
# 10. иконка загрузок: анимация = только пока реально идут байты
# =============================================================================
def download_icon_live():
    icon = 'ui/DownloadProgressIcon.java'

    patch(icon, 'KAMIGRAM_DOWNLOAD_ANIM_LIVE_FIELD',
          '    private boolean kamigramDownloadAnim; /* KAMIGRAM_NO_FAKE_DOWNLOAD_FIELD */',
          '\n    private float kamigramLastProgress = -1f; /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_FIELD */\n'
          '    private long kamigramLastProgressAt; /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_FIELD */\n'
          '    private boolean kamigramLiveCheckScheduled; /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_FIELD */',
          'загрузки: поля живого прогресса')

    replace(icon, 'KAMIGRAM_DOWNLOAD_ANIM_LIVE',
            '        progressDt = (progress - currentProgress) * 16f / 150f;\n'
            '        invalidate();\n'
            '    }',
            '        progressDt = (progress - currentProgress) * 16f / 150f;\n'
            '        /* KAMIGRAM_DOWNLOAD_ANIM_LIVE (r70): анимация живёт, пока реально\n'
            '           идут байты; замерло — гасится контроллером ниже. */\n'
            '        if (progress != kamigramLastProgress) {\n'
            '            kamigramLastProgress = progress;\n'
            '            kamigramLastProgressAt = System.currentTimeMillis();\n'
            '            if (currentListeners.size() > 0 && !kamigramDownloadAnim) {\n'
            '                kamigramDownloadAnim = true;\n'
            '                downloadDrawable.start();\n'
            '            }\n'
            '        }\n'
            '        if (currentListeners.size() > 0) {\n'
            '            kamigramScheduleLiveCheck();\n'
            '        }\n'
            '        invalidate();\n'
            '    }',
            'загрузки: анимация только при реальном прогрессе')

    patch(icon, 'KAMIGRAM_DOWNLOAD_ANIM_LIVE_CHECK',
          '    private class ProgressObserver implements DownloadController.FileDownloadProgressListener {',
          '    /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_CHECK (r70): загрузка замерла (очередь/пауза/\n'
          '       нет прогресса) — гасим анимацию: иконка не крутится «ни о чём».\n'
          '       Один контроллер на время: повторные вызовы не плодят запусков. */\n'
          '    private void kamigramScheduleLiveCheck() {\n'
          '        if (kamigramLiveCheckScheduled) {\n'
          '            return;\n'
          '        }\n'
          '        kamigramLiveCheckScheduled = true;\n'
          '        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {\n'
          '            kamigramLiveCheckScheduled = false;\n'
          '            if (currentListeners.size() == 0 || !kamigramDownloadAnim) {\n'
          '                return;\n'
          '            }\n'
          '            if (System.currentTimeMillis() - kamigramLastProgressAt > 3000L) {\n'
          '                kamigramDownloadAnim = false;\n'
          '                downloadDrawable.stop();\n'
          '                downloadDrawable.setCurrentFrame(0, false);\n'
          '            } else {\n'
          '                kamigramScheduleLiveCheck();\n'
          '            }\n'
          '        }, 1500);\n'
          '    }\n\n',
          'загрузки: контроллер «живого» состояния анимации',
          after=False)

    replace(icon, 'KAMIGRAM_DOWNLOAD_ANIM_LIVE_NEW',
            '        /* KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE: анимация = признак реальной загрузки */\n'
            '        if (currentListeners.size() > 0) {\n'
            '            if (!kamigramDownloadAnim) {\n'
            '                kamigramDownloadAnim = true;\n'
            '                downloadDrawable.start();\n'
            '            }\n'
            '        } else if (kamigramDownloadAnim) {',
            '        /* KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE: анимация = признак реальной загрузки */\n'
            '        if (currentListeners.size() > 0) {\n'
            '            if (!kamigramDownloadAnim) {\n'
            '                kamigramDownloadAnim = true;\n'
            '                downloadDrawable.start();\n'
            '            }\n'
            '            /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_NEW (r70): новый файл — 3 секунды на\n'
            '               первый прогресс, потом контроль живого состояния. */\n'
            '            kamigramLastProgressAt = System.currentTimeMillis();\n'
            '            kamigramScheduleLiveCheck();\n'
            '        } else if (kamigramDownloadAnim) {',
            'загрузки: новый файл получает «окно» на первый прогресс')

    replace(icon, 'KAMIGRAM_DOWNLOAD_ANIM_LIVE_DRAW',
            '        } else if (!kamigramDownloadAnim && progress != 1f) {',
            '        } else if (!kamigramDownloadAnim && progress != 1f && (kamigramLastProgressAt == 0L || System.currentTimeMillis() - kamigramLastProgressAt <= 3000L)) { /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_DRAW (r70): перезапуск только при свежем прогрессе — замерла загрузка, анимация не оживает сама */',
            'загрузки: авто-перезапуск анимации только при свежем прогрессе')


# =============================================================================
# 9. «Сгореть» / «Прочитать» в меню + пересылка сгорающих
# =============================================================================
def burn_read_menu():
    chat = 'ui/ChatActivity.java'

    patch(chat, 'KAMIGRAM_OPTIONS_R70',
          '    public final static int OPTION_WELCOME_REVERT = 116;',
          '\n    /* KAMIGRAM_OPTIONS_R70: «Сгореть» и «Прочитать» в меню сообщения */\n'
          '    public final static int OPTION_KAMIGRAM_BURN = 201;\n'
          '    public final static int OPTION_KAMIGRAM_READ = 202;',
          'меню: новые опции «Сгореть» и «Прочитать»')

    replace(chat, 'KAMIGRAM_MENU_BURN',
            '                if (canForward) {\n'
            '                    items.add(LocaleController.getString(R.string.Forward));\n'
            '                    options.add(OPTION_FORWARD);\n'
            '                    icons.add(R.drawable.msg_forward);\n'
            '                }',
            '                if (canForward) {\n'
            '                    items.add(LocaleController.getString(R.string.Forward));\n'
            '                    options.add(OPTION_FORWARD);\n'
            '                    icons.add(R.drawable.msg_forward);\n'
            '                }\n'
            '                /* KAMIGRAM_MENU_BURN (r70): «Сгореть» — уничтожить у\n'
            '                   собеседника (у нас локально остаётся); «Прочитать» —\n'
            '                   просто пометить прочитанным, без сгорания. Показываем\n'
            '                   для одноразовых/по таймеру сообщений. */\n'
            '                try {\n'
            '                    if (selectedObject != null && selectedObject.messageOwner != null\n'
            '                        && ' + GHOST + '.isEphemeralMedia(selectedObject.messageOwner)) {\n'
            '                        items.add("Сгореть");\n'
            '                        options.add(OPTION_KAMIGRAM_BURN);\n'
            '                        icons.add(org.telegram.messenger.R.drawable.kamigram_burn);\n'
            '                        if (!selectedObject.isOut()) {\n'
            '                            items.add("Прочитать");\n'
            '                            options.add(OPTION_KAMIGRAM_READ);\n'
            '                            icons.add(R.drawable.msg_actions);\n'
            '                        }\n'
            '                    }\n'
            '                } catch (Throwable kamigramBurnIgnore) {\n'
            '                }',
            'меню: пункты «Сгореть» и «Прочитать»')

    replace(chat, 'KAMIGRAM_CASE_BURN',
            '    private void processSelectedOption(int option) {\n'
            '        if (selectedObject == null || getParentActivity() == null) {\n'
            '            return;\n'
            '        }\n'
            '        boolean preserveDim = false;\n'
            '        switch (option) {',
            '    private void processSelectedOption(int option) {\n'
            '        if (selectedObject == null || getParentActivity() == null) {\n'
            '            return;\n'
            '        }\n'
            '        boolean preserveDim = false;\n'
            '        switch (option) {\n'
            '            /* KAMIGRAM_CASE_BURN (r70): «Сгореть» — у собеседника сгорает,\n'
            '               у нас локальная копия остаётся (защиты от удаления). */\n'
            '            case OPTION_KAMIGRAM_BURN: {\n'
            '                try {\n'
            '                    ' + GHOST + '.burnMessage(currentAccount, dialog_id, selectedObject);\n'
            '                    ' + UI + '.notify(getParentActivity(), "Сообщение будет уничтожено у собеседника");\n'
            '                } catch (Throwable kamigramBurnIgnore) {\n'
            '                }\n'
            '                break;\n'
            '            }\n'
            '            /* KAMIGRAM_CASE_READ (r70): «Прочитать» — пометить прочитанным,\n'
            '               без сгорания. */\n'
            '            case OPTION_KAMIGRAM_READ: {\n'
            '                try {\n'
            '                    ' + GHOST + '.readMessage(currentAccount, dialog_id, selectedObject);\n'
            '                    ' + UI + '.notify(getParentActivity(), "Отмечено прочитанным");\n'
            '                } catch (Throwable kamigramReadIgnore) {\n'
            '                }\n'
            '                break;\n'
            '            }',
            'меню: действия «Сгореть» и «Прочитать»')

    replace(chat, 'KAMIGRAM_FORWARD_EPHEMERAL',
            '                final boolean canForward = !selectedObject.isSponsored()\n'
            '                    && !isQuickRepliesOrWelcomeMessagesMode()\n'
            '                    && chatMode != MODE_SCHEDULED\n'
            '                    && (!selectedObject.needDrawBluredPreview() || selectedObject.hasExtendedMediaPreview())',
            '                final boolean canForward = !selectedObject.isSponsored()\n'
            '                    && !isQuickRepliesOrWelcomeMessagesMode()\n'
            '                    && chatMode != MODE_SCHEDULED\n'
            '                    && (!selectedObject.needDrawBluredPreview() || selectedObject.hasExtendedMediaPreview()\n'
            '                        || (/* KAMIGRAM_FORWARD_EPHEMERAL (r70): как в AyuGram — */\n'
            '                            ' + CFG + '.forwardEphemeral()\n'
            '                            && selectedObject.messageOwner != null\n'
            '                            && ' + GHOST + '.isEphemeralMedia(selectedObject.messageOwner)))',
            'меню: сгорающие и по таймеру можно пересылать')


# =============================================================================
# 7. пересылка без имени
# =============================================================================
def forward_no_name():
    replace('ui/ChatActivity.java', 'KAMIGRAM_FORWARD_NONAME',
            '                    forwardMessages(messagesToForward, messagePreviewParams.hideForwardSendersName, messagePreviewParams.hideCaption, notify, scheduleDate != 0 && scheduleDate != 0x7ffffffe ? scheduleDate + 1 : scheduleDate, payStars);',
            '                    /* KAMIGRAM_FORWARD_NONAME (r70): «Пересылать без имени» —\n'
            '                       пересылки всегда без имени отправителя. */\n'
            '                    forwardMessages(messagesToForward, messagePreviewParams.hideForwardSendersName || ' + CFG + '.forwardWithoutName(), messagePreviewParams.hideCaption, notify, scheduleDate != 0 && scheduleDate != 0x7ffffffe ? scheduleDate + 1 : scheduleDate, payStars);',
            'пересылка: всегда без имени (если включено)')


# =============================================================================
# 8. точечный буст скорости
# =============================================================================
def net_boost():
    queue = 'messenger/FileLoaderPriorityQueue.java'

    replace(queue, 'KAMIGRAM_QUEUE_MAX',
            '        int max = type == TYPE_LARGE ? MessagesController.getInstance(currentAccount).largeQueueMaxActiveOperations : MessagesController.getInstance(currentAccount).smallQueueMaxActiveOperations;',
            '        /* KAMIGRAM_QUEUE_MAX (r70): лимиты параллельных загрузок выше штатных —\n'
            '           часть «искусственных задержек» Telegram снята. */\n'
            '        int max = type == TYPE_LARGE ? Math.max(MessagesController.getInstance(currentAccount).largeQueueMaxActiveOperations, ' + SPEED + '.largeQueueMax()) : Math.max(MessagesController.getInstance(currentAccount).smallQueueMaxActiveOperations, ' + SPEED + '.smallQueueMax());',
            'сети: лимиты очередей загрузок подняты')

    patch(queue, 'KAMIGRAM_NET_FOCUS_QUEUE_VAR',
          '        tmpListOperations.clear();',
          '        /* KAMIGRAM_NET_FOCUS_QUEUE_VAR (r70): пока пользователь смотрит\n'
          '           конкретный файл — остальные загрузки на паузе. */\n'
          '        final String kamigramFocus = ' + BOOST + '.focusFile(currentAccount);\n',
          'сети: переменная фокуса в планировщике очередей')

    replace(queue, 'KAMIGRAM_NET_FOCUS_LOOP',
            '            FileLoadOperation operation = allOperations.get(i);\n'
            '            if (i > 0 && !pauseAllNextOperations) {',
            '            FileLoadOperation operation = allOperations.get(i);\n'
            '            /* KAMIGRAM_NET_FOCUS_LOOP (r70): фокусный файл — идёт, остальные — пауза */\n'
            '            boolean kamigramFocusHit = kamigramFocus != null && operation.getFileName().equals(kamigramFocus);\n'
            '            boolean kamigramPaused = kamigramFocus != null && !kamigramFocusHit;\n'
            '            if (i > 0 && !pauseAllNextOperations) {',
            'сети: фокусное/пауза в цикле очередей')

    replace(queue, 'KAMIGRAM_NET_FOCUS_START',
            '            } else if (!pauseAllNextOperations && i < max) {',
            '            } else if (!pauseAllNextOperations && (kamigramFocusHit || (i < max && !kamigramPaused))) { /* KAMIGRAM_NET_FOCUS_START */',
            'сети: фокусный файл стартует вне очереди')

    op = 'messenger/FileLoadOperation.java'

    replace(op, 'KAMIGRAM_NET_FOCUS_PARAMS',
            '            maxDownloadRequests = 4;\n'
            '            maxDownloadRequestsBig = 4;\n'
            '        }\n'
            '        maxCdnParts = (int) (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig);\n'
            '    }',
            '            maxDownloadRequests = 4;\n'
            '            maxDownloadRequestsBig = 4;\n'
            '        }\n'
            '        /* KAMIGRAM_NET_FOCUS_PARAMS (r70): у фокусного файла (нажал\n'
            '           пользователь) — больше потоков и крупнее блок. Пересчитывается\n'
            '           в start(), когда имя файла уже известно. */\n'
            '        if (fileName != null && ' + BOOST + '.isFocused(currentAccount, fileName)) {\n'
            '            downloadChunkSize = ' + BOOST + '.boostChunk();\n'
            '            downloadChunkSizeBig = ' + BOOST + '.boostChunk();\n'
            '            maxDownloadRequests = ' + BOOST + '.boostRequests();\n'
            '            maxDownloadRequestsBig = ' + BOOST + '.boostRequests();\n'
            '            maxDownloadRequestsAnimation = ' + BOOST + '.boostRequests();\n'
            '        }\n'
            '        maxCdnParts = (int) (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig);\n'
            '    }',
            'сети: фокусный файл получает все потоки')

    patch(op, 'KAMIGRAM_NET_FOCUS_FASTNET',
          '            maxDownloadRequestsAnimation = org.telegram.messenger.kamigram.KamiGramSpeed.streamRequests();',
          '\n            /* KAMIGRAM_NET_FOCUS_FASTNET (r70): фокусный файл важнее\n'
          '               режима «быстрый интернет» — ему все потоки. */\n'
          '            if (fileName != null && ' + BOOST + '.isFocused(currentAccount, fileName)) {\n'
          '                downloadChunkSize = ' + BOOST + '.boostChunk();\n'
          '                downloadChunkSizeBig = ' + BOOST + '.boostChunk();\n'
          '                maxDownloadRequests = ' + BOOST + '.boostRequests();\n'
          '                maxDownloadRequestsBig = ' + BOOST + '.boostRequests();\n'
          '                maxDownloadRequestsAnimation = ' + BOOST + '.boostRequests();\n'
          '            }',
          'сети: фокус побеждает режим быстрого интернета')

    loader = 'messenger/FileLoader.java'

    patch(loader, 'KAMIGRAM_NET_FOCUS_TRIGGER',
          '        if (fileName == null || fileName.contains("" + Integer.MIN_VALUE)) {\n'
          '            return null;\n'
          '        }',
          '        if (fileName == null || fileName.contains("" + Integer.MIN_VALUE)) {\n'
          '            return null;\n'
          '        }\n'
          '        /* KAMIGRAM_NET_FOCUS_TRIGGER (r70): пользователь запросил файл\n'
          '           (высокий приоритет, размер > 300 КБ) — берём «фокус скорости». */\n'
          '        try {\n'
          '            if (stream == null && priority >= PRIORITY_HIGH && locationSize > ' + BOOST + '.minFocusSize()) {\n'
          '                ' + BOOST + '.focus(currentAccount, fileName);\n'
          '            }\n'
          '        } catch (Throwable kamigramFocusIgnore) {\n'
          '        }',
          'сети: фокус берётся на запрос пользователя')

    patch(loader, 'KAMIGRAM_NET_FOCUS_RECHECK',
          '    public void setDelegate(FileLoaderDelegate fileLoaderDelegate) {',
          '    /* KAMIGRAM_NET_FOCUS_RECHECK (r70): пересобрать все очереди загрузок\n'
          '       (фокус включился/снялся — какие файлы качаются). */\n'
          '    public void kamigramRecheckQueues() {\n'
          '        try {\n'
          '            for (FileLoadOperation operation : loadOperationPaths.values()) {\n'
          '                try {\n'
          '                    FileLoaderPriorityQueue queue = operation.getQueue();\n'
          '                    if (queue != null) {\n'
          '                        queue.checkLoadingOperations();\n'
          '                    }\n'
          '                } catch (Throwable ignore) {\n'
          '                }\n'
          '            }\n'
          '        } catch (Throwable ignore) {\n'
          '        }\n'
          '    }\n\n',
          'сети: метод пересборки очередей (фокус)',
          after=False)

    replace(loader, 'KAMIGRAM_NET_FOCUS_UNFOCUS',
            '                if (!operation.isPreloadVideoOperation()) {\n'
            '                    loadOperationPathsUI.remove(fileName);\n'
            '                    if (delegate != null) {\n'
            '                        delegate.fileDidLoaded(fileName, finalFile, parentObject, finalType);\n'
            '                    }\n'
            '                }\n\n'
            '                checkDownloadQueue(operation, operation.getQueue(), 0);',
            '                if (!operation.isPreloadVideoOperation()) {\n'
            '                    loadOperationPathsUI.remove(fileName);\n'
            '                    if (delegate != null) {\n'
            '                        delegate.fileDidLoaded(fileName, finalFile, parentObject, finalType);\n'
            '                    }\n'
            '                }\n\n'
            '                /* KAMIGRAM_NET_FOCUS_UNFOCUS (r70): файл готов — фокус снимаем,\n'
            '                   остальные загрузки продолжают. */\n'
            '                try {\n'
            '                    ' + BOOST + '.unfocus(currentAccount, fileName);\n'
            '                } catch (Throwable kamigramFocusIgnore) {\n'
            '                }\n'
            '                checkDownloadQueue(operation, operation.getQueue(), 0);',
            'сети: фокус снимается после докачки')

    replace(loader, 'KAMIGRAM_NET_FOCUS_FAIL',
            '            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {\n'
            '                loadOperationPathsUI.remove(fileName);',
            '            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {\n'
            '                loadOperationPathsUI.remove(fileName);\n'
            '                /* KAMIGRAM_NET_FOCUS_FAIL (r70): файл сломался — фокус снимаем */\n'
            '                try {\n'
            '                    ' + BOOST + '.unfocus(currentAccount, fileName);\n'
            '                } catch (Throwable kamigramFocusIgnore) {\n'
            '                }',
            'сети: фокус снимается после сбоя')

    replace(loader, 'KAMIGRAM_NET_FOCUS_TOUCH',
            '            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {\n'
            '                if (delegate != null) {',
            '            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {\n'
            '                /* KAMIGRAM_NET_FOCUS_TOUCH (r70): прогресс идёт — фокус жив */\n'
            '                try {\n'
            '                    ' + BOOST + '.touchFocus(currentAccount, fileName);\n'
            '                } catch (Throwable kamigramFocusIgnore) {\n'
            '                }\n'
            '                if (delegate != null) {',
            'сети: прогресс продлевает фокус')


# =============================================================================
# 14. локальный премиум
# =============================================================================
def premium_local():
    replace_all('tgnet/TLRPC.java', 'KAMIGRAM_PREMIUM',
                '            premium = hasFlag(flags, FLAG_28);',
                '            premium = hasFlag(flags, FLAG_28) || self; /* KAMIGRAM_PREMIUM: локальный премиум для своего аккаунта (self — уже распарсен выше) */',
                'премиум: свой аккаунт — premium')

    replace('messenger/UserConfig.java', 'KAMIGRAM_PREMIUM_UI',
            '    public boolean isPremium() {\n'
            '        TLRPC.User user = currentUser;\n'
            '        if (user == null) {\n'
            '            return false;\n'
            '        }\n'
            '        return user.premium;\n'
            '    }',
            '    public boolean isPremium() {\n'
            '        TLRPC.User user = currentUser;\n'
            '        if (user == null) {\n'
            '            return false;\n'
            '        }\n'
            '        return true; /* KAMIGRAM_PREMIUM_UI: премиум-функции доступны локально */\n'
            '    }',
            'премиум: isPremium() — всегда для своего аккаунта')


# =============================================================================
# 15. всегда HD
# =============================================================================
def always_hd():
    replace('messenger/MediaController.java', 'KAMIGRAM_SEND_HD',
            '        public boolean isHighQuality() {\n'
            '            if (highQuality == null)\n'
            '                return SharedConfig.photoHighQualityDefault;\n'
            '            return highQuality;\n'
            '        }',
            '        public boolean isHighQuality() {\n'
            '            /* KAMIGRAM_SEND_HD (r70): «Отправлять всегда HD» — фото и видео\n'
            '               всегда уходят в максимальном качестве (4096, JPEG 99). */\n'
            '            if (' + CFG + '.sendHd()) {\n'
            '                return true;\n'
            '            }\n'
            '            if (highQuality == null)\n'
            '                return SharedConfig.photoHighQualityDefault;\n'
            '            return highQuality;\n'
            '        }',
            'отправка: всегда HD (4096, JPEG 99)')


# =============================================================================
# 5. подписка на канал (только после входа)
# =============================================================================
def channel_guard():
    patch('ui/LaunchActivity.java', 'KAMIGRAM_CHANNEL_GUARD',
          '        /* KAMIGRAM_SMART_PROXY */\n'
          '        try {\n'
          '            org.telegram.messenger.kamigram.KamiGramProxyHelper.activateFromClipboard(this);\n'
          '            org.telegram.messenger.kamigram.KamiGramProxyHelper.watchProxy(this);\n'
          '        } catch (Throwable ignore) {\n'
          '        }',
          '\n        /* KAMIGRAM_CHANNEL_GUARD (r70): после входа в аккаунт проверяем\n'
          '           подписку на канал — без подписки пользоваться нельзя. */\n'
          '        try {\n'
          '            ' + GUARD + '.check(this);\n'
          '        } catch (Throwable ignore) {\n'
          '        }',
          'подписка: проверка после входа в аккаунт')


# =============================================================================
# 1. (МАСШТАБНОЕ) поверх приложений: PiP + летающий круглешок
# =============================================================================
def float_window():
    patch('ui/DialogsActivity.java', 'KAMIGRAM_FLOAT_HEADER',
          '            org.telegram.messenger.kamigram.KamiGramGhost.addHeaderItem(menu, null);',
          '\n            /* KAMIGRAM_FLOAT_HEADER (r70): «поверх приложений» — рядом с призраком */\n'
          '            ' + FLOAT + '.addHeaderItem(menu);',
          'плавающее окно: иконка в шапке рядом с призраком')

    replace('ui/LaunchActivity.java', 'KAMIGRAM_FLOAT_STOP',
            '    protected void onStop() {\n'
            '        super.onStop();\n'
            '        isStarted = false;\n'
            '        pipActivityHandler.onStop();',
            '    protected void onStop() {\n'
            '        super.onStop();\n'
            '        isStarted = false;\n'
            '        pipActivityHandler.onStop();\n'
            '        /* KAMIGRAM_FLOAT_STOP (r70): на фоне — летающий круглешок поверх всего */\n'
            '        try {\n'
            '            ' + FLOAT + '.onAppStop();\n'
            '        } catch (Throwable ignore) {\n'
            '        }',
            'плавающее окно: круглешок при уходе на фон')

    replace('ui/LaunchActivity.java', 'KAMIGRAM_FLOAT_PIP',
            '        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);\n'
            '        pipActivityHandler.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);',
            '        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);\n'
            '        pipActivityHandler.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);\n'
            '        /* KAMIGRAM_FLOAT_PIP (r70): вышли из PiP — показываем круглешок */\n'
            '        try {\n'
            '            ' + FLOAT + '.onPipModeChanged(isInPictureInPictureMode);\n'
            '        } catch (Throwable ignore) {\n'
            '        }',
            'плавающее окно: реакция на выход из PiP')

    # после channel_guard (один и тот же onResume)
    patch('ui/LaunchActivity.java', 'KAMIGRAM_FLOAT_RESUME',
          '        /* KAMIGRAM_CHANNEL_GUARD (r70): после входа в аккаунт проверяем\n'
          '           подписку на канал — без подписки пользоваться нельзя. */\n'
          '        try {\n'
          '            ' + GUARD + '.check(this);\n'
          '        } catch (Throwable ignore) {\n'
          '        }',
          '\n        /* KAMIGRAM_FLOAT_RESUME (r70): на переднем плане — круглешок прячем */\n'
          '        try {\n'
          '            ' + FLOAT + '.onAppResume();\n'
          '        } catch (Throwable ignore) {\n'
          '        }',
          'плавающее окно: прячем круглешок на переднем плане')


def main():
    connecting_subtitle()
    download_icon_live()
    burn_read_menu()
    forward_no_name()
    net_boost()
    premium_local()
    always_hd()
    channel_guard()
    float_window()

    print('r70: изменений — %d' % len(DONE))
    for what in DONE:
        print('  ✓ %s' % what)
    if MISS:
        print('r70: пропущено — %d' % len(MISS))
        for what in MISS:
            print('  ! %s' % what)


if __name__ == '__main__':
    main()
