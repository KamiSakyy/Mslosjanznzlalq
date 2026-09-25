package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KamiGram: кэш и файлы — «скачанное не пропадает, но чистить можно».
 *
 * Что было не так в прошлой сборке: автоочистка отключалась жёстко, из-за этого
 * пользователь вообще не мог освободить место («почему кэш нельзя чистить»).
 *
 * Как сделано сейчас:
 * <ul>
 *   <li>обычная очистка кэша Telegram работает ВСЕГДА (как в оригинале);</li>
 *   <li>галочка «скачанное не удалять» (по умолчанию выключена) запрещает
 *       автоудаление медиа по сроку и по размеру;</li>
 *   <li>если галочка включена, файлы, которые пользователь скачал вручную,
 *       переживают и ручную очистку кэша: их пути лежат в постоянном списке
 *       (SharedPreferences), а не только в памяти;</li>
 *   <li>менеджер загрузок мода умеет показать размер каждой категории и
 *       удалить её отдельно: кэш, фото, видео, музыка, документы, стикеры
 *       и истории. Технические логи в интерфейс и в менеджер не попадают.</li>
 * </ul>
 */
public final class KamiGramCache {

    /** Категории для менеджера загрузок. */
    public static final int TYPE_CACHE = 0;
    public static final int TYPE_PHOTOS = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_MUSIC = 3;
    public static final int TYPE_DOCUMENTS = 4;
    public static final int TYPE_STICKERS = 5;
    public static final int TYPE_STORIES = 6;
    public static final int TYPE_COUNT = 7;

    private static final String PREFS = "kamigram_files";
    private static final String KEY_PROTECTED = "protected_files";

    /** Пути, которые нельзя удалять: пользователь скачал их сам. */
    private static final Set<String> PROTECTED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static boolean loaded;

    private KamiGramCache() {
    }

    // ------------------------------------------------------------------ защита

    /** Основной переключатель: «скачанное не удалять» (по умолчанию выключен). */
    public static boolean keep() {
        return KamiGramConfig.keepDownloads();
    }

    private static SharedPreferences prefs() {
        try {
            return org.telegram.messenger.ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            final SharedPreferences preferences = prefs();
            if (preferences != null) {
                final String stored = preferences.getString(KEY_PROTECTED, "");
                if (stored != null && stored.length() > 0) {
                    for (String path : stored.split("\n")) {
                        if (path.length() > 0) {
                            PROTECTED.add(path);
                        }
                    }
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static void save() {
        try {
            final SharedPreferences preferences = prefs();
            if (preferences != null) {
                final StringBuilder builder = new StringBuilder();
                for (String path : PROTECTED) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(path);
                }
                preferences.edit().putString(KEY_PROTECTED, builder.toString()).apply();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Помечает файл как скачанный пользователем (переживает перезапуск). */
    public static void protect(String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        load();
        if (PROTECTED.add(path)) {
            save();
        }
    }

    public static void protect(File file) {
        if (file != null) {
            protect(file.getAbsolutePath());
        }
    }

    /** Имя файла (без пути) тоже защищаем: FileLoader часто оперирует именами. */
    public static void protectByName(String name) {
        protect(name);
    }

    /**
     * Mark the local representation of a message as explicitly saved. This is
     * intentionally called from Telegram's native save path, so protected,
     * view-once and disappearing media use the same gallery implementation as
     * ordinary media and survive cache cleanup/restart.
     */
    public static void protectMessage(org.telegram.messenger.MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        try {
            if (message.messageOwner.attachPath != null) {
                protect(message.messageOwner.attachPath);
            }
            final File file = FileLoader.getInstance(UserConfig.selectedAccount)
                .getPathToMessage(message.messageOwner);
            if (file != null) {
                protect(file);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isProtected(String path) {
        if (path == null) {
            return false;
        }
        load();
        return PROTECTED.contains(path);
    }

    public static boolean isProtected(File file) {
        return file != null && isProtected(file.getAbsolutePath());
    }

    /** Пропускать ли файл при удалении: только если включена защита скачанного. */
    public static boolean shouldSkipDelete(File file) {
        if (file == null) {
            return false;
        }
        if (!keep()) {
            return false;
        }
        load();
        final String path = file.getAbsolutePath();
        final String name = file.getName();
        return PROTECTED.contains(path) || PROTECTED.contains(name);
    }

    public static void forget() {
        load();
        PROTECTED.clear();
        save();
    }

    public static int protectedCount() {
        load();
        return PROTECTED.size();
    }

    // ------------------------------------------------------------------ размеры

    /** Размер одной категории в байтах. */
    public static long sizeOf(int type) {
        switch (type) {
            case TYPE_CACHE:
                return size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 5);
            case TYPE_PHOTOS:
                return size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), 0)
                    + size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), 0);
            case TYPE_VIDEO:
                return size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), 0)
                    + size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), 0);
            case TYPE_MUSIC:
                return size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 2)
                    + size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 2);
            case TYPE_DOCUMENTS:
                return size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 1)
                    + size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 1);
            case TYPE_STICKERS:
                return size(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), 0)
                    + size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 3);
            case TYPE_STORIES:
                return size(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_STORIES), 0);
            default:
                return 0;
        }
    }

    private static long size(File dir, int documentsMusicType) {
        if (dir == null) {
            return 0;
        }
        try {
            if (dir.isDirectory()) {
                return Utilities.getDirSize(dir.getAbsolutePath(), documentsMusicType, true);
            }
            if (dir.isFile()) {
                return dir.length();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        return 0;
    }

    /** Суммарный кэш всех категорий. */
    public static long total() {
        long sum = 0;
        for (int i = 0; i < TYPE_COUNT; i++) {
            sum += sizeOf(i);
        }
        return sum;
    }

    public static String nameOf(int type) {
        switch (type) {
            case TYPE_CACHE:
                return "Кэш и временные файлы";
            case TYPE_PHOTOS:
                return "Фото";
            case TYPE_VIDEO:
                return "Видео";
            case TYPE_MUSIC:
                return "Музыка";
            case TYPE_DOCUMENTS:
                return "Документы";
            case TYPE_STICKERS:
                return "Стикеры и эмодзи";
            case TYPE_STORIES:
                return "Истории";
            default:
                return "?";
        }
    }

    /** Человеческий размер: 1,5 ГБ / 740 МБ / 25 КБ. */
    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " Б";
        }
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f КБ", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024.0 * 1024));
        }
        return String.format(java.util.Locale.US, "%.2f ГБ", bytes / (1024.0 * 1024 * 1024));
    }

    /** Строка состояния для экрана мода. */
    public static String describe() {
        final String totalText = human(total());
        if (!keep()) {
            return totalText;
        }
        return "занято " + totalText + " · автоудаление выключено, защищено файлов: "
            + protectedCount();
    }

    // ------------------------------------------------------------------ очистка

    /**
     * Удалить категорию. Защищённые файлы остаются на месте.
     *
     * @param type категория TYPE_*
     */
    public static void clear(int type) {
        final ArrayList<File> targets = new ArrayList<>();
        switch (type) {
            case TYPE_CACHE:
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), targets);
                break;
            case TYPE_PHOTOS:
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), targets);
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), targets);
                break;
            case TYPE_VIDEO:
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), targets);
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), targets);
                break;
            case TYPE_MUSIC:
            case TYPE_DOCUMENTS:
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), targets);
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), targets);
                break;
            case TYPE_STICKERS:
                collect(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), targets);
                break;
            case TYPE_STORIES:
                collect(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_STORIES), targets);
                break;
            default:
                break;
        }
        final ArrayList<File> files = new ArrayList<>();
        for (File file : targets) {
            if (!shouldSkipDelete(file)) {
                files.add(file);
            }
        }
        if (!files.isEmpty()) {
            try {
                FileLoader.getInstance(UserConfig.selectedAccount).deleteFiles(files, 0);
            } catch (Throwable throwable) {
                KamiGramLog.e(throwable);
                for (File file : files) {
                    deleteRecursive(file);
                }
            }
        }
        try {
            ImageLoader.getInstance().clearMemory();
        } catch (Throwable ignore) {
        }
    }

    /** Удалить весь кэш (кроме защищённого). */
    public static void clearAll() {
        for (int i = 0; i < TYPE_COUNT; i++) {
            clear(i);
        }
    }

    /** Выкинуть из памяти протухшее: Telegram сам подтянет заново. */
    public static void freeMemory() {
        try {
            ImageLoader.getInstance().clearMemory();
        } catch (Throwable ignore) {
        }
        try {
            FileLoader.getInstance(UserConfig.selectedAccount).cancelLoadAllFiles();
        } catch (Throwable ignore) {
        }
    }

    private static void collect(File dir, ArrayList<File> out) {
        if (dir == null || !dir.exists()) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            out.add(file);
        }
    }

    private static void deleteRecursive(File file) {
        try {
            if (file.isDirectory()) {
                final File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Кэш заблокирован? (используется на экране очистки Telegram). */
    public static boolean lockedByMod() {
        return keep();
    }

    /** Список активных автоскачиваний, которые нельзя обрывать при чистке. */
    public static Set<String> activeDownloads() {
        final Set<String> names = new LinkedHashSet<>();
        try {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (!UserConfig.getInstance(account).isClientActivated()) {
                    continue;
                }
            }
        } catch (Throwable ignore) {
        }
        return names;
    }

    /** Не даём Telegram удалить кэш при низком месте, если защита включена. */
    public static boolean allowSystemCleanup() {
        return !keep();
    }

    /** Для настроек: пояснение текущего режима. */
    public static String modeText() {
        return keep()
            ? "Скачанное вручную защищено, автоудаление выключено"
            : "Как в оригинале: кэш чистится, ничего не блокируется";
    }

    /** Перечитать настройки: полезно после смены режима кэша. */
    public static void reloadSettings() {
        try {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (UserConfig.getInstance(account).isClientActivated()) {
                    MessagesController.getInstance(account).checkPromoInfo(true);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /** Текущая тема (для отладки строки состояния). */
    public static String themeText() {
        try {
            return Theme.isCurrentThemeDark() ? "тёмная iOS" : "светлая системная";
        } catch (Throwable ignore) {
            return "тёмная iOS";
        }
    }
}
