package org.telegram.messenger.kamigram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import org.telegram.ui.ActionBar.ActionBarMenuItem;

/**
 * Sakura: показ ID чатов и пользователей.
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
    /**
     * ID в профиле/канале/группе — МЕЖДУ описанием и ссылкой @username.
     *
     * Раньше ID прятался в «трёх точках»; пользователь просил иначе: строка
     * с ID должна стоять сразу под описанием (как в других клиентах),
     * а ссылка @username остаётся ниже родным элементом Telegram.
     */
    public static String aboutWithId(CharSequence about, long dialogId) {
        try {
            if (!KamiGramConfig.showIds() || dialogId == 0) {
                return about == null ? "" : about.toString();
            }
            final String id = Long.toString(dialogId);
            final String prefix = "ID: " + id;
            final String text = about == null ? "" : about.toString();
            if (text.contains(prefix)) {
                return text;
            }
            return text.length() == 0 ? prefix : text + "\n\n" + prefix;
        } catch (Throwable ignore) {
            return about == null ? "" : about.toString();
        }
    }

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
            KamiGramLog.e(e);
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
                    manager.setPrimaryClip(ClipData.newPlainText("Sakura ID", Long.toString(dialogId)));
                    KamiGramUi.notify(context, "ID скопирован: " + dialogId);
                }
            }
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
        return true;
    }

    /** Короткий вид ID для интерфейса. */
    public static String shortId(long dialogId) {
        return Long.toString(dialogId);
    }
}
