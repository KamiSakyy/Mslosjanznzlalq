package org.telegram.messenger.kamigram;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Sakura: режим «ТОЛЬКО ТЕКСТ» — максимальная экономия трафика.
 *
 * KAMIGRAM_TEXT_ONLY_GATE_R123: пока режим включён, не загружается НИЧЕГО
 * медиа-подобного — фото, аватарки, стикеры, видео, голосовые, файлы, GIF,
 * превью ссылок (ноль трафика, в ленте только текст). Исключение одно и
 * явное: пользователь нажал на фото/видео — этот конкретный медиа-файл
 * попадает в разрешающий список и загружается (далее читается из кэша).
 *
 * Гейт стоит в общей воронке FileLoader до сети, поэтому заблокированные
 * запросы не тратят ни байта. Разрешающие отметки ставятся на входах
 * PhotoViewer (KAMIGRAM_TEXT_ONLY_TAP_R123) — там, где нажатие становится
 * просмотром.
 */
public final class KamiGramTextOnly {

    private static final int MAX_ALLOWED = 4000;

    private static final Set<String> ALLOWED =
        Collections.synchronizedSet(new HashSet<String>());

    private KamiGramTextOnly() {
    }

    public static boolean enabled() {
        try {
            return KamiGramConfig.textOnly();
        } catch (Throwable ignore) {
            return false;
        }
    }

    // ------------------------------------------------------------------ гейт

    /**True — загрузку этого медиа-запроса нужно отменить до выхода в сеть.*/
    public static boolean blocks(TLRPC.Document document,
                                 TLRPC.SecureDocument secureDocument,
                                 TLRPC.WebFile webDocument,
                                 TLRPC.TL_fileLocationToBeDeprecated location,
                                 ImageLocation imageLocation) {
        if (!enabled()) {
            return false;
        }
        try {
            // паспортные документы вне режима: они всегда открываются явно
            if (secureDocument != null) {
                return false;
            }
            if (document != null) {
                return !ALLOWED.contains(docKey(document.id));
            }
            if (location != null) {
                return !ALLOWED.contains(locKey(location));
            }
            if (imageLocation != null) {
                if (imageLocation.document != null) {
                    return !ALLOWED.contains(docKey(imageLocation.document.id));
                }
                if (imageLocation.location != null) {
                    return !ALLOWED.contains(locKey(imageLocation.location));
                }
                if (imageLocation.webFile != null) {
                    return !ALLOWED.contains(webKey(imageLocation.webFile));
                }
                return false;
            }
            if (webDocument != null) {
                return !ALLOWED.contains(webKey(webDocument));
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        // запрос без медиа-части (текст, служебное) режим не трогает
        return false;
    }

    // --------------------------------------------------- отметки по нажатию

    /** Нажатие на сообщение с медиа: разрешаем всё медиа этого сообщения. */
    public static void allowMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        try {
            final TLRPC.MessageMedia media = messageObject.messageOwner.media;
            if (media == null) {
                return;
            }
            if (media.photo != null) {
                allowPhoto(media.photo);
            }
            if (media.document != null) {
                allowDocument(media.document);
            }
            if (media.webpage != null) {
                if (media.webpage.photo != null) {
                    allowPhoto(media.webpage.photo);
                }
                if (media.webpage.document != null) {
                    allowDocument(media.webpage.document);
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Нажатие открывает список сообщений (галерея) — разрешаем все. */
    public static void allowMessages(java.util.ArrayList<MessageObject> messages) {
        if (messages == null) {
            return;
        }
        try {
            for (int a = 0; a < messages.size(); a++) {
                allowMessage(messages.get(a));
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Нажатие на отдельную точку изображения (аватар, обои, превью). */
    public static void allowLocation(TLRPC.FileLocation fileLocation) {
        if (fileLocation instanceof TLRPC.TL_fileLocationToBeDeprecated) {
            add(locKey((TLRPC.TL_fileLocationToBeDeprecated) fileLocation));
        }
    }

    public static void allowImageLocation(ImageLocation imageLocation) {
        if (imageLocation == null) {
            return;
        }
        try {
            if (imageLocation.document != null) {
                allowDocument(imageLocation.document);
            }
            if (imageLocation.location != null) {
                add(locKey(imageLocation.location));
            }
            if (imageLocation.webFile != null) {
                add(webKey(imageLocation.webFile));
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    // ------------------------------------------------------------ служебное

    private static void allowPhoto(TLRPC.Photo photo) {
        if (photo == null) {
            return;
        }
        for (int a = 0; a < photo.sizes.size(); a++) {
            final TLRPC.PhotoSize size = photo.sizes.get(a);
            if (size != null && size.location instanceof TLRPC.TL_fileLocationToBeDeprecated) {
                add(locKey((TLRPC.TL_fileLocationToBeDeprecated) size.location));
            }
        }
    }

    private static void allowDocument(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        add(docKey(document.id));
        for (int a = 0; a < document.thumbs.size(); a++) {
            final TLRPC.PhotoSize size = document.thumbs.get(a);
            if (size != null && size.location instanceof TLRPC.TL_fileLocationToBeDeprecated) {
                add(locKey((TLRPC.TL_fileLocationToBeDeprecated) size.location));
            }
        }
    }

    private static void add(String key) {
        synchronized (ALLOWED) {
            if (ALLOWED.size() > MAX_ALLOWED) {
                ALLOWED.clear();
            }
            ALLOWED.add(key);
        }
    }

    private static String docKey(long id) {
        return "d" + id;
    }

    private static String locKey(TLRPC.TL_fileLocationToBeDeprecated location) {
        return "l" + location.volume_id + "_" + location.local_id;
    }

    private static String webKey(TLRPC.WebFile webFile) {
        return "w" + webFile.url + "_" + webFile.size;
    }

    /**
     * Совместимость с r78 (KAMIGRAM_MEDIA_POLICY_R78): старые точки патча не
     * veto-ят обычные запросы. Но образы (ImageLocation) в режиме «только
     * текст» блокируются до сети, если не разрешены нажатием — этот метод
     * зовёт ранний образ-вход воронки FileLoader (r77 гейт с исключением
     * для сгорающих медиа).
     */
    public static boolean blockImage(Object imageLocation) {
        if (!enabled() || !(imageLocation instanceof ImageLocation)) {
            return false; /* KAMIGRAM_MEDIA_POLICY_R78 */
        }
        final ImageLocation location = (ImageLocation) imageLocation;
        try {
            if (location.document != null) {
                return !ALLOWED.contains(docKey(location.document.id));
            }
            if (location.location != null) {
                return !ALLOWED.contains(locKey(location.location));
            }
            if (location.webFile != null) {
                return !ALLOWED.contains(webKey(location.webFile));
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        return false;
    }

    /** r78: старые широкие предикаты не блокируют документы и запросы. */
    public static boolean blockDocument(TLRPC.Document document, Object parentObject, long size) {
        return false; /* KAMIGRAM_MEDIA_POLICY_R78 */
    }

    /** r78: совместимый предикат загрузки — решение принимает воронка. */
    public static boolean allow(Object parentObject, String mime, long size) {
        return true; /* KAMIGRAM_MEDIA_POLICY_R78 */
    }

    /** Подсказка для Центра и статуса: что делает режим сейчас. */
    public static String describe() {
        if (!enabled()) {
            return "режим «только текст» выключен";
        }
        return "трафик только на текст · медиа — по нажатию · сэкономлено "
            + KamiGramCache.human(KamiGramTraffic.saved());
    }
}
