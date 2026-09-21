package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;

/**
 * KamiGram: применение переключателей мода к настройкам самого Telegram.
 *
 * Некоторые функции Telegram уже умеет — просто спрятаны глубоко в настройках
 * или по умолчанию выключены. Здесь мод приводит их к состоянию, выбранному
 * в центре KamiGram: размер текста, отправка по Enter, скрытие текста в
 * уведомлениях, счётчик трафика.
 *
 * Вызывается при старте приложения и после смены настроек в центре мода.
 */
public final class KamiGramTweaks {

    private KamiGramTweaks() {
    }

    /** Применить всё разом. */
    public static void apply() {
        applyFontSize();
        applyEnterToSend();
        applyNotificationPreview();
        KamiGramTraffic.restore();
        KamiGramTraffic.init();
        applyBackground();
    }

    /** Размер текста сообщений: 16 + выбранная надбавка. */
    public static void applyFontSize() {
        try {
            final int boost = KamiGramConfig.fontBoost();
            if (boost > 0) {
                SharedConfig.fontSize = 16 + boost;
                SharedConfig.fontSizeIsDefault = false;
            } else if (SharedConfig.fontSizeIsDefault) {
                SharedConfig.fontSize = 16;
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /**
     * Отправка по Enter. В Telegram это настройка «send_by_enter» (по умолчанию
     * выкл). Если в центре мода включено — записываем её как в оригинальном
     * приложении, поэтому работает и аппаратная клавиатура, и Enter на экране.
     */
    public static void applyEnterToSend() {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences == null) {
                return;
            }
            final boolean want = KamiGramConfig.enterToSend();
            if (preferences.getBoolean("send_by_enter", false) != want) {
                preferences.edit().putBoolean("send_by_enter", want).apply();
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /**
     * Скрыть текст уведомлений: Telegram показывает превью, если включены
     * «EnablePreviewAll / Group / Channel». Выключаем все три, когда в центре
     * мода стоит галочка.
     */
    public static void applyNotificationPreview() {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
            if (preferences == null || !KamiGramConfig.hideNotificationText()) {
                return;
            }
            preferences.edit()
                .putBoolean("EnablePreviewAll", false)
                .putBoolean("EnablePreviewGroup", false)
                .putBoolean("EnablePreviewChannel", false)
                .apply();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Фон чата: чёрный (AMOLED) или графит — как выбрано в центре мода. */
    public static void applyBackground() {
        try {
            org.telegram.ui.ActionBar.Theme.setColor(org.telegram.ui.ActionBar.Theme.key_chat_wallpaper,
                KamiGramConfig.chatBackgroundIndex() == 0 ? 0xFF000000 : 0xFF1C1C1E, false);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Применить после смены настроек в центре мода. */
    public static void applyAndRefresh() {
        apply();
        ThemeHook.applyAccent();
    }
}
