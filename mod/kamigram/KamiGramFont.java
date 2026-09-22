package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * KamiGram: свой шрифт приложения.
 *
 * Пользователь выбирает файл .ttf (или .otf) через проводник Google
 * (системный выбор документа — SAF), файл копируется во внутреннюю папку
 * приложения, и с этого момента ЛЮБОЙ текст интерфейса рисуется этим шрифтом:
 * перехватываются обе точки, где Telegram берёт шрифты —
 * {@link AndroidUtilities#getTypeface(String)} (обычный текст) и
 * {@link AndroidUtilities#bold()} (полужирный), плюс сбрасывается кэш шрифта.
 *
 * Кнопка «Сбросить шрифт» возвращает родные шрифты Telegram.
 */
public final class KamiGramFont {

    /** Код запроса для проводника Google (SAF). */
    public static final int REQUEST_CODE = 0x4B46;

    private static final String FILE_NAME = "kamigram_font.bin";

    private static Typeface cachedRegular;
    private static Typeface cachedBold;
    private static boolean loaded;
    private static String cachedName;

    private KamiGramFont() {
    }

    private static File file() {
        try {
            return new File(ApplicationLoader.applicationContext.getFilesDir(), FILE_NAME);
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Установлен ли свой шрифт. */
    public static boolean installed() {
        try {
            final File target = file();
            return target != null && target.exists() && target.length() > 512;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Обычное начертание — весь текст интерфейса. */
    public static Typeface regular() {
        if (!installed()) {
            return null;
        }
        if (!loaded) {
            loaded = true;
            try {
                cachedRegular = Typeface.createFromFile(file());
            } catch (Throwable throwable) {
                FileLog.e(throwable);
                cachedRegular = null;
            }
        }
        return cachedRegular;
    }

    /** Полужирное начертание — заголовки и акценты. */
    public static Typeface bold() {
        final Typeface base = regular();
        if (base == null) {
            return null;
        }
        if (cachedBold == null) {
            try {
                cachedBold = Typeface.create(base, Typeface.BOLD);
            } catch (Throwable ignore) {
                cachedBold = base;
            }
        }
        return cachedBold;
    }

    /** Начертание для конкретной роли (medium/rbold/italic) — как в Telegram. */
    public static Typeface forAsset(String assetPath) {
        if (org.telegram.messenger.kamigram.ThemeHook.uiHooksDisabled()) {
            return null;
        }
        final Typeface base = regular();
        if (base == null) {
            return null;
        }
        try {
            if (assetPath != null) {
                if (assetPath.contains("italic")) {
                    return Typeface.create(base, Typeface.ITALIC);
                }
                if (assetPath.contains("medium") || assetPath.contains("rbold") || assetPath.contains("rextrabold")) {
                    return bold();
                }
            }
        } catch (Throwable ignore) {
        }
        return base;
    }

    /** Выбор .ttf через проводник Google (системный «Открыть документ»). */
    public static void pick(Context context) {
        try {
            final Activity activity = AndroidUtilities.findActivity(context);
            if (activity == null) {
                KamiGramUi.notify(context, "Не удалось открыть проводник");
                return;
            }
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"});
            activity.startActivityForResult(intent, REQUEST_CODE);
            KamiGramUi.notify(context, "Выберите файл шрифта .ttf");
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            KamiGramUi.notify(context, "Проводник недоступен");
        }
    }

    /**
     * Результат выбора файла. Вызывает LaunchActivity — единственное место,
     * куда Android присылает ответ проводника.
     */
    public static boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        final Uri uri = data.getData();
        final Context context = ApplicationLoader.applicationContext;
        try {
            try {
                context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignore) {
                // необязательно: файл всё равно копируется во внутреннюю папку
            }
            final InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                throw new IllegalStateException("нет доступа к файлу");
            }
            final File target = file();
            if (target == null) {
                throw new IllegalStateException("нет внутренней папки");
            }
            final FileOutputStream output = new FileOutputStream(target);
            final byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
            output.close();
            input.close();
            cachedName = displayName(context, uri);
            loaded = false;
            cachedRegular = null;
            cachedBold = null;
            apply();
            onFontChanged();
            KamiGramUi.notify(context, "Шрифт применён: " + describe());
            return true;
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            KamiGramUi.notify(context, "Не удалось прочитать файл шрифта");
            return true;
        }
    }

    /** Сбросить шрифт: назад к родным шрифтам Telegram. */
    public static void reset() {
        try {
            final File target = file();
            if (target != null && target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            cachedName = null;
            loaded = false;
            cachedRegular = null;
            cachedBold = null;
            apply();
            onFontChanged();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /**
     * Применить шрифт к «краскам» Telegram. Экраны НЕ пересоздаются: этот метод
     * вызывается и при старте, и на каждом показе экрана, а пересоздание из
     * жизненного цикла давало бесконечный цикл — приложение мерцало.
     */
    public static void apply() {
        try {
            AndroidUtilities.mediumTypeface = null;
            PAINT_STYLE.clear();
            clearStyles();
            typedGeneration = -1;
            applyToTheme();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Поколение шрифта: растёт при выборе/сбросе, чтобы не перекрашивать зря. */
    private static int generation = 1;
    private static int typedGeneration = -1;

    /** Перекрасить «краски» только если шрифт менялся с прошлого раза. */
    public static void applyToThemeIfNeeded() {
        if (typedGeneration == generation) {
            return;
        }
        typedGeneration = generation;
        applyToTheme();
    }

    /**
     * Пользователь сменил или сбросил шрифт: перекрашиваем и один раз
     * обновляем экраны (защита от повторов — в ThemeHook.recreateScreensOnce).
     */
    private static void onFontChanged() {
        generation++;
        typedGeneration = generation;
        try {
            AndroidUtilities.mediumTypeface = null;
            PAINT_STYLE.clear();
            clearStyles();
            applyToTheme();
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                .postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, 0);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Подпись для центра мода. */
    public static String describe() {
        if (!installed()) {
            return "родной шрифт Telegram";
        }
        try {
            if (cachedName == null) {
                cachedName = "выбранный .ttf";
            }
            final File target = file();
            final long size = target == null ? 0 : target.length();
            return cachedName + (size > 0 ? " · " + (size / 1024) + " КБ" : "");
        } catch (Throwable ignore) {
            return "выбранный .ttf";
        }
    }

    // ------------------------------------------------------------------ шрифт ВЕЗДЕ

    /** Стиль (полужирный/курсив), который был у «краски» до подмены шрифта. */
    private static final IdentityHashMap<Paint, Integer> PAINT_STYLE = new IdentityHashMap<>();

    /** Готовые начертания (обычный/жирный/курсив/жирный курсив) — создаются один раз. */
    private static final Typeface[] STYLES = new Typeface[4];

    private static void clearStyles() {
        for (int i = 0; i < STYLES.length; i++) {
            STYLES[i] = null;
        }
    }

    /** Начертание нашего шрифта: один и тот же объект, поэтому повторных переразметок нет. */
    private static Typeface styled(int style) {
        int index = style & (Typeface.BOLD | Typeface.ITALIC);
        Typeface typeface = STYLES[index];
        if (typeface == null) {
            if (index == Typeface.NORMAL) {
                typeface = regular();
            } else {
                typeface = Typeface.create(regular(), index);
            }
            STYLES[index] = typeface;
        }
        return typeface;
    }

    /**
     * Подменить шрифт во ВСЕХ текстовых «красках» Telegram (сообщения, список
     * чатов, профиль, подписи) — это те места, где текст рисуется кодом, а не
     * обычным TextView. Начертание каждого шрифта запоминается: заголовки
     * остаются полужирными, курсив — курсивом.
     */
    public static void applyToTheme() {
        final Typeface base = regular();
        if (base == null) {
            return;
        }
        try {
            applyToPaints(org.telegram.ui.ActionBar.Theme.class);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void applyToPaints(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        final Field[] fields = clazz.getDeclaredFields();
        for (int a = 0; a < fields.length; a++) {
            final Field field = fields[a];
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final String name = field.getName().toLowerCase();
            if (name.contains("code") || name.contains("mono")) {
                // код и моноширинный текст должны остаться моноширинными
                continue;
            }
            final Class<?> type = field.getType();
            try {
                if (type == TextPaint.class || type == Paint.class) {
                    field.setAccessible(true);
                    final Object value = field.get(null);
                    if (value instanceof Paint) {
                        stylePaint((Paint) value, false);
                    }
                } else if (type.isArray()) {
                    final Class<?> component = type.getComponentType();
                    if (component == TextPaint.class || component == Paint.class) {
                        field.setAccessible(true);
                        final Object array = field.get(null);
                        if (array != null) {
                            final int size = Array.getLength(array);
                            for (int b = 0; b < size; b++) {
                                final Object value = Array.get(array, b);
                                if (value instanceof Paint) {
                                    stylePaint((Paint) value, false);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static void stylePaint(Paint paint, boolean forceBold) {
        final Typeface base = regular();
        if (paint == null || base == null) {
            return;
        }
        try {
            Integer style = PAINT_STYLE.get(paint);
            if (style == null) {
                int value = Typeface.NORMAL;
                final Typeface current = paint.getTypeface();
                if (current != null) {
                    if (current.isBold()) {
                        value |= Typeface.BOLD;
                    }
                    if (current.isItalic()) {
                        value |= Typeface.ITALIC;
                    }
                }
                style = value;
                PAINT_STYLE.put(paint, style);
            }
            final int finalStyle = forceBold ? (style | Typeface.BOLD) : style;
            final Typeface want = styled(finalStyle);
            if (paint.getTypeface() != want) {
                paint.setTypeface(want);
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Подменить шрифт во всех надписях экрана (обычные TextView: настройки,
     * профили, меню, подписи). Вызывается при каждом показе экрана, поэтому
     * новый шрифт подхватывается сразу после выбора.
     */
    public static void applyToView(View view) {
        if (view == null || regular() == null) {
            return;
        }
        try {
            if (view instanceof TextView) {
                final TextView textView = (TextView) view;
                int style = Typeface.NORMAL;
                final Typeface current = textView.getTypeface();
                if (current != null) {
                    if (current.isBold()) {
                        style |= Typeface.BOLD;
                    }
                    if (current.isItalic()) {
                        style |= Typeface.ITALIC;
                    }
                }
                final Typeface want = styled(style);
                if (current != want) {
                    textView.setTypeface(want);
                }
            } else if (view instanceof ViewGroup) {
                final ViewGroup group = (ViewGroup) view;
                for (int a = 0; a < group.getChildCount(); a++) {
                    applyToView(group.getChildAt(a));
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /** Какой экран уже прошёлся по нашим рукам (и на каком поколении шрифта). */
    private static final java.util.WeakHashMap<View, Integer> VIEW_GENERATION = new java.util.WeakHashMap<>();

    /**
     * Полное применение: и «краски» Telegram (если менялись), и надписи экрана.
     * Для каждого экрана — один раз на поколение шрифта: раньше обход всех
     * надписей шёл при каждом показе экрана, из-за этого вьюхи переразмечались.
     */
    public static void applyToScreen(View view) {
        if (view == null || regular() == null
            || org.telegram.messenger.kamigram.ThemeHook.uiHooksDisabled()) {
            return;
        }
        applyToThemeIfNeeded();
        final Integer done = VIEW_GENERATION.get(view);
        if (done != null && done == generation) {
            return;
        }
        VIEW_GENERATION.put(view, generation);
        applyToView(view);
    }

    private static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    final String name = cursor.getString(index);
                    if (name != null && name.length() > 0) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignore) {
                }
            }
        }
        return "выбранный .ttf";
    }
}
