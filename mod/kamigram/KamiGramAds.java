package org.telegram.messenger.kamigram;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;

import java.util.Locale;

/**
 * KamiGram: умный фильтр рекламы в сообщениях.
 *
 * Если в сообщении есть метка рекламы («реклама», «#реклама», «erid», «промокод»
 * и т.п.), сообщение не показывается в чате совсем — как будто его нет.
 * Работает и для новых сообщений, и для старых (при открытии чата).
 *
 * Правила:
 *   * ищем только в тексте и подписи, без учёта регистра;
 *   * слово «реклама» ловится и в виде хештега (#реклама, #рекламa);
 *   * «erid» — обязательная маркировка рекламы по закону РФ, по ней реклама
 *     видна почти всегда;
 *   * служебные сообщения и сообщения от самого пользователя не трогаем;
 *   * можно включать и выключать в центре KamiGram.
 */
public final class KamiGramAds {

    /** Слова-метки. Проверяются как отдельные слова, чтобы не ловить лишнее. */
    private static final String[] WORDS = {
        "реклама", "рекламу", "рекламный", "рекламное", "рекламная",
        "erid", "промокод", "промокоды", "спонсорский", "спонсорская",
        "advertising", "sponsored", "promo code"
    };

    private KamiGramAds() {
    }

    /** Скрыть ли это сообщение. */
    public static boolean hide(MessageObject message) {
        try {
            if (message == null || !KamiGramConfig.adsFilter()) {
                return false;
            }
            if (message.messageOwner == null || message.isOut() || message.isDateObject
                || message.messageOwner.action != null) {
                return false;
            }
            final CharSequence text = textOf(message);
            if (TextUtils.isEmpty(text)) {
                return false;
            }
            return looksLikeAd(text.toString());
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static CharSequence textOf(MessageObject message) {
        final StringBuilder builder = new StringBuilder();
        try {
            if (message.caption != null && message.caption.length() > 0) {
                builder.append(message.caption);
            }
            if (!TextUtils.isEmpty(message.messageText)) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(message.messageText);
            }
            if (builder.length() == 0 && message.messageOwner != null
                && !TextUtils.isEmpty(message.messageOwner.message)) {
                builder.append(message.messageOwner.message);
            }
        } catch (Throwable ignore) {
        }
        return builder;
    }

    /** Проверка текста на рекламные метки. */
    public static boolean looksLikeAd(String text) {
        if (text == null || text.length() == 0 || text.length() > 4000) {
            // очень длинные тексты не реклама (обычно это статьи/лонгриды)
            if (text == null || text.length() == 0) {
                return false;
            }
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        // хештеги: #реклама, #реклама_телеграм
        if (lower.contains("#реклам") || lower.contains("#рекламa") || lower.contains("#ad ")
            || lower.startsWith("#ad\n")) {
            return true;
        }
        for (int a = 0; a < WORDS.length; a++) {
            if (containsWord(lower, WORDS[a])) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли слово как отдельное (границы — не буквы и не цифры). */
    private static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            final int index = text.indexOf(word, from);
            if (index < 0) {
                return false;
            }
            final boolean leftOk = index == 0 || !isLetterOrDigit(text.charAt(index - 1));
            final int end = index + word.length();
            final boolean rightOk = end >= text.length() || !isLetterOrDigit(text.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = index + word.length();
        }
    }

    private static boolean isLetterOrDigit(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Сколько сообщений скрыто за сессию и последние примеры. */
    private static int hiddenCount;
    private static final java.util.ArrayList<String> SAMPLES = new java.util.ArrayList<>();

    public static void countHidden(String preview) {
        hiddenCount++;
        try {
            if (preview != null && preview.length() > 0) {
                final String short_ = preview.length() > 90 ? preview.substring(0, 90) + "…" : preview;
                SAMPLES.add(0, short_);
                while (SAMPLES.size() > 50) {
                    SAMPLES.remove(SAMPLES.size() - 1);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static int hidden() {
        return hiddenCount;
    }

    /** Экран: что именно скрыто (чтобы фильтр не был «чёрным ящиком»). */
    public static void showHiddenReport(android.content.Context context) {
        try {
            final StringBuilder builder = new StringBuilder();
            if (SAMPLES.isEmpty()) {
                builder.append("Пока ничего не скрыто.\n\nФильтр срабатывает на слова «реклама», «#реклама», «erid», «промокод».");
            } else {
                builder.append("Скрыто за сессию: ").append(hiddenCount).append("\n\n");
                for (int a = 0; a < SAMPLES.size(); a++) {
                    builder.append("• ").append(SAMPLES.get(a)).append('\n');
                }
            }
            final android.widget.TextView text = new android.widget.TextView(context);
            text.setText(builder.toString());
            text.setTextSize(13);
            text.setTextColor(ThemeHook.primaryText());
            text.setTextIsSelectable(true);
            text.setPadding(org.telegram.messenger.AndroidUtilities.dp(16),
                org.telegram.messenger.AndroidUtilities.dp(10),
                org.telegram.messenger.AndroidUtilities.dp(16),
                org.telegram.messenger.AndroidUtilities.dp(10));
            final android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.addView(text, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle("Скрытая реклама")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .show();
        } catch (Throwable throwable) {
            org.telegram.messenger.FileLog.e(throwable);
        }
    }

    public static String describe() {
        return hiddenCount > 0 ? "скрыто за сессию: " + hiddenCount : "фильтр рекламы включён";
    }
}
