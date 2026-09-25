package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * KamiProxy — встроенный резервный каталог прокси.
 *
 * Built-ins and user-added proxies remain separate rows. KamiProxy never deletes
 * or rewrites a user row; it only keeps its own fallback catalog alive and can
 * route to the best healthy row when the active route is dead or demonstrably
 * too slow for a download.
 */
public final class KamiGramBuiltinProxy {

    /** User-prioritized catalog, in probe preference order. */
    public static final String[] LINKS = {
        "https://t.me/proxy?server=relay.surfvpn.app&port=443&secret=eedf44a4347c1eb8938c7e63340bd1ca4972656c61792e7375726676706e2e617070",
        "https://t.me/proxy?server=akenai.tg&port=853&secret=ee54ce330e4690cc297d2b031ff3f288b06d742e616b656e61692e636c69636b",
        "https://t.me/proxy?server=s02.neo-trading.org&port=443&secret=ee6ec9f7e082baf2397b450727ce78447e6f7a6f6e2e7275",
        "https://t.me/proxy?server=s01.neo-trading.org&port=443&secret=ee7391242569590e01416101927d38b565646e732d73686f702e7275",
        "https://t.me/proxy?server=ardesvpn1.ru&port=8443&secret=ee05cf8e164f926f4a664b2404d276a1d6617264657376706e312e7275",
        "https://t.me/proxy?server=akenai.top&port=853&secret=ee54ce330e4690cc297d2b031ff3f288b06d742e616b656e61692e636c69636b",
        "https://t.me/proxy?server=t.meow-network.com&port=443&secret=ee5622e11fff3e49bcc85280197a6106b5742e6d656f772d6e6574776f726b2e636f6d",
        "https://t.me/proxy?server=s03.neo-trading.org&port=443&secret=eeaf794bcc20f70b1436b6b92b01b207e26d61676e69742e7275"
    };

    private static final long ROUTE_INTERVAL = 8_000L;
    private static final long BETTER_BY_MS = 300L;
    private static final long SWITCH_COOLDOWN = 45_000L;

    private static final ArrayList<SharedConfig.ProxyInfo> PRESETS = new ArrayList<>();
    private static final HashSet<String> PRESET_KEYS = new HashSet<>();

    private static boolean inited;
    private static boolean loadingProxyList;
    private static boolean watchPosted;
    private static long lastRoute;
    private static long lastSwitch;

    private KamiGramBuiltinProxy() {
    }

    public static boolean enabled() {
        return KamiGramConfig.builtinProxy();
    }

    public static void setEnabled(boolean value) {
        KamiGramConfig.set(KamiGramConfig.KEY_BUILTIN_PROXY, value);
    }

    static void onEnabledChanged(boolean value) {
        if (value) {
            ensureBuiltinsLoaded();
            KamiGramProxyPower.init();
            route(contextOrNull(), true);
            startWatch();
        } else {
            disableOurProxy();
        }
    }

    /** Safe before login; starts the health watcher after the first screen. */
    public static void init(final Context context) {
        ensureBuiltinsLoaded();
        if (inited) {
            return;
        }
        inited = true;
        if (!enabled()) {
            disableOurProxy();
            return;
        }
        try {
            AndroidUtilities.runOnUIThread(() -> route(context, true), 600L);
            startWatch();
        } catch (Throwable ignored) {
        }
    }

    private static void startWatch() {
        if (watchPosted) {
            return;
        }
        watchPosted = true;
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                watchPosted = false;
                try {
                    if (enabled()) {
                        route(null, false);
                    }
                } catch (Throwable ignored) {
                }
                if (enabled()) {
                    startWatch();
                }
            }
        }, ROUTE_INTERVAL);
    }

    private static String keyOf(ProxySettings settings) {
        if (settings == null) {
            return "";
        }
        return settings.getType().name() + "|"
            + String.valueOf(settings.getAddress()).toLowerCase() + "|"
            + settings.getPort() + "|"
            + String.valueOf(settings.getUser()) + "|"
            + String.valueOf(settings.getPassword()) + "|"
            + String.valueOf(settings.getSecret());
    }

    private static void buildPresets() {
        if (!PRESETS.isEmpty()) {
            return;
        }
        for (String link : LINKS) {
            try {
                final ProxySettings settings = ProxySettings.fromUri(Uri.parse(link));
                if (settings == null || !settings.isValid()) {
                    continue;
                }
                final String key = keyOf(settings);
                if (!PRESET_KEYS.add(key)) {
                    continue;
                }
                PRESETS.add(new SharedConfig.ProxyInfo(settings));
            } catch (Throwable ignored) {
            }
        }
    }

    /** Merges only missing built-in rows into Telegram's persisted proxy list. */
    public static synchronized void ensureBuiltinsLoaded() {
        if (loadingProxyList) {
            return;
        }
        loadingProxyList = true;
        try {
            buildPresets();
            SharedConfig.loadProxyList();
        } catch (Throwable ignored) {
            // The native loader can be unavailable before ApplicationLoader is ready.
        } finally {
            loadingProxyList = false;
        }
        try {
            if (SharedConfig.proxyList == null) {
                return;
            }
            boolean changed = false;
            for (SharedConfig.ProxyInfo preset : PRESETS) {
                if (findInList(preset.settings) == null) {
                    SharedConfig.proxyList.add(preset);
                    changed = true;
                }
            }
            if (changed) {
                SharedConfig.saveProxyList();
            }
        } catch (Throwable ignored) {
        }
    }

    private static SharedConfig.ProxyInfo findInList(ProxySettings settings) {
        if (settings == null || SharedConfig.proxyList == null) {
            return null;
        }
        final String key = keyOf(settings);
        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
            if (info != null && info.settings != null && key.equals(keyOf(info.settings))) {
                return info;
            }
        }
        return null;
    }

    public static boolean isBuiltIn(SharedConfig.ProxyInfo info) {
        if (info == null || info.settings == null) {
            return false;
        }
        buildPresets();
        return PRESET_KEYS.contains(keyOf(info.settings));
    }

    public static boolean ourProxyActive() {
        return isBuiltIn(SharedConfig.currentProxy);
    }

    public static int count() {
        buildPresets();
        return PRESETS.size();
    }

    /** Best healthy built-in; ties retain the supplied priority order. */
    public static SharedConfig.ProxyInfo bestAvailable() {
        ensureBuiltinsLoaded();
        SharedConfig.ProxyInfo best = null;
        try {
            buildPresets();
            for (SharedConfig.ProxyInfo preset : PRESETS) {
                final SharedConfig.ProxyInfo info = findInList(preset.settings);
                if (info == null || !info.available || info.ping <= 0) {
                    continue;
                }
                if (best == null || info.ping < best.ping) {
                    best = info;
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    /** The first configured route follows LINKS order even after an upgrade. */
    private static SharedConfig.ProxyInfo firstBuiltIn() {
        ensureBuiltinsLoaded();
        try {
            buildPresets();
            for (SharedConfig.ProxyInfo preset : PRESETS) {
                final SharedConfig.ProxyInfo info = findInList(preset.settings);
                if (info != null) {
                    return info;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static int aliveCount() {
        int alive = 0;
        try {
            ensureBuiltinsLoaded();
            for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
                if (isBuiltIn(info) && info.available && info.ping > 0) {
                    alive++;
                }
            }
        } catch (Throwable ignored) {
        }
        return alive;
    }

    public static void route(final Context context, final boolean first) {
        try {
            if (!enabled()) {
                return;
            }
            ensureBuiltinsLoaded();
            KamiGramProxyPower.pingAll();

            final long now = SystemClock.elapsedRealtime();
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            final boolean currentBuiltin = isBuiltIn(current);
            final boolean proxyOn = SharedConfig.isProxyEnabled();
            if (current == null) {
                activateBestOrProbe(context, first);
                lastRoute = now;
                return;
            }
            if (!proxyOn) {
                if (currentBuiltin) {
                    activate(current, context, true);
                }
                return;
            }

            if (!currentBuiltin && current.availableCheckTime == 0 && !current.checking) {
                KamiGramProxyPower.checkOne(current);
                return;
            }

            final boolean currentAlive = current.available && current.ping > 0;
            final SharedConfig.ProxyInfo best = bestAvailable();
            final boolean routeDue = now - lastRoute >= ROUTE_INTERVAL;
            final boolean cooldownOver = now - lastSwitch >= SWITCH_COOLDOWN;
            if (!currentAlive) {
                if (best != null && best != current) {
                    activate(best, contextOrNull(), true);
                }
            } else if (routeDue && cooldownOver && currentBuiltin && best != null && best != current
                && best.ping + BETTER_BY_MS < current.ping) {
                activate(best, contextOrNull(), true);
            }
            lastRoute = now;
        } catch (Throwable ignored) {
        }
    }

    private static void activateBestOrProbe(Context context, boolean first) {
        SharedConfig.ProxyInfo best = bestAvailable();
        if (best == null) {
            best = firstBuiltIn();
            if (best != null) {
                activate(best, context, true);
                KamiGramProxyPower.checkOne(best);
            }
        } else {
            activate(best, first ? context : contextOrNull(), true);
        }
    }

    /** Native proxy screen entry point, including an empty custom list. */
    public static boolean enableForProxyScreen(Context context) {
        if (!enabled()) {
            setEnabled(true);
        }
        if (!enabled()) {
            return false;
        }
        ensureBuiltinsLoaded();
        if (SharedConfig.currentProxy != null && isBuiltIn(SharedConfig.currentProxy)) {
            activate(SharedConfig.currentProxy, context, true);
            return true;
        }
        final SharedConfig.ProxyInfo first = firstBuiltIn();
        if (first == null) {
            return false;
        }
        activate(first, context, true);
        KamiGramProxyPower.checkOne(first);
        return true;
    }

    public static void recoverAfterProxyDeleted() {
        recoverAfterProxyDeleted(true);
    }

    public static void recoverAfterProxyDeleted(boolean deletedCurrent) {
        try {
            ensureBuiltinsLoaded();
            if (!enabled() || !deletedCurrent
                || !MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false)
                || SharedConfig.currentProxy != null) {
                return;
            }
            activateBestOrProbe(null, false);
        } catch (Throwable ignored) {
        }
    }

    private static void activate(SharedConfig.ProxyInfo info, Context context, boolean silent) {
        if (info == null || info.settings == null) {
            return;
        }
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences == null) {
                return;
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
            lastSwitch = SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
        }
    }

    private static void disableOurProxy() {
        try {
            if (!ourProxyActive()) {
                return;
            }
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences != null) {
                preferences.edit().putBoolean("proxy_enabled", false)
                    .putBoolean("proxy_enabled_calls", false).commit();
            }
            SharedConfig.currentProxy = null;
            SharedConfig.saveProxyList();
            ConnectionsManager.setProxySettings(false, null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } catch (Throwable ignored) {
        }
    }

    private static Context contextOrNull() {
        try {
            return ApplicationLoader.applicationContext;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String statusText() {
        try {
            if (!enabled()) {
                return "SakuProxy выключен";
            }
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            if (current == null || current.settings == null) {
                return "SakuProxy · ищу живой";
            }
            final String prefix = isBuiltIn(current) ? "SakuProxy" : "Пользовательский прокси";
            final String state = current.checking ? "проверяю…"
                : current.available && current.ping > 0 ? current.ping + " мс" : "ищу резерв";
            return prefix + " · " + state + " · живых: " + aliveCount() + '/' + count();
        } catch (Throwable ignored) {
            return "SakuProxy";
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
            return current.available && current.ping > 0 ? 0xFF30D158 : 0xFFFF453A;
        } catch (Throwable ignored) {
            return 0xFF8E8E93;
        }
    }

    public static int proxyCount() {
        try {
            return SharedConfig.proxyList == null ? 0 : SharedConfig.proxyList.size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean directMode() {
        return false;
    }
}
