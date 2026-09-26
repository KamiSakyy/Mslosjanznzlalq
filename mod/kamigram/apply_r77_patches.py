#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sakura r77 fixes.

r76 is intentionally the base. This pass fixes the remaining behavioural gaps:
  * the proxy connection overlay is cleared only after a confirmed healthy state;
  * opening media never pauses unrelated download operations;
  * self-destruct media bypasses economy filters, has normal save/forward actions,
    and is shown without screenshot protection;
  * the native fallback rotator cannot replace a live custom proxy and selects the
    quickest live SakuProxy route;
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
           а когда соединение есть (title == null) — имя Sakura на месте. */
        if (parentFragment instanceof org.telegram.ui.DialogsActivity) {
            if (title == null) {
                /* соединение есть — держим имя Sakura, ничего не подменяем */
                org.telegram.messenger.kamigram.KamiGramProxyStatus.refresh();
                return;
            }
            /* нет сети — показываем оригинальную подпись «Подключение…» */
        }''',
        '''        /* KAMIGRAM_PROXY_OVERLAY_R77: do not hide the connection overlay on
           an unconfirmed state. When Telegram supplies title == null, only a
           confirmed Connected/Updating state may clear an already shown
           «Настроить прокси >» overlay; then the native title code below runs
           and restores Sakura. */
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
           Sakura title only after Telegram confirms a usable route. Calling it
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


def patch_themes_marker():
    """Stock-theme audit: no custom palette is generated or applied."""
    theme = os.path.join(os.path.dirname(__file__), "apply_theme_pro.py")
    try:
        text = io.open(theme, encoding="utf-8").read()
    except OSError as exc:
        MISS.append("apply_theme_pro.py: %s (stock themes)" % exc)
        return
    if "stock Telegram themes only" not in text or "custom attheme generated" not in text:
        MISS.append("apply_theme_pro.py: stock-theme no-op marker missing")




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
    DONE.append("proxy: native rotation honors live custom and fastest SakuProxy")


def main():
    patch_overlay()
    patch_download_queue()
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
