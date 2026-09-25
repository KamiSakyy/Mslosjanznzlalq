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
 * SakuProxy — встроенный резервный каталог прокси.
 *
 * Важное правило r76: встроенные и пользовательские прокси — это два разных
 * слоя. SakuProxy добавляет свои записи во внутренний список Telegram, но не
 * удаляет и не подменяет пользовательские записи. Если пользовательский
 * прокси выбран и отвечает, он остаётся главным. При подтверждённом сбое
 * движок мгновенно выбирает живой встроенный прокси.
 */
public final class KamiGramBuiltinProxy {

    /** Каталог SakuProxy. Меняется только здесь. */
    /* KAMIGRAM_PROXY_CATALOG_R76: built-ins are never deleted with custom proxies. */
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

    private static final long ROUTE_INTERVAL = 1_000L;
    /* KAMIGRAM_PROXY_FASTEST_LIVE_R77: select the quickest confirmed live
       built-in route instead of waiting 120 ms for a nominal improvement. */
    private static final long BETTER_BY_MS = 25L;

    private static final ArrayList<SharedConfig.ProxyInfo> PRESETS = new ArrayList<>();
    private static final HashSet<String> PRESET_KEYS = new HashSet<>();

    private static boolean inited;
    private static boolean watchPosted;
    private static boolean loadingProxyList;
    private static long lastRoute;

    private static final Runnable WATCH_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            watchPosted = false;
            if (!enabled() || !KamiGramDownloadRecovery.hasActiveDownloads()) {
                return;
            }
            try {
                route(null, false);
            } catch (Throwable throwable) {
                KamiGramLog.e(throwable);
            }
            if (enabled() && KamiGramDownloadRecovery.hasActiveDownloads()) {
                startWatch();
            }
        }
    };

    private KamiGramBuiltinProxy() {
    }

    // ------------------------------------------------------------------ настройка

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
            onDownloadActivityChanged();
        } else {
            disableOurProxy();
        }
    }

    /** Запускается из LaunchActivity и безопасен до входа в аккаунт. */
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
            if (KamiGramDownloadRecovery.hasActiveDownloads()) {
                AndroidUtilities.runOnUIThread(() -> route(context, true), 600L);
                startWatch();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static void startWatch() {
        if (watchPosted || !enabled() || !KamiGramDownloadRecovery.hasActiveDownloads()) {
            return;
        }
        watchPosted = true;
        AndroidUtilities.runOnUIThread(WATCH_RUNNABLE, ROUTE_INTERVAL);
    }

    private static void stopWatch() {
        watchPosted = false;
        AndroidUtilities.cancelRunOnUIThread(WATCH_RUNNABLE);
    }

    /** Called by native FileLoader when a download starts or ends. */
    public static void onDownloadActivityChanged() {
        if (enabled() && KamiGramDownloadRecovery.hasActiveDownloads()) {
            startWatch();
        } else {
            stopWatch();
        }
    }

    // ------------------------------------------------------------------ каталог

    private static String keyOf(ProxySettings settings) {
        if (settings == null) {
            return "";
        }
        /* Только host:port было недостаточно: пользовательский SOCKS/MTProto
           с тем же адресом ошибочно считался встроенным и скрывался/удалялся. */
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
            } catch (Throwable throwable) {
                KamiGramLog.e(throwable);
            }
        }
    }

    /**
     * Всегда восстанавливает каталог SakuProxy после load/delete custom proxy.
     * Метод публичный специально для патча SharedConfig.loadProxyList().
     */
    public static synchronized void ensureBuiltinsLoaded() {
        /* KAMIGRAM_PROXY_CATALOG_REENTRANT_R76: SharedConfig.loadProxyList()
           calls back into this method after deserialization. Do not recurse
           through the loader thousands of times on first app start. */
        if (loadingProxyList) {
            return;
        }
        loadingProxyList = true;
        try {
            buildPresets();
            /* Ensure persisted custom rows are loaded before the catalog is
               merged. loadProxyList() is idempotent and its r76 tail calls
               this method once more after deserialization. */
            SharedConfig.loadProxyList();
            if (SharedConfig.proxyList == null) {
                return;
            }
            boolean changed = false;
            for (SharedConfig.ProxyInfo preset : PRESETS) {
                SharedConfig.ProxyInfo existing = findInList(preset.settings);
                if (existing == null) {
                    SharedConfig.proxyList.add(preset);
                    changed = true;
                }
            }
            if (changed) {
                SharedConfig.saveProxyList();
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        } finally {
            loadingProxyList = false;
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

    /** Лучший уже проверенный встроенный прокси; ties retain LINKS priority. */
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
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        return best;
    }

    /** First route follows the user-provided LINKS order after upgrades too. */
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
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
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
        } catch (Throwable ignore) {
        }
        return alive;
    }

    // ------------------------------------------------------------------ роутинг

    /**
     * Пользовательский прокси не заменяется «самым быстрым» встроенным, пока он
     * жив. Резерв включается только при подтверждённом сбое или отсутствии
     * текущего прокси.
     */
    public static void route(final Context context, final boolean first) {
        try {
            if (!enabled()) {
                return;
            }
            /* KAMIGRAM_PROXY_SEND_GUARD_R78: do not replace a route while an
               ordinary outgoing message is waiting for its proxy response. */
            if (KamiGramProxyPower.messageSendInFlight()) {
                return;
            }
            ensureBuiltinsLoaded();
            KamiGramProxyPower.pingAll();

            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            final boolean currentBuiltin = isBuiltIn(current);
            final boolean proxyOn = SharedConfig.isProxyEnabled();

            if (current == null) {
                activateBestOrProbe(context, first);
                return;
            }
            if (!proxyOn) {
                /* Выключатель Telegram мог быть выключен после удаления custom.
                   Восстанавливаем только встроенный/отсутствующий маршрут; живой
                   пользовательский маршрут не перехватываем. */
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
            if (!currentAlive) {
                if (best != null) {
                    activate(best, contextOrNull(), true);
                }
            } else if (currentBuiltin && best != null && best != current
                && best.ping + BETTER_BY_MS < current.ping) {
                activate(best, contextOrNull(), true);
            }
            lastRoute = SystemClock.elapsedRealtime();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
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

    /** Включает SakuProxy из родного экрана, даже если пользовательских строк нет. */
    public static boolean enableForProxyScreen(Context context) {
        /* The native Telegram checkbox is also a SakuProxy entry point. If the
           user previously switched SakuProxy off there, turn the feature back
           on instead of sending an empty screen to ProxySettingsActivity. */
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

    /** Совместимый хук: старые callers считают удаление активным. */
    public static void recoverAfterProxyDeleted() {
        recoverAfterProxyDeleted(true);
    }

    /**
     * Восстанавливает fallback только после удаления активного custom-row.
     * Если пользователь просто чистит неактивную строку при выключенном
     * Telegram-прокси, SakuProxy не должен самовольно включать соединение.
     */
    public static void recoverAfterProxyDeleted(boolean deletedCurrent) {
        try {
            ensureBuiltinsLoaded();
            if (!enabled()) {
                return;
            }
            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            /* Respect an intentional Telegram-proxy off state. Only an active
               custom row deletion is allowed to trigger the fallback. */
            if (!deletedCurrent
                || !MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false)
                || current != null) {
                return;
            }
            activateBestOrProbe(null, false);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
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
            lastRoute = SystemClock.elapsedRealtime();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    private static void disableOurProxy() {
        stopWatch();
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

    // ------------------------------------------------------------------ состояние для UI

    public static String statusText() {
        try {
            if (!enabled()) {
                return "SakuProxy выключен";
            }
            final SharedConfig.ProxyInfo info = SharedConfig.currentProxy;
            if (info == null || info.settings == null) {
                return "SakuProxy · ищу живой";
            }
            if (!isBuiltIn(info)) {
                return info.available && info.ping > 0
                    ? "Пользовательский прокси · " + info.ping + " мс"
                    : "Пользовательский прокси · ищу резерв";
            }
            return info.available && info.ping > 0
                ? "SakuProxy · " + info.ping + " мс"
                : "SakuProxy · ищу живой";
        } catch (Throwable ignore) {
            return "SakuProxy";
        }
    }

    public static boolean vpnActive() {
        return false;
    }
}
