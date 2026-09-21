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
    /**
     * «Мощный» прокси: своя база живых прокси, проверка пинга и моментальное
     * переключение на рабочий (быстрее встроенного в разы).
     */
    public static final String KEY_SMART_PROXY = "kamigram_smart_proxy";
    /** Ускорение загрузок и потоков на любом интернете, особенно на слабом. */
    public static final String KEY_FAST_NET = "kamigram_fast_net";
    /** Скачанное вручную не удаляется автоматически: кэш не чистится за спиной. */
    public static final String KEY_KEEP_DOWNLOADS = "kamigram_keep_downloads";
    /** Не грузить GIF и анимации в чатах (0 байт на них). */
    public static final String KEY_NO_GIFS = "kamigram_no_gifs";
    /** Больше не спрашивать разрешения на контакты, телефон и уведомления. */
    public static final String KEY_NO_PERMISSION_NAGS = "kamigram_no_permission_nags";

    // ---------------------------------------------------------------- трафик
    /** Ничего не грузить по стикерам, наборам эмодзи и премиум-эмодзи (0 байт). */
    public static final String KEY_NO_STICKERS = "kamigram_no_stickers";
    /** Ничего не грузить по историям: ни списки, ни просмотры, ни само медиа. */
    public static final String KEY_NO_STORIES = "kamigram_no_stories";
    /** Премиум-эмодзи рисовать обычным эмодзи: файлы .tgs не скачиваются вообще. */
    public static final String KEY_NO_ANIMATED_EMOJI = "kamigram_no_animated_emoji";
    /** Убрать рекламные блоки Telegram Premium / Stars / TON / подарков. */
    public static final String KEY_NO_PREMIUM_UI = "kamigram_no_premium_ui";

    /** Реклама и рекомендации: спонсорские сообщения, рекомендованные каналы, папки. */
    public static final String KEY_NO_ADS = "kamigram_no_ads";
    /** Не грузить «часто используемые» контакты и топ-пиры. */
    public static final String KEY_NO_TOP_PEERS = "kamigram_no_top_peers";
    /** Не искать GIF/стикеры при вводе текста (поиск не уходит на сервер). */
    public static final String KEY_NO_GIF_SEARCH = "kamigram_no_gif_search";
    /** Не подгружать превью ссылок и веб-страницы (экономия трафика). */
    public static final String KEY_NO_LINK_PREVIEW = "kamigram_no_link_preview";
    /** Призрак для историй: просмотры чужих историй не записываются (stealth mode). */
    public static final String KEY_STORIES_STEALTH = "kamigram_stories_stealth";

    // ---------------------------------------------------------------- дизайн
    /** iOS-дизайн KamiGram: плоские табы, плоская шапка, свои иконки. */
    public static final String KEY_IOS_DESIGN = "kamigram_ios_design";
    /** iOS-геометрия облаков сообщений (скругление 18 вместо 17). */
    public static final String KEY_IOS_BUBBLES = "kamigram_ios_bubbles";

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

    /** Значение переключателя (для экрана настроек мода). */
    public static boolean value(String key) {
        return get(key);
    }

    /** Записать переключатель (экран настроек мода). */
    public static void set(String key, boolean value) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences != null) {
                preferences.edit().putBoolean(key, value).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Сводка состояния для строки настроек. */
    public static String summary() {
        return "призрак " + onOff(ghostMode())
            + " · стикеры " + onOff(!noStickers())
            + " · эмодзи " + onOff(!noAnimatedEmoji())
            + " · истории " + onOff(!noStories())
            + " · gif " + onOff(!noGifs())
            + " · прокси-ускорение " + onOff(smartProxy())
            + " · кэш " + onOff(keepDownloads());
    }

    private static String onOff(boolean value) {
        return value ? "вкл" : "выкл";
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
        return get(KEY_IOS_TABS) && iosDesign();
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

    /** Моментальное переключение прокси на рабочий (мощный прокси-движок). */
    public static boolean smartProxy() {
        return get(KEY_SMART_PROXY);
    }

    /** Ускорение сети и загрузок. */
    public static boolean fastNet() {
        return get(KEY_FAST_NET);
    }

    /** Скачанное не удалять автоматически. */
    public static boolean keepDownloads() {
        return get(KEY_KEEP_DOWNLOADS);
    }

    /** Не грузить GIF. */
    public static boolean noGifs() {
        return get(KEY_NO_GIFS);
    }

    /** Не надоедать запросами разрешений. */
    public static boolean noPermissionNags() {
        return get(KEY_NO_PERMISSION_NAGS);
    }

    /** Ноль трафика на стикеры, наборы эмодзи и премиум-эмодзи. */
    public static boolean noStickers() {
        return get(KEY_NO_STICKERS);
    }

    /** Ноль трафика на истории. */
    public static boolean noStories() {
        return get(KEY_NO_STORIES);
    }

    /** Премиум-эмодзи показываются обычным эмодзи (файлы не скачиваются). */
    public static boolean noAnimatedEmoji() {
        return get(KEY_NO_ANIMATED_EMOJI);
    }

    /** Реклама и рекомендации не запрашиваются вообще. */
    public static boolean noAds() {
        return get(KEY_NO_ADS);
    }

    /** «Часто используемые» и топ-пиры не грузятся. */
    public static boolean noTopPeers() {
        return get(KEY_NO_TOP_PEERS);
    }

    /** Поиск GIF/стикеров при вводе не уходит на сервер. */
    public static boolean noGifSearch() {
        return get(KEY_NO_GIF_SEARCH);
    }

    /** Превью ссылок и веб-страницы не подгружаются. */
    public static boolean noLinkPreview() {
        return get(KEY_NO_LINK_PREVIEW);
    }

    /** Призрак для историй: просмотры не записываются. */
    public static boolean storiesStealth() {
        return get(KEY_STORIES_STEALTH);
    }

    /** Без рекламных блоков Premium / Stars / TON. */
    public static boolean noPremiumUi() {
        return get(KEY_NO_PREMIUM_UI);
    }

    /** Основной iOS-дизайн KamiGram (кодом, а не темой). */
    public static boolean iosDesign() {
        return get(KEY_IOS_DESIGN);
    }

    /** iOS-геометрия облаков сообщений. */
    public static boolean iosBubbles() {
        return get(KEY_IOS_BUBBLES);
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
