package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.app.Dialog;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Process;
import android.os.storage.StorageManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * KAMIGRAM_HIDDEN_CACHE_R137
 *
 * Android показывает три разные цифры, и это не «один кэш Telegram»:
 * приложение — APK и библиотеки, кэш — только папка cache, данные —
 * база сообщений и скачанные файлы. Экран кэша Telegram часть файлов
 * в тех же папках не считает (фильтр по типу).
 *
 * Очистка удаляет только отмеченные файлы и, если отмечено, сжимает
 * локальную базу через штатный clearLocalDatabase. Файл сессии tgnet.dat,
 * его резервная копия и настройки не удаляются. Выхода из аккаунта нет.
 */
public final class KamiGramHiddenCache {

    private KamiGramHiddenCache() {
    }

    private static final String ID_CACHE = "cache";
    private static final String ID_TEMP = "temp";
    private static final String ID_STICKERS = "stickers";
    private static final String ID_LOGS = "logs";
    private static final String ID_WEBVIEW = "webview";
    private static final String ID_CODE = "codecache";
    private static final String ID_PHOTO = "photo";
    private static final String ID_VIDEO = "video";
    private static final String ID_AUDIO = "audio";
    private static final String ID_DOCUMENT = "document";
    private static final String ID_FILES = "files";
    private static final String ID_STORIES = "stories";
    private static final String ID_OTHER = "other";
    private static final String ID_DATABASE = "database";
    private static final String ID_GALLERY = "gallery";
    private static final String ID_SESSION = "session";
    private static final String ID_INDEX = "index";

    private static final long SHOW_MIN = 32L * 1024L;

    private static Dialog openDialog;

    public static void show(Context context) {
        try {
            final Activity activity = AndroidUtilities.findActivity(context);
            if (activity == null) {
                return;
            }
            try {
                if (openDialog != null) {
                    openDialog.dismiss();
                }
            } catch (Throwable ignore) {
            }
            openDialog = null;

            final boolean[] alive = new boolean[]{true};
            final boolean[] busy = new boolean[]{false};
            final Map<String, Boolean> picked = new LinkedHashMap<>();
            final TextView explain = text(activity, 13, ThemeHook.secondaryText(), false);
            explain.setText("Считаю папки и сверяю с цифрами Android…");
            final TextView status = text(activity, 12, ThemeHook.secondaryText(), false);
            status.setText("");
            final LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);

            final TextView clearData = button(activity, "Очистить данные без выхода", true);
            final TextView clearPicked = button(activity, "Очистить выбранное", false);
            clearPicked.setEnabled(false);
            clearData.setEnabled(false);
            clearPicked.setAlpha(0.45f);
            clearData.setAlpha(0.45f);

            final LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.addView(explain, match());
            body.addView(list, matchTop(10));
            body.addView(clearData, buttonParams());
            body.addView(clearPicked, buttonParams());
            body.addView(status, matchTop(8));

            final Dialog dialog = KamiGramDialog.create(activity)
                .title("Скрытый кэш")
                .icon(KamiGramDialog.ICON_INFO)
                .content(body)
                .negative("Закрыть", null)
                .show();
            openDialog = dialog;
            if (dialog != null) {
                dialog.setOnDismissListener(d -> {
                    alive[0] = false;
                    if (openDialog == dialog) {
                        openDialog = null;
                    }
                });
            }

            final Runnable[] rescan = new Runnable[1];
            rescan[0] = () -> Utilities.cacheClearQueue.postRunnable(() -> {
                final Report report;
                try {
                    report = scan(activity);
                } catch (Throwable throwable) {
                    FileLog.e(throwable);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!alive[0]) {
                            return;
                        }
                        explain.setText("Не удалось посчитать папки. Сессия не изменялась.");
                    });
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!alive[0]) {
                        return;
                    }
                    bind(activity, explain, list, status, picked, report);
                    final boolean ready = !busy[0];
                    clearPicked.setEnabled(ready);
                    clearData.setEnabled(ready);
                    clearPicked.setAlpha(ready ? 1f : 0.45f);
                    clearData.setAlpha(ready ? 1f : 0.45f);
                });
            });

            clearPicked.setOnClickListener(v -> {
                if (busy[0] || !clearPicked.isEnabled()) {
                    return;
                }
                final HashSet<String> ids = selected(picked);
                if (ids.isEmpty()) {
                    KamiGramUi.notify(activity, "Нечего очищать: ничего не отмечено");
                    return;
                }
                KamiGramDialog.confirm(activity, "Очистить отмеченное?",
                    confirmText(ids, false),
                    "Очистить", "Отмена", () -> runClear(activity, alive, busy, clearPicked, clearData, status, ids, rescan[0]));
            });
            clearData.setOnClickListener(v -> {
                if (busy[0] || !clearData.isEnabled()) {
                    return;
                }
                final HashSet<String> ids = dataIds();
                KamiGramDialog.confirm(activity, "Очистить данные без выхода?",
                    confirmText(ids, true),
                    "Очистить", "Отмена", () -> runClear(activity, alive, busy, clearPicked, clearData, status, ids, rescan[0]));
            });

            rescan[0].run();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            FileLog.e(throwable);
        }
    }

    private static void runClear(final Activity activity, final boolean[] alive, final boolean[] busy,
                                 final TextView clearPicked, final TextView clearData, final TextView status,
                                 final HashSet<String> ids, final Runnable rescan) {
        busy[0] = true;
        clearPicked.setEnabled(false);
        clearData.setEnabled(false);
        clearPicked.setAlpha(0.45f);
        clearData.setAlpha(0.45f);
        status.setText("Очищаю. Сессия не удаляется.");
        final boolean database = ids.contains(ID_DATABASE);
        if (database) {
            AndroidUtilities.runOnUIThread(() -> clearDatabases());
        }
        Utilities.cacheClearQueue.postRunnable(() -> {
            long freed = 0L;
            int files = 0;
            try {
                final int[] count = new int[]{0};
                freed = deleteSelected(activity, ids, count);
                files = count[0];
                if (ids.contains(ID_CACHE) || ids.contains(ID_PHOTO) || ids.contains(ID_VIDEO)
                    || ids.contains(ID_AUDIO) || ids.contains(ID_DOCUMENT) || ids.contains(ID_FILES)
                    || ids.contains(ID_STORIES) || ids.contains(ID_STICKERS) || ids.contains(ID_TEMP)) {
                    dropPathIndex();
                }
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
            final long freedBytes = freed;
            final int freedFiles = files;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ImageLoader.getInstance().clearMemory();
                } catch (Throwable ignore) {
                }
                busy[0] = false;
                if (!alive[0]) {
                    KamiGramUi.notify(activity, doneText(freedBytes, freedFiles, database));
                    return;
                }
                status.setText(doneText(freedBytes, freedFiles, database));
                KamiGramUi.notify(activity, doneText(freedBytes, freedFiles, database));
                if (rescan != null) {
                    rescan.run();
                }
            });
            if (database) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (alive[0] && rescan != null) {
                        rescan.run();
                    }
                }, 25000L);
            }
        });
    }

    private static void clearDatabases() {
        try {
            final int max = UserConfig.MAX_ACCOUNT_COUNT;
            for (int account = 0; account < max; account++) {
                if (!UserConfig.getInstance(account).isClientActivated()) {
                    continue;
                }
                try {
                    MessagesController.getInstance(account).clearQueryTime();
                } catch (Throwable ignore) {
                }
                MessagesStorage.getInstance(account).clearLocalDatabase();
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void dropPathIndex() {
        try {
            final int max = UserConfig.MAX_ACCOUNT_COUNT;
            for (int account = 0; account < max; account++) {
                if (!UserConfig.getInstance(account).isClientActivated()) {
                    continue;
                }
                FileLoader.getInstance(account).clearFilePaths();
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static String doneText(long bytes, int files, boolean database) {
        final String size = AndroidUtilities.formatFileSize(Math.max(0L, bytes));
        if (database) {
            return "Файлы: " + size + " (" + files + "). База сжимается, чаты подгрузятся заново. Вход на месте.";
        }
        return "Освобождено " + size + " (" + files + " файлов). Сессия на месте.";
    }

    private static HashSet<String> selected(Map<String, Boolean> picked) {
        final HashSet<String> ids = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : picked.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue()) && clearable(entry.getKey())) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    private static HashSet<String> dataIds() {
        final HashSet<String> ids = new HashSet<>();
        ids.add(ID_DATABASE);
        ids.add(ID_PHOTO);
        ids.add(ID_VIDEO);
        ids.add(ID_AUDIO);
        ids.add(ID_DOCUMENT);
        ids.add(ID_FILES);
        ids.add(ID_STORIES);
        ids.add(ID_CACHE);
        ids.add(ID_TEMP);
        ids.add(ID_STICKERS);
        ids.add(ID_LOGS);
        ids.add(ID_WEBVIEW);
        ids.add(ID_CODE);
        return ids;
    }

    private static boolean clearable(String id) {
        return !ID_SESSION.equals(id) && !ID_INDEX.equals(id) && !"missed".equals(id) && !"hidden".equals(id);
    }

    private static String confirmText(HashSet<String> ids, boolean data) {
        if (data) {
            return "Удалятся локальная база сообщений и скачанные фото, видео, аудио, документы, файлы и истории внутри приложения, плюс папка cache, временные файлы, стикеры, логи и служебный кэш. Чаты откроются заново с сервера, медиа скачается снова. Вход останется: tgnet.dat и настройки не удаляются. Копии в галерее не трогаются.";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("Удалятся только отмеченные пункты: ");
        boolean any = false;
        for (String id : order()) {
            if (!ids.contains(id)) {
                continue;
            }
            if (any) {
                builder.append(", ");
            }
            builder.append(titleOf(id));
            any = true;
        }
        builder.append(". tgnet.dat и настройки не удаляются, выхода из аккаунта нет.");
        if (ids.contains(ID_DATABASE)) {
            builder.append(" База сожмётся в фоне, чаты подгрузятся заново.");
        }
        return builder.toString();
    }

    private static void bind(Activity activity, TextView explain, LinearLayout list, TextView status,
                             Map<String, Boolean> picked, Report report) {
        explain.setText(explain(report));
        list.removeAllViews();
        picked.clear();
        addStat(activity, list, "Приложение", report.appBytes, "APK и библиотеки. Это не кэш, очисткой не уменьшается.");
        addStat(activity, list, "Данные пользователя", report.dataBytes, "База сообщений и скачанные файлы. Android не называет это кэшем.");
        addStat(activity, list, "Кэш Android", report.cacheBytes, "Только папка cache. Системная кнопка очистки видит только её.");
        if (report.hiddenBytes >= 0L && report.hiddenBytes >= SHOW_MIN) {
            addStat(activity, list, "Не видно экрану Telegram", report.hiddenBytes,
                "Файлы в тех же папках, которые экран кэша Telegram не считает.");
        }
        if (report.gapBytes > 32L * 1024L * 1024L) {
            addStat(activity, list, "Ещё в данных Android", report.gapBytes,
                "Система видит эти байты вне разобранных папок Telegram.");
        }

        for (String id : order()) {
            final long bytes = report.bytes(id);
            if (!always(id) && bytes < SHOW_MIN) {
                continue;
            }
            final boolean canClear = clearable(id);
            final boolean on = canClear && defaultOn(id);
            if (canClear) {
                picked.put(id, on);
            }
            list.addView(row(activity, id, bytes, canClear, on, picked), matchTop(8));
        }
        long folders = 0L;
        for (String id : order()) {
            folders += report.bytes(id);
        }
        status.setText("По папкам разобрано " + AndroidUtilities.formatFileSize(folders)
            + ". «Очистить данные без выхода» убирает базу и скачанные файлы. Сессия остаётся.");
    }

    private static boolean always(String id) {
        return ID_DATABASE.equals(id) || ID_SESSION.equals(id) || ID_CACHE.equals(id);
    }

    private static boolean defaultOn(String id) {
        return ID_CACHE.equals(id) || ID_TEMP.equals(id) || ID_STICKERS.equals(id)
            || ID_LOGS.equals(id) || ID_WEBVIEW.equals(id) || ID_CODE.equals(id);
    }

    private static String explain(Report report) {
        final String app = AndroidUtilities.formatFileSize(report.appBytes);
        final String data = AndroidUtilities.formatFileSize(report.dataBytes);
        final String cache = AndroidUtilities.formatFileSize(report.cacheBytes);
        return "Android делит память на три строки, это не один кэш.\n"
            + "Приложение " + app + " — код. Кэш " + cache + " — папка cache. "
            + "Данные " + data + " — база и файлы, их нет в строке «кэш».\n"
            + "Ниже полный размер папок, без фильтра Telegram. Сессия не удаляется.";
    }

    private static void addStat(Activity activity, LinearLayout list, String title, long bytes, String hint) {
        final LinearLayout card = card(activity);
        final TextView head = text(activity, 14, ThemeHook.primaryText(), true);
        head.setText(title + "  ·  " + AndroidUtilities.formatFileSize(Math.max(0L, bytes)));
        final TextView sub = text(activity, 12, ThemeHook.secondaryText(), false);
        sub.setText(hint);
        card.addView(head, match());
        card.addView(sub, matchTop(2));
        list.addView(card, matchTop(8));
    }

    private static View row(final Activity activity, final String id, long bytes, boolean canClear,
                            boolean on, final Map<String, Boolean> picked) {
        final LinearLayout card = card(activity);
        final LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        final LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        final TextView head = text(activity, 14, canClear ? ThemeHook.primaryText() : ThemeHook.secondaryText(), true);
        head.setText(titleOf(id) + "  ·  " + AndroidUtilities.formatFileSize(Math.max(0L, bytes)));
        final TextView sub = text(activity, 12, ThemeHook.secondaryText(), false);
        sub.setText(hintOf(id));
        labels.addView(head, match());
        labels.addView(sub, matchTop(2));
        line.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (canClear) {
            final KamiGramUi.Toggle toggle = new KamiGramUi.Toggle(activity);
            toggle.setChecked(on);
            toggle.setOnToggleListener(checked -> picked.put(id, checked));
            line.setOnClickListener(v -> toggle.userToggle());
            line.addView(toggle, new LinearLayout.LayoutParams(AndroidUtilities.dp(42), AndroidUtilities.dp(25)));
        }
        card.addView(line, match());
        return card;
    }

    private static String[] order() {
        return new String[]{
            ID_DATABASE, ID_PHOTO, ID_VIDEO, ID_AUDIO, ID_DOCUMENT, ID_FILES, ID_STORIES,
            ID_CACHE, ID_TEMP, ID_STICKERS, ID_LOGS, ID_WEBVIEW, ID_CODE, ID_OTHER,
            ID_GALLERY, ID_INDEX, ID_SESSION
        };
    }

    private static String titleOf(String id) {
        if (ID_DATABASE.equals(id)) return "Локальная база";
        if (ID_PHOTO.equals(id)) return "Фото";
        if (ID_VIDEO.equals(id)) return "Видео";
        if (ID_AUDIO.equals(id)) return "Аудио и голосовые";
        if (ID_DOCUMENT.equals(id)) return "Документы и музыка";
        if (ID_FILES.equals(id)) return "Файлы";
        if (ID_STORIES.equals(id)) return "Истории";
        if (ID_CACHE.equals(id)) return "Папка cache";
        if (ID_TEMP.equals(id)) return "Временные загрузки";
        if (ID_STICKERS.equals(id)) return "Стикеры и эмодзи";
        if (ID_LOGS.equals(id)) return "Логи";
        if (ID_WEBVIEW.equals(id)) return "Кэш страниц";
        if (ID_CODE.equals(id)) return "Служебный код";
        if (ID_OTHER.equals(id)) return "Прочие файлы";
        if (ID_GALLERY.equals(id)) return "Копии в галерее";
        if (ID_INDEX.equals(id)) return "Индекс путей";
        if (ID_SESSION.equals(id)) return "Сессия и настройки";
        return id;
    }

    private static String hintOf(String id) {
        if (ID_DATABASE.equals(id)) return "Главная часть «данных». Очистка не выходит из аккаунта, чаты скачаются снова.";
        if (ID_PHOTO.equals(id) || ID_VIDEO.equals(id) || ID_AUDIO.equals(id) || ID_DOCUMENT.equals(id) || ID_FILES.equals(id) || ID_STORIES.equals(id)) {
            return "Лежит в данных приложения, не в строке «кэш» Android.";
        }
        if (ID_CACHE.equals(id)) return "То, что система называет кэшем.";
        if (ID_TEMP.equals(id)) return "Оборванные загрузки. Экран Telegram их часто не показывает.";
        if (ID_STICKERS.equals(id)) return "Папка acache внутри кэша.";
        if (ID_LOGS.equals(id)) return "Журнал. На размер чатов не влияет.";
        if (ID_WEBVIEW.equals(id)) return "Страницы внутри приложения, не сессия Telegram.";
        if (ID_CODE.equals(id)) return "Пересоздаётся само. На сообщения не влияет.";
        if (ID_OTHER.equals(id)) return "Файлы вне известных папок. Настройки и сессия сюда не входят.";
        if (ID_GALLERY.equals(id)) return "Не входит в размер приложения. По умолчанию не удаляется.";
        if (ID_INDEX.equals(id)) return "Открытая служебная база. Не удаляется, это не сессия.";
        return "tgnet.dat и настройки. Этот пункт не удаляется.";
    }

    private static final class Report {
        long appBytes = -1L;
        long dataBytes = -1L;
        long cacheBytes = -1L;
        long hiddenBytes = -1L;
        long gapBytes;
        long explained;
        final LinkedHashMap<String, Long> sizes = new LinkedHashMap<>();

        long bytes(String id) {
            final Long value = sizes.get(id);
            return value == null ? 0L : value;
        }

        void add(String id, long bytes) {
            if (bytes <= 0L || id == null) {
                return;
            }
            final Long was = sizes.get(id);
            sizes.put(id, (was == null ? 0L : was) + bytes);
        }
    }

    private static final class Roots {
        final ArrayList<Dir> dirs = new ArrayList<>();
        final ArrayList<String> appRoots = new ArrayList<>();

        void add(File file, String id) {
            if (file == null) {
                return;
            }
            try {
                final String path = file.getAbsolutePath();
                if (path.length() < 2) {
                    return;
                }
                dirs.add(new Dir(path, id));
            } catch (Throwable ignore) {
            }
        }

        void app(File file) {
            if (file == null) {
                return;
            }
            try {
                appRoots.add(file.getAbsolutePath());
            } catch (Throwable ignore) {
            }
        }
    }

    private static final class Dir {
        final String path;
        final String id;

        Dir(String path, String id) {
            this.path = path;
            this.id = id;
        }
    }

    private static Report scan(Context context) {
        final Report report = new Report();
        readSystem(context, report);
        final Roots roots = roots(context);
        final HashSet<String> seen = new HashSet<>();
        final File data = dataDir();
        walk(data, roots, report, seen);
        walkExternal(context, roots, report, seen);
        final File logs = AndroidUtilities.getLogsDir();
        walk(logs, roots, report, seen);
        walkMedia(roots, report, seen);
        report.hiddenBytes = hiddenDiff(report);
        if (report.dataBytes >= 0L && report.cacheBytes >= 0L) {
            report.gapBytes = (report.dataBytes + report.cacheBytes) - report.explained;
        }
        return report;
    }

    private static void readSystem(Context context, Report report) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            final StorageStatsManager manager = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
            if (manager == null) {
                return;
            }
            final StorageStats stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid());
            report.appBytes = stats.getAppBytes();
            report.dataBytes = stats.getDataBytes();
            report.cacheBytes = stats.getCacheBytes();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static Roots roots(Context context) {
        final Roots roots = new Roots();
        final File data = dataDir();
        roots.app(data);
        final File cache = context.getCacheDir();
        final File code = context.getCodeCacheDir();
        roots.add(cache, ID_CACHE);
        roots.add(code, ID_CODE);
        roots.app(cache);
        roots.app(code);
        if (data != null) {
            roots.add(new File(data, "app_webview"), ID_WEBVIEW);
            roots.add(new File(data, "app_webview_cache"), ID_WEBVIEW);
            roots.add(new File(cache == null ? data : cache, "WebView"), ID_WEBVIEW);
        }
        roots.add(AndroidUtilities.getLogsDir(), ID_LOGS);
        addMedia(roots, FileLoader.MEDIA_DIR_IMAGE, ID_PHOTO);
        addMedia(roots, FileLoader.MEDIA_DIR_VIDEO, ID_VIDEO);
        addMedia(roots, FileLoader.MEDIA_DIR_AUDIO, ID_AUDIO);
        addMedia(roots, FileLoader.MEDIA_DIR_DOCUMENT, ID_DOCUMENT);
        addMedia(roots, FileLoader.MEDIA_DIR_FILES, ID_FILES);
        addMedia(roots, FileLoader.MEDIA_DIR_STORIES, ID_STORIES);
        addMedia(roots, FileLoader.MEDIA_DIR_CACHE, ID_CACHE);
        addMedia(roots, FileLoader.MEDIA_DIR_IMAGE_PUBLIC, ID_GALLERY);
        addMedia(roots, FileLoader.MEDIA_DIR_VIDEO_PUBLIC, ID_GALLERY);
        try {
            final File mediaCache = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
            if (mediaCache != null) {
                roots.add(new File(mediaCache, "acache"), ID_STICKERS);
            }
        } catch (Throwable ignore) {
        }
        if (cache != null) {
            roots.add(new File(cache, "acache"), ID_STICKERS);
        }
        try {
            final File extra = AndroidUtilities.getCacheDir();
            roots.add(extra, ID_CACHE);
            if (extra != null) {
                roots.add(new File(extra, "acache"), ID_STICKERS);
            }
        } catch (Throwable ignore) {
        }
        addExternal(context, roots);
        return roots;
    }

    private static void addMedia(Roots roots, int type, String id) {
        try {
            roots.add(FileLoader.checkDirectory(type), id);
        } catch (Throwable ignore) {
        }
    }

    private static void addExternal(Context context, Roots roots) {
        try {
            final File[] caches = context.getExternalCacheDirs();
            if (caches != null) {
                for (File file : caches) {
                    roots.add(file, ID_CACHE);
                    roots.app(file);
                    if (file != null) {
                        roots.add(new File(file, "acache"), ID_STICKERS);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        try {
            final File[] files = context.getExternalFilesDirs(null);
            if (files != null) {
                for (File file : files) {
                    roots.app(file);
                    if (file != null) {
                        roots.add(new File(file, "logs"), ID_LOGS);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                final File[] media = context.getExternalMediaDirs();
                if (media != null) {
                    for (File file : media) {
                        roots.app(file);
                    }
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void walkExternal(Context context, Roots roots, Report report, HashSet<String> seen) {
        try {
            final File[] caches = context.getExternalCacheDirs();
            if (caches != null) {
                for (File file : caches) {
                    walk(file, roots, report, seen);
                }
            }
        } catch (Throwable ignore) {
        }
        try {
            final File[] files = context.getExternalFilesDirs(null);
            if (files != null) {
                for (File file : files) {
                    walk(file, roots, report, seen);
                }
            }
        } catch (Throwable ignore) {
        }
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                final File[] media = context.getExternalMediaDirs();
                if (media != null) {
                    for (File file : media) {
                        walk(file, roots, report, seen);
                    }
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void walk(File dir, Roots roots, Report report, HashSet<String> seen) {
        if (dir == null || !dir.exists() || isLink(dir)) {
            return;
        }
        final File[] kids = dir.listFiles();
        if (kids == null) {
            if (dir.isFile()) {
                count(dir, roots, report, seen);
            }
            return;
        }
        for (File kid : kids) {
            if (kid == null || isLink(kid)) {
                continue;
            }
            if (kid.isDirectory()) {
                walk(kid, roots, report, seen);
            } else {
                count(kid, roots, report, seen);
            }
        }
    }

    private static void count(File file, Roots roots, Report report, HashSet<String> seen) {
        final String path = file.getAbsolutePath();
        if (!seen.add(path)) {
            return;
        }
        final long length = file.length();
        if (length <= 0L) {
            return;
        }
        final String id = category(file, path, roots);
        if (id == null) {
            return;
        }
        report.add(id, length);
        if (underApp(path, roots)) {
            report.explained += length;
        }
    }

    private static long deleteSelected(Context context, HashSet<String> ids, int[] count) {
        final Roots roots = roots(context);
        final HashSet<String> seen = new HashSet<>();
        final long[] freed = new long[]{0L};
        deleteWalk(dataDir(), roots, ids, seen, freed, count);
        deleteExternal(context, roots, ids, seen, freed, count);
        deleteWalk(AndroidUtilities.getLogsDir(), roots, ids, seen, freed, count);
        deleteMedia(roots, ids, seen, freed, count);
        return freed[0];
    }

    private static void walkMedia(Roots roots, Report report, HashSet<String> seen) {
        for (int type : mediaTypes()) {
            try {
                walk(FileLoader.checkDirectory(type), roots, report, seen);
            } catch (Throwable ignore) {
            }
        }
    }

    private static void deleteMedia(Roots roots, HashSet<String> ids, HashSet<String> seen, long[] freed, int[] count) {
        for (int type : mediaTypes()) {
            try {
                deleteWalk(FileLoader.checkDirectory(type), roots, ids, seen, freed, count);
            } catch (Throwable ignore) {
            }
        }
    }

    private static int[] mediaTypes() {
        return new int[]{
            FileLoader.MEDIA_DIR_IMAGE,
            FileLoader.MEDIA_DIR_AUDIO,
            FileLoader.MEDIA_DIR_VIDEO,
            FileLoader.MEDIA_DIR_DOCUMENT,
            FileLoader.MEDIA_DIR_CACHE,
            FileLoader.MEDIA_DIR_FILES,
            FileLoader.MEDIA_DIR_STORIES,
            FileLoader.MEDIA_DIR_IMAGE_PUBLIC,
            FileLoader.MEDIA_DIR_VIDEO_PUBLIC
        };
    }

    private static void deleteExternal(Context context, Roots roots, HashSet<String> ids, HashSet<String> seen,
                                       long[] freed, int[] count) {
        try {
            final File[] caches = context.getExternalCacheDirs();
            if (caches != null) {
                for (File file : caches) {
                    deleteWalk(file, roots, ids, seen, freed, count);
                }
            }
        } catch (Throwable ignore) {
        }
        try {
            final File[] files = context.getExternalFilesDirs(null);
            if (files != null) {
                for (File file : files) {
                    deleteWalk(file, roots, ids, seen, freed, count);
                }
            }
        } catch (Throwable ignore) {
        }
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                final File[] media = context.getExternalMediaDirs();
                if (media != null) {
                    for (File file : media) {
                        deleteWalk(file, roots, ids, seen, freed, count);
                    }
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void deleteWalk(File dir, Roots roots, HashSet<String> ids, HashSet<String> seen,
                                   long[] freed, int[] count) {
        if (dir == null || !dir.exists() || isLink(dir)) {
            return;
        }
        final File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            if (kid == null || isLink(kid)) {
                continue;
            }
            if (kid.isDirectory()) {
                deleteWalk(kid, roots, ids, seen, freed, count);
                continue;
            }
            final String path = kid.getAbsolutePath();
            if (!seen.add(path) || kept(kid, path)) {
                continue;
            }
            final String id = category(kid, path, roots);
            if (id == null || !ids.contains(id) || !clearable(id) || !allowed(path, roots)) {
                continue;
            }
            final long length = kid.length();
            if (kid.delete()) {
                freed[0] += Math.max(0L, length);
                count[0]++;
            }
        }
    }

    static boolean kept(File file, String path) {
        final String name = file.getName().toLowerCase(Locale.US);
        if (name.contains("tgnet")) {
            return true;
        }
        if (name.startsWith("userconfig") || name.startsWith("mainconfig")) {
            return true;
        }
        if (name.startsWith("cache4.db") || name.startsWith("file_to_path")) {
            return true;
        }
        if (".nomedia".equals(name) || "cookies".equals(name) || "cookies-journal".equals(name)) {
            return true;
        }
        if (path.contains("/shared_prefs/") || path.contains("/lib/")) {
            return true;
        }
        return inAccountDir(path);
    }

    private static boolean inAccountDir(String path) {
        int from = 0;
        while (from < path.length()) {
            final int at = path.indexOf("/account", from);
            if (at < 0) {
                return false;
            }
            int cursor = at + "/account".length();
            final int start = cursor;
            while (cursor < path.length() && Character.isDigit(path.charAt(cursor))) {
                cursor++;
            }
            if (cursor > start && cursor < path.length() && path.charAt(cursor) == '/') {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    private static String category(File file, String path, Roots roots) {
        final String name = file.getName().toLowerCase(Locale.US);
        if (".nomedia".equals(name)) {
            return null;
        }
        if (name.contains("tgnet") || name.startsWith("userconfig") || name.startsWith("mainconfig")
            || "cookies".equals(name) || "cookies-journal".equals(name)) {
            return ID_SESSION;
        }
        if (path.contains("/shared_prefs/") || path.contains("/lib/")) {
            return ID_SESSION;
        }
        if (name.startsWith("cache4.db")) {
            return ID_DATABASE;
        }
        if (name.startsWith("file_to_path")) {
            return ID_INDEX;
        }
        if (inAccountDir(path)) {
            return ID_SESSION;
        }
        if (isTemp(name)) {
            return ID_TEMP;
        }
        String bestId = null;
        int best = -1;
        for (int i = 0; i < roots.dirs.size(); i++) {
            final Dir dir = roots.dirs.get(i);
            if (under(path, dir.path) && dir.path.length() > best) {
                best = dir.path.length();
                bestId = dir.id;
            }
        }
        if (bestId != null) {
            return bestId;
        }
        final String named = namedTelegram(path);
        if (named != null) {
            return named;
        }
        if (underApp(path, roots)) {
            return ID_OTHER;
        }
        return null;
    }

    private static String namedTelegram(String path) {
        if (path.contains("/Telegram Images/")) return ID_PHOTO;
        if (path.contains("/Telegram Video/")) return ID_VIDEO;
        if (path.contains("/Telegram Audio/")) return ID_AUDIO;
        if (path.contains("/Telegram Documents/")) return ID_DOCUMENT;
        if (path.contains("/Telegram Files/")) return ID_FILES;
        if (path.contains("/Telegram Stories/")) return ID_STORIES;
        return null;
    }

    private static boolean isTemp(String name) {
        return name.endsWith(".temp") || name.endsWith(".tmp") || name.endsWith(".part")
            || name.endsWith(".preload") || name.endsWith(".pt") || name.endsWith(".download")
            || name.endsWith(".partial");
    }

    private static boolean under(String path, String root) {
        if (path == null || root == null || root.length() < 2) {
            return false;
        }
        if (path.equals(root)) {
            return true;
        }
        final String prefix = root.endsWith("/") ? root : root + "/";
        return path.startsWith(prefix);
    }

    private static boolean underApp(String path, Roots roots) {
        for (int i = 0; i < roots.appRoots.size(); i++) {
            if (under(path, roots.appRoots.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static long hiddenDiff(Report report) {
        try {
            final File cache = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
            long visible = 0L;
            boolean counted = false;
            counted |= addNative(cache, 5);
            visible += nativeSize(cache, 5);
            visible += nativeSize(cache, 4);
            visible += nativeSize(cache, 3);
            visible += nativeSize(cache == null ? null : new File(cache, "acache"), 0);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), 0);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), 0);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), 0);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), 0);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 1);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 1);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 2);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 2);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), 0);
            visible += nativeSize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_STORIES), 0);
            long logs = nativeSize(AndroidUtilities.getLogsDir(), 1);
            if (logs > 0L) {
                counted = true;
            }
            if (!BuildVars.DEBUG_VERSION && logs < 256L * 1024L * 1024L) {
                logs = 0L;
            }
            visible += logs;
            if (visible > 0L) {
                counted = true;
            }
            if (!counted) {
                return -1L;
            }
            long raw = report.bytes(ID_CACHE) + report.bytes(ID_TEMP) + report.bytes(ID_STICKERS)
                + report.bytes(ID_PHOTO) + report.bytes(ID_VIDEO) + report.bytes(ID_AUDIO)
                + report.bytes(ID_DOCUMENT) + report.bytes(ID_FILES) + report.bytes(ID_STORIES)
                + report.bytes(ID_GALLERY) + report.bytes(ID_LOGS);
            return Math.max(0L, raw - visible);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return -1L;
        }
    }

    private static boolean addNative(File dir, int type) {
        return dir != null && dir.exists();
    }

    private static boolean allowed(String path, Roots roots) {
        if (underApp(path, roots)) {
            return true;
        }
        for (int i = 0; i < roots.dirs.size(); i++) {
            if (under(path, roots.dirs.get(i).path)) {
                return true;
            }
        }
        return false;
    }

    private static long nativeSize(File dir, int type) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        try {
            return Math.max(0L, Utilities.getDirSize(dir.getAbsolutePath(), type, true));
        } catch (Throwable throwable) {
            return 0L;
        }
    }

    private static File dataDir() {
        try {
            final File files = ApplicationLoader.getFilesDirFixed();
            if (files != null) {
                final File parent = files.getParentFile();
                if (parent != null) {
                    return parent;
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static boolean isLink(File file) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                return java.nio.file.Files.isSymbolicLink(file.toPath());
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    private static TextView text(Context context, int sp, int color, boolean bold) {
        final TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? AndroidUtilities.bold() : android.graphics.Typeface.DEFAULT);
        view.setLineSpacing(AndroidUtilities.dp(2), 1f);
        return view;
    }

    private static TextView button(Context context, String label, boolean primary) {
        final TextView view = text(context, 14, primary ? ThemeHook.onAccent() : ThemeHook.primaryText(), true);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        final GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? ThemeHook.accent() : ThemeHook.surfaceNested());
        background.setCornerRadius(AndroidUtilities.dp(16));
        if (!primary) {
            background.setStroke(Math.max(1, AndroidUtilities.dp(1)), ThemeHook.separator());
        }
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private static LinearLayout card(Context context) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        final GradientDrawable background = new GradientDrawable();
        background.setColor(ThemeHook.surfaceNested());
        background.setCornerRadius(AndroidUtilities.dp(14));
        background.setStroke(Math.max(1, AndroidUtilities.dp(1)), ThemeHook.separator());
        card.setBackground(background);
        return card;
    }

    private static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams matchTop(int top) {
        final LinearLayout.LayoutParams params = match();
        params.topMargin = AndroidUtilities.dp(top);
        return params;
    }

    private static LinearLayout.LayoutParams buttonParams() {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(46));
        params.topMargin = AndroidUtilities.dp(8);
        return params;
    }
}
