package org.telegram.messenger.kamigram;

import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;

/**
 * KamiGram: «призрак» — сделан по образцу AyuGram (github.com/AyuGram/AyuGram4A).
 *
 * Главное отличие от прежней версии: запросы НЕ выбрасываются наугад по имени
 * класса. Перехват идёт в одной точке — там, где Telegram реально отправляет
 * запрос в сеть ({@code ConnectionsManager.sendRequestInternal}), и по конкретным
 * типам запросов:
 *
 *   * «печатает» и «загружаю файл» ({@code messages.setTyping}) — не уходят;
 *   * статус «в сети» ({@code account.updateStatus}) — уходит всегда как
 *     «не в сети», поэтому собеседник видит только «был(а) недавно»;
 *   * подтверждения прочтения ({@code messages.readHistory},
 *     {@code channels.readHistory}, {@code messages.readEncryptedHistory},
 *     {@code messages.readDiscussion}) — не уходят, но приложение получает
 *     «пустой» ответ, поэтому непрочитанные очищаются ЛОКАЛЬНО и счётчики
 *     работают как обычно (в прежней версии запрос просто выбрасывался, из-за
 *     чего призрак выглядел нерабочим).
 *
 * Одноразовые и ограниченные по времени сообщения (просмотр без пометки):
 *   * запросы «я посмотрел» ({@code messages.readMessageContents},
 *     {@code channels.readMessageContents}) не уходят — сервер считает, что
 *     сообщение не просмотрено, и не запускает его удаление.
 *
 * Иконка призрака живёт в шапке ГЛАВНОГО экрана, рядом с «⋮» — там, где
 * название приложения. В чатах и каналах её нет.
 */
public final class KamiGramGhost {

    /** Идентификатор пункта-иконки призрака. */
    public static final int HEADER_ITEM_ID = 0x4B4701;

    private static WeakReference<ActionBarMenuItem> headerItem = new WeakReference<>(null);
    private static WeakReference<Theme.ResourcesProvider> headerProvider = new WeakReference<>(null);

    private KamiGramGhost() {
    }

    // ------------------------------------------------------------------ сеть

    /**
     * Перехват запроса перед отправкой.
     *
     * @return true — запрос обработан здесь, в сеть его отправлять не нужно.
     */
    public static boolean interceptRequest(TLObject object, RequestDelegate onComplete) {
        try {
            if (object == null) {
                return false;
            }
            final boolean ghost = KamiGramConfig.ghostMode();

            if (ghost && (object instanceof TLRPC.TL_messages_setTyping
                || object instanceof TLRPC.TL_messages_setEncryptedTyping)) {
                return true;
            }

            if (ghost && object instanceof TL_account.updateStatus) {
                // призрак: сервер всегда видит «не в сети»
                ((TL_account.updateStatus) object).offline = true;
                return false;
            }

            if (ghost && isReadHistory(object)) {
                answerLocally(onComplete);
                return true;
            }

            if (viewOnce() && isReadContents(object)) {
                // «просмотрено» не уходит: сервер не удаляет одноразовое сообщение
                answerLocally(onComplete);
                return true;
            }
            return false;
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return false;
        }
    }

    /** Смотреть одноразовые сообщения, не отправляя серверу «просмотрено». */
    public static boolean viewOnce() {
        try {
            return KamiGramConfig.viewOnce();
        } catch (Throwable ignore) {
            return true;
        }
    }

    private static boolean isReadHistory(TLObject object) {
        return object instanceof TLRPC.TL_messages_readHistory
            || object instanceof TLRPC.TL_channels_readHistory
            || object instanceof TLRPC.TL_messages_readEncryptedHistory
            || object instanceof TLRPC.TL_messages_readDiscussion;
    }

    private static boolean isReadContents(TLObject object) {
        return object instanceof TLRPC.TL_messages_readMessageContents
            || object instanceof TLRPC.TL_channels_readMessageContents;
    }

    /**
     * «Пустой» ответ вместо настоящего: приложение считает, что запрос выполнен,
     * и продолжает обычную работу (отмечает прочитанное локально), а сервер о
     * прочтении не знает.
     */
    private static void answerLocally(RequestDelegate onComplete) {
        if (onComplete == null) {
            return;
        }
        try {
            final TLRPC.TL_messages_affectedMessages fake = new TLRPC.TL_messages_affectedMessages();
            fake.pts = -1;
            fake.pts_count = 0;
            onComplete.run(fake, null);
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ иконка

    /** Пункт-иконка призрака рядом с «⋮» в шапке главного экрана. */
    public static ActionBarMenuItem addHeaderItem(ActionBarMenu menu, Theme.ResourcesProvider provider) {
        try {
            final ActionBarMenuItem item = menu.addItem(HEADER_ITEM_ID,
                org.telegram.messenger.R.drawable.kamigram_ghost);
            item.setContentDescription("Призрак");
            headerItem = new WeakReference<>(item);
            headerProvider = new WeakReference<>(provider);
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
        item.setOnClickListener(v -> toggle(item));
        refreshHeader(item, provider);
    }

    private static void toggle(ActionBarMenuItem item) {
        final boolean enabled = !KamiGramConfig.value(KamiGramConfig.KEY_GHOST);
        KamiGramConfig.set(KamiGramConfig.KEY_GHOST, enabled);
        refreshHeader(item, null);
        KamiGramUi.notify(item.getContext(), enabled ? "Призрак включён" : "Призрак выключен");
    }

    /** Обновить иконку (например, после переключения в настройках мода). */
    public static void refreshAll() {
        final ActionBarMenuItem item = headerItem == null ? null : headerItem.get();
        if (item != null) {
            refreshHeader(item, headerProvider == null ? null : headerProvider.get());
        }
    }

    /**
     * Состояние видно сразу:
     *   * призрак ВЫКЛЮЧЕН — серая иконка;
     *   * призрак ВКЛЮЧЁН — ЗЕЛЁНАЯ иконка (изумруд Yoru #88E0A0).
     */
    public static void refreshHeader(ActionBarMenuItem item, Theme.ResourcesProvider provider) {
        if (item == null) {
            return;
        }
        final boolean enabled = KamiGramConfig.value(KamiGramConfig.KEY_GHOST);
        try {
            item.setIconColor(enabled ? ThemeHook.YORU_EMERALD : ThemeHook.YORU_MUTED);
        } catch (Throwable ignore) {
        }
        final View icon = item.getIconView();
        if (icon != null) {
            icon.setAlpha(enabled ? 1f : 0.75f);
        }
    }

    // ------------------------------------------------------------------ прочее

    /** Тихая отправка: призрак включён. Сообщение уходит как обычно. */
    public static boolean silentSending() {
        try {
            return KamiGramConfig.ghostMode();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Дата отправки никогда не подменяется (иначе ломались отложенные). */
    public static int sendDate(int scheduleDate) {
        return scheduleDate;
    }

    // ------------------------------------------------------------------ совместимость

    private static int account = 0;
    private static long lastOnline = 0;

    /** Запуск: запоминаем аккаунт (расписание больше не подменяется, ничего не планируем). */
    public static void onAppStarted(int currentAccount) {
        account = currentAccount;
    }

    /** Обычная отправка: выходим в сеть честно — статус уйдёт на сервер обычным путём. */
    public static void onRealSend(int currentAccount) {
        account = currentAccount;
        lastOnline = System.currentTimeMillis() / 1000L;
    }

    /** Отметка «были в сети» — нужна для честного времени в своём профиле. */
    public static void markOnline(long when) {
        lastOnline = when;
    }

    /** Время, когда мы последний раз реально выходили в сеть (для профиля). */
    public static long lastOnlineTime() {
        return lastOnline;
    }

    /** Строка «был(а) недавно» для своего профиля (без выдуманного «в сети»). */
    public static String ownStatusText() {
        try {
            return org.telegram.messenger.LocaleController.formatDateOnline(lastOnline, null);
        } catch (Throwable ignore) {
            return "";
        }
    }

    /** Оставлено для совместимости со старым кодом: сейчас статус всегда «не в сети». */
    public static boolean statusAllowed() {
        return false;
    }
}
