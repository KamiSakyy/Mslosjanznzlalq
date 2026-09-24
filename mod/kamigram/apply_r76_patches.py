#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KamiGram r76 patches.

This is the cleanup/fix pass after r70:
  * removes the entire KamiGram overlay/PiP/float feature;
  * puts the ghost action in the main three-dot menu;
  * makes the download spinner depend on real progress, not merely a queued row;
  * keeps custom and built-in proxies independent and repairs the empty-list toggle;
  * persists local Premium peer colours, including collectible/background TL data.

All operations are deliberately idempotent. Missing upstream anchors are reported,
not fatal: the shell wrapper performs final marker checks for a compatible source.
"""

import io
import os
import re
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
ROOT = os.path.join(TG, "TMessagesProj/src/main/java")
RES = os.path.join(TG, "TMessagesProj/src/main/res")
DONE = []
MISS = []


def p(rel):
    return os.path.join(ROOT, "org/telegram", rel)


def read(path):
    with io.open(path, encoding="utf-8") as stream:
        return stream.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as stream:
        stream.write(text)


def once(rel, marker, old, new, what):
    path = p(rel)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    if old not in text:
        MISS.append("%s: anchor not found (%s)" % (rel, what))
        return False
    write(path, text.replace(old, new, 1))
    DONE.append(what)
    return True


def regex_once(rel, marker, pattern, replacement, what, flags=re.S):
    path = p(rel)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    if marker in text:
        return True
    result, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count == 0:
        MISS.append("%s: pattern not found (%s)" % (rel, what))
        return False
    write(path, result)
    DONE.append(what)
    return True


def all_regex(rel, pattern, replacement, what, flags=re.S):
    path = p(rel)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (%s)" % (rel, exc, what))
        return False
    result, count = re.subn(pattern, replacement, text, flags=flags)
    if count:
        write(path, result)
        DONE.append("%s (x%d)" % (what, count))
    return bool(count)


# --------------------------------------------------------------------------- overlay removal + ghost overflow action


def remove_overlay_wiring():
    """Keep native Telegram PiP and the restored overlay path intact.

    r76 used to delete these declarations and callbacks. The corrected pass
    only moves Ghost to the overflow menu; SYSTEM_ALERT_WINDOW, PiP callbacks,
    the native activity declaration, and KamiGramFloat are deliberately kept.
    """
    dialogs = "ui/DialogsActivity.java"
    # Ghost is menu-only, while the neighboring floating-window item remains.
    all_regex(
        dialogs,
        r"\n\s*/\* KAMIGRAM_GHOST_HEADER.*?KamiGramGhost\.addHeaderItem\(menu, null\);\n",
        "\n            /* KAMIGRAM_NO_GHOST_HEADER_R76: ghost is overflow-only */\n",
        "ghost: remove old header button; preserve overlay item",
    )
    all_regex(
        dialogs,
        r"\n\s*org\.telegram\.messenger\.kamigram\.KamiGramGhost\.addHeaderItem\(menu, null\);\n",
        "\n            /* KAMIGRAM_NO_GHOST_HEADER_R76: ghost is overflow-only */\n",
        "ghost: remove stale header call; preserve overlay item",
    )

    # On trees produced by an earlier r76 run, reintroduce the upstream
    # permission/declarations from the checkout only when they are absent.
    manifest = os.path.join(TG, "TMessagesProj/src/main/AndroidManifest.xml")
    try:
        text = read(manifest)
        permission = '<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />'
        if permission not in text:
            application = "    <application"
            if application in text:
                text = text.replace(application, "    " + permission + "\n\n" + application, 1)
                write(manifest, text)
                DONE.append("overlay: restore SYSTEM_ALERT_WINDOW permission")
            else:
                MISS.append("AndroidManifest.xml: application anchor not found (overlay permission)")
    except OSError as exc:
        MISS.append("AndroidManifest.xml: %s (overlay permission)" % exc)


def remove_old_float_config_rows():
    """No-op compatibility name: the user explicitly restored this feature."""
    return


def ghost_overflow():
    rel = "ui/DialogsActivity.java"
    path = p(rel)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (ghost overflow)" % (rel, exc))
        return
    marker = "KAMIGRAM_GHOST_OVERFLOW_R76"
    if marker not in text:
        method_start = text.find("    private void showItemOptions()")
        if method_start < 0:
            MISS.append("%s: showItemOptions not found (ghost overflow)" % rel)
        else:
            show = text.rfind("        io.show();", method_start)
            if show < 0:
                MISS.append("%s: final io.show not found (ghost overflow)" % rel)
            else:
                insertion = '''        /* KAMIGRAM_GHOST_OVERFLOW_R76: ghost lives in the three-dot menu,
           never in the action-bar header. */
        if (initialDialogsType == DIALOGS_TYPE_DEFAULT) {
            io.add(org.telegram.messenger.kamigram.KamiGramConfig.ghostMode() ? R.drawable.kamigram_ghost_on : R.drawable.kamigram_ghost,
                "Призрак", () -> org.telegram.messenger.kamigram.KamiGramGhost.toggle(getParentActivity()));
            io.addGap();
        }
'''
                text = text[:show] + insertion + text[show:]
                write(path, text)
                DONE.append("ghost: add overflow menu action")


# --------------------------------------------------------------------------- download icon


def fix_download_icon():
    rel = "ui/DownloadProgressIcon.java"
    path = p(rel)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (download animation)" % (rel, exc))
        return
    if "KAMIGRAM_DOWNLOAD_STATIC_IDLE_R76" in text:
        return

    # Idle drawable must not loop. It is switched back to auto-repeat only for
    # the duration of actual progress below.
    text = text.replace("downloadImageReceiver.setAutoRepeat(1);", "downloadImageReceiver.setAutoRepeat(0);", 1)
    text = text.replace("downloadDrawable.setAutoRepeat(1);", "downloadDrawable.setAutoRepeat(0);", 1)
    if "kamigramLastDownloaded" not in text:
        byte_anchor = "    private long kamigramLastProgressAt; /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_FIELD */\n"
        if byte_anchor in text:
            text = text.replace(byte_anchor, byte_anchor + "    private long kamigramLastDownloaded = -1L; /* KAMIGRAM_DOWNLOAD_STATIC_IDLE_R76 */\n", 1)
        else:
            MISS.append("%s: r70 byte field not found (download animation)" % rel)

    # r70 started as soon as a row was attached. Replace that whole listener
    # transition with a passive state update; updateProgress is the only place
    # allowed to start the animation now.
    old_listener = '''        /* KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE: анимация = признак реальной загрузки */
        if (currentListeners.size() > 0) {
            if (!kamigramDownloadAnim) {
                kamigramDownloadAnim = true;
                downloadDrawable.start();
            }
            /* KAMIGRAM_DOWNLOAD_ANIM_LIVE_NEW (r70): новый файл — 3 секунды на
               первый прогресс, потом контроль живого состояния. */
            kamigramLastProgressAt = System.currentTimeMillis();
            kamigramScheduleLiveCheck();
        } else if (kamigramDownloadAnim) {
            kamigramDownloadAnim = false;
            downloadDrawable.stop();
            downloadDrawable.setCurrentFrame(0, false);
        }
'''
    new_listener = '''        /* KAMIGRAM_NO_FAKE_DOWNLOAD_UPDATE: анимация = признак реальной загрузки */
        if (currentListeners.size() > 0) {
            /* Attaching a listener means queued, not downloading. The first
               non-zero progress update below is the start signal. */
            if (kamigramLastProgressAt == 0L) {
                kamigramLastProgressAt = System.currentTimeMillis();
            }
            kamigramScheduleLiveCheck();
        } else {
            kamigramLastProgress = -1f;
            kamigramLastProgressAt = 0L;
            kamigramLastDownloaded = -1L;
            if (kamigramDownloadAnim) {
                kamigramDownloadAnim = false;
                downloadDrawable.setAutoRepeat(0);
                downloadDrawable.stop();
                downloadDrawable.setCurrentFrame(0, false);
            }
        }
'''
    if old_listener in text:
        text = text.replace(old_listener, new_listener, 1)
    else:
        MISS.append("%s: r70 listener block not found (download animation)" % rel)

    # Make the r70 update block distinguish a real byte delta from the initial
    # 0.0 progress callback. This is the only start point.
    old_update = '''        if (progress != kamigramLastProgress) {
            kamigramLastProgress = progress;
            kamigramLastProgressAt = System.currentTimeMillis();
            if (currentListeners.size() > 0 && !kamigramDownloadAnim) {
                kamigramDownloadAnim = true;
                downloadDrawable.start();
            }
        }
'''
    new_update = '''        if (downloaded != kamigramLastDownloaded) {
            final boolean kamigramRealMovement = downloaded > kamigramLastDownloaded && downloaded > 0L;
            kamigramLastDownloaded = downloaded;
            kamigramLastProgress = progress;
            if (kamigramRealMovement) {
                kamigramLastProgressAt = System.currentTimeMillis();
                if (currentListeners.size() > 0 && !kamigramDownloadAnim) {
                    kamigramDownloadAnim = true;
                    downloadDrawable.setAutoRepeat(1);
                    downloadDrawable.start();
                }
            }
        }
'''
    if old_update in text:
        text = text.replace(old_update, new_update, 1)
    else:
        MISS.append("%s: r70 progress block not found (download animation)" % rel)

    # Stop/reset every idle path and the 3-second stale-progress watchdog.
    text = text.replace('''                kamigramDownloadAnim = false;
                downloadDrawable.stop();
                downloadDrawable.setCurrentFrame(0, false);
''', '''                kamigramDownloadAnim = false;
                downloadDrawable.setAutoRepeat(0);
                downloadDrawable.stop();
                downloadDrawable.setCurrentFrame(0, false);
''')

    marker = '''        /* KAMIGRAM_DOWNLOAD_STATIC_IDLE_R76: spinner starts only after a byte delta */
'''
    text = text.replace(marker, "", 1)
    # Put the marker directly before updateProgress so an interrupted run can
    # never mistake the idle constructor for a live state.
    anchor = "    public void updateProgress() {\n"
    if anchor in text:
        text = text.replace(anchor, marker + anchor, 1)
    else:
        MISS.append("%s: updateProgress not found (download animation)" % rel)
    write(path, text)
    DONE.append("downloads: spinner only runs during real progress")


# --------------------------------------------------------------------------- proxy catalogue and native proxy screen


def fix_proxy_sources():
    shared = "messenger/SharedConfig.java"
    path = p(shared)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (proxy catalog)" % (shared, exc))
        text = None
    if text is not None:
        marker = "KAMIGRAM_PROXY_CATALOG_R76"
        if marker in text:
            # Repair trees produced by the first r76 draft, which inserted the
            # catalog call after the original method brace and left one extra
            # closing brace behind. This keeps reruns genuinely idempotent.
            bad_catalog = """        }
    }

        /* KAMIGRAM_PROXY_CATALOG_R76: custom and built-in rows are independent. */
        org.telegram.messenger.kamigram.KamiGramBuiltinProxy.ensureBuiltinsLoaded();
    }

    public static void saveProxyList() {"""
            good_catalog = """        }
        /* KAMIGRAM_PROXY_CATALOG_R76: custom and built-in rows are independent. */
        org.telegram.messenger.kamigram.KamiGramBuiltinProxy.ensureBuiltinsLoaded();
    }

    public static void saveProxyList() {"""
            if bad_catalog in text:
                text = text.replace(bad_catalog, good_catalog, 1)
                DONE.append("proxy: repair catalog method brace")
        else:
            anchor = "    }\n\n    public static void saveProxyList() {"
            if anchor in text:
                text = text.replace(anchor,
                    '''        /* KAMIGRAM_PROXY_CATALOG_R76: custom and built-in rows are independent. */
        org.telegram.messenger.kamigram.KamiGramBuiltinProxy.ensureBuiltinsLoaded();
    }

    public static void saveProxyList() {''', 1)
                DONE.append("proxy: restore built-in catalog after load")
            else:
                MISS.append("%s: loadProxyList end not found (proxy catalog)" % shared)

        # A built-in row is hidden, never deleted. This guard must be before
        # currentProxy bookkeeping, otherwise deleting the active built-in still
        # disables the global proxy switch.
        guard = '''    public static void deleteProxy(ProxyInfo proxyInfo) {
        if (org.telegram.messenger.kamigram.KamiGramBuiltinProxy.isBuiltIn(proxyInfo)) {
            return;
        }
        final boolean kamigramWasCurrent = currentProxy == proxyInfo;
        final boolean kamigramProxyWasEnabled = kamigramWasCurrent
            && MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false);
'''
        old = "    public static void deleteProxy(ProxyInfo proxyInfo) {\n"
        if "KAMIGRAM_PROXY_DELETE_GUARD_R76" not in text and old in text:
            guard = guard.replace("return;", "return; /* KAMIGRAM_PROXY_DELETE_GUARD_R76 */", 1)
            text = text.replace(old, guard, 1)
            DONE.append("proxy: protect built-in rows from deletion")

        # Keep the fallback call inside deleteProxy, after the custom row is
        # removed. Calling the recovery routine for a non-current custom row is
        # harmless: it sees the live current custom row and leaves it alone.
        if "KAMIGRAM_PROXY_RECOVER_AFTER_DELETE_R76" not in text:
            old_tail = "        proxyList.remove(proxyInfo);\n        saveProxyList();\n"
            new_tail = old_tail + "        if (kamigramWasCurrent && kamigramProxyWasEnabled) {\n            MessagesController.getGlobalMainSettings().edit().putBoolean(\"proxy_enabled\", true).commit();\n        }\n        org.telegram.messenger.kamigram.KamiGramBuiltinProxy.recoverAfterProxyDeleted(kamigramWasCurrent); /* KAMIGRAM_PROXY_RECOVER_AFTER_DELETE_R76 */\n"
            if old_tail in text:
                text = text.replace(old_tail, new_tail, 1)
                DONE.append("proxy: recover KamiProxy after custom deletion")
            else:
                MISS.append("%s: delete tail not found (proxy recovery)" % shared)
        write(path, text)

    activity = "ui/ProxyListActivity.java"
    path = p(activity)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (proxy screen)" % (activity, exc))
        return

    old = "        SharedConfig.loadProxyList();\n"
    if "KAMIGRAM_PROXY_SCREEN_LOAD_R76" not in text and old in text:
        text = text.replace(old, old + "        org.telegram.messenger.kamigram.KamiGramBuiltinProxy.ensureBuiltinsLoaded(); /* KAMIGRAM_PROXY_SCREEN_LOAD_R76 */\n", 1)
        DONE.append("proxy: load built-ins before native screen rows")

    old_empty = '''                    } else {
                        presentFragment(new ProxySettingsActivity());
                        return;
                    }
'''
    new_empty = '''                    } else if (org.telegram.messenger.kamigram.KamiGramBuiltinProxy.enableForProxyScreen(getParentActivity())) {
                        /* KAMIGRAM_PROXY_SCREEN_EMPTY_R76: KamiProxy works with zero custom rows. */
                    } else {
                        presentFragment(new ProxySettingsActivity());
                        return;
                    }
'''
    if "KAMIGRAM_PROXY_SCREEN_EMPTY_R76" not in text and old_empty in text:
        text = text.replace(old_empty, new_empty, 1)
        DONE.append("proxy: native enable toggle works without custom rows")

    # Delete-all in Telegram mutates the same list during a foreach. Iterate
    # backwards and explicitly skip built-ins, so a user cleanup cannot corrupt
    # or erase the hidden catalog.
    old_all = '''                    for (SharedConfig.ProxyInfo info : proxyList) {
                        SharedConfig.deleteProxy(info);
                    }
'''
    new_all = '''                    for (int kamigramIndex = proxyList.size() - 1; kamigramIndex >= 0; kamigramIndex--) {
                        final SharedConfig.ProxyInfo info = proxyList.get(kamigramIndex);
                        if (!org.telegram.messenger.kamigram.KamiGramBuiltinProxy.isBuiltIn(info)) {
                            SharedConfig.deleteProxy(info);
                        }
                    }
                    /* KAMIGRAM_PROXY_DELETE_ALL_R76 */
'''
    if "KAMIGRAM_PROXY_DELETE_ALL_R76" not in text and old_all in text:
        text = text.replace(old_all, new_all, 1)
        DONE.append("proxy: delete-all removes custom rows only")

    if "KAMIGRAM_PROXY_DELETE_ALL_STATE_R76" not in text:
        old_state = """                    useProxyForCalls = false;
                    useProxySettings = false;
                    NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
"""
        new_state = """                    useProxyForCalls = false;
                    /* KamiProxy may have replaced the deleted custom row. Keep
                       the native checkbox truthful instead of turning the
                       hidden built-in fallback off by accident. */
                    useProxySettings = SharedConfig.isProxyEnabled(); /* KAMIGRAM_PROXY_DELETE_ALL_STATE_R76 */
                    NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
"""
        if old_state in text:
            text = text.replace(old_state, new_state, 1)
            DONE.append("proxy: delete-all keeps fallback checkbox enabled")
        else:
            MISS.append("%s: delete-all state anchor not found (proxy screen)" % activity)

    # Treat the native Telegram checkbox as a real KamiProxy switch when the
    # selected row is built-in. This prevents the watcher from undoing an
    # intentional user-off action and lets user-on re-enable it.
    old_toggle = "                useProxySettings = !useProxySettings;\n                updateRows(true);\n"
    new_toggle = '''                final boolean kamigramBuiltinBeforeToggle = org.telegram.messenger.kamigram.KamiGramBuiltinProxy.isBuiltIn(SharedConfig.currentProxy);
                useProxySettings = !useProxySettings;
                if (kamigramBuiltinBeforeToggle) {
                    org.telegram.messenger.kamigram.KamiGramBuiltinProxy.setEnabled(useProxySettings);
                }
                updateRows(true); /* KAMIGRAM_PROXY_NATIVE_TOGGLE_R76 */
'''
    if "KAMIGRAM_PROXY_NATIVE_TOGGLE_R76" not in text and old_toggle in text:
        text = text.replace(old_toggle, new_toggle, 1)
        DONE.append("proxy: native checkbox controls built-in enable state")

    old_toggle_proxy = "                ConnectionsManager.setProxySettings(useProxySettings, SharedConfig.currentProxy.settings);\n"
    new_toggle_proxy = "                ConnectionsManager.setProxySettings(useProxySettings, SharedConfig.currentProxy == null ? null : SharedConfig.currentProxy.settings); /* KAMIGRAM_PROXY_NATIVE_NULLSAFE_R76 */\n"
    if "KAMIGRAM_PROXY_NATIVE_NULLSAFE_R76" not in text and old_toggle_proxy in text:
        text = text.replace(old_toggle_proxy, new_toggle_proxy, 1)
        DONE.append("proxy: native off toggle tolerates cleared current row")

    write(path, text)


# --------------------------------------------------------------------------- local Premium colour/background persistence


def fix_premium_persistence():
    user = "messenger/UserConfig.java"
    path = p(user)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (Premium restore)" % (user, exc))
        text = None
    if text is not None:
        old = "            currentUser = user;\n"
        new = old + "            org.telegram.messenger.kamigram.KamiGramPremiumState.restore(this, currentUser); /* KAMIGRAM_PREMIUM_RESTORE_R76 */\n"
        if "KAMIGRAM_PREMIUM_RESTORE_R76" not in text:
            if old in text:
                text = text.replace(old, new, 1)
                DONE.append("premium: restore local colours in setCurrentUser")
            else:
                MISS.append("%s: setCurrentUser anchor not found (Premium restore)" % user)

        old_load = "                    currentUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);\n"
        new_load = old_load + "                    org.telegram.messenger.kamigram.KamiGramPremiumState.restore(this, currentUser); /* KAMIGRAM_PREMIUM_LOAD_R76 */\n"
        if "KAMIGRAM_PREMIUM_LOAD_R76" not in text:
            if old_load in text:
                text = text.replace(old_load, new_load, 1)
                DONE.append("premium: restore local colours from serialized user")
            else:
                MISS.append("%s: loadConfig user anchor not found (Premium restore)" % user)
        write(path, text)

    peer = "ui/PeerColorActivity.java"
    path = p(peer)
    try:
        text = read(path)
    except OSError as exc:
        MISS.append("%s: %s (Premium save)" % (peer, exc))
        return
    old = "            getMessagesController().putUser(me, false);\n"
    new = '''            /* KAMIGRAM_PREMIUM_SAVE_R76: preserve colour/profile/background TL data
               before a server refresh can replace the locally unlocked Premium user. */
            org.telegram.messenger.kamigram.KamiGramPremiumState.save(getUserConfig(), me);
            getUserConfig().saveConfig(true);
            getMessagesController().putUser(me, false);
'''
    if "KAMIGRAM_PREMIUM_SAVE_R76" not in text:
        if old in text:
            text = text.replace(old, new, 1)
            DONE.append("premium: save colours and background after apply")
        else:
            MISS.append("%s: apply save anchor not found (Premium save)" % peer)
    write(path, text)


def main():
    remove_overlay_wiring()
    ghost_overflow()
    remove_old_float_config_rows()
    fix_download_icon()
    fix_proxy_sources()
    fix_premium_persistence()

    print("r76: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r76: skipped — %d" % len(MISS))
        for item in MISS:
            print("  ! %s" % item)


if __name__ == "__main__":
    main()
