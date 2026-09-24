#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram: правильная тёмная тема «как iOS Telegram / MDGram» (P80).

Главная идея: НЕ выдумывать палитру, а взять родную тёмную тему Telegram
(night.attheme, там все 509 ключей: каждый текст и каждая поверхность уже
согласованы) и поменять только то, что делает тему «iOS»:

  * поверхности и разделители — iOS-значения (#000000, #1C1C1E, #2C2C2E, #38383A);
  * акцент — iOS-синий #0A84FF (в рантайме его можно сменить в экране мода);
  * переключатели — iOS-зелёный #34C759, запись голосового — iOS-красный #FF453A;
  * облака: входящие #262628, исходящие #2B5278 (это родные цвета iOS-темы Telegram);
  * никаких градиентов и «стекла».

Тем же тёмным набором переписываются все тёмные темы, а также тема дня «Blue»:
так при любой настройке системы приложение выглядит одинаково тёмно-iOS и
текст нигде не может стать чёрным на чёрном.
"""

import io
import os
import re
import sys

# ---------------------------------------------------------------------------
# Палитра Yoru (взята из кода приложения Yoru: Ui.BG/CARD/SURFACE/PURPLE/TEXT/MUTED/LINE)
#   BG #0D0B12 · SURFACE #15111C · CARD #1C1724 · PURPLE #C8A7FF
#   TEXT #F7F0FF · MUTED #A99BB8 · LINE #352A43 · EMERALD #88E0A0 · AMBER #FFCF70
# ---------------------------------------------------------------------------
BLACK = '0D0B12'        # фон приложения (Yoru BG)
CARD = '1C1724'         # карточки и строки (Yoru CARD)
CARD2 = '15111C'        # вложенные поверхности (Yoru SURFACE)
LINE = '352A43'         # разделители (Yoru LINE)
GRAY_TEXT = 'A99BB8'    # приглушённый текст (Yoru MUTED)
WHITE = 'F7F0FF'        # основной текст (Yoru TEXT)
ACCENT = 'C8A7FF'       # акцент (Yoru PURPLE)
ACCENT_SOFT = '33C8A7FF'
GREEN = '88E0A0'        # Yoru EMERALD
AMBER = 'FFCF70'        # Yoru AMBER
RED = 'FF8F9F'          # мягкий красный в тон палитре (удаление/запись)
BLUE_TEXT = 'C8A7FF'    # время и галочки в исходящих — акцент Yoru

PALETTE = {
    # ---------------- поверхности ----------------
    'windowBackgroundGray': BLACK,          # фон экранов (Yoru BG)
    'windowBackgroundWhite': CARD,          # строки и карточки (Yoru CARD)
    'actionBarDefault': BLACK,              # шапка — фон Yoru (BG), без чёрной полосы
    'actionBarDefaultSelector': '22FFFFFF',
    'actionBarDefaultSubmenuBackground': CARD,
    'actionBarDefaultSubmenuSeparator': LINE,
    'dialogBackground': CARD,
    'dialogBackgroundGray': CARD2,
    'graySection': CARD,
    'divider': LINE,
    'dialogGrayLine': LINE,
    'dialogShadowLine': '00000000',
    'dialogLineProgressBackground': LINE,
    'chat_wallpaper': BLACK,
    'chat_topPanelBackground': CARD,
    'chat_topPanelLine': LINE,
    'chat_messagePanelBackground': CARD,
    'chat_emojiPanelBackground': CARD,
    'chat_emojiPanelShadowLine': CARD2,
    'chat_stickersHintPanel': CARD,
    'chats_menuBackground': CARD,
    'chats_menuTopBackgroundCats': CARD,
    'chats_menuTopShadow': '00000000',
    'chats_archivePinBackground': CARD,
    'undo_background': CARD2,
    'inappPlayerBackground': CARD,
    'player_background': CARD,
    'sharedMedia_linkPlaceholder': CARD,
    'chat_attachButtonBackground': CARD2,
    'chat_attachButtonBackgroundPressed': '2A2138',
    'profile_actionBackground': '00000000',
    'profile_actionPressedBackground': '00000000',

    # ---------------- текст (всё светлое — ничего не может пропасть) ----------------
    'actionBarDefaultTitle': WHITE,
    'actionBarDefaultIcon': WHITE,
    'actionBarDefaultSubtitle': GRAY_TEXT,
    'actionBarDefaultSearchPlaceholder': GRAY_TEXT,
    # ПАПКИ (вкладки «Все», «Личные», «Непрочитанные»…): текст БЕЛЫЙ.
    # Раньше активная папка рисовалась тёмным текстом, а «пилюля» под ней —
    # тёмная (CARD2), поэтому названия папок сливались с фоном и их не было видно.
    'actionBarTabUnactiveText': 'CCFFFFFF',
    # «пилюля» выбранной папки («Все», «Личные»…): раньше бралась из родной
    # тёмной темы — из-за этого папки выглядели чёрными. Теперь — наш акцент,
    # как у разделов в центре мода (светлая «пилюля» + тёмный текст)
    'actionBarTabLine': ACCENT,
    'actionBarTabActiveText': 'FFFFFF',
    # A folder row is a KamiGram surface, never a black/transparent native tab.
    'actionBarTabSelector': CARD,
    # непрочитанные у папок: спокойный наш цвет вместо красного.
    # r68: у НЕвыбранной папки счётчик теперь такой же, как у выбранной — раньше он
    # был тёмно-фиолетовым (3A2E50) на тёмной панели, и число не было видно вообще.
    'chats_tabUnreadActiveBackground': ACCENT,
    'chats_tabUnreadUnactiveBackground': ACCENT,
    'chats_tabUnreadActiveText': '21152F',
    'chats_tabUnreadUnactiveText': '21152F',
    'chats_name': WHITE,
    'chats_message': GRAY_TEXT,
    'chats_date': GRAY_TEXT,
    'chats_nameMessage': GRAY_TEXT,
    'chats_unreadCounterText': WHITE,
    'windowBackgroundWhiteBlackText': WHITE,
    'windowBackgroundWhiteGrayText': GRAY_TEXT,
    'windowBackgroundWhiteGrayText2': GRAY_TEXT,
    'windowBackgroundWhiteGrayText3': GRAY_TEXT,
    'windowBackgroundWhiteGrayText4': GRAY_TEXT,
    'windowBackgroundWhiteGrayText5': GRAY_TEXT,
    'windowBackgroundWhiteGrayText6': GRAY_TEXT,
    'windowBackgroundWhiteGrayText8': GRAY_TEXT,
    'windowBackgroundWhiteHintText': GRAY_TEXT,
    'windowBackgroundWhiteGrayIcon': GRAY_TEXT,
    'windowBackgroundWhiteBlueHeader': GRAY_TEXT,
    'windowBackgroundWhiteGrayText7': GRAY_TEXT,
    'dialogTextBlack': WHITE,
    'dialogTextGray': GRAY_TEXT,
    'dialogTextGray2': GRAY_TEXT,
    'dialogTextGray3': GRAY_TEXT,
    'dialogTextGray4': GRAY_TEXT,
    'dialogTextHint': GRAY_TEXT,
    'dialogIcon': GRAY_TEXT,
    'profile_title': GRAY_TEXT,
    'profile_status': GRAY_TEXT,
    'avatar_subtitleInProfileBlue': GRAY_TEXT,
    'emptyListPlaceholder': GRAY_TEXT,
    'fastScrollInactive': LINE,
    'chat_messageTextIn': WHITE,
    'chat_messageTextOut': WHITE,
    'chat_status': GRAY_TEXT,
    'chat_inTimeText': GRAY_TEXT,
    'chat_outTimeText': BLUE_TEXT,
    'chat_outTimeSelectedText': WHITE,
    'chat_inTimeSelectedText': GRAY_TEXT,
    'chat_inSentClock': GRAY_TEXT,
    'chat_outSentClock': BLUE_TEXT,
    'chat_outSentClockSelected': WHITE,
    'chat_outSentCheck': BLUE_TEXT,
    'chat_outSentCheckSelected': WHITE,
    'chats_sentClock': GRAY_TEXT,
    'chat_serviceText': WHITE,
    'chat_serviceBackground': 'CC1C1724',
    'chat_serviceBackgroundSelected': 'CC2A2138',
    'chat_selectedBackground': '22C8A7FF',
    'chat_messagePanelText': WHITE,
    'chat_messagePanelHint': GRAY_TEXT,
    'chat_messagePanelIcons': GRAY_TEXT,
    'chat_messagePanelSend': ACCENT,
    'chat_recordTime': RED,
    'chat_recordedVoiceDot': RED,
    'chat_recordVoiceCancel': RED,
    'chat_recordVoiceCancelSelected': RED,
    'chat_goDownButton': CARD2,
    'chat_goDownButtonIcon': WHITE,
    'text_RedBold': RED,
    'text_RedRegular': RED,
    'windowBackgroundWhiteGreenText': GREEN,
    'windowBackgroundWhiteGreenText2': GREEN,
    'calls_callReceivedGreenIcon': GREEN,

    # ---------------- облака и связи ----------------
    'chat_inBubble': '1C1724',
    'chat_inBubbleSelected': '241D30',
    'chat_outBubble': '2A2138',
    'chat_outBubbleSelected': '332745',
    'chat_outBubbleGradient': '2A2138',
    'chat_outBubbleGradientSelectedOverlay': '33C8A7FF',
    'chat_inBubbleShadow': '00000000',
    'chat_outBubbleShadow': '00000000',
    'chat_messageLinkIn': ACCENT,
    'chat_messageLinkOut': 'A8D4FF',

    # ---------------- акценты iOS-синие ----------------
    'windowBackgroundWhiteValueText': ACCENT,
    'windowBackgroundWhiteLinkText': ACCENT,
    'windowBackgroundWhiteLinkSelection': ACCENT_SOFT,
    'dialogTextLink': ACCENT,
    'dialogTextBlue': ACCENT,
    'dialogTextBlue2': ACCENT,
    'dialogTextBlue4': ACCENT,
    'dialogButton': ACCENT,
    'dialogButtonSelector': ACCENT_SOFT,
    'profile_actionIcon': ACCENT,
    'profile_creatorIcon': ACCENT,
    'windowBackgroundWhiteBlueText': ACCENT,
    'windowBackgroundWhiteBlueText2': ACCENT,
    'windowBackgroundWhiteBlueText3': ACCENT,
    'windowBackgroundWhiteBlueText4': ACCENT,
    'windowBackgroundWhiteBlueText5': ACCENT,
    'windowBackgroundWhiteBlueText7': ACCENT,
    'contextProgressOuter1': ACCENT,
    'contextProgressInner1': '3A3A3C',
    'chat_fieldOverlayText': ACCENT,
    'chat_goDownButtonCounter': ACCENT,
    'chat_goDownButtonCounterBackground': CARD2,
    'chats_sentCheck': ACCENT,
    'fastScrollActive': ACCENT,
    'progressCircle': ACCENT,
    'player_progress': ACCENT,
    'player_buttonActive': ACCENT,
    'switchTrackChecked': ACCENT,         # переключатели — фиолетовый Yoru
    'switchTrackBlue': '39393D',
    'switchTrackBlueSelector': '48484A',
    'switch2Track': '39393D',
    'checkbox': ACCENT,
    'checkboxDisabled': '3A3A3C',
    'chat_inLoader': ACCENT,
    'chat_outLoader': ACCENT,
    'chat_inLoaderSelected': ACCENT,
    'chat_outLoaderSelected': ACCENT,
    'chat_attachIcon': GRAY_TEXT,
    'chat_attachIconPressed': WHITE,
    'chats_actionBackground': CARD,
    'chats_actionIcon': WHITE,
    'chats_actionPressedBackground': CARD2,
    # Цвета счётчика непрочитанного НЕ задаём: остаются родные цвета Telegram
    # (в тёмной теме это её собственный синий и серый). Раньше здесь был красный —
    # из-за него кружки непрочитанных были красными, это было неправильно.
    'featuredStickers_addButton': ACCENT,
    'featuredStickers_addedIcon': ACCENT,
    'inappPlayerPlayPause': ACCENT,
    'chat_mediaLoaderPhotoIcon': ACCENT,
    'chat_mediaLoaderPhotoIconSelected': WHITE,
    'voipgroup_mutedIcon': RED,

    # иконки в меню — серые, как в Telegram
    'actionBarDefaultSubmenuItem': WHITE,
    'actionBarDefaultSubmenuItemIcon': GRAY_TEXT,

    # ---------------- ЧАТ: всё, что было «родным синим» — в палитру Yoru ----------------
    # (пользователь просил, чтобы цвета в самих чатах тоже были наши)
    'chat_inInstant': ACCENT,
    'chat_inInstantSelected': ACCENT,
    'chat_inReplyNameText': ACCENT,
    'chat_inForwardedNameText': ACCENT,
    'chat_inViaBotNameText': ACCENT,
    'chat_inSiteNameText': ACCENT,
    'chat_inPreviewLine': ACCENT,
    'chat_inPreviewInstantText': ACCENT,
    'chat_inReplyLine': ACCENT,
    'chat_inQuote': ACCENT,
    'chat_inVoiceSeekbarFill': ACCENT,
    'chat_inAudioTitleText': ACCENT,
    'chat_inAudioSeekbar': LINE,
    'chat_inAudioSeekbarSelected': ACCENT,
    'chat_inAudioProgress': ACCENT,
    'chat_inAudioCacheSeekbar': LINE,
    'chat_inAudioPerfomerText': GRAY_TEXT,
    'chat_inAudioDurationText': GRAY_TEXT,
    'chat_inAudioPerfomerSelectedText': GRAY_TEXT,
    'chat_inAudioDurationSelectedText': GRAY_TEXT,
    'chat_inAudioSelectedProgress': ACCENT,
    'chat_inVoiceSeekbar': LINE,
    'chat_inVoiceSeekbarSelected': ACCENT,
    'chat_outVoiceSeekbar': '4A3B63',
    'chat_outVoiceSeekbarSelected': ACCENT,
    'chat_outVoiceSeekbarFill': ACCENT,
    'chat_outAudioSeekbar': '4A3B63',
    'chat_outAudioSeekbarSelected': ACCENT,
    'chat_outAudioProgress': ACCENT,
    'chat_outAudioCacheSeekbar': '4A3B63',
    'chat_outAudioSelectedProgress': ACCENT,
    'chat_outAudioTitleText': WHITE,
    'chat_outAudioPerfomerText': 'E2CCFF',
    'chat_outAudioDurationText': 'E2CCFF',
    'chat_outAudioPerfomerSelectedText': 'E2CCFF',
    'chat_outAudioDurationSelectedText': 'E2CCFF',
    'chat_outForwardedNameText': 'E2CCFF',
    'chat_outReplyNameText': 'E2CCFF',
    'chat_outViaBotNameText': 'E2CCFF',
    'chat_outSiteNameText': WHITE,
    'chat_outPreviewLine': 'E2CCFF',
    'chat_outPreviewInstantText': 'E2CCFF',
    'chat_outReplyLine': 'E2CCFF',
    'chat_outInstant': 'E2CCFF',
    'chat_outInstantSelected': WHITE,
    'chat_outSentCheckRead': ACCENT,
    'chat_outSentCheckReadSelected': WHITE,
    'chat_inCodeBackground': '241D30',
    'chat_outCodeBackground': '332745',
    'chat_inFileBackground': '241D30',
    'chat_inFileBackgroundSelected': '2E2440',
    'chat_outFileBackground': '332745',
    'chat_outFileBackgroundSelected': '3B2E50',
    'chat_inFileInfoText': GRAY_TEXT,
    'chat_inFileInfoSelectedText': GRAY_TEXT,
    'chat_inFileNameText': WHITE,
    'chat_outFileNameText': WHITE,
    'chat_outFileInfoText': 'D9C8FF',
    'chat_outFileInfoSelectedText': 'E2CCFF',
    'chat_inLoaderPhoto': '241D30',
    'chat_inContactBackground': ACCENT,
    'chat_inContactNameText': ACCENT,
    'chat_inContactPhoneText': GRAY_TEXT,
    'chat_inContactPhoneSelectedText': GRAY_TEXT,
    'chat_inContactIcon': '21152F',
    'chat_outContactBackground': ACCENT,
    'chat_outContactNameText': WHITE,
    'chat_outContactPhoneText': 'E2CCFF',
    'chat_outContactPhoneSelectedText': 'E2CCFF',
    'chat_outContactIcon': '21152F',
    'chat_botKeyboardButtonBackground': CARD2,
    'chat_botKeyboardButtonBackgroundPressed': '2A2138',
    'chat_botKeyboardButtonText': WHITE,
    'chat_botSwitchToInlineText': ACCENT,
    'chat_unreadMessagesStartBackground': CARD2,
    'chat_unreadMessagesStartText': WHITE,
    'chat_unreadMessagesStartArrowIcon': GRAY_TEXT,
    'chat_secretTimeText': GRAY_TEXT,
    'chat_secretChatStatusText': GRAY_TEXT,
    'chat_emojiPanelIcon': GRAY_TEXT,
    'chat_emojiPanelIconSelected': ACCENT,
    'chat_emojiPanelBackspace': GRAY_TEXT,
    'chat_emojiPanelEmptyText': GRAY_TEXT,
    'chat_emojiPanelTrendingTitle': WHITE,
    'chat_emojiPanelTrendingDescription': GRAY_TEXT,
    'chat_emojiSearchIcon': GRAY_TEXT,
    'chat_emojiBottomPanelIcon': GRAY_TEXT,
    'chat_emojiPanelBadgeBackground': ACCENT,
    'chat_emojiPanelBadgeText': '21152F',
    'chat_emojiPanelStickerPackSelector': ACCENT_SOFT,
    'chat_emojiPanelStickerPackSelectorLine': ACCENT,
    'chat_emojiPanelNewTrending': ACCENT,
    'chat_attachActiveTab': ACCENT,
    'chat_attachUnactiveTab': GRAY_TEXT,
    'chat_replyPanelIcons': ACCENT,
    'chat_replyPanelName': ACCENT,
    'chat_replyPanelClose': GRAY_TEXT,
    'chat_replyPanelLine': LINE,
    'chat_topPanelTitle': ACCENT,
    'chat_topPanelMessage': GRAY_TEXT,
    'chat_topPanelClose': GRAY_TEXT,
    'chat_searchPanelIcons': GRAY_TEXT,
    'chat_searchPanelText': WHITE,
    'chat_messagePanelCancelInlineBot': GRAY_TEXT,
    'chat_messagePanelShadow': '00000000',
    'chat_messagePanelVoiceBackground': ACCENT,
    'chat_messagePanelVoiceDelete': '21152F',
    'chat_messagePanelVoiceDuration': '21152F',
    # ВАЖНО: этим цветом рисуется и стрелка отправки, и микрофон при записи.
    # Раньше он был красным — из-за этого кнопка отправки (в т.ч. отложенной)
    # была красной. В Telegram это белый.
    'chat_messagePanelVoicePressed': WHITE,
    'chat_recordedVoiceBackground': ACCENT,
    'chat_mediaMenu': WHITE,
    'chat_mediaSentClock': WHITE,
    'chat_muteIcon': GRAY_TEXT,
    'chat_lockIcon': WHITE,
    'chat_inMenu': GRAY_TEXT,
    'chat_inMenuSelected': GRAY_TEXT,
    'chat_outMenu': 'E2CCFF',
    'chat_outMenuSelected': 'E2CCFF',
    'chat_inViews': GRAY_TEXT,
    'chat_inViewsSelected': GRAY_TEXT,
    'chat_outViews': 'D9C8FF',
    'chat_outViewsSelected': 'E2CCFF',
    'chat_inReactionButtonText': WHITE,
    'chat_inReactionButtonTextSelected': WHITE,
    'chat_inlineResultIcon': ACCENT,
    'chat_TextSelectionCursor': ACCENT,
    'chat_linkSelectBackground': ACCENT_SOFT,
    'chat_textSelectBackground': ACCENT_SOFT,
    'chat_inTextSelectionHighlight': ACCENT_SOFT,
    'chat_outTextSelectionHighlight': ACCENT_SOFT,
    'chat_adminText': GRAY_TEXT,
    'chat_adminSelectedText': GRAY_TEXT,
    'chat_addContact': ACCENT,
    'chat_gifSaveHintText': WHITE,
    'chat_gifSaveHintBackground': CARD2,
    'chat_sentError': RED,
    'chat_unreadMessagesStartTextUnread': WHITE,

    # ---------------- список чатов: мелочи в тон палитре ----------------
    'chats_onlineCircle': GREEN,
    'chats_secretName': GREEN,
    'chats_secretIcon': GREEN,
    'chats_pinnedIcon': GRAY_TEXT,
    'chats_attachMessage': GRAY_TEXT,
    'chats_menuItemIcon': GRAY_TEXT,
    'chats_menuItemText': WHITE,
    'chats_menuPhone': GRAY_TEXT,
    'chats_menuPhoneCats': GRAY_TEXT,
    'chats_muteIcon': GRAY_TEXT,
    'chats_nameArchived': GRAY_TEXT,
    'chats_nameMessageArchived': GRAY_TEXT,
    'chats_messageArchived': GRAY_TEXT,
    'chats_archiveBackground': CARD2,
    'chats_archivePullDownBackground': CARD2,
    'chats_actionMessage': GRAY_TEXT,

    # ---------------- нижние вкладки (новый таб-бар «Glass» в ТГ 12.x) ----------------
    # В Telegram 12.10 нижняя навигация берёт цвета из ключей glass_*.
    # Ставим плоский чёрный фон без «стекла» и серо-белые иконки как в iOS/ТГ.
    'glass_targetMainTabs': BLACK,
    'glass_targetMainTopPanel': BLACK,
    'glass_tabSelected': WHITE,
    'glass_tabSelectedText': WHITE,
    'glass_tabUnselected': GRAY_TEXT,
    'glass_defaultIcon': GRAY_TEXT,
    'glass_defaultText': WHITE,
}


def to_int(hexstr):
    """#RRGGBB[AA] -> signed int для .attheme."""
    value = int(hexstr, 16)
    if len(hexstr) == 6:
        value |= 0xFF000000
    if value >= (1 << 31):
        value -= (1 << 32)
    return value


def build(assets, base_name='night.attheme'):
    base_path = os.path.join(assets, base_name)
    src = io.open(base_path, encoding='utf-8').read()

    # r81: keep a pristine Telegram Night asset beside the generated palette.
    # The settings switch uses this private copy for a reversible preview; it
    # must be created before any built-in theme is overwritten and must remain
    # stable on idempotent reruns.
    original_path = os.path.join(assets, 'kamigram_telegram_original_night.attheme')
    if not os.path.isfile(original_path):
        io.open(original_path, 'w', encoding='utf-8').write(src)

    # 1. запоминаем родные ключи и значения (они гарантированно согласованы)
    values = {}
    order = []
    for line in src.split('\n'):
        if '=' not in line:
            continue
        key, _, value = line.partition('=')
        key = key.strip()
        values[key] = value.strip()
        order.append(key)

    # 2. перекрываем iOS-значениями
    applied, added, missing = 0, [], []
    for key, hexval in PALETTE.items():
        if key in values:
            values[key] = str(to_int(hexval))
            applied += 1
        else:
            # Ключа нет в родной теме (например, новые glass_* из Telegram 12.x).
            # Дописываем его в конец: парсер тем Telegram берёт имена из своей
            # таблицы цветов (ThemeColors.colorKeysMap), поэтому новая строка
            # подхватывается и таб-бар становится плоским чёрным без «стекла».
            added.append(key)
            order.append(key)
            values[key] = str(to_int(hexval))

    # 3. собираем ответ
    out = '\n'.join('%s=%s' % (k, values[k]) for k in order) + '\n'

    # 4. Сохраняем отдельную встроенную тему KamiGram. Родные Blue, Night,
    #    Dark Blue, Day и Arctic намеренно НЕ перезаписываем: переключатель
    #    «Тема Telegram» должен вернуть настоящую тему Telegram, а не копию,
    #    которую мод уже изменил. Theme.java регистрирует этот asset как
    #    отдельный ключ KamiGram.
    target = os.path.join(assets, 'kamigram.attheme')
    io.open(target, 'w', encoding='utf-8').write(out)

    print('KamiGram: отдельная тема записана в %s; ключей перекрыто %d, дописано новых %d'
          % (os.path.basename(target), applied, len(added)))
    if added:
        print('  (дописаны новые ключи: %s)' % ', '.join(added))
    return 0


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else None
    if not assets or not os.path.isdir(assets):
        print('укажи каталог assets')
        return 2
    return build(assets)


if __name__ == '__main__':
    sys.exit(main())
