package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * KamiGram: счётчик трафика и экономия — «сколько реально ушло в сеть».
 *
 * Telegram не показывает расход трафика вообще, а на слабом мобильном интернете
 * это первое, что хочется видеть. Считаем по счётчикам системы (TrafficStats)
 * для нашего приложения: приём, отдача, итог за сессию и с запуска устройства.
 *
 * Плюс отсюда видно, сколько сэкономили отсечки стикеров/GIF/историй — это
 * оценка по счётчику заблокированных файлов из KamiGramNetFilter.
 */
public final class KamiGramTraffic {

    private static long sessionStartRx;
    private static long sessionStartTx;
    private static long savedBytes;
    private static long blockedFiles;
    private static boolean inited;

    private KamiGramTraffic() {
    }

    /** Вызывается один раз при старте приложения. */
    public static void init() {
        if (inited) {
            return;
        }
        inited = true;
        try {
            sessionStartRx = totalRx();
            sessionStartTx = totalTx();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Принято байт с момента запуска приложения. */
    public static long sessionRx() {
        try {
            return Math.max(0, totalRx() - sessionStartRx);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /** Отдано байт с момента запуска приложения. */
    public static long sessionTx() {
        try {
            return Math.max(0, totalTx() - sessionStartTx);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /** Итого за сессию. */
    public static long sessionTotal() {
        return sessionRx() + sessionTx();
    }

    /** Всего приложение приняло за всё время (с запуска устройства). */
    public static long totalRx() {
        try {
            return android.net.TrafficStats.getUidRxBytes(uid());
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static long totalTx() {
        try {
            return android.net.TrafficStats.getUidTxBytes(uid());
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static int uid() {
        return android.os.Process.myUid();
    }

    /** Сколько байт мод НЕ скачал: стикеры, GIF, истории, премиум-эмодзи, реклама. */
    public static long saved() {
        return savedBytes;
    }

    public static long blockedFiles() {
        return blockedFiles;
    }

    /** Вызывается фильтром трафика, когда запрос или файл отсекли. */
    public static void countBlocked(long bytes) {
        blockedBytes(bytes);
    }

    /** Отсекли файл: помним и размер, и количество. */
    public static void blockedBytes(long bytes) {
        blockedFiles++;
        if (bytes > 0) {
            savedBytes += bytes;
        }
        persist();
    }

    /** Отсекли запрос (без размера) — тоже экономия. */
    public static void blockedRequest() {
        blockedFiles++;
    }

    private static SharedPreferences prefs() {
        try {
            return ApplicationLoader.applicationContext
                .getSharedPreferences("kamigram_traffic", android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static void persist() {
        try {
            final SharedPreferences preferences = prefs();
            if (preferences != null) {
                preferences.edit().putLong("saved", savedBytes).putLong("files", blockedFiles).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Восстановить накопленную статистику экономии. */
    public static void restore() {
        try {
            final SharedPreferences preferences = prefs();
            if (preferences != null) {
                savedBytes = preferences.getLong("saved", 0);
                blockedFiles = preferences.getLong("files", 0);
            }
        } catch (Throwable ignore) {
        }
    }

    public static void reset() {
        savedBytes = 0;
        blockedFiles = 0;
        persist();
    }

    /** Строка для экрана мода. */
    public static String describe() {
        return "за сессию " + KamiGramCache.human(sessionTotal())
            + " (↓ " + KamiGramCache.human(sessionRx()) + ", ↑ " + KamiGramCache.human(sessionTx()) + ")";
    }

    /** Что не скачали благодаря отсечкам. */
    public static String economyText() {
        return "не скачано: " + KamiGramCache.human(savedBytes)
            + " · отсечено файлов и запросов: " + blockedFiles;
    }
}
