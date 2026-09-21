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

    /** Добавляет кнопку прокси в шапку (слева от «трёх точек»). */
    public static ActionBarMenuItem add(ActionBar actionBar, final Context context, final Runnable openPanel) {
        try {
            if (actionBar == null) {
                return null;
            }
            final ActionBarMenu menu = actionBar.createMenu();
            final IconDrawable icon = new IconDrawable();
            final ActionBarMenuItem item = menu.addItem(ID_PROXY, icon);
            item.setContentDescription("Прокси KamiGram");
            item.setVisibility(View.VISIBLE);
            ICONS.add(icon);
            refreshAll();
            item.setOnClickListener(v -> {
                paint(item);
                if (openPanel != null) {
                    openPanel.run();
                } else {
                    showPanel(context);
                }
            });
            paint(item);
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
            menuItem.addSubItem(ID_PROXY, 0, "Прокси: " + KamiGramProxyPower.statusText());
            menuItem.addSubItem(ID_PROXY_LINK, 0, "Вставить ссылку на прокси");
            menuItem.addSubItem(ID_PROXY_BEST, 0, "Подобрать лучший прокси");
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
            Toast.makeText(context, KamiGramProxyPower.statusText(), Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ панель прокси

    /** Панель прокси: состояние, список с пингом, поле для ссылки. */
    public static void showPanel(final Context context) {
        if (context == null) {
            return;
        }
        try {
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(6), AndroidUtilities.dp(18), AndroidUtilities.dp(6));

            final TextView status = new TextView(context);
            status.setText(KamiGramProxyPower.statusText());
            status.setTextSize(14);
            status.setTextColor(KamiGramProxyPower.stateColor());
            root.addView(status, wrap());

            final TextView hint = new TextView(context);
            hint.setText("Мод держит базу живых прокси и переключается на лучший за секунду. Вставь ссылку — подключу сразу.");
            hint.setTextSize(12);
            hint.setTextColor(0xFF8E8E93);
            hint.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(10));
            root.addView(hint, wrap());

            final LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            final ArrayList<SharedConfig.ProxyInfo> infos = new ArrayList<>(SharedConfig.proxyList);
            for (int a = 0; a < infos.size(); a++) {
                final SharedConfig.ProxyInfo info = infos.get(a);
                final TextView row = new TextView(context);
                final boolean current = info == SharedConfig.currentProxy;
                final String ping = info.checking ? "проверяю…"
                    : info.available && info.ping > 0 ? info.ping + " мс"
                    : info.availableCheckTime != 0 ? "не отвечает" : "не проверен";
                row.setText((current ? "● " : "○ ") + info.settings.getAddress() + ":" + info.settings.getPort() + "  ·  " + ping);
                row.setTextSize(14);
                row.setTextColor(current ? 0xFF30D158 : (info.availableCheckTime != 0 && !info.available ? 0xFFFF453A : Theme.getColor(Theme.key_dialogTextBlack)));
                row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(11), AndroidUtilities.dp(10), AndroidUtilities.dp(11));
                row.setOnClickListener(v -> {
                    KamiGramProxyPower.activate(info, context, false);
                    KamiGramProxyPower.checkOne(info);
                    dismissLater(context);
                });
                list.addView(row, wrap());
            }
            if (infos.isEmpty()) {
                final TextView empty = new TextView(context);
                empty.setText("Список пуст. Вставь ссылку на прокси ниже.");
                empty.setTextSize(14);
                empty.setTextColor(0xFF8E8E93);
                list.addView(empty, wrap());
            }
            root.addView(list, wrap());

            final EditText input = new EditText(context);
            input.setHint("https://t.me/proxy?server=…");
            input.setTextSize(14);
            input.setSingleLine(false);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
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
            root.addView(input, wrap());

            final ScrollView scroll = new ScrollView(context);
            scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Прокси KamiGram")
                .setView(scroll)
                .setPositiveButton("Подключить", null)
                .setNegativeButton("Закрыть", null)
                .setNeutralButton("Проверить все", null)
                .create();
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    final String text = input.getText() != null ? input.getText().toString() : "";
                    final String link = KamiGramProxyHelper.extractLink(text);
                    if (link == null) {
                        Toast.makeText(context, "Это не похоже на ссылку прокси", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (KamiGramProxyPower.addAndActivate(link, context)) {
                        dialog.dismiss();
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    KamiGramProxyPower.pingAll();
                    Toast.makeText(context, "Проверяю все прокси…", Toast.LENGTH_SHORT).show();
                });
            });
            dialog.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void dismissLater(Context context) {
        Toast.makeText(context, KamiGramProxyPower.statusText(), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(context, "Ссылка не распознана", Toast.LENGTH_SHORT).show();
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
