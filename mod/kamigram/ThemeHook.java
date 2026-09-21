package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * KamiGram: оформление «iOS 2026» — правильная версия.
 *
 * В прошлой сборке цвета задавались кодом целиком: фон карточек делали
 * чёрным (Theme.setColor(key_windowBackgroundWhite, 0xFF000000)), а имя чата
 * тоже чёрным — получался чёрный текст на чёрном фоне. Здесь так нельзя:
 *
 *   * ВСЕ поверхности, тексты, облака и разделители приходят из assets-темы
 *     (mod/kamigram/apply_theme_pro.py переписывает родную тёмную тему
 *     Telegram на iOS-палитру — там каждый текст согласован со своим фоном).
 *   * Код трогает ТОЛЬКО акценты: синяя кнопка отправки, галочки, прогресс,
 *     ссылки, переключатели. Ни одного фонового или текстового ключа.
 *   * Тема «Night» (наш iOS-набор) держится всегда, поэтому система не может
 *     вернуть светлую тему и белый фон с белым текстом.
 *
 * Плюс мелочи iOS: прозрачная строка состояния, светлые иконки, отсутствие
 * «стекла» и градиентов в шапке.
 */
public final class ThemeHook {

    /** Встроенная тёмная тема Telegram, в которую P16 записал iOS-палитру. */
    private static final String THEME_KEY = "Night";

    private static final ArrayList<WeakReference<Activity>> ACTIVITIES = new ArrayList<>();

    private ThemeHook() {
    }

    // ------------------------------------------------------------------ тема

    /**
     * Держим тёмную iOS-тему. Если пользователь поставил свою тему — уважаем
     * её и ничего не ломаем: тогда применяются только акценты мода.
     */
    public static void keepDarkTheme() {
        try {
            if (Theme.getActiveTheme() != null && Theme.isCurrentThemeDark()) {
                return;
            }
            final Theme.ThemeInfo info = Theme.getTheme(THEME_KEY);
            if (info != null) {
                Theme.applyTheme(info, true);
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Тёмная тема сейчас? */
    public static boolean isDark(Context context) {
        try {
            if (Theme.isCurrentThemeDark()) {
                return true;
            }
            if (context != null) {
                final int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
                return mode == Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignore) {
        }
        return true;
    }

    // ------------------------------------------------------------------ акценты

    /** Акцент из настроек мода (по умолчанию iOS-синий #0A84FF). */
    public static int accent() {
        try {
            return KamiGramConfig.accentColor();
        } catch (Throwable ignore) {
            return 0xFF0A84FF;
        }
    }

    /**
     * Применить акцент пользователя к акцентным ключам Telegram.
     * Тут НЕТ ни одного ключа фона/текста — только «цветные» элементы,
     * поэтому читаемость не может пострадать.
     */
    public static void applyAccent() {
        final int accent = accent();
        final int soft = (accent & 0x00FFFFFF) | 0x33000000;
        final int soft22 = (accent & 0x00FFFFFF) | 0x22000000;

        set(Theme.key_dialogTextBlue, accent);
        set(Theme.key_dialogTextBlue2, accent);
        set(Theme.key_dialogTextBlue4, accent);
        set(Theme.key_dialogTextLink, accent);
        set(Theme.key_dialogButton, accent);
        set(Theme.key_dialogButtonSelector, soft22);
        set(Theme.key_windowBackgroundWhiteValueText, accent);
        set(Theme.key_windowBackgroundWhiteLinkText, accent);
        set(Theme.key_windowBackgroundWhiteLinkSelection, soft);
        set(Theme.key_windowBackgroundWhiteBlueText, accent);
        set(Theme.key_windowBackgroundWhiteBlueText2, accent);
        set(Theme.key_windowBackgroundWhiteBlueText3, accent);
        set(Theme.key_windowBackgroundWhiteBlueText4, accent);
        set(Theme.key_windowBackgroundWhiteBlueText5, accent);
        set(Theme.key_chat_messageLinkIn, accent);
        set(Theme.key_chat_messageLinkOut, 0xFFA8D4FF);
        set(Theme.key_chat_messagePanelSend, accent);
        set(Theme.key_chat_inLoader, accent);
        set(Theme.key_chat_outLoader, accent);
        set(Theme.key_chat_inLoaderSelected, accent);
        set(Theme.key_chat_outLoaderSelected, accent);
        set(Theme.key_profile_actionIcon, accent);
        set(Theme.key_profile_creatorIcon, accent);
        set(Theme.key_chat_replyPanelName, accent);
        set(Theme.key_checkbox, accent);
        set(Theme.key_fastScrollActive, accent);
        set(Theme.key_progressCircle, accent);
        set(Theme.key_player_progress, accent);
        set(Theme.key_player_buttonActive, accent);
        set(Theme.key_featuredStickers_addButton, accent);
        set(Theme.key_featuredStickers_addedIcon, accent);
        set(Theme.key_chat_recordedVoiceProgress, accent);
        set(Theme.key_contextProgressOuter1, accent);
        set(Theme.key_switchTrackChecked, 0xFF34C759);
        set(Theme.key_switchTrackBlueChecked, 0xFF34C759);
        set(Theme.key_windowBackgroundChecked, 0xFF34C759);
        set(Theme.key_radioBackgroundChecked, accent);
        set(Theme.key_checkboxSquareBackground, accent);
        set(Theme.key_chat_messagePanelVoicePressed, 0xFFFF453A);
    }

    private static void set(int key, int color) {
        try {
            Theme.setColor(key, color, false);
        } catch (Throwable ignore) {
        }
    }

    /** Пользователь поменял цвет — обновляем все открытые экраны. */
    public static void notifyAccentChanged() {
        applyAccent();
        for (int i = ACTIVITIES.size() - 1; i >= 0; i--) {
            final Activity current = ACTIVITIES.get(i).get();
            if (current == null) {
                ACTIVITIES.remove(i);
                continue;
            }
            try {
                current.recreate();
            } catch (Throwable ignore) {
            }
        }
    }

    // ------------------------------------------------------------------ экраны

    /** Вызывать после создания окна экрана: тема, полосы, акценты. */
    public static void apply(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            register(activity);
            keepDarkTheme();
            applyAccent();
            tintSystemBars(activity);
            applySecureFlag(activity);
            KamiGramTweaks.apply();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    public static void forget(Activity activity) {
        if (activity == null) {
            return;
        }
        for (int i = ACTIVITIES.size() - 1; i >= 0; i--) {
            final Activity current = ACTIVITIES.get(i).get();
            if (current == null || current == activity) {
                ACTIVITIES.remove(i);
            }
        }
    }

    private static void register(Activity activity) {
        for (WeakReference<Activity> reference : ACTIVITIES) {
            if (reference.get() == activity) {
                return;
            }
        }
        ACTIVITIES.add(new WeakReference<>(activity));
    }

    /** Прозрачный статус-бар, тёмная навигация, светлые иконки — как в iOS. */
    public static void tintSystemBars(Activity activity) {
        try {
            final Window window = activity.getWindow();
            if (window == null) {
                return;
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(0xFF000000);
            final View decor = window.getDecorView();
            if (Build.VERSION.SDK_INT >= 23) {
                decor.setSystemUiVisibility(0); // светлые иконки в тёмных полосах
            }
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
                window.setNavigationBarDividerColor(0x00000000);
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Запрет скриншотов и превью в списке задач — если пользователь включил
     * это в центре мода. По умолчанию выключено: ничего не меняем.
     */
    public static void applySecureFlag(Activity activity) {
        try {
            if (KamiGramConfig.noScreenshots()) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------------------ цвета для вьюх мода

    public static int surface() {
        return 0xFF1C1C1E;
    }

    public static int surfaceNested() {
        return 0xFF2C2C2E;
    }

    public static int background() {
        return 0xFF000000;
    }

    public static int primaryText() {
        return 0xFFFFFFFF;
    }

    public static int secondaryText() {
        return 0xFF8E8E93;
    }

    public static int separator() {
        return 0xFF38383A;
    }

    public static int green() {
        return 0xFF34C759;
    }

    public static int red() {
        return 0xFFFF453A;
    }

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    /** «Стекло» и размытие в моде выключены — как в iOS. */
    public static boolean allowBlur() {
        return false;
    }

    public static String designVersion() {
        return "KamiGram iOS 2026.2";
    }

    /** Включить/выключить принудительную тёмную тему (для экрана мода). */
    public static void setDark(boolean dark) {
        KamiGramConfig.set(KamiGramConfig.KEY_IOS_DESIGN, dark);
        keepDarkTheme();
    }
}
