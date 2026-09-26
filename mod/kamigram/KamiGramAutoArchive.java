package org.telegram.messenger.kamigram;

import android.os.SystemClock;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;

/**
 * Sakura r115: перегруженные диалоги (строго больше 500 непрочитанных)
 * автоматически уходят В АРХИВ — и только; sweeps are event/resume driven
 * (без постоянного таймера). Ничего не удаляется: диалоги не стираются,
 * боты не блокируются, каналы и группы не покидаются, медиа-кэш (видео,
 * фото, файлы, музыка) не трогается вообще — перемещение в архив файлы
 * не удаляет.
 *
 * Защищены: «Избранное», служебный аккаунт, секретные чаты, закреплённые
 * диалоги, личные переписки с живыми людьми и контакты, каналы/группы,
 * где аккаунт — создатель или уже покинул их.
 *
 * KAMIGRAM_ARCHIVE_CLEAN_R77 (r115: только архивация, без удалений —
 * прежняя чистка стирала медиа-кэш «сама»),
 * KAMIGRAM_ARCHIVE_SAFETY_R78 (личные и контакты неприкосновенны),
 * KAMIGRAM_ARCHIVE_THRESHOLD_R82 (строго больше 500 непрочитанных).
 */
public final class KamiGramAutoArchive implements NotificationCenter.NotificationCenterDelegate {

    private static final int LIMIT = 500;
    private static final long MIN_INTERVAL = 5_000L;
    private static final long DELAY = 0L;

    private static final KamiGramAutoArchive INSTANCE = new KamiGramAutoArchive();
    private static final HashMap<Integer, Boolean> POSTED = new HashMap<>();
    private static final HashMap<Integer, Long> LAST_RUN = new HashMap<>();
    private static boolean initialized;

    private KamiGramAutoArchive() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter.getInstance(account).addObserver(INSTANCE, NotificationCenter.dialogsNeedReload);
                NotificationCenter.getInstance(account).addObserver(INSTANCE, NotificationCenter.updateInterfaces);
                schedule(account);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** r82: явный триггер при возврате в приложение. */
    public static void checkNow(int account) {
        if (account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT) {
            schedule(account);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
                return;
            }
            schedule(account);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static synchronized void schedule(final int account) {
        if (!KamiGramConfig.autoArchive() || Boolean.TRUE.equals(POSTED.get(account))) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        final Long previous = LAST_RUN.get(account);
        if (previous != null && now - previous < MIN_INTERVAL) {
            return;
        }
        POSTED.put(account, true);
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (KamiGramAutoArchive.class) {
                POSTED.put(account, false);
                LAST_RUN.put(account, SystemClock.elapsedRealtime());
            }
            INSTANCE.sweep(account);
        }, DELAY);
    }

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
            final UserConfig config = UserConfig.getInstance(account);
            final long self = config.getClientUserId();

            for (int a = 0; a < dialogs.size(); a++) {
                final TLRPC.Dialog dialog = dialogs.valueAt(a);
                if (dialog == null || dialog.isFolder) {
                    continue; // папки не переносятся сами в себя
                }
                /* KAMIGRAM_ARCHIVE_THRESHOLD_R82: строго больше 500
                   непрочитанных, посчитанных сервером. */
                final int unread = Math.max(0, dialog.unread_count);
                if (unread <= LIMIT) {
                    continue;
                }

                final long dialogId = dialogs.keyAt(a);
                if (dialogId == 0 || dialogId == self || dialogId == 777000L
                    || DialogObject.isEncryptedDialog(dialogId)) {
                    continue;
                }
                if (dialog.folder_id == 1 || dialog.pinned) {
                    continue; // уже в архиве или закреплён — не трогаем
                }

                if (dialogId > 0) {
                    /* KAMIGRAM_ARCHIVE_SAFETY_R78: живые люди и контакты
                       неприкосновенны; в архив может уйти только
                       перегруженный неконтатный бот. */
                    final TLRPC.User user = controller.getUser(dialogId);
                    if (user == null || UserObject.isService(user.id)
                        || ContactsController.getInstance(account).isContact(user.id)
                        || !user.bot) {
                        continue;
                    }
                } else {
                    final TLRPC.Chat chat = controller.getChat(-dialogId);
                    if (chat == null || chat.creator || chat.left || chat.kicked) {
                        continue;
                    }
                }

                /* KAMIGRAM_ARCHIVE_CLEAN_R77 (r115): ТОЛЬКО архивация.
                   Диалог переезжает в архив штатным механизмом Telegram —
                   сообщение остаётся, файлы медиа остаются, ничего не
                   блокируется и не покидается. */
                controller.addDialogToFolder(dialogId, 1, -1, 0);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }
}
