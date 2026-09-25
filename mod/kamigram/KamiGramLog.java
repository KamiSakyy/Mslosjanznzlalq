package org.telegram.messenger.kamigram;

/**
 * Deliberately empty error sink for optional KamiGram paths. KamiGram does not
 * persist technical diagnostics or expose a log viewer; failures fall back to
 * Telegram's normal behavior silently.
 */
public final class KamiGramLog {
    private KamiGramLog() {
    }

    public static void e(Throwable ignored) {
    }

    public static void e(Object ignored) {
    }
}
