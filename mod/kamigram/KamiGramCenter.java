package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProxyListActivity;

/**
 * KamiGram: центр настроек мода.
 *
 * Как устроено (переделано по замечаниям: «слишком перегружено, листать
 * постоянно, кнопка «сохранить» неудобно»):
 *   * сверху — разделы (вкладки): Трафик, Приватность, Прокси, Вид, Прочее.
 *     Переключение одним касанием, ленты внутри короткие, длинной прокрутки нет;
 *   * настройки применяются СРАЗУ при касании — отдельной кнопки «Сохранить»
 *     больше не нужно;
 *   * снизу закреплена кнопка «Готово» — она всегда на виду, не надо листать;
 *   * тексты короткие, без подсказок и «детских» надписей.
 */
public final class KamiGramCenter {

    private KamiGramCenter() {
    }

    /** Открытый сейчас диалог центра — чтобы кнопка «Готово» закрывала именно его. */
    private static android.app.Dialog shownDialog;

    /** Список разделов: подпись и наполнение. */
    private interface Section {
        void fill(LinearLayout root, Context context, Runnable onChanged);
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
            final String[] tabs = {"Трафик", "Приватность", "Прокси", "Вид", "Прочее"};

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);

            // ---- шапка
            final LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(18), dp(16), dp(12), dp(10));

            final TextView title = new TextView(context);
            title.setText("KamiGram");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(KamiGramUi.primaryText());
            header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView close = new TextView(context);
            close.setText("✕");
            close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
            close.setTextColor(KamiGramUi.secondaryText());
            close.setGravity(Gravity.CENTER);
            close.setOnClickListener(v -> dismissAll(context));
            header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
            root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // ---- вкладки
            final LinearLayout tabStrip = new LinearLayout(context);
            tabStrip.setOrientation(LinearLayout.HORIZONTAL);
            tabStrip.setPadding(dp(12), 0, dp(12), dp(6));
            root.addView(tabStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // ---- содержимое (прокручивается внутри выбранной вкладки)
            final ScrollView scroll = new ScrollView(context);
            scroll.setClipToPadding(false);
            final LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(14), dp(4), dp(14), dp(14));
            scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            // высота списка задана явно: карточка диалога измеряется по содержимому,
            // поэтому «вес» здесь не работает
            final int viewport = Math.max(dp(240), (int) (AndroidUtilities.displaySize.y * 0.58f));
            root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, viewport));

            // ---- закреплённая кнопка «Готово»
            final TextView done = new TextView(context);
            done.setText("Готово");
            done.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
            done.setTypeface(AndroidUtilities.bold());
            done.setTextColor(KamiGramUi.accent());
            done.setGravity(Gravity.CENTER);
            done.setBackground(rounded(KamiGramUi.surface(), 14));
            done.setOnClickListener(v -> dismissAll(context));
            final LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            doneParams.setMargins(dp(14), dp(6), dp(14), dp(14));
            root.addView(done, doneParams);

            final Section[] sections = {KamiGramCenter::fillTraffic, KamiGramCenter::fillPrivacy,
                KamiGramCenter::fillProxy, KamiGramCenter::fillLook, KamiGramCenter::fillOther};

            final int[] current = {0};
            final TextView[] tabViews = new TextView[tabs.length];
            for (int i = 0; i < tabs.length; i++) {
                final int index = i;
                final TextView tab = new TextView(context);
                tab.setText(tabs[i]);
                tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
                tab.setGravity(Gravity.CENTER);
                tab.setPadding(dp(10), dp(9), dp(10), dp(9));
                tab.setTypeface(i == 0 ? AndroidUtilities.bold() : Typeface.DEFAULT);
                tab.setTextColor(i == 0 ? KamiGramUi.primaryText() : KamiGramUi.secondaryText());
                tab.setOnClickListener(v -> {
                    if (current[0] == index) {
                        return;
                    }
                    current[0] = index;
                    for (int t = 0; t < tabViews.length; t++) {
                        final boolean on = t == index;
                        tabViews[t].setTypeface(on ? AndroidUtilities.bold() : Typeface.DEFAULT);
                        tabViews[t].setTextColor(on ? KamiGramUi.primaryText() : KamiGramUi.secondaryText());
                        tabViews[t].setBackground(on ? tabBackground() : null);
                    }
                    content.removeAllViews();
                    try {
                        sections[index].fill(content, context, onChanged);
                    } catch (Throwable throwable) {
                        FileLog.e(throwable);
                    }
                    scroll.scrollTo(0, 0);
                });
                tab.setBackground(i == 0 ? tabBackground() : null);
                tabViews[i] = tab;
                final LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tabParams.setMargins(dp(2), 0, dp(2), 0);
                tabStrip.addView(tab, tabParams);
            }

            sections[0].fill(content, context, onChanged);

            final android.app.Dialog dialog = KamiGramDialog.create(context)
                .title(null)
                .content(root)
                .show();
            shownDialog = dialog;
            if (dialog != null) {
                final Window window = dialog.getWindow();
                if (window != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void dismissAll(Context context) {
        final android.app.Dialog dialog = shownDialog;
        shownDialog = null;
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        } catch (Throwable ignore) {
        }
        KamiGramUi.notify(context, "Настройки сохранены");
    }

    // ------------------------------------------------------------------ ТРАФИК

    private static void fillTraffic(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Только текст", KamiGramConfig.KEY_TEXT_ONLY,
                "Фото и видео — по нажатию.", onChanged),
            Row.toggle(context, "Не грузить стикеры", KamiGramConfig.KEY_NO_STICKERS, null, onChanged),
            Row.toggle(context, "Не грузить анимации (GIF)", KamiGramConfig.KEY_NO_GIFS, null, onChanged),
            Row.toggle(context, "Не грузить премиум-эмодзи", KamiGramConfig.KEY_NO_ANIMATED_EMOJI, null, onChanged),
            Row.toggle(context, "Не грузить истории", KamiGramConfig.KEY_NO_STORIES, null, onChanged),
            Row.toggle(context, "Без превью ссылок", KamiGramConfig.KEY_NO_LINK_PREVIEW, null, onChanged),
            Row.toggle(context, "Без поиска GIF и стикеров", KamiGramConfig.KEY_NO_GIF_SEARCH, null, onChanged),
            Row.toggle(context, "Без «часто используемых»", KamiGramConfig.KEY_NO_TOP_PEERS, null, onChanged),
            Row.toggle(context, "Без рекламы и рекомендаций", KamiGramConfig.KEY_NO_ADS, null, onChanged),
            Row.toggle(context, "Без Premium, Stars и TON", KamiGramConfig.KEY_NO_PREMIUM_UI, null, onChanged),
            Row.toggle(context, "Ускорение загрузок", KamiGramConfig.KEY_FAST_NET, null, onChanged)
        });
        card(root, context, onChanged, new Row[]{
            Row.info(KamiGramTraffic.describe()),
            Row.info(KamiGramTraffic.economyText()),
            Row.action("Обнулить счётчик", () -> {
                KamiGramTraffic.reset();
                KamiGramUi.notify(context, "Счётчик обнулён");
            })
        });
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Скрывать рекламные посты", KamiGramConfig.KEY_ADS_FILTER, null, onChanged),
            Row.info(KamiGramAds.describe()),
            Row.action("Показать скрытую рекламу", () -> KamiGramAds.showHiddenReport(context))
        });
    }

    // ------------------------------------------------------------------ ПРИВАТНОСТЬ

    private static void fillPrivacy(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Призрак", KamiGramConfig.KEY_GHOST,
                "Нет отметок «в сети», «печатает» и прочтений.", onChanged),
            Row.info(KamiGramGhost.statusText()),
            Row.toggle(context, "Призрак для историй", KamiGramConfig.KEY_STORIES_STEALTH, null, onChanged),
            Row.toggle(context, "Снять запреты защищённого контента", KamiGramConfig.KEY_NO_RESTRICTIONS, null, onChanged),
            Row.toggle(context, "Скрывать текст уведомлений", KamiGramConfig.KEY_HIDE_NOTIFICATION_TEXT, null, onChanged),
            Row.toggle(context, "Запретить скриншоты", KamiGramConfig.KEY_NO_SCREENSHOTS, null, onChanged),
            Row.toggle(context, "Не спрашивать разрешения", KamiGramConfig.KEY_NO_PERMISSION_NAGS, null, onChanged)
        });
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Сохранять удалённые сообщения", KamiGramConfig.KEY_KEEP_DELETED, null, onChanged),
            Row.action("Журнал удалённых (" + KamiGramDeleted.size() + ")",
                () -> KamiGramDeleted.show(context)),
            Row.action("Очистить журнал", () -> {
                KamiGramDeleted.clear();
                KamiGramUi.notify(context, "Журнал очищен");
            })
        });
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Показывать ID", KamiGramConfig.KEY_SHOW_IDS, null, onChanged),
            Row.info("Мой ID: " + myId()),
            Row.action("Копировать мой ID", () -> copy(context, "ID", myId())),
            Row.action("Копировать @username", () -> copy(context, "@", "@" + myUsername()))
        });
    }

    // ------------------------------------------------------------------ ПРОКСИ

    private static void fillProxy(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "KamiProxy", KamiGramConfig.KEY_BUILTIN_PROXY,
                "Прокси из сборки: включаются сами и выбирают самый быстрый.", onChanged),
            Row.info(KamiGramBuiltinProxy.statusText()),
            Row.info("Прокси в сборке: " + KamiGramBuiltinProxy.count()
                + " · живых: " + KamiGramBuiltinProxy.aliveCount()),
            Row.action("Проверить и подключить лучший", () -> {
                KamiGramBuiltinProxy.refreshNow(context);
                KamiGramUi.notify(context, KamiGramBuiltinProxy.statusText());
            }),
            Row.action("Открыть прокси Telegram", () -> openProxyScreen(context))
        });
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Подключать прокси из буфера", KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, null, onChanged),
            Row.toggle(context, "Быстрое переключение", KamiGramConfig.KEY_SMART_PROXY, null, onChanged),
            Row.info("VPN: если Telegram не подключается, KamiProxy поднимается и при включённом VPN.")
        });
    }

    // ------------------------------------------------------------------ ВИД

    private static void fillLook(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, onChanged, null);
        accentPicker(root, context, onChanged);
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Отправлять по Enter", KamiGramConfig.KEY_ENTER_TO_SEND, null, onChanged),
            Row.toggle(context, "Компактный список чатов", KamiGramConfig.KEY_COMPACT_CHATS, null, onChanged)
        });
        fontPicker(root, context, onChanged);
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Плавные анимации", KamiGramConfig.KEY_SMOOTH_ANIMATIONS,
                "Больше анимаций и мягче прокрутка.", onChanged),
            Row.toggle(context, "Размытие и «стекло»", KamiGramConfig.KEY_ALLOW_BLUR,
                "Выключено — интерфейс быстрее.", onChanged)
        });
    }

    // ------------------------------------------------------------------ ПРОЧЕЕ

    private static void fillOther(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, onChanged, new Row[]{
            Row.info("Аккаунтов доступно: " + UserConfig.MAX_ACCOUNT_COUNT),
            Row.info("Галочка: " + KamiGramVerified.describe()),
            Row.toggle(context, "Всегда простой SMS-код", KamiGramConfig.KEY_FORCE_SMS, null, onChanged),
            Row.toggle(context, "Быстрый вход", KamiGramConfig.KEY_FAST_LOGIN, null, onChanged),
            Row.info("Состояние: " + KamiGramSelfCheck.describe() + " · " + KamiGramFirstRun.describe())
        });
        card(root, context, onChanged, new Row[]{
            Row.toggle(context, "Скачанное вручную не удалять", KamiGramConfig.KEY_KEEP_DOWNLOADS, null, onChanged),
            Row.info(KamiGramCache.describe()),
            Row.action("Менеджер загрузок", () -> openDownloads(context))
        });
        cacheCategories(root, context);
        card(root, context, onChanged, new Row[]{
            Row.action("Очистить весь кэш", () -> confirm(context, "Очистить весь кэш?",
                "Скачанное вручную не удаляется.", () -> {
                    KamiGramCache.clearAll();
                    KamiGramUi.notify(context, "Кэш очищен");
                })),
            Row.action("Освободить память", () -> {
                KamiGramCache.freeMemory();
                KamiGramUi.notify(context, "Память освобождена");
            })
        });
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
        final String[] names = {"Как в Telegram", "Синий", "Зелёный", "Голубой", "Оранжевый", "Розовый"};
        final Row[] rows = new Row[names.length];
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            rows[i] = Row.action((KamiGramConfig.accentIndex() == i ? "Акцент — " : "") + names[i],
                () -> {
                    KamiGramConfig.setInt(KamiGramConfig.KEY_ACCENT, index);
                    ThemeHook.notifyAccentChanged();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    KamiGramUi.notify(context, "Акцент: " + names[index]);
                });
        }
        card(root, context, onChanged, rows);
    }

    // ------------------------------------------------------------------ размер текста и свой шрифт

    private static void fontPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final String[] names = {"Как в Telegram", "Крупнее на 1", "Крупнее на 2", "Крупнее на 3"};
        final Row[] rows = new Row[names.length + 3];
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            rows[i] = Row.action((KamiGramConfig.fontBoost() == i ? "Размер — " : "") + names[i],
                () -> {
                    KamiGramConfig.setInt(KamiGramConfig.KEY_FONT_BOOST, index);
                    KamiGramTweaks.applyFontSize();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
        }
        rows[names.length] = Row.info("Свой шрифт: " + KamiGramFont.describe());
        rows[names.length + 1] = Row.action("Выбрать шрифт (.ttf, .otf)",
            () -> KamiGramFont.pick(context));
        rows[names.length + 2] = Row.action("Сбросить шрифт", () -> {
            KamiGramFont.reset();
            KamiGramUi.notify(context, "Шрифт сброшен");
        });
        card(root, context, onChanged, rows);
    }

    // ------------------------------------------------------------------ открытие экранов

    public static void openProxyScreen(Context context) {
        try {
            final Activity activity = AndroidUtilities.findActivity(context);
            if (activity instanceof LaunchActivity) {
                ((LaunchActivity) activity).presentFragment(new ProxyListActivity());
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    public static void openDownloads(Context context) {
        try {
            final Activity activity = AndroidUtilities.findActivity(context);
            if (activity instanceof LaunchActivity) {
                ((LaunchActivity) activity).presentFragment(new CacheControlActivity());
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ данные профиля

    private static String myId() {
        try {
            return Long.toString(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
        } catch (Throwable ignore) {
            return "—";
        }
    }

    private static String myUsername() {
        try {
            final org.telegram.tgnet.TLRPC.User user =
                UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            return user != null && user.username != null ? user.username : "";
        } catch (Throwable ignore) {
            return "";
        }
    }

    private static void copy(Context context, String label, String value) {
        try {
            final ClipboardManager manager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
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
                divider.setBackgroundColor(KamiGramUi.separator());
                final LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
                dividerParams.leftMargin = dp(12);
                dividerParams.rightMargin = dp(12);
                container.addView(divider, dividerParams);
            }
        }
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        root.addView(container, params);
    }

    private static GradientDrawable cardBackground() {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(KamiGramUi.surface());
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private static GradientDrawable tabBackground() {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(KamiGramUi.surface());
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private static GradientDrawable rounded(int color, int radius) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private static int dp(float value) {
        return (int) (value * AndroidUtilities.density);
    }

    // ------------------------------------------------------------------ строки

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
         * Строка-переключатель. Нажимается вся строка (а не только ползунок),
         * а значение записывается ровно один раз на каждое действие.
         */
        static Row toggle(final Context context, String title, final String key, String hint, final Runnable onChanged) {
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
                view.setTextColor(KamiGramUi.secondaryText());
                view.setPadding(dp(12), dp(9), dp(12), dp(9));
                return view;
            }

            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));

            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            final TextView text = new TextView(context);
            text.setText(title);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(type == TYPE_ACTION ? KamiGramUi.accent() : KamiGramUi.primaryText());
            texts.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (hint != null) {
                final TextView hintView = new TextView(context);
                hintView.setText(hint);
                hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                hintView.setTextColor(KamiGramUi.secondaryText());
                hintView.setPadding(0, dp(2), 0, 0);
                texts.addView(hintView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            final LinearLayout.LayoutParams textsParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.rightMargin = dp(10);
            row.addView(texts, textsParams);

            if (type == TYPE_TOGGLE) {
                final KamiGramUi.Toggle toggle = new KamiGramUi.Toggle(context);
                toggle.setChecked(KamiGramConfig.value(key), false);
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
                    new LinearLayout.LayoutParams(dp(40), dp(24));
                row.addView(toggle, toggleParams);
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
