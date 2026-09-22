package org.telegram.messenger.kamigram;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.ActionBar;

import java.lang.ref.WeakReference;

/**
 * KamiGram: имя приложения в шапке главного экрана.
 *
 * Замечания пользователя:
 *   * «при включённом прокси название KamiGram исчезает» — родной оверлей Telegram
 *     подменял заголовок строкой «Подключение к прокси…»;
 *   * «убери надпись Подключение… зачем она, она бесконечная даже если уже подключено».
 *
 * Поэтому: имя приложения теперь НИКОГДА не подменяется и НИКАКОГО текста
 * состояния рядом с ним нет. Ни «подключение», ни «подключено», ни «нет сети» —
 * только название, как и просил пользователь. Состояние связи видно по родной
 * кнопке «Прокси» у трёх точек (её рисует сам Telegram).
 */
public final class KamiGramProxyStatus {

    /** Базовая строка заголовка («KamiGram» + логотип). */
    private static WeakReference<CharSequence> baseTitle = new WeakReference<>(null);
    private static WeakReference<ActionBar> actionBarRef = new WeakReference<>(null);
    private static WeakReference<Drawable> rightDrawableRef = new WeakReference<>(null);

    private KamiGramProxyStatus() {
    }

    /** Заголовок главного экрана создан: просто запоминаем его и держим на месте. */
    public static void attach(ActionBar actionBar, CharSequence base, Drawable rightDrawable) {
        try {
            if (base != null) {
                baseTitle = new WeakReference<>(base);
            }
            if (actionBar != null) {
                actionBarRef = new WeakReference<>(actionBar);
            }
            rightDrawableRef = new WeakReference<>(rightDrawable);
            apply();
        } catch (Throwable ignore) {
        }
    }

    /** Никаких подписей состояния — оставлено для совместимости с патчами. */
    public static void setHint(String value) {
        // намеренно ничего не делаем: пользователь просил убрать надписи полностью
    }

    /** Проверка, что название на месте (например после смены темы). */
    public static void refresh() {
        apply();
    }

    private static void apply() {
        try {
            final ActionBar actionBar = actionBarRef.get();
            final CharSequence base = baseTitle.get();
            if (actionBar == null || base == null) {
                return;
            }
            // имя приложения без каких-либо приписок
            actionBar.setTitle(base instanceof SpannableStringBuilder ? base : new SpannableStringBuilder(base),
                rightDrawableRef.get());
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Имя приложения (для окон и подсказок). */
    public static CharSequence appName() {
        try {
            return ApplicationLoader.applicationContext.getString(
                org.telegram.messenger.R.string.AppName);
        } catch (Throwable ignore) {
            return "KamiGram";
        }
    }
}
