package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoadOperation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Быстрый наблюдатель KamiProxy.
 *
 * Пользовательские записи не смешиваются с каталогом KamiProxy: пока custom
 * прокси жив, он остаётся выбранным. При подтверждённом сбое выбирается лучший
 * уже проверенный встроенный прокси, а если проверки ещё нет — запускается сразу.
 */
public final class KamiGramProxyPower implements NotificationCenter.NotificationCenterDelegate {

    private static final long SWITCH_DELAY = 1_200L;
    private static final long DEAD_DELAY = 250L;
    private static final long CURRENT_FRESHNESS = 8_000L;
    private static final long LOOP_DELAY = 2_000L;
    private static final long SPEED_MARGIN = 250L;
    private static final long SPEED_SWITCH_COOLDOWN = 30_000L;

    private static final KamiGramProxyPower INSTANCE = new KamiGramProxyPower();

    private boolean initialized;
    private boolean switching;
    private boolean loopPosted;
    private long lastSwitchTime;
    private int loadFailures;
    private long loadFailureTime;
    private long lastLoadSwitch;

    private final Runnable switchRunnable = () -> {
        switching = false;
        switchToBest(null);
    };

    private KamiGramProxyPower() {
    }

    public static void init() {
        INSTANCE.initInternal();
        KamiGramBuiltinProxy.ensureBuiltinsLoaded();
    }

    private void initInternal() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            KamiGramBuiltinProxy.ensureBuiltinsLoaded();
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.didUpdateConnectionState);
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.httpFileDidFailedLoad);
            }
            final NotificationCenter global = NotificationCenter.getGlobalInstance();
            global.addObserver(this, NotificationCenter.proxyCheckDone);
            global.addObserver(this, NotificationCenter.proxySettingsChanged);
            startLoop();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static boolean enabled() {
        return KamiGramBuiltinProxy.enabled();
    }

    private static boolean smartEnabled() {
        return KamiGramConfig.smartProxy();
    }

    private void startLoop() {
        if (loopPosted) {
            return;
        }
        loopPosted = true;
        AndroidUtilities.runOnUIThread(loopRunnable, LOOP_DELAY);
    }

    private final Runnable loopRunnable = new Runnable() {
        @Override
        public void run() {
            loopPosted = false;
            try {
                if (enabled() && smartEnabled()) {
                    tickInternal();
                }
            } catch (Throwable throwable) {
                KamiGramLog.e(throwable);
            }
            if (enabled() && smartEnabled()) {
                startLoop();
            }
        }
    };

    private void tickInternal() {
        try {
            KamiGramBuiltinProxy.ensureBuiltinsLoaded();
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            final long now = SystemClock.elapsedRealtime();
            if (current != null && current.settings != null && !current.checking
                && (current.availableCheckTime == 0 || now - current.availableCheckTime > CURRENT_FRESHNESS)) {
                checkOne(current);
            }
            pingAll();

            /* The built-in manager also handles the no-current case. Calling it
               here closes the gap between app resume and its delayed watcher. */
            KamiGramBuiltinProxy.route(null, false);

            final SharedConfig.ProxyInfo refreshed = SharedConfig.currentProxy;
            if (refreshed == null || refreshed.settings == null) {
                return;
            }
            if (!refreshed.checking && refreshed.availableCheckTime != 0 && !refreshed.available) {
                switchToBest(null);
            } else if (refreshed.available && refreshed.ping > 0
                && KamiGramBuiltinProxy.isBuiltIn(refreshed)) {
                final SharedConfig.ProxyInfo best = KamiGramBuiltinProxy.bestAvailable();
                if (best != null && best != refreshed && best.ping + SPEED_MARGIN < refreshed.ping
                    && now - lastSwitchTime > SPEED_SWITCH_COOLDOWN) {
                    if (activate(best, null, true)) {
                        lastSwitchTime = now;
                        checkOne(best);
                    }
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static Context contextOrNull() {
        try {
            return ApplicationLoader.applicationContext;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * A live custom proxy is never replaced. An untested custom proxy is probed
     * first, so a short connection setup is not mistaken for a failure.
     */
    private static boolean customIsKnownDead(SharedConfig.ProxyInfo info) {
        return info != null && !KamiGramBuiltinProxy.isBuiltIn(info)
            && !info.checking && info.availableCheckTime != 0 && !info.available;
    }

    private static ArrayList<SharedConfig.ProxyInfo> aliveBuiltins() {
        final ArrayList<SharedConfig.ProxyInfo> result = new ArrayList<>();
        try {
            KamiGramBuiltinProxy.ensureBuiltinsLoaded();
            for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
                if (KamiGramBuiltinProxy.isBuiltIn(info) && info.available && info.ping > 0) {
                    result.add(info);
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        return result;
    }

    /** Activate only a built-in fallback, never an arbitrary custom row. */
    private void switchToBest(Context context) {
        if (!enabled() || !smartEnabled()) {
            return;
        }
        try {
            KamiGramBuiltinProxy.ensureBuiltinsLoaded();
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            if (current != null && !KamiGramBuiltinProxy.isBuiltIn(current)) {
                if (current.available && current.ping > 0) {
                    return;
                }
                if (current.availableCheckTime == 0 || current.checking) {
                    checkOne(current);
                    return;
                }
                if (!customIsKnownDead(current)) {
                    return;
                }
            }

            final SharedConfig.ProxyInfo best = KamiGramBuiltinProxy.bestAvailable();
            if (best != null && best != current) {
                activate(best, contextOrNull(), true);
                return;
            }

            /* No result yet: probe all built-ins immediately. We deliberately do
               not disable the proxy or erase the custom pool while waiting. */
            pingAllBuiltins();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static void pingAllBuiltins() {
        try {
            KamiGramBuiltinProxy.ensureBuiltinsLoaded();
            for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
                if (KamiGramBuiltinProxy.isBuiltIn(info) && !info.checking
                    && (info.availableCheckTime == 0 || !info.available)) {
                    checkOne(info);
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Called when a large download has stalled on the active route. */
    public static void onSlowDownload(FileLoadOperation operation) {
        try {
            if (operation == null || !enabled() || !smartEnabled()) {
                return;
            }
            final long now = SystemClock.elapsedRealtime();
            if (now - INSTANCE.lastLoadSwitch < 15_000L) {
                return;
            }
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            if (current == null || current.settings == null) {
                return;
            }
            INSTANCE.lastLoadSwitch = now;
            INSTANCE.loadFailures = 0;
            current.available = false;
            current.ping = 0;
            current.availableCheckTime = now;
            INSTANCE.switchToBest(contextOrNull());
        } catch (Throwable ignored) {
        }
    }

    private void onLoadFailure() {
        final long now = SystemClock.elapsedRealtime();
        if (now - loadFailureTime > 45_000L) {
            loadFailures = 0;
        }
        loadFailureTime = now;
        loadFailures++;

        final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        if (current == null || current.settings == null) {
            return;
        }
        if (!current.checking && now - current.availableCheckTime > 3_000L) {
            checkOne(current);
        }
        if (loadFailures >= 2 && now - lastLoadSwitch > 15_000L) {
            final boolean isCustom = !KamiGramBuiltinProxy.isBuiltIn(current);
            if (isCustom || KamiGramBuiltinProxy.bestAvailable() != null) {
                loadFailures = 0;
                lastLoadSwitch = now;
                current.available = false;
                current.ping = 0;
                current.availableCheckTime = now;
                // ConnectionsManager.setProxySettings() invokes the resumable
                // FileLoader handover hook; no legacy network nudge is needed.
                switchToBest(contextOrNull());
            }
        }
    }

    /** Connect a proxy object already present in SharedConfig.proxyList. */
    public static boolean activate(SharedConfig.ProxyInfo info, Context context, boolean silent) {
        if (info == null || info.settings == null) {
            return false;
        }
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences == null) {
                return false;
            }
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("proxy_enabled", true);
            info.settings.toSharedPreferences(editor);
            if (!info.settings.getSecret().isEmpty()) {
                editor.putBoolean("proxy_enabled_calls", false);
            }
            editor.commit();
            SharedConfig.currentProxy = info;
            SharedConfig.saveProxyList();
            ConnectionsManager.setProxySettings(true, info.settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
            lastSwitchTime = SystemClock.elapsedRealtime();
            return true;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            return false;
        }
    }

    /** Add a user link without confusing it with a built-in preset. */
    public static boolean addAndActivate(String link, Context context) {
        try {
            final ProxySettings settings = ProxySettings.fromUri(Uri.parse(link));
            if (settings == null || !settings.isValid()) {
                return false;
            }
            final SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo(settings));
            return activate(info, context, true);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            return false;
        }
    }

    private void scheduleSwitch(long delay) {
        if (switching) {
            return;
        }
        switching = true;
        AndroidUtilities.runOnUIThread(switchRunnable, delay);
    }

    private void cancelSwitch() {
        switching = false;
        AndroidUtilities.cancelRunOnUIThread(switchRunnable);
    }

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
                    info.available = true;
                    info.ping = time;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, info);
            }));
        } catch (Throwable throwable) {
            info.checking = false;
            KamiGramLog.e(throwable);
        }
    }

    /** Check all distinct proxy settings without collapsing custom and built-in rows. */
    public static void pingAll() {
        try {
            KamiGramBuiltinProxy.ensureBuiltinsLoaded();
            if (SharedConfig.proxyList == null) {
                return;
            }
            final long now = SystemClock.elapsedRealtime();
            final HashSet<String> seen = new HashSet<>();
            for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
                if (info == null || info.settings == null) {
                    continue;
                }
                final String key = info.settings.getLink();
                if (!seen.add(key) || info.checking) {
                    continue;
                }
                final boolean current = info == SharedConfig.currentProxy;
                final long freshness = current ? 8_000L : 30_000L;
                if (info.availableCheckTime == 0 || now - info.availableCheckTime >= freshness) {
                    checkOne(info);
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    public static void tick(Context context) {
        try {
            if (!enabled() || !smartEnabled()) {
                return;
            }
            INSTANCE.tickInternal();
            INSTANCE.startLoop();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    public static String statusText() {
        try {
            if (!enabled()) {
                return "KamiProxy выключен";
            }
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            if (current == null || current.settings == null) {
                return "KamiProxy · ищу живой";
            }
            final String prefix = KamiGramBuiltinProxy.isBuiltIn(current)
                ? "KamiProxy" : "Пользовательский прокси";
            final String state;
            if (current.checking) {
                state = "проверяю…";
            } else if (current.available && current.ping > 0) {
                state = current.ping + " мс";
            } else {
                state = "ищу резерв";
            }
            final int alive = aliveBuiltins().size();
            return prefix + " · " + state + " · Kami живых: " + alive + '/' + KamiGramBuiltinProxy.count();
        } catch (Throwable throwable) {
            return "KamiProxy";
        }
    }

    public static int stateColor() {
        try {
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            if (!enabled() || current == null) {
                return 0xFF8E8E93;
            }
            if (current.checking) {
                return 0xFFFF9F0A;
            }
            if (current.available && current.ping > 0) {
                return 0xFF30D158;
            }
            return 0xFFFF453A;
        } catch (Throwable throwable) {
            return 0xFF8E8E93;
        }
    }

    public static int proxyCount() {
        try {
            return SharedConfig.proxyList == null ? 0 : SharedConfig.proxyList.size();
        } catch (Throwable throwable) {
            return 0;
        }
    }

    public static boolean directMode() {
        /* r76 never disables KamiProxy merely because all probes are pending. */
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id == NotificationCenter.didUpdateConnectionState) {
                if (account != UserConfig.selectedAccount || !enabled() || !smartEnabled()) {
                    return;
                }
                final int state = ConnectionsManager.getInstance(account).getConnectionState();
                if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                    scheduleSwitch(SWITCH_DELAY);
                } else if (state == ConnectionsManager.ConnectionStateConnecting) {
                    scheduleSwitch(DEAD_DELAY);
                } else if (state == ConnectionsManager.ConnectionStateConnected
                    || state == ConnectionsManager.ConnectionStateUpdating
                    || state == ConnectionsManager.ConnectionStateWaitingForNetwork) {
                    cancelSwitch();
                    if (state != ConnectionsManager.ConnectionStateWaitingForNetwork) {
                        lastSwitchTime = 0;
                    }
                }
            } else if (id == NotificationCenter.proxyCheckDone) {
                if (!enabled() || !smartEnabled()) {
                    return;
                }
                final SharedConfig.ProxyInfo checked = args != null && args.length > 0 && args[0] instanceof SharedConfig.ProxyInfo
                    ? (SharedConfig.ProxyInfo) args[0] : null;
                if (checked == null) {
                    return;
                }
                final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
                if (!checked.available && checked == current) {
                    switchToBest(null);
                } else if (checked.available && (current == null
                    || (current.availableCheckTime != 0 && !current.available))) {
                    /* A successful built-in probe is enough to replace a
                       completed dead probe, including a dead built-in that was
                       selected as the initial optimistic candidate. */
                    switchToBest(null);
                }
            } else if (id == NotificationCenter.fileLoadFailed || id == NotificationCenter.httpFileDidFailedLoad) {
                if (account == UserConfig.selectedAccount && enabled() && smartEnabled()) {
                    onLoadFailure();
                }
            } else if (id == NotificationCenter.proxySettingsChanged) {
                cancelSwitch();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    public static void refreshNow(Context context) {
        if (!enabled() || !smartEnabled()) {
            return;
        }
        KamiGramBuiltinProxy.ensureBuiltinsLoaded();
        pingAll();
        INSTANCE.switchToBest(context);
        KamiGramBuiltinProxy.route(context, true);
    }

    public static boolean networkOnline() {
        try {
            return ApplicationLoader.isNetworkOnline();
        } catch (Throwable throwable) {
            return true;
        }
    }
}
