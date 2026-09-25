package org.telegram.messenger.kamigram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;


/**
 * Sakura: разовые действия при первом запуске.
 *
 * Что делает:
 *   * определяет первый запуск на этом устройстве и запоминает его;
 *   * при первом запуске НЕ показывает никакого «мастера знакомства» — это
 *     лишний экран, которого пользователь не просил: приложение сразу
 *     открывается на обычном экране входа;
 *   * проверяет, что аккаунты на месте: если пользователь был в аккаунте, но
 *     активный аккаунт вдруг пустой — переключаемся на тот, где он есть
 *     (страховка на случай сбоя после обновления версии);
 *   * всё работает в try/catch и не мешает запуску, что бы ни случилось.
 */
public final class KamiGramFirstRun {

    private static final String PREFS = "kamigram";
    private static final String KEY_INSTALLED = "installed_version";

    private KamiGramFirstRun() {
    }

    /** Вызывается один раз при создании главного экрана. */
    public static void check(Context context) {
        try {
            final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            final int current = Build.VERSION.SDK_INT;
            if (preferences != null) {
                final boolean first = preferences.getInt(KEY_INSTALLED, 0) == 0;
                if (first) {
                    preferences.edit().putInt(KEY_INSTALLED, Math.max(1, current)).apply();
                }
            }
            keepActiveAccount();
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /**
     * Страховка: если активный аккаунт пуст, а в одном из остальных есть вход,
     * делаем активным его — так после обновления версии пользователь не
     * оказывается на пустом экране входа.
     */
    private static void keepActiveAccount() {
        try {
            final UserConfig selected = UserConfig.getInstance(UserConfig.selectedAccount);
            if (selected != null && selected.isClientActivated()) {
                return;
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                final UserConfig config = UserConfig.getInstance(a);
                if (a != UserConfig.selectedAccount && config != null && config.isClientActivated()) {
                    UserConfig.selectedAccount = a;
                    UserConfig.getInstance(0).saveConfig(false);
                    return;
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Короткая подпись состояния для центра мода. */
    public static String describe() {
        try {
            final UserConfig config = UserConfig.getInstance(UserConfig.selectedAccount);
            return config != null && config.isClientActivated()
                ? "вход выполнен" : "требуется вход";
        } catch (Throwable ignore) {
            return "";
        }
    }
}
