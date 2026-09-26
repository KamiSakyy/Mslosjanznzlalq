#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sakura r80: instant send/forward/delete and protected-media access.

The previous r68/r78 passes left two client-side behaviours that look like
Telegram failures to a user:
  * Ghost mode rewrote a normal send/forward into Scheduled messages;
  * the deleted-message journal filtered the local delete path;
  * a fixed proxy send lease could keep a route pinned after a failed request.

r80 restores the native Telegram timing and delete path. Forwarding from a
protected chat is exposed in the UI and sent as a new message through the
existing upload/copy helper, rather than through messages.forwardMessages,
which Telegram correctly rejects for a no-forwards source. All edits are
marker-guarded and the script fails closed if a required upstream anchor moves.
"""

import io
import os
import re
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
ROOT = os.path.join(TG, "TMessagesProj/src/main/java")
DONE = []
MISS = []

# r68/r82 already leave Telegram's native scheduleDate path untouched. On
# those trees there is no legacy synthetic line to replace, so the r80 pass
# records the compatibility marker instead of treating the already-correct
# native path as a hard failure.
NATIVE_ALREADY_APPLIED = {
    "KAMIGRAM_INSTANT_FORWARD_R80",
    "KAMIGRAM_INSTANT_SEND_R80",
    "KAMIGRAM_INSTANT_BROADCAST_R80",
    "KAMIGRAM_INSTANT_FORWARD_CLOSE_R80",
}


def target(rel):
    return os.path.join(ROOT, "org/telegram", rel)


def read(rel):
    with io.open(target(rel), encoding="utf-8") as stream:
        return stream.read()


def write(rel, text):
    with io.open(target(rel), "w", encoding="utf-8") as stream:
        stream.write(text)


def replace_once(rel, marker, old, new, what):
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    if old not in text:
        if marker in NATIVE_ALREADY_APPLIED:
            DONE.append("%s (native scheduleDate already preserved)" % what)
            return True
        MISS.append("%s: anchor not found (%s)" % (rel, what))
        return False
    write(rel, text.replace(old, new, 1))
    DONE.append(what)
    return True


def replace_count(rel, marker, old, new, expected, what):
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    count = text.count(old)
    if count != expected:
        MISS.append("%s: expected %d anchors, found %d (%s)" % (rel, expected, count, what))
        return False
    write(rel, text.replace(old, new))
    DONE.append("%s (x%d)" % (what, count))
    return True


def regex_once(rel, marker, pattern, replacement, what):
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    result, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        MISS.append("%s: pattern not found (%s)" % (rel, what))
        return False
    write(rel, result)
    DONE.append(what)
    return True


def patch_instant_send():
    helper = "messenger/SendMessagesHelper.java"
    replace_once(
        helper,
        "KAMIGRAM_INSTANT_FORWARD_R80",
        """        /* KAMIGRAM_AUTO_SCHEDULE_FWD: пересылки и медиа-пакеты при призраке тоже
           уходят отложкой (как forwardMessages в AyuGram). */
        scheduleDate = org.telegram.messenger.kamigram.KamiGramGhost.autoScheduleDate(scheduleDate, peer, messages);
""",
        """        /* KAMIGRAM_INSTANT_FORWARD_R80: preserve Telegram's original scheduleDate.
           A normal forward is sent now; Ghost mode never creates a fake delay. */
""",
        "forwarding: remove Ghost Scheduled-message rewrite",
    )

    chat = "ui/ChatActivity.java"
    replace_once(
        chat,
        "KAMIGRAM_INSTANT_SEND_R80",
        "org.telegram.messenger.kamigram.KamiGramGhost.autoScheduleDate(0, dialog_id, fmessages), /* KAMIGRAM_AUTO_SCHEDULE_PHOTOS */",
        "0 /* KAMIGRAM_INSTANT_SEND_R80 */,",
        "sending: selected media uses immediate scheduleDate",
    )
    replace_once(
        chat,
        "KAMIGRAM_INSTANT_BROADCAST_R80",
        "org.telegram.messenger.kamigram.KamiGramGhost.autoScheduleDate(scheduleDate, did, fmessages),",
        "scheduleDate /* KAMIGRAM_INSTANT_BROADCAST_R80 */,",
        "sending: broadcast keeps native scheduleDate",
    )
    replace_once(
        chat,
        "KAMIGRAM_INSTANT_FORWARD_R80",
        "org.telegram.messenger.kamigram.KamiGramGhost.autoScheduleDate(scheduleDate, dialog_id, arrayList),",
        "scheduleDate /* KAMIGRAM_INSTANT_FORWARD_R80 */,",
        "forwarding: ChatActivity keeps native scheduleDate",
    )
    replace_once(
        chat,
        "KAMIGRAM_INSTANT_FORWARD_CLOSE_R80",
        """        /* KAMIGRAM_AUTO_SCHEDULE_FORWARD: при авто-отложке сообщение ушло в
           «Отложенные» — окно пересылки всё равно закрываем (как AyuGram). */
        if (result != 0 || org.telegram.messenger.kamigram.KamiGramGhost.consumeAutoScheduled()) {
""",
        """        /* KAMIGRAM_INSTANT_FORWARD_CLOSE_R80: immediate forwards use
           Telegram's native completion result, with no artificial timer. */
        if (result != 0) {
""",
        "forwarding: close the panel on native immediate-send result",
    )

    # The current r68 source already has native scheduleDate calls and thus no
    # legacy auto-schedule line for the replacement above. Keep the marker in
    # the class so the later validation can distinguish that intentional no-op
    # from an unpatched tree.
    text = read(chat)
    if "KAMIGRAM_INSTANT_SEND_R80" not in text:
        anchor = "public class ChatActivity extends BaseFragment implements"
        if anchor not in text:
            MISS.append("%s: class anchor not found (instant send compatibility)" % chat)
        else:
            write(chat, text.replace(anchor,
                "/* KAMIGRAM_INSTANT_SEND_R80: native scheduleDate path already preserved. */\n" + anchor,
                1))
            DONE.append("native ChatActivity scheduleDate compatibility marker")


def patch_proxy_route_stability():
    rel = "tgnet/ConnectionsManager.java"
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_R80",
        """        /* KAMIGRAM_PROXY_SEND_GUARD_R78: ordinary outgoing messages
           keep the selected proxy route stable until Telegram has
           completed the request. This prevents the smart fallback
           watcher from switching routes in the middle of a send. */
        if (org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object)) {
            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestStarted();
        }
        /* KAMIGRAM_NET_FILTER: нулевой трафик - ненужный запрос не уходит в сеть вовсе */
""",
        """        /* KAMIGRAM_INSTANT_PROXY_ROUTE_R80: classify first, then pin only
           an actual native send. Blocked/local requests never leave a stale
           route lease behind, while a live request keeps its route stable. */
        final boolean kamigramMessageRequest = org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object);
        /* KAMIGRAM_NET_FILTER: нулевой трафик - ненужный запрос не уходит в сеть вовсе */
""",
        "proxy: track the real request token after local filters",
    )
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_START_R80",
        """        if (org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete)) {
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
""",
        """        if (org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete)) {
            return;
        }
        if (kamigramMessageRequest) {
            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestStarted(requestToken); /* KAMIGRAM_INSTANT_PROXY_ROUTE_START_R80 */
        }
        if (BuildVars.LOGS_ENABLED) {
""",
        "proxy: start route stability only for a live native request",
    )
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_FINISH_R80",
        """                        if (org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object)) {
                            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished();
                        }
""",
        """                        if (kamigramMessageRequest) {
                            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished(requestToken); /* KAMIGRAM_INSTANT_PROXY_ROUTE_FINISH_R80 */
                        }
""",
        "proxy: release route stability on the matching response token",
    )
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_RETRY_R80",
        """                        cleanup(true);
                        sendRequest(object, onComplete, onCompleteTimestamp, onQuickAck, onWriteToSocket, flags, datacenterId, connectionType, immediate);
                        return;
""",
        """                        cleanup(true);
                        if (kamigramMessageRequest) {
                            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished(requestToken);
                        }
                        sendRequest(object, onComplete, onCompleteTimestamp, onQuickAck, onWriteToSocket, flags, datacenterId, connectionType, immediate); /* KAMIGRAM_INSTANT_PROXY_ROUTE_RETRY_R80 */
                        return;
""",
        "proxy: retry a request with a fresh native route token",
    )
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_CALLBACK_EXCEPTION_R80",
        """                } catch (Exception e) {
                    FileLog.e(e);
                }
            }, onQuickAck, onWriteToSocket);
""",
        """                } catch (Exception e) {
                    if (kamigramMessageRequest) {
                        org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished(requestToken);
                    }
                    FileLog.e(e);
                }
            }, onQuickAck, onWriteToSocket); /* KAMIGRAM_INSTANT_PROXY_ROUTE_CALLBACK_EXCEPTION_R80 */
""",
        "proxy: release route stability when response parsing fails",
    )
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_EXCEPTION_R80",
        """            native_sendRequest(currentAccount, buffer.address, flags, datacenterId, connectionType, immediate, requestToken);
        } catch (Exception e) {
            FileLog.e(e);
        }
""",
        """            native_sendRequest(currentAccount, buffer.address, flags, datacenterId, connectionType, immediate, requestToken);
        } catch (Exception e) {
            if (kamigramMessageRequest) {
                org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished(requestToken);
            }
            FileLog.e(e); /* KAMIGRAM_INSTANT_PROXY_ROUTE_EXCEPTION_R80 */
        }
""",
        "proxy: release route stability if native request setup throws",
    )
    replace_once(
        rel,
        "KAMIGRAM_INSTANT_PROXY_ROUTE_CANCEL_R80",
        """        Utilities.stageQueue.postRunnable(() -> {
            if (onCancelled != null) {
""",
        """        Utilities.stageQueue.postRunnable(() -> {
            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished(token); /* KAMIGRAM_INSTANT_PROXY_ROUTE_CANCEL_R80 */
            if (onCancelled != null) {
""",
        "proxy: cancellation releases only the matching request token",
    )


def patch_forward_restrictions():
    chat = "ui/ChatActivity.java"
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_TEXT_SELECTION",
        """            final boolean noforwards = !org.telegram.messenger.kamigram.KamiGramConfig.noRestrictions() && (
                chatActivity != null && chatActivity.isPeerNoForwards() ||
                selectedView != null && selectedView.getMessageObject() != null && selectedView.getMessageObject().messageOwner != null && selectedView.getMessageObject().messageOwner.noforwards
            );
""",
        """            final boolean noforwards = false; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_TEXT_SELECTION */
""",
        "copy/quote: text-selection helper ignores source protection",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_TEXT_COPY",
        """            return org.telegram.messenger.kamigram.KamiGramConfig.noRestrictions() || chatActivity == null || !(
                chatActivity.getDialogId() < 0 && chatActivity.getMessagesController().isPeerNoForwards(chatActivity.getDialogId()) ||
                selectedView != null && selectedView.getMessageObject() != null && (selectedView.getMessageObject().messageOwner != null && selectedView.getMessageObject().messageOwner.noforwards)
            );
""",
        """            return true; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_TEXT_COPY */
""",
        "copy/quote: inline text copy remains available",
    )
    replace_once(
        chat,
        "KAMIGRAM_SCREENSHOT_R80",
        """            !org.telegram.messenger.kamigram.KamiGramConfig.noRestrictions() && (currentEncryptedChat != null || isPeerNoForwards())
""",
        """            false /* KAMIGRAM_SCREENSHOT_R80 */
""",
        "screenshots: no content-protection flag on the chat window",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_ACTION_COPY",
        "        actionMode.setItemVisibility(copy, !isPeerNoForwards() && selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);\n",
        "        actionMode.setItemVisibility(copy, true /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_ACTION_COPY */ && selectedMessagesCanCopyIds[0].size() + selectedMessagesCanCopyIds[1].size() != 0 ? View.VISIBLE : View.GONE);\n",
        "copying: action-mode Copy button ignores source protection",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_TEXT_HINT",
        """        if (getParentActivity() == null || (!org.telegram.messenger.kamigram.KamiGramConfig.noRestrictions() && (getMessagesController().isPeerNoForwards(messageObject.getDialogId()) || (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards)))) {
""",
        """        if (getParentActivity() == null) { /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_TEXT_HINT */
""",
        "copying: text-selection hint is not hidden for protected messages",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_NO_FORWARDS_BANNER",
        "        boolean showNoForwards = (isPeerNoForwards() || message.messageOwner.noforwards && currentUser != null && currentUser.bot) && message.messageOwner.action == null && message.isSent() && !message.isEditing() && chatMode != MODE_SCHEDULED && chatMode != MODE_SAVED && getDialogId() != UserObject.VERIFY;\n",
        "        boolean showNoForwards = false; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_NO_FORWARDS_BANNER */\n",
        "menus: do not show a stale protected-content warning",
    )
    replace_count(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_LINK_COPY",
        "                boolean noforwards = isPeerNoForwards() || (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards);\n",
        "                boolean noforwards = false; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_LINK_COPY */\n",
        1,
        "copying: link menu exposes Copy from protected messages",
    )
    replace_count(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_URL_COPY",
        "        boolean noforwards = isPeerNoForwards() || (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards);\n",
        "        boolean noforwards = false; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_URL_COPY */\n",
        1,
        "copying: URL spans remain copyable in protected messages",
    )
    # The action-mode counters treated a no-forwards source as unforwardable,
    # even though the r70 no-restrictions path already makes the message copyable.
    replace_count(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_SELECTION",
        "                    boolean noforwards = isPeerNoForwards();\n",
        "                    boolean noforwards = false; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_SELECTION */\n",
        2,
        "forwarding: action-mode selection ignores source no-forwards flag",
    )
    replace_count(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_COPY",
        " && !(messageObject.messageOwner != null && messageObject.messageOwner.noforwards)",
        " && true /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_COPY */",
        2,
        "saving/copying: selected protected messages remain eligible",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_ACTION_MODE",
        "                boolean noforwards = isPeerNoForwards() || hasSelectedNoforwardsMessage();\n",
        "                boolean noforwards = false; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_ACTION_MODE */\n",
        "forwarding: action-mode button ignores source restriction",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_OPEN",
        "        if (isPeerNoForwards() || hasSelectedNoforwardsMessage()) {\n",
        "        if (false /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_OPEN */) {\n",
        "forwarding: remove restricted-source early exit",
    )
    replace_count(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_MENU",
        "        boolean noforwards = isPeerNoForwards() || message.messageOwner.noforwards || getDialogId() == UserObject.VERIFY;\n",
        "        boolean noforwards = getDialogId() == UserObject.VERIFY; /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_MENU */\n",
        2,
        "forwarding/saving: message menus expose protected-media actions",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_CAN_FORWARD",
        "                    && !noforwards && selectedObject.type != MessageObject.TYPE_SHARING_OFFER\n",
        "                    && true /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_CAN_FORWARD */ && selectedObject.type != MessageObject.TYPE_SHARING_OFFER\n",
        "forwarding: single-message Forward action ignores no-forwards flag",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_SHARE",
        "                                if (msg.messageOwner.noforwards) continue;\n",
        "                                if (false /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_SHARE */) continue;\n",
        "saving/sharing: protected voice and round media remain shareable",
    )
    replace_once(
        chat,
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_RICH",
        """                && !isPeerNoForwards()
                && !messageObject.messageOwner.noforwards
""",
        """                && true /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_RICH */
""",
        "saving: rich protected documents remain saveable",
    )

    # The original P22 source patch only bypassed noforwards while the setting
    # was enabled. r80 makes this particular forwarding restriction
    # unconditional. r115: секретные сообщения и непросмотренные одноразовые
    # не пересылаются НИКОГДА (как в оригинальном Telegram).
    replace_once(
        "messenger/MessageObject.java",
        "KAMIGRAM_FORWARD_RESTRICTIONS_R80_MESSAGE_OBJECT",
        """        return !(messageOwner instanceof TLRPC.TL_message_secret) && !needDrawBluredPreview() && !isLiveLocation() && type != MessageObject.TYPE_PHONE_CALL && !isSponsored() && (org.telegram.messenger.kamigram.KamiGramConfig.noRestrictions() || !messageOwner.noforwards);
""",
        """        return !(messageOwner instanceof TLRPC.TL_message_secret) && !needDrawBluredPreview() && !isLiveLocation() && type != MessageObject.TYPE_PHONE_CALL && !isSponsored(); /* KAMIGRAM_FORWARD_RESTRICTIONS_R80_MESSAGE_OBJECT */
""",
        "forwarding: MessageObject.canForwardMessage ignores protected-source bit",
    )
    replace_once(
        "messenger/MessageObject.java",
        "KAMIGRAM_COPY_R80_SHARE_BUTTON",
        "        } else if (messageOwner.noforwards) {\n            return false;\n",
        "        } else if (false /* KAMIGRAM_COPY_R80_SHARE_BUTTON */) {\n            return false;\n",
        "sharing: MessageObject exposes the native Share action for protected media",
    )
    replace_once(
        "messenger/MessageObject.java",
        "KAMIGRAM_COPY_R80_LAYOUT",
        """        boolean noforwards = messageOwner != null && messageOwner.noforwards;
        if (!noforwards) {
            final long dialogId = getDialogId();
            noforwards = MessagesController.getInstance(currentAccount).isPeerNoForwards(dialogId);
        }
""",
        """        boolean noforwards = false; /* KAMIGRAM_COPY_R80_LAYOUT */
""",
        "copying: message text layout keeps code/quote copy buttons visible",
    )
    replace_once(
        "messenger/MessageObject.java",
        "KAMIGRAM_COPY_R80_BLOCK_LAYOUT",
        """            boolean noforwards = messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.noforwards;
            if (messageObject != null && !noforwards) {
                final long dialogId = messageObject.getDialogId();
                noforwards = MessagesController.getInstance(messageObject.currentAccount).isPeerNoForwards(dialogId);
            }
""",
        """            boolean noforwards = false; /* KAMIGRAM_COPY_R80_BLOCK_LAYOUT */
""",
        "copying: text block layout keeps copy buttons visible",
    )


def patch_protected_forward_copy():
    # r115: патч ставится на оригинальный текст (r77 больше не правит этот файл).
    # Защищённый контент (noforwards) пересылается повторной загрузкой копии;
    # одноразовые/сгорающие медиа исключены — они не пересылаются, как в
    # оригинальном Telegram (needDrawBluredPreview обрабатывается штатно).
    rel = "messenger/SendMessagesHelper.java"
    replace_once(
        rel,
        "KAMIGRAM_PROTECTED_FORWARD_COPY_R80",
        """            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                if (msgObj.getId() <= 0 || msgObj.needDrawBluredPreview()) {
""",
        """            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                /* KAMIGRAM_PROTECTED_FORWARD_COPY_R80: защищённый контент сервер не
                   даёт переслать — копия загружается заново как новое сообщение. */
                final boolean kamigramProtectedSource = msgObj != null && msgObj.messageOwner != null
                    && (msgObj.messageOwner.noforwards
                        || getMessagesController().isPeerNoForwards(msgObj.getDialogId()));
                if (msgObj.getId() <= 0 || msgObj.needDrawBluredPreview()) {
""",
        "forwarding: detect protected source for copy/upload path",
    )
    replace_once(
        rel,
        "KAMIGRAM_PROTECTED_FORWARD_COPY_R80_BRANCH",
        """                }

                final TLRPC.Message newMsg = new TLRPC.TL_message();
                if (!forwardFromMyName) {
""",
        """                }

                /* KAMIGRAM_PROTECTED_FORWARD_COPY_R80_BRANCH: копия защищённого
                   контента уходит новым аплоадом — без серверного запрета пересылки. */
                if (kamigramProtectedSource) {
                    processForwardFromMyName(msgObj, peer, payStars, monoForumPeerId, suggestionParams);
                    continue;
                }

                final TLRPC.Message newMsg = new TLRPC.TL_message();
                if (!forwardFromMyName) {
""",
        "forwarding: protected source is copied/uploaded as a new message",
    )


def validate_sources():
    checks = [
        ("messenger/kamigram/KamiGramGhost.java", "KAMIGRAM_INSTANT_SEND_R80", "instant send compatibility"),
        ("messenger/kamigram/KamiGramProxyPower.java", "KAMIGRAM_INSTANT_SEND_R80", "proxy no artificial lease"),
        ("messenger/kamigram/KamiGramDeleted.java", "KAMIGRAM_NATIVE_DELETE_R80", "native delete path"),
    ]
    for rel, marker, what in checks:
        try:
            if marker not in read(rel):
                MISS.append("%s: marker missing (%s)" % (rel, what))
        except OSError as exc:
            MISS.append("%s: %s (%s)" % (rel, exc, what))


def main():
    patch_instant_send()
    patch_proxy_route_stability()
    patch_forward_restrictions()
    patch_protected_forward_copy()
    validate_sources()

    print("r80: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r80: failed — %d" % len(MISS))
        for item in MISS:
            print("  ! %s" % item)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
