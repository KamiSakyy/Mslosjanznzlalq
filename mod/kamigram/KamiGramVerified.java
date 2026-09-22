package org.telegram.messenger.kamigram;

import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

/**
 * KamiGram: официальная галочка для СВОИХ каналов — только локально.
 *
 * Работает так: когда Telegram загружает данные канала, мод выставляет у него
 * флаг verified тем же способом, каким это делает сервер. Дальше галочку рисует
 * сам Telegram своим штатным кодом — ни дизайн, ни размер, ни цвет не меняются,
 * это ровно та же официальная галочка.
 *
 * Видно только на этом устройстве: на сервере ничего не меняется, другие люди
 * галочку не увидят. Список каналов — ниже, в одном месте.
 */
public final class KamiGramVerified {

    // =========================================================================
    //  МОИ КАНАЛЫ (сравнение по @username, регистр не важен)
    // =========================================================================
    private static final String[] MY_CHANNELS = {
        "AsuMeo",
        "AsunaYukki",
    };
    // =========================================================================

    private KamiGramVerified() {
    }

    /** Нужно ли выдать галочку этому каналу. */
    public static boolean isVerified(TLRPC.Chat chat) {
        try {
            if (chat == null) {
                return false;
            }
            final String username = chat.username;
            if (!TextUtils.isEmpty(username)) {
                for (int a = 0; a < MY_CHANNELS.length; a++) {
                    if (MY_CHANNELS[a].equalsIgnoreCase(username)) {
                        return true;
                    }
                }
            }
            // совпадение по названию — строгое, чтобы галочка не появлялась
            // у посторонних каналов со похожим словом в названии
            final String title = chat.title;
            if (!TextUtils.isEmpty(title)) {
                for (int a = 0; a < MY_CHANNELS.length; a++) {
                    if (MY_CHANNELS[a].equalsIgnoreCase(title.trim())
                        || ("@" + MY_CHANNELS[a]).equalsIgnoreCase(title.trim())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /** Ставит флаг verified у канала перед показом (галочка Telegram). */
    public static void apply(TLRPC.Chat chat) {
        try {
            if (chat != null && !chat.verified && isVerified(chat)) {
                chat.verified = true;
            }
        } catch (Throwable ignore) {
        }
    }

    public static String describe() {
        return "галочка для: " + TextUtils.join(", ", MY_CHANNELS);
    }
}
