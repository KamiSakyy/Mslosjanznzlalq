package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stories;

/**
 * KamiGram «призрак» для историй.
 *
 * Telegram умеет режим невидимки для историй (`stories.activateStealthMode`):
 * просмотры чужих историй не записываются - автор не видит, что мы смотрели.
 * Мод включает этот режим сам при запуске, если включён «призрак» и «призрак для историй».
 */
public final class KamiGramGhost {

    private static boolean stealthSent;

    private KamiGramGhost() {
    }

    /** Вызывается при старте приложения: включает невидимку для историй. */
    /** Задержка «тихой» отправки при призраке (секунды). */
    public static final int SILENT_DELAY = 5;

    /** Время последнего выхода в сеть (по нашим отметкам) — видно в профиле. */
    private static long lastOnline;
    private static boolean lastOnlineLoaded;

    private static android.content.SharedPreferences prefs() {
        try {
            return org.telegram.messenger.ApplicationLoader.applicationContext
                .getSharedPreferences("kamigram_ghost", android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Своё время захода: сохраняется между запусками. */
    public static long lastOnlineTime() {
        if (!lastOnlineLoaded) {
            lastOnlineLoaded = true;
            try {
                final android.content.SharedPreferences preferences = prefs();
                if (preferences != null) {
                    lastOnline = preferences.getLong("last_online", 0);
                }
            } catch (Throwable ignore) {
            }
        }
        return lastOnline;
    }

    /** Отметка «я был онлайн»: обновляется, когда пользователь реально заходил. */
    public static void markOnline(long unixTime) {
        if (unixTime <= 0) {
            return;
        }
        lastOnline = unixTime;
        lastOnlineLoaded = true;
        try {
            final android.content.SharedPreferences preferences = prefs();
            if (preferences != null) {
                preferences.edit().putLong("last_online", unixTime).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Свой статус для профиля: при призраке — родная строка Telegram
     * «был(а) в 12:00» (рисует сам Telegram, это НЕ своя надпись), иначе обычное
     * «в сети». Так в профиле не остаётся вечного «в сети» при включённом призраке.
     */
    public static CharSequence ownStatusText() {
        try {
            if (KamiGramConfig.ghostMode()) {
                final long time = lastOnlineTime();
                if (time > 0) {
                    return org.telegram.messenger.LocaleController.formatDateOnline(time, null);
                }
            }
            return org.telegram.messenger.LocaleController.getString(
                org.telegram.messenger.R.string.Online);
        } catch (Throwable ignore) {
            return "";
        }
    }

    /**
     * Тихая отправка: при включённом призраке сообщения уходят отложенными
     * (через планировщик Telegram), поэтому статус «в сети» не появляется.
     */
    public static boolean silentSending() {
        try {
            return KamiGramConfig.ghostMode() && KamiGramConfig.ghostSend();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Короткая подпись состояния для центра мода. */
    public static String statusText() {
        try {
            return KamiGramConfig.ghostMode()
                ? "призрак включён · журнал удалённых: " + KamiGramDeleted.size()
                : "призрак выключен";
        } catch (Throwable ignore) {
            return "призрак";
        }
    }

    public static void onAppStarted(int account) {
        try {
            if (!KamiGramConfig.ghostMode() || !KamiGramConfig.storiesStealth() || stealthSent) {
                return;
            }
            final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
            if (connectionsManager == null) {
                return;
            }
            final TL_stories.TL_stories_activateStealthMode request = new TL_stories.TL_stories_activateStealthMode();
            request.past = true;
            request.future = true;
            stealthSent = true;
            connectionsManager.sendRequest(request, (response, error) -> {
                if (error != null) {
                    // тихо: сообщения в лог не пишем (просьба «убери все логи»)
                } else {
                    // тихо
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
