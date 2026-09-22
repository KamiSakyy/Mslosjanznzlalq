package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * KamiGram: совместимость с прошлыми сборками.
 *
 * Раньше этот класс переопределял цвета кодом (красные счётчики, индиго-акценты)
 * — из-за этого цвета отличались от Telegram. Теперь цвета приходят только из
 * темы (assets), а здесь остаётся один вызов акцента, который пользователь
 * выбрал в центре KamiGram (по умолчанию — «как в Telegram», без правок).
 */
public final class KamiGramTheme {

    private KamiGramTheme() {
    }

    /**
     * Цвета в моде больше НЕ задаются кодом.
     *
     * Почему: любое переопределение цвета кодом ломало вид — появлялись
     * «красные» счётчики и акценты, которых в Telegram нет. Теперь источник
     * цвета один — тема приложения (assets, P16), а код применяет только тот
     * акцент, который пользователь сам выбрал в центре KamiGram.
     */
    public static void apply() {
        try {
            ThemeHook.applyAccent();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
