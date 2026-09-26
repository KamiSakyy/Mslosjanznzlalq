package org.telegram.messenger.kamigram;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;

/**
 * r116: фильтр по словам. Слова добавляются в Sakura-центре («Фильтр по
 * словам»): посты, сообщения, каналы, чаты и боты, где встречается слово-
 * исключение, исчезают из ленты и из поиска. Пустой список = фильтр
 * выключен (проверка стоит ноль трафика и ноль затрат).
 *
 * Совпадение ищется по подстроке без учёта регистра: слово «кот» скрывает
 * «кот», «Коты», «который» — как обычный стоп-фильтр.
 * KAMIGRAM_WORD_FILTER_R116
 */
public final class KamiGramWordFilter {

    public static final String KEY_WORDS = "kamigram_filter_words";

    private static volatile String[] cache;

    private KamiGramWordFilter() {
    }

    public static String[] words() {
        String[] local = cache;
        if (local == null) {
            local = parse(getString());
            cache = local;
        }
        return local;
    }

    public static String getString() {
        try {
            return KamiGramConfig.getStringValue(KEY_WORDS, "");
        } catch (Throwable ignore) {
            return "";
        }
    }

    public static void setWords(String csv) {
        try {
            KamiGramConfig.setStringValue(KEY_WORDS, csv == null ? "" : csv.trim());
        } catch (Throwable ignore) {
        }
        cache = null;
    }

    private static String[] parse(String csv) {
        if (csv == null || csv.isEmpty()) {
            return new String[0];
        }
        final String[] parts = csv.toLowerCase().split("[,;\n]+");
        final ArrayList<String> out = new ArrayList<>();
        for (int a = 0; a < parts.length; a++) {
            final String word = parts[a].trim();
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out.toArray(new String[0]);
    }

    public static boolean enabled() {
        return words().length > 0;
    }

    /** Любой из текстов содержит слово-исключение. */
    public static boolean matches(CharSequence... texts) {
        try {
            final String[] list = words();
            if (list.length == 0 || texts == null) {
                return false;
            }
            for (int t = 0; t < texts.length; t++) {
                final CharSequence text = texts[t];
                if (text == null || text.length() == 0) {
                    continue;
                }
                final String lower = text.toString().toLowerCase();
                for (int w = 0; w < list.length; w++) {
                    if (lower.contains(list[w])) {
                        return true;
                    }
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        return false;
    }

    /**
     * Сообщения со словами-исключениями убираются из списка (лента канала,
     * чата, бота). Собственные и ещё отправляемые сообщения не трогаются —
     * своё не исчезает. Служебные разделители дат остаются (у них нет текста).
     */
    public static void filterMessages(ArrayList<MessageObject> messages) {
        try {
            if (messages == null || messages.isEmpty() || !enabled()) {
                return;
            }
            for (int a = 0; a < messages.size(); a++) {
                final MessageObject object = messages.get(a);
                if (object == null || object.isOutOwner() || object.isSending()) {
                    continue;
                }
                if (matches(object.messageText)) {
                    messages.remove(a);
                    a--;
                }
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }
}
