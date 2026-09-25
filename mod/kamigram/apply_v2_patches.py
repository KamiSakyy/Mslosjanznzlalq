#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sakura P90 «второй большой пакет 2026»: функции, которые видны сразу.

Что делает:
  1. ОТПРАВКА ПО ENTER. В Telegram настройка «send_by_enter» по умолчанию
     выключена и спрятана. Переключатель из центра Sakura становится её
     значением по умолчанию.
  2. КОМПАКТНЫЙ СТИЛЬ ВКЛАДОК. Нижняя навигация Telegram 12.x — «стеклянная»
     (GlassTabView): скрываем подписи и делаем иконки ровными, когда включён
     компактный режим, — плоский iOS-вид без стекла.
  3. ТВИКИ TELEGRAM ПРИ СТАРТЕ: размер текста, Enter, скрытие текста
     в уведомлениях, фон чата, счётчик трафика (KamiGramTweaks).
  4. СВОДКА В ОТЧЁТ: пункты попадают в MOD_MORE_FEATURES.txt.

Патч идемпотентный: ищет маркер KAMIGRAM_* и второй раз ничего не меняет.
"""

import io
import os
import sys

DONE = []
FAILED = []

TG_DIR = os.environ.get('TG_DIR', '.')
JAVA = os.path.join(TG_DIR, 'TMessagesProj/src/main/java/org/telegram')

TWEAKS = 'org.telegram.messenger.kamigram.KamiGramTweaks'
CFG = 'org.telegram.messenger.kamigram.KamiGramConfig'


def path(*parts):
    return os.path.join(JAVA, *parts)


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, src):
    io.open(p, 'w', encoding='utf-8').write(src)


def patch(file_name, marker, anchor, insert, category, what):
    """Вставить строки сразу после anchor (без маркера — идемпотентно)."""
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return
    if marker in src:
        return
    if anchor not in src:
        FAILED.append('%s: не найдено (%s)' % (file_name, what))
        return
    write(p, src.replace(anchor, anchor + insert, 1))
    DONE.append((category, what, file_name.split('/')[-1]))


def replace_once(file_name, marker, old, new, category, what):
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return
    if marker in src:
        return
    if old not in src:
        FAILED.append('%s: не найдено (%s)' % (file_name, what))
        return
    write(p, src.replace(old, new, 1))
    DONE.append((category, what, file_name.split('/')[-1]))


# =============================================================================
# 1. Отправка по Enter — значение по умолчанию из центра мода
# =============================================================================

def enter_to_send():
    replace_once('ui/Components/ChatActivityEnterView.java', 'KAMIGRAM_ENTER_SEND',
                 '        sendByEnter = preferences.getBoolean("send_by_enter", false);\n',
                 '        sendByEnter = preferences.getBoolean("send_by_enter", '
                 + CFG + '.enterToSend()); /* KAMIGRAM_ENTER_SEND */\n',
                 'Мессенджер', 'отправка сообщения по Enter (значение по умолчанию из центра мода)')


# =============================================================================
# 2. Нижние вкладки: плоский iOS-вид, без подписей в компактном режиме
# =============================================================================

def flat_tabs():
    patch('ui/Components/glass/GlassTabView.java', 'KAMIGRAM_FLAT_TABS',
          '        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);\n',
          '        /* KAMIGRAM_FLAT_TABS: компактный iOS-вид вкладок без «стекла» */\n'
          '        try {\n'
          '            if (' + CFG + '.compactChats()) {\n'
          '                textView.setVisibility(android.view.View.GONE);\n'
          '            }\n'
          '            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);\n'
          '            textView.setTypeface(org.telegram.messenger.AndroidUtilities.bold());\n'
          '        } catch (Throwable kamigramIgnore) {\n'
          '        }\n',
          'Дизайн', 'подписи вкладок скрываются в компактном режиме (плоский iOS-вид)')


# =============================================================================
# 3. Твики Telegram при старте приложения
# =============================================================================

def startup_tweaks():
    patch('ui/LaunchActivity.java', 'KAMIGRAM_TWEAKS',
          '        org.telegram.messenger.kamigram.KamiGramGhost.onAppStarted(currentAccount);\n',
          '        ' + TWEAKS + '.apply(); // KAMIGRAM_TWEAKS\n',
          'Система', 'при старте применяются размер текста, Enter, уведомления, фон и счётчик трафика')


def main():
    enter_to_send()
    flat_tabs()
    startup_tweaks()

    if FAILED:
        print('Sakura P90: проблемы:')
        for f in FAILED:
            print('  - ' + f)
        return 1

    lines = ['=== P90: второй большой пакет 2026 (%d пунктов) ===' % len(DONE)]
    for i, (cat, what, where) in enumerate(DONE, 1):
        lines.append('%3d. [%s] %s — %s' % (i, cat, what, where))
    lines.append('')
    lines.append('Подробности по всему моду — в MOD_FEATURES.txt / MOD_PRO_FEATURES.txt / MOD_MORE_FEATURES.txt')
    io.open(os.path.join(TG_DIR, 'MOD_P90_FEATURES.txt'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print('Sakura P90: применено %d пунктов' % len(DONE))
    return 0


if __name__ == '__main__':
    sys.exit(main())
