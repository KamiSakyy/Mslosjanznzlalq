package org.telegram.ui.Components.kamigram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * KamiGram: iOS-style tab bar background - flat (no blur, no glass),
 * rounded, with a thin iOS-like hairline border.
 */
public class KamiGramIOSTabBarDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float radius = -1;

    public KamiGramIOSTabBarDrawable(int surfaceColor, int borderColor) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(surfaceColor);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(0.7f);
        strokePaint.setColor(Color.argb(38, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor)));
    }

    public void updateColors(int surfaceColor, int borderColor) {
        fillPaint.setColor(surfaceColor);
        strokePaint.setColor(Color.argb(38, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor)));
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final RectF bounds = rect;
        bounds.set(getBounds());
        if (bounds.isEmpty()) {
            return;
        }
        if (radius < 0) {
            radius = Math.min(bounds.width(), bounds.height()) / 2f;
        }
        final float inset = strokePaint.getStrokeWidth() / 2f;
        bounds.inset(inset, inset);
        canvas.drawRoundRect(bounds, radius, radius, fillPaint);
        canvas.drawRoundRect(bounds, radius, radius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
