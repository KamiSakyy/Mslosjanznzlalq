package org.telegram.messenger.kamigram;

import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;

/**
 * KamiGram «призрак» (невидимка).
 *
 * Что делает:
 *   * не отправляет подтверждения прочтения, «печатает» и статус «в сети»
 *     (запросы отсекаются в {@link KamiGramNetFilter});
 *   * невидимка для историй — родной режим Telegram `stories.activateStealthMode`;
 *   * иконка призрака стоит в шапке чата РЯДОМ с «тремя точками»: касание
 *     включает и выключает призрак, состояние видно по цвету иконки;
 *   * честный онлайн: когда сообщение уходит НЕ отложенным, приложение
 *     действительно выходит в сеть на секунду — это настоящий статус
 *     (никаких поддельных надписей «был в сети»).
 *
 * Раньше «тихая отправка» делала сообщение отложенным на 5 секунд — из-за
 * этого ломались отложенные и запланированные сообщения («message id нету»).
 * Теперь отправка обычная, а невидимость даёт сам призрак.
 */
public final class KamiGramGhost {

    /** Идентификатор пункта-иконки призрака в шапке чата. */
    public static final int HEADER_ITEM_ID = 0x4B4701;

    /** Сколько миллисекунд показываем честный онлайн при отправке. */
    private static final long ONLINE_PULSE_MS = 1000;

    private static boolean stealthSent;

    /** Пока идёт «честный онлайн» — запрос статуса разрешён. */
    private static volatile long pulseUntil;
    private static volatile boolean statusAllowed;

    /** Время последнего реального выхода в сеть (для подписи в центре мода). */
    private static long lastOnline;
    private static boolean lastOnlineLoaded;

    private KamiGramGhost() {
    }

    // ------------------------------------------------------------------ время в сети

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
     * Честный онлайн: нормальное (не отложенное) сообщение отправлено — реально
     * выходим в сеть на секунду и сразу уходим обратно. Никаких поддельных
     * подписей: сервер видит настоящий заход.
     */
    public static void onRealSend(final int account) {
        if (!silentSending()) {
            return;
        }
        final int currentAccount = account >= 0 ? account : UserConfig.selectedAccount;
        statusAllowed = true;
        pulseUntil = SystemClock.elapsedRealtime() + ONLINE_PULSE_MS;
        try {
            final TL_account.updateStatus online = new TL_account.updateStatus();
            online.offline = false;
            ConnectionsManager.getInstance(currentAccount).sendRequest(online, null);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final TL_account.updateStatus offline = new TL_account.updateStatus();
                offline.offline = true;
                ConnectionsManager.getInstance(currentAccount).sendRequest(offline, null);
            } catch (Throwable ignore) {
            }
            statusAllowed = false;
            pulseUntil = 0;
        }, ONLINE_PULSE_MS + 100);
    }

    /** Разрешён ли сейчас запрос статуса «в сети» (нужно сетевому фильтру). */
    public static boolean statusAllowed() {
        return statusAllowed || SystemClock.elapsedRealtime() < pulseUntil;
    }

    // ------------------------------------------------------------------ иконка в шапке чата

    /** Пункт-иконка призрака РЯДОМ с «тремя точками» (не внутри меню). */
    public static ActionBarMenuItem addHeaderItem(ActionBarMenu menu, Theme.ResourcesProvider provider) {
        try {
            final ActionBarMenuItem item = menu.addItem(HEADER_ITEM_ID, org.telegram.messenger.R.drawable.kamigram_ghost);
            item.setContentDescription("Призрак");
            bindHeader(item, provider);
            return item;
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return null;
        }
    }

    /** Привязка касания и отрисовка состояния. */
    public static void bindHeader(final ActionBarMenuItem item, final Theme.ResourcesProvider provider) {
        if (item == null) {
            return;
        }
        item.setOnClickListener(v -> toggleFromHeader(item, provider));
        refreshHeader(item, provider);
    }

    private static void toggleFromHeader(ActionBarMenuItem item, Theme.ResourcesProvider provider) {
        final boolean enabled = !KamiGramConfig.value(KamiGramConfig.KEY_GHOST);
        KamiGramConfig.set(KamiGramConfig.KEY_GHOST, enabled);
        refreshHeader(item, provider);
        KamiGramUi.notify(item.getContext(), enabled ? "Призрак включён" : "Призрак выключен");
    }

    /** Состояние видно сразу: включён — акцентный цвет, выключен — приглушённая иконка. */
    public static void refreshHeader(ActionBarMenuItem item, Theme.ResourcesProvider provider) {
        if (item == null) {
            return;
        }
        final boolean enabled = KamiGramConfig.value(KamiGramConfig.KEY_GHOST);
        try {
            int color;
            if (enabled) {
                color = KamiGramUi.accent();
            } else if (provider != null) {
                color = provider.getColor(Theme.key_actionBarDefaultIcon);
            } else {
                color = Theme.getColor(Theme.key_actionBarDefaultIcon);
            }
            item.setIconColor(color);
        } catch (Throwable ignore) {
        }
        final View icon = item.getIconView();
        if (icon != null) {
            icon.setAlpha(enabled ? 1f : 0.6f);
        }
    }

    // ------------------------------------------------------------------ обычные режимы

    /**
     * Дата отправки. Оставлено для совместимости: раньше призрак делал сообщение
     * отложенным (+5 секунд), из-за чего ломались запланированные сообщения.
     * Теперь дата никогда не подменяется — сообщение уходит как обычно.
     */
    public static int sendDate(int scheduleDate) {
        return scheduleDate;
    }

    /**
     * Тихая отправка: призрак включён и «не выходить в сеть» разрешено.
     * Само сообщение отправляется обычным способом — невидимость даёт призрак.
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
            if (!KamiGramConfig.ghostMode()) {
                return "призрак выключен";
            }
            return "призрак включён · удалённых в журнале: " + KamiGramDeleted.size();
        } catch (Throwable ignore) {
            return "призрак";
        }
    }

    /** Запуск: невидимка для историй. */
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
                // тихо
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
