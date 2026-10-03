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

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Sakura: локальный прокси (KAMIGRAM_WS_PROXY_R131).
 *
 * Это нативный MTProto-прокси, а не SOCKS: клиент подключается к
 * 127.0.0.1:1443 как к обычному MTProto-прокси с dd-секретом, а сам прокси
 * несёт соединения к датацентрам поверх WebSocket/WSS-транспорта — там, где
 * сырой MTProto режется, такой трафик проходит.
 *
 * Сервис живёт в фоне: foreground-уведомление + partial wakelock с
 * периодическим обновлением, повторная доставка intent системой
 * (START_REDELIVER_INTENT) и keep-alive при закрытии приложения. Включается
 * одним тумблером в Центре и сам активирует маршрут в Telegram, как только
 * приёмник поднялся.
 */
public class KamiGramWsProxy extends Service {

    /* KAMIGRAM_WS_PROXY_R131: native MTProto-over-WebSocket proxy. */

    public static final String BIND_IP = "127.0.0.1";
    public static final int BASE_PORT = 1443;

    public static final String ACTION_START = "sakura.localproxy.START";
    public static final String ACTION_STOP = "sakura.localproxy.STOP";
    public static final String ACTION_RESTART = "sakura.localproxy.RESTART";
    public static final String EXTRA_BIND_IP = "EXTRA_BIND_IP";
    public static final String EXTRA_PORT = "EXTRA_PORT";
    public static final String EXTRA_IPS = "EXTRA_IPS";
    public static final String EXTRA_POOL_SIZE = "EXTRA_POOL_SIZE";
    public static final String EXTRA_CFPROXY_ENABLED = "EXTRA_CFPROXY_ENABLED";
    public static final String EXTRA_CFPROXY_PRIORITY = "EXTRA_CFPROXY_PRIORITY";
    public static final String EXTRA_CFPROXY_DOMAIN = "EXTRA_CFPROXY_DOMAIN";
    public static final String EXTRA_SECRET_KEY = "EXTRA_SECRET_KEY";

    private static final String LIB_NAME = "tgwsproxy";
    private static final String KEY_SECRET = "kamigram_ws_proxy_secret";

    /* Значения по умолчанию: пул из четырёх предварительных соединений,
       WS-фронтинг включён и в приоритете, датацентры определяются сами. */
    private static final int POOL_SIZE = 4;
    private static final boolean CF_ENABLED = true;
    private static final boolean CF_PRIORITY = true;
    private static final int VERBOSE = 1;

    private static final int NOTIFICATION_ID = 39770;
    private static final String CHANNEL_ID = "sakura_ws_proxy";

    private static final long WAKELOCK_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final long WAKELOCK_REFRESH_MS = 25L * 60L * 1000L;
    private static final long STATS_UPDATE_MS = 3000L;
    private static final long NOTIFICATION_MIN_UPDATE_MS = 3000L;
    private static final long NATIVE_STOP_WAIT_MS = 3000L;
    private static final long RESTART_DELAY_MS = 350L;

    private static final String TEXT_STARTING = "Запуск прокси…";
    private static final String TEXT_RUNNING = "Прокси работает";
    private static final String TEXT_RESTARTING = "Перезапуск прокси…";
    private static final String TEXT_STOPPING = "Остановка прокси…";

    private static volatile NativeLibrary library;
    private static volatile boolean libraryBroken;

    private static volatile boolean running;
    private static volatile boolean verifiedRunning;
    private static volatile boolean suppressConfigSync;

    private PowerManager.WakeLock wakeLock;
    private Thread statsThread;
    private volatile boolean stopInProgress;
    private long notificationStartedAtMs;
    private String lastNotificationContent = "";
    private long lastNotificationAtMs;

    /* Параметры последнего запуска: нужны, чтобы система могла поднять прокси
       заново после убийства процесса. */
    private String lastBindIp = BIND_IP;
    private int lastPort = BASE_PORT;
    private String lastIps = "";
    private int lastPoolSize = POOL_SIZE;
    private boolean lastCfEnabled = CF_ENABLED;
    private boolean lastCfPriority = CF_PRIORITY;
    private String lastCfDomain = "";
    private String lastSecretKey = "";

    // ------------------------------------------------------------------
    // Нативная библиотека
    // ------------------------------------------------------------------

    private static NativeLibrary library() {
        final NativeLibrary local = library;
        if (local != null) {
            return local;
        }
        if (libraryBroken) {
            return null;
        }
        try {
            final NativeLibrary loaded = NativeLibrary.getInstance(LIB_NAME);
            library = loaded;
            return loaded;
        } catch (Throwable throwable) {
            libraryBroken = true;
            KamiGramLog.e(throwable);
            return null;
        }
    }

    public static boolean isNativeAvailable() {
        return library() != null;
    }

    private static Function function(String symbol) {
        final NativeLibrary local = library();
        if (local == null) {
            return null;
        }
        try {
            return local.getFunction(symbol);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            return null;
        }
    }

    private static int nativeStart(String host, int port, String dcIps, String secret, int verbose) {
        final Function start = function("StartProxy");
        if (start == null) {
            return -100;
        }
        return start.invokeInt(new Object[]{host, port, dcIps, secret, verbose});
    }

    private static int nativeStop() {
        final Function stop = function("StopProxy");
        if (stop == null) {
            return -100;
        }
        return stop.invokeInt(new Object[0]);
    }

    private static void nativeSetPoolSize(int size) {
        final Function setter = function("SetPoolSize");
        if (setter != null) {
            setter.invokeVoid(new Object[]{size});
        }
    }

    private static void nativeSetSecret(String secret) {
        final Function setter = function("SetSecret");
        if (setter != null && secret != null && secret.length() == 32) {
            setter.invokeVoid(new Object[]{secret});
        }
    }

    private static void nativeSetCfProxyCacheDir(String dir) {
        final Function setter = function("SetCfProxyCacheDir");
        if (setter != null) {
            setter.invokeVoid(new Object[]{dir});
        }
    }

    private static void nativeSetCfProxyConfig(boolean enabled, boolean priority, String domain) {
        final Function setter = function("SetCfProxyConfig");
        if (setter != null) {
            setter.invokeVoid(new Object[]{enabled ? 1 : 0, priority ? 1 : 0, domain == null ? "" : domain});
        }
    }

    private static String nativeString(String symbol) {
        final Function getter = function(symbol);
        if (getter == null) {
            return null;
        }
        Pointer pointer = null;
        try {
            pointer = getter.invokePointer(new Object[0]);
            if (pointer == null) {
                return null;
            }
            return pointer.getString(0);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            return null;
        } finally {
            if (pointer != null) {
                final Function free = function("FreeString");
                if (free != null) {
                    try {
                        free.invokeVoid(new Object[]{pointer});
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Секрет и ссылка маршрута
    // ------------------------------------------------------------------

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

    private static boolean isValidSecret(String value) {
        if (value == null || value.length() != 32) {
            return false;
        }
        for (int a = 0; a < 32; a++) {
            final char ch = Character.toLowerCase(value.charAt(a));
            final boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** Секрет прокси: создаётся один раз и хранится в настройках Sakura. */
    public static String secret(Context context) {
        try {
            String value = KamiGramConfig.getStringValue(KEY_SECRET, "");
            if (isValidSecret(value)) {
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

    /** Секрет с префиксом — ровно тот, который ждёт клиент MTProto. */
    public static String secretWithPrefix(Context context) {
        final String fromNative = nativeString("GetSecretWithPrefix");
        if (fromNative != null && fromNative.length() >= 34) {
            return fromNative;
        }
        return "dd" + secret(context);
    }

    /** Ссылка локального MTProto-прокси для активации в Telegram. */
    public static String proxyLink(Context context) {
        return "https://t.me/proxy?server=" + BIND_IP
            + "&port=" + BASE_PORT
            + "&secret=" + secretWithPrefix(context);
    }

    // ------------------------------------------------------------------
    // Публичное управление
    // ------------------------------------------------------------------

    public static boolean isRunning() {
        return running;
    }

    public static boolean isVerifiedRunning() {
        return verifiedRunning;
    }

    public static int port() {
        return BASE_PORT;
    }

    public static void ensureStarted(Context context) {
        if (context == null) {
            return;
        }
        try {
            final Intent intent = new Intent(context, KamiGramWsProxy.class);
            intent.setAction(ACTION_START);
            intent.putExtra(EXTRA_BIND_IP, BIND_IP);
            intent.putExtra(EXTRA_PORT, BASE_PORT);
            /* Пустой список = датацентры определяются автоматически. */
            intent.putExtra(EXTRA_IPS, "");
            intent.putExtra(EXTRA_POOL_SIZE, POOL_SIZE);
            intent.putExtra(EXTRA_CFPROXY_ENABLED, CF_ENABLED);
            intent.putExtra(EXTRA_CFPROXY_PRIORITY, CF_PRIORITY);
            intent.putExtra(EXTRA_CFPROXY_DOMAIN, "");
            intent.putExtra(EXTRA_SECRET_KEY, secret(context));
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        try {
            context.stopService(new Intent(context, KamiGramWsProxy.class));
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Поднять прокси заново при старте приложения, если тумблер включён. */
    public static void restoreIfEnabled() {
        try {
            final Context context = ApplicationLoader.applicationContext;
            if (context == null || running) {
                return;
            }
            if (!KamiGramConfig.value(KamiGramConfig.KEY_WS_PROXY)) {
                return;
            }
            ensureStarted(context);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------
    // Сервис
    // ------------------------------------------------------------------

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        try {
            if (ACTION_STOP.equals(action)) {
                stopProxy();
                syncToggleOff();
                return START_REDELIVER_INTENT;
            }
            if (ACTION_RESTART.equals(action)) {
                restartProxy();
                return START_REDELIVER_INTENT;
            }
            if (intent == null || action == null) {
                /* Система подняла сервис заново после убийства процесса. */
                if (lastPort > 0 && lastSecretKey.length() > 0) {
                    startProxy(lastBindIp, lastPort, lastIps, lastPoolSize,
                        lastCfEnabled, lastCfPriority, lastCfDomain, lastSecretKey);
                } else {
                    stopSelf();
                }
                return START_REDELIVER_INTENT;
            }
            String bindIp = intent.getStringExtra(EXTRA_BIND_IP);
            if (bindIp == null || bindIp.length() == 0) {
                bindIp = BIND_IP;
            }
            String ips = intent.getStringExtra(EXTRA_IPS);
            if (ips == null) {
                ips = "";
            }
            String domain = intent.getStringExtra(EXTRA_CFPROXY_DOMAIN);
            if (domain == null) {
                domain = "";
            }
            String secretKey = intent.getStringExtra(EXTRA_SECRET_KEY);
            if (!isValidSecret(secretKey)) {
                secretKey = secret(this);
            }
            startProxy(bindIp,
                intent.getIntExtra(EXTRA_PORT, BASE_PORT),
                ips,
                intent.getIntExtra(EXTRA_POOL_SIZE, POOL_SIZE),
                intent.getBooleanExtra(EXTRA_CFPROXY_ENABLED, CF_ENABLED),
                intent.getBooleanExtra(EXTRA_CFPROXY_PRIORITY, CF_PRIORITY),
                domain,
                secretKey);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            stopProxy();
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        /* stopWithTask="false": прокси продолжает работать в фоне. */
    }

    @Override
    public void onDestroy() {
        stopStatsLoop();
        releaseWakeLock();
        running = false;
        verifiedRunning = false;
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

    private boolean isPortAvailable(String bindIp, int port) {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName(bindIp), port));
            return true;
        } catch (Throwable throwable) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private void startProxy(final String bindIp, final int port, final String ips, final int poolSize,
                            final boolean cfEnabled, final boolean cfPriority, final String cfDomain,
                            final String secretKey) {
        if (running || stopInProgress) {
            return;
        }
        verifiedRunning = false;

        lastBindIp = bindIp;
        lastPort = port;
        lastIps = ips;
        lastPoolSize = poolSize;
        lastCfEnabled = cfEnabled;
        lastCfPriority = cfPriority;
        lastCfDomain = cfDomain;
        lastSecretKey = secretKey;

        notificationStartedAtMs = System.currentTimeMillis();
        lastNotificationContent = TEXT_STARTING;
        lastNotificationAtMs = notificationStartedAtMs;

        boolean foreground = false;
        try {
            final Notification notification = createNotification(TEXT_STARTING);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foreground = true;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        if (!foreground) {
            /* Система не позволила поднять foreground-сервис (например, процесс
               стартовал в фоне). Сервис нельзя оставлять запущенным без
               уведомления — система убьёт процесс по таймауту. */
            running = false;
            verifiedRunning = false;
            stopSelf();
            return;
        }

        acquireWakeLock();
        stopInProgress = false;

        final Thread starter = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isPortAvailable(bindIp, port)) {
                    final String text = "Порт " + port + " занят";
                    updateNotification(text, true);
                    stopProxy();
                    return;
                }
                try {
                    nativeSetPoolSize(poolSize);
                    nativeSetSecret(secretKey);
                    nativeSetCfProxyCacheDir(getCacheDir().getAbsolutePath());
                    nativeSetCfProxyConfig(cfEnabled, cfPriority, cfDomain);
                    final int result = nativeStart(bindIp, port, ips, secretKey, VERBOSE);
                    if (result == 0) {
                        if (!stopInProgress) {
                            running = true;
                            verifiedRunning = true;
                            updateNotification(TEXT_RUNNING, true);
                            activateRoute();
                        }
                    } else {
                        updateNotification("Ошибка запуска (код: " + result + ")", true);
                        sleep(3000L);
                        stopProxy();
                    }
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                    final String message = throwable.getMessage();
                    updateNotification("Ошибка: " + (message == null ? throwable.getClass().getSimpleName() : message), true);
                    sleep(3000L);
                    stopProxy();
                }
            }
        }, "SakuraProxyStart");
        starter.setDaemon(true);
        starter.start();

        startStatsLoop();
    }

    private void restartProxy() {
        if (lastPort <= 0 || lastSecretKey.length() == 0) {
            return;
        }
        final String bindIp = lastBindIp;
        final int port = lastPort;
        final String ips = lastIps;
        final int poolSize = lastPoolSize;
        final boolean cfEnabled = lastCfEnabled;
        final boolean cfPriority = lastCfPriority;
        final String cfDomain = lastCfDomain;
        final String secretKey = lastSecretKey;

        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                updateNotification(TEXT_RESTARTING, true);
                stopStatsLoop();
                requestNativeStop();
                releaseWakeLock();
                running = false;
                verifiedRunning = false;
                sleep(RESTART_DELAY_MS);
                stopInProgress = false;
                startProxy(bindIp, port, ips, poolSize, cfEnabled, cfPriority, cfDomain, secretKey);
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
        updateNotification(TEXT_STOPPING, true);
        stopStatsLoop();
        requestNativeStop();
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

    /** Остановка нативной части отдельным потоком: она может занять до пары секунд. */
    private void requestNativeStop() {
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    nativeStop();
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                } finally {
                    latch.countDown();
                }
            }
        }, "SakuraProxyStop");
        thread.setDaemon(true);
        thread.start();
        try {
            latch.await(NATIVE_STOP_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignore) {
        }
    }

    /** Маршрут активируется только после успешного bind, чтобы не рвать связь. */
    private void activateRoute() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Context context = getApplicationContext();
                    KamiGramProxyPower.addAndActivate(proxyLink(context), context);
                } catch (Throwable throwable) {
                    KamiGramLog.e(throwable);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Wakelock
    // ------------------------------------------------------------------

    private void acquireWakeLock() {
        try {
            final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager == null) {
                return;
            }
            final PowerManager.WakeLock lock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sakura::LocalProxy");
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

    // ------------------------------------------------------------------
    // Статистика и уведомление
    // ------------------------------------------------------------------

    private void startStatsLoop() {
        stopStatsLoop();
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long lastRefresh = SystemClock.elapsedRealtime();
                while (running && !stopInProgress) {
                    sleep(STATS_UPDATE_MS);
                    if (!running || stopInProgress) {
                        return;
                    }
                    if (SystemClock.elapsedRealtime() - lastRefresh >= WAKELOCK_REFRESH_MS) {
                        lastRefresh = SystemClock.elapsedRealtime();
                        refreshWakeLock();
                    }
                    try {
                        final String stats = nativeString("GetStats");
                        if (stats == null) {
                            continue;
                        }
                        final double total = parseHumanBytes(extractStat(stats, "up="))
                            + parseHumanBytes(extractStat(stats, "down="));
                        int active = 0;
                        try {
                            active = Integer.parseInt(extractStat(stats, "active="));
                        } catch (Throwable ignore) {
                        }
                        updateNotification(String.format(Locale.US, "Трафик: %s · %d сесс.",
                            formatBytes(total), active), false);
                    } catch (Throwable ignore) {
                    }
                }
            }
        }, "SakuraProxyStats");
        thread.setDaemon(true);
        statsThread = thread;
        thread.start();
    }

    private void stopStatsLoop() {
        final Thread thread = statsThread;
        statsThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }

    private static String extractStat(String stats, String key) {
        final int index = stats.indexOf(key);
        if (index == -1) {
            return "0B";
        }
        final int start = index + key.length();
        final int end = stats.indexOf(' ', start);
        return end == -1 ? stats.substring(start) : stats.substring(start, end);
    }

    private static double parseHumanBytes(String value) {
        double number;
        try {
            number = Double.parseDouble(value.replaceAll("[^0-9.]", ""));
        } catch (Throwable ignore) {
            return 0d;
        }
        if (value.endsWith("TB")) {
            return number * 1024d * 1024d * 1024d * 1024d;
        }
        if (value.endsWith("GB")) {
            return number * 1024d * 1024d * 1024d;
        }
        if (value.endsWith("MB")) {
            return number * 1024d * 1024d;
        }
        if (value.endsWith("KB")) {
            return number * 1024d;
        }
        return number;
    }

    private static String formatBytes(double bytes) {
        if (bytes < 1024d) {
            return String.format(Locale.US, "%.0fB", bytes);
        }
        if (bytes < 1024d * 1024d) {
            return String.format(Locale.US, "%.1fKB", bytes / 1024d);
        }
        if (bytes < 1024d * 1024d * 1024d) {
            return String.format(Locale.US, "%.1fMB", bytes / (1024d * 1024d));
        }
        return String.format(Locale.US, "%.2fGB", bytes / (1024d * 1024d * 1024d));
    }

    private void updateNotification(String content, boolean force) {
        if (content == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (!force) {
            if (content.equals(lastNotificationContent)) {
                return;
            }
            if (lastNotificationAtMs != 0L && now - lastNotificationAtMs < NOTIFICATION_MIN_UPDATE_MS) {
                return;
            }
        }
        lastNotificationContent = content;
        lastNotificationAtMs = now;
        try {
            final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification(content));
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private void createNotificationChannel() {
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
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private Notification createNotification(String content) {
        final int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent contentIntent = null;
        try {
            final Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                contentIntent = PendingIntent.getActivity(this, 1, launch, pendingFlags);
            }
        } catch (Throwable ignore) {
        }

        PendingIntent restartIntent = null;
        PendingIntent stopIntent = null;
        try {
            final Intent restart = new Intent(this, KamiGramWsProxy.class);
            restart.setAction(ACTION_RESTART);
            restartIntent = PendingIntent.getService(this, 2, restart, pendingFlags);

            final Intent stop = new Intent(this, KamiGramWsProxy.class);
            stop.setAction(ACTION_STOP);
            stopIntent = PendingIntent.getService(this, 0, stop, pendingFlags);
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
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .setWhen(notificationStartedAtMs > 0L ? notificationStartedAtMs : System.currentTimeMillis())
            .setShowWhen(false);
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }
        if (restartIntent != null) {
            builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_popup_sync, "Перезапуск", restartIntent).build());
        }
        if (stopIntent != null) {
            builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stopIntent).build());
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }
}
