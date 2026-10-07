package org.telegram.messenger.kamigram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.io.File;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Локальный прокси (KAMIGRAM_LOCAL_PROXY_R132).
 *
 * Это MTProto-прокси на 127.0.0.1, трафик к датацентрам идёт через Cloudflare
 * по ядру из собственного исходника (список фронтов зашит там же). Не SOCKS
 * и не чужой транспорт. Сервис живёт в фоне с уведомлением и wakelock.
 * Включается одним тумблером и поднимается до входа в аккаунт.
 * Пока он жив — у него приоритет. Если он не поднялся или умер, маршрут
 * отдаётся встроенному SakuProxy.
 */
public class KamiGramWsProxy extends Service {

    /* KAMIGRAM_LOCAL_PROXY_R132 */
    public static final String BIND_IP = "127.0.0.1";
    public static final int BASE_PORT = 1443;

    public static final String ACTION_START = "sakura.localproxy.START";
    public static final String ACTION_STOP = "sakura.localproxy.STOP";
    public static final String ACTION_RESTART = "sakura.localproxy.RESTART";

    private static final String KEY_SECRET = "kamigram_local_proxy_secret";
    private static final int POOL_SIZE = 8;
    private static final int NOTIFICATION_ID = 39770;
    private static final String CHANNEL_ID = "sakura_local_proxy";
    private static final long WAKELOCK_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final long WAKELOCK_REFRESH_MS = 25L * 60L * 1000L;
    private static final long MONITOR_MS = 3000L;
    private static final long FALLBACK_AFTER_MS = 8000L;

    private static volatile boolean running;
    private static volatile boolean verifiedRunning;
    private static volatile boolean suppressConfigSync;
    private static volatile boolean stopRequested;
    private static volatile KamiGramWsProxy instance;
    private static volatile long downSince;

    private PowerManager.WakeLock wakeLock;
    private Thread monitorThread;
    private volatile boolean stopInProgress;
    private String lastSecret = "";
    private long notificationStartedAt;

    public static boolean isRunning() {
        return running;
    }

    public static boolean isVerifiedRunning() {
        return verifiedRunning && coreAlive();
    }

    /** Локальный прокси сейчас живой и должен держать маршрут. */
    public static boolean suppressingConfigSync() {
        return suppressConfigSync;
    }

    public static boolean holdsPriority() {
        try {
            return KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY) && isVerifiedRunning();
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean isLocalRoute(SharedConfig.ProxyInfo info) {
        try {
            return info != null && info.settings != null && BIND_IP.equals(info.settings.getAddress());
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static int port() {
        return BASE_PORT;
    }

    private static boolean coreAlive() {
        try {
            return KamiGramProxyCore.available() && KamiGramProxyCore.nativeAlive();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static String generateSecret() {
        final byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        final StringBuilder builder = new StringBuilder(32);
        for (byte value : bytes) {
            final String hex = Integer.toHexString(value & 0xff);
            builder.append(hex.length() == 1 ? "0" + hex : hex);
        }
        return builder.toString();
    }

    private static boolean validSecret(String value) {
        if (value == null || value.length() != 32) {
            return false;
        }
        for (int a = 0; a < 32; a++) {
            final char ch = Character.toLowerCase(value.charAt(a));
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    public static String secret(Context context) {
        try {
            String value = KamiGramConfig.getStringValue(KEY_SECRET, "");
            if (validSecret(value)) {
                return value;
            }
            value = generateSecret();
            KamiGramConfig.setStringValue(KEY_SECRET, value);
            return value;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            return generateSecret();
        }
    }

    public static String proxyLink(Context context) {
        return "https://t.me/proxy?server=" + BIND_IP
            + "&port=" + BASE_PORT
            + "&secret=dd" + secret(context);
    }

    public static void ensureStarted(Context context) {
        if (context == null || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
            return;
        }
        stopRequested = false;
        final KamiGramWsProxy service = instance;
        if (service != null) {
            service.stopInProgress = false;
        }
        try {
            final Intent intent = new Intent(context, KamiGramWsProxy.class);
            intent.setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            if (KamiGramConfig.builtinProxy()) {
                KamiGramBuiltinProxy.engageFallback(context);
            }
        }
    }

    /** Тумблер выключен: ядро останавливается и локальный маршрут снимается. Сервис сам не поднимается. */
    public static void stop(Context context) {
        stopRequested = true;
        verifiedRunning = false;
        running = false;
        final Thread halt = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (KamiGramProxyCore.available()) {
                        KamiGramProxyCore.nativeStop();
                    }
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                }
            }
        }, "SakuraProxyStop");
        halt.setDaemon(true);
        halt.start();
        releaseLocalRoute();
        if (context == null) {
            return;
        }
        try {
            final Intent intent = new Intent(context, KamiGramWsProxy.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        try {
            context.stopService(new Intent(context, KamiGramWsProxy.class));
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Снимает 127.0.0.1, если он сейчас выбран. Чужой прокси не трогает. */
    public static void releaseLocalRoute() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isLocalRoute(SharedConfig.currentProxy)) {
                        return;
                    }
                    SharedConfig.currentProxy = null;
                    SharedConfig.saveProxyList();
                    ConnectionsManager.setProxySettings(false, null);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    if (KamiGramConfig.builtinProxy() && !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                        KamiGramBuiltinProxy.engageFallback(ApplicationLoader.applicationContext);
                    }
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                }
            }
        });
    }

    /** До входа в аккаунт: тумблер включён по умолчанию, сервис поднимается сразу. */
    public static void restoreIfEnabled() {
        try {
            final Context context = ApplicationLoader.applicationContext;
            if (context == null || running) {
                return;
            }
            if (!KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                return;
            }
            if (!KamiGramProxyCore.available()) {
                if (KamiGramConfig.builtinProxy()) {
                    KamiGramBuiltinProxy.engageFallback(context);
                }
                return;
            }
            ensureStarted(context);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        try {
            if (ACTION_STOP.equals(action)) {
                stopRequested = true;
                syncToggleOff();
                stopProxy();
                return START_NOT_STICKY;
            }
            if (stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                stopProxy();
                return START_NOT_STICKY;
            }
            if (ACTION_RESTART.equals(action)) {
                stopInProgress = false;
                restartProxy();
                return START_STICKY;
            }
            if (!KamiGramProxyCore.available()) {
                failAndFallback("Локальный прокси недоступен");
                return START_NOT_STICKY;
            }
            stopInProgress = false;
            startProxy(secret(this));
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            failAndFallback("Ошибка запуска");
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopMonitor();
        releaseWakeLock();
        try {
            if (KamiGramProxyCore.available()) {
                KamiGramProxyCore.nativeStop();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        running = false;
        verifiedRunning = false;
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    private void syncToggleOff() {
        if (suppressConfigSync) {
            return;
        }
        suppressConfigSync = true;
        try {
            KamiGramConfig.set(KamiGramConfig.KEY_WS_PROXY, false);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        } finally {
            suppressConfigSync = false;
        }
    }

    private void failAndFallback(String text) {
        updateNotification(text, true);
        running = false;
        verifiedRunning = false;
        downSince = 0L;
        try {
            KamiGramBuiltinProxy.engageFallback(this);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        stopProxy();
    }

    private void startProxy(final String secretKey) {
        if (stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
            stopProxy();
            return;
        }
        if (running || stopInProgress) {
            return;
        }
        if (!validSecret(secretKey)) {
            failAndFallback("Ошибка запуска");
            return;
        }
        lastSecret = secretKey;
        verifiedRunning = false;
        downSince = 0L;
        notificationStartedAt = System.currentTimeMillis();

        boolean foreground = false;
        try {
            final Notification notification = createNotification("Запуск прокси…");
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foreground = true;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        if (!foreground) {
            stopSelf();
            KamiGramBuiltinProxy.engageFallback(this);
            return;
        }
        acquireWakeLock();
        stopInProgress = false;

        final File cache = new File(getCacheDir(), "local-proxy");
        if (!cache.exists()) {
            cache.mkdirs();
        }
        final String cachePath = cache.getAbsolutePath();
        final Thread starter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                        KamiGramProxyCore.nativeStop();
                        return;
                    }
                    if (coreAlive()) {
                        KamiGramProxyCore.nativeStop();
                    }
                    KamiGramProxyCore.nativeConfigure(POOL_SIZE, cachePath, 1, "");
                    final int code = KamiGramProxyCore.nativeStart(BIND_IP, BASE_PORT, secretKey);
                    if (stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                        KamiGramProxyCore.nativeStop();
                        running = false;
                        verifiedRunning = false;
                        return;
                    }
                    if (code == 0 && !stopInProgress) {
                        running = true;
                        verifiedRunning = true;
                        downSince = 0L;
                        updateNotification("Прокси работает", true);
                        activateRoute();
                    } else if (!stopInProgress) {
                        failAndFallback("Ошибка запуска (код: " + code + ")");
                    }
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                    if (!stopInProgress) {
                        failAndFallback("Ошибка запуска");
                    }
                }
            }
        }, "SakuraProxyStart");
        starter.setDaemon(true);
        starter.start();
        startMonitor();
    }

    private void restartProxy() {
        final String secretKey = lastSecret.length() == 32 ? lastSecret : secret(this);
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                updateNotification("Перезапуск прокси…", true);
                stopMonitor();
                try {
                    KamiGramProxyCore.nativeStop();
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                }
                running = false;
                verifiedRunning = false;
                stopInProgress = false;
                try {
                    Thread.sleep(350L);
                } catch (InterruptedException ignore) {
                }
                startProxy(secretKey);
            }
        }, "SakuraProxyRestart");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopProxy() {
        if (stopInProgress) {
            return;
        }
        stopInProgress = true;
        stopMonitor();
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (KamiGramProxyCore.available()) {
                        KamiGramProxyCore.nativeStop();
                    }
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                }
            }
        }, "SakuraProxyStop");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(2500L);
        } catch (InterruptedException ignore) {
        }
        releaseWakeLock();
        running = false;
        verifiedRunning = false;
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        stopSelf();
    }

    private void activateRoute() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                        return;
                    }
                    KamiGramProxyPower.addAndActivate(proxyLink(getApplicationContext()), getApplicationContext());
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                }
            }
        });
    }

    private void startMonitor() {
        stopMonitor();
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long lastRefresh = SystemClock.elapsedRealtime();
                while (!stopInProgress && monitorThread == Thread.currentThread()) {
                    try {
                        Thread.sleep(MONITOR_MS);
                    } catch (InterruptedException interrupted) {
                        return;
                    }
                    if (stopInProgress || stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                        return;
                    }
                    if (SystemClock.elapsedRealtime() - lastRefresh >= WAKELOCK_REFRESH_MS) {
                        lastRefresh = SystemClock.elapsedRealtime();
                        refreshWakeLock();
                    }
                    watchHealth();
                }
            }
        }, "SakuraProxyMonitor");
        thread.setDaemon(true);
        monitorThread = thread;
        thread.start();
    }

    private void stopMonitor() {
        final Thread thread = monitorThread;
        monitorThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void watchHealth() {
        if (stopRequested || !KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
            return;
        }
        boolean up = false;
        long upBytes = 0L;
        long downBytes = 0L;
        int live = 0;
        try {
            up = coreAlive();
            if (up) {
                final int heal = KamiGramProxyCore.nativeHeal();
                live = KamiGramProxyCore.nativeLive();
                /* 2 = все фронты мертвы, 3 = нет сети. 0 = ещё не измерено, это не сбой. */
                if (heal == 2) {
                    up = false;
                }
                upBytes = KamiGramProxyCore.nativeBytesUp();
                downBytes = KamiGramProxyCore.nativeBytesDown();
            }
        } catch (Throwable throwable) {
            up = false;
        }
        if (up) {
            verifiedRunning = true;
            running = true;
            downSince = 0L;
            updateNotification(String.format(Locale.US, "Прокси работает · %s · %d",
                formatBytes(upBytes + downBytes), live), false);
            if (!isLocalRoute(SharedConfig.currentProxy)) {
                activateRoute();
            }
            return;
        }
        verifiedRunning = false;
        if (downSince == 0L) {
            downSince = SystemClock.elapsedRealtime();
            return;
        }
        if (SystemClock.elapsedRealtime() - downSince >= FALLBACK_AFTER_MS) {
            updateNotification("Прокси не отвечает, включён резерв", true);
            KamiGramBuiltinProxy.engageFallback(this);
        }
    }

    private static String formatBytes(double bytes) {
        if (bytes < 1024d) {
            return String.format(Locale.US, "%.0f Б", bytes);
        }
        if (bytes < 1024d * 1024d) {
            return String.format(Locale.US, "%.1f КБ", bytes / 1024d);
        }
        if (bytes < 1024d * 1024d * 1024d) {
            return String.format(Locale.US, "%.1f МБ", bytes / (1024d * 1024d));
        }
        return String.format(Locale.US, "%.2f ГБ", bytes / (1024d * 1024d * 1024d));
    }

    private void acquireWakeLock() {
        try {
            final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager == null) {
                return;
            }
            final PowerManager.WakeLock lock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sakura::LocalProxy");
            lock.setReferenceCounted(false);
            lock.acquire(WAKELOCK_TIMEOUT_MS);
            wakeLock = lock;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private void refreshWakeLock() {
        try {
            final PowerManager.WakeLock lock = wakeLock;
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        acquireWakeLock();
    }

    private void releaseWakeLock() {
        try {
            final PowerManager.WakeLock lock = wakeLock;
            if (lock != null && lock.isHeld()) {
                lock.release();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        wakeLock = null;
    }

    private void updateNotification(String content, boolean force) {
        if (content == null) {
            return;
        }
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification(content));
            }
        } catch (Throwable throwable) {
            if (force) {
                KamiGramLog.e(throwable);
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        try {
            final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Локальный прокси", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Уведомление о работе локального прокси");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
            final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private Notification createNotification(String content) {
        final int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = null;
        PendingIntent restart = null;
        PendingIntent stop = null;
        try {
            final Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                open = PendingIntent.getActivity(this, 1, launch, flags);
            }
            restart = PendingIntent.getService(this, 2, new Intent(this, KamiGramWsProxy.class).setAction(ACTION_RESTART), flags);
            stop = PendingIntent.getService(this, 0, new Intent(this, KamiGramWsProxy.class).setAction(ACTION_STOP), flags);
        } catch (Throwable ignore) {
        }
        final Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        builder.setContentTitle("Локальный прокси")
            .setContentText(content)
            .setSmallIcon(getApplicationInfo().icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false);
        if (open != null) {
            builder.setContentIntent(open);
        }
        if (restart != null) {
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_popup_sync, "Перезапуск", restart).build());
        }
        if (stop != null) {
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stop).build());
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }
}
