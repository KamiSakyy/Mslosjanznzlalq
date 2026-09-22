package org.telegram.messenger.kamigram;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;

import java.lang.ref.WeakReference;

/**
 * KamiGram: название приложения и состояние прокси в шапке главного экрана.
 *
 * Замечание пользователя: «при включённом прокси название KamiGram исчезает».
 * Причина — родной оверлей Telegram: пока соединение идёт через прокси,
 * Telegram подменял заголовок строкой «Подключение к прокси…». Теперь оверлей
 * не используется: имя приложения остаётся на месте, а состояние прокси
 * дописывается РЯДОМ с названием тем же мелким приглушённым шрифтом.
 */
public final class KamiGramProxyStatus {

    /** Базовая строка заголовка («KamiGram» + логотип) — без состояния прокси. */
    private static WeakReference<CharSequence> baseTitle = new WeakReference<>(null);
    private static WeakReference<ActionBar> actionBarRef = new WeakReference<>(null);
    private static WeakReference<Drawable> rightDrawableRef = new WeakReference<>(null);
    /** Временная подсказка («подключение…», «нет сети») — приоритетнее статуса прокси. */
    private static String hint;

    private KamiGramProxyStatus() {
    }

    /** Заголовок главного экрана создан: запоминаем базу и оформляем её. */
    public static void attach(ActionBar actionBar, CharSequence base, Drawable rightDrawable) {
        try {
            if (base != null) {
                baseTitle = new WeakReference<>(base);
            }
            actionBarRef = new WeakReference<>(actionBar);
            rightDrawableRef = new WeakReference<>(rightDrawable);
            apply();
        } catch (Throwable ignore) {
        }
    }

    /** Состояние связи изменилось (родной оверлей больше не подменяет название). */
    public static void setHint(String value) {
        hint = value;
        apply();
    }

    /** Прокси включили/выключили или соединение установилось. */
    public static void refresh() {
        apply();
    }

    private static void apply() {
        try {
            final ActionBar actionBar = actionBarRef.get();
            if (actionBar == null) {
                return;
            }
            final CharSequence base = baseTitle.get();
            if (base == null) {
                return;
            }
            final String status = status();
            if (status == null) {
                actionBar.setTitle(base, rightDrawableRef.get());
                return;
            }
            final SpannableStringBuilder builder = new SpannableStringBuilder(base);
            builder.append("  ");
            final int start = builder.length();
            builder.append(status);
            builder.setSpan(new RelativeSizeSpan(0.72f), start, builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(KamiGramUi.secondaryText()), start, builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            actionBar.setTitle(builder, rightDrawableRef.get());
        } catch (Throwable ignore) {
        }
    }

    /** Строка состояния: подсказка связи важнее статуса прокси. */
    private static String status() {
        if (hint != null) {
            return hint;
        }
        try {
            final boolean proxyEnabled = ApplicationLoader.applicationContext
                .getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                .getBoolean("proxy_enabled", false);
            if (!proxyEnabled) {
                return null;
            }
            final int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
            final boolean connected = state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating;
            return connected ? "прокси подключён" : "прокси: подключение…";
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Заголовок приложения для окна задач/подсказок. */
    public static CharSequence appName() {
        try {
            return LocaleController.getString(R.string.AppName);
        } catch (Throwable ignore) {
            return "KamiGram";
        }
    }

    /** Небольшая утилита: показать подсказку связи через секунду после старта. */
    public static void clearHintDelayed() {
        AndroidUtilities.runOnUIThread(() -> setHint(null), 1500);
    }
}
