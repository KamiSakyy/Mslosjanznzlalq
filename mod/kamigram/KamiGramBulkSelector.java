package org.telegram.messenger.kamigram;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Массовый выбор сообщений в родном чате.
 *
 * Компактный минималистичный диалог в стиле Sakura: чипсы-фильтры по типу
 * (все/фото/видео/кружки/голосовые/файлы/ссылки/своё расширение) и три
 * небольших кнопки количества. Системные сообщения (звонки, видеозвонки и
 * прочие сервисные) не выделяются — как в родном выделении Telegram.
 */
public final class KamiGramBulkSelector {

    /** Количество + фильтр по типу контента. */
    public interface Callback {
        void onCount(int count, Filter filter);
    }

    /** Фильтр по типу сообщений. */
    public static final class Filter {
        public static final int ALL = 0;
        public static final int PHOTO = 1;
        public static final int VIDEO = 2;
        public static final int ROUND = 3;
        public static final int VOICE = 4;
        public static final int FILE = 5;
        public static final int LINK = 6;
        public static final int EXT = 7;

        public final int type;
        public final String extension;

        Filter(int type, String extension) {
            this.type = type;
            this.extension = extension;
        }

        public static Filter all() {
            return new Filter(ALL, null);
        }
    }

    private KamiGramBulkSelector() {
    }

    // ------------------------------------------------------------------ отбор

    /** Первые `requested` сообщений, подходящие под фильтр, в порядке списка. */
    public static ArrayList<MessageObject> collect(ArrayList<MessageObject> source,
                                                   int requested, Filter filter) {
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
            if (!matchesFilter(message, filter)) {
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
                    || name.contains("groupcall") || name.contains("conferencecall")
                    || name.contains("call")) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Соответствие сообщения выбранному типу. */
    public static boolean matchesFilter(MessageObject message, Filter filter) {
        if (filter == null || filter.type == Filter.ALL) {
            return true;
        }
        try {
            switch (filter.type) {
                case Filter.PHOTO:
                    return message.isPhoto();
                case Filter.VIDEO:
                    return message.isVideo() && !message.isRoundVideo();
                case Filter.ROUND:
                    return message.isRoundVideo();
                case Filter.VOICE:
                    return message.isVoice();
                case Filter.FILE:
                    return message.isDocument() && !message.isVoice()
                        && !message.isRoundVideo() && !message.isVideo() && !message.isMusic();
                case Filter.LINK:
                    return hasLink(message);
                case Filter.EXT:
                    return matchesExtension(message, filter.extension);
                default:
                    return true;
            }
        } catch (Throwable ignore) {
            return true;
        }
    }

    private static boolean hasLink(MessageObject message) {
        final String text = message.messageOwner.message;
        if (text == null || text.isEmpty()) {
            return false;
        }
        final String lower = text.toLowerCase(java.util.Locale.US);
        return lower.contains("http://") || lower.contains("https://")
            || lower.contains("www.") || lower.contains("t.me/");
    }

    private static boolean matchesExtension(MessageObject message, String extension) {
        if (extension == null || !message.isDocument()) {
            return false;
        }
        String ext = extension.trim().toLowerCase(java.util.Locale.US);
        if (ext.isEmpty()) {
            return false;
        }
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        final String name = message.getFileName();
        return name != null && name.toLowerCase(java.util.Locale.US).endsWith(ext);
    }

    // ------------------------------------------------------------------ компактный диалог

    private static final String[] CHIP_TITLES = {
        "Все", "Фото", "Видео", "Кружки", "Голосовые", "Файлы", "Ссылки", "Расширение"
    };
    private static final int[] CHIP_TYPES = {
        Filter.ALL, Filter.PHOTO, Filter.VIDEO, Filter.ROUND,
        Filter.VOICE, Filter.FILE, Filter.LINK, Filter.EXT
    };

    /** Компактный диалог: чипсы-фильтры + количество. */
    public static void showCountDialog(Context context, final Callback callback) {
        if (context == null || callback == null) {
            return;
        }
        try {
            final Dialog[] shown = new Dialog[1];
            final int[] selectedType = {Filter.ALL};

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            /* r111: компактная карточка — маленькое аккуратное окно вместо огромного диалога. */
            root.setBackground(cardBackground());
            root.setPadding(dp(14), dp(12), dp(14), dp(12));

            // --- шапка: заголовок слева, «Отмена» справа ---
            final LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            final TextView heading = new TextView(context);
            heading.setText("Массовый выбор");
            heading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            heading.setTypeface(Typeface.DEFAULT_BOLD);
            heading.setTextColor(KamiGramUi.primaryText());
            heading.setSingleLine(true);
            header.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView cancel = new TextView(context);
            cancel.setText("Отмена");
            cancel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            cancel.setTextColor(KamiGramUi.accent());
            cancel.setSingleLine(true);
            cancel.setPadding(dp(10), dp(4), dp(2), dp(4));
            cancel.setClickable(true);
            cancel.setFocusable(true);
            cancel.setOnClickListener(v -> dismiss(shown[0]));
            header.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // --- ввод расширения (показывается с чипсом «Расширение») ---
            final EditText extInput = new EditText(context);
            extInput.setSingleLine(true);
            extInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            extInput.setHint("например, .apk");
            extInput.setHintTextColor(KamiGramUi.secondaryText());
            extInput.setTextColor(KamiGramUi.primaryText());
            extInput.setBackground(pill(dp(10), true));
            extInput.setPadding(dp(12), dp(7), dp(12), dp(7));
            extInput.setVisibility(View.GONE);

            // --- чипсы фильтров (горизонтальная прокрутка) ---
            final HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            final LinearLayout chips = new LinearLayout(context);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            final TextView[] chipViews = new TextView[CHIP_TITLES.length];
            for (int i = 0; i < CHIP_TITLES.length; i++) {
                final int type = CHIP_TYPES[i];
                final TextView chip = chip(context, CHIP_TITLES[i], type == Filter.ALL);
                chipViews[i] = chip;
                chip.setOnClickListener(v -> {
                    selectedType[0] = type;
                    for (int j = 0; j < chipViews.length; j++) {
                        paintChip(chipViews[j], CHIP_TYPES[j] == type);
                    }
                    extInput.setVisibility(type == Filter.EXT ? View.VISIBLE : View.GONE);
                    if (type == Filter.EXT) {
                        extInput.requestFocus();
                    }
                });
                final LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                chipParams.rightMargin = dp(6);
                chips.addView(chip, chipParams);
            }
            scroll.addView(chips);
            final LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scrollParams.topMargin = dp(8);
            root.addView(scroll, scrollParams);

            final LinearLayout.LayoutParams extParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            extParams.topMargin = dp(8);
            root.addView(extInput, extParams);

            // --- своё количество (показывается кнопкой «Своё») ---
            final LinearLayout customRow = new LinearLayout(context);
            customRow.setOrientation(LinearLayout.HORIZONTAL);
            customRow.setGravity(Gravity.CENTER_VERTICAL);
            customRow.setVisibility(View.GONE);

            final EditText countInput = new EditText(context);
            countInput.setSingleLine(true);
            countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            countInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            countInput.setHint("Количество");
            countInput.setHintTextColor(KamiGramUi.secondaryText());
            countInput.setTextColor(KamiGramUi.primaryText());
            countInput.setBackground(pill(dp(10), true));
            countInput.setPadding(dp(10), 0, dp(10), 0);
            customRow.addView(countInput, new LinearLayout.LayoutParams(0, dp(36), 1f));

            final TextView okButton = smallButton(context, "OK");
            okButton.setOnClickListener(v -> {
                final int value = parseInt(countInput.getText().toString(), -1);
                if (value < 1) {
                    return;
                }
                finish(shown[0], callback, Math.min(value, 10000),
                    selectedType[0], extInput.getText().toString());
            });
            final LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            okParams.leftMargin = dp(8);
            customRow.addView(okButton, okParams);

            final LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            customParams.topMargin = dp(8);
            root.addView(customRow, customParams);

            // --- кнопки количества ---
            final LinearLayout counts = new LinearLayout(context);
            counts.setOrientation(LinearLayout.HORIZONTAL);

            final TextView ten = smallButton(context, "10");
            ten.setOnClickListener(v -> finish(shown[0], callback, 10,
                selectedType[0], extInput.getText().toString()));
            final TextView fifty = smallButton(context, "50");
            fifty.setOnClickListener(v -> finish(shown[0], callback, 50,
                selectedType[0], extInput.getText().toString()));
            final TextView custom = smallButton(context, "Своё");
            custom.setOnClickListener(v -> {
                final boolean visible = customRow.getVisibility() == View.VISIBLE;
                customRow.setVisibility(visible ? View.GONE : View.VISIBLE);
                if (!visible) {
                    countInput.setText("");
                    countInput.requestFocus();
                }
            });
            for (TextView button : new TextView[]{ten, fifty, custom}) {
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1f);
                if (button != ten) {
                    params.leftMargin = dp(8);
                }
                counts.addView(button, params);
            }
            final LinearLayout.LayoutParams countsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            countsParams.topMargin = dp(10);
            root.addView(counts, countsParams);

            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(root);
            final Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(0x00000000));
                window.setLayout(Math.min((int) (AndroidUtilities.displaySize.x * 0.86f), dp(300)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setDimAmount(0.55f);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
            shown[0] = dialog;
        } catch (Throwable ignore) {
        }
    }

    private static void finish(Dialog dialog, Callback callback, int count,
                               int type, String extensionText) {
        dismiss(dialog);
        String extension = null;
        if (type == Filter.EXT) {
            extension = extensionText == null ? null : extensionText.trim();
            if (extension == null || extension.isEmpty()) {
                type = Filter.ALL;
            }
        }
        callback.onCount(count, new Filter(type, extension));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ оформление

    private static TextView chip(Context context, String text, boolean selected) {
        final TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setSingleLine(true);
        chip.setClickable(true);
        chip.setFocusable(true);
        paintChip(chip, selected);
        return chip;
    }

    private static void paintChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setTextColor(KamiGramUi.accent());
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setBackground(tintedPill(dp(13), (KamiGramUi.accent() & 0x00FFFFFF) | 0x1F000000));
        } else {
            chip.setTextColor(KamiGramUi.secondaryText());
            chip.setTypeface(Typeface.DEFAULT);
            chip.setBackground(pill(dp(13), false));
        }
    }

    private static TextView smallButton(Context context, String text) {
        final TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTextColor(KamiGramUi.accent());
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setSingleLine(true);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(pill(dp(18), true));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    /** Контурная пилюля: тонкая обводка, без тяжёлой заливки. */
    /** Компактная карточка диалога «Массовый выбор» (r111). */
    private static GradientDrawable cardBackground() {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(KamiGramUi.surface());
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(Math.max(1, dp(1)), KamiGramUi.separator());
        return drawable;
    }

    private static GradientDrawable pill(int radius, boolean stroke) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius);
        drawable.setColor(0);
        if (stroke) {
            drawable.setStroke(dp(1), KamiGramUi.separator());
        }
        return drawable;
    }

    private static GradientDrawable tintedPill(int radius, int color) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius);
        drawable.setColor(color);
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
