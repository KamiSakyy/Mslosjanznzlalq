package org.telegram.messenger.kamigram;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/**
 * KamiGram: режим «ТОЛЬКО ТЕКСТ» — максимальная экономия трафика.
 *
 * Когда режим включён, приложение не тратит ни байта на картинки:
 *   * аватарки, превью фото, GIF-превью и стикеры не загружаются вообще;
 *   * фото, видео, музыка, документы и голосовые не скачиваются сами;
 *   * в списке чатов вместо аватарок — цветные кружки с буквой (их рисует
 *     Telegram, трафика ноль);
 *   * как только пользователь сам нажимает на фото/видео/голосовое, загрузка
 *     разрешается (окно 30 секунд) — то есть «медиа только при нажатии».
 *
 * Открыть текст чата, писать сообщения и читать всё можно без ограничений.
 */
public final class KamiGramTextOnly {

    private KamiGramTextOnly() {
    }

    public static boolean enabled() {
        return KamiGramConfig.textOnly();
    }

    /** Можно ли сейчас качать этот файл/картинку. false = блокируем. */
    public static boolean allow(Object parentObject, String mime, long size) {
        try {
            if (!enabled()) {
                return true;
            }
            if (KamiGramUi.isManual()) {
                return true; // пользователь сам открыл медиа
            }
            return false;
        } catch (Throwable ignore) {
            return true;
        }
    }

    /** Блокируем ли загрузку картинки (фото, аватар, превью, стикер). */
    public static boolean blockImage(Object parentObject) {
        try {
            if (!enabled() || KamiGramUi.isManual()) {
                return false;
            }
            // обои чата качает сам Telegram отдельно; тут только картинки в интерфейсе
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Блокируем ли загрузку файла (видео, аудио, документ, стикер). */
    public static boolean blockDocument(TLRPC.Document document, Object parentObject, long size) {
        try {
            if (!enabled() || KamiGramUi.isManual()) {
                return false;
            }
            if (document == null) {
                return false;
            }
            if (size > 0) {
                // считаем сэкономленный трафик
                KamiGramTraffic.blockedBytes(size);
            } else {
                KamiGramTraffic.blockedRequest();
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
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
