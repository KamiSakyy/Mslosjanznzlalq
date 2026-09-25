package org.telegram.messenger.kamigram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/**
 * Foreground lifetime for user-requested downloads. Telegram's FileLoader still
 * owns every byte and every retry; this service only keeps the process alive and
 * exposes one quiet progress notification while the app is in the background.
 */
public final class KamiGramDownloadService extends Service implements NotificationCenter.NotificationCenterDelegate {
    private static final String CHANNEL_ID = "kamigram_downloads";
    private static final int NOTIFICATION_ID = 0x4B4744;
    private static final long IDLE_GRACE_MS = 3500L;

    private static volatile KamiGramDownloadService instance;
    private boolean observing;
    private long lastLoaded;
    private long lastTotal;
    private long lastEventAt;

    public static void ensureStarted(Context context) {
        if (context == null) {
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
            // Android may reject a background start; the native download remains
            // valid and will be retried by Telegram's normal queue.
        }
    }

    public static void refresh(Context context) {
        ensureStarted(context);
    }

    public static void reportProgress(long loaded, long total) {
        final KamiGramDownloadService service = instance;
        if (service != null) {
            service.lastLoaded = loaded;
            service.lastTotal = total;
            service.lastEventAt = android.os.SystemClock.elapsedRealtime();
            service.updateNotification();
        }
    }

    public static void reportStateChanged() {
        final KamiGramDownloadService service = instance;
        if (service != null) {
            service.lastEventAt = android.os.SystemClock.elapsedRealtime();
            service.updateNotification();
            service.scheduleIdleCheck();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {
            instance = null;
            stopSelf();
            return;
        }
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
        lastEventAt = android.os.SystemClock.elapsedRealtime();
        updateNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastEventAt = android.os.SystemClock.elapsedRealtime();
        updateNotification();
        scheduleIdleCheck();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (observing) {
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
        }
        observing = false;
        instance = null;
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
                lastLoaded = ((Long) args[1]);
                lastTotal = ((Long) args[2]);
            } catch (Throwable ignore) {
            }
        }
        lastEventAt = android.os.SystemClock.elapsedRealtime();
        updateNotification();
        scheduleIdleCheck();
    }

    private void scheduleIdleCheck() {
        final long event = lastEventAt;
        android.os.Handler handler = new android.os.Handler(getMainLooper());
        handler.postDelayed(() -> {
            if (event == lastEventAt && !hasActiveDownloads()) {
                stopSelf();
            }
        }, IDLE_GRACE_MS);
    }

    private boolean hasActiveDownloads() {
        try {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                final DownloadController controller = AccountInstance.getInstance(account).getDownloadController();
                if (controller == null || controller.downloadingFiles == null) {
                    continue;
                }
                for (int i = 0; i < controller.downloadingFiles.size(); i++) {
                    final MessageObject message = controller.downloadingFiles.get(i);
                    if (message != null) {
                        if (message.getFileName() != null
                            && FileLoader.getInstance(account).isLoadingFile(message.getFileName())) {
                            return true;
                        }
                        // DownloadController keeps the row while it is queued;
                        // do not let the foreground lifetime expire before the
                        // native priority queue starts the operation.
                        if (message.getDocument() != null) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    private void updateNotification() {
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification());
            }
        } catch (Throwable ignore) {
        }
    }

    private Notification buildNotification() {
        final int progress;
        final boolean determinate = lastTotal > 0L;
        if (determinate) {
            progress = (int) Math.max(0L, Math.min(100L, lastLoaded * 100L / lastTotal));
        } else {
            progress = 0;
        }
        final String content = determinate
            ? "Скачивание · " + formatSize(lastLoaded) + " / " + formatSize(lastTotal) + " · " + progress + "%"
            : "Скачивание · ожидаю данные";
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Sakura")
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
