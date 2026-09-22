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

    // ---------------------------------------------------- r68: отправка «отложкой»

    /**
     * Призрак: обычная отправка уходит через «Отложенные» — точно как в AyuGram
     * (SendMessagesHelper: scheduleDate = currentTime + 10 + 1).
     *
     * Почему так: если отправить сообщение сразу, собеседник получает его в ту же
     * секунду, когда мы нажали «отправить», — по времени прихода видно, что мы
     * были в сети. Отложенное сообщение уходит по расписанию, и по нему нельзя
     * понять, когда мы реально заходили.
     *
     * Telegram отправляет сообщение сразу, если до даты отправки меньше 10 секунд
     * (в этом случае приходит обычный updateNewMessage) — поэтому берём +10 и ещё
     * +1 секунду «окна ошибки». Картинке нужно время на загрузку (+10), документу
     * тоже (+15) — иначе сообщение может «созреть» раньше, чем загрузится файл.
     */
    public static int autoScheduleDate(int scheduleDate, long peer, boolean hasPhoto, boolean hasDocument) {
        try {
            if (!KamiGramConfig.ghostMode() || !KamiGramConfig.autoSchedule()) {
                return scheduleDate;
            }
            if (scheduleDate != 0) {
                return scheduleDate;
            }
            if (org.telegram.messenger.DialogObject.isEncryptedDialog(peer)) {
                // в секретных чатах отложенных сообщений нет
                return scheduleDate;
            }
            int date = org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime() + 10;
            date += 1; // окно ошибки: < 10 секунд — Telegram отправит немедленно
            if (hasDocument) {
                date += 15;
            } else if (hasPhoto) {
                date += 10;
            }
            markAutoScheduled();
            return date;
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return scheduleDate;
        }
    }

    /** То же для пересылок и медиа-пакетов: смотрим, что именно отправляем. */
    public static int autoScheduleDate(int scheduleDate, long peer, java.util.ArrayList<org.telegram.messenger.MessageObject> messages) {
        boolean photo = false;
        boolean document = false;
        try {
            if (messages != null) {
                for (int a = 0; a < messages.size(); a++) {
                    final org.telegram.messenger.MessageObject object = messages.get(a);
                    if (object == null || object.messageOwner == null) {
                        continue;
                    }
                    if (object.isPhoto()) {
                        photo = true;
                    } else if (object.isDocument() || object.isVideo() || object.isVoice() || object.isMusic()) {
                        document = true;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return autoScheduleDate(scheduleDate, peer, photo, document);
    }

    private static boolean autoScheduled;
    private static long autoScheduledTime;

    private static boolean autoScheduleHintShown;

    private static void markAutoScheduled() {
        autoScheduled = true;
        autoScheduledTime = System.currentTimeMillis();
        if (!autoScheduleHintShown) {
            // один раз за запуск: чтобы не было сюрприза «нажал отправить, а сообщения нет»
            autoScheduleHintShown = true;
            final android.content.Context context = org.telegram.messenger.ApplicationLoader.applicationContext;
            if (context != null) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                    KamiGramUi.notify(context, "Призрак: отправка через «Отложенные»"));
            }
        }
    }

    /**
     * Была ли последняя отправка переведена в «отложенную» (флаг сбрасывается
     * после прочтения — как AyuState.getAutomaticallyScheduled в AyuGram).
     */
    public static boolean consumeAutoScheduled() {
        final boolean value = autoScheduled && System.currentTimeMillis() - autoScheduledTime < 5000L;
        autoScheduled = false;
        return value;
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
