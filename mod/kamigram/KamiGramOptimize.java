package org.telegram.messenger.kamigram;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;

/**
 * KamiGram: оптимизация и плавность без потери функций и дизайна.
 *
 * Что делает:
 *   * поднимает класс производительности устройства — Telegram перестаёт
 *     экономить на анимациях и предзагрузке (интерфейс листается плавно);
 *   * «стекло» и размытие включаются/выключаются одной настройкой в центре —
 *     размытие дорогое, без него UI заметно быстрее;
 *   * сохраняет автоскачивание медиа выключенным (трафик), но не трогает
 *     анимации чата и аватарки, поэтому дизайн остаётся прежним.
 *
 * Ничего не удаляется и не отключается навсегда: всё это переключатели,
 * которые пользователь видит в центре KamiGram.
 */
public final class KamiGramOptimize {

    private KamiGramOptimize() {
    }

    /** Вызывается при запуске приложения (и после смены настроек). */
    public static void apply() {
        try {
            final boolean smooth = KamiGramConfig.value(KamiGramConfig.KEY_SMOOTH_ANIMATIONS);
            if (smooth) {
                // плавность важнее экономии: интерфейс как на топовых устройствах
                if (SharedConfig.getDevicePerformanceClass() < SharedConfig.PERFORMANCE_CLASS_HIGH) {
                    SharedConfig.overrideDevicePerformanceClass(SharedConfig.PERFORMANCE_CLASS_HIGH);
                }
            }
            LiteMode.toggleFlag(LiteMode.FLAGS_ANIMATED_EMOJI, smooth);
            LiteMode.toggleFlag(LiteMode.FLAGS_ANIMATED_STICKERS, smooth);
            LiteMode.toggleFlag(LiteMode.FLAG_CALLS_ANIMATIONS, smooth);
            if (!KamiGramConfig.value(KamiGramConfig.KEY_NO_GIFS)) {
                // гифки не грузятся вообще, но настройка авто-проигрывания не мешает
                LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS, smooth);
            }
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, smooth && KamiGramConfig.value(KamiGramConfig.KEY_ALLOW_BLUR));
            if (!KamiGramConfig.value(KamiGramConfig.KEY_ALLOW_BLUR)) {
                SharedConfig.useNewBlur = false;
                SharedConfig.photoViewerBlur = false;
            }
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
    }

    /** Короткая подпись для центра мода. */
    public static String describe() {
        try {
            final int performanceClass = SharedConfig.getDevicePerformanceClass();
            final String name = performanceClass >= SharedConfig.PERFORMANCE_CLASS_HIGH ? "высокая"
                : performanceClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? "средняя" : "экономичная";
            return "производительность: " + name;
        } catch (Throwable ignore) {
            return "производительность";
        }
    }
}
