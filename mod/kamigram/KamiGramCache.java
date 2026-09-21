package org.telegram.messenger.kamigram;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KamiGram: скачанное остаётся на месте.
 *
 * Жалоба «скачал аудио/видео, а кэш очистился» - это встроенная автоочистка
 * Telegram (AutoDeleteMediaTask): раз в сутки она удаляет медиа старше заданного
 * срока и подрезает кэш по размеру. Мод это выключает:
 * <ul>
 *   <li>удаление по сроку больше не работает (медиа хранится, пока пользователь
 *       сам не очистит кэш);</li>
 *   <li>подрезка кэша по размеру выключена;</li>
 *   <li>файлы, которые пользователь качал руками (нажал на скачивание), помечаются
 *       защищёнными и не удаляются даже ручной очисткой кэша.</li>
 * </ul>
 */
public final class KamiGramCache {

    /** Пути, которые нельзя удалять: пользователь скачал их сам. */
    private static final Set<String> PROTECTED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private KamiGramCache() {
    }

    /** Основной переключатель: «скачанное не удалять». */
    public static boolean keep() {
        return KamiGramConfig.keepDownloads();
    }

    /** Помечает файл как скачанный пользователем. */
    public static void protect(String path) {
        if (path != null && path.length() > 0) {
            PROTECTED.add(path);
        }
    }

    public static void protect(File file) {
        if (file != null) {
            protect(file.getAbsolutePath());
        }
    }

    public static boolean isProtected(String path) {
        return path != null && PROTECTED.contains(path);
    }

    public static boolean isProtected(File file) {
        return file != null && isProtected(file.getAbsolutePath());
    }

    /** Имя файла (без пути) тоже защищаем: FileLoader часто оперирует именами. */
    public static void protectByName(String name) {
        if (name != null && name.length() > 0) {
            PROTECTED.add(name);
        }
    }

    public static boolean shouldSkipDelete(File file) {
        return keep() && isProtected(file);
    }

    public static int protectedCount() {
        return PROTECTED.size();
    }

    /** Строка состояния для экрана мода. */
    public static String describe() {
        if (!keep()) {
            return "автоочистка Telegram как обычно";
        }
        return "медиа не удаляется автоматически, защищено файлов: " + protectedCount();
    }
}
