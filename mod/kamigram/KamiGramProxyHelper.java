package org.telegram.messenger.kamigram;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KamiGram: proxy helper.
 *
 * - activates an MTProto / SOCKS / web proxy as soon as its link shows up in the
 *   clipboard (copy the link anywhere, open the app - the proxy is already on);
 * - turns the proxy off automatically when it does not connect, so that VPN or a
 *   direct connection can work instead of hanging forever;
 * - warns on the login screen if there is no connection at all.
 */
public final class KamiGramProxyHelper {

    private static final Pattern PROXY_LINK = Pattern.compile(
        "(?i)(?:https?://)?(?:t\\.me|telegram\\.me|telegram\\.dog)/(?:proxy|socks|webproxy)\\?[^\\s\"'<>]*"
            + "|tg://(?:proxy|socks|webproxy)\\?[^\\s\"'<>]*"
    );

    /** last link we already activated, so we do not restart the same proxy over and over */
    private static String lastActivatedLink;

    private static final long PROXY_WATCH_DELAY = 45_000L;

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
            final SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(settings);
            SharedConfig.addProxy(info);
            SharedConfig.currentProxy = info;
            SharedConfig.saveProxyList();

            final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", true);
            settings.toSharedPreferences(editor);
            editor.commit();

            ConnectionsManager.setProxySettings(true, settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

            lastActivatedLink = link;
            if (context != null) {
                Toast.makeText(context, "KamiGram: " + settings.getAddress() + ":" + settings.getPort()
                    + " - proxy enabled", Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private static boolean sameAsCurrent(ProxySettings settings) {
        try {
            if (!proxyEnabled() || SharedConfig.currentProxy == null || SharedConfig.currentProxy.settings == null) {
                return false;
            }
            final ProxySettings current = SharedConfig.currentProxy.settings;
            return settings.getPort() == current.getPort()
                && settings.getAddress() != null && settings.getAddress().equalsIgnoreCase(current.getAddress());
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
            FileLog.e(e);
            return false;
        }
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
            if (context != null) {
                Toast.makeText(context, "KamiGram: " + reason, Toast.LENGTH_LONG).show();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * Watches the connection: if a proxy is on but Telegram is still not connected
     * after ~25 seconds (while the device itself is online), the proxy is switched
     * off automatically - a dead proxy must not block VPN or a direct connection.
     */
    public static void watchProxy(final Context context) {
        if (!KamiGramConfig.proxyFallback() || !proxyEnabled()) {
            return;
        }
        final int account = UserConfig.selectedAccount;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (!proxyEnabled() || !ApplicationLoader.isNetworkOnline()) {
                    return;
                }
                final int state = ConnectionsManager.getInstance(account).getConnectionState();
                if (state == ConnectionsManager.ConnectionStateConnected
                    || state == ConnectionsManager.ConnectionStateUpdating
                    || state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                    return;
                }
                disableProxy(context, "proxy did not answer in 45 s - switched off, direct connection is active");
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, PROXY_WATCH_DELAY);
    }

    /**
     * A stuck login is the worst case for a mod user: the button spins and nothing explains why.
     * This shows exactly what the app sees - connection state, proxy address, server answer -
     * and offers to drop the proxy and retry.
     */
    public static void showLoginProblem(Context context, String serverAnswer, String details) {
        try {
            if (context == null) {
                return;
            }
            final int account = UserConfig.selectedAccount;
            final int state = ConnectionsManager.getInstance(account).getConnectionState();
            final String stateText;
            switch (state) {
                case ConnectionsManager.ConnectionStateConnected:
                    stateText = "connected";
                    break;
                case ConnectionsManager.ConnectionStateUpdating:
                    stateText = "connected (updating)";
                    break;
                case ConnectionsManager.ConnectionStateConnecting:
                    stateText = "connecting to server";
                    break;
                case ConnectionsManager.ConnectionStateConnectingToProxy:
                    stateText = "connecting to proxy";
                    break;
                case ConnectionsManager.ConnectionStateWaitingForNetwork:
                    stateText = "waiting for network";
                    break;
                default:
                    stateText = "unknown (" + state + ")";
                    break;
            }
            final StringBuilder text = new StringBuilder();
            text.append("KamiGram: why the code did not arrive\n\n");
            text.append("Connection: ").append(stateText).append('\n');
            if (proxyEnabled() && SharedConfig.currentProxy != null && SharedConfig.currentProxy.settings != null) {
                text.append("Proxy: ").append(SharedConfig.currentProxy.settings.getAddress())
                    .append(':').append(SharedConfig.currentProxy.settings.getPort()).append(" (on)\n");
            } else {
                text.append("Proxy: off\n");
            }
            if (serverAnswer != null && serverAnswer.length() > 0) {
                text.append("Server: ").append(serverAnswer).append('\n');
            }
            if (details != null && details.length() > 0) {
                text.append(details).append('\n');
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("KamiGram: login");
            builder.setMessage(text.toString());
            builder.setPositiveButton("Turn off proxy and retry", (dialog, which) ->
                disableProxy(context, "proxy off - press the login button again"));
            builder.setNegativeButton("Keep waiting", null);
            builder.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Called when the user taps the login button: fixes the proxy situation before we send the code. */
    public static void prepareForLogin(Context context) {
        if (context == null) {
            return;
        }
        try {
            activateFromClipboard(context);
            if (proxyEnabled()) {
                watchProxy(context);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
