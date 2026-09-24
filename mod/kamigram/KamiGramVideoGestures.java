package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.media.AudioManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Invisible video-player gesture zones. A vertical swipe on the left half
 * changes the current Activity brightness; the same gesture on the right half
 * changes music volume. No overlay, progress stripe, toast, or persistent view
 * is drawn, and volume changes use flags zero so Android does not show a bar.
 */
public final class KamiGramVideoGestures {
    private static final float START_DP = 12f;
    private static final float DEAD_ZONE_DP = 18f;

    private static View trackedView;
    private static float startX;
    private static float startY;
    private static float lastY;
    private static boolean vertical;
    private static boolean changed;
    private static boolean brightness;
    private static float brightnessValue;
    private static int volumeValue;
    private static int maxVolume;

    private KamiGramVideoGestures() {
    }

    /** Return true only while this gesture is being consumed. */
    public static boolean handle(View view, MotionEvent event, boolean enabled) {
        if (!enabled || view == null || event == null) {
            if (event != null && (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                reset();
            }
            return false;
        }
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            trackedView = view;
            startX = event.getX();
            startY = event.getY();
            lastY = startY;
            vertical = false;
            changed = false;
            brightness = startX < Math.max(1, view.getWidth()) * 0.5f;
            prepare(view, brightness);
            return false;
        }
        if (trackedView != view) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            final float totalX = event.getX() - startX;
            final float totalY = event.getY() - startY;
            if (!vertical) {
                final float threshold = dp(view.getContext(), START_DP);
                if (Math.abs(totalY) < threshold || Math.abs(totalY) < Math.abs(totalX)) {
                    return false;
                }
                vertical = true;
            }
            final float delta = event.getY() - lastY;
            lastY = event.getY();
            if (Math.abs(delta) > 0.01f) {
                apply(view, delta);
                changed = true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            final boolean consume = vertical && changed;
            reset();
            return consume;
        }
        return vertical;
    }

    private static void prepare(View view, boolean isBrightness) {
        if (isBrightness) {
            final Window window = findWindow(view.getContext());
            if (window == null) {
                brightnessValue = 0.5f;
                return;
            }
            final float current = window.getAttributes().screenBrightness;
            brightnessValue = current >= 0f ? current : 0.5f;
        } else {
            final AudioManager manager = (AudioManager) view.getContext()
                .getSystemService(Context.AUDIO_SERVICE);
            if (manager != null) {
                maxVolume = Math.max(1, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                volumeValue = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
            }
        }
    }

    private static void apply(View view, float deltaY) {
        if (brightness) {
            final Window window = findWindow(view.getContext());
            if (window == null) {
                return;
            }
            final float height = Math.max(1f, view.getHeight());
            brightnessValue = clamp(brightnessValue - deltaY / height, 0.01f, 1f);
            final WindowManager.LayoutParams params = window.getAttributes();
            params.screenBrightness = brightnessValue;
            window.setAttributes(params);
        } else {
            final AudioManager manager = (AudioManager) view.getContext()
                .getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) {
                return;
            }
            if (maxVolume <= 0) {
                maxVolume = Math.max(1, manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            }
            volumeValue = Math.max(0, Math.min(maxVolume,
                volumeValue + Math.round(-deltaY / Math.max(1f, view.getHeight()) * maxVolume)));
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeValue, 0);
        }
    }

    private static Window findWindow(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return ((Activity) current).getWindow();
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? ((Activity) current).getWindow() : null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float dp(Context context, float value) {
        final float density = context == null ? 1f : context.getResources().getDisplayMetrics().density;
        return value * density;
    }

    private static void reset() {
        trackedView = null;
        vertical = false;
        changed = false;
    }
}
