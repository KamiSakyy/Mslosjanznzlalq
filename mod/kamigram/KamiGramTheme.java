package org.telegram.messenger.kamigram;

import org.telegram.ui.ActionBar.Theme;

/**
 * Compatibility shim for legacy patch points.
 *
 * Sakura deliberately ships only Telegram's original themes.  Older patch
 * stages may still call this method, so it remains as a harmless no-op instead
 * of changing Theme colors or selecting a custom theme.
 */
public final class KamiGramTheme {

    private KamiGramTheme() {
    }

    public static void apply() {
        try {
            ThemeHook.applyAccent();
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
    }
}
