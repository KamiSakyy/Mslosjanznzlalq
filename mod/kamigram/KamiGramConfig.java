package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

/**
 * KamiGram: switches for the unique mod features.
 *
 * Values are read from the app-wide settings, so they can be toggled from the UI
 * (kamigram_* keys); every feature is on by default.
 */
public final class KamiGramConfig {

    /** Do not tell the server that we read / are typing / are online. */
    public static final String KEY_GHOST = "kamigram_ghost";
    /** Ignore forward/save/screenshot restrictions in protected chats. */
    public static final String KEY_NO_RESTRICTIONS = "kamigram_no_restrictions";
    /** Show chat and user IDs in the profile. */
    public static final String KEY_SHOW_IDS = "kamigram_show_ids";
    /** Flat iOS-style tabs (no glass, no blur). */
    public static final String KEY_IOS_TABS = "kamigram_ios_tabs";

    private KamiGramConfig() {
    }

    private static boolean get(String key) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            return preferences == null || preferences.getBoolean(key, true);
        } catch (Throwable ignore) {
            return true;
        }
    }

    /** Ghost mode: the peer cannot see that we read, type or are online. */
    public static boolean ghostMode() {
        return get(KEY_GHOST);
    }

    /** Lift protected-content restrictions (forward, save, screenshots). */
    public static boolean noRestrictions() {
        return get(KEY_NO_RESTRICTIONS);
    }

    /** Show chat/user ID. */
    public static boolean showIds() {
        return get(KEY_SHOW_IDS);
    }

    /** Flat iOS-style tab bar. */
    public static boolean iosTabs() {
        return get(KEY_IOS_TABS);
    }
}
