package org.telegram.messenger.kamigram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/**
 * Foreground lifetime for native FileLoader operations larger than 10 MiB.
 * FileLoader still owns every byte, retry, temp file, part range and resume
 * offset; this service only keeps Android's process alive and shows progress.
 */
public final class KamiGramDownloadService extends Service implements NotificationCenter.NotificationCenterDelegate {
    public static final long FOREGROUND_MIN_BYTES = 10L * 1024L * 1024L;
    private static final String CHANNEL_ID = "kamigram_downloads";
    private static final int NOTIFICATION_ID = 0x4B4744;
    private static final long IDLE_GRACE_MS = 1800L;
    private static final long NOTIFICATION_MIN_INTERVAL_MS = 500L;

    private static volatile KamiGramDownloadService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleStopRunnable = this::stopIfIdle;
    private boolean observing;
    private long lastLoaded;
    private long lastTotal;
    private long lastNotificationAt;
    private long lastNotifiedLoaded = -1L;

    /** Start is allowed only when the caller already knows the file is >10 MiB. */
    public static void ensureStarted(Context context) {
        ensureStartedForLargeDownload(context, FOREGROUND_MIN_BYTES + 1L);
    }

    public static void ensureStartedForLargeDownload(Context context, long size) {
        if (context == null || size <= FOREGROUND_MIN_BYTES) {
            return;
        }
        try {
            final Context app = context.getApplicationContext();
            final Intent intent = new Intent(app, KamiGramDownloadService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(app, intent);
            } else {
                app.startService(intent);
            }
        } catch (Throwable ignore) {
            // Android may reject a background start; native FileLoader remains valid.
        }
    }

    public static void reportProgress(long loaded, long total) {
        if (total <= FOREGROUND_MIN_BYTES) {
            return;
        }
        final KamiGramDownloadService service = instance;
        if (service != null) {
            service.lastLoaded = Math.max(0L, loaded);
            service.lastTotal = total;
            service.updateNotification(false);
            service.scheduleIdleCheck();
        }
    }

    public static void reportStateChanged() {
        final KamiGramDownloadService service = instance;
        if (service != null) {
            service.handleStateChanged();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        observe();
        if (!hasActiveLargeDownloads()) {
            stopSelf();
            return;
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {
            stopSelf();
            return;
        }
        updateNotification(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasActiveLargeDownloads()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        updateNotification(true);
        scheduleIdleCheck();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(idleStopRunnable);
        unobserve();
        removeNotification();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoadProgressChanged && args != null && args.length >= 3) {
            try {
                final Object loaded = args[1];
                final Object total = args[2];
                if (loaded instanceof Long && total instanceof Long) {
                    lastLoaded = (Long) loaded;
                    lastTotal = (Long) total;
                }
            } catch (Throwable ignore) {
            }
        }
        handleStateChanged();
    }

    private void observe() {
        observing = true;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            try {
                final NotificationCenter center = NotificationCenter.getInstance(account);
                center.addObserver(this, NotificationCenter.onDownloadingFilesChanged);
                center.addObserver(this, NotificationCenter.fileLoadProgressChanged);
                center.addObserver(this, NotificationCenter.fileLoaded);
                center.addObserver(this, NotificationCenter.fileLoadFailed);
            } catch (Throwable ignore) {
            }
        }
    }

    private void unobserve() {
        if (!observing) {
            return;
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            try {
                final NotificationCenter center = NotificationCenter.getInstance(account);
                center.removeObserver(this, NotificationCenter.onDownloadingFilesChanged);
                center.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
                center.removeObserver(this, NotificationCenter.fileLoaded);
                center.removeObserver(this, NotificationCenter.fileLoadFailed);
            } catch (Throwable ignore) {
            }
        }
        observing = false;
    }

    private boolean hasActiveLargeDownloads() {
        return KamiGramDownloadRecovery.hasActiveLargeDownloads();
    }

    private void handleStateChanged() {
        if (!hasActiveLargeDownloads()) {
            stopNow();
            return;
        }
        updateNotification(false);
        scheduleIdleCheck();
    }

    private void scheduleIdleCheck() {
        mainHandler.removeCallbacks(idleStopRunnable);
        mainHandler.postDelayed(idleStopRunnable, IDLE_GRACE_MS);
    }

    private void stopIfIdle() {
        if (!hasActiveLargeDownloads()) {
            stopNow();
        }
    }

    private void stopNow() {
        mainHandler.removeCallbacks(idleStopRunnable);
        removeNotification();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        stopSelf();
    }

    private void updateNotification(boolean force) {
        if (!hasActiveLargeDownloads()) {
            stopNow();
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        final boolean changed = lastTotal != 0L
            && (lastNotifiedLoaded < 0L || lastTotal == 0L
                || Math.abs(lastLoaded - lastNotifiedLoaded) * 100L / Math.max(1L, lastTotal) >= 1L);
        if (!force && now - lastNotificationAt < NOTIFICATION_MIN_INTERVAL_MS && !changed) {
            return;
        }
        lastNotificationAt = now;
        lastNotifiedLoaded = lastLoaded;
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification());
            }
        } catch (Throwable ignore) {
        }
    }

    private Notification buildNotification() {
        final boolean determinate = lastTotal > FOREGROUND_MIN_BYTES;
        final int progress = determinate
            ? (int) Math.max(0L, Math.min(100L, lastLoaded * 100L / Math.max(1L, lastTotal)))
            : 0;
        final String content = determinate
            ? "Скачивание · " + formatSize(lastLoaded) + " / " + formatSize(lastTotal) + " · " + progress + "%"
            : "Скачивание · ожидаю данные";
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("KamiGram")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false);
        if (determinate) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(100, 0, true);
        }
        return builder.build();
    }

    private void removeNotification() {
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (Throwable ignore) {
        }
    }

    private static String formatSize(long value) {
        if (value < 1024L) {
            return value + " B";
        }
        if (value < 1024L * 1024L) {
            return (value / 1024L) + " KB";
        }
        if (value < 1024L * 1024L * 1024L) {
            return (value / (1024L * 1024L)) + " MB";
        }
        return (value / (1024L * 1024L * 1024L)) + " GB";
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                final NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Скачивания", NotificationManager.IMPORTANCE_LOW);
                channel.setSound(null, null);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        } catch (Throwable ignore) {
        }
    }
}
