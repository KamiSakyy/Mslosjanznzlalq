package org.telegram.messenger.kamigram;

import android.app.Activity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/**
 * KamiGram: mandatory subscription gate for the public AsuMeo channel.
 *
 * The gate is intentionally automatic. After an account becomes active we
 * resolve @AsuMeo and call channels.joinChannel ourselves when the account is
 * not a member. There is no dismissible dialog and no extra manual tap that
 * can leave a user in a half-subscribed state.
 */
public final class KamiGramChannelGuard {

    public static final String CHANNEL_USERNAME = "AsuMeo";
    public static final String CHANNEL_URL = "https://t.me/AsuMeo";

    private static final long RECHECK_INTERVAL = 60 * 1000L;
    private static final long RETRY_DELAY = 15 * 1000L;

    private static volatile boolean checking;
    private static volatile long lastCheckAt;
    private static volatile int lastAccount = -1;

    private KamiGramChannelGuard() {
    }

    /**
     * Called from LaunchActivity.onResume. Before login this is a no-op; after
     * login it silently resolves the channel and joins it when necessary.
     */
    public static void check(final Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) {
                return;
            }
            final int account = UserConfig.selectedAccount;
            final UserConfig config = UserConfig.getInstance(account);
            if (config == null || !config.isClientActivated()) {
                return;
            }
            final long now = System.currentTimeMillis();
            if (checking || (lastAccount == account && now - lastCheckAt < RECHECK_INTERVAL)) {
                return;
            }
            checking = true;
            lastAccount = account;
            lastCheckAt = now;

            final TLRPC.TL_contacts_resolveUsername request =
                new TLRPC.TL_contacts_resolveUsername();
            request.username = CHANNEL_USERNAME;
            ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> {
                if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                    checking = false;
                    scheduleRetry(activity, account);
                    return;
                }
                final TLRPC.TL_contacts_resolvedPeer resolved =
                    (TLRPC.TL_contacts_resolvedPeer) response;
                final TLRPC.Chat channel = resolved.chats != null && !resolved.chats.isEmpty()
                    ? resolved.chats.get(0) : null;
                if (channel == null) {
                    checking = false;
                    scheduleRetry(activity, account);
                    return;
                }
                if (!channel.left && !channel.kicked) {
                    checking = false;
                    markJoined(account);
                    return;
                }
                autoJoin(activity, account, channel);
            });
        } catch (Throwable throwable) {
            checking = false;
            FileLog.e(throwable);
            scheduleRetry(activity, UserConfig.selectedAccount);
        }
    }

    /** KAMIGRAM_AUTO_JOIN_R78: subscribe without a dialog or manual button. */
    private static void autoJoin(final Activity activity, final int account,
                                 final TLRPC.Chat channel) {
        try {
            final TLRPC.TL_inputChannel input = new TLRPC.TL_inputChannel();
            input.channel_id = channel.id;
            input.access_hash = channel.access_hash;
            final TLRPC.TL_channels_joinChannel request = new TLRPC.TL_channels_joinChannel();
            request.channel = input;
            ConnectionsManager.getInstance(account).sendRequestTyped(request, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    checking = false;
                    if (error == null) {
                        markJoined(account);
                    } else {
                        scheduleRetry(activity, account);
                    }
                });
            });
        } catch (Throwable throwable) {
            checking = false;
            FileLog.e(throwable);
            scheduleRetry(activity, account);
        }
    }

    private static void scheduleRetry(final Activity activity, final int account) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        /* Do not spin on an offline account, but retry automatically once the
           route is likely available. A failed join never opens a fake success UI. */
        lastCheckAt = System.currentTimeMillis() - RECHECK_INTERVAL;
        AndroidUtilities.runOnUIThread(() -> {
            if (UserConfig.selectedAccount == account) {
                check(activity);
            }
        }, RETRY_DELAY);
    }

    private static void markJoined(int account) {
        try {
            final android.content.SharedPreferences prefs =
                MessagesController.getGlobalMainSettings();
            if (prefs != null) {
                prefs.edit().putLong("kamigram_channel_joined_" + account,
                    System.currentTimeMillis()).apply();
            }
        } catch (Throwable ignore) {
        }
    }
}
