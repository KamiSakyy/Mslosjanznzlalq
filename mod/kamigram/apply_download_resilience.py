#!/usr/bin/env python3
# -*- coding: utf-8
"""Install resumable proxy handover, foreground download lifetime and progress UI."""

import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
JAVA = os.path.join(TG, "TMessagesProj/src/main/java")
ROOT = os.path.join(JAVA, "org/telegram")
DONE = []


def path(rel):
    return os.path.join(ROOT, *rel.split("/"))


def read(rel):
    return io.open(path(rel), encoding="utf-8").read()


def write(rel, text):
    io.open(path(rel), "w", encoding="utf-8").write(text)


def insert_once(rel, marker, anchor, block, before=False):
    text = read(rel)
    if marker in text:
        return
    if anchor not in text:
        raise RuntimeError("%s: anchor not found for %s" % (rel, marker))
    replacement = block + anchor if before else anchor + block
    write(rel, text.replace(anchor, replacement, 1))
    DONE.append(marker)


def replace_once(rel, marker, old, new):
    text = read(rel)
    if marker in text:
        return
    if old not in text:
        raise RuntimeError("%s: block not found for %s" % (rel, marker))
    write(rel, text.replace(old, new, 1))
    DONE.append(marker)


def patch_file_load_operation():
    insert_once(
        "messenger/FileLoadOperation.java",
        "KAMIGRAM_PROXY_REBIND_OPERATION",
        "    public void pause() {\n",
        """    /* KAMIGRAM_PROXY_REBIND_OPERATION */
    /** Rebind in-flight requests after a proxy transport handover. */
    public void kamigramRebindAfterProxySwitch() {
        Utilities.stageQueue.postRunnable(() -> {
            if (state != stateDownloading || requestInfos == null) {
                return;
            }
            if (!requestInfos.isEmpty() || delayedRequestInfos != null && !delayedRequestInfos.isEmpty()) {
                clearOperation(null, false, true);
            }
            paused = false;
            startDownloadRequest(-1);
        });
    }

    /** Restart a failed operation from its existing .temp/.pt byte ranges. */
    public void kamigramRestartAfterProxySwitch() {
        Utilities.stageQueue.postRunnable(() -> {
            if (state != stateFailed) {
                return;
            }
            state = stateIdle;
            paused = false;
            preFinished = false;
            getQueue().add(this);
            getQueue().checkLoadingOperations();
        });
    }

""",
        before=True,
    )


def insert_after_signature(rel, marker, signature, block):
    text = read(rel)
    if marker in text:
        return
    pos = text.find(signature)
    if pos < 0:
        raise RuntimeError("%s: method not found for %s" % (rel, marker))
    pos += len(signature)
    write(rel, text[:pos] + block + text[pos:])
    DONE.append(marker)


def patch_file_loader():
    insert_once(
        "messenger/FileLoader.java",
        "KAMIGRAM_PROXY_REBIND_LOADER",
        "    public void onNetworkChanged(final boolean slow) {\n",
        """    /* KAMIGRAM_PROXY_REBIND_LOADER */
    /** Keep downloads alive while the global Telegram proxy is replaced. */
    public void kamigramRebindActiveDownloads() {
        fileLoaderQueue.postRunnable(() -> {
            for (ConcurrentHashMap.Entry<String, FileLoadOperation> entry : loadOperationPaths.entrySet()) {
                if (entry.getValue() != null) {
                    entry.getValue().kamigramRebindAfterProxySwitch();
                }
            }
        });
    }

""",
        before=True,
    )

    fail_signature = "            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {\n"
    insert_after_signature(
        "messenger/FileLoader.java",
        "KAMIGRAM_PROXY_RETRY_DELEGATE",
        fail_signature,
        """                /* KAMIGRAM_PROXY_RETRY_DELEGATE: retry without dropping the queue. */
                /* A proxy handover is recoverable: keep the same operation in the
                   queue and reopen its existing temporary ranges instead of
                   reporting a broken video to the user. */
                if (org.telegram.messenger.kamigram.KamiGramDownloadRecovery.shouldRetry(operation, reason)) {
                    loadOperationPaths.put(fileName, operation);
                    operation.kamigramRestartAfterProxySwitch();
                    org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged();
                    return;
                }
                org.telegram.messenger.kamigram.KamiGramDownloadRecovery.onFinished(operation);
""",
    )

    insert_after_signature(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_FINISH_HOOK",
        "            public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {\n",
        """                /* KAMIGRAM_DOWNLOAD_FINISH_HOOK */
                org.telegram.messenger.kamigram.KamiGramDownloadRecovery.onFinished(operation);
                org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged();
""",
    )

    rel = "messenger/FileLoader.java"
    text = read(rel)
    marker = "KAMIGRAM_DOWNLOAD_FAIL_STATE"
    if marker not in text:
        signature = "            public void didFailedLoadingFile(FileLoadOperation operation, int reason) {\n"
        start = text.find(signature)
        if start < 0:
            raise RuntimeError("%s: failed-load method not found for %s" % (rel, marker))
        tail = text[start + len(signature):]
        old = "            }\n\n            @Override"
        new = "                org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged(); /* %s */\n            }\n\n            @Override" % marker
        if old not in tail:
            raise RuntimeError("%s: failed-load end not found for %s" % (rel, marker))
        write(rel, text[:start + len(signature)] + tail.replace(old, new, 1))
        DONE.append(marker)

    insert_after_signature(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_PROGRESS_HOOK",
        "            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {\n",
        """                /* KAMIGRAM_DOWNLOAD_PROGRESS_HOOK */
                org.telegram.messenger.kamigram.KamiGramDownloadRecovery.onProgress(operation, uploadedSize, totalSize);
                org.telegram.messenger.kamigram.KamiGramDownloadService.reportProgress(uploadedSize, totalSize);
""",
    )


def patch_download_controller():
    rel = "messenger/DownloadController.java"
    text = read(rel)
    marker = "KAMIGRAM_DOWNLOAD_SERVICE_START"
    if marker in text:
        return
    method_start = text.find("    public void startDownloadFile(TLRPC.Document document, MessageObject parentObject) {")
    if method_start < 0:
        raise RuntimeError("DownloadController: startDownloadFile not found")
    post = text.find("            getNotificationCenter().postNotificationName(NotificationCenter.onDownloadingFilesChanged);", method_start)
    if post < 0:
        raise RuntimeError("DownloadController: start notification not found")
    end = post + len("            getNotificationCenter().postNotificationName(NotificationCenter.onDownloadingFilesChanged);\n")
    text = text[:end] + "            org.telegram.messenger.kamigram.KamiGramDownloadService.ensureStarted(org.telegram.messenger.ApplicationLoader.applicationContext); /* %s */\n" % marker + text[end:]
    write(rel, text)
    DONE.append(marker)


def patch_file_loader_start():
    # Explicit document downloads also start the foreground lifetime. The
    # service exits after a short idle grace, so thumbnails do not leave a row.
    rel = "messenger/FileLoader.java"
    text = read(rel)
    marker = "KAMIGRAM_DOWNLOAD_SERVICE_DOCUMENT"
    if marker in text:
        return
    signature = "    public void loadFile(TLRPC.Document document, Object parentObject, int priority, int cacheType) {\n"
    start = text.find(signature)
    if start < 0:
        raise RuntimeError("%s: document load method not found" % rel)
    null_block = "        if (document == null) {\n            return;\n        }\n"
    null_pos = text.find(null_block, start + len(signature))
    if null_pos < 0:
        raise RuntimeError("%s: document null guard not found" % rel)
    end = null_pos + len(null_block)
    injection = "        org.telegram.messenger.kamigram.KamiGramDownloadService.ensureStarted(org.telegram.messenger.ApplicationLoader.applicationContext); /* %s */\n" % marker
    write(rel, text[:end] + injection + text[end:])
    DONE.append(marker)


def patch_connections_manager():
    # Every native proxy toggle, including a manual Telegram settings change,
    # gets the same resumable handover treatment.
    replace_once(
        "tgnet/ConnectionsManager.java",
        "KAMIGRAM_PROXY_SWITCH_HOOK",
        """            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }
    }

    public static native void native_switchBackend""",
        """            if (accountInstance.getUserConfig().isClientActivated()) {
                accountInstance.getMessagesController().checkPromoInfo(true);
            }
        }
        org.telegram.messenger.kamigram.KamiGramDownloadRecovery.onProxySwitch(); /* KAMIGRAM_PROXY_SWITCH_HOOK */
    }

    public static native void native_switchBackend""",
    )


def patch_manifest():
    manifest = os.path.join(TG, "TMessagesProj/src/main/AndroidManifest.xml")
    text = io.open(manifest, encoding="utf-8").read()
    marker = "KAMIGRAM_DOWNLOAD_SERVICE_MANIFEST"
    if marker in text:
        return
    service = """        <!-- KAMIGRAM_DOWNLOAD_SERVICE_MANIFEST: resumable background downloads -->
        <service
            android:name="org.telegram.messenger.kamigram.KamiGramDownloadService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

"""
    if "</application>" not in text:
        raise RuntimeError("AndroidManifest.xml: application end not found")
    text = text.replace("</application>", service + "    </application>", 1)
    io.open(manifest, "w", encoding="utf-8").write(text)
    DONE.append(marker)


def main():
    try:
        patch_file_load_operation()
        patch_file_loader()
        patch_download_controller()
        patch_file_loader_start()
        patch_connections_manager()
        patch_manifest()
    except Exception as exc:
        print("download resilience: %s" % exc, file=sys.stderr)
        return 1
    print("download resilience: %d patches" % len(DONE))
    return 0


if __name__ == "__main__":
    sys.exit(main())
