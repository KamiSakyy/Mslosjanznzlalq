package org.telegram.messenger.kamigram;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stories;

/**
 * KamiGram «призрак» для историй.
 *
 * Telegram умеет режим невидимки для историй (`stories.activateStealthMode`):
 * просмотры чужих историй не записываются - автор не видит, что мы смотрели.
 * Мод включает этот режим сам при запуске, если включён «призрак» и «призрак для историй».
 */
public final class KamiGramGhost {

    private static boolean stealthSent;

    private KamiGramGhost() {
    }

    /** Вызывается при старте приложения: включает невидимку для историй. */
    public static void onAppStarted(int account) {
        try {
            if (!KamiGramConfig.ghostMode() || !KamiGramConfig.storiesStealth() || stealthSent) {
                return;
            }
            final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
            if (connectionsManager == null) {
                return;
            }
            final TL_stories.TL_stories_activateStealthMode request = new TL_stories.TL_stories_activateStealthMode();
            request.past = true;
            request.future = true;
            stealthSent = true;
            connectionsManager.sendRequest(request, (response, error) -> {
                if (error != null) {
                    FileLog.d("KamiGram: stealth mode не включился: " + error.text);
                } else {
                    FileLog.d("KamiGram: истории - невидимка включена");
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
