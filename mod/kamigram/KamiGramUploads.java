package org.telegram.messenger.kamigram;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KAMIGRAM_UPLOAD_SERVICE_R101: отправка файлов больше 10 МБ живёт в фоне.
 *
 * Почему это нужно: раньше в фоне с уведомлением работали только загрузки
 * (KamiGramDownloadService + native FileLoader). Отправка большого видео или
 * документа продолжалась только пока приложение в переднем плане — стоило
 * уйти, и Android замораживал процесс вместе с загрузкой частей.
 *
 * Как это работает:
 *   * саму отправку по-прежнему выполняет родной FileUploadOperation Telegram
 *     (части, повторные попытки, resume, шифрование — всё нативное);
 *   * этот класс только считает активные ОТПРАВКИ крупнее 10 МБ и поднимает
 *     foreground-сервис, который держит процесс живым и показывает прогресс;
 *   * никаких опросов и таймеров: состояние меняется только по событиям
 *     (прогресс части / файл отправлен / ошибка / отмена);
 *   * как только крупных отправок не осталось, сервис останавливается сам.
 */
public final class KamiGramUploads {

    /** Та же планка, что и у загрузок: 10 МБ. */
    public static final long FOREGROUND_MIN_BYTES = 10L * 1024L * 1024L;

    /** Активные крупные отправки: "account:key" -> {отправлено, всего}. */
    private static final ConcurrentHashMap<String, long[]> ACTIVE = new ConcurrentHashMap<>();

    private KamiGramUploads() {
    }

    private static String id(int account, String key) {
        return account + ":" + (key == null ? "" : key);
    }

    /**
     * Прогресс отправки. Вызывается из FileUploadOperation после каждой части и
     * из ImageLoader для фотографий. Крупная отправка поднимает сервис.
     */
    public static void onProgress(int account, String key, long uploaded, long total) {
        try {
            if (key == null || total <= FOREGROUND_MIN_BYTES) {
                return;
            }
            ACTIVE.put(id(account, key), new long[]{Math.max(0L, uploaded), total});
            KamiGramDownloadService.ensureStartedForLargeUpload(
                org.telegram.messenger.ApplicationLoader.applicationContext, total);
            KamiGramDownloadService.reportUploadStateChanged();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Файл отправлен, отправка не удалась или отменена — запись убирается. */
    public static void onFinished(int account, String key) {
        try {
            if (key == null) {
                return;
            }
            ACTIVE.remove(id(account, key));
            KamiGramDownloadService.reportUploadStateChanged();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Есть ли хотя бы одна крупная активная отправка. */
    public static boolean hasActiveLargeUploads() {
        return !ACTIVE.isEmpty();
    }

    /** Сколько крупных отправок идёт сейчас. */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** Суммарный размер активных крупных отправок. */
    public static long totalBytes() {
        long total = 0L;
        for (Map.Entry<String, long[]> entry : ACTIVE.entrySet()) {
            final long[] value = entry.getValue();
            if (value != null && value.length > 1) {
                total += value[1];
            }
        }
        return total;
    }

    /** Суммарно отправлено байт по активным крупным отправкам. */
    public static long uploadedBytes() {
        long uploaded = 0L;
        for (Map.Entry<String, long[]> entry : ACTIVE.entrySet()) {
            final long[] value = entry.getValue();
            if (value != null && value.length > 0) {
                uploaded += value[0];
            }
        }
        return uploaded;
    }
}
