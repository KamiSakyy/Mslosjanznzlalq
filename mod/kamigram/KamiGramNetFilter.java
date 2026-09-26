package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/**
 * Sakura: фильтр «нулевого трафика».
 *
 * Всё, что жрёт интернет впустую, отсекается ДО выхода в сеть - в одном месте:
 *   - стикеры, премиум-эмодзи и истории — единственные категории, которые по
 *     умолчанию НЕ загружаются (r101); каждая отключается своим тумблером;
 *   - аватары, обои, фото, видео, документы, аудио и GIF идут штатно;
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

    /**
     * «Часто используемые» контакты и топ-пиры: лишний трафик и лишние данные о нас.
     * KAMIGRAM_SEARCH_NO_LIMITS_R101: TL_contacts_search здесь БОЛЬШЕ НЕТ — это
     * запрос глобального поиска людей/каналов/групп, и именно из-за него
     * «глобальный поиск не работал, искал только по своим».
     */
    private static final String[] TOP_PEERS = {
        "TL_contacts_getTopPeers",
        "TL_contacts_toggleTopPeers",
        "TL_contacts_resetTopPeerRating"
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

    /** Premium/Stars/boosts-запросы: отсекаются только вместе с витриной Premium. */
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

    /**
     * KAMIGRAM_STORIES_TOGGLE_R101: истории — отдельный тумблер, не связанный со
     * стикерами и премиум-эмодзи. По умолчанию истории НЕ загружаются.
     * Здесь только читающие запросы ленты/архива: публикация, редактирование,
     * удаление своей истории и выбор чата для публикации не блокируются
     * никогда — иначе нельзя было бы выложить свою историю.
     */
    private static final String[] STORIES = {
        "TL_stories_getAllStories",
        "TL_stories_getStories",
        "TL_stories_getPeerStories",
        "TL_stories_getStoriesByID",
        "TL_stories_getPeerMaxIDs",
        "TL_stories_getPinnedStories",
        "TL_stories_getArchive",
        "TL_stories_getForyouFeed",
        "TL_stories_getStoryViewsList",
        "TL_stories_getStoryViewers",
        "TL_stories_getStoryReactionsList",
        "TL_stories_getPublicForwards",
        "TL_stories_getOutboxReadDate"
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

    /**
     * KAMIGRAM_PUSH_SAFE_R83: Telegram's FCM/background path is always native.
     * These requests register the device or fetch the update/dialog delta after
     * a push wakes the process. They must not be swallowed by optional traffic
     * filters, even when a user has enabled the economy or Ghost switches.
     */
    public static boolean isPushCriticalRequest(TLObject object) {
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
            final String[] safe = {
                "TL_account_registerDevice",
                "TL_account_unregisterDevice",
                "TL_account_updateDeviceLocked",
                "TL_account_getNotifySettings",
                "TL_account_getNotifyExceptions",
                "TL_account_updateNotifySettings",
                "TL_updates_getDifference",
                "TL_updates_getState",
                "TL_updates_getChannelDifference",
                "TL_messages_getDifference",
                "TL_messages_getDialogs",
                "TL_messages_getPeerDialogs",
                "TL_messages_getMessages",
                "TL_messages_getHistory",
                "TL_messages_getPinnedDialogs",
                "TL_messages_getUnreadMentions",
                "TL_messages_getUnreadReactions",
                "TL_messages_getScheduledHistory"
            };
            return hit(safe, simple) || hit(safe, full);
        } catch (Throwable ignore) {
            return false;
        }
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
            /* KAMIGRAM_SEARCH_NO_LIMITS_R101: поиск не блокируется НИЧЕМ —
               ни тумблерами экономии, ни стикерами, ни премиум-эмодзи, ни
               историями. Глобальный поиск всегда имеет приоритет и работает
               с первого введённого символа. */
            if (isSearchRequest(full, simple)) {
                return false;
            }
            if (KamiGramConfig.noStickers() && isStickerRequest(full, simple)) {
                return deny();
            }
            if (KamiGramConfig.noAnimatedEmoji() && isEmojiRequest(full, simple)) {
                return deny();
            }
            if (KamiGramConfig.noPremiumUi() && (hit(PREMIUM, simple) || hit(PREMIUM, full))) {
                return deny();
            }
            /* KAMIGRAM_STORIES_TOGGLE_R101: истории отключаются ОТДЕЛЬНЫМ
               тумблером (по умолчанию включён). Всё остальное — фото, видео,
               документы, аудио, аватары, обои, GIF — проходит штатно. */
            if (KamiGramConfig.noStories() && (hit(STORIES, simple) || hit(STORIES, full))) {
                return deny();
            }
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
            /* KAMIGRAM_NOTHING_BLOCKED_R101: «мусорные» Premium/Stars/boosts
               запросы отсекаются ТОЛЬКО вместе с витриной Premium (её тумблер по
               умолчанию выключен). По умолчанию не блокируется ничего, кроме
               стикеров, премиум-эмодзи и историй — как просил пользователь. */
            if (KamiGramConfig.noPremiumUi() && (hit(TRASH, simple) || hit(TRASH, full))) {
                return deny();
            }
            return false;
        } catch (Throwable e) {
            KamiGramLog.e(e);
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
            /* KAMIGRAM_STICKER_DOC_DENY_R116: стикеры и премиум-эмодзи не
               скачиваются НИ ОТКУДА — панель стикеров, «недавние», подсказки
               при вводе и панель эмодзи грузят документ с parentObject =
               сам документ (не MessageObject) и раньше обходили фильтр.
               Аватары не страдают: стикер-атрибут у аватаров не встречается,
               а эмодзи-статусы и видео-аватары несут атрибут видео вместе
               с CustomEmoji и исключены проверкой isPremiumEmojiDocument. */
            if (document != null) {
                final boolean stickerFile = isStickerFile(document);
                final boolean emojiFile = isPremiumEmojiDocument(document);
                if ((KamiGramConfig.noStickers() && (stickerFile || emojiFile))
                    || (KamiGramConfig.noAnimatedEmoji() && emojiFile)) {
                    return denyFile(document);
                }
            }
            /* KAMIGRAM_MEDIA_POLICY_R78 → KAMIGRAM_AVATAR_SAFE_R101:
               фильтры медиа применяются ТОЛЬКО к
               сообщениям чата. Аватары, видео-аватары, премиум/эмодзи-аватары,
               фотообои, обои-видео и медиа профиля приходят с другим
               parentObject (не MessageObject) и никогда не блокируются —
               иначе «аватарки не грузятся». */
            if (!(parentObject instanceof MessageObject)) {
                return false;
            }
            if (KamiGramConfig.noStickers() && isStickerMessage(parentObject)) {
                return denyFile(document);
            }
            if (KamiGramConfig.noGifs() && isGifMessage(parentObject)) {
                return denyFile(document);
            }
        } catch (Throwable e) {
            KamiGramLog.e(e);
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

    /**
     * r114: миниатюры стикеров и премиум-эмодзи, которые качаются через
     * loadFile(ImageLocation, parentObject, ...) БЕЗ передачи документа —
     * панель стикеров, «недавние», подсказки при вводе, подарочные стикеры.
     * Документ в этом пути равен null, поэтому определяется по родителю:
     * набор стикеров, сообщение-стикер или сам документ-стикер.
     */
    public static boolean blockThumb(Object parentObject) {
        try {
            final boolean noStickers = KamiGramConfig.noStickers();
            final boolean noEmoji = KamiGramConfig.noAnimatedEmoji();
            if (!noStickers && !noEmoji) {
                return false;
            }
            /* KAMIGRAM_STICKER_DOC_DENY_R116: превью премиум-эмодзи
               (videoThumbs) грузятся с parentObject = документ — теперь
               отсекаются вместе со стикерными миниатюрами. */
            if (parentObject instanceof TLRPC.Document) {
                final TLRPC.Document doc = (TLRPC.Document) parentObject;
                final boolean emojiFile = isPremiumEmojiDocument(doc);
                return (noStickers && (isStickerFile(doc) || emojiFile))
                    || (noEmoji && emojiFile);
            }
            if (parentObject instanceof MessageObject) {
                final MessageObject messageObject = (MessageObject) parentObject;
                if (noStickers && messageObject.isSticker()) {
                    return true;
                }
                final TLRPC.Document document = messageObject.getDocument();
                if (document == null) {
                    return false;
                }
                return (noStickers && isStickerDocument(document))
                    || (noEmoji && isPremiumEmojiDocument(document));
            }
            return noStickers
                && (parentObject instanceof TLRPC.TL_stickerSet
                    || parentObject instanceof TLRPC.TL_messages_stickerSet
                    || parentObject instanceof TLRPC.StickerSetCovered);
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
        return false;
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

    /**
     * Любой поисковый запрос: глобальный поиск людей/каналов (contacts.search),
     * поиск сообщений и постов (messages.search / searchGlobal), поиск по
     * участникам канала, подбор эмодзи/GIF/стикеров по запросу и резолв
     * @username. Ни один из них не может быть отсеян фильтрами экономии.
     */
    private static boolean isSearchRequest(String full, String simple) {
        if (full != null && (full.contains("search") || full.contains("Search"))) {
            return true;
        }
        if (simple != null && (simple.contains("search") || simple.contains("Search"))) {
            return true;
        }
        if (full == null) {
            return false;
        }
        return full.endsWith("resolveUsername") || full.endsWith("getParticipants")
            || full.endsWith("getEmojiKeywords") || full.endsWith("getSearchResultsCalendar")
            || full.endsWith("getSearchCounters");
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

    /** r116: чистый стикер-файл (.tgs/.webm-стикер, атрибут Sticker). */
    private static boolean isStickerFile(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        if ("application/x-tgsticker".equals(document.mime_type)) {
            return true;
        }
        if (document.attributes != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                if (document.attributes.get(a) instanceof TLRPC.TL_documentAttributeSticker) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * r116: документ премиум-эмодзи (атрибут CustomEmoji) без видео-атрибута —
     * видео-атрибут означает эмодзи-статус/видео-аватар, они неприкосновенны
     * (KAMIGRAM_AVATAR_SAFE_R101).
     */
    private static boolean isPremiumEmojiDocument(TLRPC.Document document) {
        if (document == null || document.attributes == null) {
            return false;
        }
        boolean emoji = false;
        boolean video = false;
        for (int a = 0; a < document.attributes.size(); a++) {
            final TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                emoji = true;
            } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                video = true;
            }
        }
        return emoji && !video;
    }

    /**
     * r117: заблокированный стикер/премиум-эмодзи не просто не скачивается —
     * если файл уже лежал в кэше (скачан до включения блокировки), он сразу
     * удаляется, поэтому стикеры исчезают и на устройствах, где они уже были
     * загружены. Касается только стикерных/эмодзи-файлов (вызов стоит внутри
     * сработавшего гейта), никогда — пользовательских медиа. Temp/parts и
     * resume не трогаются: операция загрузки для этого файла не создавалась
     * (гейт срабатывает до очереди). KAMIGRAM_STICKER_PURGE_R117
     */
    public static void purgeCached(TLRPC.Document document,
                                   TLRPC.TL_fileLocationToBeDeprecated location,
                                   String locationExt) {
        try {
            String fileName = null;
            if (location != null) {
                fileName = FileLoader.getAttachFileName(location, locationExt);
            } else if (document != null) {
                fileName = FileLoader.getAttachFileName(document);
            }
            if (fileName == null || fileName.isEmpty()) {
                return;
            }
            final File cacheFile = new File(
                FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            if (cacheFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                cacheFile.delete();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }
}
