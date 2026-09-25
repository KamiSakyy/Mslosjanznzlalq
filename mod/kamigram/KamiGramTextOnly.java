package org.telegram.messenger.kamigram;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/**
 * Sakura: режим «ТОЛЬКО ТЕКСТ» — максимальная экономия трафика.
 *
 * r78 keeps this compatibility class because older patch points still call it,
 * but it no longer blocks a user-visible media request. Telegram's native
 * DownloadController still controls background/autodownload; a tap reaches
 * FileLoader immediately. Sticker, premium-emoji, and GIF denial is centralized
 * in KamiGramNetFilter so ordinary video/audio/voice/files cannot be caught by
 * a broad "text only" predicate.
 */
public final class KamiGramTextOnly {

    private KamiGramTextOnly() {
    }

    public static boolean enabled() {
        return KamiGramConfig.textOnly();
    }

    /**
     * Media policy r78: the old text-only switch must never veto a normal
     * Telegram media request. Autodownload is controlled by Telegram's own
     * DownloadController presets; once the user taps, FileLoader is allowed to
     * start immediately.
     */
    public static boolean allow(Object parentObject, String mime, long size) {
        return true;
    }

    /**
     * Images are not a text-only exception anymore. Sticker and GIF documents
     * are filtered centrally by KamiGramNetFilter, while ordinary photos,
     * avatars, previews, and round-media thumbnails stay native Telegram.
     */
    public static boolean blockImage(Object parentObject) {
        return false; /* KAMIGRAM_MEDIA_POLICY_R78 */
    }

    /**
     * The former text-only gate blocked video, audio, voice, and documents even
     * after a user tapped them. r78 leaves category filtering to NetFilter so
     * ordinary files always enter FileLoader; only sticker/premium-emoji/GIF
     * documents may be denied there.
     */
    public static boolean blockDocument(TLRPC.Document document, Object parentObject, long size) {
        return false; /* KAMIGRAM_MEDIA_POLICY_R78 */
    }

    /** Является ли сообщение медиа-сообщением (для подсказки в чате). */
    public static boolean isMedia(MessageObject message) {
        try {
            return message != null && (message.isPhoto() || message.isVideo() || message.isVoice()
                || message.isMusic() || message.isSticker() || message.isGif() || message.isDocument());
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static String describe() {
        if (!enabled()) {
            return "режим «только текст» выключен";
        }
        return "трафик только на текст · медиа — по нажатию · сэкономлено "
            + KamiGramCache.human(KamiGramTraffic.saved());
    }
}
