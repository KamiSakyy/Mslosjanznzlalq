package org.telegram.messenger.kamigram;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.voip.Instance;
import org.telegram.proxy.ProxySettings;

import java.util.ArrayList;

/**
 * r115: звонки и видеозвонки идут через прокси.
 *
 * Звонки несет только SOCKS5-прокси (MTProto-прокси, включая встроенные
 * SakuProxy, звонки переносить не умеет — ограничение самого движка звонков).
 * Если активный прокси для звонков не годится или выключен, выбирается
 * подходящий SOCKS5-прокси из сохранённого списка — точечно для звонка,
 * глобальные настройки соединения не меняются.
 */
public final class KamiGramCallProxy {

    private KamiGramCallProxy() {
    }

    /** SOCKS5-прокси для звонка: сначала проверенный живой, затем любой из списка. */
    public static Instance.Proxy callProxy() {
        try {
            final ArrayList<SharedConfig.ProxyInfo> list = SharedConfig.proxyList;
            if (list == null || list.isEmpty()) {
                return null;
            }
            ProxySettings best = null;
            for (int a = 0; a < list.size(); a++) {
                final SharedConfig.ProxyInfo info = list.get(a);
                if (info == null || info.settings == null) {
                    continue;
                }
                final ProxySettings settings = info.settings;
                if (settings.getType() != ProxySettings.Type.SOCKS5) {
                    continue;
                }
                if (settings.getAddress().isEmpty()) {
                    continue;
                }
                if (best == null || info.available) {
                    best = settings;
                    if (info.available) {
                        break;
                    }
                }
            }
            if (best == null) {
                return null;
            }
            final String user = best.getUser().isEmpty() ? null : best.getUser();
            final String password = best.getPassword().isEmpty() ? null : best.getPassword();
            return new Instance.Proxy(best.getAddress(), best.getPort(), user, password);
        } catch (Throwable e) {
            KamiGramLog.e(e);
            return null;
        }
    }
}
