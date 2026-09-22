package org.telegram.messenger.kamigram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * KamiGram: кнопка прокси и панель прокси.
 *
 * Кнопка живёт прямо в шапке (рядом с «тремя точками») на главном экране и в чате:
 * цвет сразу показывает состояние - зелёный (работает), жёлтый (проверяется),
 * красный (не отвечает), серый (прокси выключен). По нажатию открывается панель,
 * где можно вставить ссылку на прокси и подключиться моментально.
 */
public final class KamiGramProxyButton {

    public static final int ID_PROXY = 0x4B41;          // «KA»
    public static final int ID_PROXY_LINK = 0x4B42;
    public static final int ID_PROXY_BEST = 0x4B43;

    /** Все созданные иконки кнопок: их цвет обновляется при смене состояния прокси. */
    private static final ArrayList<IconDrawable> ICONS = new ArrayList<>();

    private KamiGramProxyButton() {
    }

    // ------------------------------------------------------------------ кнопка в шапке

    /**
     * Добавляет кнопку прокси в шапку (слева от «трёх точек»).
     *
     * Иконка — РОДНАЯ иконка Telegram (org.telegram.ui.Components.ProxyDrawable),
     * та же самая, что Telegram показывает в своём меню прокси. Никаких
     * самодельных щитов: выглядит один в один как в приложении.
     */
    public static ActionBarMenuItem add(ActionBar actionBar, final Context context, final Runnable openPanel) {
        try {
            if (actionBar == null) {
                return null;
            }
            final ActionBarMenu menu = actionBar.createMenu();
            final org.telegram.ui.Components.ProxyDrawable icon =
                new org.telegram.ui.Components.ProxyDrawable(context);
            final ActionBarMenuItem item = menu.addItem(ID_PROXY, icon);
            item.setContentDescription("Прокси");
            item.setVisibility(View.VISIBLE);
            item.setOnClickListener(v -> {
                if (openPanel != null) {
                    openPanel.run();
                } else {
                    showPanel(context);
                }
            });
            return item;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Перекрашивает иконку по состоянию прокси. */
    public static void paint(ActionBarMenuItem item) {
        refreshAll();
    }

    /** Обновляет все кнопки прокси: цвет = состояние соединения. */
    public static void refreshAll() {
        try {
            final int color = KamiGramProxyPower.stateColor();
            for (int a = 0; a < ICONS.size(); a++) {
                final IconDrawable icon = ICONS.get(a);
                icon.setStateColor(color);
                icon.invalidateSelf();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Строки прокси для меню «три точки». */
    public static void addToMenu(final ActionBarMenuItem menuItem, final Context context, final Runnable openList) {
        try {
            if (menuItem == null || context == null) {
                return;
            }
            /* Подсказок и своих строк про прокси в меню БОЛЬШЕ НЕТ: прокси живёт
               там же, где в обычном Telegram — в «Настройках → Данные и память → Прокси»,
               а из центра мода открывается родной экран прокси. */
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Обработка нажатия на строки прокси из меню. true, если нажатие наше. */
    public static boolean handleClick(int id, Context context) {
        if (id == ID_PROXY) {
            showPanel(context);
            return true;
        }
        if (id == ID_PROXY_LINK) {
            showLinkDialog(context, null);
            return true;
        }
        if (id == ID_PROXY_BEST) {
            KamiGramProxyPower.refreshNow(context);
            KamiGramUi.notify(context, KamiGramProxyPower.statusText());
            return true;
        }
        return false;
    }

    /** Родной экран прокси Telegram — свой дизайн прокси не нужен. */
    static void openNativeProxyList(final Context context) {
        try {
            final android.app.Activity activity = AndroidUtilities.findActivity(context);
            if (activity instanceof org.telegram.ui.LaunchActivity) {
                ((org.telegram.ui.LaunchActivity) activity).presentFragment(new org.telegram.ui.ProxyListActivity());
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ панель прокси

    /** Панель прокси: состояние, список с пингом, поле для ссылки. */
    public static void showPanel(final Context context) {
        /* Свой дизайн прокси не нужен: открываем родной экран прокси Telegram. */
        openNativeProxyList(context);
    }

    private static void dismissLater(Context context) {
        KamiGramUi.notify(context, KamiGramProxyPower.statusText());
    }

    /** Диалог «вставь ссылку на прокси» — подключает моментально. */
    public static void showLinkDialog(final Context context, final Runnable after) {
        if (context == null) {
            return;
        }
        try {
            final EditText input = new EditText(context);
            input.setHint("https://t.me/proxy?server=…&port=…&secret=…");
            input.setTextSize(15);
            input.setSingleLine(false);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            try {
                final ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (manager != null && manager.hasPrimaryClip()) {
                    final ClipData clip = manager.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        final String link = KamiGramProxyHelper.extractLink(clip.getItemAt(0).coerceToText(context));
                        if (link != null) {
                            input.setText(link);
                        }
                    }
                }
            } catch (Throwable ignore) {
            }

            final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Ссылка на прокси")
                .setMessage("Вставь ссылку — подключу моментально, без похода в настройки.")
                .setView(input)
                .setPositiveButton("Подключить", null)
                .setNegativeButton("Отмена", null)
                .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String text = input.getText() != null ? input.getText().toString() : "";
                final String link = KamiGramProxyHelper.extractLink(text);
                if (link == null) {
                    KamiGramUi.notify(context, "Ссылка не распознана");
                    return;
                }
                if (KamiGramProxyPower.addAndActivate(link, context)) {
                    dialog.dismiss();
                    if (after != null) {
                        after.run();
                    }
                }
            }));
            dialog.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ------------------------------------------------------------------ иконка

    /** Иконка прокси: щит со «молнией» — цвет зависит от состояния. */
    public static class IconDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shield = new Path();
        private final Path bolt = new Path();
        private int stateColor = 0xFF8E8E93;
        private float scale = 1f;
        private float offsetX, offsetY;

        public IconDrawable() {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.dp(1.6f));
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.FILL);
        }

        public void setStateColor(int color) {
            stateColor = color;
        }

        public void setScale(float value) {
            scale = value;
        }

        public void setTranslate(float x, float y) {
            offsetX = x;
            offsetY = y;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect bounds = getBounds();
            final float cx = bounds.centerX() + offsetX;
            final float cy = bounds.centerY() + offsetY;
            final float w = AndroidUtilities.dp(18) * scale;
            final float h = AndroidUtilities.dp(20) * scale;

            shield.reset();
            final RectF rect = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            shield.addRoundRect(new RectF(rect.left, rect.top, rect.right, rect.top + h * 0.55f), w * 0.28f, w * 0.28f, Path.Direction.CW);
            shield.moveTo(rect.left, rect.top + h * 0.45f);
            shield.lineTo(rect.right, rect.top + h * 0.45f);
            shield.lineTo(cx, rect.bottom);
            shield.close();

            stroke.setColor(stateColor);
            canvas.drawPath(shield, stroke);
            paint.setColor(stateColor);
            canvas.drawPath(shield, paint);

            bolt.reset();
            final float bw = w * 0.26f;
            final float bh = h * 0.46f;
            bolt.moveTo(cx + bw * 0.45f, cy - bh / 2f);
            bolt.lineTo(cx - bw * 0.75f, cy + bh * 0.08f);
            bolt.lineTo(cx + bw * 0.05f, cy + bh * 0.08f);
            bolt.lineTo(cx - bw * 0.35f, cy + bh / 2f);
            bolt.lineTo(cx + bw * 0.8f, cy - bh * 0.06f);
            bolt.lineTo(cx - bw * 0.02f, cy - bh * 0.06f);
            bolt.close();
            paint.setColor(0xFF000000);
            paint.setAlpha(210);
            canvas.drawPath(bolt, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            stroke.setAlpha(alpha);
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
}
