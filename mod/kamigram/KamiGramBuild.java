package org.telegram.messenger.kamigram;

/**
 * Номер сборки Sakura. Пишется автоматически при сборке и виден в настройках
 * (строка Sakura) — по нему сразу понятно, какая версия стоит на телефоне.
 */
public final class KamiGramBuild {

    /** Номер сборки, подставляется сборщиком (например, "r64"). */
    public static final String NUMBER = "local";

    private KamiGramBuild() {
    }

    /** Короткая подпись версии. */
    public static String label() {
        return "сборка " + NUMBER;
    }
}
