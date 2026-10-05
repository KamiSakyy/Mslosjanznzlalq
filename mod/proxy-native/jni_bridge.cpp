#include "tsproxy.h"

#include <jni.h>

#include <string>

static std::string from_java(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars == nullptr ? "" : chars;
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeStart(
        JNIEnv* env, jclass, jstring host, jint port, jstring secret) {
    std::string host_text = from_java(env, host);
    std::string secret_text = from_java(env, secret);
    if (host_text.empty()) {
        host_text = "127.0.0.1";
    }
    return proxy_start(host_text.c_str(), port, secret_text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeStop(JNIEnv*, jclass) {
    proxy_stop();
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeConfigure(
        JNIEnv* env, jclass, jint pool, jstring cache_dir, jint cf_enabled, jstring user_domain) {
    std::string cache = from_java(env, cache_dir);
    std::string domain = from_java(env, user_domain);
    proxy_configure(pool, cache.c_str(), cf_enabled, domain.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeAlive(JNIEnv*, jclass) {
    return proxy_alive() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeBytesUp(JNIEnv*, jclass) {
    return static_cast<jlong>(proxy_bytes_up());
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeBytesDown(JNIEnv*, jclass) {
    return static_cast<jlong>(proxy_bytes_down());
}

extern "C" JNIEXPORT jint JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativePing(JNIEnv*, jclass) {
    return proxy_ping_ms();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeHeal(JNIEnv*, jclass) {
    return proxy_heal();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeLive(JNIEnv*, jclass) {
    return proxy_live_fronts();
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_messenger_kamigram_KamiGramProxyCore_nativeNetwork(JNIEnv*, jclass) {
    proxy_network_changed();
}
