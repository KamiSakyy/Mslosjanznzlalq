package org.telegram.messenger.kamigram;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * KAMIGRAM_SAKURA_BRAND_R101: внутри приложения везде «Sakura», а не «Telegram».
 *
 * Зачем отдельный класс: строки приходят из трёх источников — ресурсы
 * (values/strings.xml), встроенные переводы и ОБЛАЧНЫЕ языковые пакеты, которые
 * Telegram скачивает и применяет поверх ресурсов в рантайме
 * (LocaleController.loadRemoteLanguages). Поэтому переписанных strings.xml
 * недостаточно: облачной пакет возвращает «Telegram» обратно. Здесь строка
 * проходит через один и тот же фильтр независимо от источника.
 *
 * Что НЕ трогается (иначе приложение сломается):
 *   * ссылки и домены: https://telegram.org/..., t.me/..., tg://..., www...;
 *   * идентификаторы: org.telegram.messenger.provider, telegram_bot, @Telegram;
 *   * всё, где «telegram» стоит вплотную к точке, слэшу, «:», «=», «_», «@».
 *
 * Плюс точечные замены, которые важны пользователю:
 *   * AppName / AppNameBeta → Sakura / Sakura Beta;
 *   * «Возможности Telegram» → «Sakura канал», ссылка → https://t.me/AsuMeo.
 */
public final class KamiGramBranding {

    /** Канал разработчика Sakura: его открывает строка «Sakura канал». */
    public static final String CHANNEL_URL = "https://t.me/AsuMeo";

    /** Название приложения. */
    public static final String APP_NAME = "Sakura";

    private static final String BRAND = "Telegram";
    private static final int BRAND_LENGTH = BRAND.length();

    /**
     * Символ ПЕРЕД «telegram», который означает идентификатор или ссылку, а не
     * видимый текст: org.telegram.messenger, t.me/Telegram, tg:Telegram,
     * ?domain=telegram, @Telegram.
     */
    private static final String PREV_TECHNICAL = "./:=@#&%~|;";

    /**
     * Символ ПОСЛЕ «telegram» с тем же смыслом. Точка проверяется отдельно:
     * «telegram.org» — домен (не трогаем), «Telegram.» в конце предложения —
     * обычный текст и заменяется на «Sakura.».
     */
    private static final String NEXT_TECHNICAL = "/:=#&%@~|;";

    /** Символы, на которых обрывается токен при проверке «это ссылка?». */
    private static final String TOKEN_STOP = "<>\"'\\;&";

    /** Точечные значения, которые не зависят от языкового пакета. */
    private static final HashMap<String, String> FORCED = new HashMap<>();

    /** Кэш замен: одни и те же строки запрашиваются списками по много раз. */
    private static final HashMap<String, String> CACHE = new HashMap<>();
    private static final int CACHE_MAX = 2048;

    static {
        FORCED.put("AppName", APP_NAME);
        FORCED.put("AppNameBeta", APP_NAME + " Beta");
        FORCED.put("AppNamePrivate", APP_NAME);
        FORCED.put("TelegramFeaturesUrl", CHANNEL_URL);
    }

    private KamiGramBranding() {
    }

    /** Заголовок строки «Возможности Telegram» → «Sakura канал». */
    public static String featuresTitle() {
        return isRussian() ? "Sakura канал" : "Sakura Channel";
    }

    private static boolean isRussian() {
        try {
            final String language = Locale.getDefault().getLanguage();
            return "ru".equals(language) || "uk".equals(language)
                || "be".equals(language) || "kk".equals(language);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * Точка входа из LocaleController: key известен — можно применить точечные
     * значения, иначе строка проходит общую замену бренда.
     */
    public static String localize(String key, String value) {
        try {
            if (key != null) {
                if ("TelegramFeatures".equals(key)) {
                    /* «Возможности Telegram» → «Sakura канал» (тумблер канала). */
                    return featuresTitle();
                }
                final String forced = FORCED.get(key);
                if (forced != null) {
                    return forced;
                }
            }
            return text(value);
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
            return value;
        }
    }

    /** Общая замена: «Telegram» → «Sakura» с сохранением регистра и ссылок. */
    public static String text(String value) {
        if (value == null || value.length() < BRAND_LENGTH) {
            return value;
        }
        /* Быстрый выход: в большинстве строк бренда нет вообще. */
        if (value.indexOf("elegram") < 0 && value.indexOf("ELEGRAM") < 0) {
            return value;
        }
        synchronized (CACHE) {
            final String cached = CACHE.get(value);
            if (cached != null) {
                return cached;
            }
        }
        final String replaced = replaceBrand(value);
        if (!replaced.equals(value)) {
            synchronized (CACHE) {
                if (CACHE.size() >= CACHE_MAX) {
                    CACHE.clear();
                }
                CACHE.put(value, replaced);
            }
        }
        return replaced;
    }

    private static String replaceBrand(String value) {
        final String lower = value.toLowerCase(Locale.ROOT);
        final StringBuilder out = new StringBuilder(value.length() + 8);
        int index = 0;
        int found = lower.indexOf("telegram", index);
        while (found >= 0) {
            final int end = found + BRAND_LENGTH;
            out.append(value, index, found);
            if (isTechnical(value, found, end)) {
                out.append(value, found, end);
            } else {
                out.append(matchCase(value, found, end));
            }
            index = end;
            found = lower.indexOf("telegram", index);
        }
        out.append(value, index, value.length());
        return out.toString();
    }

    /**
     * «telegram» стоит внутри ссылки, домена, пакета или идентификатора —
     * такое заменять нельзя.
     */
    private static boolean isTechnical(String value, int start, int end) {
        /* «telegram» рядом с подчёркиванием — идентификатор (telegram_bot),
           «Telegram» — видимый текст, в том числе в markdown __Telegram__. */
        final boolean lowercase = value.charAt(start) == 't';
        if (start > 0) {
            final char before = value.charAt(start - 1);
            if (PREV_TECHNICAL.indexOf(before) >= 0 || Character.isLetterOrDigit(before)) {
                return true;
            }
            if (before == '_' && lowercase) {
                return true;
            }
        }
        if (end < value.length()) {
            final char after = value.charAt(end);
            if (NEXT_TECHNICAL.indexOf(after) >= 0 || Character.isLetterOrDigit(after)) {
                // TelegramX, Telegrams и т.п. — не наш бренд
                return true;
            }
            if (after == '_' && lowercase) {
                return true;
            }
            if (after == '.' && isDomainSuffix(value, end)) {
                // telegram.org / telegram.me / telegram.dog — это домены
                return true;
            }
        }
        /* Ссылка целиком: смотрим на «слово», в котором стоит бренд. */
        int left = start;
        while (left > 0 && !Character.isWhitespace(value.charAt(left - 1))
            && TOKEN_STOP.indexOf(value.charAt(left - 1)) < 0) {
            left--;
        }
        int right = end;
        while (right < value.length() && !Character.isWhitespace(value.charAt(right))
            && TOKEN_STOP.indexOf(value.charAt(right)) < 0) {
            right++;
        }
        final String token = value.substring(left, right);
        return token.contains("://") || token.startsWith("www.") || token.contains("@");
    }

    private static String matchCase(String value, int start, int end) {
        final String word = value.substring(start, end);
        if (word.equals(word.toUpperCase(Locale.ROOT))) {
            return "SAKURA";
        }
        if (word.equals(word.toLowerCase(Locale.ROOT))) {
            return "sakura";
        }
        return APP_NAME;
    }

    /** «.org», «.me», «.dog» и т.п. сразу за брендом — значит это домен. */
    private static boolean isDomainSuffix(String value, int dotIndex) {
        int index = dotIndex + 1;
        int letters = 0;
        while (index < value.length() && Character.isLetter(value.charAt(index)) && letters < 8) {
            index++;
            letters++;
        }
        if (letters < 2) {
            return false;
        }
        return index >= value.length() || !Character.isLetter(value.charAt(index));
    }

    /** Отладка/проверки: сколько точечных значений задано. */
    public static int forcedCount() {
        return FORCED.size();
    }

    /** Только для проверок сборки. */
    public static Map<String, String> forced() {
        return FORCED;
    }
}
