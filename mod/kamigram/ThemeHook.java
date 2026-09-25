package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;

/**
 * Палитра и совместимость собственных экранов Sakura.
 *
 * KAMIGRAM_SAKURA_PALETTE_R101: возвращён прежний красивый дизайн (Yoru) для
 * САМИХ настроек Sakura — карточки, чипы, переключатели, диалоги и кнопки снова
 * рисуются в своей палитре, а не в цветах выбранной темы Telegram.
 *
 * При этом реестр тем Telegram не трогается: stock Telegram themes only —
 * в приложении по-прежнему доступны только оригинальные темы Telegram (нет
 * кастомного attheme, нет мутации нативной темы, нет перерисовки по lifecycle).
 * Цвета ниже читает только код Sakura (KamiGramCenter / KamiGramDialog /
 * KamiGramUi / KamiGramBranding).
 */
public final class ThemeHook {

    private ThemeHook() {
    }

    /** Legacy patch point; the selected Telegram theme remains authoritative. */
    public static void keepDarkTheme() {
    }

    /** Legacy patch point; never changes a Telegram color key. */
    public static void applyAccent() {
    }

    public static void notifyAccentChanged() {
    }

    /**
     * Kept for source compatibility with the old settings row.  The row cannot
     * switch to a custom theme in Sakura; it only records the stock-only state
     * and explains the policy to the user.
     */
    public static void toggleTelegramTheme(Context context) {
        try {
            KamiGramConfig.set(KamiGramConfig.KEY_TELEGRAM_THEME, true);
            KamiGramUi.notify(context, "В Sakura доступны только оригинальные темы");
        } catch (Throwable ignore) {
        }
    }

    public static void setTelegramTheme(boolean enabled) {
        try {
            KamiGramConfig.set(KamiGramConfig.KEY_TELEGRAM_THEME, true);
        } catch (Throwable ignore) {
        }
    }

    public static boolean telegramTheme() {
        return true;
    }

    public static boolean isDark(Context context) {
        try {
            if (context != null) {
                final int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
                return mode == Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /** Safe for old generated calls; only the optional screenshot flag remains. */
    public static void apply(Activity activity) {
        applySecureFlag(activity);
    }

    public static boolean uiHooksDisabled() {
        return false;
    }

    public static boolean flickerDetected() {
        return false;
    }

    public static void resetFlickerGuard() {
    }

    public static void requestAccentRefresh() {
    }

    public static void recreateScreensOnce() {
    }

    public static void forget(Activity activity) {
    }

    // ------------------------------------------------- палитра Sakura (Yoru)
    //
    // KAMIGRAM_SAKURA_PALETTE_R101: возвращён прежний красивый дизайн САМИХ
    // настроек Sakura. Эти цвета использует только собственный интерфейс мода
    // (KamiGramCenter / KamiGramDialog / KamiGramUi). Реестр тем Telegram не
    // трогается: в приложении по-прежнему доступны только оригинальные темы.

    /** Фон экрана Sakura-настроек. */
    public static final int YORU_BG = 0xFF0D0B12;
    /** Вложенная поверхность (чипы, поля). */
    public static final int YORU_SURFACE = 0xFF15111C;
    /** Карточка. */
    public static final int YORU_CARD = 0xFF1C1724;
    /** Приподнятая карточка. */
    public static final int YORU_CARD_HIGH = 0xFF21192E;
    /** Основной акцент. */
    public static final int YORU_PURPLE = 0xFFC8A7FF;
    /** Мягкий акцент. */
    public static final int YORU_PURPLE_SOFT = 0xFFE2CCFF;
    /** Основной текст. */
    public static final int YORU_TEXT = 0xFFF7F0FF;
    /** Вторичный текст. */
    public static final int YORU_MUTED = 0xFFA99BB8;
    /** Разделители и обводки. */
    public static final int YORU_LINE = 0xFF352A43;
    /** Тёплый акцент (предупреждения, «внимание»). */
    public static final int YORU_AMBER = 0xFFFFCF70;
    /** Положительный статус («включено», «загружается»). */
    public static final int YORU_EMERALD = 0xFF88E0A0;
    /** Текст на акцентной подложке. */
    public static final int YORU_ON_ACCENT = 0xFF21152F;

    public static int surface() {
        return YORU_CARD;
    }

    public static int surfaceHigh() {
        return YORU_CARD_HIGH;
    }

    public static int surfaceNested() {
        return YORU_SURFACE;
    }

    public static int background() {
        return YORU_BG;
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

    public static int amber() {
        return YORU_AMBER;
    }

    public static int accent() {
        return YORU_PURPLE;
    }

    public static int accentSoft() {
        return YORU_PURPLE_SOFT;
    }

    /** Цвет текста/иконки, нарисованных поверх accent(). */
    public static int onAccent() {
        return YORU_ON_ACCENT;
    }

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    /** Custom blur is not part of Telegram's native theme lifecycle. */
    public static boolean allowBlur() {
        return false;
    }

    public static String designVersion() {
        return "Sakura · Yoru";
    }

    /** Preserve the optional screenshot setting without touching theme state. */
    public static void setDark(boolean dark) {
        try {
            KamiGramConfig.set(KamiGramConfig.KEY_IOS_DESIGN, dark);
            KamiGramConfig.set(KamiGramConfig.KEY_TELEGRAM_THEME, true);
        } catch (Throwable ignore) {
        }
    }

    public static void applySecureFlag(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            final Window window = activity.getWindow();
            if (window == null) {
                return;
            }
            final boolean want = KamiGramConfig.noScreenshots();
            final boolean has = (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
            if (want && !has) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else if (!want && has) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable ignore) {
        }
    }
}
