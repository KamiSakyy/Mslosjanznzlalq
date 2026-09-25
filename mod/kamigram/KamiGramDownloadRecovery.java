package org.telegram.messenger.kamigram;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoadOperation;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.UserConfig;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps active downloads alive while Telegram reconnects through another proxy.
 * A proxy change is a transport event, not a cancellation: the operation keeps
 * its temporary file and resumes from the already written byte ranges.
 */
public final class KamiGramDownloadRecovery {
    private static final long SWITCH_GRACE_MS = 60_000L;
    private static final long STALL_AFTER_MS = 8_000L;
    private static final long MIN_STALL_SIZE = 1024L * 1024L;
    private static final long MIN_RATE_BYTES = 16L * 1024L;
    private static final int MAX_RETRIES_PER_SWITCH = 2;

    private static volatile long lastProxySwitchAt;
    private static final Map<FileLoadOperation, Integer> RETRIES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<FileLoadOperation, Sample> SAMPLES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private KamiGramDownloadRecovery() {
    }

    /** Called from Telegram's single proxy-setting choke point. */
    public static void onProxySwitch() {
        lastProxySwitchAt = SystemClock.elapsedRealtime();
        synchronized (RETRIES) {
            RETRIES.clear();
        }
        synchronized (SAMPLES) {
            SAMPLES.clear();
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                try {
                    FileLoader.getInstance(account).kamigramRebindActiveDownloads();
                } catch (Throwable ignore) {
                }
            }
        }, 240L);
    }

    /** A transient proxy failure must not remove the download from the queue. */
    public static boolean shouldRetry(FileLoadOperation operation, int reason) {
        if (operation == null || reason == 1 || reason == -1) {
            return false;
        }
        final long switchedAt = lastProxySwitchAt;
        if (switchedAt == 0L || SystemClock.elapsedRealtime() - switchedAt > SWITCH_GRACE_MS) {
            return false;
        }
        synchronized (RETRIES) {
            final Integer count = RETRIES.get(operation);
            if (count != null && count >= MAX_RETRIES_PER_SWITCH) {
                return false;
            }
            RETRIES.put(operation, count == null ? 1 : count + 1);
        }
        return true;
    }

    public static void onFinished(FileLoadOperation operation) {
        if (operation == null) {
            return;
        }
        RETRIES.remove(operation);
        SAMPLES.remove(operation);
    }

    /** Called for every real byte-progress callback. */
    public static void onProgress(FileLoadOperation operation, long loaded, long total) {
        if (operation == null || total < MIN_STALL_SIZE || loaded < 0L) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        final Sample sample;
        synchronized (SAMPLES) {
            sample = SAMPLES.get(operation);
            if (sample == null) {
                final Sample first = new Sample(loaded, now);
                SAMPLES.put(operation, first);
                scheduleWatchdog(operation, first, loaded);
                return;
            }
            if (loaded > sample.loaded) {
                final long deltaTime = Math.max(1L, now - sample.at);
                final long rate = (loaded - sample.loaded) * 1000L / deltaTime;
                sample.loaded = loaded;
                sample.at = now;
                sample.lastRate = rate;
                if (rate < MIN_RATE_BYTES) {
                    if (sample.slowSince == 0L) {
                        sample.slowSince = now;
                    }
                } else {
                    sample.slowSince = 0L;
                }
                sample.stalled = false;
                final long watchdog = ++sample.watchdog;
                scheduleWatchdog(operation, sample, loaded, watchdog);
            }
        }
    }

    private static void scheduleWatchdog(FileLoadOperation operation, Sample sample, long expectedLoaded) {
        scheduleWatchdog(operation, sample, expectedLoaded, ++sample.watchdog);
    }

    private static void scheduleWatchdog(FileLoadOperation operation, Sample sample, long expectedLoaded, long token) {
        AndroidUtilities.runOnUIThread(() -> {
            boolean slow = false;
            synchronized (SAMPLES) {
                final Sample current = SAMPLES.get(operation);
                if (current == null || current.watchdog != token || current.stalled
                    || current.loaded != expectedLoaded) {
                    return;
                }
                final long now = SystemClock.elapsedRealtime();
                final boolean noProgress = now - current.at >= STALL_AFTER_MS;
                final boolean lowRate = current.slowSince != 0L
                    && now - current.slowSince >= STALL_AFTER_MS;
                if (noProgress || lowRate) {
                    current.stalled = true;
                    slow = true;
                }
            }
            if (slow) {
                AndroidUtilities.runOnUIThread(() -> KamiGramProxyPower.onSlowDownload(operation), 0L);
            }
        }, STALL_AFTER_MS + 250L);
    }

    private static final class Sample {
        long loaded;
        long at;
        long lastRate;
        long slowSince;
        long watchdog;
        boolean stalled;

        Sample(long loaded, long at) {
            this.loaded = loaded;
            this.at = at;
        }
    }
}
