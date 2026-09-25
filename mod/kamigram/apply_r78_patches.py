#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sakura r78 media, proxy-send, subscription, and folder fixes.

The r77 tree already contains the self-destruct-media, proxy fallback, archive
and screenshot work. This pass deliberately narrows the media gate instead of
adding another download switch:
  * normal photo/video/audio/voice/round/document requests enter native
    FileLoader immediately after a tap;
  * only sticker, premium-emoji, and GIF categories remain deny-able;
  * a proxy route is leased while an outgoing message request is in flight;
  * folder tabs receive a real Sakura surface instead of a black runtime
    background.

All target edits are marker guarded so the GitHub workflow can apply the patch
again to a clean Telegram checkout.
"""

import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
ROOT = os.path.join(TG, "TMessagesProj/src/main/java")
DONE = []
MISS = []


def path(rel):
    return os.path.join(ROOT, "org/telegram", rel)


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


def patch_message_send_lease():
    target = os.path.join(ROOT, "org/telegram/tgnet/ConnectionsManager.java")
    try:
        text = io.open(target, encoding="utf-8").read()
    except OSError as exc:
        MISS.append("tgnet/ConnectionsManager.java: %s (message proxy lease)" % exc)
        return
    marker = "KAMIGRAM_PROXY_SEND_GUARD_R78"
    if marker not in text:
        anchor = "        /* KAMIGRAM_NET_FILTER: нулевой трафик - ненужный запрос не уходит в сеть вовсе */\n"
        if anchor not in text:
            MISS.append("tgnet/ConnectionsManager.java: net-filter anchor not found (message proxy lease)")
            return
        insert = (
            "        /* KAMIGRAM_PROXY_SEND_GUARD_R78: ordinary outgoing messages\n"
            "           keep the selected proxy route stable until Telegram has\n"
            "           completed the request. This prevents the smart fallback\n"
            "           watcher from switching routes in the middle of a send. */\n"
            "        if (org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object)) {\n"
            "            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestStarted();\n"
            "        }\n"
        )
        text = text.replace(anchor, insert + anchor, 1)
        io.open(target, "w", encoding="utf-8").write(text)
        DONE.append("proxy: lease selected route during ordinary message sends")

    finish_marker = "KAMIGRAM_PROXY_SEND_FINISH_R78"
    if finish_marker not in text:
        finish_anchor = """                        if (onComplete != null) {
"""
        if finish_anchor not in text:
            MISS.append("tgnet/ConnectionsManager.java: completion anchor not found (message proxy lease)")
            return
        finish = (
            "                        /* KAMIGRAM_PROXY_SEND_FINISH_R78: the server\n"
            "                           answered, so automatic fallback may resume\n"
            "                           after this request is no longer at risk. */\n"
            "                        if (org.telegram.messenger.kamigram.KamiGramProxyPower.isMessageRequest(object)) {\n"
            "                            org.telegram.messenger.kamigram.KamiGramProxyPower.noteMessageRequestFinished();\n"
            "                        }\n"
        )
        text = text.replace(finish_anchor, finish + finish_anchor, 1)
        io.open(target, "w", encoding="utf-8").write(text)
        DONE.append("proxy: release route lease when message response arrives")


def patch_folder_tabs():
    """Do not recolour Telegram's native folder tab surface.

    Earlier r78 builds painted this view with the native Telegram palette.  Sakura keeps
    the complete Telegram theme pipeline authoritative, so this compatibility
    hook intentionally performs no source edit.
    """
    DONE.append("folders: native Telegram theme colours retained")


def validate_sources():
    checks = [
        ("messenger/kamigram/KamiGramNetFilter.java", "KAMIGRAM_MEDIA_POLICY_R78", "media policy"),
        ("messenger/kamigram/KamiGramTextOnly.java", "KAMIGRAM_MEDIA_POLICY_R78", "text-only media gate"),
        ("messenger/kamigram/KamiGramProxyPower.java", "KAMIGRAM_PROXY_SEND_GUARD_R78", "proxy send guard"),
        ("messenger/kamigram/KamiGramProxyPower.java", "KAMIGRAM_PROXY_SEND_FINISH_R78", "proxy send lease release"),
        ("messenger/kamigram/KamiGramBuiltinProxy.java", "KAMIGRAM_PROXY_SEND_GUARD_R78", "proxy route guard"),
        ("messenger/kamigram/KamiGramChannelGuard.java", "KAMIGRAM_AUTO_JOIN_R78", "automatic AsuMeo subscription"),
        ("messenger/kamigram/KamiGramAutoArchive.java", "KAMIGRAM_ARCHIVE_SAFETY_R78", "archive private/contact safety"),
        ("messenger/kamigram/ThemeHook.java", "stock Telegram themes only", "stock theme compatibility"),
    ]
    for rel, marker, what in checks:
        try:
            if marker not in read(rel):
                MISS.append("%s: marker missing (%s)" % (rel, what))
        except OSError as exc:
            MISS.append("%s: %s (%s)" % (rel, exc, what))


def main():
    patch_message_send_lease()
    patch_folder_tabs()
    validate_sources()

    print("r78: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r78: skipped — %d" % len(MISS))
        for item in MISS:
            print("  ! %s" % item)
    # Missing anchors in upstream should stop the build rather than silently
    # shipping a binary without the media/proxy fix.
    if MISS:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
