package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

/**
 * Persistent local copy of Premium peer colours.
 *
 * Telegram may replace the current user with a server response after the colour
 * screen closes. The response is authoritative for the account, but it does not
 * know about KamiGram's local Premium unlock, so the selected colour/background
 * would disappear on the next refresh. This class stores the two PeerColor TL
 * objects verbatim in the account's UserConfig preferences and restores them
 * whenever that account receives a new current user.
 */
public final class KamiGramPremiumState {

    private static final String VERSION = "kamigram_premium_state_v1_";
    private static final String OWNER = VERSION + "owner";
    private static final String COLOR_PRESENT = VERSION + "color_present";
    private static final String COLOR_DATA = VERSION + "color_data";
    private static final String PROFILE_PRESENT = VERSION + "profile_present";
    private static final String PROFILE_DATA = VERSION + "profile_data";

    private KamiGramPremiumState() {
    }

    /** Save an explicit state, including an explicit removal (present=false). */
    public static void save(UserConfig config, TLRPC.User user) {
        if (config == null || user == null) {
            return;
        }
        try {
            final SharedPreferences preferences = config.getPreferences();
            if (preferences == null) {
                return;
            }
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putLong(OWNER, user.id);
            putPeer(editor, COLOR_PRESENT, COLOR_DATA, user.color);
            putPeer(editor, PROFILE_PRESENT, PROFILE_DATA, user.profile_color);
            /* commit is intentional: PeerColorActivity is followed by a user
               refresh and the process may be killed before apply() reaches disk. */
            editor.commit();
        } catch (Throwable ignore) {
        }
    }

    /** Restore only state belonging to this account's current user. */
    public static void restore(UserConfig config, TLRPC.User user) {
        if (config == null || user == null) {
            return;
        }
        try {
            final SharedPreferences preferences = config.getPreferences();
            if (preferences == null || !preferences.contains(OWNER)
                || preferences.getLong(OWNER, 0L) != user.id) {
                return;
            }
            user.color = getPeer(preferences, COLOR_PRESENT, COLOR_DATA, user.color);
            user.profile_color = getPeer(preferences, PROFILE_PRESENT, PROFILE_DATA, user.profile_color);
            if (preferences.getBoolean(COLOR_PRESENT, true)) {
                user.flags2 |= 1 << 8;
            } else {
                user.flags2 &= ~(1 << 8);
            }
            if (preferences.getBoolean(PROFILE_PRESENT, true)) {
                user.flags2 |= 1 << 9;
            } else {
                user.flags2 &= ~(1 << 9);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void putPeer(SharedPreferences.Editor editor, String presentKey,
                                String dataKey, TLRPC.PeerColor peer) {
        if (peer == null) {
            editor.putBoolean(presentKey, false).remove(dataKey);
            return;
        }
        editor.putBoolean(presentKey, true);
        final String encoded = encode(peer);
        if (encoded == null) {
            editor.remove(dataKey);
        } else {
            editor.putString(dataKey, encoded);
        }
    }

    private static TLRPC.PeerColor getPeer(SharedPreferences preferences, String presentKey,
                                           String dataKey, TLRPC.PeerColor fallback) {
        if (!preferences.getBoolean(presentKey, true)) {
            return null;
        }
        final String encoded = preferences.getString(dataKey, null);
        if (encoded == null || encoded.length() == 0) {
            return fallback;
        }
        try {
            final byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            final SerializedData data = new SerializedData(bytes);
            final TLRPC.PeerColor result = TLRPC.PeerColor.TLdeserialize(data, data.readInt32(false), false);
            data.cleanup();
            return result == null ? fallback : result;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private static String encode(TLRPC.PeerColor peer) {
        try {
            final SerializedData data = new SerializedData(peer.getObjectSize());
            peer.serializeToStream(data);
            final String result = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
            data.cleanup();
            return result;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
