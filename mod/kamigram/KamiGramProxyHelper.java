package org.telegram.messenger.kamigram;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KamiGram: proxy + login helper.
 *
 * - activates an MTProto / SOCKS / web proxy as soon as its link shows up in the
 *   clipboard (copy the link anywhere, open the app - the proxy is already on);
 * - turns a clearly dead proxy off automatically, but never while the proxy is
 *   still connecting and never in the middle of a login attempt;
 * - explains a stuck login: connection state, proxy, server answer, signature and
 *   build info - with one tap to copy it.
 */
public final class KamiGramProxyHelper {

    private static final Pattern PROXY_LINK = Pattern.compile(
        "(?i)(?:https?://)?(?:t\\.me|telegram\\.me|telegram\\.dog)/(?:proxy|socks|webproxy)\\?[^\\s\"'<>]*"
            + "|tg://(?:proxy|socks|webproxy)\\?[^\\s\"'<>]*"
    );

    /** last link we already activated, so we do not restart the same proxy over and over */
    private static String lastActivatedLink;

    private static final long PROXY_WATCH_DELAY = 25_000L;

    /**
     * Login trace: where the last login attempt got stuck.
     * 0 = nothing, 1 = button pressed, 4 = code request sent, 5 = server answered,
     * 9 = the press was ignored because another request is still running.
     */
    private static volatile int loginStage;
    private static volatile String loginStageInfo;

    public static void traceLogin(int stage, String info) {
        loginStage = stage;
        loginStageInfo = info;
    }

    public static int loginStage() {
        return loginStage;
    }

    /** Human readable step, so the user can see exactly where the login stopped. */
    public static String loginStageText() {
        switch (loginStage) {
            case 1:
                return "Step: button pressed, request is being prepared.";
            case 4:
                return "Step: code request sent - waiting for the server answer.";
            case 5:
                return "Step: server answered" + (loginStageInfo != null && loginStageInfo.length() > 0 ? " (" + loginStageInfo + ")" : "") + ".";
            case 9:
                return "Step: the press was ignored - another request is still running.";
            default:
                return "Step: no request was sent yet.";
        }
    }

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

    private static String connectionStateText(int account) {
        final int state = ConnectionsManager.getInstance(account).getConnectionState();
        switch (state) {
            case ConnectionsManager.ConnectionStateConnected:
                return "connected";
            case ConnectionsManager.ConnectionStateUpdating:
                return "connected (updating)";
            case ConnectionsManager.ConnectionStateConnecting:
                return "connecting to server";
            case ConnectionsManager.ConnectionStateConnectingToProxy:
                return "connecting to proxy";
            case ConnectionsManager.ConnectionStateWaitingForNetwork:
                return "waiting for network";
            default:
                return "unknown (" + state + ")";
        }
    }

    /**
     * Watches the connection: if a proxy is on but Telegram is not connected at all
     * after ~25 seconds (while the device itself is online and the proxy is not even
     * being tried), the proxy is switched off - a dead proxy must not block VPN or a
     * direct connection. A proxy that is still connecting is never dropped.
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
                disableProxy(context, "proxy did not answer in 25 s - switched off, direct connection is active");
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, PROXY_WATCH_DELAY);
    }

    /** SHA-256 of the signing certificate: shows whether the build carries the original Telegram key. */
    private static String signatureHash(Context context) {
        try {
            final PackageManager manager = context.getPackageManager();
            final PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] signatures = null;
            if (Build.VERSION.SDK_INT >= 28) {
                if (info.signingInfo != null) {
                    signatures = info.signingInfo.getApkContentsSigners();
                }
            } else {
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) {
                return "unknown";
            }
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(signatures[0].toByteArray());
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
                if (i == 7) {
                    break;
                }
            }
            return builder.toString();
        } catch (Throwable e) {
            return "unknown";
        }
    }

    /** Everything needed to understand why the login code does not arrive. */
    public static String loginDiagnostics(Context context) {
        final StringBuilder text = new StringBuilder();
        try {
            final int account = UserConfig.selectedAccount;
            text.append("Build: ").append(BuildVars.BUILD_VERSION_STRING)
                .append(", api_id ").append(BuildVars.APP_ID).append('\n');
            if (context != null) {
                text.append("Package: ").append(context.getPackageName()).append('\n');
                text.append("Cert SHA-256: ").append(signatureHash(context)).append("...\n");
            }
            text.append("SafetyNet key: ").append(BuildVars.SAFETYNET_KEY == null || BuildVars.SAFETYNET_KEY.length() == 0 ? "empty (Google integrity is not used)" : "set").append('\n');
            try {
                text.append("Google services: ")
                    .append(PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices() ? "yes" : "no")
                    .append('\n');
            } catch (Throwable ignore) {
            }
            text.append("Connection: ").append(connectionStateText(account)).append('\n');
            text.append("Online: ").append(ApplicationLoader.isNetworkOnline() ? "yes" : "no").append('\n');
            if (proxyEnabled() && SharedConfig.currentProxy != null && SharedConfig.currentProxy.settings != null) {
                text.append("Proxy: ").append(SharedConfig.currentProxy.settings.getAddress())
                    .append(':').append(SharedConfig.currentProxy.settings.getPort()).append(" (on)\n");
            } else {
                text.append("Proxy: off\n");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return text.toString();
    }

    private static void copyToClipboard(Context context, String text) {
        try {
            final ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("KamiGram login", text));
                Toast.makeText(context, "KamiGram: copied", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * A stuck login is the worst case for a mod user: the button spins and nothing explains why.
     * This shows exactly what the app sees - connection state, proxy, server answer, build info -
     * and offers to retry, to drop the proxy or to copy the whole report.
     */
    public static void showLoginProblem(Context context, String serverAnswer, String details) {
        showLoginProblem(context, serverAnswer, details, null);
    }

    public static void showLoginProblem(Context context, String serverAnswer, String details, final Runnable onRetry) {
        try {
            if (context == null) {
                return;
            }
            final int account = UserConfig.selectedAccount;
            final StringBuilder text = new StringBuilder();
            text.append("KamiGram: no answer yet / ответа пока нет\n\n");
            if (serverAnswer != null && serverAnswer.length() > 0) {
                text.append("Server: ").append(serverAnswer).append('\n');
            }
            if (details != null && details.length() > 0) {
                text.append(details).append('\n');
            }
            text.append('\n').append(loginStageText()).append('\n');
            text.append('\n').append(loginDiagnostics(context));
            text.append("\nThe code is sent to your Telegram app (service message) or by SMS.\n");
            final String report = text.toString();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("KamiGram");
            builder.setMessage(report);
            builder.setPositiveButton("Retry login", (dialog, which) -> {
                if (onRetry != null) {
                    AndroidUtilities.runOnUIThread(onRetry, 300);
                }
            });
            if (proxyEnabled()) {
                builder.setNegativeButton("Proxy off", (dialog, which) ->
                    disableProxy(context, "proxy off - press the login button again"));
            } else {
                builder.setNegativeButton("Close", null);
            }
            builder.setNeutralButton("Copy", (dialog, which) -> copyToClipboard(context, report));
            builder.show();
        } catch (Throwable e) {
            FileLog.e(e);
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
            FileLog.e(e);
            return false;
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
