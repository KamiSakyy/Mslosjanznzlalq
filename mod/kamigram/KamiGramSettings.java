package org.telegram.messenger.kamigram;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Switch;

/**
 * KamiGram: экран функций мода - открывается из «Настройки → KamiGram: функции мода».
 *
 * Здесь видно и переключается всё, что делает мод модом: режим «призрак»,
 * нулевой трафик на стикеры/эмодзи/истории, iOS-дизайн и остальные функции.
 */
public final class KamiGramSettings {

    private KamiGramSettings() {
    }

    public static void show(final Context context) {
        show(context, null);
    }

    public static void show(final Context context, final Runnable onChanged) {
        if (context == null) {
            return;
        }
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        final int pad = AndroidUtilities.dp(14);
        layout.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(4));

        add(layout, context, "Призрак: не видно чтение, «печатает» и «в сети»", KamiGramConfig.KEY_GHOST, onChanged);
        add(layout, context, "Не грузить стикеры и наборы эмодзи (0 байт)", KamiGramConfig.KEY_NO_STICKERS, onChanged);
        add(layout, context, "Не грузить истории и их медиа (0 байт)", KamiGramConfig.KEY_NO_STORIES, onChanged);
        add(layout, context, "Премиум-эмодзи показывать обычным эмодзи (без .tgs)", KamiGramConfig.KEY_NO_ANIMATED_EMOJI, onChanged);
        add(layout, context, "iOS-дизайн KamiGram: табы, шапка, скругления", KamiGramConfig.KEY_IOS_DESIGN, onChanged);
        add(layout, context, "iOS-скругление облаков сообщений", KamiGramConfig.KEY_IOS_BUBBLES, onChanged);
        add(layout, context, "Убрать блоки Telegram Premium / Stars / TON", KamiGramConfig.KEY_NO_PREMIUM_UI, onChanged);
        add(layout, context, "Убрать рекламу, спонсорские и рекомендации каналов", KamiGramConfig.KEY_NO_ADS, onChanged);
        add(layout, context, "Не грузить «часто используемые» контакты", KamiGramConfig.KEY_NO_TOP_PEERS, onChanged);
        add(layout, context, "Не искать GIF при вводе текста", KamiGramConfig.KEY_NO_GIF_SEARCH, onChanged);
        add(layout, context, "Снять запреты защищённого контента", KamiGramConfig.KEY_NO_RESTRICTIONS, onChanged);
        add(layout, context, "Показывать ID чатов и пользователей", KamiGramConfig.KEY_SHOW_IDS, onChanged);
        add(layout, context, "Прокси из буфера обмена включается сам", KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, onChanged);
        add(layout, context, "Нерабочий прокси выключается сам", KamiGramConfig.KEY_PROXY_FALLBACK, onChanged);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            new AlertDialog.Builder(context)
                .setTitle("KamiGram: функции мода")
                .setView(scroll)
                .setPositiveButton("Готово", null)
                .show();
        } catch (Throwable ignore) {
        }
    }

    private static void add(LinearLayout parent, Context context, String title, final String key, final Runnable onChanged) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9));

        final TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(15);
        text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        final LinearLayout.LayoutParams textParams =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.rightMargin = AndroidUtilities.dp(10);
        row.addView(text, textParams);

        final Switch toggle = new Switch(context);
        toggle.setChecked(KamiGramConfig.value(key), false);
        toggle.setOnCheckedChangeListener((view, isChecked) -> {
            KamiGramConfig.set(key, isChecked);
            if (onChanged != null) {
                onChanged.run();
            }
        });
        row.addView(toggle, new LinearLayout.LayoutParams(AndroidUtilities.dp(37), AndroidUtilities.dp(40)));

        parent.addView(row, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
