package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;

import java.lang.ref.WeakReference;

/**
 * KamiGram: «поверх всех приложений» (r70, масштабный пункт 1).
 *
 * Две связки, обе 100% штатные API Android — Telegram не ломается:
 *
 * <ol>
 *   <li><b>PiP (картинка-в-картинке)</b> — тап по иконке в шапке:
 *       приложение сворачивается в маленькое окно СВЕРХУ всех приложений
 *       (игры, другие программы). Окно интерактивное — всё кликается,
 *       можно пользоваться ТГ как обычно. Размер меняется тянущейся
 *       ручкой системы (больше/меньше), тап по окну — на весь экран.
 *       Разрешения не нужны (манифест уже поддерживает PiP).</li>
 *   <li><b>Летающий круглешок</b> — маленькая круглая «пузырьковая»
 *       иконка поверх любого приложения: перетаскивается пальцем,
 *       тап — открывает Telegram, долгое нажатие — прячет.
 *       Нужное разрешение «Поверх других приложений» запрашиваем
 *       сами (системный экран), как и просил заказчик.</li>
 * </ol>
 *
 * Порядок действий иконки в шапке:
 *   * тап — свернуть в PiP (или, если уже в PiP — выйти из него);
 *   * после выхода из PiP (крестик) автоматически показывается круглешок;
 *   * долгое нажатие на иконку — сразу круглешок (если PiP не открыт).
 */
public final class KamiGramFloat {

    /** Идентификатор пункта-иконки «поверх приложений». */
    public static final int HEADER_ITEM_ID = 0x4B4702;

    private static WeakReference<ActionBarMenuItem> headerItem;
    private static WeakReference<Activity> lastActivityRef;
    private static WindowManager windowManager;
    private static View bubbleView;
    private static boolean pipActive;
    private static boolean pendingOverlayPermission;

    private KamiGramFloat() {
    }

    // ------------------------------------------------------------------ иконка

    /** Иконка «поверх приложений» рядом с иконкой призрака в шапке. */
    public static ActionBarMenuItem addHeaderItem(ActionBarMenu menu) {
        try {
            final ActionBarMenuItem item = menu.addItem(HEADER_ITEM_ID,
                R.drawable.kamigram_float);
            item.setContentDescription("Поверх приложений");
            headerItem = new WeakReference<>(item);
            final Activity activity = activity(item);
            lastActivityRef = new WeakReference<>(activity);
            item.setOnClickListener(v -> onIconClick(activity));
            item.setOnLongClickListener(v -> {
                onIconLongClick(activity);
                return true;
            });
            refreshHeader(item);
            return item;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static void refreshHeader(ActionBarMenuItem item) {
        try {
            item.setIconColor(0xFFFFFFFF);
        } catch (Throwable ignore) {
        }
    }

    private static Activity activity(ActionBarMenuItem item) {
        try {
            final Context ctx = item.getContext();
            if (ctx instanceof Activity) {
                return (Activity) ctx;
            }
            if (ctx != null) {
                Context unwrapped = ctx;
                while (unwrapped instanceof android.content.ContextWrapper) {
                    unwrapped = ((android.content.ContextWrapper) unwrapped).getBaseContext();
                }
                if (unwrapped instanceof Activity) {
                    return (Activity) unwrapped;
                }
            }
        } catch (Throwable ignore) {
        }
        return lastActivityRef == null ? null : lastActivityRef.get();
    }

    // ------------------------------------------------------------------ PiP

    /** Тап по иконке: свернуть в PiP / выйти из PiP. */
    public static void onIconClick(Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (!KamiGramConfig.floatWindow()) {
                KamiGramUi.notify(activity, "Включите «Плавающее окно» в центре KamiGram");
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                KamiGramUi.notify(activity, "Нужен Android 8.0+");
                return;
            }
            if (pipActive) {
                // уже в PiP — выходим на весь экран
                activity.moveTaskToBack(false);
                return;
            }
            enterPip(activity);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Долгий тап: сразу показать летающий круглешок. */
    public static void onIconLongClick(Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (!KamiGramConfig.floatWindow()) {
                KamiGramUi.notify(activity, "Включите «Плавающее окно» в центре KamiGram");
                return;
            }
            if (!canDrawOverlays(activity)) {
                pendingOverlayPermission = true;
                KamiGramChannelGuard.requestOverlayPermission(activity);
                return;
            }
            pendingOverlayPermission = false;
            if (!pipActive) {
                showBubble();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Скрыть/показать: вызывается из onPictureInPictureModeChanged. */
    public static void onPipModeChanged(boolean inPip) {
        pipActive = inPip;
        if (!inPip) {
            // вышли из PiP — показываем круглешок (если разрешено)
            final Activity activity = lastActivity();
            if (activity != null && KamiGramConfig.floatWindow() && canDrawOverlays(activity)) {
                showBubble();
            }
        }
    }

    /** Приложение ушло на фон: если пользователь включил «плавающее окно» —
        показываем круглешок (чтобы ТГ был под рукой поверх любого приложения). */
    public static void onAppStop() {
        try {
            if (pipActive) {
                return; // PiP-окно и так видно
            }
            final Activity activity = lastActivity();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (KamiGramConfig.floatWindow() && canDrawOverlays(activity)) {
                showBubble();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Приложение вернулось на передний план — круглешок прячем. */
    public static void onAppResume() {
        hideBubble();
        final Activity activity = lastActivity();
        if (activity != null && pendingOverlayPermission) {
            pendingOverlayPermission = false;
            if (canDrawOverlays(activity)) {
                KamiGramUi.notify(activity, "Разрешение получено — долгий тап по иконке покажет круглешок");
            }
        }
    }

    private static void enterPip(Activity activity) {
        try {
            final android.app.PictureInPictureParams.Builder builder =
                new android.app.PictureInPictureParams.Builder();
            final android.graphics.Rect rect = new android.graphics.Rect(
                (int) (AndroidUtilities.displaySize.x * 0.15f),
                (int) (AndroidUtilities.displaySize.y * 0.25f),
                (int) (AndroidUtilities.displaySize.x * 0.85f),
                (int) (AndroidUtilities.displaySize.y * 0.75f));
            builder.setSourceRectHint(rect);
            activity.enterPictureInPictureMode(builder.build());
        } catch (Throwable t) {
            FileLog.e(t);
            KamiGramUi.notify(activity, "Не удалось открыть плавающее окно");
        }
    }

    private static boolean canDrawOverlays(Activity activity) {
        try {
            return Settings.canDrawOverlays(activity);
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static Activity lastActivity() {
        try {
            return lastActivityRef == null ? null : lastActivityRef.get();
        } catch (Throwable ignore) {
            return null;
        }
    }

    // ------------------------------------------------------------------ пузырёк

    /** Показать летающий круглешок поверх всех приложений. */
    public static void showBubble() {
        try {
            if (bubbleView != null) {
                return;
            }
            final Activity activity = lastActivity();
            if (activity == null || !canDrawOverlays(activity)) {
                return;
            }
            if (windowManager == null) {
                windowManager = (WindowManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.WINDOW_SERVICE);
            }
            if (windowManager == null) {
                return;
            }

            final int size = AndroidUtilities.dp(58);
            final ImageView bubble = new ImageView(activity);
            bubble.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            final GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xE61C1724);
            background.setStroke(AndroidUtilities.dp(2), 0xFFC8A7FF);
            bubble.setBackground(background);
            bubble.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10),
                AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            try {
                bubble.setImageDrawable(activity.getApplicationInfo().loadIcon(
                    activity.getPackageManager()));
            } catch (Throwable ignore) {
            }

            float downX, downY, viewX, viewY;
            boolean moved;
            bubble.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        viewX = v.getX();
                        viewY = v.getY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        final float dx = event.getRawX() - downX;
                        final float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                        }
                        v.setX(viewX + dx);
                        v.setY(viewY + dy);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            openApp(activity);
                        }
                        return true;
                    // долгое нажатие обрабатывает setOnLongClickListener ниже
                    default:
                        return false;
                }
            });

            bubble.setOnLongClickListener(v -> {
                hideBubble();
                return true;
            });

            final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(size, size,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = AndroidUtilities.displaySize.x / 2 - size / 2;
            params.y = (int) (AndroidUtilities.displaySize.y * 0.2f);

            windowManager.addView(bubble, params);
            bubbleView = bubble;
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Скрыть круглешок. */
    public static void hideBubble() {
        try {
            if (bubbleView != null && windowManager != null) {
                windowManager.removeView(bubbleView);
            }
        } catch (Throwable ignore) {
        }
        bubbleView = null;
    }

    /** Открыть приложение (круглешок → Telegram на передний план). */
    private static void openApp(Activity activity) {
        try {
            hideBubble();
            final android.content.Intent intent = new android.content.Intent(activity,
                org.telegram.ui.LaunchActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
