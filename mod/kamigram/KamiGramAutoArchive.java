package org.telegram.messenger.kamigram;

import android.os.SystemClock;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;

/**
 * KamiGram r82: remove overloaded group/channel dialogs automatically.
 *
 * A dialog is eligible when Telegram's own unread_count is strictly greater
 * than 500. Channels and group chats are left immediately wherever they are
 * (archive or the main list), then Telegram's native dialog removal clears the
 * row. Private user chats and contacts remain protected; only an untrusted,
 * non-contact bot can be blocked and removed. Saved Messages, the service
 * account, folders, secret chats, missing peers, and channels owned by the
 * account are conservative no-ops.
 *
 * The old implementation accidentally passed TL_inputPeerSelf to
 * deleteParticipantFromChat(). That API detects the current user only when it
 * receives TL_inputPeerUser, so it sent a ban request instead of leaving the
 * channel. r82 deliberately obtains the current-user object first.
 */
public final class KamiGramAutoArchive implements NotificationCenter.NotificationCenterDelegate {

    private static final int LIMIT = 500;
    private static final long MIN_INTERVAL = 1_000L;
    private static final long DELAY = 0L;
    private static final long PERIOD = 30_000L;

    private static final KamiGramAutoArchive INSTANCE = new KamiGramAutoArchive();
    private static final HashMap<Integer, Boolean> POSTED = new HashMap<>();
    private static final HashMap<Integer, Long> LAST_RUN = new HashMap<>();
    private static boolean initialized;
    private static boolean periodicPosted;

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
            startPeriodicSweep();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** r82: explicit on-resume trigger so already-loaded dialogs are handled. */
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

    private static synchronized void startPeriodicSweep() {
        if (periodicPosted) {
            return;
        }
        periodicPosted = true;
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (KamiGramAutoArchive.class) {
                periodicPosted = false;
            }
            if (KamiGramConfig.autoArchive()) {
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    schedule(account);
                }
            }
            startPeriodicSweep();
        }, PERIOD);
    }

    /**
     * KAMIGRAM_ARCHIVE_CLEAN_R77 + KAMIGRAM_ARCHIVE_SAFETY_R78
     * + KAMIGRAM_ARCHIVE_THRESHOLD_R82.
     */
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
            final TLRPC.User selfUser = config.getCurrentUser();
            final TLRPC.InputPeer selfPeer = selfUser == null ? null : controller.getInputPeer(selfUser);

            for (int a = 0; a < dialogs.size(); a++) {
                final TLRPC.Dialog dialog = dialogs.valueAt(a);
                if (dialog == null || dialog.isFolder) {
                    continue; // folders themselves are never peers to remove
                }
                /* KAMIGRAM_ARCHIVE_THRESHOLD_R82: no unread_mark inflation;
                   strictly more than 500 server-counted unread messages. */
                final int unread = Math.max(0, dialog.unread_count);
                if (unread <= LIMIT) {
                    continue;
                }

                final long dialogId = dialogs.keyAt(a);
                if (dialogId == 0 || dialogId == self || dialogId == 777000L
                    || DialogObject.isEncryptedDialog(dialogId)) {
                    continue;
                }

                if (dialogId > 0) {
                    /* Private people and contacts are always untouchable. */
                    final TLRPC.User user = controller.getUser(dialogId);
                    if (user == null || UserObject.isService(user.id)
                        || ContactsController.getInstance(account).isContact(user.id)
                        || !user.bot) {
                        continue;
                    }
                    controller.blockPeer(dialogId);
                    controller.deleteDialog(dialogId, 0, true);
                    continue;
                }

                final TLRPC.Chat chat = controller.getChat(-dialogId);
                if (chat == null || chat.creator || chat.left || chat.kicked || selfPeer == null) {
                    continue;
                }

                /* Negative dialog ids are groups/channels. This is deliberately
                   not limited to folder_id == 1 in r82: the user asked that an
                   overloaded ordinary chat/channel be left as well. Passing a
                   TL_inputPeerUser is essential; TL_inputPeerSelf is treated as
                   a moderator target by MessagesController. */
                controller.deleteParticipantFromChat(-dialogId, selfPeer, false, false);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }
}
