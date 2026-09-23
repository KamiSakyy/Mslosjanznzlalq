#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KamiGram r77 fixes.

r76 is intentionally the base. This pass fixes the remaining behavioural gaps:
  * the proxy connection overlay is cleared only after a confirmed healthy state;
  * opening media never pauses unrelated download operations;
  * self-destruct media bypasses economy filters, has normal save/forward actions,
    and is shown without screenshot protection;
  * the native fallback rotator cannot replace a live custom proxy and selects the
    quickest live KamiProxy route;
  * archive cleanup is supplied by the r77 KamiGramAutoArchive source file.

Every edit is marker guarded so applying the installer twice is safe.
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


def regex_once(rel, marker, pattern, replacement, what, flags=re.S):
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    result, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if not count:
        MISS.append("%s: pattern not found (%s)" % (rel, what))
        return False
    write(rel, result)
    DONE.append(what)
    return True


def method_text(rel, start, end="\n    private "):
    text = read(rel)
    begin = text.find(start)
    if begin < 0:
        return text, -1, -1
    finish = text.find(end, begin + len(start))
    if finish < 0:
        finish = len(text)
    return text, begin, finish


def patch_overlay():
    once(
        "ui/ActionBar/ActionBar.java",
        "KAMIGRAM_PROXY_OVERLAY_R77",
        '''        /* KAMIGRAM_TITLE_LOCK_R70: на главном экране — как в оригинальном
           Telegram: при разрыве сети (title != null) показываем «Подключение…»,
           а когда соединение есть (title == null) — имя KamiGram на месте. */
        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {
            if (title == null) {
                /* соединение есть — держим имя KamiGram, ничего не подменяем */
                org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();
                return;
            }
            /* нет сети — показываем оригинальную подпись «Подключение…» */
        }''',
        '''        /* KAMIGRAM_PROXY_OVERLAY_R77: do not hide the connection overlay on
           an unconfirmed state. When Telegram supplies title == null, only a
           confirmed Connected/Updating state may clear an already shown
           «Настроить прокси >» overlay; then the native title code below runs
           and restores KamiGram. */
        if (parentFragment instanceof org.telegram.ui.DialogsActivity
            && title == null
            && !org.telegram.messenger.kamigram.KamiGramProxyPower.connectionHealthy()) {
            return;
        }''',
        "proxy overlay: clear stale overlay only after healthy connection",
    )

    once(
        "ui/DialogsActivity.java",
        "KAMIGRAM_PROXY_REFRESH_HEALTHY_R77",
        "        org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();\n",
        '''        /* KAMIGRAM_PROXY_REFRESH_HEALTHY_R77: ProxyStatus restores the base
           KamiGram title only after Telegram confirms a usable route. Calling it
           while offline overwrites the «Настроить прокси >» overlay. */
        if (connected) {
            org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();
        }
''',
        "proxy overlay: do not overwrite offline setup hint",
    )


def patch_download_queue():
    once(
        "messenger/FileLoaderPriorityQueue.java",
        "KAMIGRAM_NET_NO_PAUSE_R77",
        "            boolean kamigramPaused = kamigramFocus != null && !kamigramFocusHit;",
        '''            /* KAMIGRAM_NET_NO_PAUSE_R77: a focused photo/video/file gets
               priority, but opening it must not pause or cancel other downloads. */
            boolean kamigramPaused = false;''',
        "downloads: focus boosts the opened file without pausing other files",
    )
    once(
        "messenger/FileLoaderPriorityQueue.java",
        "KAMIGRAM_NET_NO_PRIORITY_PAUSE_R77",
        "            if (i > 0 && !pauseAllNextOperations) {",
        "            if (i > 0 && !pauseAllNextOperations && kamigramFocus == null) { /* KAMIGRAM_NET_NO_PRIORITY_PAUSE_R77 */",
        "downloads: focused operation does not trigger priority-wide pause",
    )

    # Text-only mode receives an ImageLocation rather than its MessageObject at
    # the first image-loading entry point. Preserve its economy behaviour for
    # ordinary images, but let a self-destruct message through.
    once(
        "messenger/FileLoader.java",
        "KAMIGRAM_EPHEMERAL_IMAGE_LOAD_R77",
        '''            if (org.telegram.messenger.kamigram.KamiGramTextOnly.blockImage(imageLocation)) {
                return;
            }''',
        '''            if (org.telegram.messenger.kamigram.KamiGramTextOnly.blockImage(imageLocation)
                && !(parentObject instanceof org.telegram.messenger.MessageObject
                    && ((org.telegram.messenger.MessageObject) parentObject).messageOwner != null
                    && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(
                        ((org.telegram.messenger.MessageObject) parentObject).messageOwner))) {
                return; /* KAMIGRAM_EPHEMERAL_IMAGE_LOAD_R77 */
            }''',
        "downloads: ephemeral photos bypass text-only image filter",
    )
    once(
        "messenger/FileLoader.java",
        "KAMIGRAM_EPHEMERAL_GIF_LOAD_R77",
        '''        if (org.telegram.messenger.kamigram.KamiGramConfig.noGifs() && document != null && MessageObject.isGifDocument(document)) {
            return;
        }''',
        '''        if (org.telegram.messenger.kamigram.KamiGramConfig.noGifs() && document != null && MessageObject.isGifDocument(document)
            && !(parentObject instanceof org.telegram.messenger.MessageObject
                && ((org.telegram.messenger.MessageObject) parentObject).messageOwner != null
                && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(
                    ((org.telegram.messenger.MessageObject) parentObject).messageOwner))) {
            return; /* KAMIGRAM_EPHEMERAL_GIF_LOAD_R77 */
        }''',
        "downloads: ephemeral GIF/media bypass no-GIF filter",
    )


def patch_archive_marker():
    # The source class is copied by apply-mod.sh. The marker is checked here too
    # so an old copied r68 class cannot silently survive a rerun.
    target = os.path.join(ROOT, "org/telegram/messenger/kamigram/KamiGramAutoArchive.java")
    if not os.path.isfile(target):
        MISS.append("messenger/kamigram/KamiGramAutoArchive.java: missing (archive cleanup)")
        return
    if "KAMIGRAM_ARCHIVE_CLEAN_R77" not in read("messenger/kamigram/KamiGramAutoArchive.java"):
        MISS.append("messenger/kamigram/KamiGramAutoArchive.java: r77 marker missing (archive cleanup)")


def patch_ephemeral_menu():
    rel = "ui/ChatActivity.java"
    try:
        text, begin, finish = method_text(rel, "    public void fillMessageMenu(")
    except OSError as exc:
        MISS.append("%s: %s (ephemeral menu)" % (rel, exc))
        return
    if begin < 0:
        MISS.append("%s: fillMessageMenu not found (ephemeral menu)" % rel)
        return
    segment = text[begin:finish]
    if "KAMIGRAM_EPHEMERAL_ACTIONS_R77" not in segment:
        old = '''        boolean noforwards = isPeerNoForwards() || message.messageOwner.noforwards || getDialogId() == UserObject.VERIFY;
        boolean noforwardsOrPaidMedia = noforwards || message.type == MessageObject.TYPE_PAID_MEDIA;'''
        new = '''        boolean noforwards = isPeerNoForwards() || message.messageOwner.noforwards || getDialogId() == UserObject.VERIFY;
        boolean noforwardsOrPaidMedia = noforwards || message.type == MessageObject.TYPE_PAID_MEDIA;
        /* KAMIGRAM_EPHEMERAL_ACTIONS_R77: a self-destructing photo, video,
           file, audio, voice, GIF, or document gets Auygram-like save/share/
           forward actions after it has been opened. */
        final boolean kamigramEphemeralMedia = message != null && message.messageOwner != null
            && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(message.messageOwner);
        final boolean kamigramMediaActions = kamigramEphemeralMedia || !noforwardsOrPaidMedia;'''
        if old not in segment:
            MISS.append("%s: menu flags anchor not found (ephemeral actions)" % rel)
            return
        segment = segment.replace(old, new, 1)

        replacements = [
            ("selectedObject.isMusic() && !noforwardsOrPaidMedia && !selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()",
             "selectedObject.isMusic() && kamigramMediaActions && (!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce() || kamigramEphemeralMedia)"),
            ("selectedObject.isDocument() && !noforwardsOrPaidMedia && !selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()",
             "selectedObject.isDocument() && kamigramMediaActions && (!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce() || kamigramEphemeralMedia)"),
            ("if (type == 3 && !noforwardsOrPaidMedia)",
             "if (type == 3 && kamigramMediaActions)"),
            ("if (!noforwardsOrPaidMedia && !selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce())",
             "if (kamigramMediaActions && (!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce() || kamigramEphemeralMedia))"),
            ("if (type == 6 && !noforwardsOrPaidMedia && !selectedObject.hasRevealedExtendedMedia())",
             "if (type == 6 && kamigramMediaActions && !selectedObject.hasRevealedExtendedMedia())"),
            ("if (!noforwardsOrPaidMedia && !selectedObject.hasRevealedExtendedMedia())",
             "if (kamigramMediaActions && !selectedObject.hasRevealedExtendedMedia())"),
            ("if (!selectedObject.needDrawBluredPreview())",
             "if (!selectedObject.needDrawBluredPreview() || kamigramEphemeralMedia)"),
            ("!selectedObject.needDrawBluredPreview() && !selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()",
             "(!selectedObject.needDrawBluredPreview() || kamigramEphemeralMedia) && (!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce() || kamigramEphemeralMedia)"),
            ("selectedObject.isMusic() && !selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()",
             "selectedObject.isMusic() && (!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce() || kamigramEphemeralMedia)"),
            ("selectedObject.getDocument() != null && !selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()",
             "selectedObject.getDocument() != null && (!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce() || kamigramEphemeralMedia)"),
            ("type == 4 && !noforwardsOrPaidMedia && !selectedObject.hasRevealedExtendedMedia() && !selectedObject.needDrawBluredPreview()",
             "type == 4 && kamigramMediaActions && !selectedObject.hasRevealedExtendedMedia() && (!selectedObject.needDrawBluredPreview() || kamigramEphemeralMedia)"),
            ("&& !noforwards && selectedObject.type != MessageObject.TYPE_SHARING_OFFER",
             "&& (!noforwards || kamigramEphemeralMedia) && selectedObject.type != MessageObject.TYPE_SHARING_OFFER"),
            ("(!selectedObject.needDrawBluredPreview() || selectedObject.hasExtendedMediaPreview()\n                        || (/* KAMIGRAM_FORWARD_EPHEMERAL (r70): как в AyuGram — */\n                            org.telegram.messenger.kamigram.KamiGramConfig.forwardEphemeral()\n                            && selectedObject.messageOwner != null\n                            && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(selectedObject.messageOwner)))",
             "(!selectedObject.needDrawBluredPreview() || selectedObject.hasExtendedMediaPreview() || kamigramEphemeralMedia)"),
        ]
        for old, new in replacements:
            segment = segment.replace(old, new)

        # A damaged r76 application could duplicate this local declaration;
        # normalize it while touching the method so r77 remains compilable.
        segment = segment.replace(
            "TLRPC.User user = null;\n                    TLRPC.User user = null;",
            "TLRPC.User user = null;"
        )

        # Make sure the custom Read action uses the eye asset, not the generic
        # actions/kebab glyph. This is deliberately restricted to our menu.
        segment = segment.replace("icons.add(R.drawable.msg_actions);\n",
                                  "icons.add(R.drawable.msg_views); /* KAMIGRAM_READ_EYE_R77 */\n")
        write(rel, text[:begin] + segment + text[finish:])
        DONE.append("ephemeral media: save/share/forward menu actions and eye icon")
    else:
        # r77 may have been partially applied before a later invocation; keep
        # the eye replacement idempotent as well.
        if "R.drawable.msg_actions" in segment:
            segment = segment.replace("R.drawable.msg_actions", "R.drawable.msg_views /* KAMIGRAM_READ_EYE_R77 */")
            write(rel, text[:begin] + segment + text[finish:])
            DONE.append("ephemeral media: replace Read action with eye icon")


def patch_forward_upload():
    rel = "messenger/SendMessagesHelper.java"
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (ephemeral forward)" % (rel, exc))
        return

    if "KAMIGRAM_EPHEMERAL_FORWARD_UPLOAD_R77" not in text:
        old = '''            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                if (msgObj.getId() <= 0 || msgObj.needDrawBluredPreview()) {'''
        new = '''            for (int a = 0; a < messages.size(); a++) {
                MessageObject msgObj = messages.get(a);
                final boolean kamigramEphemeralMedia = msgObj != null && msgObj.getId() > 0
                    && msgObj.messageOwner != null
                    && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(msgObj.messageOwner);
                if (msgObj.getId() <= 0 || msgObj.needDrawBluredPreview() && !kamigramEphemeralMedia) {'''
        if old not in text:
            MISS.append("%s: forward loop anchor not found (ephemeral upload)" % rel)
            return
        text = text.replace(old, new, 1)

        old = '''                }

                final TLRPC.Message newMsg = new TLRPC.TL_message();
                if (!forwardFromMyName) {'''
        new = '''                }

                /* KAMIGRAM_EPHEMERAL_FORWARD_UPLOAD_R77: do not send the source
                   message id back to Telegram. Upload the already opened bytes as
                   a new ordinary media message, so server no-forward/view-once
                   restrictions cannot make forwarding fail. */
                if (kamigramEphemeralMedia) {
                    processForwardFromMyName(msgObj, peer, payStars, monoForumPeerId, suggestionParams);
                    continue;
                }

                final TLRPC.Message newMsg = new TLRPC.TL_message();
                if (!forwardFromMyName) {'''
        if old not in text:
            MISS.append("%s: forward upload insertion anchor not found" % rel)
            return
        text = text.replace(old, new, 1)
        DONE.append("ephemeral media: forward through upload path with ttl cleared")

    # A flag carried by this explicit upload path also suppresses the default
    # encrypted-chat TTL inheritance, so the forwarded copy is ordinary media.
    if "KAMIGRAM_PLAIN_EPHEMERAL_MEDIA_R77" not in text:
        old = "        public long ephemeralReceiverBotId;\n"
        new = old + "        public boolean kamigramPlainMedia; /* KAMIGRAM_PLAIN_EPHEMERAL_MEDIA_R77 */\n"
        if old not in text:
            MISS.append("%s: SendMessageParams field anchor not found" % rel)
        else:
            text = text.replace(old, new, 1)

        old = '''            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, 0, messageObject.messageOwner.media.ttl_seconds, messageObject, false);
                fparams.payStars = payStars;'''
        new = '''            final boolean kamigramPlainMedia = org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(messageObject.messageOwner);
            final int kamigramForwardTtl = kamigramPlainMedia ? 0 : messageObject.messageOwner.media.ttl_seconds;
            if (messageObject.messageOwner.media.photo instanceof TLRPC.TL_photo) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_photo) messageObject.messageOwner.media.photo, null, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, 0, kamigramForwardTtl, messageObject, false);
                fparams.kamigramPlainMedia = kamigramPlainMedia;
                fparams.payStars = payStars;'''
        if old not in text:
            MISS.append("%s: photo upload branch anchor not found" % rel)
        else:
            text = text.replace(old, new, 1)

        old = '''            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, 0, messageObject.messageOwner.media.ttl_seconds, messageObject, null, false);
                fparams.payStars = payStars;'''
        new = '''            } else if (messageObject.messageOwner.media.document instanceof TLRPC.TL_document) {
                SendMessagesHelper.SendMessageParams fparams = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_document) messageObject.messageOwner.media.document, null, messageObject.messageOwner.attachPath, did, messageObject.replyMessageObject, null, messageObject.messageOwner.message, messageObject.messageOwner.entities, null, params, true, 0, 0, kamigramForwardTtl, messageObject, null, false);
                fparams.kamigramPlainMedia = kamigramPlainMedia;
                fparams.payStars = payStars;'''
        if old not in text:
            MISS.append("%s: document upload branch anchor not found" % rel)
        else:
            text = text.replace(old, new, 1)

        old = "        int ttl = sendMessageParams.ttl;\n"
        new = old + "        final boolean kamigramPlainMedia = sendMessageParams.kamigramPlainMedia;\n"
        if old not in text:
            MISS.append("%s: ttl local anchor not found" % rel)
        else:
            text = text.replace(old, new, 1)

        old = '''                if (ttl != 0) {
                    newMsg.ttl = ttl;
                } else {
                    newMsg.ttl = encryptedChat.ttl;'''
        new = '''                if (kamigramPlainMedia) {
                    newMsg.ttl = 0; /* KAMIGRAM_PLAIN_EPHEMERAL_MEDIA_R77 */
                } else if (ttl != 0) {
                    newMsg.ttl = ttl;
                } else {
                    newMsg.ttl = encryptedChat.ttl;'''
        if old not in text:
            MISS.append("%s: encrypted TTL anchor not found" % rel)
        else:
            text = text.replace(old, new, 1)
        DONE.append("ephemeral media: uploaded forwards do not inherit self-destruct TTL")

    write(rel, text)


def patch_secure_windows():
    # A one-time voice/round message also asks ChatMessageCell to secure the
    # parent activity. Remove that reason only for the confirmed ephemeral
    # message; other no-forward/protected media retain Telegram's protection.
    once(
        "ui/Cells/ChatMessageCell.java",
        "KAMIGRAM_EPHEMERAL_CELL_SCREENSHOT_R77",
        "                        currentMessageObject.isVoiceOnce() ||",
        "                        (currentMessageObject.isVoiceOnce() || currentMessageObject.isRoundOnce()) && !(currentMessageObject.messageOwner != null && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(currentMessageObject.messageOwner)) /* KAMIGRAM_EPHEMERAL_CELL_SCREENSHOT_R77 */ ||",
        "screenshots: do not secure parent window for one-time voice/round cell",
    )

    # SecretMediaViewer is constructed before the message is assigned. Leave its
    # default flags neutral and decide immediately before addView.
    once(
        "ui/SecretMediaViewer.java",
        "KAMIGRAM_EPHEMERAL_SCREENSHOT_R77",
        "        windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;\n        AndroidUtilities.logFlagSecure();\n",
        "        /* KAMIGRAM_EPHEMERAL_SCREENSHOT_R77: secure is selected per message below. */\n",
        "screenshots: remove unconditional secure flag from secret viewer",
    )
    once(
        "ui/SecretMediaViewer.java",
        "KAMIGRAM_SECRET_VIEWER_SECURE_R77",
        "        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);\n        wm.addView(windowView, windowLayoutParams);",
        '''        /* KAMIGRAM_SECRET_VIEWER_SECURE_R77: screenshots are allowed for
           self-destruct media; retain secure mode for any future non-ephemeral
           caller of this viewer. */
        final boolean kamigramEphemeralScreenshot = messageObject.messageOwner != null
            && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(messageObject.messageOwner);
        if (kamigramEphemeralScreenshot) {
            windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        } else {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;
            AndroidUtilities.logFlagSecure();
        }
        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        wm.addView(windowView, windowLayoutParams);''',
        "screenshots: condition secret viewer secure flag on ephemeral media",
    )

    once(
        "ui/PhotoViewer.java",
        "KAMIGRAM_PHOTO_VIEWER_EPHEMERAL_SCREENSHOT_R77",
        '''            if (chatActivity != null && chatActivity.getCurrentEncryptedChat() != null ||
                avatarsDialogId != 0 && MessagesController.getInstance(currentAccount).isPeerNoForwards(avatarsDialogId) ||
                messageObject != null && (MessagesController.getInstance(currentAccount).isPeerNoForwards(messageObject.getDialogId()) ||
                (messageObject.messageOwner != null && messageObject.messageOwner.noforwards)) || messageObject != null && messageObject.hasRevealedExtendedMedia()
            ) {''',
        '''            final boolean kamigramEphemeralScreenshot = messageObject != null && messageObject.messageOwner != null
                && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(messageObject.messageOwner);
            if (!kamigramEphemeralScreenshot && (chatActivity != null && chatActivity.getCurrentEncryptedChat() != null ||
                avatarsDialogId != 0 && MessagesController.getInstance(currentAccount).isPeerNoForwards(avatarsDialogId) ||
                messageObject != null && (MessagesController.getInstance(currentAccount).isPeerNoForwards(messageObject.getDialogId()) ||
                (messageObject.messageOwner != null && messageObject.messageOwner.noforwards)) || messageObject != null && messageObject.hasRevealedExtendedMedia()
            )) { /* KAMIGRAM_PHOTO_VIEWER_EPHEMERAL_SCREENSHOT_R77 */''',
        "screenshots: allow ephemeral media in PhotoViewer",
    )

    once(
        "ui/SecretVoicePlayer.java",
        "KAMIGRAM_SECRET_VOICE_SCREENSHOT_R77",
        '''        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
            AndroidUtilities.logFlagSecure();
        }''',
        '''        /* KAMIGRAM_SECRET_VOICE_SCREENSHOT_R77: voice/round media that
           self-destructs is screenshotable just like photo/video media. */
        final boolean kamigramEphemeralVoice = messageObject != null && messageObject.messageOwner != null
            && org.telegram.messenger.kamigram.KamiGramGhost.isEphemeralMedia(messageObject.messageOwner);
        if (!BuildVars.DEBUG_PRIVATE_VERSION && !kamigramEphemeralVoice) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
            AndroidUtilities.logFlagSecure();
        } else if (kamigramEphemeralVoice) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        }''',
        "screenshots: make one-time voice player conditional",
    )


def patch_themes_marker():
    rel = os.path.join("..", "..", "..", "apply_theme_pro.py")
    # This check is performed on the repository-side generator by the shell
    # wrapper; keeping a source marker makes the r77 palette audit visible in
    # generated MOD_INFO without modifying user themes at runtime.
    theme = os.path.join(os.path.dirname(__file__), "apply_theme_pro.py")
    try:
        text = io.open(theme, encoding="utf-8").read()
    except OSError as exc:
        MISS.append("apply_theme_pro.py: %s (folder palette)" % exc)
        return
    required = ("'chats_archiveBackground': CARD2", "'chats_archivePullDownBackground': CARD2",
                "'actionBarTabActiveText': 'FFFFFF'", "'actionBarTabUnactiveText': 'CCFFFFFF'")
    if not all(item in text for item in required):
        MISS.append("apply_theme_pro.py: KamiGram folder/archive palette incomplete (r77)")


def patch_proxy_rotator():
    rel = "messenger/ProxyRotationController.java"
    try:
        text = read(rel)
    except OSError as exc:
        MISS.append("%s: %s (proxy rotation)" % (rel, exc))
        return
    if "KAMIGRAM_PROXY_ROTATION_R77" in text:
        return
    old = '''        List<SharedConfig.ProxyInfo> sortedList = new ArrayList<>(SharedConfig.proxyList);
        Collections.sort(sortedList, (o1, o2) -> Long.compare(o1.ping, o2.ping));
        for (SharedConfig.ProxyInfo info : sortedList) {
            if (info == SharedConfig.currentProxy || info.checking || !info.available) {
                continue;
            }'''
    new = '''        final SharedConfig.ProxyInfo kamigramCurrent = SharedConfig.currentProxy;
        List<SharedConfig.ProxyInfo> sortedList = new ArrayList<>(SharedConfig.proxyList);
        Collections.sort(sortedList, (o1, o2) -> Long.compare(o1.ping, o2.ping));
        for (SharedConfig.ProxyInfo info : sortedList) {
            /* KAMIGRAM_PROXY_ROTATION_R77: native fallback may use only the
               built-in catalog. A live custom row is never replaced; a live
               built-in row is replaced only by a faster live built-in. */
            if (info == kamigramCurrent || info.checking || !info.available || info.ping <= 0
                || !org.telegram.messenger.kamigram.KamiGramBuiltinProxy.isBuiltIn(info)) {
                continue;
            }
            if (kamigramCurrent != null && kamigramCurrent.available && kamigramCurrent.ping > 0) {
                if (!org.telegram.messenger.kamigram.KamiGramBuiltinProxy.isBuiltIn(kamigramCurrent)
                    || info.ping >= kamigramCurrent.ping) {
                    continue;
                }
            }'''
    if old not in text:
        MISS.append("%s: switchToAvailable anchor not found (proxy rotation)" % rel)
        return
    write(rel, text.replace(old, new, 1))
    DONE.append("proxy: native rotation honors live custom and fastest KamiProxy")


def main():
    patch_overlay()
    patch_download_queue()
    patch_ephemeral_menu()
    patch_forward_upload()
    patch_secure_windows()
    patch_proxy_rotator()
    patch_archive_marker()
    patch_themes_marker()

    print("r77: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r77: skipped — %d" % len(MISS))
        for item in MISS:
            print("  ! %s" % item)


if __name__ == "__main__":
    main()
