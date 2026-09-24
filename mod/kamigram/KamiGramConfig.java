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
    /** Удалённые сообщения остаются в чате (как в AyuGram). */
    public static final String KEY_KEEP_DELETED = "kamigram_keep_deleted";
    /** Смотреть одноразовые и ограниченные по времени сообщения без пометки «просмотрено». */
    public static final String KEY_VIEW_ONCE = "kamigram_view_once";
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

    /** Плавные анимации и «стекло»: по умолчанию включены (красиво и плавно). */
    public static final String KEY_SMOOTH_ANIMATIONS = "kamigram_smooth_animations";

    /** Размытие интерфейса: дорого по ресурсам, но можно оставить — по умолчанию включено. */
    public static final String KEY_ALLOW_BLUR = "kamigram_allow_blur";
    /** Отправка сообщения по Enter. */
    public static final String KEY_ENTER_TO_SEND = "kamigram_enter_to_send";
    /** Тема Telegram: true — временно использовать оригинальную палитру Telegram. */
    public static final String KEY_TELEGRAM_THEME = "kamigram_telegram_theme";
    /** Тихая отправка (без звука). */
    public static final String KEY_SILENT_SEND = "kamigram_silent_send";

    // ------------------------------------------------------------- r68
    /**
     * Призрак + отправка через «Отложенные» (как в AyuGram).
     *
     * Сообщение уходит не в момент нажатия, а через несколько секунд (сервер
     * доставляет его по расписанию), поэтому по времени прихода сообщения
     * нельзя понять, когда мы реально были в сети.
     */
    public static final String KEY_AUTO_SCHEDULE = "kamigram_auto_schedule";
    /** Чаты со «100+» непрочитанных сами уходят в архив. */
    public static final String KEY_AUTO_ARCHIVE = "kamigram_auto_archive";

    // ------------------------------------------------------------- r70
    /** Применять настройки KamiGram ко всем аккаунтам (выкл = только текущий). */
    public static final String KEY_APPLY_ALL = "kamigram_apply_all";
    /** Отправлять фото/видео всегда в HD-качестве (4096, JPEG 99). */
    public static final String KEY_SEND_HD = "kamigram_send_hd";
    /** При пересылке всегда без имени отправителя. */
    public static final String KEY_FORWARD_NO_NAME = "kamigram_forward_no_name";
    /** Сгорающие и по таймеру можно пересылать. */
    public static final String KEY_FORWARD_EPHEMERAL = "kamigram_forward_ephemeral";
    /** Точечный буст: нажатое фото/файл качает первым, со всеми потоками. */
    public static final String KEY_NET_FOCUS = "kamigram_net_focus";

    // ------------------------------------------------------------- вход
    /** Всегда простой SMS-код вместо Google-аттестации. */
    public static final String KEY_FORCE_SMS = "kamigram_force_sms";
    /** Войти сразу: без лишних подтверждений и запросов разрешений. */
    public static final String KEY_FAST_LOGIN = "kamigram_fast_login";

    private KamiGramConfig() {
    }

    private static boolean get(String key, boolean fallback) {
        try {
            // r70: флаг «ко всем аккаунтам» сам читается из ГЛОБАЛЬНЫХ
            // настроек (это точка входа), остальные ключи — из того хранилища,
            // которое выбрано флагом.
            if (KEY_APPLY_ALL.equals(key)) {
                return applyToAllGlobal();
            }
            final SharedPreferences preferences = store();
            return preferences == null || preferences.getBoolean(key, fallback);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private static int getInt(String key, int fallback) {
        try {
            final SharedPreferences preferences = store();
            return preferences == null ? fallback : preferences.getInt(key, fallback);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    /** r70: включено ли «Применять KamiGram ко всем аккаунтам» (всегда гл. хранилище). */
    private static boolean applyToAllGlobal() {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            return preferences == null || preferences.getBoolean(KEY_APPLY_ALL, true);
        } catch (Throwable ignore) {
            return true;
        }
    }

    /** r70: где хранятся настройки мода: у всех аккаунтов вместе или у каждого. */
    private static SharedPreferences store() {
        try {
            if (applyToAllGlobal()) {
                return MessagesController.getGlobalMainSettings();
            }
            int account;
            try {
                account = org.telegram.messenger.UserConfig.selectedAccount;
            } catch (Throwable ignore) {
                account = 0;
            }
            return MessagesController.getMainSettings(account);
        } catch (Throwable ignore) {
            return MessagesController.getGlobalMainSettings();
        }
    }

    /** r70: перенос настроек текущего аккаунта в общее хранилище (без затирающего эффекта). */
    private static void migrateAccountToGlobal() {
        try {
            final SharedPreferences accountPrefs = MessagesController.getMainSettings(
                org.telegram.messenger.UserConfig.selectedAccount);
            final SharedPreferences globalPrefs = MessagesController.getGlobalMainSettings();
            if (accountPrefs == null || globalPrefs == null) {
                return;
            }
            android.content.SharedPreferences.Editor editor = null;
            for (java.util.Map.Entry<String, ?> entry : accountPrefs.getAll().entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith("kamigram_")
                    && !globalPrefs.contains(entry.getKey())) {
                    final Object val = entry.getValue();
                    if (val instanceof Boolean) {
                        if (editor == null) {
                            editor = globalPrefs.edit();
                        }
                        editor.putBoolean(entry.getKey(), (Boolean) val);
                    } else if (val instanceof Integer) {
                        if (editor == null) {
                            editor = globalPrefs.edit();
                        }
                        editor.putInt(entry.getKey(), (Integer) val);
                    }
                }
            }
            if (editor != null) {
                editor.apply();
            }
        } catch (Throwable ignore) {
        }
    }

    public static boolean value(String key) {
        return get(key, defaultValue(key));
    }

    /** Значение по умолчанию для каждого ключа. */
    public static boolean defaultValue(String key) {
        // Выключено по умолчанию — то, что меняет обычное поведение Telegram:
        //   * призрак (пользователь включает сам, когда нужно);
        //   * «только текст» (самый жёсткий режим экономии);
        //   * обычные фото, видео, аудио, голосовые, кружочки и документы
        //     всегда проходят native FileLoader;
        //   * единственные медиакатегории с ограничением — stickers, premium
        //     emoji и GIFs;
        //   * noStories не является media block и по умолчанию выключен.
        if (KEY_NO_STICKERS.equals(key) || KEY_NO_ANIMATED_EMOJI.equals(key)
            || KEY_NO_GIFS.equals(key)) {
            return true;
        }
        if (KEY_KEEP_DOWNLOADS.equals(key) || KEY_NO_SCREENSHOTS.equals(key)
            || KEY_HIDE_NOTIFICATION_TEXT.equals(key) || KEY_SILENT_SEND.equals(key)
            || KEY_ENTER_TO_SEND.equals(key) || KEY_COMPACT_CHATS.equals(key)
            || KEY_TELEGRAM_THEME.equals(key)
            || KEY_TEXT_ONLY.equals(key)
            || KEY_GHOST.equals(key) || KEY_GHOST_SEND.equals(key)
            || KEY_NO_PREMIUM_UI.equals(key) || KEY_NO_STORIES.equals(key)
            || KEY_FORWARD_NO_NAME.equals(key)) { // пересылка без имени — по желанию
            return false;
        }
        return true;
    }

    public static void set(String key, boolean value) {
        try {
            // r70: «ко всем аккаунтам» пишется только в глобальное хранилище —
            // это переключатель самого хранилища, а не обычная настройка.
            final SharedPreferences preferences = KEY_APPLY_ALL.equals(key)
                ? MessagesController.getGlobalMainSettings()
                : store();
            if (preferences != null) {
                preferences.edit().putBoolean(key, value).apply();
            }
            if (KEY_APPLY_ALL.equals(key)) {
                // при переводе на «все аккаунты» настройки текущего аккаунта
                // подтягиваются в общее хранилище (не затирая новые значения)
                if (value) {
                    migrateAccountToGlobal();
                }
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
            /* r76: числовые настройки должны использовать то же хранилище, что и
               boolean-настройки. Раньше setInt() всегда писал в global main settings,
               поэтому при режиме «отдельно для аккаунтов» фон/акцент/размер текста
               визуально сбрасывались после перезапуска. */
            final SharedPreferences preferences = store();
            if (preferences != null) {
                preferences.edit().putInt(key, value).commit();
            }
        } catch (Throwable ignore) {
        }
    }

    public static int intValue(String key, int fallback) {
        return getInt(key, fallback);
    }

    /**
     * Подпись строки в настройках. Коротко и без служебной информации:
     * пользователю не нужно видеть ID, вход и прочие технические детали.
     */
    public static String summary() {
        /* В подписи — только версия сборки: по ней видно, какая сборка стоит. */
        return KamiGramBuild.label();
    }

    // ------------------------------------------------------------------ геттеры

    public static boolean ghostMode() {
        return value(KEY_GHOST);
    }

    /** Тихая отправка при призраке. */
    public static boolean ghostSend() {
        return value(KEY_GHOST_SEND);
    }

    public static boolean storiesStealth() {
        return value(KEY_STORIES_STEALTH);
    }

    public static boolean noRestrictions() {
        return value(KEY_NO_RESTRICTIONS);
    }

    public static boolean showIds() {
        return value(KEY_SHOW_IDS);
    }

    /**
     * r80: retained key for settings/database compatibility, but native Telegram
     * deletion is always authoritative and the old keep-deleted behaviour is off.
     */
    public static boolean keepDeleted() {
        return false; /* KAMIGRAM_NATIVE_DELETE_R80 */
    }

    /** Одноразовые сообщения смотрим без пометки «просмотрено» (сервер не удаляет). */
    public static boolean viewOnce() {
        return value(KEY_VIEW_ONCE);
    }

    public static boolean noPermissionNags() {
        return value(KEY_NO_PERMISSION_NAGS);
    }

    public static boolean noScreenshots() {
        return value(KEY_NO_SCREENSHOTS);
    }

    public static boolean hideNotificationText() {
        return value(KEY_HIDE_NOTIFICATION_TEXT);
    }

    public static boolean smartProxy() {
        return value(KEY_SMART_PROXY);
    }

    public static boolean proxyFallback() {
        return value(KEY_PROXY_FALLBACK);
    }

    public static boolean autoProxyFromClipboard() {
        return value(KEY_AUTO_PROXY_CLIPBOARD);
    }

    public static boolean fastNet() {
        return value(KEY_FAST_NET);
    }

    /** Режим «только текст»: максимальная экономия трафика. */
    public static boolean textOnly() {
        return value(KEY_TEXT_ONLY);
    }

    /** Скрывать сообщения с метками рекламы. */
    public static boolean adsFilter() {
        return value(KEY_ADS_FILTER);
    }

    /** Встроенные прокси сборки (KamiProxy) с моментальным авто-роутингом. */
    public static boolean builtinProxy() {
        return value(KEY_BUILTIN_PROXY);
    }

    /**
     * r80: retained key for old preferences; normal sends never become
     * Scheduled messages, regardless of the stored legacy value.
     */
    public static boolean autoSchedule() {
        return false; /* KAMIGRAM_INSTANT_SEND_R80 */
    }

    /** Авто-архив чатов со «100+» непрочитанных (r68). */
    public static boolean autoArchive() {
        return value(KEY_AUTO_ARCHIVE);
    }

    /**
     * r68: не вычищать медиа «истёкших» (одноразовых и самоуничтожающихся) сообщений.
     *
     * Именно этот шаг Telegram превращал фото в пустышку — в чате появлялась
     * «истёкшая фотография», а само сообщение исчезало. Пользователь просил: фото
     * не должны исчезать, поэтому здесь всегда true (вернуть можно одной строкой).
     */
    public static boolean keepExpiredMedia() {
        return true;
    }

    public static boolean noStickers() {
        return value(KEY_NO_STICKERS);
    }

    public static boolean noStories() {
        return value(KEY_NO_STORIES);
    }

    public static boolean noAnimatedEmoji() {
        return value(KEY_NO_ANIMATED_EMOJI);
    }

    public static boolean noGifs() {
        return value(KEY_NO_GIFS);
    }

    public static boolean noLinkPreview() {
        return value(KEY_NO_LINK_PREVIEW);
    }

    public static boolean noGifSearch() {
        return value(KEY_NO_GIF_SEARCH);
    }

    public static boolean noTopPeers() {
        return value(KEY_NO_TOP_PEERS);
    }

    public static boolean noAds() {
        return value(KEY_NO_ADS);
    }

    public static boolean noPremiumUi() {
        return value(KEY_NO_PREMIUM_UI);
    }

    public static boolean keepDownloads() {
        return value(KEY_KEEP_DOWNLOADS);
    }

    public static boolean iosDesign() {
        return value(KEY_IOS_DESIGN);
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
        return value(KEY_IOS_BUBBLES);
    }

    public static boolean material3() {
        return get(KEY_MATERIAL3, true);
    }

    public static boolean compactChats() {
        return value(KEY_COMPACT_CHATS);
    }

    public static boolean enterToSend() {
        return value(KEY_ENTER_TO_SEND);
    }

    public static boolean silentSend() {
        return value(KEY_SILENT_SEND);
    }

    public static boolean forceSmsLogin() {
        return value(KEY_FORCE_SMS);
    }

    public static boolean fastLogin() {
        return value(KEY_FAST_LOGIN);
    }

    // ------------------------------------------------------------------ акцент

    /** Акценты: первый — фиолетовый Yoru (#C8A7FF), остальные в той же гамме. */
    private static final String[] ACCENT_NAMES = {
        "Yoru фиолетовый", "лаванда", "изумруд", "янтарь", "виноград", "графит", "роза"
    };

    private static final int[] ACCENT_COLORS = {
        0xFFC8A7FF, 0xFFA78BFA, 0xFF88E0A0, 0xFFFFCF70, 0xFF7C5CFF, 0xFFA99BB8, 0xFFFF8F9F
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

    /** Границы размера текста (sp) и значение по умолчанию — как в Telegram. */
    public static final int FONT_SIZE_MIN = 12;
    public static final int FONT_SIZE_MAX = 24;
    public static final int FONT_SIZE_DEFAULT = 16;

    /** Шаг полоски размера текста: 0 — самый мелкий (12sp), максимум — 24sp. */
    public static int fontBoost() {
        return Math.max(0, Math.min(FONT_SIZE_MAX - FONT_SIZE_MIN, getInt(KEY_FONT_BOOST, FONT_SIZE_DEFAULT - FONT_SIZE_MIN)));
    }

    /** Итоговый размер текста в sp. */
    public static int fontSize() {
        return FONT_SIZE_MIN + fontBoost();
    }

    /** Полоска в центре мода: тянешь — текст меньше или больше. */
    public static void setFontBoost(int boost) {
        setInt(KEY_FONT_BOOST, Math.max(0, Math.min(FONT_SIZE_MAX - FONT_SIZE_MIN, boost)));
    }

    /** Служебное: чтобы не спрашивать простой SMS-код дважды. */
    private static boolean forceSmsResent;

    public static boolean forceSmsConsumed() {
        return forceSmsResent;
    }

    public static void markForceSmsResent() {
        forceSmsResent = true;
    }

    // ------------------------------------------------------------------ r70

    /** Настройки KamiGram применяются ко всем аккаунтам (по умолчанию да). */
    public static boolean applyToAll() {
        return value(KEY_APPLY_ALL);
    }

    /** «Отправлять всегда HD»: фото/видео уходят в максимальном качестве. */
    public static boolean sendHd() {
        return value(KEY_SEND_HD);
    }

    /** «Пересылать без имени»: пересылки всегда без имени отправителя. */
    public static boolean forwardWithoutName() {
        return value(KEY_FORWARD_NO_NAME);
    }

    /** «Пересылать сгорающие»: одноразовые и по таймеру можно переслать. */
    public static boolean forwardEphemeral() {
        return value(KEY_FORWARD_EPHEMERAL);
    }

    /** «Точечный буст»: нажатое медиа качает первым и со всеми потоками. */
    public static boolean netFocus() {
        return value(KEY_NET_FOCUS);
    }

    /** Двусторонний переключатель «Тема Telegram», сохраняемый между запусками. */
    public static boolean telegramTheme() {
        return value(KEY_TELEGRAM_THEME);
    }
}
