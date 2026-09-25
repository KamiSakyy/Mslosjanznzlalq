package org.telegram.ui.Components.kamigram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.kamigram.KamiGramConfig;

/**
 * Sakura: иконки кодом - iOS-шестерёнка настроек и щит прокси.
 *
 * Рисуются прямо в коде (без картинок), поэтому выглядят одинаково на любом
 * экране и меняют цвет вместе с темой. Шестерёнка - как в iOS: восемь зубьев,
 * круглое отверстие, ровная толщина линии, ничего «стеклянного».
 */
public final class KamiGramIcons {

    private KamiGramIcons() {
    }

    /** Иконка настроек: iOS-шестерёнка (SF Symbols). href = размер в dp. */
    public static Drawable settings() {
        return new GearDrawable();
    }

    /** Иконка настроек нужного размера. */
    public static Drawable settings(float sizeDp) {
        final GearDrawable drawable = new GearDrawable();
        drawable.setSize(sizeDp);
        return drawable;
    }

    /** Щит-прокси. */
    public static Drawable proxy(int color) {
        final ShieldDrawable drawable = new ShieldDrawable();
        drawable.setColor(color);
        return drawable;
    }

    // ------------------------------------------------------------------ шестерёнка

    public static class GearDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int color = 0xFF8E8E93;
        private float sizeDp = 24f;

        public GearDrawable() {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        public GearDrawable setColor(int value) {
            color = value;
            return this;
        }

        public GearDrawable setSize(float dp) {
            sizeDp = dp;
            return this;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect bounds = getBounds();
            final float full = Math.min(bounds.width(), bounds.height());
            final float scale = full / AndroidUtilities.dp(sizeDp);
            final float cx = bounds.centerX();
            final float cy = bounds.centerY();
            final float unit = AndroidUtilities.dp(1f) * scale;

            final float rOut = 9.5f * unit;
            final float rIn = 7.1f * unit;
            final float rHole = 3.2f * unit;
            final int teeth = 8;

            path.reset();
            for (int t = 0; t < teeth; t++) {
                final double a0 = t * (Math.PI * 2.0 / teeth);
                final double[][] stepPoints = {
                    {rIn, a0},
                    {rOut, a0 + 0.16 * Math.PI * 2.0 / teeth},
                    {rOut, a0 + 0.34 * Math.PI * 2.0 / teeth},
                    {rIn, a0 + 0.50 * Math.PI * 2.0 / teeth},
                };
                for (int p = 0; p < stepPoints.length; p++) {
                    final float x = cx + (float) (stepPoints[p][0] * Math.cos(stepPoints[p][1]));
                    final float y = cy + (float) (stepPoints[p][0] * Math.sin(stepPoints[p][1]));
                    if (t == 0 && p == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
            }
            path.close();
            path.addCircle(cx, cy, rHole, Path.Direction.CCW);

            paint.setColor(color);
            paint.setStrokeWidth(1.6f * unit);
            canvas.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(sizeDp);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(sizeDp);
        }
    }

    // ------------------------------------------------------------------ щит прокси

    public static class ShieldDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shield = new Path();
        private final Path lightning = new Path();
        private int color = 0xFF8E8E93;

        public ShieldDrawable() {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            bolt.setStyle(Paint.Style.FILL);
        }

        public ShieldDrawable setColor(int value) {
            color = value;
            return this;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect bounds = getBounds();
            final float cx = bounds.centerX();
            final float cy = bounds.centerY();
            final float w = Math.min(bounds.width(), bounds.height()) * 0.62f;
            final float h = w * 1.15f;
            final RectF rect = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);

            shield.reset();
            shield.moveTo(rect.left, rect.top + h * 0.12f);
            shield.lineTo(cx, rect.top);
            shield.lineTo(rect.right, rect.top + h * 0.12f);
            shield.lineTo(rect.right, rect.top + h * 0.52f);
            shield.quadTo(rect.right, rect.bottom, cx, rect.bottom);
            shield.quadTo(rect.left, rect.bottom, rect.left, rect.top + h * 0.52f);
            shield.close();

            paint.setColor(color);
            paint.setStrokeWidth(Math.max(1.2f, w * 0.075f));
            canvas.drawPath(shield, paint);

            lightning.reset();
            lightning.moveTo(cx + w * 0.12f, cy - h * 0.22f);
            lightning.lineTo(cx - w * 0.18f, cy + h * 0.02f);
            lightning.lineTo(cx + w * 0.01f, cy + h * 0.02f);
            lightning.lineTo(cx - w * 0.09f, cy + h * 0.24f);
            lightning.lineTo(cx + w * 0.2f, cy - h * 0.03f);
            lightning.lineTo(cx + w * 0.01f, cy - h * 0.03f);
            lightning.close();
            bolt.setColor(color);
            canvas.drawPath(lightning, bolt);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            bolt.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(24);
        }
    }

    /** Цвет иконок: iOS-серый, при включённом iOS-дизайне — чуть светлее. */
    public static int iconColor() {
        return KamiGramConfig.iosDesign() ? Color.parseColor("#EBEBF5") : Color.parseColor("#8E8E93");
    }
}
