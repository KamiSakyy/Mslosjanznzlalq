package org.telegram.messenger.kamigram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.ProxyListActivity;

/**
 * KamiGram: центр настроек (Material 3 / iOS, тёмная тема).
 *
 * Главное отличие от прошлой версии: КАЖДАЯ строка нажимается целиком —
 * по строке, а не только по маленькому переключателю. Поэтому «не работает
 * ползунок» больше невозможно: тап в любом месте строки меняет значение и
 * сохраняет его сразу.
 *
 * Тексты короткие и по делу, без «подсказок для разработчиков».
 */
public final class KamiGramCenter {

    private KamiGramCenter() {
    }

    public static void show(final Context context) {
        show(context, null);
    }

    // ------------------------------------------------------------------ экран

    public static void show(final Context context, final Runnable onChanged) {
        if (context == null) {
            return;
        }
        try {
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(18));

            // ---------------------------------------------------------- ЭКОНОМИЯ
            section(root, context, "ЭКОНОМИЯ ТРАФИКА");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Только текст: без картинок и медиа", KamiGramConfig.KEY_TEXT_ONLY,
                    "Ни одной картинки, фото и видео — только по нажатию. Самая жёсткая экономия.", onChanged),
                Row.toggle(context, "Не грузить стикеры и наборы", KamiGramConfig.KEY_NO_STICKERS, null, onChanged),
                Row.toggle(context, "Не грузить GIF и анимации", KamiGramConfig.KEY_NO_GIFS, null, onChanged),
                Row.toggle(context, "Не грузить истории", KamiGramConfig.KEY_NO_STORIES, null, onChanged),
                Row.toggle(context, "Премиум-эмодзи как обычные эмодзи", KamiGramConfig.KEY_NO_ANIMATED_EMOJI, null, onChanged),
                Row.toggle(context, "Без превью ссылок", KamiGramConfig.KEY_NO_LINK_PREVIEW, null, onChanged),
                Row.toggle(context, "Без поиска GIF и стикеров", KamiGramConfig.KEY_NO_GIF_SEARCH, null, onChanged),
                Row.toggle(context, "Без «часто используемых»", KamiGramConfig.KEY_NO_TOP_PEERS, null, onChanged),
                Row.toggle(context, "Без рекламы и рекомендаций", KamiGramConfig.KEY_NO_ADS, null, onChanged),
                Row.toggle(context, "Без Premium / Stars / TON", KamiGramConfig.KEY_NO_PREMIUM_UI, null, onChanged),
                Row.toggle(context, "Ускорение загрузок", KamiGramConfig.KEY_FAST_NET, null, onChanged),
                Row.info("Трафик: " + KamiGramTraffic.describe()),
                Row.info(KamiGramTraffic.economyText()),
                Row.action("Обнулить счётчик экономии", () -> {
                    KamiGramTraffic.reset();
                    KamiGramUi.notify(context, "Счётчик сброшен");
                })
            });

            // ---------------------------------------------------------- ФИЛЬТРЫ
            section(root, context, "ФИЛЬТРЫ");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Скрывать рекламные посты", KamiGramConfig.KEY_ADS_FILTER,
                    "Сообщения с метками «реклама», «#реклама», «erid», «промокод» не показываются.", onChanged),
                Row.action("Показать скрытые рекламные сообщения", () -> {
                    KamiGramAds.showHiddenReport(context);
                }),
                Row.info(KamiGramAds.describe())
            });

            // ---------------------------------------------------------- ПРОКСИ
            section(root, context, "KAMIPROXY (ВСТРОЕННЫЕ ПРОКСИ)");
            card(root, context, onChanged, new Row[]{
                Row.info("Состояние: " + KamiGramSelfCheck.describe() + " · " + KamiGramFirstRun.describe()),
                Row.toggle(context, "KamiProxy включён", KamiGramConfig.KEY_BUILTIN_PROXY,
                    "Встроенные прокси подключаются сами и переключаются на самый быстрый.", onChanged),
                Row.info(KamiGramBuiltinProxy.statusText()),
                Row.info("Прокси в сборке: " + KamiGramBuiltinProxy.count()
                    + " · живых сейчас: " + KamiGramBuiltinProxy.aliveCount()),
                Row.action("Проверить и подключить лучший", () -> {
                    KamiGramBuiltinProxy.refreshNow(context);
                    KamiGramUi.notify(context, KamiGramBuiltinProxy.statusText());
                }),
                Row.action("Открыть прокси Telegram", () -> openProxyScreen(context)),
                Row.toggle(context, "Прокси из буфера подключать сам", KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, null, onChanged),
                Row.toggle(context, "Мощное переключение (своё)", KamiGramConfig.KEY_SMART_PROXY, null, onChanged)
            });

            // ---------------------------------------------------------- КЭШ
            section(root, context, "КЭШ И ФАЙЛЫ");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Скачанное вручную не удалять", KamiGramConfig.KEY_KEEP_DOWNLOADS,
                    "Очистка кэша работает всегда — эта галочка только защищает ваши файлы.", onChanged),
                Row.info(KamiGramCache.describe()),
                Row.action("Очистить весь кэш", () -> confirm(context, "Очистить весь кэш?",
                    "Файлы, скачанные вручную, останутся, если включена защита.", () -> {
                        KamiGramCache.clearAll();
                        KamiGramUi.notify(context, "Кэш очищен");
                    })),
                Row.action("Освободить память", () -> {
                    KamiGramCache.freeMemory();
                    KamiGramUi.notify(context, "Память освобождена");
                }),
                Row.action("Менеджер загрузок", () -> openDownloads(context))
            });
            cacheCategories(root, context);

            // ---------------------------------------------------------- ID
            section(root, context, "ID И ССЫЛКИ");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Показывать ID под @username в профиле", KamiGramConfig.KEY_SHOW_IDS, null, onChanged),
                Row.info("Мой ID: " + myId()),
                Row.action("Копировать мой ID", () -> copy(context, "ID", myId())),
                Row.action("Копировать @username", () -> copy(context, "@", "@" + myUsername())),
                Row.action("Копировать ссылку на меня", () -> copy(context, "Ссылка", "https://t.me/" + myUsername()))
            });

            // ---------------------------------------------------------- ПРИВАТНОСТЬ
            section(root, context, "ПРИВАТНОСТЬ");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Призрак: нет чтения, «печатает», «в сети»", KamiGramConfig.KEY_GHOST, null, onChanged),
                Row.toggle(context, "Призрак для историй", KamiGramConfig.KEY_STORIES_STEALTH, null, onChanged),
                Row.toggle(context, "Отправлять тихо (без выхода в сеть)", KamiGramConfig.KEY_GHOST_SEND,
                    "Сообщение уходит отложенным, поэтому «в сети» не появляется.", onChanged),
                Row.toggle(context, "Снять запреты защищённого контента", KamiGramConfig.KEY_NO_RESTRICTIONS, null, onChanged),
                Row.toggle(context, "Скрывать текст уведомлений", KamiGramConfig.KEY_HIDE_NOTIFICATION_TEXT, null, onChanged),
                Row.toggle(context, "Запретить скриншоты", KamiGramConfig.KEY_NO_SCREENSHOTS, null, onChanged),
                Row.toggle(context, "Не спрашивать разрешения", KamiGramConfig.KEY_NO_PERMISSION_NAGS, null, onChanged),
                Row.info(KamiGramGhost.statusText())
            });

            // ---------------------------------------------------------- ЖУРНАЛ
            section(root, context, "УДАЛЁННЫЕ СООБЩЕНИЯ");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Сохранять текст удалённых", KamiGramConfig.KEY_KEEP_DELETED, null, onChanged),
                Row.action("Открыть журнал (" + KamiGramDeleted.size() + ")", () -> KamiGramDeleted.show(context)),
                Row.action("Очистить журнал", () -> {
                    KamiGramDeleted.clear();
                    KamiGramUi.notify(context, "Журнал очищен");
                })
            });

            // ---------------------------------------------------------- МОИ КАНАЛЫ
            section(root, context, "МОИ КАНАЛЫ");
            card(root, context, onChanged, new Row[]{
                Row.info("Галочка как в Telegram: " + KamiGramVerified.describe()),
                Row.info("Видно только у тебя: права выдаются на этом устройстве."),
                Row.toggle(context, "Режим «только текст»", KamiGramConfig.KEY_TEXT_ONLY, null, onChanged)
            });

            // ---------------------------------------------------------- ДИЗАЙН
            section(root, context, "ОФОРМЛЕНИЕ");
            card(root, context, onChanged, new Row[]{
                Row.toggle(context, "Отправлять по Enter", KamiGramConfig.KEY_ENTER_TO_SEND, null, onChanged),
                Row.toggle(context, "Компактный список чатов", KamiGramConfig.KEY_COMPACT_CHATS, null, onChanged)
            });
            accentPicker(root, context, onChanged);
            fontPicker(root, context, onChanged);

            // ---------------------------------------------------------- АККАУНТЫ
            section(root, context, "АККАУНТЫ");
            card(root, context, onChanged, new Row[]{
                Row.info("Лимит аккаунтов: " + UserConfig.MAX_ACCOUNT_COUNT + " (в модe расширено)"),
                Row.toggle(context, "Всегда простой SMS-код", KamiGramConfig.KEY_FORCE_SMS, null, onChanged),
                Row.toggle(context, "Быстрый вход", KamiGramConfig.KEY_FAST_LOGIN, null, onChanged)
            });

            final ScrollView scroll = new ScrollView(context);
            scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            KamiGramDialog.create(context)
                .title("KamiGram")
                .content(scroll)
                .positive("Готово", null)
                .show();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ кэш по категориям

    private static void cacheCategories(LinearLayout root, final Context context) {
        final Row[] rows = new Row[KamiGramCache.TYPE_COUNT + 1];
        for (int i = 0; i < KamiGramCache.TYPE_COUNT; i++) {
            final int type = i;
            rows[i] = Row.action(KamiGramCache.nameOf(type) + " · " + KamiGramCache.human(KamiGramCache.sizeOf(type)),
                () -> {
                    KamiGramCache.clear(type);
                    KamiGramUi.notify(context, KamiGramCache.nameOf(type) + " очищено");
                });
        }
        rows[KamiGramCache.TYPE_COUNT] = Row.info("Всего занято: " + KamiGramCache.human(KamiGramCache.total()));
        card(root, context, null, rows);
    }

    // ------------------------------------------------------------------ акцент

    private static void accentPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        row.setBackground(cardBackground());

        final TextView title = new TextView(context);
        title.setText("Акцент");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTextColor(ThemeHook.primaryText());
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final int[] colors = {0xFF0A84FF, 0xFF32ADE6, 0xFF34C759, 0xFFFF9F0A, 0xFFFF453A, 0xFF8E8E93, 0xFFFF375F};
        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            final View dot = new View(context);
            final GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(colors[i]);
            if (KamiGramConfig.accentIndex() == i) {
                shape.setStroke(AndroidUtilities.dp(2), 0xFFFFFFFF);
            }
            dot.setBackground(shape);
            dot.setOnClickListener(v -> {
                KamiGramConfig.setAccent(index);
                ThemeHook.notifyAccentChanged();
                KamiGramUi.notify(context, "Акцент: " + KamiGramConfig.accentName());
                if (onChanged != null) {
                    onChanged.run();
                }
            });
            final LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            params.leftMargin = AndroidUtilities.dp(6);
            row.addView(dot, params);
        }
        root.addView(row, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spacing(root, 10);
    }

    private static void fontPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final Row[] rows = new Row[5];
        for (int i = 0; i < 5; i++) {
            final int boost = i;
            rows[i] = Row.action((KamiGramConfig.fontBoost() == i ? "Размер текста — " : "")
                + (i == 0 ? "как в Telegram" : "крупнее на " + i),
                () -> {
                    KamiGramConfig.setInt(KamiGramConfig.KEY_FONT_BOOST, boost);
                    SharedConfig.fontSize = 16 + boost;
                    KamiGramUi.notify(context, "Размер текста: " + (16 + boost));
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
        }
        card(root, context, null, rows);
    }

    // ------------------------------------------------------------------ переходы

    public static void openProxyScreen(Context context) {
        try {
            if (context instanceof org.telegram.ui.LaunchActivity) {
                ((org.telegram.ui.LaunchActivity) context).presentFragment(new ProxyListActivity());
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    public static void openDownloads(Context context) {
        try {
            if (context instanceof org.telegram.ui.LaunchActivity) {
                ((org.telegram.ui.LaunchActivity) context).presentFragment(new CacheControlActivity());
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ утилиты

    private static String myId() {
        try {
            return Long.toString(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
        } catch (Throwable ignore) {
            return "?";
        }
    }

    private static String myUsername() {
        try {
            final TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            if (user == null) {
                return "me";
            }
            final String username = UserObject.getPublicUsername(user);
            return username != null ? username : "me";
        } catch (Throwable ignore) {
            return "me";
        }
    }

    private static void copy(Context context, String label, String value) {
        try {
            final ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText(label, value));
                KamiGramUi.notify(context, value + " — скопировано");
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void confirm(final Context context, String title, String message, final Runnable onYes) {
        KamiGramDialog.create(context)
            .title(title)
            .message(message)
            .icon(KamiGramDialog.ICON_CHECK)
            .positive("Да", onYes)
            .negative("Отмена", null)
            .show();
    }

    // ------------------------------------------------------------------ вид

    private static void card(LinearLayout root, Context context, Runnable onChanged, Row[] rows) {
        if (rows == null || rows.length == 0) {
            return;
        }
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(cardBackground());
        for (int i = 0; i < rows.length; i++) {
            container.addView(rows[i].build(context), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i != rows.length - 1) {
                final View divider = new View(context);
                divider.setBackgroundColor(ThemeHook.separator());
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(1));
                params.leftMargin = AndroidUtilities.dp(12);
                container.addView(divider, params);
            }
        }
        root.addView(container, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spacing(root, 10);
    }

    private static GradientDrawable cardBackground() {
        final GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(ThemeHook.surface());
        shape.setCornerRadius(AndroidUtilities.dp(16));
        return shape;
    }

    private static void section(LinearLayout root, Context context, String title) {
        final TextView text = new TextView(context);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setLetterSpacing(0.08f);
        text.setTextColor(ThemeHook.secondaryText());
        text.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(14), 0, AndroidUtilities.dp(6));
        root.addView(text, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void spacing(LinearLayout root, int dp) {
        final View space = new View(root.getContext());
        root.addView(space, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(dp)));
    }

    /** Строка карточки: переключатель, действие или пояснение. */
    private static final class Row {

        private static final int TYPE_TOGGLE = 0;
        private static final int TYPE_ACTION = 1;
        private static final int TYPE_INFO = 2;

        private final int type;
        private final String title;
        private final String hint;
        private final String key;
        private final Runnable click;
        private final Runnable onChanged;

        private Row(int type, String title, String key, String hint, Runnable click, Runnable onChanged) {
            this.type = type;
            this.title = title;
            this.key = key;
            this.hint = hint;
            this.click = click;
            this.onChanged = onChanged;
        }

        /**
         * Строка-переключатель. Нажимается ВСЯ строка (а не только ползунок),
         * поэтому «не работает» исключено.
         */
        static Row toggle(final Context context, String title, final String key, String hint, final Runnable onChanged) {
            // ВАЖНО: значение переключателя записывается ровно в одном месте ниже —
            // в обработчике самого переключателя. Раньше здесь был ещё один обработчик,
            // который переворачивал значение обратно, и ползунок «не включался».
            return new Row(TYPE_TOGGLE, title, key, hint, null, onChanged);
        }

        static Row action(String title, Runnable click) {
            return new Row(TYPE_ACTION, title, null, null, click, null);
        }

        static Row info(String title) {
            return new Row(TYPE_INFO, title, null, null, null, null);
        }

        View build(final Context context) {
            if (type == TYPE_INFO) {
                final TextView view = new TextView(context);
                view.setText(title);
                view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                view.setTextColor(ThemeHook.secondaryText());
                view.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9), AndroidUtilities.dp(12), AndroidUtilities.dp(9));
                return view;
            }

            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));

            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            final TextView text = new TextView(context);
            text.setText(title);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(type == TYPE_ACTION ? ThemeHook.accent() : ThemeHook.primaryText());
            texts.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (hint != null) {
                final TextView hintView = new TextView(context);
                hintView.setText(hint);
                hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                hintView.setTextColor(ThemeHook.secondaryText());
                hintView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
                texts.addView(hintView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            final LinearLayout.LayoutParams textsParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.rightMargin = AndroidUtilities.dp(10);
            row.addView(texts, textsParams);

            if (type == TYPE_TOGGLE) {
                final KamiGramUi.Toggle toggle = new KamiGramUi.Toggle(context);
                toggle.setChecked(KamiGramConfig.value(key), false);
                // значение пишется ровно один раз на каждое действие пользователя:
                // apply() вызывается и когда тронули ползунок, и когда нажали строку
                final Runnable apply = () -> {
                    KamiGramConfig.set(key, toggle.isChecked());
                    if (onChanged != null) {
                        onChanged.run();
                    }
                };
                toggle.setOnToggleListener(checked -> {
                    toggle.setChecked(checked, false);
                    apply.run();
                });
                final LinearLayout.LayoutParams toggleParams =
                    new LinearLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(24));
                row.addView(toggle, toggleParams);
                // тап по всей строке переключает — «не работает» исключено
                row.setOnClickListener(v -> {
                    toggle.setChecked(!toggle.isChecked(), true);
                    apply.run();
                });
            } else {
                row.setOnClickListener(v -> {
                    try {
                        if (click != null) {
                            click.run();
                        }
                    } catch (Throwable throwable) {
                        FileLog.e(throwable);
                    }
                });
            }
            return row;
        }
    }
}
