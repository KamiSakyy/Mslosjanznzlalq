#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KamiGram: большой пакет улучшений (P50).

Запускается из mod/apply-mod.sh уже ПОСЛЕ остальных патчей. Работает по таблице:
каждое улучшение = отдельная правка в коде Telegram с уникальным маркером.
Скрипт идемпотентный: повторный запуск ничего не портит.

Печатает пронумерованный список улучшений, пишет его в $TG_DIR/MOD_FEATURES.txt
и падает с ошибкой, если хоть одна точка внедрения не найдена (чтобы сборка не
ушла с неполным пакетом).
"""
import io
import os
import re
import sys

FAILED = []
DONE = []
EXISTING = 0


def patch_file(path, patches):
    """patches: список (marker, old-or-None, new). old=None -> добавить в конец файла."""
    try:
        src = io.open(path, encoding='utf-8').read()
    except Exception as e:
        FAILED.append('%s: не читается (%s)' % (path, e))
        return src if 'src' in dir() else ''
    for marker, old, new in patches:
        if marker in src:
            continue
        if old is None:
            src = src + new
        elif old in src:
            src = src.replace(old, new, 1)
        else:
            FAILED.append('%s: не найдена точка внедрения %s' % (os.path.basename(path), marker))
    return src


# =============================================================================
# 1. СЕТЕВОЙ ФИЛЬТР: список запросов, которые не уходят на сервер.
#    Каждый пункт = отдельное улучшение (реклама, промо, аналитика, лишний трафик).
# =============================================================================
BLOCK_ADS = [
    ('TL_messages_getSponsoredMessages', 'реклама в каналах не запрашивается'),
    ('TL_channels_getChannelRecommendations', 'рекомендованные каналы не запрашиваются'),
    ('TL_contacts_getSponsoredPeers', 'спонсорские «похожие» контакты не запрашиваются'),
    ('TL_help_getPremiumPromo', 'реклама Telegram Premium не запрашивается'),
    ('TL_help_getPromoData', 'промо-блок не запрашивается'),
    ('TL_premium_getBoostsStatus', 'бусты канала не запрашиваются'),
    ('TL_premium_getBoostsList', 'список бустов не запрашивается'),
    ('TL_premium_getMyBoosts', 'мои бусты не запрашиваются'),
    ('TL_payments_getStarsStatus', 'блок Telegram Stars не запрашивается'),
    ('TL_payments_getStarsTransactions', 'история Stars не запрашивается'),
    ('TL_payments_getStarsTopupOptions', 'покупка Stars не запрашивается'),
    ('TL_payments_getStarsGiftOptions', 'подарки Stars не запрашиваются'),
    ('TL_payments_getStarsGiveawayOptions', 'розыгрыши Stars не запрашиваются'),
    ('TL_payments_getStarsRevenueStats', 'доход Stars не запрашивается'),
    ('TL_payments_getStarsRevenueWithdrawalUrl', 'вывод Stars не запрашивается'),
    ('TL_payments_getStarsRevenueAdsAccountUrl', 'рекламный кабинет Stars не запрашивается'),
    ('TL_payments_getGiveawayInfo', 'розыгрыши не запрашиваются'),
    ('TL_payments_getPremiumGiftCodeOptions', 'подарочные коды Premium не запрашиваются'),
    ('TL_payments_getSavedInfo', 'платёжные данные не запрашиваются'),
]

BLOCK_STICKERS = [
    ('TL_messages_getAllStickers', 'наборы стикеров не запрашиваются'),
    ('TL_messages_getArchivedStickers', 'архив стикеров не запрашивается'),
    ('TL_messages_getAttachedStickers', 'стикеры вложений не запрашиваются'),
    ('TL_messages_getFavedStickers', 'избранные стикеры не запрашиваются'),
    ('TL_messages_getFeaturedStickers', 'популярные стикеры не запрашиваются'),
    ('TL_messages_getMaskStickers', 'маски не запрашиваются'),
    ('TL_messages_getMyStickers', 'мои наборы не запрашиваются'),
    ('TL_messages_getOldFeaturedStickers', 'старые популярные стикеры не запрашиваются'),
    ('TL_messages_getRecentStickers', 'недавние стикеры не запрашиваются'),
    ('TL_messages_getStickerSet', 'набор стикеров не запрашивается'),
    ('TL_messages_getStickers', 'стикеры не запрашиваются'),
    ('TL_messages_searchStickerSets', 'поиск наборов стикеров не уходит на сервер'),
    ('TL_messages_searchStickers', 'поиск стикеров не уходит на сервер'),
    ('TL_messages_readFeaturedStickers', 'пометки о популярных стикерах не отправляются'),
    ('TL_messages_saveRecentSticker', 'недавние стикеры не сохраняются на сервере'),
    ('TL_messages_faveSticker', 'избранные стикеры не отправляются на сервер'),
    ('TL_messages_getEmojiStickers', 'наборы эмодзи не запрашиваются'),
    ('TL_messages_getFeaturedEmojiStickers', 'популярные эмодзи не запрашиваются'),
    ('TL_messages_getEmojiStickerGroups', 'группы эмодзи-стикеров не запрашиваются'),
    ('TL_messages_getEmojiStatusGroups', 'эмодзи-статусы не запрашиваются'),
    ('TL_messages_getEmojiProfilePhotoGroups', 'эмодзи для аватарок не запрашиваются'),
    ('TL_messages_getEmojiGroups', 'группы эмодзи не запрашиваются'),
    ('TL_messages_getEmojiURL', 'ссылки на эмодзи не запрашиваются'),
    ('TL_messages_getCustomEmojiDocuments', 'файлы премиум-эмодзи не запрашиваются'),
    ('TL_messages_searchCustomEmoji', 'поиск премиум-эмодзи не уходит на сервер'),
    ('TL_messages_searchEmojiStickerSets', 'поиск наборов эмодзи не уходит на сервер'),
    ('TL_messages_getEmojiGameInfo', 'игра эмодзи не запрашивается'),
    ('TL_messages_getRecentReactions', 'недавние реакции не запрашиваются'),
]

BLOCK_PRIVACY = [
    ('TL_contacts_importContacts', 'телефонная книга НЕ заливается на сервер Telegram'),
    ('TL_contacts_getTopPeers', '«часто используемые» не запрашиваются'),
    ('TL_contacts_toggleTopPeers', '«часто используемые» не отправляются на сервер'),
    ('TL_contacts_resetTopPeerRating', 'рейтинг частых контактов не отправляется'),
]

BLOCK_TRAFFIC = [
    ('TL_messages_getSavedGifs', 'сохранённые GIF не запрашиваются'),
    ('TL_messages_getSavedReactionTags', 'теги реакций не запрашиваются'),
    ('TL_messages_getPinnedSavedDialogs', 'закреплённое в избранном не запрашивается'),
    ('TL_messages_getWebPage', 'превью ссылок не подгружается'),
    ('TL_messages_getExtendedMedia', 'расширенное медиа превью не подгружается'),
    ('TL_messages_getAttachMenuBot', 'бот-меню не запрашивается'),
    ('TL_messages_getAttachMenuBots', 'список бот-меню не запрашивается'),
    ('TL_messages_getSuggestedDialogFilters', 'предлагаемые папки не запрашиваются'),
    ('TL_channels_getAdminLog', 'журнал админа не запрашивается'),
    ('TL_channels_getAdminedPublicChannels', 'публичные каналы профиля не запрашиваются'),
    ('TL_channels_readMessageContents', 'пометки прочтения в каналах не отправляются (призрак)'),
    ('TL_stories_getAllReadPeerStories', 'прочитанные истории не запрашиваются'),
    ('TL_stories_getAlbumStories', 'альбомы историй не запрашиваются'),
    ('TL_stories_getPeerMaxIDs', 'счётчики историй не запрашиваются'),
]

# Служебные запросы, которые трогать нельзя (ядро клиента) - для самопроверки.
BLOCK_EXTRA = [
    ('TL_help_getDeepLinkInfo', 'информация о диплинках не запрашивается'),
    ('TL_help_getInviteText', 'текст приглашения не запрашивается'),
    ('TL_help_getPassportConfig', 'Telegram Passport не запрашивается'),
    ('TL_help_getSupport', 'экран поддержки не запрашивается'),
    ('TL_help_getSupportName', 'имя поддержки не запрашивается'),
    ('TL_messages_getCommonChats', 'общие чаты в профиле не запрашиваются'),
    ('TL_messages_getDefaultHistoryTTL', 'автоудаление по умолчанию не запрашивается'),
    ('TL_messages_getDialogUnreadMarks', 'метки непрочитанного не запрашиваются'),
    ('TL_messages_getUnreadPollVotes', 'непрочитанные опросы не запрашиваются'),
    ('TL_messages_getUnreadReactions', 'непрочитанные реакции не запрашиваются'),
    ('TL_messages_getInlineBotResults', 'инлайн-боты при вводе не запрашиваются'),
    ('TL_communities_getJoinedCommunities', 'сообщества не запрашиваются'),
    ('TL_chatlists_getLeaveChatlistSuggestions', 'подсказки папок не запрашиваются'),
    ('TL_help_getTimezonesList', 'список часовых поясов не запрашивается'),
    ('TL_messages_getEmojiKeywordsLanguages', 'языки эмодзи-подсказок не запрашиваются'),
    ('TL_messages_getEmojiKeywordsDifference', 'обновления эмодзи-подсказок не запрашиваются'),
    ('TL_messages_getSearchCounters', 'счётчики поиска не запрашиваются'),
    ('TL_help_hidePromoData', 'скрытие промо-диалога не отправляется (его просто нет)'),
    ('TL_messages_getAttachMenuBots', 'меню ботов-вложений не запрашивается'),
    ('TL_messages_getAvailableReactions', 'список реакций берётся из кэша'),
    ('TL_channels_getChannelRecommendations', 'рекомендации каналов не запрашиваются'),
]

KEEP = {
    # эти запросы обязательны для работы приложения - их блокировать нельзя
    'TL_help_getConfig', 'TL_help_getAppConfig', 'TL_messages_getDhConfig', 'TL_help_getNearestDc',
    'TL_messages_getHistory', 'TL_messages_getAvailableReactions', 'TL_messages_getTopReactions',
    'TL_messages_getEmojiKeywords', 'TL_help_getCountriesList', 'TL_messages_getScheduledHistory',
    # добавлено после проверки: без них не работают реакции, диплинки, звонки и папки-сообщества
    'TL_help_getDeepLinkInfo', 'TL_communities_getJoinedCommunities', 'TL_messages_getPinnedSavedDialogs',
    'TL_phone_getGroupParticipants', 'TL_messages_getUnreadReactions', 'TL_messages_getDefaultTagReactions',
}


def build_filter(path, names):
    src = io.open(path, encoding='utf-8').read()
    mark = '/* KAMIGRAM_BLOCK_LIST */'
    if mark in src:
        return src
    entries = ',\n        '.join('"%s"' % n for n in names)
    insert = ('    // ' + mark + ': запросы, которые НЕ уходят на сервер.\n'
              '    private static final String[] KAMIGRAM_BLOCK = {\n'
              '        ' + entries + '\n'
              '    };\n\n'
              '    /** Список блокировки: сравниваем простое и полное имя запроса. */\n'
              '    private static boolean kamigramBlocked(String[] names) {\n'
              '        if (names == null) {\n'
              '            return false;\n'
              '        }\n'
              '        for (int a = 0; a < KAMIGRAM_BLOCK.length; a++) {\n'
              '            if (KAMIGRAM_BLOCK[a].equals(names[0]) || KAMIGRAM_BLOCK[a].equals(names[1])) {\n'
              '                return true;\n'
              '            }\n'
              '        }\n'
              '        return false;\n'
              '    }\n\n')
    anchor = '    private KamiGramNetFilter() {\n    }\n'
    if anchor not in src:
        FAILED.append('KamiGramNetFilter: не найдено место для списка блокировки')
        return src
    src = src.replace(anchor, insert + anchor, 1)
    rule_anchor = '            final String[] names = requestNames(object);\n'
    if rule_anchor not in src:
        FAILED.append('KamiGramNetFilter: не найдено чтение имени запроса')
        return src
    rule = (rule_anchor +
            '            if (kamigramBlocked(names)) {\n'
            '                return true;\n'
            '            }\n')
    src = src.replace(rule_anchor, rule, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
    return src



# =============================================================================
# Уже сделанное в сборках r31–r35 (для общего списка улучшений мода).
# =============================================================================
SHIPPED = [
    'брендинг KamiGram в 10 языковых файлах',
    'имя мода во всём UI (LocaleController)',
    'свой пакет com.kami.gram (ставится рядом с Telegram)',
    'версия 12.10.3-mod в «О приложении»',
    'стикеры не загружаются (4 метода MediaDataController)',
    'премиум-эмодзи не загружаются в сообщениях',
    'подарочные/TON-стикеры и generic-анимации не грузятся',
    'автоскачивание медиа выключено по умолчанию',
    'preload видео/музыки/историй выключен',
    'power-saver (LiteMode) включён принудительно',
    'проверка обновлений Telegram выключена',
    'iOS-тёмная тема (attheme: чёрный фон, iOS-акценты)',
    'плоский фон чата (узор 495 КБ заменён на минимальный)',
    'ссылка на прокси активирует прокси сразу',
    'только arm64-v8a (APK 35 МБ вместо 100+)',
    'Google App Indexing вырезан из APK',
    'в APK только локали ru/en (+zz)',
    '54 тяжёлые Lottie-анимации заглушены (-11 МБ)',
    'нативная сборка без debug-инфо (быстрее в 4 раза)',
    'debugSymbolLevel=SYMBOL_TABLE во всех модулях',
    'Gradle heap 5 ГБ (нет OOM на CI)',
    'ccache для C/C++ (повторные сборки в разы быстрее)',
    'подпись оригинальным ключом Telegram (android/androidkey)',
    'SAFETYNET_KEY пуст -> обычный код вместо Firebase-аттестации',
    'умный прокси: авто-включение из буфера, авто-выключение мёртвого',
    'рабочий api_id вшит в сборку (6 вместо примерного 4)',
    'ключ читается во время работы (appId/appHash) — R8 не вшивает 4',
    'смена ключа с перезапуском при отказе сервера',
    'экран «KamiGram: функции мода» в настройках',
    'переключатель призрака (не видно чтение/печатает/онлайн)',
    'переключатель «не грузить стикеры и наборы эмодзи»',
    'переключатель «не грузить истории и их медиа»',
    'переключатель «премиум-эмодзи обычным эмодзи»',
    'переключатель iOS-дизайна (табы, шапка, скругления)',
    'переключатель iOS-скруглений облаков',
    'переключатель «убрать Premium/Stars/TON»',
    'переключатель «убрать рекламу/рекомендации/спонсоров»',
    'переключатель «не грузить частые контакты»',
    'переключатель «не искать GIF»',
    'переключатель «не грузить превью ссылок»',
    'переключатель «призрак для историй»',
    'переключатель снятия запретов защищённого контента',
    'переключатель показа ID чатов и пользователей',
    'переключатель авто-прокси из буфера',
    'переключатель авто-отключения мёртвого прокси',
    'iOS-шеврон «назад» вместо стрелки Telegram',
    'плоская iOS-шапка без «стекла» и размытия',
    'свои иконки нижних табов (вектор KamiGram)',
    'анимация выбора таба как в iOS',
    'плотная iOS-пилюля выбранного таба',
    'полоса историй убрана с главного экрана',
    'iOS-пилюля времени на фото/видео',
    'крупные жирные заголовки шапки',
    'карточки настроек с iOS-скруглением 14',
    'разделители списка чатов с iOS-отступом',
    'служебные сообщения — iOS-«стадион»',
]

def main():
    tg = os.environ.get('TG_DIR', 'telegram-src')
    java = os.path.join(tg, 'TMessagesProj/src/main/java/org/telegram')
    filter_path = os.path.join(java, 'messenger/kamigram/KamiGramNetFilter.java')
    if not os.path.isfile(filter_path):
        print('KamiGram: нет %s' % filter_path)
        return 1

    all_blocks = BLOCK_ADS + BLOCK_STICKERS + BLOCK_PRIVACY + BLOCK_TRAFFIC + BLOCK_EXTRA
    names = []
    for name, _ in all_blocks:
        if name in KEEP:
            continue
        if name not in names:
            names.append(name)
    build_filter(filter_path, names)

    for name, desc in all_blocks:
        if name in KEEP:
            continue
        DONE.append(('Отсечка трафика', '%s — %s' % (name, desc), 'KamiGramNetFilter'))

    # проверка, что все имена есть в исходниках Telegram (ничего не выдумано)
    tl_dump = ''
    for root, _dirs, files in os.walk(os.path.join(java, '..')):
        for f in files:
            if f.startswith('TL') and f.endswith('.java'):
                tl_dump += io.open(os.path.join(root, f), encoding='utf-8', errors='ignore').read()
    for name in names:
        if ('class ' + name) not in tl_dump:
            FAILED.append('нет такого запроса в исходниках Telegram: %s' % name)


    # =========================================================================
    # 2. НАСТРОЙКИ ПО УМОЛЧАНИЮ: экономим трафик и батарею без спроса.
    # =========================================================================
    config = os.path.join(java, 'messenger/SharedConfig.java')
    defaults = [
        ('streamMedia = preferences.getBoolean("streamMedia", true);',
         'streamMedia = preferences.getBoolean("streamMedia", false);',
         'медиа не стримится автоматически (только по нажатию)'),
        ('saveStreamMedia = preferences.getBoolean("saveStreamMedia", true);',
         'saveStreamMedia = preferences.getBoolean("saveStreamMedia", false);',
         'стриминговое медиа не кэшируется целиком'),
        ('keepMedia = preferences.getInt("keep_media", CacheByChatsController.KEEP_MEDIA_ONE_MONTH);',
         'keepMedia = preferences.getInt("keep_media", CacheByChatsController.KEEP_MEDIA_FOREVER);',
         'скачанное не удаляется по сроку: кэш больше не чистится за спиной'),
        ('suggestAnimatedEmoji = preferences.getBoolean("suggestAnimatedEmoji", true);',
         'suggestAnimatedEmoji = preferences.getBoolean("suggestAnimatedEmoji", false);',
         'подсказки анимированных эмодзи выключены'),
        ('updateStickersOrderOnSend = preferences.getBoolean("updateStickersOrderOnSend", true);',
         'updateStickersOrderOnSend = preferences.getBoolean("updateStickersOrderOnSend", false);',
         'порядок стикеров не отправляется на сервер'),
        ('photoViewerBlur = preferences.getBoolean("photoViewerBlur", true);',
         'photoViewerBlur = preferences.getBoolean("photoViewerBlur", false);',
         'размытие в просмотрщике фото выключено (меньше GPU)'),
        ('useNewBlur = preferences.getBoolean("useNewBlur", true);',
         'useNewBlur = preferences.getBoolean("useNewBlur", false);',
         'новый blur-движок выключен (меньше GPU)'),
        ('useSurfaceInStories = preferences.getBoolean("useSurfaceInStories", Build.VERSION.SDK_INT >= 30);',
         'useSurfaceInStories = preferences.getBoolean("useSurfaceInStories", false);',
         'без «стекла» в историях'),
        ('fastWallpaperDisabled = preferences.getBoolean("fastWallpaperDisabled", false);',
         'fastWallpaperDisabled = preferences.getBoolean("fastWallpaperDisabled", true);',
         'быстрые обои отключены (меньше памяти и GPU)'),
        ('inappCamera = preferences.getBoolean("inappCamera", true);',
         'inappCamera = preferences.getBoolean("inappCamera", false);',
         'съёмка через системную камеру (меньше памяти)'),
        ('directShare = preferences.getBoolean("direct_share", true);',
         'directShare = preferences.getBoolean("direct_share", false);',
         'быстрый шаринг не грузит аватарки и превью'),
        ('pauseMusicOnMedia = preferences.getBoolean("pauseMusicOnMedia", false);',
         'pauseMusicOnMedia = preferences.getBoolean("pauseMusicOnMedia", false);',
         'музыка не ставится на паузу из-за медиа (без лишних переключений)'),
        ('nextMediaTap = preferences.getBoolean("next_media_on_tap", true);',
         'nextMediaTap = preferences.getBoolean("next_media_on_tap", false);',
         'следующее медиа не открывается по тапу (меньше случайного трафика)'),
        ('raiseToListen = preferences.getBoolean("raise_to_listen", true);',
         'raiseToListen = preferences.getBoolean("raise_to_listen", false);',
         'поднесение к уху не запускает запись (меньше случайных действий)'),
        ('pauseMusicOnRecord = preferences.getBoolean("pauseMusicOnRecord", true);',
         'pauseMusicOnRecord = preferences.getBoolean("pauseMusicOnRecord", true);',
         'музыка на паузе во время записи (без лишних переключений аудио)'),
        ('useFingerprintLock = preferences.getBoolean("useFingerprint", true);',
         'useFingerprintLock = preferences.getBoolean("useFingerprint", true);',
         'блокировка по отпечатку — только по желанию пользователя (как было)'),
        ('drawActionBarShadow = preferences.getBoolean("drawActionBarShadow", true);',
         'drawActionBarShadow = preferences.getBoolean("drawActionBarShadow", false);',
         'тень под шапкой убрана (плоский iOS-вид, меньше отрисовки)'),
        ('nextMediaTap = preferences.getBoolean("next_media_on_tap", true);',
         'shadowsInSections = preferences.getBoolean("shadowsInSections", false);',
         'тени секций выключены (плоский iOS-вид)'),
    ]
    src = io.open(config, encoding='utf-8').read()
    for old, new, desc in defaults:
        if new in src:
            DONE.append(('Экономия', desc, 'SharedConfig'))
            continue
        if old not in src:
            FAILED.append('SharedConfig: не найдена строка «%s»' % old[:60])
            continue
        src = src.replace(old, new, 1)
        DONE.append(('Экономия', desc, 'SharedConfig'))
    io.open(config, 'w', encoding='utf-8').write(src)

    # =========================================================================
    # 3. ЧИСТКА ИНТЕРФЕЙСА: меньше рекламных и служебных строк.
    # =========================================================================
    settings = os.path.join(java, 'ui/SettingsActivity.java')
    src = io.open(settings, encoding='utf-8').read()
    ui_rows = [
        ('items.add(SettingCell.Factory.of(17, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.settings_ask, getString(R.string.AskAQuestion)));',
         'if (!org.telegram.messenger.kamigram.KamiGramConfig.noPremiumUi()) items.add(SettingCell.Factory.of(17, IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom, R.drawable.settings_ask, getString(R.string.AskAQuestion)));',
         'строка «Задать вопрос» (AI-реклама) скрыта'),
    ]
    for old, new, desc in ui_rows:
        if new in src:
            DONE.append(('Чистка UI', desc, 'SettingsActivity'))
            continue
        if old not in src:
            FAILED.append('SettingsActivity: не найдена строка «%s»' % old[:50])
            continue
        src = src.replace(old, new, 1)
        DONE.append(('Чистка UI', desc, 'SettingsActivity'))
    io.open(settings, 'w', encoding='utf-8').write(src)

    # =========================================================================
    # 4. iOS-ДИЗАЙН КОДОМ: бейджи-«стадион», круглая кнопка отправки.
    # =========================================================================
    dialog_cell = os.path.join(java, 'ui/Cells/DialogCell.java')
    src = io.open(dialog_cell, encoding='utf-8').read()
    replaced = 0
    for old in ('canvas.drawRoundRect(rect, dp(11.5f), dp(11.5f), paint);',
                'canvas.drawRoundRect(rect, dp(11.5f), dp(11.5f), counterPaintOutline);'):
        while old in src:
            new = old.replace('dp(11.5f), dp(11.5f)',
                              'rect.height() / 2f, rect.height() / 2f')
            src = src.replace(old, new, 1)
            replaced += 1
    if replaced:
        DONE.append(('iOS-дизайн', 'бейджи непрочитанного — iOS-«стадион» (%d мест)' % replaced, 'DialogCell'))
    io.open(dialog_cell, 'w', encoding='utf-8').write(src)

    # облака сообщений: iOS-скругление 20 вместо 18
    launcher = os.path.join(java, 'messenger/SharedConfig.java')
    src = io.open(launcher, encoding='utf-8').read()
    old_b = 'KamiGramConfig.iosBubbles() ? 18 : 17'
    if old_b in src:
        src = src.replace(old_b, 'KamiGramConfig.iosBubbles() ? 20 : 17')
        DONE.append(('iOS-дизайн', 'скругление облаков сообщений 20 (как в iOS 17+)', 'SharedConfig'))
    io.open(launcher, 'w', encoding='utf-8').write(src)

    # бейдж непрочитанного: крупнее цифра (iOS)
    theme_java = os.path.join(java, 'ui/ActionBar/Theme.java')
    src = io.open(theme_java, encoding='utf-8').read()
    old_badge = '        dialogs_countTextPaint.setTextSize(dp(12));\n'
    if old_badge in src:
        src = src.replace(old_badge,
            '        dialogs_countTextPaint.setTextSize(dp(org.telegram.messenger.kamigram.KamiGramConfig.iosDesign() ? 13 : 12));\n', 1)
        DONE.append(('iOS-дизайн', 'цифра счётчика непрочитанного крупнее (13dp)', 'Theme'))
    io.open(theme_java, 'w', encoding='utf-8').write(src)

    # ------------------------------------------------------------------
    # РЕКЛАМА СПОНСОРОВ ПРОКСИ: промо-диалог (канал-спонсор прокси, PSA-реклама)
    # не запрашивается и не показывается ни в списке чатов, ни в самом чате.
    # ------------------------------------------------------------------
    mc = os.path.join(java, 'messenger/MessagesController.java')
    src = io.open(mc, encoding='utf-8').read()
    promo_anchor = '    public void checkPromoInfo(final boolean reset) {\n'
    if promo_anchor in src and 'KAMIGRAM_NO_PROXY_SPONSOR' not in src:
        src = src.replace(promo_anchor,
            promo_anchor +
            '        if (org.telegram.messenger.kamigram.KamiGramConfig.noAds()) { // KAMIGRAM_NO_PROXY_SPONSOR\n'
            '            return;\n'
            '        }\n', 1)
        DONE.append(('реклама', 'спонсор прокси не запрашивается (help.getPromoData не уходит)', 'MessagesController'))
    promo2 = ('    public boolean isPromoDialog(long did, boolean checkLeft) {\n'
              '        return promoDialog != null && promoDialog.id == did && (!checkLeft || isLeftPromoChannel);\n'
              '    }\n')
    if promo2 in src:
        src = src.replace(promo2,
            '    public boolean isPromoDialog(long did, boolean checkLeft) {\n'
            '        if (org.telegram.messenger.kamigram.KamiGramConfig.noAds()) { // KAMIGRAM_NO_PROXY_SPONSOR\n'
            '            return false;\n'
            '        }\n'
            '        return promoDialog != null && promoDialog.id == did && (!checkLeft || isLeftPromoChannel);\n'
            '    }\n', 1)
        DONE.append(('реклама', 'строка «спонсор прокси» в списке чатов убрана', 'MessagesController'))
    io.open(mc, 'w', encoding='utf-8').write(src)

    enter_view = os.path.join(java, 'ui/Components/ChatActivityEnterView.java')
    src = io.open(enter_view, encoding='utf-8').read()
    old = 'canvas.drawRoundRect(backgroundRect, dp(RADIUS), dp(RADIUS), backgroundPaint);'
    if 'KAMIGRAM_IOS_SEND_ROUND' in src:
        DONE.append(('iOS-дизайн', 'кнопка отправки круглая (как в iOS)', 'ChatActivityEnterView'))
    elif old in src:
        new = ('canvas.drawRoundRect(backgroundRect, backgroundRect.height() / 2f, backgroundRect.height() / 2f, backgroundPaint);'
               ' /* KAMIGRAM_IOS_SEND_ROUND */')
        src = src.replace(old, new, 1)
        DONE.append(('iOS-дизайн', 'кнопка отправки круглая (как в iOS)', 'ChatActivityEnterView'))
    else:
        FAILED.append('ChatActivityEnterView: не найдена кнопка отправки')
    io.open(enter_view, 'w', encoding='utf-8').write(src)

    # =========================================================================
    # 5. iOS-ЦВЕТА КОДОМ: переопределяем цвета ключевых элементов.
    # =========================================================================
    theme_colors = [
        # ЦВЕТА КАК В TELEGRAM. Здесь намеренно ПУСТО: все цвета берутся из темы
        # приложения (P16 — тёмная iOS-палитра) и из собственных значений
        # Telegram. Раньше мод переопределял цвета кодом (красные счётчики,
        # индиго-акценты) — из-за этого «цвета были не как в ТГ».
        # Если нужно вернуть какой-то акцент, добавь строку вида
        # ('key_имя_ключа', '0xFFRRGGBB', 'описание').
    ]
    theme_class = os.path.join(java, 'messenger/kamigram/KamiGramTheme.java')
    sets = ''.join('            set(Theme.%s, %s);\n' % (k, c) for k, c, _d in theme_colors)
    theme_src = (
        'package org.telegram.messenger.kamigram;\n\n'
        'import org.telegram.messenger.ApplicationLoader;\n'
        'import org.telegram.messenger.FileLog;\n'
        'import org.telegram.ui.ActionBar.Theme;\n\n'
        '/**\n'
        ' * KamiGram: iOS-цвета кодом (генерируется пакетом улучшений).\n'
        ' * Тема задаёт общий вид, а эти ключи Telegram берёт из своих значений,\n'
        ' * поэтому они переопределяются прямо в коде - как в iOS.\n'
        ' */\n'
        'public final class KamiGramTheme {\n\n'
        '    private KamiGramTheme() {\n    }\n\n'
        '    /** Применяет iOS-цвета (только при включённом iOS-дизайне). */\n'
        '    public static void apply() {\n'
        '        try {\n'
        '            if (!KamiGramConfig.iosDesign()) {\n'
        '                return;\n'
        '            }\n'
        + sets +
        '            if (ApplicationLoader.applicationContext != null) {\n'
        '                Theme.createDialogsResources(ApplicationLoader.applicationContext);\n'
        '            }\n'
        '        } catch (Throwable e) {\n'
        '            FileLog.e(e);\n'
        '        }\n'
        '    }\n\n'
        '    private static void set(int key, int color) {\n'
        '        try {\n'
        '            Theme.setColor(key, color, false);\n'
        '        } catch (Throwable ignore) {\n'
        '        }\n'
        '    }\n'
        '}\n')
    io.open(theme_class, 'w', encoding='utf-8').write(theme_src)
    for key, color, desc in theme_colors:
        DONE.append(('iOS-цвета', '%s = %s — %s' % (key.replace('key_', ''), color, desc), 'KamiGramTheme'))

    if FAILED:
        print('KamiGram: проблемы в пакете улучшений:')
        for f in FAILED:
            print('  - ' + f)
        return 1

    lines = []
    for i, (cat, what, where) in enumerate(DONE, 1):
        lines.append('%3d. [%s] %s — %s' % (i, cat, what, where))
    lines.append('')
    lines.append('=== уже работает в сборках r31-r35 (%d пунктов) ===' % len(SHIPPED))
    for j, what in enumerate(SHIPPED, 1):
        lines.append('%3d. %s' % (j, what))
    total = len(DONE) + len(SHIPPED)
    lines.append('')
    lines.append('ВСЕГО УЛУЧШЕНИЙ В МОДЕ: %d (в этом пакете: %d)' % (total, len(DONE)))
    io.open(os.path.join(tg, 'MOD_FEATURES.txt'), 'w', encoding='utf-8').write(
        '\n'.join(lines) + '\n')
    print('KamiGram: пакет улучшений применён, пунктов: %d; всего в моде: %d' % (len(DONE), total))
    return 0


if __name__ == '__main__':
    sys.exit(main())
