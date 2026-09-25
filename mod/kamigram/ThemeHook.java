package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;

/**
 * Compatibility helpers for Sakura's own settings surfaces.
 *
 * Telegram's native theme registry is deliberately not touched here.  Sakura
 * ships only Telegram's original themes: stock Telegram themes only; there is no custom attheme,
 * no native theme mutation and no activity-lifecycle repaint. Sakura's own small settings surfaces read the currently selected
 * Telegram palette through the accessors below; they do not install a second
 * palette or write colors into Telegram's theme registry.
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
            KamiGramUi.notify(context, "В Sakura доступны только оригинальные темы Telegram");
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

    /** Colors for Sakura's private surfaces, always read from the active Telegram theme. */
    private static int nativeColor(int key, int fallback) {
        try {
            final int color = org.telegram.ui.ActionBar.Theme.getColor(key);
            return color != 0 ? color : fallback;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    public static int surface() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite, 0xFFFFFFFF);
    }

    public static int surfaceNested() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray, surface());
    }

    public static int background() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundGray, surface());
    }

    public static int primaryText() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText, 0xFF000000);
    }

    public static int secondaryText() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText, 0xFF777777);
    }

    public static int separator() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_divider, 0x22000000);
    }

    public static int green() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_color_green, 0xFF2E9E55);
    }

    public static int red() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_color_red, 0xFFD93025);
    }

    public static int accent() {
        return nativeColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlueText, 0xFF2F80ED);
    }

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    /** Custom blur is not part of Telegram's native theme lifecycle. */
    public static boolean allowBlur() {
        return false;
    }

    public static String designVersion() {
        return "Sakura · Telegram themes";
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
