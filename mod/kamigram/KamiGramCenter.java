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
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProxyListActivity;

/**
 * KamiGram: настройки мода. Дизайн — по палитре и приёмам приложения Yoru
 * (yoru-android), перенесённым на Telegram:
 *
 *   * фон #0D0B12, карточки #1C1724 с тонкой обводкой #352A43 и радиусом 18;
 *   * заголовки разделов 19sp полужирные, подписи 11sp приглушённые (#A99BB8);
 *   * главное действие — градиентная кнопка (#E2CCFF → #C8A7FF, тёмный текст);
 *   * чипы-значения (радиус 11, обводка) вместо «дешёвых» текстовых полей;
 *   * ни одного лишнего пункта: ID, вход, само-проверки и прочая служебная
 *     информация пользователю НЕ показываются;
 *   * сверху — разделы (одно касание), снизу — закреплённая кнопка «Готово».
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
            final String[] tabs = {"Связь", "Приватность", "Вид", "Память"};

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(rounded(ThemeHook.YORU_CARD_HIGH, 24));
            root.setPadding(dp(16), dp(16), dp(16), dp(14));

            // ---- заголовок
            final LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            final LinearLayout titles = new LinearLayout(context);
            titles.setOrientation(LinearLayout.VERTICAL);
            final TextView title = new TextView(context);
            title.setText("KamiGram");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(ThemeHook.YORU_TEXT);
            titles.addView(title);
            final TextView subtitle = new TextView(context);
            subtitle.setText("настройки мода");
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            subtitle.setTextColor(ThemeHook.YORU_MUTED);
            subtitle.setPadding(0, dp(2), 0, 0);
            titles.addView(subtitle);
            header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView close = new TextView(context);
            close.setText("✕");
            close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            close.setTextColor(ThemeHook.YORU_MUTED);
            close.setGravity(Gravity.CENTER);
            close.setBackground(rounded(ThemeHook.YORU_SURFACE, 14));
            close.setOnClickListener(v -> dismissAll(context));
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
            done.setOnClickListener(v -> dismissAll(context));
            final LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            doneParams.topMargin = dp(6);
            root.addView(done, doneParams);

            final Section[] sections = {KamiGramCenter::fillConnection, KamiGramCenter::fillPrivacy,
                KamiGramCenter::fillLook, KamiGramCenter::fillMemory};

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

    private static void dismissAll(Context context) {
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
            Row.toggle(context, "KamiProxy", KamiGramConfig.KEY_BUILTIN_PROXY,
                "Подбирает самый быстрый прокси сам.", onChanged),
            Row.info(KamiGramBuiltinProxy.statusText()),
            Row.toggle(context, "Ускорение загрузок", KamiGramConfig.KEY_FAST_NET, null, onChanged),
            Row.toggle(context, "Прокси из буфера обмена", KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, null, onChanged),
            Row.action("Открыть список прокси", () -> openProxyScreen(context))
        });
        card(root, context, new Row[]{
            Row.toggle(context, "Только текст", KamiGramConfig.KEY_TEXT_ONLY,
                "Фото и видео — по нажатию.", onChanged),
            Row.toggle(context, "Не грузить истории", KamiGramConfig.KEY_NO_STORIES, null, onChanged),
            Row.toggle(context, "Не грузить GIF", KamiGramConfig.KEY_NO_GIFS, null, onChanged),
            Row.toggle(context, "Без превью ссылок", KamiGramConfig.KEY_NO_LINK_PREVIEW, null, onChanged),
            Row.toggle(context, "Без рекламы и рекомендаций", KamiGramConfig.KEY_NO_ADS, null, onChanged)
        });
        card(root, context, new Row[]{
            Row.toggle(context, "Скрывать рекламные посты", KamiGramConfig.KEY_ADS_FILTER, null, onChanged),
            Row.action("Показать скрытую рекламу", () -> KamiGramAds.showHiddenReport(context))
        });
    }

    // ------------------------------------------------------------------ ПРИВАТНОСТЬ

    private static void fillPrivacy(LinearLayout root, Context context, Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle(context, "Призрак", KamiGramConfig.KEY_GHOST,
                "Нет «в сети», «печатает» и прочтений.", onChanged),
            Row.toggle(context, "Призрак для историй", KamiGramConfig.KEY_STORIES_STEALTH, null, onChanged),
            Row.toggle(context, "Снять запреты защищённого контента", KamiGramConfig.KEY_NO_RESTRICTIONS, null, onChanged),
            Row.toggle(context, "Скрывать текст уведомлений", KamiGramConfig.KEY_HIDE_NOTIFICATION_TEXT, null, onChanged),
            Row.toggle(context, "Запретить скриншоты", KamiGramConfig.KEY_NO_SCREENSHOTS, null, onChanged)
        });
        card(root, context, new Row[]{
            Row.toggle(context, "Сохранять удалённые сообщения", KamiGramConfig.KEY_KEEP_DELETED, null, onChanged),
            Row.action("Журнал удалённых (" + KamiGramDeleted.size() + ")", () -> KamiGramDeleted.show(context)),
            Row.action("Очистить журнал", () -> {
                KamiGramDeleted.clear();
                KamiGramUi.notify(context, "Журнал очищен");
            })
        });
    }

    // ------------------------------------------------------------------ ВИД

    private static void fillLook(LinearLayout root, final Context context, final Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle(context, "Плавные анимации", KamiGramConfig.KEY_SMOOTH_ANIMATIONS, null, onChanged),
            Row.toggle(context, "Размытие интерфейса", KamiGramConfig.KEY_ALLOW_BLUR, null, onChanged)
        });
        accentPicker(root, context, onChanged);
        fontPicker(root, context, onChanged);
    }

    // ------------------------------------------------------------------ ПАМЯТЬ

    private static void fillMemory(LinearLayout root, final Context context, Runnable onChanged) {
        card(root, context, new Row[]{
            Row.toggle(context, "Скачанное вручную не удалять", KamiGramConfig.KEY_KEEP_DOWNLOADS, null, onChanged),
            Row.info(KamiGramCache.describe()),
            Row.action("Менеджер загрузок", () -> openDownloads(context))
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
                .show()),
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
        card(root, context, rows);
    }

    // ------------------------------------------------------------------ акцент

    private static void accentPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final int count = KamiGramConfig.accentCount();
        final Row[] rows = new Row[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            rows[i] = Row.action((KamiGramConfig.accentIndex() == i ? "Акцент · " : "") + KamiGramConfig.accentNameAt(i),
                () -> {
                    KamiGramConfig.setAccent(index);
                    ThemeHook.notifyAccentChanged();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    KamiGramUi.notify(context, "Акцент: " + KamiGramConfig.accentNameAt(index));
                });
        }
        card(root, context, rows);
    }

    // ------------------------------------------------------------------ размер текста и свой шрифт

    private static void fontPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final String[] names = {"Как в Telegram", "Крупнее на 1", "Крупнее на 2", "Крупнее на 3"};
        final Row[] rows = new Row[names.length + 3];
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            rows[i] = Row.action((KamiGramConfig.fontBoost() == i ? "Размер · " : "") + names[i],
                () -> {
                    KamiGramConfig.setInt(KamiGramConfig.KEY_FONT_BOOST, index);
                    KamiGramTweaks.applyFontSize();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                });
        }
        rows[names.length] = Row.info("Свой шрифт: " + KamiGramFont.describe());
        rows[names.length + 1] = Row.action("Выбрать шрифт (.ttf)", () -> KamiGramFont.pick(context));
        rows[names.length + 2] = Row.action("Сбросить шрифт", () -> {
            KamiGramFont.reset();
            KamiGramUi.notify(context, "Шрифт сброшен");
        });
        card(root, context, rows);
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
                view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                view.setTextColor(ThemeHook.YORU_MUTED);
                view.setPadding(dp(16), dp(10), dp(16), dp(10));
                return view;
            }

            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(13), dp(16), dp(13));

            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);

            final TextView text = new TextView(context);
            text.setText(title);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            text.setTypeface(AndroidUtilities.bold());
            text.setTextColor(type == TYPE_ACTION ? ThemeHook.YORU_PURPLE : ThemeHook.YORU_TEXT);
            texts.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            if (hint != null) {
                final TextView hintView = new TextView(context);
                hintView.setText(hint);
                hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
                hintView.setTextColor(ThemeHook.YORU_MUTED);
                hintView.setPadding(0, dp(3), 0, 0);
                texts.addView(hintView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }

            final LinearLayout.LayoutParams textsParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.rightMargin = dp(10);
            row.addView(texts, textsParams);
            press(row);

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
    }
}
