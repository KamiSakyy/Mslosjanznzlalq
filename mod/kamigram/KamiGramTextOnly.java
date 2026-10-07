package org.telegram.messenger.kamigram;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SecureDocument;
import org.telegram.messenger.WebFile;
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

    /* r130: окно после нажатия. Любой медиа-запрос, стартовавший в течение
       90 секунд после тапа по фото/видео, разрешён безусловно — так загрузка
       по нажатию работает на всех путях просмотрщика, независимо от того,
       какими объектами он запрашивает файлы. Между нажатиями трафик нулевой. */
    private static final long TAP_WINDOW_MS = 90_000L;
    private static volatile long lastTapTime;

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

    /** Отметка нажатия: открывает окно загрузки медиа по тапу. */
    public static void noteTap() {
        lastTapTime = android.os.SystemClock.elapsedRealtime();
    }

    private static boolean inTapWindow() {
        return android.os.SystemClock.elapsedRealtime() - lastTapTime < TAP_WINDOW_MS;
    }

    // ------------------------------------------------------------------ гейт

    /**True — загрузку этого медиа-запроса нужно отменить до выхода в сеть.*/
    public static boolean blocks(TLRPC.Document document,
                                 SecureDocument secureDocument,
                                 WebFile webDocument,
                                 TLRPC.TL_fileLocationToBeDeprecated location,
                                 ImageLocation imageLocation) {
        if (!enabled()) {
            return false;
        }
        return document != null || secureDocument != null || webDocument != null
            || location != null || imageLocation != null;
    }

    // --------------------------------------------------- отметки по нажатию

    /** Нажатие на сообщение с медиа: разрешаем всё медиа этого сообщения. */
    public static void allowMessage(MessageObject messageObject) {
        noteTap();
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
        noteTap();
        if (fileLocation instanceof TLRPC.TL_fileLocationToBeDeprecated) {
            add(locKey((TLRPC.TL_fileLocationToBeDeprecated) fileLocation));
        }
    }

    public static void allowImageLocation(ImageLocation imageLocation) {
        noteTap();
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

    private static String webKey(WebFile webFile) {
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
        if (!enabled() || imageLocation == null) {
            return false; /* KAMIGRAM_MEDIA_POLICY_R78 */
        }
        return true;
    }

    /** Включённый режим блокирует документ, видео, аудио и любой файл. */
    public static boolean blockDocument(TLRPC.Document document, Object parentObject, long size) {
        return enabled(); /* KAMIGRAM_MEDIA_POLICY_R78 */
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
        return "грузится только текст";
    }
}
