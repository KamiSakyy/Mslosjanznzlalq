package org.telegram.messenger.kamigram;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Adapters.SearchAdapterHelper;

import java.util.ArrayList;

/**
 * r116: фильтры глобального поиска Sakura.
 *
 * 1) «Только глобальный поиск» — из результатов убираются собственные чаты,
 *    контакты и «свои, найденные на сервере»; остаётся только глобальный
 *    (публичный) поиск Telegram.
 * 2) «Чистый поиск» — категории: люди / группы / боты / каналы. Лишние
 *    категории выключаются тумблерами в Sakura-центре (например, остаются
 *    только люди — и поиск ищет только людей).
 * 3) Фильтр по словам (KamiGramWordFilter) применяется к названиям найденного.
 *
 * Фильтр работает ТОЛЬКО для поиска в списке чатов (DialogsSearchAdapter):
 * диалог «Поделиться» имеет собственный экземпляр SearchAdapterHelper и
 * не ограничивается. KAMIGRAM_SEARCH_FILTER_R116
 */
public final class KamiGramSearchFilter {

    private KamiGramSearchFilter() {
    }

    public static boolean globalOnly() {
        /* r125: поиск возвращён к оригинальному Telegram — режим «только
           глобальный» больше не существует. */
        return false;
    }

    /** Фильтрует глобальный и «свой серверный» списки помощника на месте. */
    public static void applyTo(SearchAdapterHelper helper) {
        /* r125: no-op — серверная выдача не фильтруется (оригинальное поведение). */
    }

    private static void filterList(ArrayList<TLObject> list, boolean ownResults) {
        if (list == null) {
            return;
        }
        if (ownResults && globalOnly()) {
            list.clear();
            return;
        }
        for (int a = 0; a < list.size(); a++) {
            if (!allow(list.get(a), null)) {
                list.remove(a);
                a--;
            }
        }
    }

    /** Проходит ли элемент поиска через категории и фильтр слов. */
    public static boolean allow(Object item, CharSequence name) {
        /* r125: поиск как в оригинале — ничего не отбрасываем (боты, люди,
           группы и каналы видимы все). */
        return true;
    }
}
