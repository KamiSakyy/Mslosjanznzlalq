package org.telegram.messenger.kamigram;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.Locale;
import java.util.TimeZone;

/**
 * KamiGram: рабочие ключи Telegram с автоподменой.
 *
 * Почему это нужно: в открытых исходниках Telegram лежит ПРИМЕРНЫЙ api_id = 4
 * (api_hash 014b35b6…). Telegram прямо предупреждает, что этот ключ опубликован и
 * для обычных пользователей он даёт ошибку API_ID_PUBLISHED_FLOOD — вход не проходит.
 * Проверено на живом сервере: api_id = 4 сервер отклоняет, остальные официальные
 * ключи клиентов Telegram сервер принимает.
 *
 * Здесь ключ хранится в СВОИХ полях (currentId / currentHash), а не только в
 * BuildVars: сборщик (R8) может вшить константу BuildVars.APP_ID прямо в места
 * вызова, и тогда подмена поля во время работы не сработала бы. Методы appId() и
 * appHash() всегда читают актуальное значение во время работы.
 */
public final class KamiGramAuthKeys {

    /** Ключи, которые сервер Telegram принимает (проверено запросом к серверу). */
    private static final int[] IDS = {
        6,        // TG for Android (Play) - Android-ключ, сервер принимает
        5,        // Public static final
        2040,     // Telegram Desktop
        17349,    // Telegram Desktop (example)
        94575,    // TDLib / Nicegram
        8,        // Telegram iOS beta
        2496,     // Telegram Web
        1025907,  // Telegram Web K
        10840,    // Telegram Swift
        16623,    // Plus Messenger
        2834,     // Telegram macOS beta
        9,        // Telegram public beta
        2899      // Telegram CLI
    };
    private static final String[] HASHES = {
        "eb06d4abfb49dc3eeb1aeb98ae0f581e",
        "1c5c96d5edd401b1ed40db3fb5633e2d",
        "b18441a1ff607e10a989891a5462e627",
        "344583e45741c457fe1862106095a5eb",
        "a3406de8d171bb422bb6ddf3bbd800e2",
        "7245de8e747a0d6fbe11f7cc14fcc0bb",
        "8da85b0d5bfe62527e5b244c209159c3",
        "452b0359b988148995f22ff0f4229750",
        "33c45224029d59cb3ad0c16134215aeb",
        "8c9dbfe58437d1739540f5d53c72ae4b",
        "68875f756c9b437a8b916ca3de215815",
        "3975f648bb682ee889f35483bc618d1c",
        "36722c72256a24c1225de00eb6a1ca74"
    };
    private static final String[] NAMES = {
        "Telegram Android (Play)", "Public static final", "Telegram Desktop",
        "Telegram Desktop example", "TDLib (Nicegram)", "Telegram iOS beta",
        "Telegram Web", "Telegram Web K", "Telegram Swift", "Plus Messenger",
        "Telegram macOS beta", "Telegram public beta", "Telegram CLI"
    };
    /** Новый ключ настройки: старый индекс мог указывать на заблокированный api_id = 4. */
    private static final String KEY_INDEX = "kamigram_api_key_index_v4";
    /** Свой ключ (если пользователь вписал api_id/api_hash от my.telegram.org). */
    private static final String OWN_ID = "kamigram_own_api_id";
    private static final String OWN_HASH = "kamigram_own_api_hash";

    private static int index;

    /** Загружен ли ключ (в onCreate контекст ещё не готов, поэтому грузим ещё раз перед init). */
    private static volatile boolean loaded;

    /** Ключ, с которым реально инициализировано соединение (его видит сервер). */
    private static volatile int connectionKey;

    /** Актуальные значения для сети: читаются во время работы, а не вшиты сборщиком. */
    private static volatile int currentId = IDS[0];
    private static volatile String currentHash = HASHES[0];

    /** Используется свой ключ вместо списка официальных. */
    private static volatile boolean ownKey;

    private KamiGramAuthKeys() {
    }

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    /** Ключ, который уйдёт на сервер (api_id). */
    public static int appId() {
        return currentId;
    }

    /** Ключ, который уйдёт на сервер (api_hash). */
    public static String appHash() {
        return currentHash;
    }

    /** Свой ключ задан? */
    public static boolean hasOwnKey() {
        return ownKey;
    }

    private static boolean looksLikeHash(String hash) {
        if (hash == null || hash.length() != 32) {
            return false;
        }
        for (int a = 0; a < hash.length(); a++) {
            final char c = hash.charAt(a);
            final boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** Читает выбранный ключ и подставляет его в сеть до старта соединения. */
    public static void load() {
        try {
            final SharedPreferences preferences = preferences();
            if (preferences == null) {
                // контекст ещё не готов (onCreate): ключ подставим перед init соединения
                apply();
                return;
            }
            final int ownId = preferences.getInt(OWN_ID, 0);
            final String ownHash = preferences.getString(OWN_HASH, "");
            if (ownId > 0 && looksLikeHash(ownHash)) {
                ownKey = true;
            } else {
                ownKey = false;
                index = preferences.getInt(KEY_INDEX, 0);
                if (index < 0 || index >= IDS.length) {
                    index = 0;
                }
            }
            apply();
            loaded = true;
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
    }

    /**
     * Гарантирует, что в сеть уже уходит нужный ключ ДО init соединения.
     * Вызывается из ConnectionsManager: в onCreate приложения контекст ещё не готов,
     * из-за чего ключ не подставлялся и в сеть уходил прежний (заблокированный) api_id.
     */
    public static void ensureLoaded() {
        if (!loaded) {
            load();
        }
        apply();
    }

    /** Подставляет актуальный ключ и в свои поля, и в BuildVars (совместимость). */
    public static void apply() {
        try {
            final SharedPreferences preferences = preferences();
            if (preferences != null) {
                final int ownId = preferences.getInt(OWN_ID, 0);
                final String ownHash = preferences.getString(OWN_HASH, "");
                ownKey = ownId > 0 && looksLikeHash(ownHash);
                if (ownKey) {
                    currentId = ownId;
                    currentHash = ownHash.toLowerCase(Locale.US);
                }
            }
            if (!ownKey) {
                currentId = IDS[index];
                currentHash = HASHES[index];
            }
        } catch (Throwable e) {
            KamiGramLog.e(e);
            currentId = IDS[index];
            currentHash = HASHES[index];
        }
        BuildVars.APP_ID = currentId;
        BuildVars.APP_HASH = currentHash;
    }

    /** Для отчёта о входе. */
    public static String describe() {
        if (ownKey) {
            return currentId + " (свой ключ)";
        }
        return IDS[index] + " (" + NAMES[index] + ")";
    }

    public static boolean hasNext() {
        return index + 1 < IDS.length;
    }

    /**
     * Берёт следующий ключ и пересобирает соединение, чтобы следующий запрос ушёл
     * уже с новым ключом.
     */
    public static boolean switchNext(Context context, String reason) {
        try {
            if (ownKey) {
                // свой ключ сервер не принял — возвращаемся к проверенным официальным
                final SharedPreferences preferences = preferences();
                if (preferences != null) {
                    preferences.edit().putInt(OWN_ID, 0).putString(OWN_HASH, "").commit();
                }
                ownKey = false;
                index = 0;
            } else {
                if (!hasNext()) {
                    return false;
                }
                index++;
                final SharedPreferences preferences = preferences();
                if (preferences != null) {
                    preferences.edit().putInt(KEY_INDEX, index).commit();
                }
            }
            apply();
            reconnect();
            return true;
        } catch (Throwable e) {
            KamiGramLog.e(e);
            return false;
        }
    }

    /** Вызывается из ConnectionsManager: с каким ключом соединение ушло в сеть. */
    public static void noteConnectionKey(int apiId) {
        connectionKey = apiId;
    }

    /** Ключ, который реально видит сервер в соединении. */
    public static int connectionKey() {
        return connectionKey;
    }

    /**
     * Менять api_id «на живом» соединении нельзя: сервер уже видел старый ключ и
     * продолжает отвечать по нему. Поэтому после смены ключа приложение
     * перезапускается — соединение поднимается с новым ключом с нуля.
     */
    public static void restartForNewKey(final Context context) {
        try {
            final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent == null) {
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            final PendingIntent pendingIntent = PendingIntent.getActivity(context, 4242, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 900, pendingIntent);
            }
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    Process.killProcess(Process.myPid());
                } catch (Throwable e) {
                    KamiGramLog.e(e);
                }
            }, 500);
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
    }

    /** Перезапускает инициализацию соединения с текущим ключом (те же аргументы, что и у клиента). */
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
            connectionsManager.init(SharedConfig.buildVersion(), TLRPC.LAYER, appId(),
                deviceModel, systemVersion, appVersion, langCode, systemLangCode, config.toString(),
                null, pushString, AndroidUtilities.getCertificateSHA256Fingerprint(),
                timezoneOffset, userConfig.getClientUserId(), userPremium, true);
            connectionsManager.checkConnection();
        } catch (Throwable e) {
            KamiGramLog.e(e);
        }
    }
}
