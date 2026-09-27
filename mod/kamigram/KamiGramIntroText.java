package org.telegram.messenger.kamigram;

import android.graphics.Color;

/**
 * Тексты и цвета стартового экрана в стиле Sakura.
 *
 * KAMIGRAM_INTRO_TEXT_R120: на интро не остаётся ни слова о Telegram —
 * заголовок, подзаголовок и страницы листалки звучат по-нашему, кнопка
 * залита нашим градиентом.
 */
public final class KamiGramIntroText {

    private KamiGramIntroText() {
    }

    /** Подзаголовок первой страницы. */
    public static String tagline() {
        return "Быстрый и безопасный мессенджер в нежном стиле сакуры.";
    }

    /** Заголовки страниц 1–5 (после первой). */
    public static String title(int page) {
        switch (page) {
            case 1:
                return "Быстрее обычного";
            case 2:
                return "Под вашей защитой";
            case 3:
                return "Синхронно везде";
            case 4:
                return "Выразительно";
            default:
                return "Без лишнего";
        }
    }

    /** Тексты страниц 1–5 (после первой). */
    public static String message(int page) {
        switch (page) {
            case 1:
                return "Лёгкие лепестки вместо тяжёлого кода: всё открывается мгновенно.";
            case 2:
                return "Ваши переписки остаются вашими. Точка.";
            case 3:
                return "Телефон, планшет, компьютер — одна история везде.";
            case 4:
                return "Стикеры, эмодзи и темы — столько характера, сколько захотите.";
            default:
                return "Никакой рекламы и лишнего шума. Только общение.";
        }
    }

    /** Правый край градиента кнопки: акцент, осветлённый к белому. */
    public static int gradientEnd() {
        final int accent = ThemeHook.accent();
        final int r = Color.red(accent) + (255 - Color.red(accent)) * 30 / 100;
        final int g = Color.green(accent) + (255 - Color.green(accent)) * 30 / 100;
        final int b = Color.blue(accent) + (255 - Color.blue(accent)) * 30 / 100;
        return Color.argb(255, r, g, b);
    }
}
