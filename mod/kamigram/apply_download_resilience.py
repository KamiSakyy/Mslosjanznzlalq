#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Install native FileLoader resume recovery and bounded download lifetime.

The patch never replaces Telegram's FileLoader with a custom downloader. It
only rebinds the existing operation after a proxy transport change, keeps the
native temporary/parts ranges, and exposes a foreground service while a real
operation larger than 10 MiB is present.
"""

import io
import os
import re
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


def insert_after_in_method(rel, marker, signature, anchor, block):
    text = read(rel)
    if marker in text:
        return
    method_start = text.find(signature)
    if method_start < 0:
        raise RuntimeError("%s: method not found for %s" % (rel, marker))
    anchor_pos = text.find(anchor, method_start + len(signature))
    if anchor_pos < 0:
        raise RuntimeError("%s: method anchor not found for %s" % (rel, marker))
    end = anchor_pos + len(anchor)
    write(rel, text[:end] + block + text[end:])
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
            final boolean wasPaused = paused;
            final boolean hadTransport = !requestInfos.isEmpty()
                || delayedRequestInfos != null && !delayedRequestInfos.isEmpty();
            if (hadTransport) {
                /* Keep notLoadedBytesRanges and the .temp/.pt files. This only
                   cancels transport requests and asks the native operation to
                   reopen the same missing byte ranges. */
                clearOperation(null, false, true);
            }
            /* A queue-paused operation must not bypass FileLoader's six-slot
               scheduler during a proxy handover. Active operations can reopen
               their own missing ranges directly; queued ones go through the
               native priority queue again. */
            if (!wasPaused) {
                paused = false;
                startDownloadRequest(-1);
            } else {
                getQueue().checkLoadingOperations();
            }
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

    /** KAMIGRAM_LARGE_DOWNLOAD_API_R83 */
    public boolean kamigramIsLargeDownload() {
        return totalBytesCount > 10L * 1024L * 1024L;
    }

    /** Finished/failed/cancelled native operations must not keep watchdogs or FGS alive. */
    public boolean kamigramIsActive() {
        return state == stateIdle || state == stateDownloading;
    }

""",
        before=True,
    )


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

    /** Do not instantiate idle account loaders just to answer an activity query. */
    public static boolean kamigramHasAnyActiveDownloads() {
        for (FileLoader loader : Instance) {
            if (loader != null && loader.kamigramHasActiveDownloads()) {
                return true;
            }
        }
        return false;
    }

    public static boolean kamigramHasAnyActiveLargeDownloads() {
        for (FileLoader loader : Instance) {
            if (loader != null && loader.kamigramHasActiveLargeDownloads()) {
                return true;
            }
        }
        return false;
    }

    public static void kamigramRebindAllActiveDownloads() {
        for (FileLoader loader : Instance) {
            if (loader != null) {
                loader.kamigramRebindActiveDownloads();
            }
        }
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
""",
    )

    insert_after_signature(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_PROGRESS_HOOK",
        "            public void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize) {\n",
        """                /* KAMIGRAM_DOWNLOAD_PROGRESS_HOOK */
                org.telegram.messenger.kamigram.KamiGramDownloadRecovery.onProgress(operation, uploadedSize, totalSize);
                org.telegram.messenger.kamigram.KamiGramDownloadService.reportProgress(uploadedSize, totalSize);
""",
    )

    # A video preload can later be promoted to an explicit user download and
    # then returns through the existing-operation branch. Attach the same
    # threshold check there without creating another downloader or operation.
    insert_after_in_method(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_SERVICE_EXISTING_OPERATION_R83",
        "        if (operation != null) {\n",
        "            operation.setStream(stream, streamPriority, streamOffset);\n",
        """            if (cacheType != 10 && operation.kamigramIsLargeDownload()) {
                org.telegram.messenger.kamigram.KamiGramDownloadService.ensureStartedForLargeDownload(
                    org.telegram.messenger.ApplicationLoader.applicationContext, operation.totalBytesCount);
            }
            org.telegram.messenger.kamigram.KamiGramProxyPower.onDownloadActivityChanged(); /* KAMIGRAM_DOWNLOAD_SERVICE_EXISTING_OPERATION_R83 */
""",
    )

    # The foreground lifetime is started only after FileLoader has created a
    # real native operation and knows its actual size. This covers documents,
    # videos, photos, and web files without starting a service for thumbnails.
    insert_once(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_SERVICE_OPERATION",
        "        loadOperationPaths.put(finalFileName, operation);\n",
        """        if (cacheType != 10 && operation.kamigramIsLargeDownload()) {
            org.telegram.messenger.kamigram.KamiGramDownloadService.ensureStartedForLargeDownload(
                org.telegram.messenger.ApplicationLoader.applicationContext, operation.totalBytesCount);
        }
        org.telegram.messenger.kamigram.KamiGramProxyPower.onDownloadActivityChanged(); /* KAMIGRAM_DOWNLOAD_SERVICE_OPERATION */
""",
    )

    # Completion/failure must remove the notification after the UI operation is
    # gone. A retry returns before this block and therefore keeps the service.
    insert_after_in_method(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_FINISH_STATE_R83",
        "            public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {\n",
        "                loadOperationPathsUI.remove(fileName);\n",
        """                org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged();
                org.telegram.messenger.kamigram.KamiGramProxyPower.onDownloadActivityChanged(); /* KAMIGRAM_DOWNLOAD_FINISH_STATE_R83 */
""",
    )
    insert_after_in_method(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_FAIL_STATE_R83",
        fail_signature,
        "                loadOperationPathsUI.remove(fileName);\n",
        """                org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged();
                org.telegram.messenger.kamigram.KamiGramProxyPower.onDownloadActivityChanged(); /* KAMIGRAM_DOWNLOAD_FAIL_STATE_R83 */
""",
    )

    # Native cancellation removes the UI operation immediately. Do not delete
    # temp/parts here: Telegram's cancel path remains responsible for that
    # choice, while our service/watchdog simply stops observing.
    cancel_signature = "    private void cancelLoadFile(final TLRPC.Document document, final SecureDocument secureDocument, final WebFile webDocument, final TLRPC.FileLocation location, final String locationExt, String name, boolean deleteFile) {\n"
    insert_after_in_method(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_CANCEL_STATE_R83",
        cancel_signature,
        "        LoadOperationUIObject uiObject = loadOperationPathsUI.remove(fileName);\n",
        """        org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged();
        org.telegram.messenger.kamigram.KamiGramProxyPower.onDownloadActivityChanged(); /* KAMIGRAM_DOWNLOAD_CANCEL_STATE_R83 */
""",
    )

    insert_after_in_method(
        "messenger/FileLoader.java",
        "KAMIGRAM_DOWNLOAD_CANCEL_OPERATION_STATE_R83",
        "    public void cancel(FileLoadOperation operation) {\n",
        "        LoadOperationUIObject uiObject = loadOperationPathsUI.remove(fileName);\n",
        """        org.telegram.messenger.kamigram.KamiGramDownloadService.reportStateChanged();
        org.telegram.messenger.kamigram.KamiGramProxyPower.onDownloadActivityChanged(); /* KAMIGRAM_DOWNLOAD_CANCEL_OPERATION_STATE_R83 */
""",
    )

    replace_once(
        "messenger/FileLoader.java",
        "KAMIGRAM_ACTIVE_DOWNLOADS_API_R83",
        """    public boolean isLoadingFile(final String fileName) {
        return fileName != null && loadOperationPathsUI.containsKey(fileName);
    }
""",
        """    public boolean isLoadingFile(final String fileName) {
        return fileName != null && loadOperationPathsUI.containsKey(fileName);
    }

    /** KAMIGRAM_ACTIVE_DOWNLOADS_API_R83: active means native FileLoader operation exists. */
    public boolean kamigramHasActiveDownloads() {
        /* Use FileLoader's native operation table, not a second queue or a
           guessed filename. The UI table can briefly contain a queued runnable
           and can use a different final name for encrypted/custom-path files. */
        for (FileLoadOperation operation : loadOperationPaths.values()) {
            if (operation != null && operation.kamigramIsActive()) {
                return true;
            }
        }
        return false;
    }

    /** Same native operation table, restricted to real files larger than 10 MiB. */
    public boolean kamigramHasActiveLargeDownloads() {
        for (FileLoadOperation operation : loadOperationPaths.values()) {
            if (operation != null && operation.kamigramIsActive()
                && !operation.isPreloadVideoOperation() && operation.kamigramIsLargeDownload()) {
                return true;
            }
        }
        return false;
    }
""",
    )


def patch_queue_limit():
    rel = "messenger/FileLoaderPriorityQueue.java"
    marker = "KAMIGRAM_MAX_PARALLEL_DOWNLOADS_R83"
    text = read(rel)
    if marker in text:
        return
    pattern = r"        int max = type == TYPE_LARGE \?[^\n]*;"
    replacement = "        int max = org.telegram.messenger.kamigram.KamiGramDownloadRecovery.downloadParallelLimit(6); /* %s */" % marker
    updated, count = re.subn(pattern, replacement, text, count=1)
    if count != 1:
        raise RuntimeError("%s: queue max line not found for %s" % (rel, marker))
    write(rel, updated)
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

    permission_lines = (
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n',
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />\n',
    )
    permission_block = "".join(line for line in permission_lines if line not in text)
    if permission_block:
        app_start = text.find("<application")
        if app_start < 0:
            raise RuntimeError("AndroidManifest.xml: application start not found")
        text = text[:app_start] + permission_block + text[app_start:]

    if marker in text:
        io.open(manifest, "w", encoding="utf-8").write(text)
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
    DONE.append("KAMIGRAM_DOWNLOAD_SERVICE_PERMISSIONS")


def main():
    try:
        patch_file_load_operation()
        patch_file_loader()
        patch_queue_limit()
        patch_connections_manager()
        patch_manifest()
    except Exception as exc:
        print("download resilience: %s" % exc, file=sys.stderr)
        return 1
    print("download resilience: %d patches" % len(DONE))
    for marker in DONE:
        print("  ✓ %s" % marker)
    return 0


if __name__ == "__main__":
    sys.exit(main())
