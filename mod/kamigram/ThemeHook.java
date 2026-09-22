package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * KamiGram: оформление «iOS 2026» — правильная версия.
 *
 * В прошлой сборке цвета задавались кодом целиком: фон карточек делали
 * чёрным (Theme.setColor(key_windowBackgroundWhite, 0xFF000000)), а имя чата
 * тоже чёрным — получался чёрный текст на чёрном фоне. Здесь так нельзя:
 *
 *   * ВСЕ поверхности, тексты, облака и разделители приходят из assets-темы
 *     (mod/kamigram/apply_theme_pro.py переписывает родную тёмную тему
 *     Telegram на iOS-палитру — там каждый текст согласован со своим фоном).
 *   * Код трогает ТОЛЬКО акценты: синяя кнопка отправки, галочки, прогресс,
 *     ссылки, переключатели. Ни одного фонового или текстового ключа.
 *   * Тема «Night» (наш iOS-набор) держится всегда, поэтому система не может
 *     вернуть светлую тему и белый фон с белым текстом.
 *
 * Плюс мелочи iOS: прозрачная строка состояния, светлые иконки, отсутствие
 * «стекла» и градиентов в шапке.
 */
public final class ThemeHook {

    /** Встроенная тёмная тема Telegram, в которую P16 записал iOS-палитру. */
    private static final String THEME_KEY = "Night";

    private static final ArrayList<WeakReference<Activity>> ACTIVITIES = new ArrayList<>();

    private ThemeHook() {
    }

    // ------------------------------------------------------------------ тема

    /**
     * Держим тёмную iOS-тему. Если пользователь поставил свою тему — уважаем
     * её и ничего не ломаем: тогда применяются только акценты мода.
     */
    public static void keepDarkTheme() {
        try {
            if (Theme.getActiveTheme() != null && Theme.isCurrentThemeDark()) {
                return;
            }
            final Theme.ThemeInfo info = Theme.getTheme(THEME_KEY);
            if (info != null) {
                Theme.applyTheme(info, true);
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Тёмная тема сейчас? */
    public static boolean isDark(Context context) {
        try {
            if (Theme.isCurrentThemeDark()) {
                return true;
            }
            if (context != null) {
                final int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
                return mode == Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignore) {
        }
        return true;
    }

    // ------------------------------------------------------------------ акценты

    /** Акцент мода — фиолетовый Yoru (#C8A7FF). Выбора цвета нет: палитра одна. */
    public static int accent() {
        return YORU_PURPLE;
    }

    /**
     * Довести акценты до палитры Yoru. Ни одного ключа фона или основного
     * текста здесь нет, поэтому читаемость не может пострадать: трогаем только
     * «цветные» элементы (ссылки, галочки, переключатели, прогресс).
     */
    public static void applyAccent() {
        final int accent = YORU_PURPLE;
        final int soft = (accent & 0x00FFFFFF) | 0x33000000;
        final int soft22 = (accent & 0x00FFFFFF) | 0x22000000;
        final int dark = 0xFF21152F;

        set(Theme.key_dialogTextBlue, accent);
        set(Theme.key_dialogTextBlue2, accent);
        set(Theme.key_dialogTextBlue4, accent);
        set(Theme.key_dialogTextLink, accent);
        set(Theme.key_dialogButton, accent);
        set(Theme.key_dialogButtonSelector, soft22);
        set(Theme.key_windowBackgroundWhiteValueText, accent);
        set(Theme.key_windowBackgroundWhiteLinkText, accent);
        set(Theme.key_windowBackgroundWhiteLinkSelection, soft);
        set(Theme.key_windowBackgroundWhiteBlueText, accent);
        set(Theme.key_windowBackgroundWhiteBlueText2, accent);
        set(Theme.key_windowBackgroundWhiteBlueText3, accent);
        set(Theme.key_windowBackgroundWhiteBlueText4, accent);
        set(Theme.key_windowBackgroundWhiteBlueText5, accent);
        set(Theme.key_chat_messageLinkIn, accent);
        set(Theme.key_chat_messageLinkOut, YORU_PURPLE_SOFT);
        set(Theme.key_chat_messagePanelSend, accent);
        set(Theme.key_chat_inLoader, accent);
        set(Theme.key_chat_outLoader, accent);
        set(Theme.key_chat_inLoaderSelected, accent);
        set(Theme.key_chat_outLoaderSelected, accent);
        set(Theme.key_profile_actionIcon, accent);
        set(Theme.key_profile_creatorIcon, accent);
        set(Theme.key_chat_replyPanelName, accent);
        set(Theme.key_checkbox, accent);
        set(Theme.key_fastScrollActive, accent);
        set(Theme.key_progressCircle, accent);
        set(Theme.key_player_progress, accent);
        set(Theme.key_player_buttonActive, accent);
        set(Theme.key_featuredStickers_addButton, accent);
        set(Theme.key_featuredStickers_addedIcon, accent);
        set(Theme.key_chat_recordedVoiceProgress, accent);
        set(Theme.key_contextProgressOuter1, accent);
        set(Theme.key_radioBackgroundChecked, accent);
        set(Theme.key_checkboxSquareBackground, accent);
        set(Theme.key_switchTrackChecked, accent);
        set(Theme.key_switchTrackBlueChecked, accent);
        set(Theme.key_windowBackgroundChecked, accent);
        // стрелка отправки и микрофон: тёмный текст на светлой кнопке (не красный!)
        set(Theme.key_chat_messagePanelVoicePressed, dark);
        set(Theme.key_chat_messagePanelVoiceDuration, dark);
        set(Theme.key_chat_messagePanelVoiceDelete, dark);
        // запись голосового — красный только там, где он и должен быть
        set(Theme.key_chat_recordTime, 0xFFFF8F9F);
        set(Theme.key_chat_recordedVoiceDot, 0xFFFF8F9F);
        set(Theme.key_chat_recordVoiceCancel, 0xFFFF8F9F);
    }

    private static void set(int key, int color) {
        try {
            Theme.setColor(key, color, false);
        } catch (Throwable ignore) {
        }
    }

    /** Пользователь поменял цвет — обновляем все открытые экраны. */
    public static void notifyAccentChanged() {
        applyAccent();
        for (int i = ACTIVITIES.size() - 1; i >= 0; i--) {
            final Activity current = ACTIVITIES.get(i).get();
            if (current == null) {
                ACTIVITIES.remove(i);
                continue;
            }
            try {
                current.recreate();
            } catch (Throwable ignore) {
            }
        }
    }

    // ------------------------------------------------------------------ экраны

    /** Вызывать после создания окна экрана: тема, полосы, акценты. */
    public static void apply(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            register(activity);
            keepDarkTheme();
            applyAccent();
            tintSystemBars(activity);
            applySecureFlag(activity);
            KamiGramTweaks.apply();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    public static void forget(Activity activity) {
        if (activity == null) {
            return;
        }
        for (int i = ACTIVITIES.size() - 1; i >= 0; i--) {
            final Activity current = ACTIVITIES.get(i).get();
            if (current == null || current == activity) {
                ACTIVITIES.remove(i);
            }
        }
    }

    private static void register(Activity activity) {
        for (WeakReference<Activity> reference : ACTIVITIES) {
            if (reference.get() == activity) {
                return;
            }
        }
        ACTIVITIES.add(new WeakReference<>(activity));
    }

    /**
     * Системные полосы — как в Telegram: статус-бар прозрачный, нижняя
     * навигационная панель ПРОЗРАЧНАЯ (никакой чёрной полосы снизу).
     *
     * Раньше мод красил её в чёрный и сбрасывал системные флаги
     * (setSystemUiVisibility(0)), из-за чего приложение перестало рисовать
     * контент под панелью — панель становилась чёрной полосой.
     */
    public static void tintSystemBars(Activity activity) {
        try {
            final Window window = activity.getWindow();
            if (window == null) {
                return;
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
                window.setNavigationBarDividerColor(0x00000000);
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Запрет скриншотов и превью в списке задач — если пользователь включил
     * это в центре мода. По умолчанию выключено: ничего не меняем.
     */
    public static void applySecureFlag(Activity activity) {
        try {
            if (KamiGramConfig.noScreenshots()) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------------------ цвета для вьюх мода

    /*
     * Палитра Yoru (yoru-android): BG #0D0B12, SURFACE #15111C, CARD #1C1724,
     * PURPLE #C8A7FF, TEXT #F7F0FF, MUTED #A99BB8, LINE #352A43.
     * Взята из кода Yoru (Ui.BG/CARD/SURFACE/PURPLE/TEXT/MUTED/LINE).
     */
    public static final int YORU_BG = 0xFF0D0B12;
    public static final int YORU_SURFACE = 0xFF15111C;
    public static final int YORU_CARD = 0xFF1C1724;
    public static final int YORU_CARD_HIGH = 0xFF21192E;
    public static final int YORU_PURPLE = 0xFFC8A7FF;
    public static final int YORU_PURPLE_SOFT = 0xFFE2CCFF;
    public static final int YORU_TEXT = 0xFFF7F0FF;
    public static final int YORU_MUTED = 0xFFA99BB8;
    public static final int YORU_LINE = 0xFF352A43;
    public static final int YORU_AMBER = 0xFFFFCF70;
    public static final int YORU_EMERALD = 0xFF88E0A0;

    public static int surface() {
        return YORU_CARD;
    }

    public static int surfaceNested() {
        return YORU_SURFACE;
    }

    public static int background() {
        return 0xFF000000;
    }

    public static int primaryText() {
        return YORU_TEXT;
    }

    public static int secondaryText() {
        return YORU_MUTED;
    }

    public static int separator() {
        return YORU_LINE;
    }

    public static int green() {
        return YORU_EMERALD;
    }

    public static int red() {
        return 0xFFFF453A;
    }

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    /** «Стекло» и размытие в моде выключены — как в iOS. */
    public static boolean allowBlur() {
        return false;
    }

    public static String designVersion() {
        return "KamiGram iOS 2026.2";
    }

    /** Включить/выключить принудительную тёмную тему (для экрана мода). */
    public static void setDark(boolean dark) {
        KamiGramConfig.set(KamiGramConfig.KEY_IOS_DESIGN, dark);
        keepDarkTheme();
    }
}
