package org.telegram.messenger.kamigram;

import android.app.Activity;

/**
 * r83: the old mandatory AsuMeo subscription gate is removed completely.
 *
 * The channel URL is a developer link exposed only in the KamiGram settings.
 * It must never block launch, sending, reading, push delivery, or any other
 * Telegram operation. The class remains as a no-op compatibility shim because
 * older patch stages may still contain the historical call site.
 */
public final class KamiGramChannelGuard {

    public static final String CHANNEL_USERNAME = "AsuMeo";
    public static final String CHANNEL_URL = "https://t.me/AsuMeo";

    private KamiGramChannelGuard() {
    }

    /**
     * KAMIGRAM_CHANNEL_GATE_R81 / KAMIGRAM_AUTO_JOIN_R78 compatibility marker.
     * KAMIGRAM_CHANNEL_GATE_R82_DISABLED: intentionally no-op; no subscription
     * request, dialog, retry, or use restriction is allowed anymore.
     */
    public static void check(Activity activity) {
        // Deliberately empty. The URL is a settings action only.
    }
}
