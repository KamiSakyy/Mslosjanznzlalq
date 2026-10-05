package org.telegram.messenger.kamigram;

/**
 * Мост к локальному прокси (KAMIGRAM_LOCAL_PROXY_CORE_R132).
 * Ядро собрано из исходника локального прокси: MTProto через Cloudflare,
 * со своим списком фронтов. Имена методов совпадают с JNI и не должны
 * переименовываться сборщиком.
 */
public final class KamiGramProxyCore {

    /* KAMIGRAM_LOCAL_PROXY_CORE_R132 */
    private static final boolean LOADED;

    static {
        boolean ok = false;
        try {
            System.loadLibrary("sakura_proxy");
            ok = true;
        } catch (Throwable throwable) {
            KamiGramLog.e(throwable);
        }
        LOADED = ok;
    }

    private KamiGramProxyCore() {
    }

    public static boolean available() {
        return LOADED;
    }

    public static native int nativeStart(String host, int port, String secret);

    public static native void nativeStop();

    public static native void nativeConfigure(int pool, String cacheDir, int cfEnabled, String domain);

    public static native boolean nativeAlive();

    public static native long nativeBytesUp();

    public static native long nativeBytesDown();

    public static native int nativePing();

    public static native int nativeHeal();

    public static native int nativeLive();

    public static native void nativeNetwork();
}
