package org.telegram.messenger.kamigram;

import android.os.SystemClock;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * KamiGram r68: авто-архив чатов с непрочитанными.
 *
 * Просьба пользователя: группы и каналы, где непрочитанных больше 500,
 * автоматически отправлять в архив.
 *
 * Как работает: мод подписывается на обновления списка чатов
 * ({@code dialogsNeedReload} / {@code updateInterfaces}). Когда список обновился,
 * через небольшую паузу один раз проходит по всем диалогам и те, у которых
 * непрочитанных больше 100, отправляет в архив (папка id = 1) родным методом
 * Telegram {@code MessagesController.addDialogToFolder} — он же делает это, когда
 * вы свайпаете чат «В архив», поэтому никаких самодельных перестроений списка нет.
 *
 * Важные детали:
 * <ul>
 *   <li>архив ставится один раз: если непрочитанных стало меньше 100, пометка
 *       сбрасывается (в следующий раз чат снова уйдёт в архив);</li>
 *   <li>если пользователь САМ разархивировал чат, мод не спорит с ним — пока
 *       непрочитанных не стало больше, чем было при авто-архиве, чат остаётся
 *       на месте;</li>
 *   <li>«Избранное», сервисные чаты и секретные чаты не трогаются;</li>
 *   <li>работает только когда включён переключатель «Авто-архив» в центре
 *       KamiGram (по умолчанию включён).</li>
 * </ul>
 */
public final class KamiGramAutoArchive implements NotificationCenter.NotificationCenterDelegate {

    /** Порог: больше 500 непрочитанных. */
    private static final int LIMIT = 500;
    /** Не чаще одной проверки в 5 секунд. */
    private static final long MIN_INTERVAL = 5000L;
    /** Пауза после обновления списка — чтобы Telegram успел применить свои изменения. */
    private static final long DELAY = 1500L;

    private static final KamiGramAutoArchive INSTANCE = new KamiGramAutoArchive();
    /** Насколько непрочитанных было в чате в момент авто-архива. */
    private static final HashMap<Long, Integer> ARCHIVED = new HashMap<>();

    private static boolean initialized;
    private static boolean posted;
    private static long lastRun;

    private KamiGramAutoArchive() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(INSTANCE, NotificationCenter.dialogsNeedReload);
                NotificationCenter.getInstance(a).addObserver(INSTANCE, NotificationCenter.updateInterfaces);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (!KamiGramConfig.autoArchive()) {
                return;
            }
            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
                return;
            }
            schedule(account);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static void schedule(final int account) {
        if (posted) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        if (now - lastRun < MIN_INTERVAL) {
            return;
        }
        posted = true;
        AndroidUtilities.runOnUIThread(() -> {
            posted = false;
            lastRun = SystemClock.elapsedRealtime();
            INSTANCE.sweep(account);
        }, DELAY);
    }

    /** Один проход по списку чатов: непрочитанных > 100 → в архив. */
    private void sweep(int account) {
        try {
            if (!KamiGramConfig.autoArchive()) {
                return;
            }
            final MessagesController controller = MessagesController.getInstance(account);
            final LongSparseArray<TLRPC.Dialog> dialogs = controller.dialogs_dict;
            if (dialogs == null || dialogs.size() == 0) {
                return;
            }
            final long self = UserConfig.getInstance(account).getClientUserId();

            final ArrayList<Long> archive = new ArrayList<>();
            final ArrayList<Long> forget = new ArrayList<>();
            for (int a = 0; a < dialogs.size(); a++) {
                final long dialogId = dialogs.keyAt(a);
                final TLRPC.Dialog dialog = dialogs.valueAt(a);
                if (dialog == null || dialog.isFolder) {
                    continue;
                }
                final int unread = dialog.unread_count + (dialog.unread_mark ? 1 : 0);
                if (unread <= LIMIT) {
                    forget.add(dialogId);
                    continue;
                }
                if (dialog.folder_id == 1) {
                    continue; // уже в архиве
                }
                if (dialogId == self || dialogId == 777000) {
                    continue; // «Избранное» и сервисные чаты
                }
                if (DialogObject.isEncryptedDialog(dialogId) || !DialogObject.isChatDialog(dialogId)) {
                    continue; // личные чаты и контакты неприкосновенны
                }
                final TLRPC.Chat chat = controller.getChat(-dialogId);
                if (chat == null) {
                    continue; // только группы и каналы
                }
                final Integer was = ARCHIVED.get(dialogId);
                if (was != null && unread <= was) {
                    continue; // пользователь сам разархивировал — не спорим
                }
                ARCHIVED.put(dialogId, unread);
                archive.add(dialogId);
            }

            for (int a = 0; a < forget.size(); a++) {
                ARCHIVED.remove(forget.get(a));
            }
            for (int a = 0; a < archive.size(); a++) {
                controller.addDialogToFolder(archive.get(a), 1, 0, 0);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }
}
