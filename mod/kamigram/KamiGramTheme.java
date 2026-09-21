package org.telegram.messenger.kamigram;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * KamiGram: iOS-цвета кодом.
 *
 * Тема (attheme) задаёт общий вид, но некоторые элементы Telegram берёт из своих
 * ключей. Здесь они переопределяются прямо в коде — как в iOS: красный счётчик
 * непрочитанного, зелёный «в сети», серые даты и разделители, тёмные поверхности.
 * Применяется при старте приложения и при каждой смене темы.

 * Один переключатель — iOS-дизайн (KamiGramConfig.iosDesign()).
 */
public final class KamiGramTheme {

    private KamiGramTheme() {
    }

    /** Переопределяет ключевые цвета интерфейса под iOS. */
    public static void apply() {
        try {
            if (!KamiGramConfig.iosDesign()) {
                return;
            }
            set(Theme.key_chats_unreadCounter, 0xFFFF3B30);
            set(Theme.key_chats_unreadCounterMuted, 0xFF8E8E93);
            set(Theme.key_chats_unreadCounterText, 0xFFFFFFFF);
            set(Theme.key_chats_onlineCircle, 0xFF30D158);
            set(Theme.key_chats_name, 0xFF000000);
            set(Theme.key_chats_message, 0xFF8E8E93);
            set(Theme.key_chats_date, 0xFF8E8E93);
            set(Theme.key_chats_nameMessage, 0xFF8E8E93);
            set(Theme.key_chats_pinnedIcon, 0xFF8E8E93);
            set(Theme.key_chats_muteIcon, 0xFF8E8E93);
            set(Theme.key_chats_secretIcon, 0xFF30D158);
            set(Theme.key_divider, 0xFF38383A);
            set(Theme.key_chat_serviceBackground, 0xFF262628);
            set(Theme.key_chat_selectedBackground, 0xFF1C1C1E);
            set(Theme.key_chat_messagePanelBackground, 0xFF1C1C1E);
            if (ApplicationLoader.applicationContext != null) {
                Theme.createDialogsResources(ApplicationLoader.applicationContext);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void set(int key, int color) {
        try {
            Theme.setColor(key, color, false);
        } catch (Throwable ignore) {
        }
    }
}
