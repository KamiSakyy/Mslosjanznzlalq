#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sakura P95 (правки по замечаниям): галочка своим каналам, ID под @,
фильтр рекламы, режим «только текст», свой статус в профиле при призраке,
журнал удалённых сообщений, встроенные прокси в общий запуск.

Каждый патч ищет СВОЙ маркер и второй раз ничего не делает (идемпотентно).
Если якорь не найден, патч просто пропускается и попадает в отчёт — сборка
не падает (важно: одна неподходящая строка не должна ломать весь релиз).
"""

import io
import os
import re
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
    """УДАЛЁННЫЕ (переделано в r54): журнал текстов больше не ведётся.

    Раньше здесь текст удаляемых сообщений складывался в SharedPreferences —
    это не работало по-настоящему. Теперь удалённые сообщения просто ОСТАЮТСЯ
    В ЧАТЕ (патчи KAMIGRAM_KEEP_DELETED* из apply_r54_patches.py): локальная
    строка не стирается, а сообщение об удалении в интерфейс не приходит.
    """
    return


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
    """Keep built-in relay rows internal while showing only custom rows."""
    rel = 'ui/ProxyListActivity.java'
    target = path(*rel.split('/'))
    try:
        src = read(target)
    except Exception as exc:
        SKIPPED.append('%s: %s (hidden built-ins)' % (rel, exc))
        return False
    marker = 'KAMIGRAM_HIDE_BUILTIN'
    if marker in src:
        return True

    # Telegram has used this copy in more than one refresh/exception path.
    # Filter every direct copy, never SharedConfig.proxyList itself, so native
    # routing still sees the relay while the user-facing activity sees custom
    # rows only.
    pattern = re.compile(
        r'(?m)^(?P<indent>[ \t]*)proxyList\.clear\(\);\n'
        r'(?P=indent)proxyList\.addAll\(SharedConfig\.proxyList\);\n'
    )
    replacement = (
        '\g<indent>/* KAMIGRAM_HIDE_BUILTIN: internal Sakura relays stay out of the user list. */\n'
        '\g<indent>proxyList.clear();\n'
        '\g<indent>for (int kamigramIndex = 0; kamigramIndex < SharedConfig.proxyList.size(); kamigramIndex++) {\n'
        '\g<indent>    final SharedConfig.ProxyInfo kamigramInfo = SharedConfig.proxyList.get(kamigramIndex);\n'
        '\g<indent>    if (!org.telegram.messenger.kamigram.KamiGramBuiltinProxy.isBuiltIn(kamigramInfo)) {\n'
        '\g<indent>        proxyList.add(kamigramInfo);\n'
        '\g<indent>    }\n'
        '\g<indent>}\n'
    )
    src, count = pattern.subn(replacement, src)
    if count == 0:
        SKIPPED.append('%s: no proxy-list copy anchor (hidden built-ins)' % rel)
        return False
    write(target, src)
    DONE.append(('internal relays hidden from user proxy list (%d paths)' % count, rel))
    return True


# =============================================================================
# 9. Нативная отправка: ghost никогда не подменяет дату или send path
# =============================================================================

def ghost_silent_send():
    """Compatibility no-op: native Telegram sends are never rewritten."""
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


# =============================================================================
# 14. ЛИМИТ АККАУНТОВ: ИСПРАВЛЕНИЕ ФАТАЛЬНОГО ВЫЛЕТА НА СТАРТЕ
# =============================================================================
#
# ЧТО БЫЛО: в Java лимит аккаунтов подняли до 10, а нативная часть Telegram
# (jni/tgnet) осталась на своём #define MAX_ACCOUNT_COUNT 5. Её
# ConnectionsManager::getInstance() — это switch с ветками 0..4 и default.
# Java-класс ApplicationLoader.postInitApplication() создаёт ConnectionsManager
# для КАЖДОГО аккаунта 0..9, поэтому для аккаунтов 6..10 нативный код возвращал
# ТОТ ЖЕ объект instance4 и повторно вызывал его init(): в нём ещё раз делается
# pthread_create(&networkThread) на тот же объект, перезагружается чужой конфиг
# и пересоздаются дата-центры. Это гонка/повторная инициализация и падение
# приложения сразу после запуска (r45 вылета не давал: там лимит был 4 <= 5).
#
# ЧТО ДЕЛАЕМ: поднимаем нативный лимит РОВНО до того же значения, что и в Java,
# и добавляем недостающие ветки switch. Функционал не теряем: 10 аккаунтов
# остаются, нативный код на них теперь рассчитан (jniEnv[], TgNetWrapper, switch).

LAUNCH = 'ui/LaunchActivity.java'
CPP = 'TMessagesProj/jni/tgnet/ConnectionsManager.cpp'
CPP_DEFINES = 'TMessagesProj/jni/tgnet/Defines.h'


def native_accounts():
    """Синхронизация лимита аккаунтов между Java и нативным кодом.

    ЧТО БЫЛО (причина вылета прошлой сборки): в Java лимит подняли до 10, а
    нативная часть Telegram (jni/tgnet) осталась на своём MAX_ACCOUNT_COUNT 5, где
    ConnectionsManager::getInstance() — switch с ветками 0..4 и default. Java-код
    ApplicationLoader.postInitApplication() создаёт ConnectionsManager для КАЖДОГО
    аккаунта 0..9, поэтому для 6..10 нативный getInstance() возвращал ТОТ ЖЕ
    instance4 и повторно вызывал его init(): ещё один pthread_create на тот же
    объект, перезагрузка чужого конфига, пересоздание дата-центров — гонка и
    падение приложения сразу после запуска (r45 не падал: 4 <= 5).

    Теперь лимит задаётся в одном месте — здесь — и переносится в нативный код:
    и Defines.h, и ветки switch. 10 аккаунтов остаются, функционал не теряем.
    """
    account_count = 10
    user_config_path = 'messenger/UserConfig.java'
    try:
        user_config = read(path(*user_config_path.split('/')))
    except Exception as e:
        SKIPPED.append('%s: %s (лимит аккаунтов)' % (user_config_path, e))
        return False
    updated, count = re.subn(r'MAX_ACCOUNT_COUNT\s*=\s*\d+',
                             'MAX_ACCOUNT_COUNT = %d' % account_count, user_config, count=1)
    if count == 0:
        SKIPPED.append('%s: не найдена константа MAX_ACCOUNT_COUNT' % user_config_path)
        return False
    if updated != user_config:
        write(path(*user_config_path.split('/')), updated)
        DONE.append(('лимит аккаунтов в Java: %d' % account_count, 'UserConfig.java'))

    # --- Defines.h: лимит нативной части равен лимиту Java
    defines_path = os.path.join(TG_DIR, 'TMessagesProj/jni/tgnet/Defines.h')
    try:
        defines = read(defines_path)
    except Exception as e:
        SKIPPED.append('TMessagesProj/jni/tgnet/Defines.h: %s (нативный лимит)' % e)
        return False
    new_define = '#define MAX_ACCOUNT_COUNT %d' % account_count
    if 'KAMIGRAM_NATIVE_ACCOUNTS' not in defines:
        updated, count = re.subn(r'#define MAX_ACCOUNT_COUNT \d+', 'PLACEHOLDER_DEFINE', defines, count=1)
        if count == 0:
            SKIPPED.append('Defines.h: не найдено #define MAX_ACCOUNT_COUNT')
            return False
        updated = updated.replace(
            'PLACEHOLDER_DEFINE',
            '/* KAMIGRAM_NATIVE_ACCOUNTS */ ' + new_define, 1)
        write(defines_path, updated)
        DONE.append(('нативный лимит аккаунтов = %d (без этого приложение падало на старте)' % account_count,
                     'jni/tgnet/Defines.h'))

    # --- ConnectionsManager.cpp: своя ветка switch для каждого аккаунта
    cpp_rel = 'TMessagesProj/jni/tgnet/ConnectionsManager.cpp'
    try:
        cpp = read(os.path.join(TG_DIR, cpp_rel))
    except Exception as e:
        SKIPPED.append('%s: %s (ветки аккаунтов)' % (cpp_rel, e))
        return False
    if 'KAMIGRAM_NATIVE_ACCOUNTS' in cpp:
        return True
    old = ('        case 4:\n'
           '        default:\n'
           '            static ConnectionsManager instance4(4);\n'
           '            return instance4;\n')
    if old not in cpp:
        SKIPPED.append('%s: не найден switch аккаунтов' % cpp_rel)
        return False
    cases = ''.join(
        '        case %d:\n'
        '            static ConnectionsManager instance%d(%d);\n'
        '            return instance%d;\n' % (i, i, i, i)
        for i in range(4, account_count - 1))
    cases += ('        default:\n'
              '            static ConnectionsManager instance%d(%d); /* KAMIGRAM_NATIVE_ACCOUNTS */\n'
              '            return instance%d;\n' % (account_count - 1, account_count - 1, account_count - 1))
    write(os.path.join(TG_DIR, cpp_rel), cpp.replace(old, cases, 1))
    DONE.append(('нативные объекты сети созданы для всех аккаунтов (switch 0..%d)' % (account_count - 1),
                 'jni/tgnet/ConnectionsManager.cpp'))
    return True


# =============================================================================
# 15. ВСПОМОГАТЕЛЬНЫЕ МЕЛОЧИ СТАРТА (не должны мешать запуску)
# =============================================================================

def safe_start():
    """Старт приложения: аварийный режим, первый запуск, порядок вызовов.

    ВАЖНО: строки Telegram мы ЗАМЕНЯЕМ (replace_once), а не дописываем рядом —
    иначе вызов выполняется дважды (так и вышло в прошлой сборке: тема и
    проверка прокси применялись по два раза на каждом старте).

    KamiGramSelfCheck.beforeStart() выполняется САМЫМ ПЕРВЫМ в onCreate
    приложения и считает запуски: три неудачных подряд включают аварийный режим,
    в котором мод пропускает тяжёлые стартовые шаги (оформление, авто-прокси).
    Так приложение всегда открывается, и функцию можно выключить уже изнутри.
    """
    launch = 'ui/LaunchActivity.java'

    # 1) аварийный режим — в самом начале ApplicationLoader.onCreate
    patch('messenger/ApplicationLoader.java', 'KAMIGRAM_SELF_CHECK',
          '    public void onCreate() {\n',
          '        /* KAMIGRAM_SELF_CHECK: защита от вылетов на старте */\n'
          '        org.telegram.messenger.kamigram.KamiGramSelfCheck.beforeStart(this);\n',
          'защита от вылетов: счётчик запусков и аварийный режим')

    # 2) тяжёлые стартовые шаги — только когда аварийный режим выключен
    replace_once(launch, 'KAMIGRAM_SAFE_THEME',
                 '        org.telegram.messenger.kamigram.KamiGramTheme.apply();\n',
                 '        /* KAMIGRAM_SAFE_THEME: в аварийном режиме стартовые шаги пропускаются */\n'
                 '        if (!org.telegram.messenger.kamigram.KamiGramSelfCheck.safeMode()) {\n'
                 '            org.telegram.messenger.kamigram.KamiGramTheme.apply();\n'
                 '        }\n',
                 'оформление применяется один раз и не мешает в аварийном режиме')
    replace_once(launch, 'KAMIGRAM_SAFE_TICK',
                 '        org.telegram.messenger.kamigram.KamiGramProxyPower.tick(this); // KAMIGRAM_PROXY_TICK\n',
                 '        if (!org.telegram.messenger.kamigram.KamiGramSelfCheck.safeMode()) { /* KAMIGRAM_SAFE_TICK */\n'
                 '            org.telegram.messenger.kamigram.KamiGramProxyPower.tick(this); // KAMIGRAM_PROXY_TICK\n'
                 '        }\n',
                 'фоновая проверка прокси не мешает в аварийном режиме')
    replace_once(launch, 'KAMIGRAM_SAFE_PROXY',
                 '        org.telegram.messenger.kamigram.KamiGramBuiltinProxy.init(this); // KAMIGRAM_BUILTIN_PROXY\n',
                 '        if (!org.telegram.messenger.kamigram.KamiGramSelfCheck.safeMode()) { /* KAMIGRAM_SAFE_PROXY */\n'
                 '            org.telegram.messenger.kamigram.KamiGramBuiltinProxy.init(this); // KAMIGRAM_BUILTIN_PROXY\n'
                 '        }\n',
                 'встроенные прокси поднимаются, когда аварийный режим выключен')

    # 3) успешный запуск и разовые действия первого запуска
    patch(launch, 'KAMIGRAM_SELF_CHECK_OK',
          '        ApplicationLoader.postInitApplication();\n',
          '        org.telegram.messenger.kamigram.KamiGramSelfCheck.onLaunchStart(this); /* KAMIGRAM_SELF_CHECK_OK */\n'
          '        org.telegram.messenger.kamigram.KamiGramFirstRun.check(this); /* KAMIGRAM_FIRST_RUN_SAFE */\n',
          'первый запуск и отметка «приложение поднялось»')


def main():
    verified()
    id_under_username()
    ads_filter()
    text_only()
    deleted_log()
    # ПРИЗРАК (R50): поддельная строка «был в сети» УБРАНА — статус в профиле
    # теперь честный (серверный), а при обычной отправке приложение реально
    # выходит в сеть на секунду. Старый патч ghost_own_status() не применяется.
    # Старая «тихая отправка» через отложенное сообщение (+5 секунд) тоже
    # УБРАНА: именно она ломала отложенные и запланированные сообщения
    # («message id нету»). Роль тихой отправки теперь у честного онлайна.
    builtin_proxy_boot()
    hide_builtin_from_list()
    native_accounts()
    safe_start()
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
    print('Sakura P95: применено %d пунктов, пропущено %d' % (len(DONE), len(SKIPPED)))
    for s in SKIPPED[:8]:
        print('  · ' + s)
    return 0


if __name__ == '__main__':
    sys.exit(main())
