package org.telegram.messenger.kamigram;

import android.os.SystemClock;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;

/**
 * KamiGram r77: clean up an archive that has become unusable, and nothing else.
 *
 * This class deliberately does not archive chats. It only inspects dialogs that
 * are already in Telegram's archive (folder_id == 1). A dialog is eligible when
 * its unread notification count is strictly greater than 500:
 *
 *   - bot dialog: block the bot and delete the dialog;
 *   - group/channel: leave the chat, which also removes its dialog;
 *   - ordinary user dialog: never touch it, including contacts.
 *
 * Favorites, service accounts, secret chats, folders, and unknown peers are
 * conservative no-ops. The feature remains behind the existing KamiGram
 * autoArchive switch so a user can disable it without changing Telegram data.
 */
public final class KamiGramAutoArchive implements NotificationCenter.NotificationCenterDelegate {

    private static final int ARCHIVE_FOLDER_ID = 1;
    private static final int LIMIT = 500;
    /* r81: debounce only duplicate notifications; never make a destructive
       sweep wait behind an artificial one-second timer. */
    private static final long MIN_INTERVAL = 1_000L;
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
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (!KamiGramConfig.autoArchive()
                || account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
                return;
            }
            schedule(account);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static synchronized void schedule(final int account) {
        if (Boolean.TRUE.equals(POSTED.get(account))) {
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

    /** KAMIGRAM_ARCHIVE_CLEAN_R77: only pre-existing archive dialogs. */
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
            for (int a = 0; a < dialogs.size(); a++) {
                final TLRPC.Dialog dialog = dialogs.valueAt(a);
                if (dialog == null || dialog.isFolder || dialog.folder_id != ARCHIVE_FOLDER_ID) {
                    continue; // never inspect the main list or a filter folder
                }
                /* unread_mark is a badge, not an additional unread message.
                   r81 must act only when the server count itself is > 500. */
                final int unread = Math.max(0, dialog.unread_count);
                if (unread <= LIMIT) {
                    continue; // requirement is strictly more than 500
                }
                final long dialogId = dialogs.keyAt(a);
                if (dialogId == self || dialogId == 777000L || DialogObject.isEncryptedDialog(dialogId)) {
                    continue; // Saved Messages, Telegram service, and secret chats
                }

                if (dialogId > 0) {
                    /* KAMIGRAM_ARCHIVE_SAFETY_R78: every private user dialog is
                       protected. This explicit contact check is intentionally
                       before the bot branch, so even a bot that was added to the
                       address book cannot be touched by archive cleanup. */
                    final TLRPC.User user = controller.getUser(dialogId);
                    if (user == null || UserObject.isService(user.id)
                        || ContactsController.getInstance(account).isContact(user.id)
                        || !user.bot) {
                        continue;
                    }
                    // Only a non-contact bot in the archive is destructive:
                    // block it first, then remove its dialog.
                    controller.blockPeer(dialogId);
                    controller.deleteDialog(dialogId, 0, true);
                    continue;
                }

                final TLRPC.Chat chat = controller.getChat(-dialogId);
                if (chat == null || !ChatObject.isChannel(chat) && !ChatObject.isMegagroup(chat)) {
                    /* Basic groups are still chat dialogs and are safe to leave;
                       a missing/unknown peer is not safe. */
                    if (chat == null) {
                        continue;
                    }
                }
                final TLRPC.InputPeer selfPeer = controller.getInputPeer(self);
                if (selfPeer != null) {
                    /* deleteParticipantFromChat sends channels.leaveChannel for
                       channels and removes the current user from basic groups. */
                    controller.deleteParticipantFromChat(-dialogId, selfPeer);
                }
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }
}
