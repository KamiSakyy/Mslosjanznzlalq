package org.telegram.messenger.kamigram;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashSet;

/** Native-chat bulk selection helper. It does not perform network operations. */
public final class KamiGramBulkSelector {
    public interface Callback {
        void onCount(int count);
    }

    private KamiGramBulkSelector() {
    }

    /**
     * Select in list order without index jumps. Only call/video-call service
     * actions are filtered; ordinary text, media and other service messages are
     * passed to Telegram's normal selection code.
     */
    public static ArrayList<MessageObject> collect(ArrayList<MessageObject> source, int requested) {
        final ArrayList<MessageObject> result = new ArrayList<>();
        final HashSet<Integer> seen = new HashSet<>();
        if (source == null || requested <= 0) {
            return result;
        }
        for (int i = 0; i < source.size() && result.size() < requested; i++) {
            final MessageObject message = source.get(i);
            if (message == null || message.messageOwner == null || isCallService(message)) {
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

    private static boolean isCallService(MessageObject message) {
        try {
            if (message.messageOwner.action == null) {
                return false;
            }
            final String name = message.messageOwner.action.getClass().getSimpleName().toLowerCase(java.util.Locale.US);
            return name.contains("phonecall") || name.contains("videocall")
                || name.contains("groupcall") || name.contains("conferencecall");
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Native dialog: 10, 50, or a user-entered count. */
    public static void showCountDialog(Context context, final Callback callback) {
        if (context == null || callback == null) {
            return;
        }
        try {
            new android.app.AlertDialog.Builder(context)
                .setTitle("Массовый выбор сообщений")
                .setItems(new CharSequence[]{"10", "50", "Произвольное количество"}, (dialog, which) -> {
                    if (which == 0) {
                        callback.onCount(10);
                    } else if (which == 1) {
                        callback.onCount(50);
                    } else {
                        showCustomCount(context, callback);
                    }
                })
                .show();
        } catch (Throwable ignore) {
        }
    }

    private static void showCustomCount(Context context, final Callback callback) {
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Количество");
        input.setSelectAllOnFocus(true);
        try {
            new android.app.AlertDialog.Builder(context)
                .setTitle("Количество сообщений")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Выбрать", (dialog, which) -> {
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
}
