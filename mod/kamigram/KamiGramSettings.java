package org.telegram.messenger.kamigram;

import android.content.Context;

/**
 * Sakura: строка «Настройки → Sakura» в настройках Telegram.
 *
 * Весь интерфейс переехал в {@link KamiGramCenter} (карточки, акценты, кэш,
 * ID, прокси, загрузки). Этот класс оставлен как точка входа, чтобы патчи
 * сборки и строки настроек Telegram не зависели от внутреннего устройства.
 */
public final class KamiGramSettings {

    private KamiGramSettings() {
    }

    public static void show(final Context context) {
        KamiGramCenter.show(context, null);
    }

    public static void show(final Context context, final Runnable onChanged) {
        KamiGramCenter.show(context, onChanged);
    }
}
