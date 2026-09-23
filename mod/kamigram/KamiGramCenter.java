package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.app.Dialog;
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
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProxyListActivity;

/**
 * KamiGram: центр настроек. Дизайн — палитра и приёмы Yoru (yoru-android),
 * перенесённые на Telegram:
 *
 *   * фон #0D0B12, карточки #1C1724 с обводкой #352A43 и радиусом 18;
 *   * разделы-чипы сверху, закреплённая кнопка «Готово» снизу;
 *   * НИ ОДНОЙ подсказки-пояснения: только названия и понятные переключатели;
 *   * выбор цвета убран — палитра одна (Yoru);
 *   * размер текста — полоска-слайдер (тянешь пальцем) + кнопка сброса;
 *   * никаких служебных пунктов (ID, вход, само-проверки).
 */
public final class KamiGramCenter {

    private KamiGramCenter() {
    }

    /** Открытый диалог центра — чтобы «Готово» закрывало именно его. */
    private static Dialog shownDialog;

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
            final String[] tabs = {"Связь", "Приватность", "Вид", "Память", "Другое"};

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(rounded(ThemeHook.YORU_CARD_HIGH, 24));
            root.setPadding(dp(16), dp(16), dp(16), dp(14));

            // ---- заголовок
            final LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            final TextView title = new TextView(context);
            title.setText("KamiGram");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(ThemeHook.YORU_TEXT);
            header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView close = new TextView(context);
            close.setText("✕");
            close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            close.setTextColor(ThemeHook.YORU_TEXT);
            close.setGravity(Gravity.CENTER);
            close.setBackground(rounded(ThemeHook.YORU_SURFACE, 14));
            close.setOnClickListener(v -> dismissAll());
            header.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
            root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // ---- разделы (чипы; прокручиваются, если не помещаются целиком)
            final android.widget.HorizontalScrollView tabScroll = new android.widget.HorizontalScrollView(context);
            tabScroll.setHorizontalScrollBarEnabled(false);
            tabScroll.setFillViewport(false);
            final LinearLayout tabStrip = new LinearLayout(context);
            tabStrip.setOrientation(LinearLayout.HORIZONTAL);
            tabScroll.addView(tabStrip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stripParams.topMargin = dp(14);
            root.addView(tabScroll, stripParams);

            // ---- содержимое раздела
            final ScrollView scroll = new ScrollView(context);
            scroll.setClipToPadding(false);
            final LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(240), (int) (AndroidUtilities.displaySize.y * 0.56f)));
            scrollParams.topMargin = dp(12);
            root.addView(scroll, scrollParams);

            // ---- главное действие
            final TextView done = new TextView(context);
            done.setText("Готово");
            done.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            done.setTypeface(AndroidUtilities.bold());
            done.setTextColor(0xFF21152F);
            done.setGravity(Gravity.CENTER);
            done.setBackground(gradient(ThemeHook.YORU_PURPLE_SOFT, ThemeHook.YORU_PURPLE, 16));
            press(done);
            done.setOnClickListener(v -> dismissAll());
            final LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            doneParams.topMargin = dp(6);
            root.addView(done, doneParams);

            final Section[] sections = {KamiGramCenter::fillConnection, KamiGramCenter::fillPrivacy,
                KamiGramCenter::fillLook, KamiGramCenter::fillMemory, KamiGramCenter::fillOther};

            final int[] current = {0};
            final TextView[] tabViews = new TextView[tabs.length];
            for (int i = 0; i < tabs.length; i++) {
                final int index = i;
                final TextView tab = new TextView(context);
                tab.setText(tabs[i]);
                tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                tab.setGravity(Gravity.CENTER);
                tab.setSingleLine(true);
                tab.setPadding(dp(14), dp(10), dp(14), dp(10));
                tab.setOnClickListener(v -> {
                    if (current[0] != index) {
                        current[0] = index;
                        for (int t = 0; t < tabViews.length; t++) {
                            styleTab(tabViews[t], t == index);
                        }
                        content.removeAllViews();
                        try {
                            sections[index].fill(content, context, onChanged);
                        } catch (Throwable throwable) {
                            FileLog.e(throwable);
                        }
                        scroll.scrollTo(0, 0);
                    }
                });
                styleTab(tab, i == 0);
                tabViews[i] = tab;
                final LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tabParams.setMargins(dp(3), 0, dp(3), 0);
                tabStrip.addView(tab, tabParams);
            }

            sections[0].fill(content, context, onChanged);

            final Dialog dialog = KamiGramDialog.create(context).title(null).content(root).show();
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

    private static void dismissAll() {
        final Dialog dialog = shownDialog;
        shownDialog = null;
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------------------ СВЯЗЬ

    private static void fillConnection(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle("KamiProxy", KamiGramConfig.KEY_BUILTIN_PROXY, onChanged),
            Row.toggle("Ускорение загрузок", KamiGramConfig.KEY_FAST_NET, onChanged),
            Row.toggle("Прокси из буфера обмена", KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, onChanged),
            Row.action("Открыть список прокси", () -> openProxyScreen(context))
        });
        card(root, context, new Row[]{
            Row.toggle("Не грузить истории", KamiGramConfig.KEY_NO_STORIES, onChanged),
            Row.toggle("Без рекламы и рекомендаций", KamiGramConfig.KEY_NO_ADS, onChanged),
            Row.toggle("Скрывать рекламные посты", KamiGramConfig.KEY_ADS_FILTER, onChanged),
            Row.action("Показать скрытую рекламу", () -> KamiGramAds.showHiddenReport(context))
        });
        card(root, context, new Row[]{
            Row.toggle("Стикеры", KamiGramConfig.KEY_NO_STICKERS, onChanged),
            Row.toggle("Премиум-эмодзи", KamiGramConfig.KEY_NO_ANIMATED_EMOJI, onChanged),
            Row.toggle("Скрывать Premium и подарки", KamiGramConfig.KEY_NO_PREMIUM_UI, onChanged),
            Row.toggle("GIF и анимации", KamiGramConfig.KEY_NO_GIFS, onChanged),
            Row.toggle("Превью ссылок", KamiGramConfig.KEY_NO_LINK_PREVIEW, onChanged),
            Row.toggle("Поиск GIF и стикеров", KamiGramConfig.KEY_NO_GIF_SEARCH, onChanged),
            Row.toggle("Часто используемые контакты", KamiGramConfig.KEY_NO_TOP_PEERS, onChanged)
        });
    }

    // ------------------------------------------------------------------ ПРИВАТНОСТЬ

    private static void fillPrivacy(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle("Призрак", KamiGramConfig.KEY_GHOST, onChanged),
            // KAMIGRAM_INSTANT_SEND_R80: legacy auto-schedule is intentionally not exposed.
            Row.toggle("Авто-архив (100+ непрочитанных)", KamiGramConfig.KEY_AUTO_ARCHIVE, onChanged),
            Row.toggle("Призрак для историй", KamiGramConfig.KEY_STORIES_STEALTH, onChanged),
            // KAMIGRAM_NATIVE_DELETE_R80: legacy keep-deleted is intentionally not exposed.
            Row.toggle("Одноразовые без пометки", KamiGramConfig.KEY_VIEW_ONCE, onChanged),
            Row.toggle("Снять запреты защищённого контента", KamiGramConfig.KEY_NO_RESTRICTIONS, onChanged)
        });
        card(root, context, new Row[]{
            Row.toggle("Скрывать текст уведомлений", KamiGramConfig.KEY_HIDE_NOTIFICATION_TEXT, onChanged),
            Row.toggle("Запретить скриншоты", KamiGramConfig.KEY_NO_SCREENSHOTS, onChanged)
        });
    }

    // ------------------------------------------------------------------ ВИД

    private static void fillLook(LinearLayout root, final Context context, final Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle("Плавные анимации", KamiGramConfig.KEY_SMOOTH_ANIMATIONS, onChanged),
            Row.toggle("Размытие интерфейса", KamiGramConfig.KEY_ALLOW_BLUR, onChanged)
        });
        textSizeCard(root, context, onChanged);
        fontCard(root, context, onChanged);
    }

    // ------------------------------------------------------------------ ПАМЯТЬ

    private static void fillMemory(LinearLayout root, final Context context, Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle("Скачанное вручную не удалять", KamiGramConfig.KEY_KEEP_DOWNLOADS, onChanged),
            Row.action("Загрузки", () -> openDownloads(context)),
            Row.action("Всего занято · " + KamiGramCache.human(KamiGramCache.total()), () -> {
                KamiGramCache.clearAll();
                KamiGramUi.notify(context, "Кэш очищен");
            }),
            Row.action("Освободить память", () -> {
                KamiGramCache.freeMemory();
                KamiGramUi.notify(context, "Память освобождена");
            })
        });
        cacheCategories(root, context);
        card(root, context, new Row[]{
            Row.action("Очистить весь кэш", () -> KamiGramDialog.create(context)
                .title("Очистить кэш?")
                .message("Скачанное вручную не удаляется.")
                .positive("Очистить", () -> {
                    KamiGramCache.clearAll();
                    KamiGramUi.notify(context, "Кэш очищен");
                })
                .negative("Отмена", null)
                .show())
        });
    }

    // ------------------------------------------------------------------ ДРУГОЕ (r70)

    private static void fillOther(LinearLayout root, final Context context, final Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle("Отправлять всегда HD", KamiGramConfig.KEY_SEND_HD, onChanged),
            Row.toggle("Пересылать без имени", KamiGramConfig.KEY_FORWARD_NO_NAME, onChanged),
            Row.toggle("Пересылать сгорающие", KamiGramConfig.KEY_FORWARD_EPHEMERAL, onChanged)
        });
        card(root, context, new Row[]{
            Row.toggle("Фокус скорости на нажатом файле", KamiGramConfig.KEY_NET_FOCUS, onChanged)
        });
        card(root, context, new Row[]{
            Row.toggle("Применять KamiGram ко всем аккаунтам", KamiGramConfig.KEY_APPLY_ALL, onChanged)
        });
    }

    // ------------------------------------------------------------------ кэш по категориям

    private static void cacheCategories(LinearLayout root, final Context context) {
        final Row[] rows = new Row[KamiGramCache.TYPE_COUNT];
        for (int i = 0; i < KamiGramCache.TYPE_COUNT; i++) {
            final int type = i;
            rows[i] = Row.action(KamiGramCache.nameOf(type) + " · " + KamiGramCache.human(KamiGramCache.sizeOf(type)),
                () -> {
                    KamiGramCache.clear(type);
                    KamiGramUi.notify(context, KamiGramCache.nameOf(type) + " очищено");
                });
        }
        card(root, context, rows);
    }

    // ------------------------------------------------------------------ размер текста (полоска)

    private static void textSizeCard(final LinearLayout root, final Context context, final Runnable onChanged) {
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(stroke(ThemeHook.YORU_CARD, 18));
        container.setPadding(dp(16), dp(14), dp(16), dp(12));

        final LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        final TextView title = new TextView(context);
        title.setText("Размер текста");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(ThemeHook.YORU_TEXT);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        value.setTextColor(ThemeHook.YORU_PURPLE);
        value.setBackground(stroke(ThemeHook.YORU_SURFACE, 11));
        value.setPadding(dp(10), dp(4), dp(10), dp(4));
        value.setText(KamiGramConfig.fontSize() + "sp");
        head.addView(value);
        container.addView(head, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final SeekBar slider = new SeekBar(context);
        slider.setMax(KamiGramConfig.FONT_SIZE_MAX - KamiGramConfig.FONT_SIZE_MIN);
        slider.setProgress(KamiGramConfig.fontBoost());
        tintSlider(slider);
        final LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderParams.topMargin = dp(6);
        container.addView(slider, sliderParams);

        final TextView reset = new TextView(context);
        reset.setText("Сбросить размер");
        reset.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        reset.setTextColor(ThemeHook.YORU_MUTED);
        reset.setPadding(0, dp(6), 0, 0);
        press(reset);
        reset.setOnClickListener(v -> {
            slider.setProgress(KamiGramConfig.FONT_SIZE_DEFAULT - KamiGramConfig.FONT_SIZE_MIN);
            KamiGramConfig.setFontBoost(KamiGramConfig.FONT_SIZE_DEFAULT - KamiGramConfig.FONT_SIZE_MIN);
            value.setText(KamiGramConfig.FONT_SIZE_DEFAULT + "sp");
            KamiGramTweaks.applyFontSize();
            if (onChanged != null) {
                onChanged.run();
            }
        });
        container.addView(reset);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText((KamiGramConfig.FONT_SIZE_MIN + progress) + "sp");
                if (fromUser) {
                    KamiGramConfig.setFontBoost(progress);
                    KamiGramTweaks.applyFontSize();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        });

        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        root.addView(container, params);
    }

    // ------------------------------------------------------------------ свой шрифт

    private static void fontCard(LinearLayout root, final Context context, final Runnable onChanged) {
        card(root, context, new Row[]{
            Row.action("Шрифт · " + KamiGramFont.describe(), () -> KamiGramFont.pick(context)),
            Row.action("Выбрать шрифт (.ttf)", () -> KamiGramFont.pick(context)),
            Row.action("Сбросить шрифт", () -> {
                KamiGramFont.reset();
                KamiGramUi.notify(context, "Шрифт сброшен");
                if (onChanged != null) {
                    onChanged.run();
                }
            })
        });
    }

    // ------------------------------------------------------------------ экраны

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

    /**
     * «Загрузки» — это родной менеджер загрузок Telegram (там видно, что качается
     * и что уже скачано), а не экран очистки кэша.
     */
    public static void openDownloads(Context context) {
        try {
            final Activity activity = AndroidUtilities.findActivity(context);
            if (!(activity instanceof LaunchActivity)) {
                return;
            }
            final org.telegram.ui.ActionBar.INavigationLayout layout =
                ((LaunchActivity) activity).actionBarLayout;
            if (layout != null) {
                final java.util.List<org.telegram.ui.ActionBar.BaseFragment> stack = layout.getFragmentStack();
                for (int a = stack.size() - 1; a >= 0; a--) {
                    final org.telegram.ui.ActionBar.BaseFragment fragment = stack.get(a);
                    if (fragment instanceof org.telegram.ui.DialogsActivity) {
                        dismissAll();
                        final org.telegram.ui.DialogsActivity dialogs = (org.telegram.ui.DialogsActivity) fragment;
                        AndroidUtilities.runOnUIThread(dialogs::kamigramShowDownloads, 160);
                        return;
                    }
                }
            }
            dismissAll();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ вид карточек/строк

    private static void styleTab(TextView tab, boolean active) {
        tab.setTypeface(active ? AndroidUtilities.bold() : Typeface.DEFAULT);
        tab.setTextColor(active ? 0xFF21152F : ThemeHook.YORU_MUTED);
        tab.setBackground(active
            ? gradient(ThemeHook.YORU_PURPLE_SOFT, ThemeHook.YORU_PURPLE, 13)
            : stroke(ThemeHook.YORU_SURFACE, 13));
    }

    private static void press(View view) {
        view.setClickable(true);
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.9f).setDuration(60).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(80).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void tintSlider(SeekBar slider) {
        try {
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(ThemeHook.YORU_PURPLE));
            slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeHook.YORU_LINE));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(ThemeHook.YORU_PURPLE));
            try {
                slider.setSplitTrack(false);
            } catch (Throwable ignore) {
            }
        } catch (Throwable ignore) {
        }
    }

    private static GradientDrawable rounded(int color, int radius) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private static GradientDrawable stroke(int color, int radius) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(Math.max(1, dp(1)), ThemeHook.YORU_LINE);
        return drawable;
    }

    private static GradientDrawable gradient(int start, int end, int radius) {
        final GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private static void card(LinearLayout root, Context context, Row[] rows) {
        if (rows == null || rows.length == 0) {
            return;
        }
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(stroke(ThemeHook.YORU_CARD, 18));
        container.setClipToOutline(true);
        for (int i = 0; i < rows.length; i++) {
            container.addView(rows[i].build(context), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i != rows.length - 1) {
                final View divider = new View(context);
                divider.setBackgroundColor(ThemeHook.YORU_LINE);
                final LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                dividerParams.leftMargin = dp(14);
                dividerParams.rightMargin = dp(14);
                container.addView(divider, dividerParams);
            }
        }
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        root.addView(container, params);
    }

    private static int dp(float value) {
        return (int) (value * AndroidUtilities.density);
    }

    // ------------------------------------------------------------------ строки карточек

    private static final class Row {

        private static final int TYPE_TOGGLE = 0;
        private static final int TYPE_ACTION = 1;

        private final int type;
        private final String title;
        private final String key;
        private final Runnable click;
        private final Runnable onChanged;

        private Row(int type, String title, String key, Runnable click, Runnable onChanged) {
            this.type = type;
            this.title = title;
            this.key = key;
            this.click = click;
            this.onChanged = onChanged;
        }

        static Row toggle(String title, final String key, final Runnable onChanged) {
            return new Row(TYPE_TOGGLE, title, key, null, onChanged);
        }

        static Row action(String title, Runnable click) {
            return new Row(TYPE_ACTION, title, null, click, null);
        }

        View build(final Context context) {
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(13), dp(16), dp(13));

            final TextView text = new TextView(context);
            text.setText(title);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text.setTypeface(AndroidUtilities.bold());
            text.setTextColor(type == TYPE_ACTION ? ThemeHook.YORU_PURPLE : ThemeHook.YORU_TEXT);
            row.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            press(row);

            if (type == TYPE_TOGGLE) {
                // «Стикеры», «Премиум-эмодзи» и т.п. хранят ЗАПРЕТ, а переключатель
                // показывает «включено» — так пользователю понятнее.
                final boolean invert = invertible(key);
                final KamiGramUi.Toggle toggle = new KamiGramUi.Toggle(context);
                toggle.setChecked(invert ? !KamiGramConfig.value(key) : KamiGramConfig.value(key), false);
                final Runnable apply = () -> {
                    final boolean shown = toggle.isChecked();
                    KamiGramConfig.set(key, invert ? !shown : shown);
                    KamiGramGhost.refreshAll();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                };
                toggle.setOnToggleListener(checked -> {
                    toggle.setChecked(checked, false);
                    apply.run();
                });
                row.addView(toggle, new LinearLayout.LayoutParams(dp(42), dp(25)));
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

        private static boolean invertible(String key) {
            return KamiGramConfig.KEY_NO_STICKERS.equals(key)
                || KamiGramConfig.KEY_NO_ANIMATED_EMOJI.equals(key)
                || KamiGramConfig.KEY_NO_PREMIUM_UI.equals(key)
                || KamiGramConfig.KEY_NO_GIFS.equals(key)
                || KamiGramConfig.KEY_NO_LINK_PREVIEW.equals(key)
                || KamiGramConfig.KEY_NO_GIF_SEARCH.equals(key)
                || KamiGramConfig.KEY_NO_TOP_PEERS.equals(key)
                || KamiGramConfig.KEY_NO_STORIES.equals(key)
                || KamiGramConfig.KEY_NO_ADS.equals(key)
                || KamiGramConfig.KEY_ADS_FILTER.equals(key);
        }
    }
}
