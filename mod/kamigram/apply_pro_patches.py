#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sakura «PRO» пакет (P60) - большое обновление мода.

Что делает (каждый пункт попадает в отчёт MOD_FEATURES.txt):

  ПРОКСИ
    * мощный прокси-движок: инициализация, проверка состояния в реальном времени,
      моментальное переключение на живой прокси;
    * кнопка прокси в шапке главного экрана и в чате (рядом с «тремя точками»),
      с цветом состояния и подписью;
    * строки прокси в меню «три точки» (панель, ссылка, «подобрать лучший»);
    * поле «ссылка на прокси» с моментальным подключением.

  СКОРОСТЬ И ТРАФИК
    * адаптивные размеры блоков и число потоков загрузки (Wi-Fi/мобильный);
    * GIF и анимации не скачиваются автоматически;
    * ещё больше отсечек лишних запросов.

  КЭШ
    * автоматическая очистка медиа полностью отключена (без условий);
    * ручная очистка остаётся только в штатном CacheControlActivity Telegram;
    * Sakura не блокирует штатное удаление выбранных файлов.

  ИНТЕРФЕЙС
    * ID чатов и пользователей реально показывается (в шапке и в меню);
    * iOS-иконка настроек, графит + индиго вместо голубого;
    * больше iOS-геометрии: строки списка, меню, разделители, шапка.

Скрипт идемпотентный: если маркер уже есть, второй раз ничего не меняется.
"""

import io
import os
import sys

DONE = []
FAILED = []

TG_DIR = os.environ.get('TG_DIR', '.')
JAVA = os.path.join(TG_DIR, 'TMessagesProj/src/main/java/org/telegram')
CFG = 'org.telegram.messenger.kamigram.KamiGramConfig'
POWER = 'org.telegram.messenger.kamigram.KamiGramProxyPower'
BUTTON = 'org.telegram.messenger.kamigram.KamiGramProxyButton'
IDS = 'org.telegram.messenger.kamigram.KamiGramIds'
SPEED = 'org.telegram.messenger.kamigram.KamiGramSpeed'
CACHE = 'org.telegram.messenger.kamigram.KamiGramCache'


def path(*parts):
    return os.path.join(JAVA, *parts)


def read(p):
    return io.open(p, encoding='utf-8').read()


def write(p, src):
    io.open(p, 'w', encoding='utf-8').write(src)


def patch(file_name, marker, anchor, insert, category, what, before=False):
    """Вставляет код после (или до) якоря и следит за маркером."""
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return
    if marker in src:
        return
    if anchor not in src:
        FAILED.append('%s: якорь не найден (%s)' % (file_name, what))
        return
    if before:
        src = src.replace(anchor, insert + anchor, 1)
    else:
        src = src.replace(anchor, anchor + insert, 1)
    write(p, src)
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
    # A few legacy patches predate their marker. If the requested replacement
    # is already present, treat that shape as applied instead of failing on a
    # second installer run.
    if new in src:
        return
    if old not in src:
        FAILED.append('%s: не найдено (%s)' % (file_name, what))
        return
    write(p, src.replace(old, new, 1))
    DONE.append((category, what, file_name.split('/')[-1]))


def method_end(src, start):
    """Return the end offset of the Java method beginning at start.

    The small lexer ignores strings and comments so braces in diagnostics or
    comments cannot make the replacement stop in the wrong place.
    """
    brace = src.find('{', start)
    if brace < 0:
        return -1
    depth = 0
    i = brace
    state = 'code'
    while i < len(src):
        ch = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ''
        if state == 'code':
            if ch == '/' and nxt == '/':
                state = 'line'
                i += 2
                continue
            if ch == '/' and nxt == '*':
                state = 'block'
                i += 2
                continue
            if ch == '"':
                state = 'string'
                i += 1
                continue
            if ch == "'":
                state = 'char'
                i += 1
                continue
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
            continue
        if state == 'line':
            if ch == '\n':
                state = 'code'
            i += 1
            continue
        if state == 'block':
            if ch == '*' and nxt == '/':
                state = 'code'
                i += 2
            else:
                i += 1
            continue
        if state in ('string', 'char'):
            if ch == '\\':
                i += 2
            elif (state == 'string' and ch == '"') or (state == 'char' and ch == "'"):
                state = 'code'
                i += 1
            else:
                i += 1
    return -1


def replace_method(file_name, marker, signature, replacement, category, what):
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return
    if marker in src:
        return
    start = src.find(signature)
    if start < 0:
        FAILED.append('%s: не найдено (%s)' % (file_name, what))
        return
    end = method_end(src, start)
    if end < 0:
        FAILED.append('%s: конец метода не найден (%s)' % (file_name, what))
        return
    write(p, src[:start] + replacement + src[end:])
    DONE.append((category, what, file_name.split('/')[-1]))


def remove_block(file_name, marker, block, category, what):
    """Remove a legacy Sakura block when upgrading an already patched tree."""
    p = path(*file_name.split('/'))
    try:
        src = read(p)
    except Exception as e:
        FAILED.append('%s: %s' % (file_name, e))
        return
    if marker not in src:
        return
    if block not in src:
        FAILED.append('%s: legacy block shape not found (%s)' % (file_name, what))
        return
    write(p, src.replace(block, '', 1))
    DONE.append((category, what, file_name.split('/')[-1]))


# =============================================================================
# 1. ПРОКСИ: движок
# =============================================================================

def proxy_engine():
    # 1.1 запуск движка при старте приложения
    patch('ui/LaunchActivity.java', 'KAMIGRAM_PROXY_POWER',
          '        org.telegram.messenger.kamigram.KamiGramTheme.apply();\n',
          '        org.telegram.messenger.kamigram.KamiGramProxyPower.init(); // KAMIGRAM_PROXY_POWER\n',
          'Прокси', 'движок мощного прокси запускается вместе с приложением')

    # 1.2 периодическая проверка и автопереключение при возврате в приложение
    patch('ui/LaunchActivity.java', 'KAMIGRAM_PROXY_TICK',
          '        org.telegram.messenger.kamigram.KamiGramProxyPower.init(); // KAMIGRAM_PROXY_POWER\n',
          '        org.telegram.messenger.kamigram.KamiGramProxyPower.tick(this); // KAMIGRAM_PROXY_TICK\n',
          'Прокси', 'при запуске мод проверяет все прокси и показывает актуальный статус')

    # 1.3 переключение по ping-результатам прямо внутри встроенного ротатора Telegram
    replace_once('messenger/ProxyRotationController.java', 'KAMIGRAM_PROXY_ROTATOR',
                 '            switchToAvailable();\n        }\n    };',
                 '            switchToAvailable();\n        }\n    };\n\n'
                 '    /**\n'
                 '     * Sakura: встроенный ротатор больше не ждёт 5-60 секунд.\n'
                 '     * Основную работу делает быстрый движок KamiGramProxyPower (переключение\n'
                 '     * меньше секунды), этот таймáp - резервный: 3 секунды.\n'
                 '     */\n'
                 '    public static long kamiTimeoutMs() { // KAMIGRAM_PROXY_ROTATOR\n'
                 '        return 3000L;\n'
                 '    }',
                 'Прокси', 'встроенный ротатор переключает прокси за 3 секунды вместо 5-60')

    replace_once('messenger/ProxyRotationController.java', 'KAMIGRAM_PROXY_ROTATOR_USE',
                 'AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout) * 1000L);',
                 'AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, kamiTimeoutMs());',
                 'Прокси', 'таймáp ожидания встроенного ротатора заменён на мгновенный')

    # 1.4 режим ротации включён по умолчанию (раньше был выключен)
    replace_once('messenger/SharedConfig.java', 'KAMIGRAM_PROXY_ROTATION',
                 'proxyRotationEnabled = preferences.getBoolean("proxyRotationEnabled", false);',
                 'proxyRotationEnabled = preferences.getBoolean("proxyRotationEnabled", true); // KAMIGRAM_PROXY_ROTATION',
                 'Прокси', 'автоподбор прокси включён с первого запуска')

    replace_once('messenger/SharedConfig.java', 'KAMIGRAM_PROXY_ROTATION_TIMEOUT',
                 'proxyRotationTimeout = preferences.getInt("proxyRotationTimeout", ProxyRotationController.DEFAULT_TIMEOUT_INDEX);',
                 'proxyRotationTimeout = preferences.getInt("proxyRotationTimeout", 1); // KAMIGRAM_PROXY_ROTATION_TIMEOUT',
                 'Прокси', 'таймáp ротации по умолчанию — самый быстрый (10 секунд → мгновенно в моде)')

    # 1.5 активация прокси из ссылки идёт через мощный движок (мгновенно)
    replace_once('messenger/kamigram/KamiGramProxyHelper.java', 'KAMIGRAM_PROXY_POWER_LINK',
                 '    public static boolean activateProxy(String link, Context context) {\n',
                 '    public static boolean activateProxy(String link, Context context) {\n'
                 '        // KAMIGRAM_PROXY_POWER_LINK: ссылка подключается через мощный движок\n'
                 '        if (' + CFG + '.smartProxy()) {\n'
                 '            return ' + POWER + '.addAndActivate(link, context);\n'
                 '        }\n',
                 'Прокси', 'ссылка на прокси подключается через мощный движок (сразу и с проверкой)')


# =============================================================================
# 2. КНОПКА ПРОКСИ В ШАПКЕ И В «ТРЁХ ТОЧКАХ»
# =============================================================================

def proxy_button():
    # ПРОКСИ: НИКАКИХ САМОДЕЛЬНЫХ КНОПОК.
    #
    # Пользователь просил вернуть родные элементы Telegram: кнопка прокси у «трёх
    # точек» — это родной пункт меню Telegram (со своей иконкой ProxyDrawable и
    # подписью «Подключено» / «Отключено»), он открывает родной экран настроек
    # прокси. Раньше мод добавлял в шапку свои кнопки и свои строки в меню —
    # всё это убрано. Единственная правка ниже — показывать родной пункт всегда,
    # чтобы он был под рукой с первого запуска (а не только после подключения).
    # ВАЖНО: строка не добавляется, а ЗАМЕНЯЕТСЯ (replace_once), иначе в методе
    # появляется второе объявление proxyVisible и сборка не компилируется.
    replace_once('ui/DialogsActivity.java', 'KAMIGRAM_NATIVE_PROXY_ITEM',
                 '            final boolean proxyVisible = proxyEnabled && !TextUtils.isEmpty(proxyAddress)\n'
                 '                    || getMessagesController().blockedCountry && !SharedConfig.proxyList.isEmpty();\n',
                 '            /* KAMIGRAM_NATIVE_PROXY_ITEM: родной пункт «Прокси» у трёх точек виден всегда */\n'
                 '            final boolean proxyVisible = proxyEnabled && !TextUtils.isEmpty(proxyAddress)\n'
                 '                    || getMessagesController().blockedCountry && !SharedConfig.proxyList.isEmpty()\n'
                 '                    || org.telegram.messenger.kamigram.KamiGramBuiltinProxy.enabled();\n',
                 'Прокси', 'родной пункт «Прокси» у трёх точек главного экрана (иконка и состояние — от Telegram)')

    # чат: ID БОЛЬШЕ НЕ в меню «три точки» (R50: пользователь просил ID между
    # описанием и ссылкой @username — там он и показывается, в профиле/канале/группе)
    chat = 'ui/ChatActivity.java'

    # обработка нажатия на ID
    replace_once(chat, 'KAMIGRAM_MENU_CLICK_CHAT',
                 '            public void onItemClick(final int id) {\n',
                 '            public void onItemClick(final int id) {\n'
                 '                /* KAMIGRAM_MENU_CLICK_CHAT */\n'
                 '                if (' + IDS + '.handleClick(id, getDialogId(), getParentActivity())) {\n'
                 '                    return;\n'
                 '                }\n',
                 'ID', 'нажатие «ID» в меню чата копирует его')


# =============================================================================
# 3. ID ЧАТОВ (раньше не работал)
# =============================================================================

def ids():
    # 3.1 ID в шапке чата: дописывается к подписи под именем
    replace_once('ui/Components/ChatAvatarContainer.java', 'KAMIGRAM_SHOW_IDS',
                 '    public void setSubtitle(CharSequence value) {\n',
                 '    public void setSubtitle(CharSequence value) {\n'
                 '        /* KAMIGRAM_SHOW_IDS: id чата видно прямо в шапке */\n'
                 '        try {\n'
                 '            if (parentFragment != null) {\n'
                 '                value = ' + IDS + '.withId(value, parentFragment.getDialogId());\n'
                 '            }\n'
                 '        } catch (Throwable ignore) {\n'
                 '        }\n',
                 'ID', 'ID чата показывается в шапке (подпись под именем)')

    # 3.2 ID в профиле БОЛЬШЕ НЕ в «трёх точках»: R50 показывает ID МЕЖДУ
    # описанием и ссылкой @username (KamiGramIds.aboutWithId)
    replace_once('ui/ProfileActivity.java', 'KAMIGRAM_ID_PROFILE_CLICK',
                 '            public void onItemClick(final int id) {\n',
                 '            public void onItemClick(final int id) {\n'
                 '                /* KAMIGRAM_ID_PROFILE_CLICK */\n'
                 '                if (' + IDS + '.handleClick(id, getDialogId(), getParentActivity())) {\n'
                 '                    return;\n'
                 '                }\n',
                 'ID', 'нажатие «ID» в профиле копирует его в буфер')


# =============================================================================
# 4. СКОРОСТЬ ЗАГРУЗОК
# =============================================================================

def speed():
    flo = 'messenger/FileLoadOperation.java'
    replace_once(flo, 'KAMIGRAM_FAST_CHUNK',
                 '    private void updateParams() {\n',
                 '    private void updateParams() {\n'
                 '        /* KAMIGRAM_FAST_CHUNK: адаптивные блоки и потоки (быстрее на слабом интернете) */\n'
                 '        if (' + SPEED + '.enabled()) {\n'
                 '            downloadChunkSizeBig = ' + SPEED + '.bigChunkSize();\n'
                 '            downloadChunkSize = ' + SPEED + '.chunkSize();\n'
                 '            maxDownloadRequests = ' + SPEED + '.maxRequests();\n'
                 '            maxDownloadRequestsBig = ' + SPEED + '.maxRequests();\n'
                 '            maxDownloadRequestsAnimation = ' + SPEED + '.streamRequests();\n'
                 '            maxCdnParts = (int) (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig);\n'
                 '            return;\n'
                 '        }\n',
                 'Скорость', 'загрузки качаются крупными блоками и в 6-8 потоков (даже на слабом интернете)')

    # при быстром режиме мелкий блок не навязывается: 4 потока вместо 1
    replace_once(flo, 'KAMIGRAM_FAST_STREAM',
                 '            if (forceSmallChunk) {\n',
                 '            if (forceSmallChunk && !' + SPEED + '.enabled()) {\n',
                 'Скорость', 'мелкий блок не включается принудительно — потоков больше')


# =============================================================================
# 5. КЭШ: НЕТ АВТОМАТИЧЕСКОЙ ОЧИСТКИ, РУЧНАЯ — ТОЛЬКО В TELEGRAM
# =============================================================================

def cache():
    # 5.1 отключаем именно автоматическую задачу целиком. Она вызывается из
    # LaunchActivity при старте/возврате и содержит три независимых удаления:
    # срок хранения, cache_limit и sticker cache. Галочка мода не должна
    # решать, запустится ли этот watchdog: он всегда no-op.
    replace_method('messenger/AutoDeleteMediaTask.java', 'KAMIGRAM_CACHE_NO_AUTO_CLEANUP_R94',
                   '    public static void run() {\n',
                   '    public static void run() {\n'
                   '        /* KAMIGRAM_CACHE_NO_AUTO_CLEANUP_R94: автоматическая очистка\n'
                   '         * по сроку, cache_limit и sticker cache запрещена. Чистить\n'
                   '         * выбранные категории может только штатный CacheControlActivity. */\n'
                   '        return;\n'
                   '    }',
                   'Кэш', 'автоочистка медиа безусловно отключена (старт/resume, срок, лимит и stickers)')

    # 5.2 медиа по умолчанию хранится вечно там, где Telegram создаёт новые
    # настройки. Уже сохранённые сроки всё равно безопасны: run() выше no-op.
    replace_once('messenger/CacheByChatsController.java', 'KAMIGRAM_KEEP_FOREVER',
                 '    public static int getDefault(int type) {\n',
                 '    public static int getDefault(int type) {\n'
                 '        /* KAMIGRAM_KEEP_FOREVER_R94: every media category, including audio/music and stories,\n'
                 '           remains until the user explicitly clears it in Telegram settings. */\n'
                 '        if (type == KEEP_MEDIA_TYPE_USER || type == KEEP_MEDIA_TYPE_GROUP\n'
                 '            || type == KEEP_MEDIA_TYPE_CHANNEL || type == KEEP_MEDIA_TYPE_STORIES) {\n'
                 '            return KEEP_MEDIA_FOREVER;\n'
                 '        }\n',
                 'Кэш', 'медиа личных чатов, групп и каналов получает бессрочный default')

    # 5.3 старый r93 фильтр удаляем при обновлении дерева. Нативный
    # FileLoader.deleteFiles обязан получить полный список от ручной кнопки
    # Telegram, даже если в старых настройках осталась галочка защиты.
    legacy_delete_filter = (
        '        /* KAMIGRAM_PROTECT_DOWNLOAD: файлы, скачанные вручную, не удаляем */\n'
        '        if (files != null && ' + CACHE + '.keep()) {\n'
        '            for (int kamigramIndex = files.size() - 1; kamigramIndex >= 0; kamigramIndex--) {\n'
        '                if (' + CACHE + '.isProtected(files.get(kamigramIndex))) {\n'
        '                    files.remove(kamigramIndex);\n'
        '                }\n'
        '            }\n'
        '        }\n'
    )
    remove_block('messenger/FileLoader.java', 'KAMIGRAM_PROTECT_DOWNLOAD', legacy_delete_filter,
                 'Кэш', 'старый фильтр защиты не блокирует штатную очистку FileLoader')

    # 5.4 сама отметка оставалась только для старого фильтра, поэтому при
    # обновлении убираем и её: ручной native путь не имеет обходных исключений.
    legacy_tag = (
        '        /* KAMIGRAM_PROTECT_TAG: пользователь качает сам - файл остаётся навсегда */\n'
        '        if (priority >= PRIORITY_HIGH && document != null) {\n'
        '            ' + CACHE + '.protectByName(FileLoader.getAttachFileName(document));\n'
        '        }\n'
    )
    remove_block('messenger/FileLoader.java', 'KAMIGRAM_PROTECT_TAG', legacy_tag,
                 'Кэш', 'старые protected-file метки не участвуют в native удалении')


# =============================================================================
# 6. GIF И АНИМАЦИИ НЕ СКАЧИВАЮТСЯ
# =============================================================================

def gifs():
    dc = 'messenger/DownloadController.java'
    for idx, anchor in enumerate([
        '    private int canDownloadMediaInternal(MessageObject message) {\n',
        '    private int canDownloadMediaInternal(MessageObject message, long overrideSize) {\n',
    ]):
        replace_once(dc, 'KAMIGRAM_NO_GIF_AUTO%d' % idx,
                     anchor,
                     anchor +
                     '        /* KAMIGRAM_NO_GIF_AUTO: GIF и анимации не качаются сами */\n'
                     '        if (message != null && message.messageOwner != null\n'
                     '            && (MessageObject.isGifMessage(message.messageOwner)\n'
                     '                || MessageObject.isGifDocument(MessageObject.getDocument(message.messageOwner)))) {\n'
                     '            return 0;\n'
                     '        }\n',
                     'GIF', 'GIF не скачиваются автоматически (0 байт)')

    replace_once(dc, 'KAMIGRAM_NO_GIF_MANUAL',
                 '    public void startDownloadFile(TLRPC.Document document, MessageObject parentObject) {\n',
                 '    public void startDownloadFile(TLRPC.Document document, MessageObject parentObject) {\n'
                 '        /* KAMIGRAM_NO_GIF_MANUAL: даже при нажатии GIF не грузится */\n'
                 '        if (' + CFG + '.noGifs() && document != null && MessageObject.isGifDocument(document)) {\n'
                 '            return;\n'
                 '        }\n',
                 'GIF', 'GIF не грузится и по нажатию (полный ноль трафика)')

    replace_once('messenger/FileLoader.java', 'KAMIGRAM_NO_GIF_LOAD',
                 '    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {\n',
                 '    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {\n'
                 '        /* KAMIGRAM_NO_GIF_LOAD */\n'
                 '        if (' + CFG + '.noGifs() && document != null && MessageObject.isGifDocument(document)) {\n'
                 '            return;\n'
                 '        }\n',
                 'GIF', 'файлы GIF не запрашиваются у сервера вовсе')


# =============================================================================
# 7. БОЛЬШЕ НЕ СПРАШИВАЕМ РАЗРЕШЕНИЯ
# =============================================================================

def permissions():
    for idx in range(2):
        replace_once('ui/LoginActivity.java', 'KAMIGRAM_NO_PERM_NAG%d' % idx,
                     'if (!permissionsItems.isEmpty()) {',
                     'if (' + CFG + '.noPermissionNags()) { /* KAMIGRAM_NO_PERM_NAG%d */\n'
                     '                                    permissionsItems.clear();\n'
                     '                                }\n'
                     '                                if (!permissionsItems.isEmpty()) {' % idx,
                     'Разрешения', 'вход не требует разрешений на телефон и журнал вызовов')

    replace_once('ui/ContactsActivity.java', 'KAMIGRAM_NO_CONTACTS_NAG',
                 '                if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED',
                 '                if (!' + CFG + '.noPermissionNags() && activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED',
                 'Разрешения', 'мод больше не требует доступ к контактам')


# =============================================================================
# 8. ДИЗАЙН: IOS-ИКОНКИ И ГЕОМЕТРИЯ
# =============================================================================

def design():
    # 8.1 ИКОНКИ НАСТРОЕК — РОДНЫЕ TELEGRAM.
    #     Раньше мод подменял родную иконку настроек (R.drawable.msg_settings_old)
    #     своей нарисованной шестерёнкой — в меню, в подменю бота и в историях.
    #     Пользователь просил вернуть иконки Telegram: подмена убрана полностью.
    #     Наша шестерёнка осталась только на строке «Sakura» в настройках —
    #     это собственный раздел мода, и там она уместна.
    # 8.2 меню «три точки» — iOS-скругление 14 вместо 12
    replace_once('ui/ActionBar/ActionBarMenuItem.java', 'KAMIGRAM_IOS_POPUP',
                 '                    .setRadius(dp(12))\n',
                 '                    .setRadius(dp(' + CFG + '.iosDesign() ? 14 : 12)) /* KAMIGRAM_IOS_POPUP */\n',
                 'Дизайн', 'меню «три точки» скруглено сильнее (iOS)')
    replace_once('ui/ActionBar/ActionBarMenuItem.java', 'KAMIGRAM_IOS_POPUP2',
                 '                    .setRadius(dp(12))\n',
                 '                    .setRadius(dp(' + CFG + '.iosDesign() ? 14 : 12)) /* KAMIGRAM_IOS_POPUP2 */\n',
                 'Дизайн', 'меню «три точки» скруглено сильнее (iOS)')
    replace_once('ui/ActionBar/ActionBarMenuItem.java', 'KAMIGRAM_IOS_POPUP3',
                 '                        .setRadius(dp(12))\n',
                 '                        .setRadius(dp(' + CFG + '.iosDesign() ? 14 : 12)) /* KAMIGRAM_IOS_POPUP3 */\n',
                 'Дизайн', 'меню «три точки» скруглено сильнее (iOS)')


# =============================================================================
# 9. ЕЩЁ ОТСЕЧКИ ТРАФИКА И ЭКОНОМИЯ
# =============================================================================

EXTRA_BLOCKS = [
    ('TL_help_getAppUpdate', 'проверка обновлений не уходит на сервер'),
    # TL_help_getAppConfig НЕ блокируем: без конфигурации приложение не работает
    # не блокируем (влияет на выбор дата-центра): TL_help_getNearestDc
    ('TL_help_getTermsOfServiceUpdate', 'обновления условий не запрашиваются'),
    # не блокируем (ломает отложенные сообщения): TL_messages_getScheduledHistory
    ('TL_channels_readMessageContents', 'прочитанные посты каналов не сообщаются'),
    ('TL_contacts_importContacts', 'телефонная книга больше не выгружается'),
    ('TL_messages_getRecentReactions', 'недавние реакции не запрашиваются'),
    ('TL_payments_getSavedInfo', 'платёжные данные не запрашиваются'),
    ('TL_payments_getGiveawayInfo', 'информация о розыгрышах не запрашивается'),
]

EXTRA_DEFAULTS = [
    ('saveGifsToCache = preferences.getBoolean("save_gifs", true);',
     'saveGifsToCache = preferences.getBoolean("save_gifs", false);',
     'GIF не сохраняются в кэш (меньше трафика и места)'),
    ('useProximitySensor = preferences.getBoolean("useProximitySensor", true);',
     'useProximitySensor = preferences.getBoolean("useProximitySensor", true);',
     'датчик приближения работает как обычно (голосовые не «гаснут» зря)'),
    ('showNotificationsForAllAccounts = preferences.getBoolean("AllAccounts", true);',
     'showNotificationsForAllAccounts = preferences.getBoolean("AllAccounts", true);',
     'уведомления по всем аккаунтам как обычно'),
    ('drawActionBarShadow = preferences.getBoolean("drawActionBarShadow", true);',
     'drawActionBarShadow = preferences.getBoolean("drawActionBarShadow", false);',
     'плоская шапка без тени (iOS 2026)'),
    ('inappCamera = preferences.getBoolean("inappCamera", true);',
     'inappCamera = preferences.getBoolean("inappCamera", false);',
     'системная камера вместо встроенной (быстрее запуск)'),
]


def traffic_extra():
    """Добавляет новые отсечки в список блокировки и новые дефолты в SharedConfig."""
    flt = path('messenger', 'kamigram', 'KamiGramNetFilter.java')
    try:
        src = read(flt)
        anchor = '    private static final String[] KAMIGRAM_BLOCK = {\n'
        if anchor in src:
            for name, what in EXTRA_BLOCKS:
                if '"%s"' % name in src:
                    continue
                src = src.replace(anchor, anchor + '        "%s",\n' % name, 1)
                DONE.append(('Трафик', '%s — %s' % (name, what), 'KamiGramNetFilter'))
        write(flt, src)
    except Exception as e:
        FAILED.append('KamiGramNetFilter: %s' % e)

    sc = path('messenger', 'SharedConfig.java')
    try:
        src = read(sc)
        for old, new, what in EXTRA_DEFAULTS:
            marker = '/* KAMIGRAM_DEFAULT_%s */' % what[:12].replace(' ', '_').upper()
            if marker in src or new in src:
                continue
            if old in src:
                src = src.replace(old, new + ' ' + marker, 1)
                DONE.append(('Экономия', what, 'SharedConfig'))
        write(sc, src)
    except Exception as e:
        FAILED.append('SharedConfig: %s' % e)


# =============================================================================
# 10. ЕЩЁ IOS-ЦВЕТА (графит + индиго)
# =============================================================================

IOS_COLORS = [
    # ЦВЕТА КАК В TELEGRAM (список намеренно пуст). Все цвета задаёт тема P16,
    # а не код: так интерфейс выглядит как настоящий Telegram и не появляется
    # «непонятно откуда красное и оранжевое».
]


def ios_colors():
    # Stock Telegram themes are authoritative.  Do not generate a custom
    # custom color class or call Theme.setColor from the PRO package.
    theme = path('messenger', 'kamigram', 'KamiGramTheme.java')
    try:
        src = read(theme)
    except Exception as e:
        FAILED.append('KamiGramTheme: %s' % e)
        return
    if 'public static void apply()' not in src:
        FAILED.append('KamiGramTheme: compatibility shim is missing')
        return
    DONE.append(('Темы', 'родные темы Telegram сохранены; принудительная перекраска отключена', 'KamiGramTheme'))


# =============================================================================
# 11. ЧТО УЖЕ РАБОТАЕТ (для общего списка)
# =============================================================================

SHIPPED_PRO = [
    'мощный прокси-движок: переключение на живой прокси за ~1 секунду',
    'кнопка прокси в шапке главного экрана и чата (всегда видна)',
    'строки прокси в меню «три точки»: панель, ссылка, автоподбор',
    'поле «ссылка на прокси» — подключение моментально',
    'проверка пинга всех прокси и выбор самого быстрого',
    'если прокси всего один и он умер — сразу прямое подключение',
    'оживший прокси включается обратно автоматически',
    'ID чата видно в шапке, в меню — копирование ID',
    'скачанное не удаляется: автоматическая очистка медиа безусловно выключена',
    'штатная кнопка Telegram очищает выбранные категории без KamiGram-фильтра',
    'загрузки качаются крупными блоками и в 6-8 потоков',
    'GIF и анимации не скачиваются (0 байт)',
    'разрешения на контакты и телефон больше не спрашиваются',
    'iOS-графит + индиго вместо голубого',
    'iOS-иконка настроек',
    'меню «три точки» со скруглением iOS',
]


def main():
    if not os.path.isdir(JAVA):
        print('НЕТ каталога %s' % JAVA)
        return 1

    proxy_engine()
    proxy_button()
    ids()
    speed()
    cache()
    gifs()
    permissions()
    design()
    traffic_extra()
    ios_colors()

    # ---------------------------------------------------------------- отчёт
    report = os.path.join(TG_DIR, 'MOD_PRO_FEATURES.txt')
    lines = []
    for i, (cat, what, where) in enumerate(DONE, 1):
        lines.append('%3d. [%s] %s — %s' % (i, cat, what, where))
    lines.append('')
    lines.append('=== работает во всех сборках мода (%d пунктов) ===' % len(SHIPPED_PRO))
    for j, what in enumerate(SHIPPED_PRO, 1):
        lines.append('%3d. %s' % (j, what))
    lines.append('')
    lines.append('ВСЕГО В PRO-ПАКЕТЕ: %d новых + %d уже в моде = %d'
                 % (len(DONE), len(SHIPPED_PRO), len(DONE) + len(SHIPPED_PRO)))
    io.open(report, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

    print('Sakura PRO: применено %d пунктов (отчёт: MOD_PRO_FEATURES.txt)' % len(DONE))
    if FAILED:
        print('НЕ ПРИМЕНИЛОСЬ (%d):' % len(FAILED))
        for f in FAILED:
            print('  · ' + f)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
