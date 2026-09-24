package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * KamiGram: фильтр «нулевого трафика».
 *
 * Всё, что жрёт интернет впустую, отсекается ДО выхода в сеть - в одном месте:
 *   - стикеры, наборы эмодзи, premium emoji и GIF-файлы (только эти медиа);
 *   - реклама: спонсорские сообщения, промо, Telegram Premium, Stars, бусты;
 *   - рекомендации, «часто используемые», превью ссылок, телефонная книга;
 *   - в режиме «призрак» - подтверждения прочтения, «печатает» и статус «в сети»
 *     (кроме честной секунды онлайна сразу после отправки сообщения).
 *
 * ВАЖНО про имена: в исходниках Telegram часть запросов объявлена как
 * `TL_messages_getWebPage`, а часть - просто `getWebPage` внутри класса `TL_messages`.
 * Поэтому имя собирается из класса-владельца и простого имени, и проверяются оба варианта.
 */
public final class KamiGramNetFilter {

    /** Запросы, которые в режиме «призрак» не должны уходить на сервер вообще. */
    private static final String[] GHOST = {
        "TL_messages_readHistory",
        "TL_channels_readHistory",
        "TL_messages_readEncryptedHistory",
        "TL_messages_readMessageContents",
        "TL_messages_readMentions",
        "TL_messages_setTyping",
        "TL_account_updateStatus",
        "TL_stories_readStories",
        "TL_stories_incrementStoryViews"
    };

    /** Реклама, спонсорские сообщения и рекомендации - моду не нужны. */
    private static final String[] ADS = {
        "TL_messages_getSponsoredMessages",
        "TL_channels_getSponsoredMessages",
        "TL_channels_getChannelRecommendations",
        "TL_messages_getSuggestedDialogFilters",
        "TL_help_getPromoData",
        "TL_help_getPremiumPromo",
        "TL_contacts_getSponsoredPeers"
    };

    /** «Часто используемые» контакты и топ-пиры: лишний трафик и лишние данные о нас. */
    private static final String[] TOP_PEERS = {
        "TL_contacts_getTopPeers",
        "TL_contacts_toggleTopPeers",
        "TL_contacts_resetTopPeerRating",
        "TL_contacts_search"
    };

    /** Поиск GIF, стикеров и инлайн-ботов при вводе текста. */
    private static final String[] GIF_SEARCH = {
        "TL_messages_searchGifs",
        "TL_messages_searchStickers",
        "TL_messages_getInlineBotResults"
    };

    /** Превью ссылок и веб-страницы: мегабайты картинок на каждую ссылку. */
    private static final String[] LINK_PREVIEW = {
        "TL_messages_getWebPage",
        "TL_messages_getWebPagePreview",
        "TL_messages_getExtendedMedia",
        "TL_messages_getFactCheck"
    };

    /**
     * Чисто «телеграмовский» трафик, который моду не нужен: реклама Premium/Stars,
     * бусты, наборы GIF и музыки. Ничего полезного в этих запросах нет.
     */
    /**
     * «Премиум-украшения» Telegram: тяжёлые файлы, которые моду не нужны.
     * Стикеры и премиум-эмодзи здесь БОЛЬШЕ НЕ лежат: они нужны пользователю
     * (по умолчанию включены) и отключаются только переключателями в центре.
     */
    private static final String[] TRASH = {
        "TL_messages_getQuickReplies",
        "TL_contacts_getLocated",
        "TL_premium_getBoostsStatus",
        "TL_premium_getBoostsList",
        "TL_premium_getMyBoosts",
        "TL_payments_getStarsStatus",
        "TL_payments_getStarsTransactions"
    };

    /** Витрина Premium/Stars/TON — скрыта, пока пользователь не включит её сам. */
    private static final String[] PREMIUM = {
        "TL_help_getPremiumPromo",
        "TL_help_getPromoData",
        "TL_messages_getAvailableEffects",
        "TL_messages_getEmojiStatuses",
        "TL_account_getRecentEmojiStatuses"
    };

    private KamiGramNetFilter() {
    }

    public static boolean blockRequest(TLObject object) {
        if (object == null) {
            return false;
        }
        try {
            final String[] names = requestNames(object);
            if (names == null) {
                return false;
            }
            final String simple = names[0];
            final String full = names[1];
            /* Призрак здесь БОЛЬШЕ НЕ РАБОТАЕТ: с r54 он перехватывается в одной точке —
               KamiGramGhost.interceptRequest() в ConnectionsManager.sendRequestInternal.
               Прежняя схема («выбросить запрос по имени класса») не срабатывала и ломала
               локальные счётчики непрочитанного. */
            if (KamiGramConfig.noStickers() && isStickerRequest(full, simple)) {
                return deny();
            }
            if (KamiGramConfig.noAnimatedEmoji() && isEmojiRequest(full, simple)) {
                return deny();
            }
            if (KamiGramConfig.noPremiumUi() && (hit(PREMIUM, simple) || hit(PREMIUM, full))) {
                return deny();
            }
            /* KAMIGRAM_MEDIA_POLICY_R78: stories are ordinary Telegram media.
               Only sticker, premium-emoji, and GIF requests are media-blocked;
               story/video/audio/document requests must reach Telegram. */
            if (KamiGramConfig.noAds() && (hit(ADS, simple) || hit(ADS, full))) {
                return deny();
            }
            if (KamiGramConfig.noTopPeers() && (hit(TOP_PEERS, simple) || hit(TOP_PEERS, full))) {
                return deny();
            }
            if (KamiGramConfig.noGifSearch() && (hit(GIF_SEARCH, simple) || hit(GIF_SEARCH, full))) {
                return deny();
            }
            if (KamiGramConfig.noLinkPreview() && (hit(LINK_PREVIEW, simple) || hit(LINK_PREVIEW, full))) {
                return deny();
            }
            return (hit(TRASH, simple) || hit(TRASH, full)) && deny();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    /** Media policy switch: when enabled, sticker sets never enter the network. */
    public static boolean stickersBlocked() {
        try {
            return KamiGramConfig.value(KamiGramConfig.KEY_NO_STICKERS);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * Only sticker, premium-emoji, and GIF files are denied here. Story media
     * and every ordinary user-selected photo/video/audio/voice/document stay
     * on the native Telegram download path.
     */
    public static boolean blockDownload(TLRPC.Document document, Object parentObject) {
        try {
            /* KAMIGRAM_EPHEMERAL_DOWNLOAD_R77: never apply economy filters to
               a self-destructing message the user opened or selected. */
            if (parentObject instanceof MessageObject
                && ((MessageObject) parentObject).messageOwner != null
                && KamiGramGhost.isEphemeralMedia(((MessageObject) parentObject).messageOwner)) {
                return false;
            }
            /* KAMIGRAM_MEDIA_POLICY_R78: story media is not a blocked category. */
            if (KamiGramConfig.noStickers()
                && (isStickerDocument(document) || isStickerMessage(parentObject))) {
                return denyFile(document);
            }
            if (KamiGramConfig.noGifs()
                && (isGifDocument(document) || isGifMessage(parentObject))) {
                return denyFile(document);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    /** Отсекли запрос: считаем это для счётчика экономии трафика. */
    private static boolean deny() {
        try {
            KamiGramTraffic.blockedRequest();
        } catch (Throwable ignore) {
        }
        return true;
    }

    /** Отсекли файл: помним и размер (экономия трафика в байтах). */
    private static boolean denyFile(TLRPC.Document document) {
        try {
            long size = 0;
            if (document != null && document.size > 0) {
                size = document.size;
            }
            KamiGramTraffic.blockedBytes(size);
        } catch (Throwable ignore) {
        }
        return true;
    }

    /** [простое имя, имя с пространством: TL_messages_getWebPage]. */
    private static String[] requestNames(TLObject object) {
        final Class<?> cls = object.getClass();
        final String simple = cls.getSimpleName();
        if (simple == null || simple.length() == 0) {
            return null;
        }
        if (simple.indexOf('_') > 0) {
            // уже с пространством имён: TL_messages_getWebPage
            return new String[] {simple, simple};
        }
        final Class<?> enclosing = cls.getEnclosingClass();
        final String full = enclosing == null ? simple : enclosing.getSimpleName() + "_" + simple;
        return new String[] {simple, full};
    }

    private static boolean hit(String[] list, String name) {
        for (int a = 0; a < list.length; a++) {
            if (list[a].equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Запросы про стикеры, наборы эмодзи и премиум-эмодзи. */
    private static boolean isStickerRequest(String full, String simple) {
        if (!full.startsWith("TL_messages_") && !simple.startsWith("TL_messages_")) {
            return false;
        }
        final String rest = full.startsWith("TL_messages_") ? full.substring("TL_messages_".length()) : simple;
        if (rest.contains("EmojiKeywords")) {
            // подсказки клавиатуры эмодзи оставляем: они крошечные и нужны для поиска
            return false;
        }
        /* KAMIGRAM_MEDIA_POLICY_R81: ordinary emoji keywords/status text are
           not a blocked media class. Sticker requests and explicitly animated
           or custom-emoji sets are the only emoji-related network requests
           eligible for this economy switch. */
        return rest.contains("Sticker")
            || rest.contains("CustomEmoji")
            || rest.contains("AnimatedEmoji");
    }

    /** Премиум-эмодзи (наборы и стикеры-эмодзи) — отключаются своим переключателем. */
    private static boolean isEmojiRequest(String full, String simple) {
        if (!full.startsWith("TL_messages_") && !simple.startsWith("TL_messages_")) {
            return false;
        }
        return full.contains("CustomEmoji") || simple.contains("CustomEmoji")
            || full.contains("AnimatedEmoji") || simple.contains("AnimatedEmoji")
            || full.contains("EmojiSticker") || simple.contains("EmojiSticker")
            || full.contains("EmojiStatus") || simple.contains("EmojiStatus");
    }

    /**
     * GIF only. Do not infer GIF from the MIME type: Telegram uses video/mp4
     * for ordinary videos, round videos, and GIFs alike. The animated document
     * attribute is the authoritative discriminator used by Telegram itself.
     */
    private static boolean isGifDocument(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        try {
            return MessageObject.isGifDocument(document);
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isStickerMessage(Object parentObject) {
        if (!(parentObject instanceof MessageObject)) {
            return false;
        }
        try {
            final MessageObject message = (MessageObject) parentObject;
            return message.isSticker() || message.isAnimatedEmoji();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isGifMessage(Object parentObject) {
        if (!(parentObject instanceof MessageObject)) {
            return false;
        }
        try {
            return ((MessageObject) parentObject).isGif();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isStickerDocument(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        if ("application/x-tgsticker".equals(document.mime_type)) {
            return true;
        }
        if (document.attributes != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                final TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker
                    || attribute instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                    return true;
                }
            }
        }
        return false;
    }
}
