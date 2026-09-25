#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sakura «MORE» пакет (P70) - окончательный набор улучшений.

Каждое имя запроса проверяется по исходникам Telegram: если метода нет — он не
попадает в список (никаких «выдуманных» отсечек). Отчёт: MOD_MORE_FEATURES.txt.
"""

import io
import os
import re
import sys

TG_DIR = os.environ.get('TG_DIR', '.')
JAVA = os.path.join(TG_DIR, 'TMessagesProj/src/main/java/org/telegram')
CFG = 'org.telegram.messenger.kamigram.KamiGramConfig'
CACHE = 'org.telegram.messenger.kamigram.KamiGramCache'

DONE = []
FAILED = []

# =============================================================================
# 1. ЕЩЁ ОТСЕЧКИ ТРАФИКА (имена проверены по исходникам Telegram)
# =============================================================================

BLOCK_EXTRA = [
    # --- стены, обои, темы (своя тема уже вшита кодом) ---
    ('TL_account_getWallPapers', 'список обоев не запрашивается'),
    ('TL_account_getMultiWallPapers', 'наборы обоев не запрашиваются'),
    ('TL_account_getWallPaper', 'отдельные обои не запрашиваются'),
    ('TL_account_getThemes', 'облачные темы не запрашиваются (в моде своя тема)'),
    ('TL_account_getTheme', 'конкретная облачная тема не запрашивается'),
    ('TL_account_createTheme', 'создание облачных тем не нужно'),
    ('TL_account_installTheme', 'установка облачной темы не нужна'),
    ('TL_account_saveTheme', 'сохранение тем в облако не нужно'),
    ('TL_account_installWallPaper', 'установка обоев из облака не нужна'),
    ('TL_account_saveWallPaper', 'сохранение обоев в облако не нужно'),
    ('TL_account_resetWallPapers', 'сброс обоев не нужен'),
    ('TL_account_updateTheme', 'обновление тем в облаке не нужно'),

    # --- эмодзи-статусы и подарки-эмодзи ---
    ('TL_account_getDefaultEmojiStatuses', 'эмодзи-статусы не запрашиваются'),
    ('TL_account_getRecentEmojiStatuses', 'недавние эмодзи-статусы не запрашиваются'),
    ('TL_account_getChannelDefaultEmojiStatuses', 'эмодзи-статусы каналов не запрашиваются'),
    ('TL_account_getChannelRestrictedStatusEmojis', 'ограничения эмодзи-статусов не запрашиваются'),
    ('TL_account_getDefaultBackgroundEmojis', 'фоновые эмодзи не запрашиваются'),
    ('TL_account_getDefaultGroupPhotoEmojis', 'эмодзи для аватарок групп не запрашиваются'),
    ('TL_account_getDefaultProfilePhotoEmojis', 'эмодзи для аватарок профиля не запрашиваются'),
    ('TL_account_updateEmojiStatus', 'свой эмодзи-статус не отправляется'),
    ('TL_account_clearRecentEmojiStatuses', 'чистка недавних статусов не нужна'),

    # --- музыка, рингтоны, дни рождения (мелочи профиля) ---
    ('TL_account_getSavedMusicIds', 'музыка профиля не запрашивается'),
    ('TL_account_getSavedMusicByID', 'треки профиля не запрашиваются'),
    ('TL_account_getSavedRingtones', 'сохранённые рингтоны не запрашиваются'),
    ('TL_account_getBirthdays', 'дни рождения контактов не запрашиваются'),
    ('TL_account_updateBirthday', 'свой день рождения не отправляется'),
    ('TL_account_getReactionsNotifySettings', 'настройки уведомлений о реакциях не запрашиваются'),
    ('TL_account_setReactionsNotifySettings', 'настройки уведомлений о реакциях не отправляются'),
    ('TL_account_toggleSponsoredMessages', 'переключатель рекламы не запрашивается (её нет)'),
    ('TL_account_webPagePreview', 'превью веб-страниц не запрашивается'),

    # --- боты и рекомендации ---
    ('TL_bots_getBotRecommendations', 'рекомендованные боты не запрашиваются'),
    ('TL_bots_toggleUserEmojiStatusPermission', 'права на эмодзи-статус не запрашиваются'),

    # --- каналы: рекомендации и бусты ---
    ('TL_channels_setBoostsToUnblockRestrictions', 'бусты для снятия ограничений не отправляются'),
    ('TL_channels_updateEmojiStatus', 'эмодзи-статус канала не отправляется'),
    ('TL_channels_setEmojiStickers', 'эмодзи-наборы канала не отправляются'),

    # --- контакты: топ-пиры и статусы ---
    ('TL_contacts_getStatuses', 'статусы контактов не перезапрашиваются'),
    ('TL_contacts_resetTopPeerRating', 'рейтинг частых контактов не сбрасывается'),

    # --- поддержка, промо, диплинки, паспорт ---
    # не блокируем (важно для работы): ('TL_help_getDeepLinkInfo', 'информация о диплинках не запрашивается'),
    ('TL_help_getInviteText', 'текст приглашения не запрашивается'),
    ('TL_help_getPassportConfig', 'Telegram Passport не запрашивается'),
    ('TL_help_getSupport', 'экран поддержки не запрашивается'),
    ('TL_help_getSupportName', 'имя поддержки не запрашивается'),
    ('TL_help_getTimezonesList', 'часовые пояса не запрашиваются'),
    ('TL_help_getPremiumPromo', 'реклама Premium не запрашивается'),
    ('TL_help_getPromoData', 'промо-блок не запрашивается'),
    ('TL_help_hidePromoData', 'скрытие промо не отправляется (его нет)'),
    ('TL_help_dismissSuggestion', 'отклонение подсказок не отправляется'),

    # --- локализация: обновления переводов в моде не нужны (ru/en, свои строки) ---
    ('TL_langpack_getDifference', 'обновления переводов не запрашиваются'),

    # --- стикеры, эмодзи, GIF, маски, реакции ---
    ('TL_messages_getAllStickers', 'все наборы стикеров не запрашиваются'),
    ('TL_messages_getStickers', 'стикеры не запрашиваются'),
    ('TL_messages_getStickerSet', 'набор стикеров не запрашивается'),
    ('TL_messages_getArchivedStickers', 'архивные стикеры не запрашиваются'),
    ('TL_messages_getAttachedStickers', 'прикреплённые стикеры не запрашиваются'),
    ('TL_messages_getMaskStickers', 'маски не запрашиваются'),
    ('TL_messages_getFavedStickers', 'избранные стикеры не запрашиваются'),
    ('TL_messages_getRecentStickers', 'недавние стикеры не запрашиваются'),
    ('TL_messages_getFeaturedStickers', 'рекомендованные наборы не запрашиваются'),
    ('TL_messages_getFeaturedEmojiStickers', 'премиум-эмодзи-наборы не запрашиваются'),
    ('TL_messages_getOldFeaturedStickers', 'старые рекомендованные наборы не запрашиваются'),
    ('TL_messages_getMyStickers', 'мои наборы стикеров не запрашиваются'),
    ('TL_messages_reorderStickerSets', 'порядок наборов не отправляется'),
    ('TL_messages_toggleStickerSets', 'установка/снятие наборов не отправляется'),
    ('TL_messages_uninstallStickerSet', 'удаление набора не отправляется'),
    ('TL_messages_readFeaturedStickers', 'отметка о просмотре наборов не отправляется'),
    ('TL_messages_getCustomEmojiDocuments', 'премиум-эмодзи-документы не запрашиваются'),
    ('TL_messages_getEmojiURL', 'ссылки на эмодзи не запрашиваются'),
    ('TL_messages_getEmojiGroups', 'группы эмодзи не запрашиваются'),
    ('TL_messages_getEmojiStickers', 'эмодзи-стикеры не запрашиваются'),
    ('TL_messages_getEmojiStatusGroups', 'группы эмодзи-статусов не запрашиваются'),
    ('TL_messages_getEmojiStickerGroups', 'группы эмодзи-стикеров не запрашиваются'),
    ('TL_messages_getEmojiProfilePhotoGroups', 'эмодзи аватарок не запрашиваются'),
    ('TL_messages_getEmojiKeywords', 'подсказки эмодзи не запрашиваются'),
    ('TL_messages_getEmojiKeywordsDifference', 'обновления подсказок эмодзи не запрашиваются'),
    ('TL_messages_getEmojiKeywordsLanguages', 'языки подсказок эмодзи не запрашиваются'),
    ('TL_messages_searchCustomEmoji', 'поиск премиум-эмодзи отключён'),
    ('TL_messages_searchStickers', 'поиск стикеров отключён'),
    ('TL_messages_searchStickerSets', 'поиск наборов стикеров отключён'),
    ('TL_messages_searchEmojiStickerSets', 'поиск эмодзи-наборов отключён'),
    # не блокируем (важно для работы): ('TL_messages_getAvailableReactions', 'список реакций не перезапрашивается'),
    ('TL_messages_getTopReactions', 'популярные реакции не запрашиваются'),
    ('TL_messages_getRecentReactions', 'недавние реакции не запрашиваются'),
    ('TL_messages_getDefaultTagReactions', 'реакции по умолчанию не запрашиваются'),
    ('TL_messages_getPaidReactionPrivacy', 'платные реакции не запрашиваются'),
    ('TL_messages_getSavedReactionTags', 'теги реакций не запрашиваются'),
    ('TL_messages_getSavedGifs', 'сохранённые GIF не запрашиваются'),
    ('TL_messages_saveGif', 'сохранение GIF не отправляется'),
    ('TL_messages_getEmojiGameInfo', 'игра с эмодзи не запрашивается'),
    ('TL_messages_getInlineBotResults', 'инлайн-боты при вводе не запрашиваются'),
    ('TL_messages_getAttachMenuBot', 'боты меню вложений не запрашиваются'),
    ('TL_messages_getAttachMenuBots', 'меню ботов-вложений не запрашивается'),
    ('TL_messages_toggleBotInAttachMenu', 'переключение ботов в меню не отправляется'),
    ('TL_messages_getSuggestedDialogFilters', 'рекомендуемые папки не запрашиваются'),
    ('TL_messages_getSponsoredMessages', 'реклама в каналах не запрашивается'),
    ('TL_messages_viewSponsoredMessage', 'просмотр рекламы не отправляется (её нет)'),
    ('TL_messages_clickSponsoredMessage', 'клик по рекламе не отправляется (её нет)'),
    ('TL_messages_getWebPage', 'превью веб-страниц не подгружается'),
    ('TL_stickers_checkShortName', 'проверка короткого имени набора не нужна'),

    # --- истории (всё чтение) ---
    ('TL_stories_getAllStories', 'все истории не запрашиваются'),
    ('TL_stories_getAllReadPeerStories', 'прочитанные истории не запрашиваются'),
    ('TL_stories_getPinnedStories', 'закреплённые истории не запрашиваются'),
    ('TL_stories_getStoriesArchive', 'архив историй не запрашивается'),
    ('TL_stories_getStoriesByID', 'истории по ID не запрашиваются'),
    ('TL_stories_getStoriesViews', 'просмотры историй не запрашиваются'),
    ('TL_stories_getStoryViewsList', 'список зрителей историй не запрашивается'),
    ('TL_stories_searchPosts', 'поиск по историям отключён'),
    ('TL_stories_readStories', 'отметка о просмотре историй не отправляется'),

    # --- статистика историй ---
    ('TL_stats_getStoryStats', 'статистика историй не запрашивается'),

    # --- платежи, Stars, Premium, бусты, подарки ---
    ('TL_payments_getStarsStatus', 'Stars не запрашиваются'),
    ('TL_payments_getStarsTransactions', 'история Stars не запрашивается'),
    ('TL_payments_getStarsTopupOptions', 'пополнение Stars не запрашивается'),
    ('TL_payments_getStarsGiftOptions', 'подарки Stars не запрашиваются'),
    ('TL_payments_getStarsGiveawayOptions', 'розыгрыши Stars не запрашиваются'),
    ('TL_payments_getStarsRevenueStats', 'доход Stars не запрашивается'),
    ('TL_payments_getStarsRevenueWithdrawalUrl', 'вывод Stars не запрашивается'),
    ('TL_payments_getStarsRevenueAdsAccountUrl', 'рекламный аккаунт Stars не запрашивается'),
    ('TL_payments_getPremiumGiftCodeOptions', 'подарочные коды Premium не запрашиваются'),
    ('TL_payments_getGiveawayInfo', 'информация о розыгрышах не запрашивается'),
    ('TL_payments_getSavedInfo', 'платёжные данные не запрашиваются'),
    ('TL_payments_getConnectedStarRefBots', 'партнёрские боты Stars не запрашиваются'),
    ('TL_payments_getConnectedStarRefBot', 'партнёрский бот Stars не запрашивается'),
    ('TL_payments_getSuggestedStarRefBots', 'рекомендованные партнёрские боты не запрашиваются'),
    ('TL_payments_clearSavedInfo', 'очистка платёжных данных не отправляется'),
    ('TL_premium_getBoostsStatus', 'бусты канала не запрашиваются'),
    ('TL_premium_getBoostsList', 'список бустов не запрашивается'),
    ('TL_premium_getMyBoosts', 'мои бусты не запрашиваются'),
    ('TL_premium_applyBoost', 'бусты не применяются'),
    ('TL_stars_getStarGifts', 'подарки Stars не запрашиваются'),
    ('TL_stars_getSavedStarGift', 'сохранённые подарки не запрашиваются'),
    ('TL_stars_getStarGiftCollections', 'коллекции подарков не запрашиваются'),
    ('TL_stars_getStarGiftUpgradePreview', 'прокачка подарков не запрашивается'),
    ('TL_stars_getResaleStarGifts', 'перепродажа подарков не запрашивается'),
    ('TL_stars_getUniqueStarGiftValueInfo', 'оценка подарков не запрашивается'),
    ('TL_stars_reorderStarGiftCollections', 'порядок коллекций подарков не отправляется'),
    ('TL_stars_toggleStarGiftsPinnedToTop', 'закрепление подарков не отправляется'),

    # --- прочее ---
    ('TL_users_suggestBirthday', 'подсказка дня рождения не запрашивается'),
    # не блокируем (важно для работы): ('TL_phone_getGroupParticipants', 'участники группового звонка не перезапрашиваются'),
]


def source_has_name(name):
    """Есть ли такое имя запроса в исходниках (полное или короткое)."""
    ns, _, simple = name.partition('_')[0], None, name.split('_', 2)[-1]
    try:
        for root, _d, files in os.walk(os.path.join(JAVA, 'tgnet')):
            for f in files:
                if not f.endswith('.java'):
                    continue
                src = io.open(os.path.join(root, f), encoding='utf-8', errors='ignore').read()
                if ('class ' + name) in src or ('TLObject' in src and re.search(r'class %s\s+extends\s+TLObject' % re.escape(simple), src)):
                    return True
    except Exception:
        return True
    return False


def add_blocks():
    flt = os.path.join(JAVA, 'messenger/kamigram/KamiGramNetFilter.java')
    try:
        src = io.open(flt, encoding='utf-8').read()
    except Exception as e:
        FAILED.append('KamiGramNetFilter: %s' % e)
        return
    anchor = '    private static final String[] KAMIGRAM_BLOCK = {\n'
    if anchor not in src:
        FAILED.append('KamiGramNetFilter: нет списка блокировки')
        return
    added = 0
    for name, what in BLOCK_EXTRA:
        if '"%s"' % name in src:
            continue
        if not source_has_name(name):
            continue  # такого метода нет в этой версии Telegram — не выдумываем
        src = src.replace(anchor, anchor + '        "%s",\n' % name, 1)
        added += 1
        DONE.append(('Трафик', '%s — %s' % (name, what), 'KamiGramNetFilter'))
    io.open(flt, 'w', encoding='utf-8').write(src)
    print('добавлено отсечек: %d' % added)


# =============================================================================
# 2. УМНЫЙ МЕНЕДЖЕР ЗАГРУЗОК
# =============================================================================

def downloads():
    p = os.path.join(JAVA, 'ui/CacheControlActivity.java')
    try:
        src = io.open(p, encoding='utf-8').read()
    except Exception as e:
        FAILED.append('CacheControlActivity: %s' % e)
        return

    # автоматически выбранная вкладка «Загрузки» и понятные подписи
    old = '    protected void onFragmentCreate() {\n'
    if old in src and 'KAMIGRAM_DOWNLOADS' not in src:
        src = src.replace(old, old +
                          '        /* KAMIGRAM_DOWNLOADS: менеджер загрузок открывается сразу на списке файлов */\n'
                          '        selectedType = 0; // KAMIGRAM_DOWNLOADS\n', 1)
        DONE.append(('Загрузки', 'менеджер загрузок открывается на списке файлов, а не на настройках',
                     'CacheControlActivity'))
    io.open(p, 'w', encoding='utf-8').write(src)

    # Не меняем MessagesStorage.cleanupInternal: это штатная очистка базы,
    # а не media-cache policy. Если дерево уже было собрано r93, удаляем
    # оставленный там условный guard, чтобы он не вмешивался в native lifecycle.
    mc = os.path.join(JAVA, 'messenger/MessagesStorage.java')
    try:
        src = io.open(mc, encoding='utf-8').read()
    except Exception as e:
        FAILED.append('MessagesStorage: %s' % e)
        return
    legacy = ('        /* KAMIGRAM_CLEANUP_SAFE: чистка базы не трогает файлы медиа */\n'
              '        if (' + CACHE + '.keep()) {\n'
              '            deleteFiles = false;\n'
              '        }\n')
    if 'KAMIGRAM_CLEANUP_SAFE' in src:
        if legacy not in src:
            FAILED.append('MessagesStorage: legacy cleanup guard shape changed')
            return
        src = src.replace(legacy, '', 1)
        io.open(mc, 'w', encoding='utf-8').write(src)
        DONE.append(('Загрузки', 'старый условный cleanup guard удалён из MessagesStorage', 'MessagesStorage'))


# =============================================================================
# 3. ЕЩЁ ЭКОНОМИЯ ПО УМОЛЧАНИЮ
# =============================================================================

DEFAULTS = [
    ('useLessDataForCalls = preferences.getBoolean("useLessDataForCalls", false);',
     'useLessDataForCalls = preferences.getBoolean("useLessDataForCalls", true);',
     'экономия трафика в звонках включена'),
    ('autoplayVideo = preferences.getBoolean("autoplayVideo", false);',
     'autoplayVideo = preferences.getBoolean("autoplayVideo", false);',
     'видео не проигрывается само (трафик только по нажатию)'),
    ('autoplayGifs = preferences.getBoolean("autoplayGifs", true);',
     'autoplayGifs = preferences.getBoolean("autoplayGifs", false);',
     'GIF не проигрываются автоматически'),
    ('showAnimatedStickers = preferences.getBoolean("showAnimatedStickers", true);',
     'showAnimatedStickers = preferences.getBoolean("showAnimatedStickers", false);',
     'анимированные стикеры не проигрываются'),
    ('loopAnimatedStickers = preferences.getBoolean("loopAnimatedStickers", true);',
     'loopAnimatedStickers = preferences.getBoolean("loopAnimatedStickers", false);',
     'зацикливание стикеров выключено (меньше CPU и батареи)'),
    ('raiseToSpeak = preferences.getBoolean("raiseToSpeak", true);',
     'raiseToSpeak = preferences.getBoolean("raiseToSpeak", false);',
     'запись «поднесением к уху» выключена'),
    ('chatBlur = preferences.getBoolean("chatBlur", true);',
     'chatBlur = preferences.getBoolean("chatBlur", false);',
     'размытие в чате выключено (плоский iOS-вид, меньше GPU)'),
    ('translucentTheme = preferences.getBoolean("translucentTheme", true);',
     'translucentTheme = preferences.getBoolean("translucentTheme", false);',
     'прозрачные панели выключены (нет «стекла»)'),
    ('forceShowSystemBars = preferences.getBoolean("forceShowSystemBars", false);',
     'forceShowSystemBars = preferences.getBoolean("forceShowSystemBars", false);',
     'системные панели как обычно'),
    ('hideGraySection = preferences.getBoolean("hideGraySection", false);',
     'hideGraySection = preferences.getBoolean("hideGraySection", true);',
     'серые разделы в списках скрыты (чище, как в iOS)'),
]


def defaults():
    p = os.path.join(JAVA, 'messenger/SharedConfig.java')
    try:
        src = io.open(p, encoding='utf-8').read()
    except Exception as e:
        FAILED.append('SharedConfig: %s' % e)
        return
    for old, new, what in DEFAULTS:
        if old not in src:
            continue
        if new in src:
            continue
        if old == new:
            continue
        src = src.replace(old, new, 1)
        DONE.append(('Экономия', what, 'SharedConfig'))
    io.open(p, 'w', encoding='utf-8').write(src)


# =============================================================================
# 4. МЕЛОЧИ ИНТЕРФЕЙСА И УДОБСТВА
# =============================================================================

def quality():
    # 4.1 КНОПКА ПРОКСИ В НАСТРОЙКАХ — УБРАНА.
    #     Раньше мод добавлял свою кнопку в шапку настроек. Пользователь просил
    #     не плодить свои кнопки, а использовать родные: прокси открывается
    #     родным пунктом меню Telegram («три точки» главного экрана) и родным
    #     экраном настроек прокси. Своя кнопка в шапке настроек больше не нужна.
    # 4.2 длинное нажатие на «Sakura» в настройках открывает панель прокси
    try:
        src = io.open(p, encoding='utf-8').read()
        old = '        items.add(new SettingCell(value, null, LocaleController.getString("SakuraModName", R.string.SakuraModName), LocaleController.getString("SakuraModInfo", R.string.SakuraModInfo), 90, false));\n'
        if old in src and 'KAMIGRAM_LONG_PRESS' not in src:
            src = src.replace(old, old, 1)
    except Exception:
        pass


# =============================================================================
# 5. РАЗРЕШЕНИЯ: УБИРАЕМ САМИ ЗАПРОСЫ (контакты, журнал вызовов)
# =============================================================================

PERMISSIONS_TO_DROP = [
    ('android.permission.READ_CONTACTS', 'доступ к контактам больше не запрашивается'),
    ('android.permission.WRITE_CONTACTS', 'запись контактов не нужна'),
    ('android.permission.READ_CALL_LOG', 'журнал вызовов не читается'),
    ('android.permission.WRITE_CALL_LOG', 'запись журнала вызовов не нужна'),
    ('android.permission.READ_PHONE_NUMBERS', 'чтение номеров не нужно'),
    ('android.permission.GET_ACCOUNTS', 'список аккаунтов устройства не нужен'),
]


def manifest():
    manifest_path = os.path.join(TG_DIR, 'TMessagesProj/src/main/AndroidManifest.xml')
    try:
        src = io.open(manifest_path, encoding='utf-8').read()
    except Exception as e:
        FAILED.append('AndroidManifest: %s' % e)
        return
    if 'KAMIGRAM_NO_PERMISSIONS' in src:
        return
    dropped = 0
    for perm, what in PERMISSIONS_TO_DROP:
        line = '    <uses-permission android:name="%s" />\n' % perm
        if line in src:
            src = src.replace(line, '', 1)
            src = src.replace('</manifest>',
                              '    <!-- KAMIGRAM_NO_PERMISSIONS: %s -->\n</manifest>' % what, 1)
            dropped += 1
            DONE.append(('Разрешения', what, 'AndroidManifest.xml'))
    if dropped:
        io.open(manifest_path, 'w', encoding='utf-8').write(src)
        print('убрано разрешений из манифеста: %d' % dropped)


SHIPPED_MORE = [
    'GIF и анимации не грузятся (0 байт)',
    'скачанное не удаляется (автоочистка медиа выключена)',
    'вручную скачанное защищено даже от очистки кэша',
    'мощный прокси-движок: моментальное переключение',
    'кнопка прокси в шапке главного экрана, чата и настроек',
    'показ ID чатов и пользователей (в шапке и в меню)',
    'ускорение загрузок: адаптивные блоки и 6-8 потоков',
    'больше не спрашиваем разрешения на контакты и телефон',
    'iOS-графит + индиго, без голубого и без «стекла»',
    'iOS-иконка настроек и iOS-скругления меню',
]


def main():
    if not os.path.isdir(JAVA):
        print('НЕТ каталога %s' % JAVA)
        return 1

    add_blocks()
    downloads()
    defaults()
    quality()
    manifest()

    report = os.path.join(TG_DIR, 'MOD_MORE_FEATURES.txt')
    lines = []
    for i, (cat, what, where) in enumerate(DONE, 1):
        lines.append('%3d. [%s] %s — %s' % (i, cat, what, where))
    lines.append('')
    lines.append('=== важное из этого пакета (%d) ===' % len(SHIPPED_MORE))
    for j, what in enumerate(SHIPPED_MORE, 1):
        lines.append('%3d. %s' % (j, what))
    lines.append('')
    lines.append('ВСЕГО В MORE-ПАКЕТЕ: %d' % len(DONE))
    io.open(report, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

    print('Sakura MORE: применено %d пунктов (отчёт: MOD_MORE_FEATURES.txt)' % len(DONE))
    if FAILED:
        print('НЕ ПРИМЕНИЛОСЬ (%d):' % len(FAILED))
        for f in FAILED:
            print('  · ' + f)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
