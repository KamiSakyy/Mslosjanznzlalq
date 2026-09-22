package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

/**
 * KamiGram: переключатели и настройки мода.
 *
 * Значения лежат в общих настройках приложения (kamigram_*), поэтому их видно
 * в экране «KamiGram: центр» и они сохраняются между запусками.
 *
 * ВАЖНО про значения по умолчанию: всё, что меняет поведение Telegram,
 * по умолчанию выключено, если это может удивить пользователя (например,
 * «скачанное не удалять» — иначе нельзя чистить кэш). Наоборот, всё, что
 * экономит трафик и не мешает, включено.
 */
public final class KamiGramConfig {

    // ------------------------------------------------------------- приватность
    /** Призрак: не видно чтение, «печатает», «в сети». */
    public static final String KEY_GHOST = "kamigram_ghost";
    /** При призраке отправлять сообщения тихо (через отложку) — без отметки «в сети». */
    public static final String KEY_GHOST_SEND = "kamigram_ghost_send";
    /** Не записывать просмотры историй (серверная невидимка). */
    public static final String KEY_STORIES_STEALTH = "kamigram_stories_stealth";
    /** Снять запреты защищённого контента. */
    public static final String KEY_NO_RESTRICTIONS = "kamigram_no_restrictions";
    /** Показывать ID чатов и пользователей. */
    public static final String KEY_SHOW_IDS = "kamigram_show_ids";
    /** Сохранять текст удалённых сообщений в журнал. */
    public static final String KEY_KEEP_DELETED = "kamigram_keep_deleted";
    /** Не спрашивать разрешения (контакты, телефон, уведомления). */
    public static final String KEY_NO_PERMISSION_NAGS = "kamigram_no_permission_nags";
    /** Запрет скриншотов во всём приложении (FLAG_SECURE). */
    public static final String KEY_NO_SCREENSHOTS = "kamigram_no_screenshots";
    /** Скрывать содержимое уведомлений. */
    public static final String KEY_HIDE_NOTIFICATION_TEXT = "kamigram_hide_notification_text";

    // ------------------------------------------------------------- прокси и сеть
    /** Мощный прокси: база живых прокси и моментальное переключение. */
    public static final String KEY_SMART_PROXY = "kamigram_smart_proxy";
    /** Нерабочий прокси выключается сам. */
    public static final String KEY_PROXY_FALLBACK = "kamigram_proxy_fallback";
    /** Прокси из буфера обмена включается сам. */
    public static final String KEY_AUTO_PROXY_CLIPBOARD = "kamigram_auto_proxy_clipboard";
    /** Ускорение загрузок и потоков (особенно на слабом интернете). */
    public static final String KEY_FAST_NET = "kamigram_fast_net";

    // ------------------------------------------------------------- трафик
    /** РЕЖИМ «ТОЛЬКО ТЕКСТ»: ни одной картинки, медиа — по нажатию. */
    public static final String KEY_TEXT_ONLY = "kamigram_text_only";
    /** Умный фильтр рекламы в сообщениях. */
    public static final String KEY_ADS_FILTER = "kamigram_ads_filter";
    /** Встроенные прокси сборки с авто-роутингом (KamiProxy). */
    public static final String KEY_BUILTIN_PROXY = "kamigram_builtin_proxy";
    /** Не грузить стикеры и наборы эмодзи. */
    public static final String KEY_NO_STICKERS = "kamigram_no_stickers";
    /** Не грузить истории и их медиа. */
    public static final String KEY_NO_STORIES = "kamigram_no_stories";
    /** Премиум-эмодзи показывать обычным эмодзи. */
    public static final String KEY_NO_ANIMATED_EMOJI = "kamigram_no_animated_emoji";
    /** Не грузить GIF и анимации. */
    public static final String KEY_NO_GIFS = "kamigram_no_gifs";
    /** Не подгружать превью ссылок. */
    public static final String KEY_NO_LINK_PREVIEW = "kamigram_no_link_preview";
    /** Не искать GIF и стикеры при вводе. */
    public static final String KEY_NO_GIF_SEARCH = "kamigram_no_gif_search";
    /** Не грузить «часто используемые» контакты. */
    public static final String KEY_NO_TOP_PEERS = "kamigram_no_top_peers";
    /** Реклама и рекомендации не запрашиваются. */
    public static final String KEY_NO_ADS = "kamigram_no_ads";
    /** Без блоков Premium / Stars / TON. */
    public static final String KEY_NO_PREMIUM_UI = "kamigram_no_premium_ui";

    // ------------------------------------------------------------- кэш
    /**
     * Защищать вручную скачанное. По умолчанию ВЫКЛЮЧЕНО: пользователь должен
     * иметь возможность чистить кэш как обычно. Включается галочкой в центре.
     */
    public static final String KEY_KEEP_DOWNLOADS = "kamigram_keep_downloads";

    // ------------------------------------------------------------- внешний вид
    /** iOS-дизайн: графит, скругления, плоская шапка. */
    public static final String KEY_IOS_DESIGN = "kamigram_ios_design";
    /** iOS-скругления облаков сообщений. */
    public static final String KEY_IOS_BUBBLES = "kamigram_ios_bubbles";
    /** Акцентный цвет: 0 — iOS-синий, 1 — бирюзовый, 2 — зелёный, 3 — оранжевый, 4 — красный, 5 — графит, 6 — розовый. */
    public static final String KEY_ACCENT = "kamigram_accent";
    /** Фон чатов: 0 — чёрный (AMOLED), 1 — графит, 2 — с узором Telegram. */
    public static final String KEY_CHAT_BACKGROUND = "kamigram_chat_background";
    /** Материал-дизайн 3 (2026): крупные карточки, мягкие скругления, MD3-строки. */
    public static final String KEY_MATERIAL3 = "kamigram_material3";
    /** Компактный список чатов (меньше высота строки). */
    public static final String KEY_COMPACT_CHATS = "kamigram_compact_chats";
    /** Шрифт сообщений крупнее на N (0 — как в Telegram). */
    public static final String KEY_FONT_BOOST = "kamigram_font_boost";
    /** Отправка сообщения по Enter. */
    public static final String KEY_ENTER_TO_SEND = "kamigram_enter_to_send";
    /** Тихая отправка (без звука). */
    public static final String KEY_SILENT_SEND = "kamigram_silent_send";

    // ------------------------------------------------------------- вход
    /** Всегда простой SMS-код вместо Google-аттестации. */
    public static final String KEY_FORCE_SMS = "kamigram_force_sms";
    /** Войти сразу: без лишних подтверждений и запросов разрешений. */
    public static final String KEY_FAST_LOGIN = "kamigram_fast_login";

    private KamiGramConfig() {
    }

    private static boolean get(String key, boolean fallback) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            return preferences == null || preferences.getBoolean(key, fallback);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private static int getInt(String key, int fallback) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            return preferences == null ? fallback : preferences.getInt(key, fallback);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    public static boolean value(String key) {
        return get(key, defaultValue(key));
    }

    /** Значение по умолчанию для каждого ключа. */
    public static boolean defaultValue(String key) {
        // выключено по умолчанию: то, что меняет обычное поведение Telegram
        if (KEY_KEEP_DOWNLOADS.equals(key) || KEY_NO_SCREENSHOTS.equals(key)
            || KEY_HIDE_NOTIFICATION_TEXT.equals(key) || KEY_SILENT_SEND.equals(key)
            || KEY_ENTER_TO_SEND.equals(key) || KEY_COMPACT_CHATS.equals(key)
            || KEY_TEXT_ONLY.equals(key)) {
            // «только текст» по умолчанию выключен: это самый жёсткий режим,
            // его включает пользователь сам, когда нужна максимальная экономия
            return false;
        }
        return true;
    }

    public static void set(String key, boolean value) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences != null) {
                preferences.edit().putBoolean(key, value).apply();
            }
        } catch (Throwable ignore) {
        }
        // выключатель KamiProxy должен не только сохраниться, но и сразу
        // применить себя: выключение снимает встроенный прокси, включение —
        // тут же подбирает лучший живой
        try {
            if (KEY_BUILTIN_PROXY.equals(key)) {
                KamiGramBuiltinProxy.onEnabledChanged(value);
            }
        } catch (Throwable ignore) {
        }
    }

    public static void setInt(String key, int value) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences != null) {
                preferences.edit().putInt(key, value).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    public static int intValue(String key, int fallback) {
        return getInt(key, fallback);
    }

    /** Сводка состояния для строки в настройках. */
    public static String summary() {
        return "прокси " + onOff(smartProxy())
            + " · стикеры " + onOff(!noStickers())
            + " · gif " + onOff(!noGifs())
            + " · истории " + onOff(!noStories())
            + " · призрак " + onOff(ghostMode())
            + " · тема " + accentName();
    }

    private static String onOff(boolean value) {
        return value ? "вкл" : "выкл";
    }

    // ------------------------------------------------------------------ геттеры

    public static boolean ghostMode() {
        return get(KEY_GHOST, true);
    }

    /** Тихая отправка при призраке. */
    public static boolean ghostSend() {
        return get(KEY_GHOST_SEND, true);
    }

    public static boolean storiesStealth() {
        return get(KEY_STORIES_STEALTH, true);
    }

    public static boolean noRestrictions() {
        return get(KEY_NO_RESTRICTIONS, true);
    }

    public static boolean showIds() {
        return get(KEY_SHOW_IDS, true);
    }

    /** Журнал удалённых сообщений. */
    public static boolean keepDeleted() {
        return get(KEY_KEEP_DELETED, true);
    }

    public static boolean noPermissionNags() {
        return get(KEY_NO_PERMISSION_NAGS, true);
    }

    public static boolean noScreenshots() {
        return get(KEY_NO_SCREENSHOTS, false);
    }

    public static boolean hideNotificationText() {
        return get(KEY_HIDE_NOTIFICATION_TEXT, false);
    }

    public static boolean smartProxy() {
        return get(KEY_SMART_PROXY, true);
    }

    public static boolean proxyFallback() {
        return get(KEY_PROXY_FALLBACK, true);
    }

    public static boolean autoProxyFromClipboard() {
        return get(KEY_AUTO_PROXY_CLIPBOARD, true);
    }

    public static boolean fastNet() {
        return get(KEY_FAST_NET, true);
    }

    /** Режим «только текст»: максимальная экономия трафика. */
    public static boolean textOnly() {
        return get(KEY_TEXT_ONLY, false);
    }

    /** Скрывать сообщения с метками рекламы. */
    public static boolean adsFilter() {
        return get(KEY_ADS_FILTER, true);
    }

    /** Встроенные прокси сборки (KamiProxy) с моментальным авто-роутингом. */
    public static boolean builtinProxy() {
        return get(KEY_BUILTIN_PROXY, true);
    }

    public static boolean noStickers() {
        return get(KEY_NO_STICKERS, true);
    }

    public static boolean noStories() {
        return get(KEY_NO_STORIES, true);
    }

    public static boolean noAnimatedEmoji() {
        return get(KEY_NO_ANIMATED_EMOJI, true);
    }

    public static boolean noGifs() {
        return get(KEY_NO_GIFS, true);
    }

    public static boolean noLinkPreview() {
        return get(KEY_NO_LINK_PREVIEW, true);
    }

    public static boolean noGifSearch() {
        return get(KEY_NO_GIF_SEARCH, true);
    }

    public static boolean noTopPeers() {
        return get(KEY_NO_TOP_PEERS, true);
    }

    public static boolean noAds() {
        return get(KEY_NO_ADS, true);
    }

    public static boolean noPremiumUi() {
        return get(KEY_NO_PREMIUM_UI, true);
    }

    public static boolean keepDownloads() {
        return get(KEY_KEEP_DOWNLOADS, false);
    }

    public static boolean iosDesign() {
        return get(KEY_IOS_DESIGN, true);
    }


    /** Совместимость с патчами сборки: iOS-таб-бар = iOS-дизайн. */
    public static boolean iosTabs() {
        return iosDesign();
    }

    /** Светлая тема принудительно выключена (иначе текст пропадает). */
    public static boolean forceDark() {
        return true;
    }

    public static boolean iosBubbles() {
        return get(KEY_IOS_BUBBLES, true);
    }

    public static boolean material3() {
        return get(KEY_MATERIAL3, true);
    }

    public static boolean compactChats() {
        return get(KEY_COMPACT_CHATS, false);
    }

    public static boolean enterToSend() {
        return get(KEY_ENTER_TO_SEND, false);
    }

    public static boolean silentSend() {
        return get(KEY_SILENT_SEND, false);
    }

    public static boolean forceSmsLogin() {
        return get(KEY_FORCE_SMS, true);
    }

    public static boolean fastLogin() {
        return get(KEY_FAST_LOGIN, true);
    }

    // ------------------------------------------------------------------ акцент

    private static final String[] ACCENT_NAMES = {
        "iOS-синий", "бирюзовый", "зелёный", "оранжевый", "красный", "графит", "розовый"
    };

    private static final int[] ACCENT_COLORS = {
        0xFF0A84FF, 0xFF32ADE6, 0xFF34C759, 0xFFFF9F0A, 0xFFFF453A, 0xFF8E8E93, 0xFFFF375F
    };

    public static int accentIndex() {
        return Math.max(0, Math.min(ACCENT_NAMES.length - 1, getInt(KEY_ACCENT, 0)));
    }

    public static int accentColor() {
        return ACCENT_COLORS[accentIndex()];
    }

    public static String accentName() {
        return ACCENT_NAMES[accentIndex()];
    }

    public static String accentNameAt(int index) {
        return ACCENT_NAMES[Math.max(0, Math.min(ACCENT_NAMES.length - 1, index))];
    }

    public static int accentCount() {
        return ACCENT_NAMES.length;
    }

    public static void setAccent(int index) {
        setInt(KEY_ACCENT, index);
    }

    // ------------------------------------------------------------------ фон чата

    public static int chatBackgroundIndex() {
        return Math.max(0, Math.min(2, getInt(KEY_CHAT_BACKGROUND, 0)));
    }

    public static String chatBackgroundName() {
        switch (chatBackgroundIndex()) {
            case 1:
                return "графит";
            case 2:
                return "узор Telegram";
            default:
                return "чёрный (AMOLED)";
        }
    }

    public static void setChatBackground(int index) {
        setInt(KEY_CHAT_BACKGROUND, index);
    }

    /** Насколько крупнее шрифт сообщений (0..4). */
    public static int fontBoost() {
        return Math.max(0, Math.min(4, getInt(KEY_FONT_BOOST, 0)));
    }

    /** Служебное: чтобы не спрашивать простой SMS-код дважды. */
    private static boolean forceSmsResent;

    public static boolean forceSmsConsumed() {
        return forceSmsResent;
    }

    public static void markForceSmsResent() {
        forceSmsResent = true;
    }
}
