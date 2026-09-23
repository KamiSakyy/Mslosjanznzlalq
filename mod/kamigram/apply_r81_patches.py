#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KamiGram r81: remove the last real send/download stalls.

This pass is intentionally small and marker-guarded. The Java helpers are
copied from this repository by apply-mod.sh; this file only touches Telegram
entry points whose lifetime and queue scheduling are owned by upstream:

* focus rechecks are posted with zero delay and never pause an already active
  background download;
* the old r68 Ghost/Scheduled compatibility wrapper is removed from the live
  SendMessagesHelper path;
* proxy send leases use an account + native request token, and every native
  completion/cancel path releases the matching key;
* archive/theme/channel behaviour lives in the copied KamiGram classes.
"""

import io
import os
import re
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


def regex_once(rel, marker, pattern, replacement, what):
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    result, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE | re.DOTALL)
    if count != 1:
        MISS.append("%s: pattern not found (%s)" % (rel, what))
        return False
    write(rel, result)
    DONE.append(what)
    return True


def patch_downloads():
    queue = "messenger/FileLoaderPriorityQueue.java"
    once(
        queue,
        "KAMIGRAM_QUEUE_IMMEDIATE_R81",
        "        workerQueue.postRunnable(checkOperationsRunnable, 20);",
        "        workerQueue.postRunnable(checkOperationsRunnable, org.telegram.messenger.kamigram.KamiGramSpeed.queueCheckDelayMs()); /* KAMIGRAM_QUEUE_IMMEDIATE_R81 */",
        "downloads: remove the 20 ms focus debounce",
    )
    once(
        queue,
        "KAMIGRAM_NO_ACTIVE_PAUSE_R81",
        """                if (operation.wasStarted()) {
                    operation.pause();
                }
""",
        """                if (operation.wasStarted() && kamigramFocus == null) {
                    operation.pause();
                } /* KAMIGRAM_NO_ACTIVE_PAUSE_R81: focus never pauses active background work */
""",
        "downloads: focused queue keeps active background operations running",
    )

    loader = "messenger/FileLoader.java"
    once(
        loader,
        "KAMIGRAM_QUEUE_RECHECK_IMMEDIATE_R81",
        """                    if (queue != null) {
                        queue.checkLoadingOperations();
                    }
""",
        """                    if (queue != null) {
                        queue.checkLoadingOperations(true); /* KAMIGRAM_QUEUE_RECHECK_IMMEDIATE_R81 */
                    }
""",
        "downloads: focus/unfocus rechecks both queues immediately",
    )


def patch_instant_send():
    helper = "messenger/SendMessagesHelper.java"
    # Remove the live r68 rewrite, not merely its implementation detail. The
    # caller's scheduleDate is now the only source of scheduling semantics.
    regex_once(
        helper,
        "KAMIGRAM_INSTANT_SEND_R81",
        r"        /\* KAMIGRAM_GHOST_PULSE:.*?"
        r"        if \(sendMessageParams != null && sendMessageParams\.scheduleDate == 0\) \{\n"
        r"            org\.telegram\.messenger\.kamigram\.KamiGramGhost\.onRealSend\(currentAccount\);\n"
        r"        \}\n",
        """        /* KAMIGRAM_INSTANT_SEND_R81: normal text, photo, video, audio,
           voice, round video, and document sends keep Telegram's native
           scheduleDate. Ghost never moves them to Scheduled and never adds a
           timer or callback wait. */
        if (sendMessageParams != null && sendMessageParams.scheduleDate == 0) {
            org.telegram.messenger.kamigram.KamiGramGhost.onRealSend(currentAccount);
        }
""",
        "sending: remove the live Ghost/Scheduled rewrite",
    )

    chat = "ui/ChatActivity.java"
    once(
        chat,
        "KAMIGRAM_INSTANT_BROADCAST_R81",
        "/* KAMIGRAM_AUTO_SCHEDULE_SEND: при призраке — отложкой */",
        "/* KAMIGRAM_INSTANT_BROADCAST_R81: forwarding keeps the caller's native scheduleDate */",
        "forwarding: remove the stale Ghost/Scheduled label from the broadcast path",
    )
    once(
        chat,
        "KAMIGRAM_INSTANT_FORWARD_CALL_R81",
        "scheduleDate /* KAMIGRAM_INSTANT_FORWARD_R80 */",
        "scheduleDate /* KAMIGRAM_INSTANT_FORWARD_CALL_R81 */",
        "forwarding: native scheduleDate is passed directly",
    )


def patch_proxy_parse_failure():
    rel = "tgnet/ConnectionsManager.java"
    once(
        rel,
        "KAMIGRAM_PROXY_ROUTE_PARSE_FAIL_R81",
        """                            FileLog.fatal(e2);
                            return;
""",
        """                            FileLog.fatal(e2);
                            if (kamigramMessageRequest) {
                                org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished(currentAccount, requestToken); /* KAMIGRAM_PROXY_ROUTE_PARSE_FAIL_R81 */
                            }
                            return;
""",
        "proxy: release route stability when response parsing returns early",
    )


def patch_proxy_tokens():
    rel = "tgnet/ConnectionsManager.java"
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (proxy request account)" % (rel, exc))
        return
    if "KAMIGRAM_REQUEST_ACCOUNT_R81" not in text:
        old = "final boolean kamigramMessageRequest = org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object);"
        if old not in text:
            MISS.append("%s: message classifier anchor not found (proxy request account)" % rel)
        else:
            text = text.replace(
                old,
                old + "\n        /* KAMIGRAM_REQUEST_ACCOUNT_R81: token ownership includes currentAccount. */",
                1,
            )
            text = text.replace(
                "noteMessageRequestStarted(requestToken)",
                "noteMessageRequestStarted(currentAccount, requestToken)",
            )
            text = text.replace(
                "noteMessageRequestFinished(requestToken)",
                "noteMessageRequestFinished(currentAccount, requestToken)",
            )
            text = text.replace(
                "noteMessageRequestFinished(token)",
                "noteMessageRequestFinished(currentAccount, token)",
            )
            write(rel, text)
            DONE.append("proxy: account-safe native request-token lease")


def validate():
    checks = [
        ("messenger/FileLoaderPriorityQueue.java", "KAMIGRAM_QUEUE_IMMEDIATE_R81", "immediate queue focus"),
        ("messenger/FileLoaderPriorityQueue.java", "KAMIGRAM_NO_ACTIVE_PAUSE_R81", "no active focus pause"),
        ("messenger/FileLoader.java", "KAMIGRAM_QUEUE_RECHECK_IMMEDIATE_R81", "immediate queue recheck"),
        ("messenger/SendMessagesHelper.java", "KAMIGRAM_INSTANT_SEND_R81", "native immediate sending"),
        ("ui/ChatActivity.java", "KAMIGRAM_INSTANT_BROADCAST_R81", "native forward scheduling"),
        ("tgnet/ConnectionsManager.java", "KAMIGRAM_REQUEST_ACCOUNT_R81", "account-safe proxy lease"),
        ("tgnet/ConnectionsManager.java", "KAMIGRAM_PROXY_ROUTE_PARSE_FAIL_R81", "parse-failure proxy release"),
    ]
    for rel, marker, what in checks:
        try:
            if marker not in read(rel):
                MISS.append("%s: marker missing (%s)" % (rel, what))
        except OSError as exc:
            MISS.append("%s: %s (%s)" % (rel, exc, what))

    try:
        helper = read("messenger/SendMessagesHelper.java")
        if "KamiGramGhost.autoScheduleDate" in helper:
            MISS.append("messenger/SendMessagesHelper.java: live autoScheduleDate call remains")
    except OSError:
        pass


def main():
    patch_downloads()
    patch_instant_send()
    patch_proxy_parse_failure()
    patch_proxy_tokens()
    validate()
    print("r81: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r81: failed — %d" % len(MISS))
        for item in MISS:
            print("  ! %s" % item)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
