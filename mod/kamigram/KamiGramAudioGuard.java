package org.telegram.messenger.kamigram;

import android.os.SystemClock;

/**
 * Перемотка музыки не должна уничтожать плеер и не должна звать native seek
 * с мусорной позицией (KAMIGRAM_AUDIO_GUARD_R132).
 *
 * ExoPlayer на точном seek по аудио без индекса зависает в экстракторе, а
 * переход в BUFFERING при audioProgress около 1.0 штатный код принимает за
 * конец трека и тут же уничтожает плеер — отсюда мгновенный вылет.
 */
public final class KamiGramAudioGuard {

    /* KAMIGRAM_AUDIO_GUARD_R132 */
    private static final long SEEK_GUARD_MS = 1800L;
    private static volatile long seekUntil;

    private KamiGramAudioGuard() {
    }

    public static void noteSeek() {
        seekUntil = SystemClock.elapsedRealtime() + SEEK_GUARD_MS;
    }

    public static boolean seeking() {
        return SystemClock.elapsedRealtime() < seekUntil;
    }

    public static float clampProgress(float progress) {
        if (progress < 0f) {
            return 0f;
        }
        if (progress > 0.995f) {
            return 0.995f;
        }
        return progress;
    }

    public static long clampPosition(long positionMs, long durationMs) {
        if (positionMs < 0L) {
            positionMs = 0L;
        }
        if (durationMs > 0L && durationMs != Long.MIN_VALUE && positionMs >= durationMs) {
            positionMs = durationMs - 1L;
        }
        return positionMs;
    }
}
