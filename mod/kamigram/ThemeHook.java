package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;

/**
 * Compatibility helpers for the mod's own small settings surfaces.
 *
 * Telegram's native Theme registry is deliberately not touched here.  There is
 * no custom attheme, no Theme.applyTheme call and no Theme.setColor call in the
 * Sakura build: the user's selected Telegram theme remains the only native
 * application theme.  The YORU_* values below are used only to draw the Sakura
 * settings dialog itself, not to repaint Telegram screens.
 */
public final class ThemeHook {

    private ThemeHook() {
    }

    /** Kept for old patch points; stock Telegram themes are never replaced. */
    public static void keepDarkTheme() {
    }

    /** Kept for source compatibility; never changes a Telegram color key. */
    public static void applyAccent() {
    }

    public static void notifyAccentChanged() {
    }

    public static void setTelegramTheme(boolean enabled) {
        // The old custom-theme switch is intentionally one-way now: only stock
        // Telegram themes are supported.  Keep the preference truthful for an
        // upgrade from an older build without applying any theme ourselves.
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

    /** No native theme hook remains; this is safe for legacy generated calls. */
    public static void apply(Activity activity) {
        if (activity != null) {
            applySecureFlag(activity);
        }
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

    /** The palette is for the Sakura settings surface only. */
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

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    /** Custom blur is not part of the native Telegram theme. */
    public static boolean allowBlur() {
        return false;
    }

    public static String designVersion() {
        return "Sakura · Telegram themes";
    }

    /** Legacy compatibility only; never changes Telegram's active theme. */
    public static void setDark(boolean dark) {
        try {
            KamiGramConfig.set(KamiGramConfig.KEY_IOS_DESIGN, dark);
            KamiGramConfig.set(KamiGramConfig.KEY_TELEGRAM_THEME, true);
        } catch (Throwable ignore) {
        }
    }

    /** Preserve the optional screenshot setting without touching theme state. */
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
