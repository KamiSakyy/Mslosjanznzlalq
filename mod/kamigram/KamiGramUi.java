package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

/**
 * KamiGram: красивый интерфейс вместо «дешёвых» уведомлений.
 *
 * Вместо системных тостов используется родной баннер Telegram (Bulletin) —
 * такой же, каким само приложение показывает «Сообщение скопировано»: тёмная
 * карточка со скруглением и иконкой, никаких серых прямоугольников Android.
 *
 * Здесь же — собственный переключатель (Toggle) в стиле iOS/Telegram: рисуется
 * кодом, поэтому он работает в любом месте, и его невозможно «сломать» темой.
 */
public final class KamiGramUi {

    private KamiGramUi() {
    }

    // ------------------------------------------------------------------ уведомления

    /** Показать родной баннер Telegram вместо тоста. */
    public static void notify(Context context, CharSequence text) {
        if (context == null || text == null || text.length() == 0) {
            return;
        }
        try {
            if (context instanceof LaunchActivity) {
                final BulletinFactory factory =
                    BulletinFactory.of(((LaunchActivity) context).actionBarLayout, null);
                Bulletin bulletin = factory.createSimpleBulletin(org.telegram.messenger.R.raw.info, text);
                bulletin.show();
                return;
            }
            final Activity activity = AndroidUtilities.findActivity(context);
            if (activity instanceof LaunchActivity) {
                final BulletinFactory factory =
                    BulletinFactory.of(((LaunchActivity) activity).actionBarLayout, null);
                factory.createSimpleBulletin(org.telegram.messenger.R.raw.info, text).show();
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Баннер с конкретной иконкой Telegram (например, «галочка»). */
    public static void notify(Context context, int rawIcon, CharSequence text) {
        try {
            if (rawIcon == 0) {
                notify(context, text);
                return;
            }
            if (context instanceof LaunchActivity) {
                BulletinFactory.of(((LaunchActivity) context).actionBarLayout, null)
                    .createSimpleBulletin(rawIcon, text).show();
            } else {
                notify(context, text);
            }
        } catch (Throwable throwable) {
            notify(context, text);
        }
    }

    // ------------------------------------------------------------------ переключатель

    /** Слушатель изменения состояния. */
    public interface OnToggle {
        void onToggle(boolean checked);
    }

    /**
     * Переключатель в стиле Telegram/iOS. Рисуется кодом: тёмная «дорожка»,
     * зелёная при включении, белая ручка. Работает от простого нажатия.
     */
    public static final class Toggle extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean checked;
        private boolean pressed;
        private float progress;
        private long lastFrame;
        private OnToggle listener;
        private boolean animate = true;

        public Toggle(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setDefaultFocusHighlightEnabled(false);
        }

        public void setChecked(boolean value) {
            setChecked(value, false);
        }

        public void setChecked(boolean value, boolean animate) {
            if (checked == value) {
                progress = value ? 1f : 0f;
                invalidate();
                return;
            }
            checked = value;
            progress = value ? 1f : 0f;
            animate = false;
            invalidate();
        }

        public boolean isChecked() {
            return checked;
        }

        public void setOnToggleListener(OnToggle value) {
            listener = value;
        }

        private void toggle() {
            checked = !checked;
            invalidate();
            if (listener != null) {
                listener.onToggle(checked);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pressed = false;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    pressed = false;
                    invalidate();
                    if (isEnabled()) {
                        toggle();
                        performClick();
                    }
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(42), AndroidUtilities.dp(25));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float h = getMeasuredHeight();
            final float w = getMeasuredWidth();
            final float radius = h / 2f;

            // дорожка: включено — фиолетовый Yoru, выключено — приглушённая #352A43
            paint.setStyle(Paint.Style.FILL);
            int track = checked ? ThemeHook.YORU_PURPLE : ThemeHook.YORU_LINE;
            if (!isEnabled()) {
                track = ThemeHook.YORU_SURFACE;
            }
            paint.setColor(track);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, paint);

            // ручка
            final float knobSize = h - AndroidUtilities.dp(4);
            final float left = checked ? w - knobSize - AndroidUtilities.dp(2) : AndroidUtilities.dp(2);
            paint.setColor(isEnabled() ? ThemeHook.YORU_TEXT : ThemeHook.YORU_MUTED);
            rect.set(left, AndroidUtilities.dp(2), left + knobSize, AndroidUtilities.dp(2) + knobSize);
            canvas.drawRoundRect(rect, knobSize / 2f, knobSize / 2f, paint);
        }
    }

    // ------------------------------------------------------------------ прочее

    /** Акцентный цвет (для иконок и заголовков в центре мода). */
    public static int accent() {
        return ThemeHook.accent();
    }

    public static int primaryText() {
        return ThemeHook.primaryText();
    }

    public static int secondaryText() {
        return ThemeHook.secondaryText();
    }

    public static int surface() {
        return ThemeHook.surface();
    }

    public static int separator() {
        return ThemeHook.separator();
    }

    /**
     * Цвет из темы Telegram по ключу (с запасным значением).
     * В этой версии Telegram ключи — целые числа, getColor(int) возвращает 0,
     * если ключа нет, поэтому просто проверяем результат.
     */
    public static int colorOf(int key, int fallback) {
        try {
            final int color = Theme.getColor(key);
            return color != 0 ? color : fallback;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    /** Лёгкая подложка нажатия: тема подсказывает, свой цвет не выдумываем. */
    public static int pressed() {
        try {
            return Theme.getColor(Theme.key_listSelector) != 0
                ? Theme.getColor(Theme.key_listSelector) : 0x1AFFFFFF;
        } catch (Throwable ignore) {
            return 0x1AFFFFFF;
        }
    }

    /** Сколько времени прошло с последнего действия пользователя (для режима «только текст»). */
    private static long lastManualAction;

    /** Пользователь сам открыл медиа — разрешаем загрузку на время. */
    public static void markManual() {
        lastManualAction = SystemClock.elapsedRealtime();
    }

    /** Медиа запрошено пользователем прямо сейчас? */
    public static boolean isManual() {
        return SystemClock.elapsedRealtime() - lastManualAction < 30_000L;
    }
}
