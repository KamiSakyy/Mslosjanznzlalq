package org.telegram.messenger.kamigram;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;

import org.telegram.messenger.ApplicationLoader;
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

    /**
     * Базовая строка заголовка («KamiGram») и сама шапка.
     *
     * r68: держим ОБЫЧНЫЕ (сильные) ссылки. Раньше здесь были WeakReference, и
     * после сборки мусора «базовая» строка исчезала — заголовок оставался пустым
     * (пользователь видел «название KamiGram пропадает»). Теперь строка и шапка
     * всегда под рукой: приложение всё равно живёт с ними до самого выхода.
     */
    private static CharSequence baseTitle = null;
    private static String baseText = "KamiGram";
    private static ActionBar actionBarRef = null;
    private static Drawable rightDrawableRef = null;

    private KamiGramProxyStatus() {
    }

    /** Заголовок главного экрана создан: просто запоминаем его и держим на месте. */
    public static void attach(ActionBar actionBar, CharSequence base, Drawable rightDrawable) {
        try {
            if (base != null) {
                baseTitle = base;
                final String text = base.toString();
                if (text != null && text.length() > 0) {
                    baseText = text;
                }
            }
            if (actionBar != null) {
                actionBarRef = actionBar;
            }
            rightDrawableRef = rightDrawable;
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
            final ActionBar actionBar = actionBarRef;
            CharSequence base = baseTitle;
            if (base == null || base.length() == 0) {
                // строку могло унести сборщиком мусора — восстанавливаем из текста
                base = baseText != null ? baseText : "KamiGram";
            }
            if (actionBar == null) {
                return;
            }
            // имя приложения без каких-либо приписок; ставим только если оно реально другое
            // (повторная установка того же заголовка заставляла шапку переразмечаться и мигать)
            final CharSequence current = actionBar.getTitle();
            if (current != null && current.length() > 0 && current.toString().contentEquals(base)) {
                return;
            }
            /* KAMIGRAM_TITLE_BACK: заголовок главного экрана всегда возвращаем на место —
               если его подменило состояние соединения (прокси), имя KamiGram возвращается. */
            actionBar.setTitle(base instanceof SpannableStringBuilder ? base : new SpannableStringBuilder(base),
                rightDrawableRef);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
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
