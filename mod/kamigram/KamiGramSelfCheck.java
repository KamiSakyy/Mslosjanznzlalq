package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

/**
 * KamiGram: защита от вылетов на старте («аварийный режим»).
 *
 * Зачем: любая правка, которая работает в самом начале запуска, теоретически
 * может уронить приложение — и тогда пользователь видит только вылет и не может
 * даже открыть настройки, чтобы что-то выключить. Чтобы такого больше не было:
 *
 *   * при каждом старте счётчик запусков увеличивается;
 *   * если приложение успешно дожило до рабочего экрана — счётчик сбрасывается;
 *   * если запусков подряд набралось три без успеха, включается АВАРИЙНЫЙ РЕЖИМ:
 *     мод пропускает свои тяжёлые стартовые шаги (правки оформления, авто-прокси,
 *     фоновые проверки), и приложение гарантированно открывается;
 *   * в аварийном режиме всё остальное (текст, чаты, вход) работает как обычно,
 *     а в центре KamiGram видно, что режим включён, и его можно выключить вручную.
 *
 * Состояние хранится в настройках приложения, поэтому переживает перезапуск.
 */
public final class KamiGramSelfCheck {

    private static final String KEY_BOOT_COUNT = "kamigram_boot_count";
    private static final String KEY_SAFE_MODE = "kamigram_safe_mode";
    /** Сколько неудачных запусков подряд включают аварийный режим. */
    private static final int BOOT_LIMIT = 3;
    /** Через сколько секунд рабочий экран считается поднявшимся. */
    private static final long ALIVE_DELAY = 8000L;

    private static boolean safeMode;
    private static boolean loaded;

    private KamiGramSelfCheck() {
    }

    private static SharedPreferences prefs(Context context) {
        // Важно: на самом первом шаге запуска контекст приложения ещё не готов,
        // поэтому берём настройки напрямую у контекста, который нам передали.
        try {
            if (context != null) {
                return context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
            }
        } catch (Throwable ignore) {
        }
        try {
            return MessagesController.getGlobalMainSettings();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static SharedPreferences prefs() {
        try {
            if (ApplicationLoader.applicationContext != null) {
                return ApplicationLoader.applicationContext
                    .getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
            }
        } catch (Throwable ignore) {
        }
        return prefs(null);
    }

    /** Вызывается самым первым при запуске приложения. Никогда не бросает. */
    public static void beforeStart(Context context) {
        try {
            final SharedPreferences preferences = prefs(context);
            if (preferences == null) {
                return;
            }
            final int boots = preferences.getInt(KEY_BOOT_COUNT, 0) + 1;
            final SharedPreferences.Editor editor = preferences.edit().putInt(KEY_BOOT_COUNT, boots);
            if (boots >= BOOT_LIMIT) {
                editor.putBoolean(KEY_SAFE_MODE, true);
                editor.putInt(KEY_GOOD_RUNS, 0);
            }
            editor.apply();
            safeMode = preferences.getBoolean(KEY_SAFE_MODE, false);
            loaded = true;
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Аварийный режим включён: тяжёлые стартовые шаги пропускаем. */
    public static boolean safeMode() {
        if (!loaded) {
            try {
                final SharedPreferences preferences = prefs();
                if (preferences != null) {
                    safeMode = preferences.getBoolean(KEY_SAFE_MODE, false);
                }
            } catch (Throwable ignore) {
            }
            loaded = true;
        }
        return safeMode;
    }

    private static final String KEY_GOOD_RUNS = "kamigram_good_runs";
    private static boolean marked;

    /**
     * Приложение дожило до рабочего экрана: сбрасываем счётчик неудач.
     *
     * Раньше аварийный режим, включившись один раз, оставался НАВСЕГДА — из-за
     * этого оформление и прокси могли больше не применяться. Теперь после двух
     * подряд удачных запусков он выключается сам.
     */
    public static void markBooted() {
        try {
            if (marked) {
                return;
            }
            marked = true;
            final SharedPreferences preferences = prefs();
            if (preferences == null) {
                return;
            }
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putInt(KEY_BOOT_COUNT, 0);
            final int good = preferences.getInt(KEY_GOOD_RUNS, 0) + 1;
            editor.putInt(KEY_GOOD_RUNS, good);
            if (good >= 2) {
                editor.putBoolean(KEY_SAFE_MODE, false);
                safeMode = false;
                loaded = true;
            }
            editor.apply();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Вызывается из главного экрана: если через несколько секунд всё живо — успех. */
    public static void onLaunchStart(Context context) {
        try {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    markBooted();
                }
            }, ALIVE_DELAY);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Состояние для центра мода. */
    public static String describe() {
        return safeMode() ? "аварийный режим включён" : "всё в порядке";
    }
}
