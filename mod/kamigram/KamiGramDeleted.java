package org.telegram.messenger.kamigram;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;

/**
 * KamiGram: удалённые сообщения остаются видны.
 *
 * «Удалил для всех» — а мне всё равно интересно, что там было, особенно если
 * сообщение удалили у меня. Мод сохраняет текст (и подпись к медиа) в локальный
 * журнал на устройстве, а не на сервере: журнал можно посмотреть и очистить.
 *
 * Хранится в общих настройках, максимум 300 последних записей — память не течёт.
 */
public final class KamiGramDeleted {

    private static final String PREFS = "kamigram_deleted";
    private static final String KEY_LOG = "log";
    private static final int MAX_ENTRIES = 300;
    private static final String SEPARATOR = "\u0001";
    private static final String LINE = "\u0002";

    private KamiGramDeleted() {
    }

    /**
     * Записать удаляемое сообщение. Вызывается из MessagesController до удаления.
     *
     * @param dialogId чат
     * @param text     текст или подпись
     */
    public static void log(long dialogId, CharSequence text) {
        try {
            if (text == null || text.length() == 0 || dialogId == 0) {
                return;
            }
            final String clean = text.toString().replace(LINE, " ").replace(SEPARATOR, " ");
            final String entry = System.currentTimeMillis() + SEPARATOR + dialogId + SEPARATOR
                + (clean.length() > 500 ? clean.substring(0, 500) + "…" : clean);
            final android.content.SharedPreferences preferences = prefs();
            if (preferences == null) {
                return;
            }
            String stored = preferences.getString(KEY_LOG, "");
            if (stored == null) {
                stored = "";
            }
            final StringBuilder builder = new StringBuilder(entry);
            int lines = 1;
            if (stored.length() > 0) {
                for (String line : stored.split(LINE)) {
                    if (lines >= MAX_ENTRIES) {
                        break;
                    }
                    builder.append(LINE).append(line);
                    lines++;
                }
            }
            preferences.edit().putString(KEY_LOG, builder.toString()).apply();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    public static int size() {
        try {
            final android.content.SharedPreferences preferences = prefs();
            if (preferences == null) {
                return 0;
            }
            final String stored = preferences.getString(KEY_LOG, "");
            if (stored == null || stored.length() == 0) {
                return 0;
            }
            return stored.split(LINE).length;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static void clear() {
        try {
            final android.content.SharedPreferences preferences = prefs();
            if (preferences != null) {
                preferences.edit().remove(KEY_LOG).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Список записей: время — чат — текст. */
    public static ArrayList<String> entries() {
        final ArrayList<String> result = new ArrayList<>();
        try {
            final android.content.SharedPreferences preferences = prefs();
            if (preferences == null) {
                return result;
            }
            final String stored = preferences.getString(KEY_LOG, "");
            if (stored == null || stored.length() == 0) {
                return result;
            }
            for (String line : stored.split(LINE)) {
                final String[] parts = line.split(SEPARATOR);
                if (parts.length < 3) {
                    continue;
                }
                final long time = Long.parseLong(parts[0]);
                final String date = android.text.format.DateFormat.format("dd.MM HH:mm", time).toString();
                result.add(date + "  ·  " + parts[1] + "\n" + parts[2]);
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
        return result;
    }

    /** Экран журнала удалённых сообщений. */
    public static void show(final Context context) {
        if (context == null) {
            return;
        }
        try {
            final ArrayList<String> items = entries();
            final StringBuilder builder = new StringBuilder();
            if (items.isEmpty()) {
                builder.append("Журнал пуст. Сюда попадают тексты сообщений, удалённых после включения мода.");
            } else {
                for (int i = 0; i < items.size() && i < 80; i++) {
                    builder.append(i + 1).append(". ").append(items.get(i)).append("\n\n");
                }
                if (items.size() > 80) {
                    builder.append("…и ещё ").append(items.size() - 80).append(" записей\n");
                }
            }
            final android.widget.TextView text = new android.widget.TextView(context);
            text.setText(builder.toString());
            text.setTextSize(13);
            text.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            text.setTextColor(ThemeHook.primaryText());
            text.setTextIsSelectable(true);

            final android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.addView(text, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Удалённые сообщения (" + items.size() + ")")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .setNegativeButton("Очистить журнал", null)
                .create();
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                    clear();
                    dialog.dismiss();
                });
            });
            dialog.show();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static android.content.SharedPreferences prefs() {
        return org.telegram.messenger.ApplicationLoader.applicationContext
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }
}
