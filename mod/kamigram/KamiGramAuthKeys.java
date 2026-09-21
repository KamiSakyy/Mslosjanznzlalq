package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.TimeZone;

/**
 * KamiGram: official Telegram API keys with automatic failover.
 *
 * Telegram rejects a key it considers published/shared ("API_ID_PUBLISHED_FLOOD") and then
 * the login simply never completes. The open-source client ships with the Android key of the
 * official app, which is exactly the kind of key the server may refuse for a third-party build.
 *
 * KamiGram therefore carries the keys of several official Telegram clients and, if the server
 * refuses the current one, switches to the next one automatically and repeats the request.
 */
public final class KamiGramAuthKeys {

    /** Keys of official Telegram clients (Telegram Desktop, Android, X, Web, iOS, Web K, Swift). */
    private static final int[] IDS = {
        2040, 4, 21724, 2496, 8, 1025907, 10840
    };
    private static final String[] HASHES = {
        "b18441a1ff607e10a989891a5462e627",
        "014b35b6184100b085b0d0572f9b5103",
        "3e0cb5efcd52300aec5994fdfc5bdc16",
        "8da85b0d5bfe62527e5b244c209159c3",
        "7245de8e747a0d6fbe11f7cc14fcc0bb",
        "452b0359b988148995f22ff0f4229750",
        "33c45224029d59cb3ad0c16134215aeb"
    };
    private static final String[] NAMES = {
        "Telegram Desktop", "Telegram Android", "Telegram X", "Telegram Web",
        "Telegram iOS beta", "Telegram Web K", "Telegram Swift"
    };

    private static final String KEY_INDEX = "kamigram_api_key_index";

    private static int index;

    private KamiGramAuthKeys() {
    }

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    /** Reads the chosen key and puts it into BuildVars before the network layer starts. */
    public static void load() {
        try {
            final SharedPreferences preferences = preferences();
            index = preferences != null ? preferences.getInt(KEY_INDEX, 0) : 0;
            if (index < 0 || index >= IDS.length) {
                index = 0;
            }
            apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void apply() {
        BuildVars.APP_ID = IDS[index];
        BuildVars.APP_HASH = HASHES[index];
    }

    /** Human readable form for the login report. */
    public static String describe() {
        return IDS[index] + " (" + NAMES[index] + ")";
    }

    public static boolean hasNext() {
        return index + 1 < IDS.length;
    }

    /**
     * Switches to the next official key and rebuilds the connection so that the very next
     * request already goes out with the new key.
     */
    public static boolean switchNext(Context context, String reason) {
        try {
            if (!hasNext()) {
                return false;
            }
            index++;
            final SharedPreferences preferences = preferences();
            if (preferences != null) {
                preferences.edit().putInt(KEY_INDEX, index).commit();
            }
            apply();
            reconnect();
            if (context != null) {
                Toast.makeText(context, "KamiGram: " + reason + " - switched to the official key " + describe(),
                    Toast.LENGTH_LONG).show();
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Re-runs the connection init with the current key (same arguments as the client itself uses). */
    private static void reconnect() {
        try {
            final int account = UserConfig.selectedAccount;
            final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
            String deviceModel = Build.MANUFACTURER + Build.MODEL;
            String systemVersion = "SDK " + Build.VERSION.SDK_INT;
            String appVersion = "KamiGram";
            try {
                final PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                appVersion = pInfo.versionName + " (" + pInfo.versionCode + ")";
            } catch (Throwable ignore) {
            }
            String langCode = LocaleController.getLocaleStringIso639().toLowerCase();
            String systemLangCode = LocaleController.getSystemLocaleStringIso639().toLowerCase();
            File config = ApplicationLoader.getFilesDirFixed();
            if (account != 0) {
                config = new File(config, "account" + account);
            }
            String pushString = SharedConfig.pushString;
            if (pushString == null || pushString.length() == 0) {
                pushString = SharedConfig.pushStringStatus;
            }
            final int timezoneOffset = (TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings()) / 1000;
            final UserConfig userConfig = UserConfig.getInstance(account);
            final boolean userPremium = userConfig.getCurrentUser() != null && userConfig.getCurrentUser().premium;
            connectionsManager.init(SharedConfig.buildVersion(), TLRPC.LAYER, BuildVars.APP_ID,
                deviceModel, systemVersion, appVersion, langCode, systemLangCode, config.toString(),
                FileLog.getNetworkLogPath(), pushString, AndroidUtilities.getCertificateSHA256Fingerprint(),
                timezoneOffset, userConfig.getClientUserId(), userPremium, true);
            connectionsManager.checkConnection();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
