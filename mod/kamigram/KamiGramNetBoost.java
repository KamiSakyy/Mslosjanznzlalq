package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.UserConfig;

/**
 * KamiGram: «точечный» буст скорости (r70) — по просьбе пользователя.
 *
 * Как работает:
 * <ul>
 *   <li>пользователь нажал на фото/видео/файл (запрос на загрузку с
 *       приоритетом выше обычного) — мы запоминаем имя файла как «фокусное»;
 *   <li>до момента, пока фокусный файл не докачается (или не сломается),
 *       ВСЕ остальные загрузки ставятся на паузу: и маленькая очередь, и
 *       большая. Вся пропускная способность мобильного интернета уходит
 *       именно на этот файл — поэтому он качается моментально;
 *   <li>фокусный файл дополнительно получает больше параллельных потоков
 *       и крупный блок, чем штатные (см. {@link #boostRequests()});
 *   <li>когда фокусный файл готов — фокус снимается, остальные загрузки
 *       сами продолжается с того места, где остановились.
 * </ul>
 *
 * Плюс «убраны искусственные задержки»: очереди загрузок проверяются
 * без пауз, а лимиты одновременных операций подняты (см.
 * {@link KamiGramSpeed#smallQueueMax()} и {@link KamiGramSpeed#largeQueueMax()}).
 */
public final class KamiGramNetBoost {

    private KamiGramNetBoost() {
    }

    /** Сколько параллельных потоков даёт фокусному файлу. */
    private static final int BOOST_REQUESTS = 12;
    /** Размер блока для фокусного файла. */
    private static final int BOOST_CHUNK = 1024 * 512;
    /** Фокус берём только для файлов крупнее этого (аватарки и превью не трогаем). */
    private static final long MIN_FOCUS_SIZE = 300L * 1024L;

    private static final String[] FOCUS = new String[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long[] FOCUS_AT = new long[UserConfig.MAX_ACCOUNT_COUNT];

    /**
     * Поставить фокус на файл: он качается первым, со всеми потоками,
     * остальные загрузки на паузе.
     */
    public static void focus(int account, String fileName) {
        try {
            if (fileName == null || fileName.length() == 0) {
                return;
            }
            if (account < 0 || account >= FOCUS.length) {
                return;
            }
            if (!KamiGramConfig.netFocus()) {
                return;
            }
            if (FOCUS[account] != null && FOCUS[account].equals(fileName)) {
                return; // уже в фокусе
            }
            FOCUS[account] = fileName;
            FOCUS_AT[account] = System.currentTimeMillis();
            // пересобрать очереди: фокусный стартует, остальные — пауза
            FileLoader loader = FileLoader.getInstance(account);
            if (loader != null) {
                loader.kamigramRecheckQueues();
            }
        } catch (Throwable t) {
            KamiGramLog.e(t);
        }
    }

    /** Убрать фокус (файл докачался/отменился) — остальные загрузки продолжают. */
    public static void unfocus(int account, String fileName) {
        try {
            if (account < 0 || account >= FOCUS.length) {
                return;
            }
            if (FOCUS[account] == null || !FOCUS[account].equals(fileName)) {
                return; // фокус уже сняли или это был не он
            }
            FOCUS[account] = null;
            FOCUS_AT[account] = 0;
            FileLoader loader = FileLoader.getInstance(account);
            if (loader != null) {
                loader.kamigramRecheckQueues();
            }
        } catch (Throwable t) {
            KamiGramLog.e(t);
        }
    }

    /** Текущий фокусный файл аккаунта (null — фокуса нет). */
    public static String focusFile(int account) {
        try {
            if (account < 0 || account >= FOCUS.length) {
                return null;
            }
            final String name = FOCUS[account];
            // защита от «вечного» фокуса: файл мог зависнуть без прогресса —
            // через 60 секунд фокус снимаем сами, загрузки не должны вестись вечно
            if (name != null && FOCUS_AT[account] != 0
                && System.currentTimeMillis() - FOCUS_AT[account] > 60_000L) {
                FOCUS[account] = null;
                FOCUS_AT[account] = 0;
                try {
                    final FileLoader loader = FileLoader.getInstance(account);
                    if (loader != null) {
                        loader.kamigramRecheckQueues(); // паузу с остальных снять
                    }
                } catch (Throwable ignore) {
                }
                return null;
            }
            return name;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Есть ли у файла фокус (для усиления потоков именно у него). */
    public static boolean isFocused(int account, String fileName) {
        try {
            return fileName != null && fileName.equals(focusFile(account));
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Продлить фокус (вызывается при каждом движении прогресса). */
    public static void touchFocus(int account, String fileName) {
        try {
            if (account < 0 || account >= FOCUS.length) {
                return;
            }
            if (FOCUS[account] != null && FOCUS[account].equals(fileName)) {
                FOCUS_AT[account] = System.currentTimeMillis();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Сколько потоков у фокусного файла. */
    public static int boostRequests() {
        return BOOST_REQUESTS;
    }

    /** Размер блока у фокусного файла. */
    public static int boostChunk() {
        return BOOST_CHUNK;
    }

    /** Минимальный размер файла, на который берём фокус. */
    public static long minFocusSize() {
        return MIN_FOCUS_SIZE;
    }
}
