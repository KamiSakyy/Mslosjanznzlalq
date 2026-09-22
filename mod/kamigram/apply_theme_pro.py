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
# iOS-значения (hex)
# ---------------------------------------------------------------------------
BLACK = '000000'
CARD = '1C1C1E'         # карточки/строки (iOS secondary background)
CARD2 = '2C2C2E'        # вложенные поверхности
LINE = '38383A'         # разделители iOS
GRAY_TEXT = '8E8E93'    # iOS secondary label
WHITE = 'FFFFFF'
ACCENT = '0A84FF'       # iOS system blue
ACCENT_SOFT = '330A84FF'
GREEN = '34C759'        # iOS switch
RED = 'FF453A'          # iOS destructive / запись
BLUE_TEXT = 'A8C7E8'    # время в исходящих (как в iOS-теме Telegram)

PALETTE = {
    # ---------------- поверхности ----------------
    'windowBackgroundGray': BLACK,          # фон экранов
    'windowBackgroundWhite': CARD,          # строки и карточки
    'actionBarDefault': BLACK,              # шапка — чёрная, как в iOS
    'actionBarDefaultSelector': '22FFFFFF',
    'actionBarDefaultSubmenuBackground': CARD,
    'actionBarDefaultSubmenuSeparator': LINE,
    'dialogBackground': CARD,
    'dialogBackgroundGray': CARD2,
    'graySection': CARD,
    'divider': LINE,
    'dialogGrayLine': LINE,
    'dialogShadowLine': '00000000',
    'dialogLineProgressBackground': '3A3A3C',
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
    'chat_attachButtonBackgroundPressed': '3A3A3C',
    'profile_actionBackground': '00000000',
    'profile_actionPressedBackground': '00000000',

    # ---------------- текст (всё светлое — ничего не может пропасть) ----------------
    'actionBarDefaultTitle': WHITE,
    'actionBarDefaultIcon': WHITE,
    'actionBarDefaultSubtitle': GRAY_TEXT,
    'actionBarDefaultSearchPlaceholder': GRAY_TEXT,
    'actionBarTabActiveText': WHITE,
    'actionBarTabUnactiveText': GRAY_TEXT,
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
    'fastScrollInactive': '3A3A3C',
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
    'chat_serviceBackground': 'CC1C1C1E',
    'chat_serviceBackgroundSelected': 'CC2C2C2E',
    'chat_selectedBackground': '14FFFFFF',
    'chat_messagePanelText': WHITE,
    'chat_messagePanelHint': GRAY_TEXT,
    'chat_messagePanelIcons': GRAY_TEXT,
    'chat_messagePanelSend': ACCENT,
    'chat_recordTime': RED,
    'chat_recordedVoiceDot': RED,
    'chat_recordVoiceCancel': RED,
    'chat_recordVoiceCancelSelected': RED,
    'chat_messagePanelVoicePressed': RED,
    'chat_goDownButton': CARD2,
    'chat_goDownButtonIcon': WHITE,
    'text_RedBold': RED,
    'text_RedRegular': RED,
    'windowBackgroundWhiteGreenText': GREEN,
    'windowBackgroundWhiteGreenText2': GREEN,
    'calls_callReceivedGreenIcon': GREEN,

    # ---------------- облака и связи ----------------
    'chat_inBubble': '262628',
    'chat_inBubbleSelected': CARD2,
    'chat_outBubble': '2B5278',
    'chat_outBubbleSelected': '33608A',
    'chat_outBubbleGradient': '2B5278',
    'chat_outBubbleGradientSelectedOverlay': '33FFFFFF',
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
    'switchTrackChecked': GREEN,          # переключатели как в iOS
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

    # 4. пишем во все тёмные темы + в тему дня (чтобы день и ночь были одинаково iOS)
    written = []
    for name in ('bluebubbles.attheme', 'darkblue.attheme', 'night.attheme'):
        path = os.path.join(assets, name)
        io.open(path, 'w', encoding='utf-8').write(out)
        written.append(name)

    print('KamiGram: iOS-тема применена к %s; ключей перекрыто %d, дописано новых %d'
          % (', '.join(written), applied, len(added)))
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
