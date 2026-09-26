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
        try {
            return KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_GLOBAL_ONLY);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Фильтрует глобальный и «свой серверный» списки помощника на месте. */
    public static void applyTo(SearchAdapterHelper helper) {
        try {
            if (helper == null) {
                return;
            }
            filterList(helper.getGlobalSearch(), false);
            filterList(helper.getLocalServerSearch(), true);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
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
        try {
            if (item == null) {
                return false;
            }
            if (item instanceof TLRPC.User) {
                final TLRPC.User user = (TLRPC.User) item;
                if (user.bot) {
                    if (!KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_BOTS)) {
                        return false;
                    }
                } else if (!KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_PEOPLE)) {
                    return false;
                }
                return !KamiGramWordFilter.matches(user.first_name, user.last_name,
                    user.username, name);
            }
            if (item instanceof TLRPC.Chat) {
                final TLRPC.Chat chat = (TLRPC.Chat) item;
                final boolean channel = ChatObject.isChannel(chat) && !chat.megagroup;
                if (channel) {
                    if (!KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_CHANNELS)) {
                        return false;
                    }
                } else if (!KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_GROUPS)) {
                    return false;
                }
                return !KamiGramWordFilter.matches(chat.title, name);
            }
            if (item instanceof TLRPC.EncryptedChat) {
                return KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_PEOPLE);
            }
            if (item instanceof TLRPC.Dialog) {
                final long dialogId = ((TLRPC.Dialog) item).id;
                final MessagesController controller =
                    MessagesController.getInstance(UserConfig.selectedAccount);
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    return KamiGramConfig.value(KamiGramConfig.KEY_SEARCH_PEOPLE);
                }
                if (dialogId > 0) {
                    final TLRPC.User user = controller.getUser(dialogId);
                    return user == null || allow(user, name);
                }
                final TLRPC.Chat chat = controller.getChat(-dialogId);
                return chat == null || allow(chat, name);
            }
            if (name != null) {
                return !KamiGramWordFilter.matches(name);
            }
            return true;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        return true;
    }
}
