package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;

/**
 * KamiGram: фильтр «нулевого трафика».
 *
 * Всё, что жрёт интернет впустую, отсекается ДО выхода в сеть - в одном месте:
 *   - стикеры, наборы эмодзи и премиум-эмодзи (запросы и сами файлы .tgs/.webm);
 *   - истории: списки, просмотры и медиа;
 *   - рекламные блоки Telegram Premium;
 *   - в режиме «призрак» - подтверждения прочтения, «печатает» и статус «в сети».
 *
 * Каждый пункт включается своим переключателем в настройках мода (KamiGramConfig),
 * по умолчанию все включены.
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

    /**
     * Чисто «телеграмовский» трафик, который моду не нужен: реклама Premium/Stars,
     * бусты, цветовые палитры, наборы GIF и музыки. Ничего полезного в этих
     * запросах нет - только мегабайты и лишние данные о пользователе.
     */
    private static final String[] TRASH = {
        "TL_help_getPremiumPromo",
        "TL_help_getPromoData",
        "TL_help_getPeerColors",
        "TL_help_getPeerProfileColors",
        "TL_messages_getEmojiGameInfo",
        "TL_messages_getSavedGifs",
        "TL_messages_getSavedMusic",
        "TL_messages_getSavedReactionTags",
        "TL_premium_getBoostsStatus",
        "TL_premium_getBoostsList",
        "TL_premium_getMyBoosts",
        "TL_payments_getStarsStatus",
        "TL_payments_getStarsTransactions",
        "TL_payments_getStarsSubscriptions",
        "TL_messages_getSponsoredMessages",
        "TL_channels_getSponsoredMessages",
        "TL_channels_getChannelRecommendations",
        "TL_messages_getSuggestedDialogFilters",
        "TL_messages_getRecentReactions",
        "TL_messages_searchGifs",
        "TL_messages_searchStickers",
        "TL_messages_getInlineBotResults"
    };

    /** Реклама, спонсорские сообщения и рекомендации - моду не нужны. */
    private static final String[] ADS = {
        "TL_messages_getSponsoredMessages",
        "TL_channels_getSponsoredMessages",
        "TL_channels_getChannelRecommendations",
        "TL_messages_getSuggestedDialogFilters",
        "TL_help_getPromoData",
        "TL_help_getPremiumPromo"
    };

    /** «Часто используемые» контакты и топ-пиры: лишний трафик и лишние данные о нас. */
    private static final String[] TOP_PEERS = {
        "TL_contacts_getTopPeers",
        "TL_contacts_toggleTopPeers",
        "TL_contacts_getLocated",
        "TL_contacts_getContactIDs"
    };

    /** Поиск GIF/стикеров/инлайн-ботов при вводе текста. */
    private static final String[] GIF_SEARCH = {
        "TL_messages_searchGifs",
        "TL_messages_searchStickers",
        "TL_messages_getInlineBotResults"
    };

    /** Исходящие действия пользователя по историям - их не блокируем даже при запрете историй. */
    private static final String[] STORY_ACTIONS = {
        "TL_stories_sendStory",
        "TL_stories_deleteStories",
        "TL_stories_editStory",
        "TL_stories_sendReaction",
        "TL_stories_report",
        "TL_stories_activateStealthMode",
        "TL_stories_togglePeerStoriesHidden",
        "TL_stories_exportStoryLink"
    };

    private KamiGramNetFilter() {
    }

    public static boolean blockRequest(TLObject object) {
        if (object == null) {
            return false;
        }
        try {
            final String name = object.getClass().getSimpleName();
            if (name == null || name.length() == 0) {
                return false;
            }
            if (KamiGramConfig.ghostMode() && matches(GHOST, name)) {
                return true;
            }
            if (KamiGramConfig.noStickers() && isStickerRequest(name)) {
                return true;
            }
            if (KamiGramConfig.noStories() && isStoryRequest(name)) {
                return true;
            }
            if (KamiGramConfig.noAds() && matches(ADS, name)) {
                return true;
            }
            if (KamiGramConfig.noTopPeers() && matches(TOP_PEERS, name)) {
                return true;
            }
            if (KamiGramConfig.noGifSearch() && matches(GIF_SEARCH, name)) {
                return true;
            }
            if (matches(TRASH, name)) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    /**
     * Файлы стикеров и премиум-эмодзи (.tgs / .webm / .webp) и медиа историй
     * не скачиваются: экономия трафика и батареи.
     */
    public static boolean blockDownload(TLRPC.Document document, Object parentObject) {
        try {
            if (KamiGramConfig.noStories() && parentObject instanceof TL_stories.StoryItem) {
                return true;
            }
            if (KamiGramConfig.noStickers() && isStickerDocument(document)) {
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    private static boolean matches(String[] list, String name) {
        for (int a = 0; a < list.length; a++) {
            if (list[a].equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Запросы про стикеры, наборы эмодзи и премиум-эмодзи. */
    private static boolean isStickerRequest(String name) {
        if (name.startsWith("TL_messages_")) {
            final String rest = name.substring("TL_messages_".length());
            if (rest.contains("EmojiKeywords")) {
                // подсказки клавиатуры эмодзи оставляем: они крошечные и нужны для поиска
                return false;
            }
            if (rest.contains("Sticker") || rest.contains("Emoji")) {
                return true;
            }
        }
        return false;
    }

    /** Запросы про истории (все, кроме исходящих действий самого пользователя). */
    private static boolean isStoryRequest(String name) {
        if (!name.startsWith("TL_stories_")) {
            return false;
        }
        return !matches(STORY_ACTIONS, name);
    }

    /** Документ - стикер или премиум-эмодзи. */
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
