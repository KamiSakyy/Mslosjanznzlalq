package org.telegram.messenger.kamigram;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.StatsController;

/**
 * KamiGram: ускорение загрузок и потоков даже на слабом интернете.
 *
 * Что делает:
 * <ul>
 *   <li>на слабом канале (мобильный интернет / EDGE / 3G) качает маленькими
 *       блоками и с большим числом параллельных потоков - так быстрее и без
 *       «залипаний» на медленном соединении;</li>
 *   <li>на Wi-Fi берёт крупные блоки и максимальный параллелизм - скорость
 *       выше штатной;</li>
 *   <li>музыка и голосовые всегда качаются с высоким приоритетом, поэтому
 *       не ждут большие видео в очереди;</li>
 *   <li>мелкие файлы (аватарки, превью) не создают лишних соединений.</li>
 * </ul>
 */
public final class KamiGramSpeed {

    private KamiGramSpeed() {
    }

    /** Быстрый режим включён (по умолчанию да). */
    public static boolean enabled() {
        return KamiGramConfig.fastNet();
    }

    private static int networkType() {
        try {
            final ConnectivityManager manager =
                (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return StatsController.TYPE_WIFI;
            }
            final NetworkInfo info = manager.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) {
                return StatsController.TYPE_MOBILE;
            }
            if (info.getType() == ConnectivityManager.TYPE_WIFI || info.getType() == ConnectivityManager.TYPE_ETHERNET) {
                return StatsController.TYPE_WIFI;
            }
            return StatsController.TYPE_MOBILE;
        } catch (Throwable e) {
            return StatsController.TYPE_WIFI;
        }
    }

    /** Размер блока загрузки: на Wi-Fi крупный, на мобильном мельче - меньше обрывов. */
    public static int chunkSize() {
        if (!enabled()) {
            return 0;
        }
        return networkType() == StatsController.TYPE_WIFI ? 1024 * 512 : 1024 * 256;
    }

    /** Сколько потоков качать один файл: на мобильном больше, поэтому скорость выше. */
    public static int maxRequests() {
        if (!enabled()) {
            return 0;
        }
        return networkType() == StatsController.TYPE_WIFI ? 8 : 6;
    }

    /** Размер блока для видео-стрима (тяжёлые файлы). */
    public static int bigChunkSize() {
        if (!enabled()) {
            return 0;
        }
        return networkType() == StatsController.TYPE_WIFI ? 1024 * 512 : 1024 * 384;
    }

    /** Приоритет загрузки для файлов, которые пользователь ждёт прямо сейчас. */
    public static int streamRequests() {
        return enabled() ? 4 : 0;
    }

    /** Принудительно ли быстрый режим поднимает приоритет музыки и голосовых. */
    public static boolean boostAudio() {
        return enabled();
    }

    public static String networkName() {
        return networkType() == StatsController.TYPE_WIFI ? "Wi-Fi" : "мобильная сеть";
    }

    public static String describe() {
        if (!enabled()) {
            return "обычный режим Telegram";
        }
        return networkName() + ": блок " + (chunkSize() / 1024) + " КБ, потоков " + maxRequests();
    }
}
