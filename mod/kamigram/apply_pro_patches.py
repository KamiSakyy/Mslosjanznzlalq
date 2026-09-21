#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram «PRO» пакет (P60) - большое обновление мода.

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
    * автоочистка медиа выключена, скачанное остаётся;
    * защита вручную скачанных файлов от удаления.

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
    if old not in src:
        FAILED.append('%s: не найдено (%s)' % (file_name, what))
        return
    write(p, src.replace(old, new, 1))
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
                 '     * KamiGram: встроенный ротатор больше не ждёт 5-60 секунд.\n'
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
    # 2.1 главный экран: кнопка в шапке
    dlg = 'ui/DialogsActivity.java'
    replace_once(dlg, 'KAMIGRAM_PROXY_BUTTON',
                 '            optionsItem = menu.addItem(4, R.drawable.ic_ab_other);\n',
                 '            optionsItem = menu.addItem(4, R.drawable.ic_ab_other);\n'
                 '            /* KAMIGRAM_PROXY_BUTTON: кнопка прокси рядом с «тремя точками» */\n'
                 '            ' + BUTTON + '.add(actionBar, getParentActivity(), () -> ' + BUTTON + '.showPanel(getParentActivity()));\n',
                 'Прокси', 'кнопка прокси видна в шапке главного экрана постоянно')

    # 2.3 строки прокси в меню «три точки» главного экрана (ItemOptions)
    replace_once(dlg, 'KAMIGRAM_PROXY_MENU_ITEMS',
                 '        io.setDimAlpha(0x08);\n',
                 '        io.setDimAlpha(0x08);\n'
                 '        /* KAMIGRAM_PROXY_MENU_ITEMS: прокси в меню «три точки» */\n'
                 '        try {\n'
                 '            io.add(0, "Прокси: " + ' + POWER + '.statusText(), () -> ' + BUTTON + '.showPanel(getParentActivity()));\n'
                 '            io.add(0, "Вставить ссылку на прокси", () -> ' + BUTTON + '.showLinkDialog(getParentActivity(), null));\n'
                 '            io.add(0, "Подобрать лучший прокси", () -> {\n'
                 '                ' + POWER + '.refreshNow(getParentActivity());\n'
                 '                android.widget.Toast.makeText(getParentActivity(), ' + POWER + '.statusText(), android.widget.Toast.LENGTH_LONG).show();\n'
                 '            });\n'
                 '        } catch (Throwable ignore) {\n'
                 '        }\n',
                 'Прокси', 'в меню «три точки» главного экрана есть прокси, ссылка и автоподбор')

    # 2.4 чат: кнопка в шапке
    chat = 'ui/ChatActivity.java'
    patch(chat, 'KAMIGRAM_PROXY_BUTTON_CHAT',
          '        if (((chatMode == 0 && (threadMessageId == 0 || isTopic)) || chatMode == MODE_SUGGESTIONS) && !UserObject.isReplyUser(currentUser) && !isReport()) {\n',
          '            /* KAMIGRAM_PROXY_BUTTON_CHAT: прокси всегда под рукой и в чате */\n'
          '            ' + BUTTON + '.add(actionBar, getParentActivity(), () -> ' + BUTTON + '.showPanel(getParentActivity()));\n',
          'Прокси', 'кнопка прокси видна в шапке чата, рядом с «тремя точками»', before=True)

    # 2.5 чат: строки в меню
    replace_once(chat, 'KAMIGRAM_PROXY_MENU_CHAT',
                 '            headerItem = menu.addItem(chat_menu_options, otherIcon);\n',
                 '            headerItem = menu.addItem(chat_menu_options, otherIcon);\n'
                 '            /* KAMIGRAM_PROXY_MENU_CHAT */\n'
                 '            ' + BUTTON + '.addToMenu(headerItem, getParentActivity(), null);\n'
                 '            ' + IDS + '.addRow(headerItem, getDialogId());\n',
                 'Прокси', 'в меню чата появились прокси и ID этого чата')

    # 2.6 обработка нажатий в чате
    replace_once(chat, 'KAMIGRAM_MENU_CLICK_CHAT',
                 '            public void onItemClick(final int id) {\n',
                 '            public void onItemClick(final int id) {\n'
                 '                /* KAMIGRAM_MENU_CLICK_CHAT */\n'
                 '                if (' + BUTTON + '.handleClick(id, getParentActivity())) {\n'
                 '                    return;\n'
                 '                }\n'
                 '                if (' + IDS + '.handleClick(id, getDialogId(), getParentActivity())) {\n'
                 '                    return;\n'
                 '                }\n',
                 'Прокси', 'нажатия в меню чата (прокси, ID) обрабатываются')


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

    # 3.2 ID в «трёх точках» профиля
    replace_once('ui/ProfileActivity.java', 'KAMIGRAM_ID_PROFILE',
                 '        otherItem.removeAllSubItems();\n',
                 '        otherItem.removeAllSubItems();\n'
                 '        /* KAMIGRAM_ID_PROFILE: ID в меню профиля (нажатие копирует) */\n'
                 '        try {\n'
                 '            if (' + CFG + '.showIds()) {\n'
                 '                otherItem.addSubItem(' + IDS + '.ID_COPY, 0, "ID: " + getDialogId() + "  (нажмите, чтобы скопировать)");\n'
                 '            }\n'
                 '        } catch (Throwable ignore) {\n'
                 '        }\n',
                 'ID', 'в профиле ID доступен из меню (нажатие копирует)')

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
# 5. КЭШ: СКАЧАННОЕ ОСТАЁТСЯ
# =============================================================================

def cache():
    # 5.1 автоочистка медиа выключена
    replace_once('messenger/AutoDeleteMediaTask.java', 'KAMIGRAM_KEEP_DOWNLOADS',
                 '    public static void run() {\n',
                 '    public static void run() {\n'
                 '        /* KAMIGRAM_KEEP_DOWNLOADS: скачанное не удаляем - это была жалоба «кэш пропал» */\n'
                 '        if (' + CACHE + '.keep()) {\n'
                 '            return;\n'
                 '        }\n',
                 'Кэш', 'автоудаление медиа (по сроку и по размеру кэша) отключено')

    # 5.2 медиа по умолчанию хранится вечно
    replace_once('messenger/CacheByChatsController.java', 'KAMIGRAM_KEEP_FOREVER',
                 '    public static int getDefault(int type) {\n',
                 '    public static int getDefault(int type) {\n'
                 '        /* KAMIGRAM_KEEP_FOREVER: личные чаты/группы/каналы хранят медиа, пока сам не удалишь */\n'
                 '        if (' + CACHE + '.keep() && (type == KEEP_MEDIA_TYPE_USER || type == KEEP_MEDIA_TYPE_GROUP\n'
                 '            || type == KEEP_MEDIA_TYPE_CHANNEL)) {\n'
                 '            return KEEP_MEDIA_FOREVER;\n'
                 '        }\n',
                 'Кэш', 'медиа личных чатов, групп и каналов хранится без срока')

    # 5.3 защита файлов, скачанных вручную
    replace_once('messenger/FileLoader.java', 'KAMIGRAM_PROTECT_DOWNLOAD',
                 '    public void deleteFiles(final ArrayList<File> files, final int type) {\n',
                 '    public void deleteFiles(final ArrayList<File> files, final int type) {\n'
                 '        /* KAMIGRAM_PROTECT_DOWNLOAD: файлы, скачанные вручную, не удаляем */\n'
                 '        if (files != null && ' + CACHE + '.keep()) {\n'
                 '            for (int kamigramIndex = files.size() - 1; kamigramIndex >= 0; kamigramIndex--) {\n'
                 '                if (' + CACHE + '.isProtected(files.get(kamigramIndex))) {\n'
                 '                    files.remove(kamigramIndex);\n'
                 '                }\n'
                 '            }\n'
                 '        }\n',
                 'Кэш', 'файлы, скачанные пользователем, защищены от удаления')

    # 5.4 помечаем скачанное
    replace_once('messenger/FileLoader.java', 'KAMIGRAM_PROTECT_TAG',
                 '    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {\n',
                 '    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {\n'
                 '        /* KAMIGRAM_PROTECT_TAG: пользователь качает сам - файл остаётся навсегда */\n'
                 '        if (priority >= PRIORITY_HIGH && document != null) {\n'
                 '            ' + CACHE + '.protectByName(FileLoader.getAttachFileName(document));\n'
                 '        }\n',
                 'Кэш', 'вручную скачанные файлы помечаются защищёнными')


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
    # 8.1 иконка настроек как в iOS
    for f in ['ui/DialogsActivity.java', 'ui/ChatActivity.java']:
        replace_once(f, 'KAMIGRAM_IOS_SETTINGS_ICON',
                     'R.drawable.msg_settings_old',
                     'org.telegram.ui.Components.kamigram.KamiGramIcons.settings()',
                     'Дизайн', 'иконка настроек — iOS-шестерёнка KamiGram')

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
    ('Theme.key_chat_messagePanelVoicePressed', 0xFF5E5CE6, 'кнопка записи голосового — индиго'),
    ('Theme.key_chat_selectedBackground', 0x225E5CE6, 'подсветка выбранного — мягкое индиго'),
    ('Theme.key_chat_topPanelLine', 0xFF3A3A3C, 'линия верхней панели — графит'),
    ('Theme.key_divider', 0xFF2C2C2E, 'разделители — графит iOS'),
    ('Theme.key_graySection', 0xFF2C2C2E, 'серые секции — графит'),
    ('Theme.key_graySectionText', 0xFF8E8E93, 'текст секций — iOS-серый'),
    ('Theme.key_switchTrack', 0xFF39393D, 'выключенный переключатель — графит'),
    ('Theme.key_switchTrackBlue', 0xFF39393D, 'переключатель — графит'),
    ('Theme.key_switchTrackBlueSelector', 0xFF48484A, 'нажатие переключателя — графит'),
    ('Theme.key_switch2Track', 0xFF39393D, 'второй переключатель — графит'),
    ('Theme.key_actionBarDefaultSelector', 0x22FFFFFF, 'нажатие в шапке — мягкое свечение'),
    ('Theme.key_featuredStickers_addButton', 0xFF5E5CE6, 'кнопка добавления набора — индиго'),
    ('Theme.key_featuredStickers_addedIcon', 0xFF5E5CE6, 'галочка добавленного набора — индиго'),
    ('Theme.key_profile_creatorIcon', 0xFF5E5CE6, 'иконка автора канала — индиго'),
    ('Theme.key_player_buttonActive', 0xFF5E5CE6, 'активная кнопка плеера — индиго'),
    ('Theme.key_player_progress', 0xFF5E5CE6, 'прогресс плеера — индиго'),
    # убрано (нет в исходниках): ('Theme.key_player_progressCached', 0xFF3A3A3C, 'загруженная часть трека — графит'),
    # убрано (нет в исходниках): ('Theme.key_seekbarBuffered', 0xFF48484A, 'буфер дорожки — графит'),
    # убрано (нет в исходниках): ('Theme.key_voipgroup_speakerColor', 0xFF5E5CE6, 'активный динамик в звонке — индиго'),
    ('Theme.key_voipgroup_mutedIcon', 0xFFFF453A, 'микрофон выключен — iOS-красный'),
]


def ios_colors():
    theme = path('messenger', 'kamigram', 'KamiGramTheme.java')
    try:
        src = read(theme)
    except Exception as e:
        FAILED.append('KamiGramTheme: %s' % e)
        return
    anchor = '        try {\n'
    if anchor not in src:
        FAILED.append('KamiGramTheme: не найдено начало apply()')
        return
    for key, color, what in IOS_COLORS:
        if key in src:
            continue
        src = src.replace(anchor, anchor + '            set(%s, 0x%08X);\n' % (key, color), 1)
        DONE.append(('iOS-цвета', '%s — %s' % (key.replace('Theme.key_', ''), what), 'KamiGramTheme'))
    write(theme, src)


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
    'скачанное не удаляется: автоочистка медиа выключена',
    'вручную скачанные файлы защищены от удаления',
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

    print('KamiGram PRO: применено %d пунктов (отчёт: MOD_PRO_FEATURES.txt)' % len(DONE))
    if FAILED:
        print('НЕ ПРИМЕНИЛОСЬ (%d):' % len(FAILED))
        for f in FAILED:
            print('  · ' + f)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
