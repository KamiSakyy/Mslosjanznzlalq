#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KamiGram r82 recovery pass.

The earlier r68/r80 stages added compatibility hooks around Telegram's send
entry point. r82 deliberately removes those live hooks instead of replacing
them with another scheduler. Telegram's own SendMessageParams.scheduleDate is
left untouched, so ordinary text and every native media/forward path use the
same code as upstream.

This pass also makes the local economy/Ghost gates conditional: ordinary
message requests and Telegram push/background synchronisation keep the native
path. The sticker, premium-emoji, GIF and other non-message filters remain
active, including the FileLoader document policy. Push registration and update
requests are never swallowed by the module.
"""

import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
ROOT = os.path.join(TG, "TMessagesProj/src/main/java")
DONE = []
MISS = []


def path(rel):
    return os.path.join(ROOT, "org/telegram", *rel.split("/"))


def read(rel):
    with io.open(path(rel), encoding="utf-8") as stream:
        return stream.read()


def write(rel, text):
    with io.open(path(rel), "w", encoding="utf-8") as stream:
        stream.write(text)


def once(rel, marker, old, new, what):
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    if old not in text:
        MISS.append("%s: anchor not found (%s)" % (rel, what))
        return False
    write(rel, text.replace(old, new, 1))
    DONE.append(what)
    return True


def patch_native_send():
    rel = "messenger/SendMessagesHelper.java"
    # r81 left an onRealSend side effect at the front of the native method.
    # Remove the complete block, including the compatibility comment. Do not
    # touch the native scheduleDate variable or the r77 protected-media upload
    # path, which are separate accepted features.
    once(
        rel,
        "KAMIGRAM_NATIVE_SEND_R82",
        """        /* KAMIGRAM_INSTANT_SEND_R81: normal text, photo, video, audio,
           voice, round video, and document sends keep Telegram's native
           scheduleDate. Ghost never moves them to Scheduled and never adds a
           timer or callback wait. */
        if (sendMessageParams != null && sendMessageParams.scheduleDate == 0) {
            org.telegram.messenger.kamigram.KamiGramGhost.onRealSend(currentAccount);
        }
""",
        """        /* KAMIGRAM_NATIVE_SEND_R82: Telegram's native send entry.
           No Ghost callback, timer, proxy guard, or schedule rewrite is
           performed here; the caller's scheduleDate is consumed unchanged. */
""",
        "sending: remove the r81 Ghost send callback and keep native scheduleDate",
    )


def patch_request_gates():
    rel = "tgnet/ConnectionsManager.java"
    # Keep the existing filter for optional economy features, but never put
    # Telegram's FCM registration/update synchronisation behind it. The push
    # classifier is deliberately separate from ordinary message sends.
    once(
        rel,
        "KAMIGRAM_PUSH_NATIVE_BYPASS_R83",
        "        final boolean kamigramMessageRequest = org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object);\n",
        "        final boolean kamigramMessageRequest = org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object);\n"
        "        final boolean kamigramPushRequest = org.telegram.messenger.kamigram.KamiGramNetFilter.isPushCriticalRequest(object); /* KAMIGRAM_PUSH_NATIVE_BYPASS_R83 */\n",
        "push: classify FCM registration and background update requests before local filters",
    )
    once(
        rel,
        "KAMIGRAM_NATIVE_MESSAGE_FILTER_BYPASS_R82",
        """        if (org.telegram.messenger.kamigram.KamiGramNetFilter.blockRequest(object)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("KamiGram: запрос не отправлен (экономия трафика) " + object);
            }
            return;
        }
""",
        """        if (!kamigramMessageRequest && !kamigramPushRequest && org.telegram.messenger.kamigram.KamiGramNetFilter.blockRequest(object)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("KamiGram: запрос не отправлен (экономия трафика) " + object);
            }
            return;
        } /* KAMIGRAM_NATIVE_MESSAGE_FILTER_BYPASS_R82: only ordinary message requests bypass this gate */
        """,
        "sending: preserve local filters while bypassing them only for ordinary message requests",
    )
    once(
        rel,
        "KAMIGRAM_NATIVE_GHOST_BYPASS_R82",
        """        if (org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete)) {
            return;
        }
""",
        """        if (!kamigramMessageRequest && !kamigramPushRequest && org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete)) {
            return;
        } /* KAMIGRAM_NATIVE_GHOST_BYPASS_R82: message requests use Telegram's native path */
        """,
        "sending: keep Ghost read/typing suppression but never intercept native message requests",
    )


def patch_remove_gate_and_trigger_cleanup():
    rel = "ui/LaunchActivity.java"
    once(
        rel,
        "KAMIGRAM_CHANNEL_GATE_R82_DISABLED",
        """        /* KAMIGRAM_CHANNEL_GUARD (r70): после входа в аккаунт проверяем
           подписку на канал — без подписки пользоваться нельзя. */
        try {
            org.telegram.messenger.kamigram.KamiGramChannelGuard.check(this);
        } catch (Throwable ignore) {
        }
""",
        """        /* KAMIGRAM_CHANNEL_GUARD / KAMIGRAM_CHANNEL_GATE_R82_DISABLED:
           the channel is a settings link only; it never blocks the application. */
        try {
            org.telegram.messenger.kamigram.KamiGramAutoArchive.checkNow(currentAccount);
        } catch (Throwable ignore) {
        }
""",
        "subscription gate: remove mandatory AsuMeo check and trigger auto-cleanup on resume",
    )



def validate():
    checks = [
        ("messenger/SendMessagesHelper.java", "KAMIGRAM_NATIVE_SEND_R82", "native send entry"),
        ("tgnet/ConnectionsManager.java", "KAMIGRAM_NATIVE_MESSAGE_FILTER_BYPASS_R82", "message-only economy bypass"),
        ("tgnet/ConnectionsManager.java", "KAMIGRAM_NATIVE_GHOST_BYPASS_R82", "message-only Ghost bypass"),
        ("tgnet/ConnectionsManager.java", "KAMIGRAM_PUSH_NATIVE_BYPASS_R83", "push/background native bypass"),
        ("ui/LaunchActivity.java", "KAMIGRAM_CHANNEL_GATE_R82_DISABLED", "subscription gate removed"),
        ("messenger/kamigram/KamiGramNetFilter.java", "KAMIGRAM_PUSH_SAFE_R83", "push-safe request classifier"),
    ]
    for rel, marker, what in checks:
        try:
            if marker not in read(rel):
                MISS.append("%s: marker missing (%s)" % (rel, what))
        except OSError as exc:
            MISS.append("%s: %s (%s)" % (rel, exc, what))

    try:
        helper = read("messenger/SendMessagesHelper.java")
        if "KamiGramGhost.autoScheduleDate" in helper or "KamiGramGhost.onRealSend" in helper:
            MISS.append("messenger/SendMessagesHelper.java: live Ghost scheduler/send callback remains")
        if "sendMessageParams.scheduleDate =" in helper:
            MISS.append("messenger/SendMessagesHelper.java: scheduleDate is rewritten before native send")
        if "KAMIGRAM_AUTO_SCHEDULE" in helper:
            MISS.append("messenger/SendMessagesHelper.java: legacy Scheduled-send marker remains")
    except OSError as exc:
        MISS.append("messenger/SendMessagesHelper.java: %s (native send validation)" % exc)

    try:
        chat = read("ui/ChatActivity.java")
        if "KamiGramGhost.autoScheduleDate" in chat or "KamiGramGhost.consumeAutoScheduled" in chat:
            MISS.append("ui/ChatActivity.java: live Ghost scheduler hook remains")
        if "KAMIGRAM_AUTO_SCHEDULE" in chat:
            MISS.append("ui/ChatActivity.java: legacy Scheduled-send marker remains")
    except OSError as exc:
        MISS.append("ui/ChatActivity.java: %s (native forward validation)" % exc)

    try:
        connections = read("tgnet/ConnectionsManager.java")
        if "if (org.telegram.messenger.kamigram.KamiGramNetFilter.blockRequest(object))" in connections:
            MISS.append("tgnet/ConnectionsManager.java: economy filter still unconditionally blocks requests")
        if "if (org.telegram.messenger.kamigram.KamiGramGhost.interceptRequest(object, onComplete))" in connections:
            MISS.append("tgnet/ConnectionsManager.java: Ghost hook still unconditionally intercepts requests")
        if "KamiGramNetFilter.blockRequest(object)" not in connections:
            MISS.append("tgnet/ConnectionsManager.java: non-message filter was removed instead of scoped")
        if "!kamigramMessageRequest && !kamigramPushRequest" not in connections:
            MISS.append("tgnet/ConnectionsManager.java: push requests are not exempt from local filters")
    except OSError as exc:
        MISS.append("tgnet/ConnectionsManager.java: %s (request validation)" % exc)

    try:
        loader = read("messenger/FileLoader.java")
        if "KamiGramNetFilter.blockDownload(document, parentObject)" not in loader:
            MISS.append("messenger/FileLoader.java: sticker/GIF document filter disappeared")
    except OSError as exc:
        MISS.append("messenger/FileLoader.java: %s (media filter validation)" % exc)

    try:
        launch = read("ui/LaunchActivity.java")
        if "KamiGramChannelGuard.check" in launch:
            MISS.append("ui/LaunchActivity.java: mandatory AsuMeo gate call remains")
    except OSError as exc:
        MISS.append("ui/LaunchActivity.java: %s (gate validation)" % exc)


def main():
    patch_native_send()
    patch_request_gates()
    patch_remove_gate_and_trigger_cleanup()
    validate()
    print("r82: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r82: failed — %d" % len(MISS))
        for item in MISS:
            print("  ! %s" % item)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
