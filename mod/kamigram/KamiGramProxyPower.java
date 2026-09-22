package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/**
 * KamiGram: «мощный» прокси-движок.
 *
 * Чем отличается от встроенного переключателя Telegram (ProxyRotationController):
 * <ul>
 *   <li>реакция мгновенная, а не через 5-60 секунд: если прокси оборвался, мод
 *       переключается за ~1 секунду, а не за минуту;</li>
 *   <li>понимает разницу между «ещё подключаюсь к прокси» и «прокси молчит»
 *       (в этом случае переключение идёт сразу);</li>
 *   <li>сам держит базу живых прокси: каждый прокси периодически проверяется,
 *       живой помечается зелёным, мёртвый - серым, выбор идёт по пингу;</li>
 *   <li>если прокси всего один и он умер - мод мгновенно включает прямое
 *       подключение (чтобы интернет вообще работал), а когда прокси оживёт -
 *       включает его обратно;</li>
 *   <li>новая ссылка на прокси подключается сразу, без похода по настройкам.</li>
 * </ul>
 */
public final class KamiGramProxyPower implements NotificationCenter.NotificationCenterDelegate {

    /** Переключение после того, как прокси начал «зависать». */
    private static final long SWITCH_DELAY = 1200L;
    /** Если прокси не поднялся даже до этого состояния - он мёртв, реагируем сразу. */
    private static final long DEAD_DELAY = 250L;
    /** Через сколько секунд прокси считается «старым» и перепроверяется. */
    private static final long CHECK_FRESHNESS = 3 * 60 * 1000L;

    private static final KamiGramProxyPower INSTANCE = new KamiGramProxyPower();

    private boolean initialized;
    private boolean switching;
    private boolean singleProxyDropped;
    private long lastSwitchTime;

    private final Runnable switchRunnable = () -> {
        switching = false;
        switchToBest(null);
    };

    private KamiGramProxyPower() {
    }

    public static void init() {
        INSTANCE.initInternal();
    }

    private void initInternal() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.didUpdateConnectionState);
            }
            final NotificationCenter global = NotificationCenter.getGlobalInstance();
            global.addObserver(this, NotificationCenter.proxyCheckDone);
            global.addObserver(this, NotificationCenter.proxySettingsChanged);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ------------------------------------------------------------------ утилиты

    private static boolean enabled() {
        try {
            return SharedConfig.isProxyEnabled() && SharedConfig.proxyList != null && !SharedConfig.proxyList.isEmpty();
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean smartEnabled() {
        return KamiGramConfig.smartProxy() || KamiGramConfig.proxyFallback();
    }

    private static ArrayList<SharedConfig.ProxyInfo> alive(boolean onlyAvailable) {
        final ArrayList<SharedConfig.ProxyInfo> result = new ArrayList<>();
        if (SharedConfig.proxyList == null) {
            return result;
        }
        for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
            final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
            if (info == null || info.settings == null || !info.settings.isValid()) {
                continue;
            }
            if (onlyAvailable && (!info.available || info.ping <= 0)) {
                continue;
            }
            result.add(info);
        }
        return result;
    }

    /** Самый быстрый живой прокси, который не является текущим. */
    private SharedConfig.ProxyInfo pickBest() {
        final ArrayList<SharedConfig.ProxyInfo> list = alive(true);
        final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        Collections.sort(list, (o1, o2) -> Long.compare(o1.ping, o2.ping));
        for (int a = 0; a < list.size(); a++) {
            if (list.get(a) != current) {
                return list.get(a);
            }
        }
        // ни одного проверенного: берём непроверенный, чтобы не сидеть на мёртвом
        final ArrayList<SharedConfig.ProxyInfo> unknown = new ArrayList<>();
        for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
            final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
            if (info != null && info != current && info.availableCheckTime == 0) {
                unknown.add(info);
            }
        }
        return unknown.isEmpty() ? null : unknown.get(0);
    }

    /** Подключает конкретный прокси (мгновенно, без похода в настройки). */
    public static boolean activate(SharedConfig.ProxyInfo info, Context context, boolean silent) {
        if (info == null || info.settings == null) {
            return false;
        }
        try {
            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", true);
            info.settings.toSharedPreferences(editor);
            if (!info.settings.getSecret().isEmpty()) {
                editor.putBoolean("proxy_enabled_calls", false);
            }
            editor.apply();

            SharedConfig.currentProxy = info;
            SharedConfig.saveProxyList();

            ConnectionsManager.setProxySettings(true, info.settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
            INSTANCE.singleProxyDropped = false;

            if (!silent && context != null) {
                KamiGramUi.notify(context, info.settings.getAddress() + ":" + info.settings.getPort()
                    + " — подключено");
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Добавляет прокси из ссылки и сразу подключает его. */
    public static boolean addAndActivate(String link, Context context) {
        try {
            final org.telegram.proxy.ProxySettings settings =
                org.telegram.proxy.ProxySettings.fromUri(android.net.Uri.parse(link));
            if (settings == null || !settings.isValid()) {
                if (context != null) {
                    KamiGramUi.notify(context, "Ссылка на прокси не распознана");
                }
                return false;
            }
            SharedConfig.ProxyInfo existing = null;
            for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
                final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
                if (info.settings != null
                    && info.settings.getPort() == settings.getPort()
                    && settings.getAddress() != null
                    && settings.getAddress().equalsIgnoreCase(info.settings.getAddress())) {
                    existing = info;
                    break;
                }
            }
            final SharedConfig.ProxyInfo info = existing != null ? existing : new SharedConfig.ProxyInfo(settings);
            if (existing == null) {
                SharedConfig.addProxy(info);
            }
            SharedConfig.saveProxyList();
            final boolean ok = activate(info, context, false);
            if (ok) {
                checkOne(info);
            }
            return ok;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ------------------------------------------------------------------ движок

    private void scheduleSwitch(long delay) {
        try {
            if (!smartEnabled() || switching) {
                return;
            }
            switching = true;
            AndroidUtilities.cancelRunOnUIThread(switchRunnable);
            AndroidUtilities.runOnUIThread(switchRunnable, delay);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void cancelSwitch() {
        switching = false;
        try {
            AndroidUtilities.cancelRunOnUIThread(switchRunnable);
        } catch (Throwable ignore) {
        }
    }

    /** Переключение на самый быстрый живой прокси. */
    private void switchToBest(Context context) {
        try {
            if (!enabled() || !smartEnabled()) {
                return;
            }
            final long now = SystemClock.elapsedRealtime();
            if (now - lastSwitchTime < 800) {
                // защита от «карусели», когда всё умерло сразу
                return;
            }
            final SharedConfig.ProxyInfo best = pickBest();
            if (best != null) {
                lastSwitchTime = now;
                activate(best, context, false);
                checkOne(best);
                return;
            }
            // живых нет: проверяем все и уходим на прямое подключение, если прокси один
            pingAll();
            if (SharedConfig.proxyList.size() == 1) {
                lastSwitchTime = now;
                dropToDirect(context);
            } else {
                AndroidUtilities.runOnUIThread(() -> switchToBest(context), 1500);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Единственный прокси мёртв - включаем прямое подключение, чтобы интернет работал. */
    private void dropToDirect(Context context) {
        if (singleProxyDropped) {
            return;
        }
        singleProxyDropped = true;
        try {
            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", false);
            editor.apply();
            ConnectionsManager.setProxySettings(false, null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            if (context != null) {
                KamiGramUi.notify(context, "Прокси не отвечает — включено прямое подключение, мод сам вернёт прокси, когда он оживёт");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // и продолжаем следить: оживший прокси вернём сами
        AndroidUtilities.runOnUIThread(this::watchRevive, 20_000L);
    }

    /** Если прокси ожил, а мы сидим на прямом подключении - включаем прокси обратно. */
    private void watchRevive() {
        try {
            if (!singleProxyDropped || SharedConfig.proxyList.isEmpty()) {
                return;
            }
            final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(0);
            if (info.available && info.ping > 0) {
                singleProxyDropped = false;
                activate(info, null, false);
            } else {
                checkOne(info);
                AndroidUtilities.runOnUIThread(this::watchRevive, 20_000L);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Проверяет один прокси (жив ли и какой пинг). */
    public static void checkOne(final SharedConfig.ProxyInfo info) {
        if (info == null || info.checking || info.settings == null) {
            return;
        }
        try {
            info.checking = true;
            final int account = UserConfig.selectedAccount;
            ConnectionsManager.getInstance(account).checkProxy(info.settings, time -> AndroidUtilities.runOnUIThread(() -> {
                info.availableCheckTime = SystemClock.elapsedRealtime();
                info.checking = false;
                if (time == -1 || time <= 0) {
                    info.available = false;
                    info.ping = 0;
                } else {
                    info.ping = time;
                    info.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, info);
            }));
        } catch (Throwable e) {
            info.checking = false;
            FileLog.e(e);
        }
    }

    /** Проверяет все прокси, у которых нет свежего результата. */
    public static void pingAll() {
        try {
            if (SharedConfig.proxyList == null) {
                return;
            }
            final long now = SystemClock.elapsedRealtime();
            final HashSet<String> seen = new HashSet<>();
            for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
                final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
                if (info == null || info.settings == null) {
                    continue;
                }
                final String key = info.settings.getAddress() + ":" + info.settings.getPort();
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                if (info.checking || now - info.availableCheckTime < 30_000L) {
                    continue;
                }
                checkOne(info);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Периодическая проверка базы прокси (вызывается из приложения). */
    public static void tick(Context context) {
        try {
            if (!enabled() || !smartEnabled()) {
                return;
            }
            pingAll();
            // если текущий прокси уже проверен и мёртв, а живой есть - меняем сразу
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            if (current != null && !current.checking && current.availableCheckTime != 0 && !current.available) {
                INSTANCE.switchToBest(context);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Что показывать на кнопке прокси: цвет и подпись. */
    public static String statusText() {
        try {
            if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null || SharedConfig.currentProxy.settings == null) {
                return "Прокси выключен";
            }
            final SharedConfig.ProxyInfo info = SharedConfig.currentProxy;
            final StringBuilder text = new StringBuilder();
            text.append(info.settings.getAddress()).append(':').append(info.settings.getPort());
            if (info.checking) {
                text.append(" — проверяю…");
            } else if (info.available && info.ping > 0) {
                text.append(" — ").append(info.ping).append(" мс");
            } else if (info.availableCheckTime != 0 && !info.available) {
                text.append(" — не отвечает, ищу живой");
            }
            final int aliveCount = alive(true).size();
            text.append(" · живых: ").append(aliveCount).append('/').append(SharedConfig.proxyList.size());
            return text.toString();
        } catch (Throwable e) {
            return "Прокси";
        }
    }

    /** Цвет состояния для кнопки в шапке. */
    public static int stateColor() {
        try {
            if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null) {
                return 0xFF8E8E93;
            }
            final SharedConfig.ProxyInfo info = SharedConfig.currentProxy;
            if (info.checking) {
                return 0xFFFF9F0A;
            }
            if (info.availableCheckTime != 0 && !info.available) {
                return 0xFFFF453A;
            }
            return 0xFF30D158;
        } catch (Throwable e) {
            return 0xFF8E8E93;
        }
    }

    public static int proxyCount() {
        try {
            return SharedConfig.proxyList == null ? 0 : SharedConfig.proxyList.size();
        } catch (Throwable e) {
            return 0;
        }
    }

    public static boolean directMode() {
        return INSTANCE.singleProxyDropped;
    }

    // ------------------------------------------------------------------ события

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id == NotificationCenter.didUpdateConnectionState) {
                if (account != UserConfig.selectedAccount || !enabled() || !smartEnabled()) {
                    return;
                }
                final int state = ConnectionsManager.getInstance(account).getConnectionState();
                onState(state);
            } else if (id == NotificationCenter.proxyCheckDone) {
                if (!enabled() || !smartEnabled()) {
                    return;
                }
                final SharedConfig.ProxyInfo checked = args != null && args.length > 0 && args[0] instanceof SharedConfig.ProxyInfo
                    ? (SharedConfig.ProxyInfo) args[0] : null;
                if (checked == null) {
                    return;
                }
                if (checked.available && checked != SharedConfig.currentProxy) {
                    // нашли живой прокси - если текущий мёртв или мы в прямом режиме, меняем сразу
                    final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
                    final boolean currentDead = current == null || (!current.checking && current.availableCheckTime != 0 && !current.available);
                    if (currentDead || singleProxyDropped) {
                        switchToBest(null);
                    }
                } else if (!checked.available && checked == SharedConfig.currentProxy) {
                    switchToBest(null);
                }
            } else if (id == NotificationCenter.proxySettingsChanged) {
                cancelSwitch();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Реакция на состояние соединения: чем оно хуже, тем быстрее переключение. */
    private void onState(final int state) {
        if (state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating) {
            cancelSwitch();
            lastSwitchTime = 0;
            return;
        }
        if (state == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            // сети нет вообще: прокси тут ни при чём
            cancelSwitch();
            return;
        }
        if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
            scheduleSwitch(SWITCH_DELAY);
            return;
        }
        // прокси включён, но соединение идёт напрямую в DC - значит прокси мёртв
        scheduleSwitch(DEAD_DELAY);
    }

    /** Ручная проверка и переключение (кнопка «Подобрать лучший»). */
    public static void refreshNow(Context context) {
        pingAll();
        INSTANCE.switchToBest(context);
    }

    public static boolean networkOnline() {
        try {
            return ApplicationLoader.isNetworkOnline();
        } catch (Throwable e) {
            return true;
        }
    }
}
