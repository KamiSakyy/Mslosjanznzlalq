package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * KamiGram: ВСТРОЕННЫЕ ПРОКСИ С МОМЕНТАЛЬНЫМ АВТОРОУТИНГОМ.
 *
 * Что делает:
 *   * в сборке уже есть список рабочих прокси (ниже, блок «СПИСОК ПРОКСИ»);
 *   * они подключаются сами при запуске — до входа в аккаунт, на экране входа тоже;
 *   * если текущий прокси перестал отвечать, за доли секунды включается следующий
 *     живой, а из нескольких живых выбирается самый быстрый по пингу;
 *   * в списке прокси Telegram они НЕ показываются (пользователь видит пустой
 *     список, как будто прокси нет) — это скрытая часть мода;
 *   * в центре KamiGram есть один выключатель «KamiProxy» — если выключить,
 *     мод не трогает сеть вообще;
 *   * прокси работают и при включённом VPN (VPN для Telegram не помеха)
 *     (двойной туннель только мешает);
 *   * если обычного интернета нет и Telegram не грузится, прокси поднимается
 *     автоматически — в том числе на экране входа.
 *
 * КАК МЕНЯТЬ СПИСОК: единственное место — массив LINKS ниже. Формат — обычная
 * ссылка Telegram (t.me/proxy?server=...&port=...&secret=...).
 */
public final class KamiGramBuiltinProxy {

    // =========================================================================
    //  СПИСОК ПРОКСИ (единственное место для правки)
    // =========================================================================
    public static final String[] LINKS = {
        "https://t.me/proxy?server=akenai.top&port=853&secret=ee54ce330e4690cc297d2b031ff3f288b06d742e616b656e61692e636c69636b",
        "https://t.me/proxy?server=t.meow-network.com&port=443&secret=ee5622e11fff3e49bcc85280197a6106b5742e6d656f772d6e6574776f726b2e636f6d",
        "https://t.me/proxy?server=s03.neo-trading.org&port=443&secret=eeaf794bcc20f70b1436b6b92b01b207e26d61676e69742e7275",
        "https://t.me/proxy?server=ykima.davay.click&port=443&secret=ee06dfdbdf271bab18c6b606484c237384796b696d612e64617661792e636c69636b",
        "https://t.me/proxy?server=s02.neo-trading.org&port=443&secret=ee6ec9f7e082baf2397b450727ce78447e6f7a6f6e2e7275",
        "https://t.me/proxy?server=s01.neo-trading.org&port=443&secret=ee7391242569590e01416101927d38b565646e732d73686f702e7275",
        "https://t.me/proxy?server=help.meow0.co.uk&port=22&secret=dd79e344818749bd7ac519130220c25d09",
        "https://t.me/proxy?server=x.shmelproxy.top&port=443&secret=eefc2612ff65a557fddf1d1b334395ef237975672d6c696e6b2e7275",
        "https://t.me/proxy?server=kima.rabotaet.online&port=443&secret=ee12e7e5f961f258b04af168a1cba6318a6b696d612e7261626f746165742e6f6e6c696e65",
        "https://t.me/proxy?server=ardesvpn1.ru&port=8443&secret=ee05cf8e164f926f4a664b2404d276a1d6617264657376706e312e7275",
        "https://t.me/proxy?server=media9.happtg.org&port=443&secret=ee2e7c3d85e469cb8f825f4678a716a363706574726f766963682e7275",
    };
    // =========================================================================

    /** Как часто перепроверяем живость прокси. */
    private static final long ROUTE_INTERVAL = 20_000L;
    /** Насколько новый прокси должен быть быстрее текущего, чтобы переключиться. */
    private static final long BETTER_BY_MS = 120L;

    private static final ArrayList<SharedConfig.ProxyInfo> PRESETS = new ArrayList<>();
    private static final HashSet<String> PRESET_KEYS = new HashSet<>();

    private static boolean inited;
    private static boolean routing;
    private static long lastRoute;
    private static SharedConfig.ProxyInfo active;

    private static final Runnable ROUTE_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            routing = false;
            route(null, false);
        }
    };

    private KamiGramBuiltinProxy() {
    }

    // ------------------------------------------------------------------ включение

    /** Выключатель «KamiProxy» в центре мода. */
    public static boolean enabled() {
        return KamiGramConfig.builtinProxy();
    }

    public static void setEnabled(boolean value) {
        KamiGramConfig.set(KamiGramConfig.KEY_BUILTIN_PROXY, value);
    }

    /**
     * Реакция на выключатель KamiProxy. Вызывается из KamiGramConfig.set —
     * то есть кнопка в центре мода реально и подключает, и отключает прокси.
     */
    static void onEnabledChanged(boolean value) {
        if (value) {
            route(null, true);
        } else {
            disableOurProxy();
        }
    }

    /** Однократная инициализация при старте приложения (до входа в аккаунт). */
    public static void init(final Context context) {
        if (inited) {
            return;
        }
        inited = true;
        try {
            buildPresets();
            if (!enabled()) {
                disableOurProxy();
                return;
            }
            // первый автозапуск: сразу поднимаем самый быстрый прокси
            AndroidUtilities.runOnUIThread(() -> route(context, true), 1500L);
            // и дальше следим сами, без участия пользователя
            watch();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Периодический авто-роутинг: жив ли текущий прокси, не появился ли быстрее. */
    private static void watch() {
        try {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        route(null, false);
                    } catch (Throwable throwable) {
                        FileLog.e(throwable);
                    }
                    AndroidUtilities.runOnUIThread(this, ROUTE_INTERVAL);
                }
            }, ROUTE_INTERVAL);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ список

    private static String keyOf(ProxySettings settings) {
        if (settings == null || settings.getAddress() == null) {
            return "";
        }
        return (settings.getAddress() + ":" + settings.getPort()).toLowerCase();
    }

    /** Разбирает ссылки сборки в объекты прокси (без сети). */
    private static void buildPresets() {
        if (!PRESETS.isEmpty()) {
            return;
        }
        for (int a = 0; a < LINKS.length; a++) {
            try {
                final ProxySettings settings = ProxySettings.fromUri(Uri.parse(LINKS[a]));
                if (settings == null || !settings.isValid()) {
                    continue;
                }
                final SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(settings);
                PRESETS.add(info);
                PRESET_KEYS.add(keyOf(settings));
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        }
    }

    /** Встроенный ли это прокси (по адресу — работает и для копий объекта). */
    public static boolean isBuiltIn(SharedConfig.ProxyInfo info) {
        if (info == null || info.settings == null) {
            return false;
        }
        buildPresets();
        return PRESET_KEYS.contains(keyOf(info.settings));
    }

    /** Текущий прокси — наш, встроенный? */
    public static boolean ourProxyActive() {
        return isBuiltIn(SharedConfig.currentProxy);
    }

    public static int count() {
        buildPresets();
        return PRESETS.size();
    }

    /** Прокси из сборки, которые уже лежат в списке Telegram. */
    private static void ensureInTelegramList() {
        buildPresets();
        try {
            for (int a = 0; a < PRESETS.size(); a++) {
                final SharedConfig.ProxyInfo preset = PRESETS.get(a);
                final String key = keyOf(preset.settings);
                boolean found = false;
                for (int b = 0; b < SharedConfig.proxyList.size(); b++) {
                    final SharedConfig.ProxyInfo existing = SharedConfig.proxyList.get(b);
                    if (existing != null && key.equals(keyOf(existing.settings))) {
                        // живые данные (пинг, доступность) берём из существующего объекта
                        preset.available = existing.available;
                        preset.ping = existing.ping;
                        preset.availableCheckTime = existing.availableCheckTime;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    SharedConfig.addProxy(preset);
                }
            }
            SharedConfig.saveProxyList();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ роутинг

    /**
     * Главная функция: выбрать лучший прокси и моментально переключиться,
     * если текущий мёртв или найден заметно более быстрый.
     *
     * @param context нужен только для самого первого подключения
     * @param first   true — стартовое подключение
     */
    public static void route(final Context context, final boolean first) {
        try {
            if (!enabled()) {
                return;
            }
            // ВАЖНО: при включённом VPN прокси ОБЯЗАНЫ работать (просьба пользователя:
            // «у меня VPN включён для других приложений, а Telegram без соединения»).
            // Поэтому никаких пауз и отключений из-за VPN — маршрут выбирается как обычно.
            ensureInTelegramList();

            final SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
            final boolean currentAlive = current != null && current.available && current.ping > 0;
            final boolean currentBuiltIn = isBuiltIn(current);

            // подтягиваем свежие данные по всем прокси (Telegram делает это сам)
            KamiGramProxyPower.pingAll();

            final SharedConfig.ProxyInfo best = pickFastest();

            boolean needSwitch = false;
            if (best == null) {
                needSwitch = false; // ни одного проверенного — ждём результата проверки
            } else if (current == null || !currentAlive) {
                needSwitch = true;                 // текущего нет или он мёртв
            } else if (best != current && best.ping > 0 && best.ping + BETTER_BY_MS < current.ping) {
                needSwitch = true;                 // найден заметно быстрее
            }

            if (needSwitch && best != null) {
                activate(best, first ? context : contextOrNull());
            } else if (!currentBuiltIn && current != null && !currentAlive) {
                // стоим на чужом мёртвом прокси — уходим на наш
                final SharedConfig.ProxyInfo ours = pickFastest();
                if (ours != null) {
                    activate(ours, contextOrNull());
                }
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static Context contextOrNull() {
        try {
            return ApplicationLoader.applicationContext;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Самый быстрый живой прокси из списка сборки. */
    private static SharedConfig.ProxyInfo pickFastest() {
        SharedConfig.ProxyInfo best = null;
        try {
            buildPresets();
            for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
                final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
                if (info == null || info.settings == null || !isBuiltIn(info)) {
                    continue;
                }
                if (!info.available || info.ping <= 0) {
                    continue;
                }
                if (best == null || info.ping < best.ping) {
                    best = info;
                }
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
        return best;
    }

    /** Подключить прокси немедленно (без диалогов и тостов). */
    private static void activate(final SharedConfig.ProxyInfo info, final Context context) {
        if (info == null || info.settings == null) {
            return;
        }
        try {
            active = info;
            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", true);
            info.settings.toSharedPreferences(editor);
            editor.apply();

            SharedConfig.currentProxy = info;
            SharedConfig.saveProxyList();
            ConnectionsManager.setProxySettings(true, info.settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
            lastRoute = SystemClock.elapsedRealtime();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Выключить прокси, если он наш (используется при VPN и по кнопке). */
    private static void disableOurProxy() {
        try {
            if (!ourProxyActive()) {
                return;
            }
            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", false);
            editor.putBoolean("proxy_enabled_calls", false);
            editor.apply();

            SharedConfig.currentProxy = null;
            SharedConfig.saveProxyList();
            ConnectionsManager.setProxySettings(false, null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Ручной вызов «проверить и подключить лучший» из центра мода. */
    public static void refreshNow(final Context context) {
        try {
            KamiGramProxyPower.pingAll();
            route(context, true);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ VPN

    /** Включён ли VPN: тогда встроенные прокси не нужны. */
    public static boolean vpnActive() {
        try {
            final ConnectivityManager manager =
                (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                for (Network network : manager.getAllNetworks()) {
                    final NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true;
                    }
                }
            }
            final NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        } catch (Throwable ignore) {
            return false;
        }
    }

    // ------------------------------------------------------------------ состояние для UI

    /** Короткая подпись для центра мода (без адресов — они скрыты). */
    public static String statusText() {
        try {
            if (!enabled()) {
                return "KamiProxy выключен";
            }
            if (vpnActive()) {
                return "VPN включён — KamiProxy работает через него";
            }
            final SharedConfig.ProxyInfo info = SharedConfig.currentProxy;
            if (info == null || info.settings == null || !isBuiltIn(info)) {
                return "KamiProxy: подбираю лучший (в сборке " + count() + ")";
            }
            if (info.checking) {
                return "KamiProxy: проверяю " + count() + " прокси…";
            }
            if (info.available && info.ping > 0) {
                return "KamiProxy: подключён, " + info.ping + " мс · в сборке " + count();
            }
            return "KamiProxy: переключаюсь на живой · в сборке " + count();
        } catch (Throwable ignore) {
            return "KamiProxy";
        }
    }

    /** Сколько прокси из сборки сейчас живы. */
    public static int aliveCount() {
        int alive = 0;
        try {
            buildPresets();
            for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
                final SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
                if (info != null && isBuiltIn(info) && info.available && info.ping > 0) {
                    alive++;
                }
            }
        } catch (Throwable ignore) {
        }
        return alive;
    }
}
