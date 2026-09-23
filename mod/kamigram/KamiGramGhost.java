package org.telegram.messenger.kamigram;

import android.content.Context;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

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
 * Иконка призрака живёт в меню «⋮» главного экрана и не занимает место в
 * шапке. В чатах и каналах её нет.
 */
public final class KamiGramGhost {

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
                // r70: «Сгореть»/«Прочитать» из меню — считывание разрешено
                if (bypassActive()) {
                    return false;
                }
                answerLocally(onComplete);
                return true;
            }

            if (viewOnce() && isReadContents(object)) {
                // «просмотрено» не уходит: сервер не удаляет одноразовое сообщение
                if (bypassActive()) {
                    return false;
                }
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

    // ------------------------------------------------------------------ меню «⋮»

    /**
     * Переключатель, вызываемый из пункта меню «Призрак». Иконка создаётся
     * заново при открытии overflow-меню, поэтому отдельная кнопка в шапке не
     * нужна и не может занять её место.
     */
    public static void toggle(Context context) {
        try {
            final boolean enabled = !KamiGramConfig.value(KamiGramConfig.KEY_GHOST);
            KamiGramConfig.set(KamiGramConfig.KEY_GHOST, enabled);
            refreshAll();
            KamiGramUi.notify(context, enabled ? "Призрак включён" : "Призрак выключен");
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Оставлен как совместимый хук для центра настроек; header-иконки больше нет. */
    public static void refreshAll() {
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
            int date = org.telegram.tgnet.ConnectionsManager.getInstance(
                org.telegram.messenger.UserConfig.selectedAccount).getCurrentTime() + 10;
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

    // ---------------------------------------------------- r70: «Сгореть» / «Прочитать»

    private static volatile long readBypassUntil;

    /** Разрешить запросы «прочитано» на N секунд (обход глушения призрака). */
    public static void allowReadsFor(int seconds) {
        readBypassUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    private static boolean bypassActive() {
        try {
            return System.currentTimeMillis() < readBypassUntil;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Сгорающее сообщение: одноразовое/с таймером медиа или секретный чат с таймером. */
    public static boolean isEphemeralMedia(org.telegram.tgnet.TLRPC.Message message) {
        try {
            if (message == null) {
                return false;
            }
            if (message instanceof org.telegram.tgnet.TLRPC.TL_message) {
                final org.telegram.tgnet.TLRPC.MessageMedia media =
                    ((org.telegram.tgnet.TLRPC.TL_message) message).media;
                return media != null && media.ttl_seconds != 0;
            }
            if (message instanceof org.telegram.tgnet.TLRPC.TL_message_secret) {
                final org.telegram.tgnet.TLRPC.TL_message_secret secret =
                    (org.telegram.tgnet.TLRPC.TL_message_secret) message;
                final org.telegram.tgnet.TLRPC.MessageMedia media = secret.media;
                return secret.ttl > 0 || (media != null && media.ttl_seconds > 0);
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /**
     * «Сгореть» (r70): отправить серверу «прочитано» С РЕАЛЬНЫМ таймером.
     * У собеседника сообщение сгорит (одноразовое — сразу, по таймеру — по
     * расписанию), а у нас локальная копия остаётся (наши защиты от удаления
     * её не трогают).
     */
    public static void burnMessage(int account, long dialogId,
                                   org.telegram.messenger.MessageObject message) {
        try {
            if (message == null || message.messageOwner == null) {
                return;
            }
            final org.telegram.tgnet.TLRPC.Message owner = message.messageOwner;
            final org.telegram.messenger.MessagesController controller =
                org.telegram.messenger.MessagesController.getInstance(account);
            if (controller == null) {
                return;
            }
            allowReadsFor(30);
            if (owner instanceof org.telegram.tgnet.TLRPC.TL_message_secret) {
                final org.telegram.tgnet.TLRPC.TL_message_secret secret =
                    (org.telegram.tgnet.TLRPC.TL_message_secret) owner;
                final org.telegram.tgnet.TLRPC.MessageMedia media = secret.media;
                final int ttl = secret.ttl > 0 ? secret.ttl
                    : (media != null && media.ttl_seconds > 0 ? media.ttl_seconds : 1);
                controller.markMessageAsRead(dialogId, secret.random_id, ttl);
            } else if (owner instanceof org.telegram.tgnet.TLRPC.TL_message) {
                final org.telegram.tgnet.TLRPC.MessageMedia media =
                    ((org.telegram.tgnet.TLRPC.TL_message) owner).media;
                if (media != null && media.ttl_seconds > 0) {
                    // одноразовое/по таймеру: читаем с реальным ttl — сервер начнёт уничтожение
                    controller.markMessageAsRead2(dialogId, owner.id, null, media.ttl_seconds, 0, true);
                } else {
                    // чат с аккаунт-таймером: обычное «прочитано» запускает общий таймер
                    controller.markMessageAsRead2(dialogId, owner.id, null, 0, 0, false);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /**
     * «Прочитать» (r70): пометить сообщение прочитанным (собеседник увидит
     * отметку), БЕЗ сгорания.
     */
    public static void readMessage(int account, long dialogId,
                                   org.telegram.messenger.MessageObject message) {
        try {
            if (message == null || message.messageOwner == null) {
                return;
            }
            final org.telegram.tgnet.TLRPC.Message owner = message.messageOwner;
            final org.telegram.messenger.MessagesController controller =
                org.telegram.messenger.MessagesController.getInstance(account);
            if (controller == null) {
                return;
            }
            allowReadsFor(30);
            if (owner instanceof org.telegram.tgnet.TLRPC.TL_message_secret) {
                final org.telegram.tgnet.TLRPC.TL_message_secret secret =
                    (org.telegram.tgnet.TLRPC.TL_message_secret) owner;
                final org.telegram.tgnet.TLRPC.EncryptedChat chat = controller.getEncryptedChat(
                    org.telegram.messenger.DialogObject.getEncryptedChatId(dialogId));
                if (chat != null) {
                    final java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                    ids.add(secret.random_id);
                    org.telegram.messenger.AccountInstance.getInstance(account)
                        .getSecretChatHelper().sendMessagesReadMessage(chat, ids, null);
                }
            } else {
                controller.markMessageAsRead2(dialogId, owner.id, null, 0, 0, false);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
