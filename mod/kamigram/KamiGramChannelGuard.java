package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

/**
 * Compatibility holder for the overlay permission helper.
 *
 * KamiGram never requires a subscription, auto-joins a channel, or blocks the
 * Telegram UI behind a gate. The old AsuMeo subscription guard was removed;
 * the handle is exposed only as a developer link in KamiGram settings.
 */
public final class KamiGramChannelGuard {

    public static final String CHANNEL_USERNAME = "AsuMeo";
    public static final String CHANNEL_URL = "https://t.me/AsuMeo";

    private KamiGramChannelGuard() {
    }

    /**
     * Kept for the native floating-window feature. This only opens Android's
     * own permission screen; it never performs a join or shows a blocking gate.
     */
    public static void requestOverlayPermission(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            final Intent intent = new Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable ignore) {
            try {
                final Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    /** No-op compatibility hook: there is deliberately no subscription gate. */
    public static void check(Activity activity) {
    }
}
