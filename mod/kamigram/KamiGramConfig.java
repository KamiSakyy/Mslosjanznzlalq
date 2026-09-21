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
    /**
     * Force the plain SMS login code instead of the Google Play Integrity / Firebase flow.
     * A mod is not published in Google Play and is signed with a different key, so the
     * integrity request can hang forever and the login button just spins.
     */
    public static final String KEY_FORCE_SMS = "kamigram_force_sms";
    /**
     * Log in straight away: no "is this your number?" popup and no runtime permission
     * dialogs. Those extra steps are where the login used to freeze on some devices -
     * the code request must be sent immediately after the button is tapped.
     */
    public static final String KEY_FAST_LOGIN = "kamigram_fast_login";
    /** Activate a proxy automatically when its link appears in the clipboard. */
    public static final String KEY_AUTO_PROXY_CLIPBOARD = "kamigram_auto_proxy_clipboard";
    /** Switch a dead proxy off automatically so VPN / direct connection can work. */
    public static final String KEY_PROXY_FALLBACK = "kamigram_proxy_fallback";

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

    /** Ask the server for a plain SMS code (no Play Integrity / Firebase). */
    public static boolean forceSmsLogin() {
        return get(KEY_FORCE_SMS);
    }

    /** Send the code request right away, without the confirmation / permission popups. */
    public static boolean fastLogin() {
        return get(KEY_FAST_LOGIN);
    }

    /** Auto-enable a proxy link found in the clipboard. */
    public static boolean autoProxyFromClipboard() {
        return get(KEY_AUTO_PROXY_CLIPBOARD);
    }

    /** Auto-disable a proxy that does not connect. */
    public static boolean proxyFallback() {
        return get(KEY_PROXY_FALLBACK);
    }

    /** Guards the "ask for a plain SMS instead of Firebase" resend so it happens only once. */
    private static boolean forceSmsResent;

    public static boolean forceSmsConsumed() {
        return forceSmsResent;
    }

    public static void markForceSmsResent() {
        forceSmsResent = true;
    }
}
