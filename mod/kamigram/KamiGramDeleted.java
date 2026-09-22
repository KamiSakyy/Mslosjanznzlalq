package org.telegram.messenger.kamigram;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * KamiGram: удалённые сообщения остаются на месте — как в AyuGram.
 *
 * Раньше мод складывал только текст в «журнал», и это не работало. Теперь
 * сообщение не исчезает из чата: оно остаётся, помечается как удалённое
 * (полупрозрачное, с отметкой «удалено» во времени) и переживает перезапуск —
 * строка не вычищается из локальной базы Telegram.
 *
 * Правило простое и понятное:
 *   * удалили у вас (или сообщение удалено на другом устройстве) — сообщение
 *     остаётся в чате с пометкой;
 *   * вы удаляете УЖЕ удалённое сообщение — оно удаляется по-настоящему.
 *
 * Хранятся только идентификаторы (диалог + номер сообщения), сам текст берётся
 * из базы Telegram, поэтому журнал не растёт в мегабайтах.
 */
public final class KamiGramDeleted {

    private static final String PREFS = "kamigram_deleted_ids";
    private static final String KEY_IDS = "ids";
    private static final int MAX_IDS = 4000;

    /** Отметка удалённого сообщения в чеке времени. */
    public static final String MARK = "удалено";

    private static HashSet<String> ids;
    private static boolean loaded;

    private KamiGramDeleted() {
    }

    // ------------------------------------------------------------------ состояние

    /** Сохранять удалённые сообщения (переключатель в центре мода). */
    public static boolean enabled() {
        try {
            return KamiGramConfig.keepDeleted();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static String key(long dialogId, int mid) {
        return dialogId + ":" + mid;
    }

    private static synchronized HashSet<String> set() {
        if (!loaded) {
            loaded = true;
            ids = new HashSet<>();
            try {
                final SharedPreferences preferences = preferences();
                if (preferences != null) {
                    final Set<String> stored = preferences.getStringSet(KEY_IDS, null);
                    if (stored != null) {
                        ids.addAll(stored);
                    }
                }
            } catch (Throwable throwable) {
                FileLog.e(throwable);
            }
        }
        return ids;
    }

    private static SharedPreferences preferences() {
        try {
            return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static synchronized void persist() {
        try {
            final SharedPreferences preferences = preferences();
            if (preferences != null) {
                preferences.edit().putStringSet(KEY_IDS, new HashSet<>(set())).apply();
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ работа

    /**
     * Вызывается перед удалением сообщений: помечаем те, которые останутся.
     * Повторное удаление уже помеченного сообщения удаляет его по-настоящему.
     */
    public static void beforeDelete(long dialogId, ArrayList<Integer> mids) {
        if (!enabled() || dialogId == 0 || mids == null || mids.isEmpty()) {
            return;
        }
        boolean changed = false;
        final HashSet<String> current = set();
        for (int a = 0; a < mids.size(); a++) {
            final Integer mid = mids.get(a);
            if (mid == null || mid <= 0) {
                continue;
            }
            final String id = key(dialogId, mid);
            if (current.contains(id)) {
                current.remove(id);
            } else {
                current.add(id);
            }
            changed = true;
        }
        if (changed) {
            trim(current);
            persist();
        }
    }

    /**
     * Сообщение удалено не нами (собеседник или другое устройство) — запоминаем,
     * чтобы оно осталось в чате.
     */
    public static void remember(long dialogId, ArrayList<Integer> mids) {
        if (!enabled() || dialogId == 0 || mids == null || mids.isEmpty()) {
            return;
        }
        try {
            final HashSet<String> current = set();
            boolean changed = false;
            for (int a = 0; a < mids.size(); a++) {
                final Integer mid = mids.get(a);
                if (mid != null && mid > 0 && current.add(key(dialogId, mid))) {
                    changed = true;
                }
            }
            if (changed) {
                trim(current);
                persist();
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    /** Оставляем сообщение в чате? */
    public static boolean shouldKeep(long dialogId, int mid) {
        if (!enabled() || dialogId == 0 || mid <= 0) {
            return false;
        }
        try {
            return set().contains(key(dialogId, mid));
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Убрать из списка (сообщение удалено приложением Telegram). */
    public static synchronized void forget(long dialogId, int mid) {
        try {
            if (set().remove(key(dialogId, mid))) {
                persist();
            }
        } catch (Throwable ignore) {
        }
    }

    /**
     * Не даём базе Telegram удалить сообщения, которые пользователь хочет видеть.
     * Возвращается тот же список без «сохранённых» номеров.
     */
    public static ArrayList<Integer> filterKept(long dialogId, ArrayList<Integer> messages) {
        if (!enabled() || messages == null || messages.isEmpty()) {
            return messages;
        }
        try {
            final ArrayList<Integer> kept = new ArrayList<>(messages.size());
            for (int a = 0; a < messages.size(); a++) {
                final Integer mid = messages.get(a);
                if (mid != null && shouldKeep(dialogId, mid)) {
                    continue;
                }
                kept.add(mid);
            }
            return kept;
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            return messages;
        }
    }

    /** Сколько сообщений сейчас помечено. */
    public static int count() {
        try {
            return set().size();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /** Забыть всё (например, при выключении функции). */
    public static synchronized void clear() {
        try {
            set().clear();
            persist();
        } catch (Throwable ignore) {
        }
    }

    private static void trim(HashSet<String> values) {
        if (values.size() <= MAX_IDS) {
            return;
        }
        int remove = values.size() - MAX_IDS;
        final java.util.Iterator<String> iterator = values.iterator();
        while (iterator.hasNext() && remove > 0) {
            iterator.next();
            iterator.remove();
            remove--;
        }
    }

    /** Совместимость: раньше центр мода открывал «журнал» — теперь это не нужно. */
    public static ArrayList<String> entries() {
        return new ArrayList<>();
    }
}
