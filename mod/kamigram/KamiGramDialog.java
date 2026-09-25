package org.telegram.messenger.kamigram;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

/**
 * Sakura: свои аккуратные диалоги вместо стандартных.
 *
 * Почему свои: у стандартного диалога Android кнопка «удалить/сбросить» красится
 * в системный красный, углы не совпадают с остальным интерфейсом, а заголовок
 * выровнен иначе. Здесь диалог собран кодом:
 *
 *   * скруглённая карточка (как в iOS) на цвете поверхности приложения;
 *   * заголовок, пояснение и кнопки — в цветах темы (белый текст, серые детали);
 *   * главная кнопка — акцентным цветом пользователя, второстепенная — серым,
 *     НИКАКОГО красного;
 *   * у каждой кнопки-действия — акцентная материальная иконка, нарисованная
 *     вектором в коде (галочка, стрелка, корзина, замок, молния);
 *   * тень и затемнение фона — как у системных диалогов Telegram.
 *
 * Использование:
 * <pre>
 *     KamiGramDialog.create(context)
 *         .title("Очистить кэш?")
 *         .message("Скачанное вручную не удаляется.")
 *         .icon(KamiGramDialog.ICON_BROOM)
 *         .positive("Очистить", () -> ...)
 *         .negative("Отмена", null)
 *         .show();
 * </pre>
 */
public final class KamiGramDialog {

    // ------------------------------------------------------------------ иконки

    public static final int ICON_NONE = 0;
    public static final int ICON_CHECK = 1;
    public static final int ICON_BROOM = 2;
    public static final int ICON_LOCK = 3;
    public static final int ICON_BOLT = 4;
    public static final int ICON_TRASH = 5;
    public static final int ICON_INFO = 6;

    private KamiGramDialog() {
    }

    public static Builder create(Context context) {
        return new Builder(context);
    }

    /** Простое подтверждение действия: главная кнопка — акцентная, вторая — серая. */
    public static void confirm(Context context, String title, String message,
                               String yes, String no, final Runnable onYes) {
        create(context)
            .title(title)
            .message(message)
            .icon(ICON_CHECK)
            .positive(yes, onYes)
            .negative(no, null)
            .show();
    }

    // ================================================================== Builder

    public static final class Builder {

        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View content;
        private int icon = ICON_NONE;
        private CharSequence positiveText;
        private Runnable positiveAction;
        private CharSequence negativeText;
        private Runnable negativeAction;
        private boolean stacked;
        private Dialog shown;

        Builder(Context context) {
            this.context = context;
        }

        public Builder title(CharSequence value) {
            title = value;
            return this;
        }

        public Builder message(CharSequence value) {
            message = value;
            return this;
        }

        /** Произвольное содержимое (список, переключатели) вместо текста. */
        public Builder content(View value) {
            content = value;
            return this;
        }

        public Builder icon(int value) {
            icon = value;
            return this;
        }

        public Builder positive(CharSequence text, Runnable action) {
            positiveText = text;
            positiveAction = action;
            return this;
        }

        public Builder negative(CharSequence text, Runnable action) {
            negativeText = text;
            negativeAction = action;
            return this;
        }

        /** Кнопки друг под другом, а не в ряд. */
        public Builder stacked(boolean value) {
            stacked = value;
            return this;
        }

        public Dialog show() {
            try {
                final Dialog dialog = new Dialog(context);
                shown = dialog;
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                final LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(cardBackground());
                card.setPadding(dp(18), dp(18), dp(18), dp(positiveText == null && negativeText == null ? 18 : 10));

                if (icon != ICON_NONE) {
                    final IconView iconView = new IconView(context, icon);
                    final LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
                    iconParams.gravity = Gravity.CENTER_HORIZONTAL;
                    iconParams.bottomMargin = dp(12);
                    card.addView(iconView, iconParams);
                }

                if (title != null && title.length() > 0) {
                    final TextView heading = new TextView(context);
                    heading.setText(title);
                    heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
                    heading.setTypeface(AndroidUtilities.bold());
                    heading.setTextColor(KamiGramUi.primaryText());
                    heading.setGravity(Gravity.CENTER_HORIZONTAL);
                    card.addView(heading, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                }

                if (message != null && message.length() > 0) {
                    final TextView body = new TextView(context);
                    body.setText(message);
                    body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
                    body.setTextColor(KamiGramUi.secondaryText());
                    body.setGravity(Gravity.CENTER_HORIZONTAL);
                    body.setLineSpacing(dp(3), 1.0f);
                    final LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    bodyParams.topMargin = dp(title != null && title.length() > 0 ? 8 : 0);
                    card.addView(body, bodyParams);
                }

                if (content != null) {
                    final LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    contentParams.topMargin = dp(14);
                    card.addView(content, contentParams);
                }

                final LinearLayout buttons = new LinearLayout(context);
                buttons.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
                final LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                buttonsParams.topMargin = dp(positiveText == null && negativeText == null ? 0 : 16);

                if (negativeText != null) {
                    buttons.addView(row(negativeText, KamiGramUi.secondaryText(), ICON_NONE, () -> {
                        dismiss();
                        if (negativeAction != null) {
                            negativeAction.run();
                        }
                    }), weightParams());
                }
                if (positiveText != null) {
                    final int positiveIcon = iconOf(positiveText) != ICON_NONE ? iconOf(positiveText) : ICON_CHECK;
                    buttons.addView(row(positiveText, KamiGramUi.accent(), positiveIcon, () -> {
                        dismiss();
                        if (positiveAction != null) {
                            positiveAction.run();
                        }
                    }), weightParams());
                }
                if (buttons.getChildCount() > 0) {
                    card.addView(buttons, buttonsParams);
                }

                final ScrollView scroll = new ScrollView(context);
                scroll.setClipToPadding(false);
                scroll.addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                dialog.setContentView(scroll);
                final Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(0x00000000));
                    window.setLayout((int) (Math.min(AndroidUtilities.displaySize.x, dp(360))),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setDimAmount(0.6f);
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
                return dialog;
            } catch (Throwable throwable) {
                KamiGramLog.e(throwable);
                return null;
            }
        }

        private LinearLayout.LayoutParams weightParams() {
            final LinearLayout.LayoutParams params = stacked
                ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                : new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (stacked) {
                params.topMargin = dp(6);
            }
            return params;
        }

        private void dismiss() {
            try {
                if (shown != null) {
                    shown.dismiss();
                }
            } catch (Throwable ignore) {
            }
        }

        /** Кнопка-строка: акцентная иконка + подпись. */
        private View row(CharSequence text, int color, int iconRes, final Runnable action) {
            final boolean primary = color == KamiGramUi.accent();
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setBackground(primary
                ? gradient(ThemeHook.accent(), ThemeHook.accent(), 16)
                : stroke(ThemeHook.surfaceNested(), 16));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.9f).setDuration(60).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(80).start();
                        break;
                    default:
                        break;
                }
                return false;
            });

            final int useIcon = iconRes;
            if (useIcon != ICON_NONE) {
                final IconView iconView = new IconView(context, useIcon);
                iconView.setAccent(color);
                final LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
                iconParams.rightMargin = dp(8);
                row.addView(iconView, iconParams);
            }

            final TextView label = new TextView(context);
            label.setText(text == null ? "" : text.toString().toUpperCase());
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
            label.setTypeface(AndroidUtilities.bold());
            label.setTextColor(primary ? 0xFF21152F : ThemeHook.primaryText());
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            row.setOnClickListener(view -> {
                if (action != null) {
                    action.run();
                }
            });
            return row;
        }

        private int iconOf(CharSequence text) {
            if (text == null) {
                return ICON_NONE;
            }
            final String value = text.toString().toLowerCase();
            if (value.contains("очист") || value.contains("сброс") || value.contains("удал")) {
                return ICON_TRASH;
            }
            if (value.contains("включ") || value.contains("подключ") || value.contains("провер")) {
                return ICON_BOLT;
            }
            return ICON_NONE;
        }

        /**
         * Карточка диалога следует активной теме Telegram: радиус 24 и
         * тонкая тематическая обводка.
         */
        private GradientDrawable cardBackground() {
            final GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(ThemeHook.surface());
            drawable.setCornerRadius(dp(24));
            drawable.setStroke(Math.max(1, dp(1)), ThemeHook.separator());
            return drawable;
        }

        private GradientDrawable gradient(int start, int end, int radius) {
            final GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
            drawable.setCornerRadius(dp(radius));
            return drawable;
        }

        private GradientDrawable stroke(int color, int radius) {
            final GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dp(radius));
            drawable.setStroke(Math.max(1, dp(1)), ThemeHook.separator());
            return drawable;
        }

        private GradientDrawable rounded(int color, int radius) {
            final GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dp(radius));
            return drawable;
        }

        private int dp(float value) {
            return (int) (value * AndroidUtilities.density);
        }
    }

    // ================================================================== IconView

    /** Акцентная материальная иконка, нарисованная вектором в коде. */
    public static class IconView extends View {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final int icon;
        private int color;

        public IconView(Context context, int icon) {
            super(context);
            this.icon = icon;
            this.color = KamiGramUi.accent();
            fill.setColor(color);
            stroke.setColor(color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.density * 2f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            setWillNotDraw(false);
        }

        public void setAccent(int value) {
            color = value;
            fill.setColor(value);
            stroke.setColor(value);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            final float size = Math.min(w, h);
            final float left = (w - size) / 2f;
            final float top = (h - size) / 2f;
            final float r = size / 2f;

            // круглая акцентная подложка (10% цвета акцента)
            fill.setAlpha(26);
            canvas.drawCircle(w / 2f, h / 2f, r, fill);
            fill.setAlpha(255);

            switch (icon) {
                case ICON_CHECK: {
                    path.reset();
                    path.moveTo(left + size * 0.26f, top + size * 0.52f);
                    path.lineTo(left + size * 0.44f, top + size * 0.70f);
                    path.lineTo(left + size * 0.76f, top + size * 0.32f);
                    canvas.drawPath(path, stroke);
                    break;
                }
                case ICON_BROOM:
                case ICON_TRASH: {
                    rect.set(left + size * 0.28f, top + size * 0.34f, left + size * 0.72f, top + size * 0.76f);
                    canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, stroke);
                    canvas.drawLine(left + size * 0.22f, top + size * 0.30f,
                        left + size * 0.78f, top + size * 0.30f, stroke);
                    canvas.drawLine(left + size * 0.44f, top + size * 0.22f,
                        left + size * 0.56f, top + size * 0.22f, stroke);
                    break;
                }
                case ICON_LOCK: {
                    rect.set(left + size * 0.28f, top + size * 0.46f, left + size * 0.72f, top + size * 0.78f);
                    canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, stroke);
                    path.reset();
                    path.moveTo(left + size * 0.38f, top + size * 0.46f);
                    path.lineTo(left + size * 0.38f, top + size * 0.34f);
                    path.quadTo(left + size * 0.50f, top + size * 0.18f, left + size * 0.62f, top + size * 0.34f);
                    path.lineTo(left + size * 0.62f, top + size * 0.46f);
                    canvas.drawPath(path, stroke);
                    break;
                }
                case ICON_BOLT: {
                    path.reset();
                    path.moveTo(left + size * 0.56f, top + size * 0.20f);
                    path.lineTo(left + size * 0.34f, top + size * 0.54f);
                    path.lineTo(left + size * 0.50f, top + size * 0.54f);
                    path.lineTo(left + size * 0.44f, top + size * 0.80f);
                    path.lineTo(left + size * 0.68f, top + size * 0.44f);
                    path.lineTo(left + size * 0.52f, top + size * 0.44f);
                    path.close();
                    canvas.drawPath(path, fill);
                    break;
                }
                case ICON_INFO:
                default: {
                    canvas.drawCircle(w / 2f, top + size * 0.30f, size * 0.06f, fill);
                    canvas.drawLine(w / 2f, top + size * 0.44f, w / 2f, top + size * 0.74f, stroke);
                    break;
                }
            }
        }
    }
}
