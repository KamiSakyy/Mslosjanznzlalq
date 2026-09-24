package org.telegram.messenger.kamigram;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KamiGram: proxy + login helper.
 *
 * - activates an MTProto / SOCKS / web proxy as soon as its link shows up in the
 *   clipboard (copy the link anywhere, open the app - the proxy is already on);
 * - turns a clearly dead proxy off automatically, but never while the proxy is
 *   still connecting and never in the middle of a login attempt;
 * - keeps login failures quiet and actionable without exposing technical reports.
 */
public final class KamiGramProxyHelper {

    private static final Pattern PROXY_LINK = Pattern.compile(
        "(?i)(?:https?://)?(?:t\\.me|telegram\\.me|telegram\\.dog)/(?:proxy|socks|webproxy)\\?[^\\s\"'<>]*"
            + "|tg://(?:proxy|socks|webproxy)\\?[^\\s\"'<>]*"
    );

    /** last link we already activated, so we do not restart the same proxy over and over */
    private static String lastActivatedLink;

    /* Login diagnostics are intentionally kept in memory only: they let the
       login screen recover from a stuck request without creating a log viewer
       or persisting network/server details. */
    private static volatile int loginStage;
    private static volatile String loginStageError;

    public static void traceLogin(int stage, String error) {
        loginStage = stage;
        loginStageError = error;
    }

    public static int loginStage() {
        return loginStage;
    }

    private static final long PROXY_WATCH_DELAY = 25_000L;

    private KamiGramProxyHelper() {
    }

    /** Pulls a proxy link out of any text (a bare link, a message, an html snippet). */
    public static String extractLink(CharSequence text) {
        if (text == null) {
            return null;
        }
        final String value = text.toString().replace("&amp;", "&");
        final Matcher matcher = PROXY_LINK.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    public static boolean proxyEnabled() {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            return preferences != null && preferences.getBoolean("proxy_enabled", false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Turns the given proxy link on right away (same as tapping "Connect" in proxy settings). */
    public static boolean activateProxy(String link, Context context) {
        if (link == null) {
            return false;
        }
        try {
            final Uri uri = Uri.parse(link);
            final ProxySettings settings = ProxySettings.fromUri(uri);
            if (settings == null || !settings.isValid()) {
                return false;
            }
            /* addProxy() returns the canonical row when the link already exists.
               Using a fresh object here made currentProxy point outside proxyList,
               which in turn made deletion and KamiProxy fallback race each other. */
            final SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo(settings));
            SharedConfig.currentProxy = info;
            SharedConfig.saveProxyList();

            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", true);
            settings.toSharedPreferences(editor);
            editor.commit();

            ConnectionsManager.setProxySettings(true, settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

            lastActivatedLink = link;
            /* Никаких всплывающих пояснений: прокси включился — и всё. */
            return true;
        } catch (Throwable e) {
            KamiGramLog.e(e);
            return false;
        }
    }

    private static boolean sameAsCurrent(ProxySettings settings) {
        try {
            if (!proxyEnabled() || SharedConfig.currentProxy == null || SharedConfig.currentProxy.settings == null) {
                return false;
            }
            final ProxySettings current = SharedConfig.currentProxy.settings;
            return settings.equals(current);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Reads the clipboard and activates a proxy link found there. */
    public static boolean activateFromClipboard(Context context) {
        if (context == null || !KamiGramConfig.autoProxyFromClipboard()) {
            return false;
        }
        try {
            // до входа в аккаунт прокси из буфера НЕ поднимаем: мёртвый прокси молча съедает
            // запрос кода, и вход выглядит так, будто ничего не происходит
            if (UserConfig.getActivatedAccountsCount() == 0) {
                return false;
            }
        } catch (Throwable ignore) {
            return false;
        }
        try {
            final ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) {
                return false;
            }
            final ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return false;
            }
            final String link = extractLink(clip.getItemAt(0).coerceToText(context));
            if (link == null || link.equals(lastActivatedLink)) {
                return false;
            }
            final Uri uri = Uri.parse(link);
            final ProxySettings parsed = ProxySettings.fromUri(uri);
            if (parsed != null && parsed.isValid() && sameAsCurrent(parsed)) {
                // такой прокси уже включён - не рвём соединение повторной активацией
                lastActivatedLink = link;
                return false;
            }
            return activateProxy(link, context);
        } catch (Throwable e) {
            KamiGramLog.e(e);
            return false;
        }
    }

    /** Is any proxy switched on right now? */
    public static boolean hasProxy() {
        return proxyEnabled();
    }

    /** True while the proxy connection is healthy - a proxy that is still only "connecting" is not. */
    public static boolean proxyLooksAlive() {
        try {
            if (!proxyEnabled()) {
                return true;
            }
            final int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
            return state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating;
        } catch (Throwable e) {
            return true;
        }
    }

    /**
     * A proxy that never connects must not eat the login request: the request dies with a
     * silent network error and the app shows absolutely nothing. This switches the proxy off
     * (the user is warned) so the retry goes direct / over VPN.
     */
    public static void disableProxyForLogin(Context context) {
        disableProxy(context, "the proxy did not connect - switched off, retrying the login directly");
    }

    /** Loud, always visible progress of the login: a toast works even when no dialog can be shown. */
    public static void toastLogin(String text) {
        /* Статусы «Подключение…» и прочие всплывающие пояснения убраны: тишина. */
    }

    /** Switches the proxy off (direct connection or VPN takes over). */
    public static void disableProxy(Context context, String reason) {
        try {
            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", false);
            editor.commit();
            SharedConfig.currentProxy = null;
            ConnectionsManager.setProxySettings(false, null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            /* Молча: никаких всплывающих пояснений про прокси. */
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
    }

    /**
     * Watches the connection: if a proxy is on but Telegram is not connected at all
     * after ~25 seconds (while the device itself is online and the proxy is not even
     * being tried), the proxy is switched off - a dead proxy must not block VPN or a
     * direct connection. A proxy that is still connecting is never dropped.
     */
    public static void watchProxy(final Context context) {
        /* KAMIGRAM_DOWNLOAD_WATCH_ONLY_R83: proxy watchdogs are useful only
           while FileLoader has a live download; app launch/login stays quiet. */
        if (!KamiGramDownloadRecovery.hasActiveDownloads()
            || !KamiGramConfig.proxyFallback() || !proxyEnabled()) {
            return;
        }
        try {
            if (SharedConfig.currentProxy != null
                && KamiGramBuiltinProxy.isBuiltIn(SharedConfig.currentProxy)) {
                return;
            }
        } catch (Throwable ignore) {
        }
        final int account = UserConfig.selectedAccount;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                /* The download may finish during the delay; do not leave a
                   delayed idle proxy action behind or alter an idle user route. */
                if (!KamiGramDownloadRecovery.hasActiveDownloads()
                    || !proxyEnabled() || !ApplicationLoader.isNetworkOnline()) {
                    return;
                }
                final int state = ConnectionsManager.getInstance(account).getConnectionState();
                if (state == ConnectionsManager.ConnectionStateConnected
                    || state == ConnectionsManager.ConnectionStateUpdating
                    || state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                    return;
                }
                disableProxy(context, "proxy did not answer in 25 s - switched off, direct connection is active");
            } catch (Throwable e) {
                KamiGramLog.e(e);
            }
        }, PROXY_WATCH_DELAY);
    }

    /** Show a short actionable login message without exposing technical diagnostics. */
    public static void showLoginProblem(Context context, String serverAnswer, String details) {
        showLoginProblem(context, serverAnswer, details, null);
    }

    public static void showLoginProblem(Context context, String serverAnswer, String details,
                                        final Runnable onRetry) {
        if (context == null) {
            return;
        }
        try {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Не удалось войти")
                .setMessage("Проверьте соединение и повторите попытку.")
                .setPositiveButton("Повторить", (dialog, which) -> {
                    if (onRetry != null) {
                        AndroidUtilities.runOnUIThread(onRetry, 300);
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Emergency proxy drop, kept for the case when the app is fully idle and the proxy
     * is clearly dead. Never touches a proxy that is still connecting.
     */
    public static boolean dropDeadProxy(Context context) {
        try {
            if (!proxyEnabled()) {
                return false;
            }
            final int account = UserConfig.selectedAccount;
            final int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating
                || state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                // прокси работает или ещё пытается - рвать его не нужно
                return false;
            }
            disableProxy(context, "proxy did not answer - switched off. Press the login button again: now direct/VPN");
            return true;
        } catch (Throwable e) {
            KamiGramLog.e(e);
            return false;
        }
    }

    /** Called when the user taps the login button: fixes the proxy situation before we send the code. */
    public static void prepareForLogin(Context context) {
        if (context == null) {
            return;
        }
        try {
            // прокси из буфера при входе НЕ поднимаем: иначе мёртвый прокси может
            // съесть запрос кода (это ровно то, из-за чего вход выглядит «мёртвым»)
            if (proxyEnabled()) {
                watchProxy(context);
            }
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
    }
}
