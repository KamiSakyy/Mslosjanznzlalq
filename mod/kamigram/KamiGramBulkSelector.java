package org.telegram.messenger.kamigram;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Массовый выбор сообщений в родном чате: фильтр «только обычные сообщения»
 * (как у родного выделения Telegram) и аккуратный диалог количества в стиле
 * Sakura. Сетевых операций не выполняет.
 */
public final class KamiGramBulkSelector {
    public interface Callback {
        void onCount(int count);
    }

    private KamiGramBulkSelector() {
    }

    /** Собирает первые `requested` выделяемых сообщений в порядке списка. */
    public static ArrayList<MessageObject> collect(ArrayList<MessageObject> source, int requested) {
        final ArrayList<MessageObject> result = new ArrayList<>();
        final HashSet<Integer> seen = new HashSet<>();
        if (source == null || requested <= 0) {
            return result;
        }
        for (int i = 0; i < source.size() && result.size() < requested; i++) {
            final MessageObject message = source.get(i);
            if (message == null || message.messageOwner == null || !selectable(message)) {
                continue;
            }
            final int id = message.getId();
            if (id == 0 || !seen.add(id)) {
                continue;
            }
            result.add(message);
        }
        return result;
    }

    /** Те же ограничения, что у родного выделения: только обычные сообщения. */
    private static boolean selectable(MessageObject message) {
        try {
            if (message.contentType != 0) {
                return false;
            }
            if (message.isEphemeral() || message.isSponsored() || message.isAnyGift()
                || message.isWallpaperAction()
                || message.type == MessageObject.TYPE_JOINED_CHANNEL
                || message.type == MessageObject.TYPE_GIFT_STARS) {
                return false;
            }
            if (message.messageOwner.action != null) {
                final String name = message.messageOwner.action.getClass()
                    .getSimpleName().toLowerCase(java.util.Locale.US);
                if (name.contains("phonecall") || name.contains("videocall")
                    || name.contains("groupcall") || name.contains("conferencecall")) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    // ------------------------------------------------------------------ диалог Sakura

    /** Диалог количества: 10, 50 или своё число — в стиле настроек Sakura. */
    public static void showCountDialog(Context context, final Callback callback) {
        if (context == null || callback == null) {
            return;
        }
        try {
            final Dialog[] shown = new Dialog[1];
            final LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);

            addRow(list, optionRow(context, "10", "Выбрать последние десять", () -> {
                dismiss(shown[0]);
                callback.onCount(10);
            }));
            addRow(list, optionRow(context, "50", "Выбрать последние пятьдесят", () -> {
                dismiss(shown[0]);
                callback.onCount(50);
            }));
            addRow(list, optionRow(context, "···", "Указать количество самому", () -> {
                dismiss(shown[0]);
                showCustomCount(context, callback);
            }));

            shown[0] = KamiGramDialog.create(context)
                .title("Массовый выбор")
                .message("Сообщения выделятся прямо в чате — их можно переслать или удалить.")
                .icon(KamiGramDialog.ICON_CHECK)
                .content(list)
                .negative("Отмена", null)
                .show();
        } catch (Throwable ignore) {
        }
    }

    private static void showCustomCount(Context context, final Callback callback) {
        try {
            final EditText input = new EditText(context);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("Количество");
            input.setHintTextColor(KamiGramUi.secondaryText());
            input.setTextColor(KamiGramUi.primaryText());
            input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            input.setSelectAllOnFocus(true);
            input.setBackground(card(14, 1));
            input.setPadding(dp(14), dp(12), dp(14), dp(12));

            KamiGramDialog.create(context)
                .title("Сколько выбрать?")
                .message("От 1 до 10000 сообщений подряд.")
                .icon(KamiGramDialog.ICON_INFO)
                .content(input)
                .negative("Отмена", null)
                .positive("Выбрать", () -> {
                    try {
                        final int value = Math.max(1, Math.min(10000,
                            Integer.parseInt(input.getText().toString().trim())));
                        callback.onCount(value);
                    } catch (Throwable ignore) {
                    }
                })
                .show();
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------------------ оформление

    private static View optionRow(Context context, String badge, String text, final Runnable onClick) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setBackground(card(16, 1));

        final TextView circle = new TextView(context);
        circle.setText(badge);
        circle.setGravity(Gravity.CENTER);
        circle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        circle.setTextColor(KamiGramUi.accent());
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        final GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor((KamiGramUi.accent() & 0x00FFFFFF) | 0x1F000000);
        circle.setBackground(circleBg);
        row.addView(circle, new LinearLayout.LayoutParams(dp(40), dp(40)));

        final TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        label.setTextColor(KamiGramUi.primaryText());
        final LinearLayout.LayoutParams labelParams =
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(12);
        row.addView(label, labelParams);

        final TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        chevron.setTextColor(KamiGramUi.secondaryText());
        row.addView(chevron, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private static void addRow(LinearLayout parent, View row) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        parent.addView(row, params);
    }

    private static GradientDrawable card(int radiusDp, int strokeDp) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setColor(KamiGramUi.surface());
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), KamiGramUi.separator());
        }
        return drawable;
    }

    private static void dismiss(Dialog dialog) {
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        } catch (Throwable ignore) {
        }
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}
