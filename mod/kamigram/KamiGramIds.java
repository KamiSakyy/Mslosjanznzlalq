package org.telegram.messenger.kamigram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.ActionBarMenuItem;

/**
 * KamiGram: показ ID чатов и пользователей.
 *
 * Раньше переключатель «Показывать ID» в моде ничего не делал - он был в настройках,
 * но нигде не использовался. Теперь ID видно в шапке чата (в подписи под именем)
 * и в меню «три точки» (нажатие копирует ID в буфер).
 */
public final class KamiGramIds {

    public static final int ID_COPY = 0x4B44; // «KD»

    private KamiGramIds() {
    }

    /** Дописывает «· id 123456» к подписи чата. */
    public static CharSequence withId(CharSequence subtitle, long dialogId) {
        try {
            if (dialogId == 0 || !KamiGramConfig.showIds()) {
                return subtitle;
            }
            final String id = Long.toString(dialogId);
            if (subtitle == null || subtitle.length() == 0) {
                return "id " + id;
            }
            return subtitle + "  ·  id " + id;
        } catch (Throwable e) {
            return subtitle;
        }
    }

    /** Добавляет строку с ID в меню «три точки». */
    public static void addRow(ActionBarMenuItem menuItem, long dialogId) {
        try {
            if (menuItem == null || dialogId == 0 || !KamiGramConfig.showIds()) {
                return;
            }
            menuItem.addSubItem(ID_COPY, 0, "ID: " + dialogId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Копирует ID в буфер. true, если нажатие наше. */
    public static boolean handleClick(int id, long dialogId, Context context) {
        if (id != ID_COPY) {
            return false;
        }
        try {
            if (context != null && dialogId != 0) {
                final ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (manager != null) {
                    manager.setPrimaryClip(ClipData.newPlainText("KamiGram ID", Long.toString(dialogId)));
                    KamiGramUi.notify(context, "ID скопирован: " + dialogId);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return true;
    }

    /** Короткий вид ID для интерфейса. */
    public static String shortId(long dialogId) {
        return Long.toString(dialogId);
    }
}
