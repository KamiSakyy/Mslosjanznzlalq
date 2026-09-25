package org.telegram.messenger.kamigram;

import android.content.Context;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;

/** Instant native deletion of the current user's loaded messages. */
public final class KamiGramDeleteMyMessages {
    private KamiGramDeleteMyMessages() {
    }

    /**
     * Uses Telegram's own deleteMessages path in one batch. Outgoing messages
     * are identified by MessageObject.isOutOwner(); call/video-call service
     * actions are deliberately excluded. No confirmation dialog or timer is
     * inserted, and KamiGramDeleted records the local copy before deletion.
     */
    public static int delete(Context context, int account, long dialogId, long topicId,
                             int chatMode, ArrayList<MessageObject> loaded) {
        final ArrayList<Integer> ids = new ArrayList<>();
        if (loaded != null) {
            for (int i = 0; i < loaded.size(); i++) {
                final MessageObject message = loaded.get(i);
                if (message == null || message.messageOwner == null || !message.isOutOwner()
                    || message.messageOwner.id <= 0 || isCallService(message)) {
                    continue;
                }
                if (!ids.contains(message.messageOwner.id)) {
                    ids.add(message.messageOwner.id);
                }
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        try {
            KamiGramDeleted.beforeDelete(dialogId, ids);
            final MessagesController controller = MessagesController.getInstance(account);
            // Telegram's own deleteMessages takes the topic as int; the chat
            // passes threadMessageId (long), exactly like stock `(int) getTopicId()`.
            controller.deleteMessages(ids, null, null, dialogId, (int) topicId, true, chatMode);
        } catch (Throwable ignored) {
        }
        return ids.size();
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
}
