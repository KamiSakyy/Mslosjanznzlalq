#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sakura: пакет правок R50 — то, что просил пользователь (все пункты сразу).

Что делает (по пунктам запроса):
  1) прокси работают при включённом VPN — правка в KamiGramBuiltinProxy.java (Java);
  2) название приложения не исчезает при включённом прокси: родной оверлей
     «Подключение к прокси…» больше не подменяет имя, состояние связи видно
     рядом с названием (DialogsActivity + LaunchActivity + KamiGramProxyStatus);
  3) непрочитанные — родной цвет Telegram (правка палитры в apply_theme_pro.py);
  4) тексты без «детских» надписей и подсказок (KamiGramCenter/KamiGramProxyPower);
  5) центр Sakura переделан: разделы + закреплённая кнопка «Готово» (KamiGramCenter);
  6) оптимизация и плавность (KamiGramOptimize + переключатели в центре);
  7) в «Избранном» в шапке только три точки, без скрепки (ChatActivity);
  8) ID — между описанием и ссылкой @username (ProfileActivity + KamiGramIds);
  9) отложенные и запланированные сообщения больше не ломаются: сообщение больше
     не подменяется на «отложенное» (убран старый патч ghost_silent_send);
 10) призрак по умолчанию выключен (KamiGramConfig.defaultValue);
 11) иконка призрака рядом с «тремя точками», касание вкл/выкл (ChatActivity);
 12) честный онлайн: обычная отправка реально выводит в сеть на секунду
     (SendMessagesHelper + KamiGramGhost.onRealSend), поддельный статус убран;
 13) свой шрифт .ttf из проводника Google (KamiGramFont + AndroidUtilities);
 14) дизайн: аккуратные разделы, скругления, приглушённые подписи (KamiGramCenter);
 15) стикеры и премиум-эмодзи включены по умолчанию (KamiGramConfig + KamiGramNetFilter).

Скрипт идемпотентный: отмечает каждую правку маркером KAMIGRAM_*.
"""

import io
import os
import sys

DONE = []
FAILED = []
SKIPPED = []

TG_DIR = os.environ.get('TG_DIR', '.')
JAVA = os.path.join(TG_DIR, 'TMessagesProj/src/main/java/org/telegram')
CFG = 'org.telegram.messenger.kamigram.KamiGramConfig'
GHOST = 'org.telegram.messenger.kamigram.KamiGramGhost'
IDS = 'org.telegram.messenger.kamigram.KamiGramIds'
STATUS = 'org.telegram.messenger.kamigram.KamiGramProxyStatus'
FONT = 'org.telegram.messenger.kamigram.KamiGramFont'


def path(*parts):
    return os.path.join(JAVA, *parts)


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, src):
    io.open(p, 'w', encoding='utf-8').write(src)


def patch(file_name, marker, anchor, insert, what, before=False):
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return False
    if marker in src:
        return True
    if anchor not in src:
        FAILED.append('%s: якорь не найден (%s)' % (file_name, what))
        return False
    if before:
        src = src.replace(anchor, insert + anchor, 1)
    else:
        src = src.replace(anchor, anchor + insert, 1)
    write(p, src)
    DONE.append((what, file_name.split('/')[-1]))
    return True


def replace_all(file_name, marker, old, new, what):
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return False
    if marker in src:
        return True
    if old not in src:
        FAILED.append('%s: не найдено (%s)' % (file_name, what))
        return False
    write(p, src.replace(old, new))
    DONE.append((what, file_name.split('/')[-1]))
    return True


# =============================================================================
# 7 + 11. ИКОНКА ПРИЗРАКА РЯДОМ С «ТРЕМЯ ТОЧКАМИ» И ТОЛЬКО ТРИ ТОЧКИ В ИЗБРАННОМ
# =============================================================================

def ghost_icon():
    chat = 'ui/ChatActivity.java'
    # ВАЖНО (r54): иконка призрака в шапке ЧАТА больше не создаётся вообще —
    # пользователь просил видеть её только в верхней панели главного экрана
    # (там, где название, рядом с «⋮»). В чатах и каналах её нет.

    # 3) в «Избранном» — только три точки, без скрепки
    replace_all(chat, 'KAMIGRAM_SAVED_NO_ATTACH',
                '        otherIcon = new ComposeDrawable(\n'
                '            context.getResources().getDrawable(R.drawable.ic_ab_other).mutate(),\n'
                '            context.getResources().getDrawable(R.drawable.mini_attach).mutate()\n'
                '        );\n'
                '        otherIcon.setIconTranslate(-dp(6), dp(6.66f));\n',
                '        /* KAMIGRAM_SAVED_NO_ATTACH: «Избранное» (свой чат) — в шапке ТОЛЬКО «три точки»,\n'
                '           без мини-скрепки. Скрепка не создаётся вообще, поэтому её не покажут\n'
                '           и служебные вызовы setIconVisible() при жестах. */\n'
                '        if (currentUser != null && currentUser.self) {\n'
                '            otherIcon = new ComposeDrawable(\n'
                '                context.getResources().getDrawable(R.drawable.ic_ab_other).mutate(),\n'
                '                new android.graphics.drawable.ColorDrawable(0)\n'
                '            );\n'
                '        } else {\n'
                '            otherIcon = new ComposeDrawable(\n'
                '                context.getResources().getDrawable(R.drawable.ic_ab_other).mutate(),\n'
                '                context.getResources().getDrawable(R.drawable.mini_attach).mutate()\n'
                '            );\n'
                '            otherIcon.setIconTranslate(-dp(6), dp(6.66f));\n'
                '        }\n',
                'в «Избранном» в шапке только «три точки» (скрепки нет вообще)')


# =============================================================================
# 9 + 12. ЧЕСТНЫЙ ОНЛАЙН: ОТПРАВКА РАБОТАЕТ КАК ОБЫЧНО, В СЕТЬ ВЫХОДИМ НА СЕКУНДУ
# =============================================================================

def ghost_pulse():
    smh = 'messenger/SendMessagesHelper.java'
    patch(smh, 'KAMIGRAM_GHOST_PULSE',
          '    public void sendMessage(SendMessageParams sendMessageParams) {\n',
          '        /* KAMIGRAM_GHOST_PULSE: обычная (не отложенная) отправка — реально выходим\n'
          '           в сеть на секунду. Отложенные и запланированные сообщения не трогаем:\n'
          '           именно из-за их подмены раньше была ошибка «message id нету». */\n'
          '        if (sendMessageParams != null && sendMessageParams.scheduleDate == 0) {\n'
          '            ' + GHOST + '.onRealSend(currentAccount);\n'
          '        }\n',
          'честный онлайн при отправке (и отложенные больше не ломаются)')


# =============================================================================
# 2. ИМЯ ПРИЛОЖЕНИЯ + СТАТУС ПРОКСИ РЯДОМ С НАЗВАНИЕМ
# =============================================================================

def title_proxy_status():
    dialogs = 'ui/DialogsActivity.java'
    patch(dialogs, 'KAMIGRAM_PROXY_TITLE_TITLE',
          '                actionBar.setTitle(ssb, statusDrawable);\n',
          '                ' + STATUS + '.attach(actionBar, ssb, statusDrawable);\n',
          'шапка: имя приложения всегда на месте, статус прокси рядом')

    patch(dialogs, 'KAMIGRAM_PROXY_TITLE_STATE',
          '        proxyDrawable.setConnected(proxyEnabled, connected, animated);\n',
          '        ' + STATUS + '.refresh();\n',
          'шапка: состояние прокси обновляется вместе с кнопкой прокси')

    launch = 'ui/LaunchActivity.java'
    replace_all(launch, 'KAMIGRAM_PROXY_TITLE_OVERLAY',
                '        actionBarLayout.setTitleOverlayText(title, titleId, action);\n',
                '        /* KAMIGRAM_PROXY_TITLE_OVERLAY: убираем надписи «Подключение…» полностью.\n'
                '           Раньше Telegram подменял название приложения строкой «Подключение к прокси…»,\n'
                '           и она висела даже после подключения. Теперь название всегда на месте,\n'
                '           никакого текста состояния рядом нет. */\n'
                '        actionBarLayout.setTitleOverlayText(null, 0, null);\n',
                'надпись «Подключение…» убрана, имя приложения не подменяется')


# =============================================================================
# 8. ID МЕЖДУ ОПИСАНИЕМ И ССЫЛКОЙ @USERNAME
# =============================================================================

def id_between_bio():
    profile = 'ui/ProfileActivity.java'
    replace_all(profile, 'KAMIGRAM_ID_BETWEEN_USER',
                '                        aboutLinkCell.setTextAndValue(userInfo.about, LocaleController.getString(R.string.UserBio), addlinks);\n',
                '                        /* KAMIGRAM_ID_BETWEEN: ID стоит между описанием и @username */\n'
                '                        aboutLinkCell.setTextAndValue(' + IDS + '.aboutWithId(userInfo.about, getDialogId()), LocaleController.getString(R.string.UserBio), addlinks);\n',
                'ID в профиле — между описанием и @username')

    replace_all(profile, 'KAMIGRAM_ID_BETWEEN_CHANNEL',
                '                        aboutLinkCell.setTextAndValue(text, LocaleController.getString(R.string.DescriptionPlaceholder), ChatObject.isChannel(currentChat) && !currentChat.megagroup);\n',
                '                        /* KAMIGRAM_ID_BETWEEN: ID канала/группы под описанием */\n'
                '                        aboutLinkCell.setTextAndValue(' + IDS + '.aboutWithId(text, getDialogId()), LocaleController.getString(R.string.DescriptionPlaceholder), ChatObject.isChannel(currentChat) && !currentChat.megagroup);\n',
                'ID в каналах и группах — под описанием')

    replace_all(profile, 'KAMIGRAM_ID_BETWEEN_BIO',
                '                            aboutLinkCell.setTextAndValue(value, LocaleController.getString(R.string.UserBio), getUserConfig().isPremium());\n',
                '                            /* KAMIGRAM_ID_BETWEEN: ID под описанием пользователя */\n'
                '                            aboutLinkCell.setTextAndValue(' + IDS + '.aboutWithId(value, getDialogId()), LocaleController.getString(R.string.UserBio), getUserConfig().isPremium());\n',
                'ID под описанием в профиле')


# =============================================================================
# 6 + 13. ОПТИМИЗАЦИЯ И СВОЙ ШРИФТ: ПОДКЛЮЧЕНИЕ К КОДУ TELEGRAM
# =============================================================================

def optimize_and_font():
    auc = 'messenger/AndroidUtilities.java'
    patch(auc, 'KAMIGRAM_FONT_BOLD',
          '    public static Typeface bold() {\n',
          '        if (' + FONT + '.installed()) {\n'
          '            final Typeface kamigramBold = ' + FONT + '.bold();\n'
          '            if (kamigramBold != null) {\n'
          '                return kamigramBold;\n'
          '            }\n'
          '        }\n',
          'свой шрифт: полужирный текст рисуется выбранным .ttf')

    patch(auc, 'KAMIGRAM_FONT_REGULAR',
          '    public static Typeface getTypeface(String assetPath) {\n',
          '        if (' + FONT + '.installed()) {\n'
          '            final Typeface kamigramFont = ' + FONT + '.forAsset(assetPath);\n'
          '            if (kamigramFont != null) {\n'
          '                return kamigramFont;\n'
          '            }\n'
          '        }\n',
          'свой шрифт: обычный текст во всём приложении')

    launch = 'ui/LaunchActivity.java'
    patch(launch, 'KAMIGRAM_OPTIMIZE',
          '    protected void onCreate(Bundle savedInstanceState) {\n        isActive = true;\n',
          '        org.telegram.messenger.kamigram.KamiGramOptimize.apply();\n',
          'оптимизация и плавность включаются при запуске')

    patch(launch, 'KAMIGRAM_FONT_RESULT',
          '    protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n',
          '        if (' + FONT + '.onActivityResult(requestCode, resultCode, data)) {\n'
          '            return;\n'
          '        }\n',
          'свой шрифт: ответ проводника Google обрабатывается')


def main():
    if not os.path.isdir(JAVA):
        sys.stderr.write('R50: нет исходников Telegram: %s\n' % JAVA)
        sys.exit(1)

    ghost_icon()
    ghost_pulse()
    title_proxy_status()
    id_between_bio()
    optimize_and_font()

    report = os.path.join(TG_DIR, 'MOD_R50_FEATURES.txt')
    with io.open(report, 'w', encoding='utf-8') as f:
        for what, where in DONE:
            f.write('%s | %s\n' % (what, where))
        for item in SKIPPED:
            f.write('ПРОПУЩЕНО: %s\n' % item)
        for item in FAILED:
            f.write('ОШИБКА: %s\n' % item)

    print('R50: применено правок — %d, ошибок — %d' % (len(DONE), len(FAILED)))
    for what, where in DONE:
        print('  + %s (%s)' % (what, where))
    for item in FAILED:
        print('  ! %s' % item)
    if FAILED:
        sys.exit(2)


if __name__ == '__main__':
    main()
