package org.telegram.messenger.kamigram;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Switch;

/**
 * KamiGram: экран функций мода «Настройки → KamiGram».
 *
 * Разделы как в iOS: крупные заголовки групп, ровные строки, серые подписи.
 * Здесь видно и переключается всё, что делает мод модом: мощный прокси,
 * нулевой трафик, сохранность кэша, iOS-дизайн, призрак и остальное.
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
        layout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(2), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        // ---------------------------------------------------------------- ПРОКСИ
        header(layout, context, "ПРОКСИ");
        add(layout, context, "Мощный прокси: моментальное переключение на рабочий",
            KamiGramConfig.KEY_SMART_PROXY, onChanged);
        add(layout, context, "Нерабочий прокси выключается сам",
            KamiGramConfig.KEY_PROXY_FALLBACK, onChanged);
        add(layout, context, "Прокси из буфера обмена включается сам",
            KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, onChanged);
        action(layout, context, "Панель прокси: ссылка и список живых",
            () -> KamiGramProxyButton.showPanel(context));
        action(layout, context, "Вставить ссылку на прокси",
            () -> KamiGramProxyButton.showLinkDialog(context, null));
        info(layout, context, KamiGramProxyPower.statusText());

        // ---------------------------------------------------------------- ТРАФИК
        header(layout, context, "ТРАФИК И СКОРОСТЬ");
        add(layout, context, "Ускорение загрузок и потоков (даже на слабом интернете)",
            KamiGramConfig.KEY_FAST_NET, onChanged);
        add(layout, context, "Не грузить стикеры и наборы эмодзи (0 байт)",
            KamiGramConfig.KEY_NO_STICKERS, onChanged);
        add(layout, context, "Не грузить истории и их медиа (0 байт)",
            KamiGramConfig.KEY_NO_STORIES, onChanged);
        add(layout, context, "Не грузить GIF и анимации (0 байт)",
            KamiGramConfig.KEY_NO_GIFS, onChanged);
        add(layout, context, "Премиум-эмодзи показывать обычным эмодзи (без .tgs)",
            KamiGramConfig.KEY_NO_ANIMATED_EMOJI, onChanged);
        add(layout, context, "Не грузить превью ссылок и веб-страницы",
            KamiGramConfig.KEY_NO_LINK_PREVIEW, onChanged);
        add(layout, context, "Не искать GIF и стикеры при вводе текста",
            KamiGramConfig.KEY_NO_GIF_SEARCH, onChanged);
        add(layout, context, "Не грузить «часто используемые» контакты",
            KamiGramConfig.KEY_NO_TOP_PEERS, onChanged);
        add(layout, context, "Убрать рекламу, спонсорские и рекомендации каналов",
            KamiGramConfig.KEY_NO_ADS, onChanged);
        add(layout, context, "Убрать блоки Telegram Premium / Stars / TON",
            KamiGramConfig.KEY_NO_PREMIUM_UI, onChanged);
        info(layout, context, "Сеть: " + KamiGramSpeed.describe());

        // ---------------------------------------------------------------- КЭШ
        header(layout, context, "КЭШ И ФАЙЛЫ");
        add(layout, context, "Скачанное не удалять автоматически",
            KamiGramConfig.KEY_KEEP_DOWNLOADS, onChanged);
        info(layout, context, KamiGramCache.describe());
        action(layout, context, "Проверить прокси и переключиться на лучший",
            () -> {
                KamiGramProxyPower.refreshNow(context);
                Toast.makeText(context, KamiGramProxyPower.statusText(), Toast.LENGTH_LONG).show();
            });

        // ---------------------------------------------------------------- ДИЗАЙН
        header(layout, context, "ДИЗАЙН");
        add(layout, context, "iOS-дизайн KamiGram: графит и индиго",
            KamiGramConfig.KEY_IOS_DESIGN, onChanged);
        add(layout, context, "Скругления облаков сообщений как в iOS",
            KamiGramConfig.KEY_IOS_BUBBLES, onChanged);
        action(layout, context, "Применить iOS-цвета ещё раз",
            () -> {
                KamiGramTheme.apply();
                Toast.makeText(context, "Цвета обновлены", Toast.LENGTH_SHORT).show();
            });

        // ---------------------------------------------------------------- ПРИВАТНОСТЬ
        header(layout, context, "ПРИВАТНОСТЬ И УДОБСТВО");
        add(layout, context, "Призрак: не видно чтение, «печатает» и «в сети»",
            KamiGramConfig.KEY_GHOST, onChanged);
        add(layout, context, "Призрак для историй: просмотры не записываются",
            KamiGramConfig.KEY_STORIES_STEALTH, onChanged);
        add(layout, context, "Снять запреты защищённого контента",
            KamiGramConfig.KEY_NO_RESTRICTIONS, onChanged);
        add(layout, context, "Показывать ID чатов и пользователей",
            KamiGramConfig.KEY_SHOW_IDS, onChanged);
        add(layout, context, "Не спрашивать разрешения (контакты, телефон, уведомления)",
            KamiGramConfig.KEY_NO_PERMISSION_NAGS, onChanged);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("KamiGram: функции мода")
                .setView(scroll)
                .setPositiveButton("Готово", null)
                .create();
            dialog.show();
        } catch (Throwable ignore) {
        }
    }

    /** Заголовок группы (как в iOS). */
    private static void header(LinearLayout parent, Context context, String title) {
        final TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setLetterSpacing(0.06f);
        text.setTextColor(0xFF8E8E93);
        text.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(14), 0, AndroidUtilities.dp(4));
        parent.addView(text, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Серая пояснительная строка. */
    private static void info(LinearLayout parent, Context context, String text) {
        final TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        view.setTextColor(0xFF8E8E93);
        view.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(6));
        parent.addView(view, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Строка-действие (не переключатель). */
    private static void action(LinearLayout parent, Context context, String title, final Runnable click) {
        final TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        text.setTextColor(0xFF5E5CE6);
        text.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(11), AndroidUtilities.dp(4), AndroidUtilities.dp(11));
        text.setOnClickListener(v -> {
            if (click != null) {
                click.run();
            }
        });
        parent.addView(text, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void add(LinearLayout parent, Context context, String title, final String key, final Runnable onChanged) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9));

        final TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
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
