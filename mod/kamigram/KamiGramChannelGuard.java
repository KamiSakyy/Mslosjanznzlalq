package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.lang.ref.WeakReference;

/**
 * KamiGram r81: mandatory AsuMeo subscription gate.
 *
 * A successful join request is not treated as proof by itself: the channel is
 * resolved again and the account is released only after Telegram reports that
 * the account is no longer left/kicked. Until that confirmation a non-cancelable
 * gate stays above the application. This also handles an unsubscribe: the next
 * periodic/on-resume verification shows the same gate and retries the join.
 */
public final class KamiGramChannelGuard {

    public static final String CHANNEL_USERNAME = "AsuMeo";
    public static final String CHANNEL_URL = "https://t.me/AsuMeo";

    private static final long RECHECK_INTERVAL = 15 * 1000L;
    private static final long RETRY_DELAY = 15 * 1000L;

    private static final boolean[] VERIFIED = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] CHECKING = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long[] LAST_CHECK_AT = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private static WeakReference<AlertDialog> gateReference;

    private KamiGramChannelGuard() {
    }

    /**
     * Called from LaunchActivity.onResume. Before login this is a no-op. After
     * login it resolves @AsuMeo, joins it automatically when needed, and keeps
     * the app locked until the membership check succeeds.
     */
    public static void check(final Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) {
                return;
            }
            final int account = UserConfig.selectedAccount;
            if (account < 0 || account >= VERIFIED.length) {
                return;
            }
            final UserConfig config = UserConfig.getInstance(account);
            if (config == null || !config.isClientActivated()) {
                return;
            }
            final long now = System.currentTimeMillis();
            if (VERIFIED[account] && now - LAST_CHECK_AT[account] < RECHECK_INTERVAL) {
                return;
            }
            if (CHECKING[account]) {
                if (!VERIFIED[account]) {
                    showGate(activity, account, "Проверяем подписку на канал AsuMeo…");
                }
                return;
            }

            CHECKING[account] = true;
            LAST_CHECK_AT[account] = now;
            if (!VERIFIED[account]) {
                showGate(activity, account, "Для работы KamiGram нужна подписка на @AsuMeo.");
            }

            final TLRPC.TL_contacts_resolveUsername request =
                new TLRPC.TL_contacts_resolveUsername();
            request.username = CHANNEL_USERNAME;
            ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> {
                if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                    failAndRetry(activity, account, "Канал AsuMeo сейчас недоступен. Подписка не подтверждена.");
                    return;
                }
                final TLRPC.TL_contacts_resolvedPeer resolved =
                    (TLRPC.TL_contacts_resolvedPeer) response;
                final TLRPC.Chat channel = resolved.chats != null && !resolved.chats.isEmpty()
                    ? resolved.chats.get(0) : null;
                if (channel == null) {
                    failAndRetry(activity, account, "Канал AsuMeo не найден. Повторяем проверку.");
                    return;
                }
                if (!channel.left && !channel.kicked) {
                    AndroidUtilities.runOnUIThread(() -> markVerified(activity, account));
                    return;
                }
                autoJoin(activity, account, channel);
            });
        } catch (Throwable throwable) {
            final int account = UserConfig.selectedAccount;
            if (account >= 0 && account < CHECKING.length) {
                CHECKING[account] = false;
                VERIFIED[account] = false;
            }
            FileLog.e(throwable);
            showGate(activity, account, "Не удалось подтвердить подписку. Повторите попытку.");
            scheduleRetry(activity, account);
        }
    }

    /** KAMIGRAM_AUTO_JOIN_R78 + KAMIGRAM_CHANNEL_GATE_R81. */
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
                    CHECKING[account] = false;
                    VERIFIED[account] = false;
                    LAST_CHECK_AT[account] = 0;
                    if (error == null) {
                        /* Join succeeded, but only the follow-up resolve is the
                           confirmation that unlocks the application. */
                        check(activity);
                    } else {
                        showGate(activity, account, "Нажмите «Повторить», чтобы оформить подписку на @AsuMeo.");
                        scheduleRetry(activity, account);
                    }
                });
            });
        } catch (Throwable throwable) {
            CHECKING[account] = false;
            VERIFIED[account] = false;
            FileLog.e(throwable);
            failAndRetry(activity, account, "Не удалось выполнить подписку. Повторяем автоматически.");
        }
    }

    private static void markVerified(Activity activity, int account) {
        CHECKING[account] = false;
        VERIFIED[account] = true;
        LAST_CHECK_AT[account] = System.currentTimeMillis();
        try {
            final android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            if (prefs != null) {
                prefs.edit().putLong("kamigram_channel_joined_" + account,
                    LAST_CHECK_AT[account]).apply();
            }
        } catch (Throwable ignore) {
        }
        dismissGate();
    }

    private static void failAndRetry(final Activity activity, final int account, final String message) {
        AndroidUtilities.runOnUIThread(() -> {
            if (account >= 0 && account < CHECKING.length) {
                CHECKING[account] = false;
                VERIFIED[account] = false;
            }
            showGate(activity, account, message);
            scheduleRetry(activity, account);
        });
    }

    private static void scheduleRetry(final Activity activity, final int account) {
        if (activity == null || activity.isFinishing() || account < 0 || account >= CHECKING.length) {
            return;
        }
        LAST_CHECK_AT[account] = 0;
        AndroidUtilities.runOnUIThread(() -> {
            if (!activity.isFinishing() && UserConfig.selectedAccount == account && !VERIFIED[account]) {
                check(activity);
            }
        }, RETRY_DELAY);
    }

    /** A non-cancelable gate is the actual use restriction, not just a notice. */
    private static void showGate(final Activity activity, final int account, final String message) {
        if (activity == null || activity.isFinishing() || account < 0 || account >= CHECKING.length) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final AlertDialog existing = gateReference == null ? null : gateReference.get();
                if (existing != null && existing.isShowing()) {
                    return;
                }
                final AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Подписка обязательна")
                    .setMessage(message)
                    .setPositiveButton("Повторить", (d, which) -> {
                        CHECKING[account] = false;
                        LAST_CHECK_AT[account] = 0;
                        check(activity);
                    })
                    .setNeutralButton("Открыть AsuMeo", (d, which) -> {
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL)));
                        } catch (Throwable throwable) {
                            FileLog.e(throwable);
                        }
                    })
                    .create();
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                gateReference = new WeakReference<>(dialog);
                dialog.show();
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        });
    }

    private static void dismissGate() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final AlertDialog dialog = gateReference == null ? null : gateReference.get();
                gateReference = null;
                if (dialog != null) {
                    dialog.dismiss();
                }
            } catch (Throwable ignore) {
            }
        });
    }
}
