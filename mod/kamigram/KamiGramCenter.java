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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Components.Switch;

/**
 * KamiGram: центр настроек мода — «Material 3 / iOS 2026».
 *
 * Почему отдельный центр, а не строки в настройках Telegram: так видно весь мод
 * сразу и всё работает (в прошлой сборке переключатели были, а часть из них
 * ничего не делала — например «Показывать ID» и очистка кэша).
 *
 * Экран сделан карточками, как в iOS: заголовок группы, карточка с ровными
 * строками, серые пояснения, акцентные действия, переключатели в стиле iOS.
 * Цвета берутся из темы (ThemeHook), поэтому экран читается и в тёмной, и в
 * светлой теме — чёрного текста на чёрном здесь быть не может.
 */
public final class KamiGramCenter {

    private static final int SECTION_PROXY = 0;
    private static final int SECTION_TRAFFIC = 1;
    private static final int SECTION_CACHE = 2;
    private static final int SECTION_IDS = 3;
    private static final int SECTION_PRIVACY = 4;
    private static final int SECTION_DESIGN = 5;
    private static final int SECTION_LOGIN = 6;
    private static final int SECTION_ABOUT = 7;

    private KamiGramCenter() {
    }

    // ------------------------------------------------------------------ экран

    public static void show(final Context context) {
        show(context, null);
    }

    public static void show(final Context context, final Runnable onChanged) {
        if (context == null) {
            return;
        }
        try {
            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(18));

            section(root, context, "ПРОКСИ И СЕТЬ");
            card(root, context, new Row[]{
                Row.switchRow("Мощный прокси: моментальное переключение на живой", KamiGramConfig.KEY_SMART_PROXY, onChanged),
                Row.switchRow("Нерабочий прокси выключается сам", KamiGramConfig.KEY_PROXY_FALLBACK, onChanged),
                Row.switchRow("Прокси из буфера включается сам", KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, onChanged),
                Row.switchRow("Ускорение загрузок и потоков (слабый интернет)", KamiGramConfig.KEY_FAST_NET, onChanged),
                Row.action("Прокси и список живых (ссылка, проверка)", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramProxyButton.showPanel(context);
                    }
                }),
                Row.action("Вставить ссылку на прокси", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramProxyButton.showLinkDialog(context, onChanged);
                    }
                }),
                Row.action("Открыть прокси-экран Telegram", new Runnable() {
                    @Override
                    public void run() {
                        openProxyScreen(context);
                    }
                }),
                Row.action("Проверить сейчас и переключиться на лучший", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramProxyPower.pingAll();
                        KamiGramProxyPower.refreshNow(context);
                        toast(context, KamiGramProxyPower.statusText());
                    }
                }),
                Row.info(KamiGramProxyPower.statusText()),
                Row.info("Сеть: " + KamiGramSpeed.describe() + " · " + KamiGramSpeed.networkName())
            });

            section(root, context, "ТРАФИК И ЭКОНОМИЯ");
            card(root, context, new Row[]{
                Row.switchRow("Не грузить стикеры и наборы (0 байт)", KamiGramConfig.KEY_NO_STICKERS, onChanged),
                Row.switchRow("Не грузить GIF и анимации (0 байт)", KamiGramConfig.KEY_NO_GIFS, onChanged),
                Row.switchRow("Не грузить истории и их медиа (0 байт)", KamiGramConfig.KEY_NO_STORIES, onChanged),
                Row.switchRow("Премиум-эмодзи показывать обычным эмодзи", KamiGramConfig.KEY_NO_ANIMATED_EMOJI, onChanged),
                Row.switchRow("Не грузить превью ссылок и страницы", KamiGramConfig.KEY_NO_LINK_PREVIEW, onChanged),
                Row.switchRow("Не искать GIF и стикеры при вводе", KamiGramConfig.KEY_NO_GIF_SEARCH, onChanged),
                Row.switchRow("Не грузить «часто используемые»", KamiGramConfig.KEY_NO_TOP_PEERS, onChanged),
                Row.switchRow("Убрать рекламу и рекомендации", KamiGramConfig.KEY_NO_ADS, onChanged),
                Row.switchRow("Убрать Premium / Stars / TON", KamiGramConfig.KEY_NO_PREMIUM_UI, onChanged),
                Row.info("Автоскачивание медиа выключено, авто-проигрывание выключено, "
                    + "экономия трафика в звонках включена — это уже в сборке."),
                Row.info("Трафик: " + KamiGramTraffic.describe()),
                Row.info(KamiGramTraffic.economyText()),
                Row.action("Обнулить счётчик экономии", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramTraffic.reset();
                        toast(context, "Счётчик обнулён");
                    }
                })
            });

            section(root, context, "КЭШ И ФАЙЛЫ");
            card(root, context, new Row[]{
                Row.switchRow("Скачанное вручную не удалять", KamiGramConfig.KEY_KEEP_DOWNLOADS, onChanged),
                Row.info(KamiGramCache.modeText()),
                Row.info(KamiGramCache.describe()),
                Row.action("Очистить ВЕСЬ кэш сейчас", new Runnable() {
                    @Override
                    public void run() {
                        confirm(context, "Очистить весь кэш?", "Скачанное вручную останется, если включена защита.", new Runnable() {
                            @Override
                            public void run() {
                                KamiGramCache.clearAll();
                                toast(context, "Кэш очищен. " + KamiGramCache.describe());
                            }
                        });
                    }
                }),
                Row.action("Освободить память (без удаления файлов)", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramCache.freeMemory();
                        toast(context, "Память освобождена");
                    }
                })
            });
            categoryRows(root, context);
            card(root, context, new Row[]{
                Row.action("Забыть защищённые файлы (" + KamiGramCache.protectedCount() + ")", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramCache.forget();
                        toast(context, "Список защиты очищен");
                    }
                }),
                Row.action("Открыть менеджер загрузок Telegram", new Runnable() {
                    @Override
                    public void run() {
                        openDownloads(context);
                    }
                }),
                Row.action("Отменить все активные загрузки", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            FileLoader.getInstance(UserConfig.selectedAccount).cancelLoadAllFiles();
                            toast(context, "Все загрузки остановлены");
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }
                })
            });

            section(root, context, "ID И ССЫЛКИ (как в MdGram / Nekogram)");
            card(root, context, new Row[]{
                Row.switchRow("Показывать ID в шапке чата и в меню", KamiGramConfig.KEY_SHOW_IDS, onChanged),
                Row.info("Мой ID: " + myId()),
                Row.action("Копировать мой ID", new Runnable() {
                    @Override
                    public void run() {
                        copy(context, "KamiGram ID", myId());
                    }
                }),
                Row.action("Копировать мой @username", new Runnable() {
                    @Override
                    public void run() {
                        copy(context, "KamiGram username", "@" + myUsername());
                    }
                }),
                Row.action("Копировать ссылку на меня", new Runnable() {
                    @Override
                    public void run() {
                        copy(context, "KamiGram link", "https://t.me/" + myUsername());
                    }
                }),
                Row.info("ID чата видно в шапке чата и в меню «три точки» — нажатие копирует")
            });

            section(root, context, "ПРИВАТНОСТЬ");
            card(root, context, new Row[]{
                Row.switchRow("Призрак: нет чтения, «печатает», «в сети»", KamiGramConfig.KEY_GHOST, onChanged),
                Row.switchRow("Призрак для историй (просмотры не пишутся)", KamiGramConfig.KEY_STORIES_STEALTH, onChanged),
                Row.switchRow("Снять запреты защищённого контента", KamiGramConfig.KEY_NO_RESTRICTIONS, onChanged),
                Row.switchRow("Скрывать текст уведомлений", KamiGramConfig.KEY_HIDE_NOTIFICATION_TEXT, onChanged),
                Row.switchRow("Запретить скриншоты во всём приложении", KamiGramConfig.KEY_NO_SCREENSHOTS, onChanged),
                Row.switchRow("Не спрашивать разрешения (контакты, телефон)", KamiGramConfig.KEY_NO_PERMISSION_NAGS, onChanged)
            });

            section(root, context, "ДИЗАЙН И ТЕМА (iOS 2026)");
            card(root, context, new Row[]{
                Row.switchRow("iOS-дизайн: графит, плоские панели, без «стекла»", KamiGramConfig.KEY_IOS_DESIGN, onChanged),
                Row.switchRow("Скругления облаков и меню как в iOS", KamiGramConfig.KEY_IOS_BUBBLES, onChanged),
                Row.switchRow("Material 3 (2026): карточки и мягкие формы", KamiGramConfig.KEY_MATERIAL3, onChanged),
                Row.switchRow("Компактный список чатов", KamiGramConfig.KEY_COMPACT_CHATS, onChanged),
                Row.switchRow("Отправлять по Enter", KamiGramConfig.KEY_ENTER_TO_SEND, onChanged),
                Row.switchRow("Тихая отправка (без звука)", KamiGramConfig.KEY_SILENT_SEND, onChanged),
                Row.action("Применить настройки Telegram сейчас", new Runnable() {
                    @Override
                    public void run() {
                        KamiGramTweaks.applyAndRefresh();
                        toast(context, "Применено: размер текста, Enter, уведомления");
                    }
                })
            });
            accentPicker(root, context, onChanged);
            backgroundPicker(root, context);
            fontPicker(root, context, onChanged);
            card(root, context, new Row[]{
                Row.action("Применить тему и акценты заново", new Runnable() {
                    @Override
                    public void run() {
                        ThemeHook.keepDarkTheme();
                        ThemeHook.notifyAccentChanged();
                        toast(context, "Тема: " + ThemeHook.designVersion());
                    }
                }),
                Row.info("Тема: " + KamiGramCache.themeText() + " · акцент: " + KamiGramConfig.accentName())
            });

            section(root, context, "ВХОД В АККАУНТ");
            card(root, context, new Row[]{
                Row.switchRow("Всегда простой SMS-код (без Google-проверки)", KamiGramConfig.KEY_FORCE_SMS, onChanged),
                Row.switchRow("Быстрый вход без лишних вопросов", KamiGramConfig.KEY_FAST_LOGIN, onChanged),
                Row.info("Сборка с официальными app id/hash Telegram, прокси при входе не включается.")
            });

            section(root, context, "О МОДЕ");
            card(root, context, new Row[]{
                Row.info("KamiGram " + versionName() + " · " + ThemeHook.designVersion()),
                Row.info("Экономия: " + (KamiGramSpeed.enabled() ? "включена" : "базовая")
                    + " · прокси: " + KamiGramProxyPower.proxyCount() + " шт."),
                Row.action("Сбросить настройки мода к заводским", new Runnable() {
                    @Override
                    public void run() {
                        confirm(context, "Сбросить настройки мода?", "Telegram и твои чаты не пострадают.", new Runnable() {
                            @Override
                            public void run() {
                                resetAll();
                                ThemeHook.notifyAccentChanged();
                                toast(context, "Настройки сброшены");
                            }
                        });
                    }
                }),
                Row.action("Закрыть", new Runnable() {
                    @Override
                    public void run() {
                    }
                })
            });

            final ScrollView scroll = new ScrollView(context);
            scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("KamiGram: центр настроек")
                .setView(scroll)
                .setPositiveButton("Готово", null)
                .create();
            dialog.show();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ группы кэша

    /** Строка на каждую категорию кэша: размер + «Очистить». */
    private static void categoryRows(LinearLayout root, final Context context) {
        final Row[] rows = new Row[KamiGramCache.TYPE_COUNT + 1];
        for (int i = 0; i < KamiGramCache.TYPE_COUNT; i++) {
            final int type = i;
            rows[i] = Row.action(KamiGramCache.nameOf(type) + " — " + KamiGramCache.human(KamiGramCache.sizeOf(type))
                + " · очистить", new Runnable() {
                @Override
                public void run() {
                    KamiGramCache.clear(type);
                    toast(context, KamiGramCache.nameOf(type) + ": " + KamiGramCache.human(KamiGramCache.sizeOf(type)));
                }
            });
        }
        rows[KamiGramCache.TYPE_COUNT] = Row.info("Всего занято: " + KamiGramCache.human(KamiGramCache.total()));
        card(root, context, rows);
    }

    // ------------------------------------------------------------------ акценты

    private static void accentPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        row.setBackground(cardBackground(context));

        final TextView title = new TextView(context);
        title.setText("Акцентный цвет");
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
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    KamiGramConfig.setAccent(index);
                    ThemeHook.notifyAccentChanged();
                    toast(context, "Акцент: " + KamiGramConfig.accentName());
                    if (onChanged != null) {
                        onChanged.run();
                    }
                }
            });
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(22), AndroidUtilities.dp(22));
            params.leftMargin = AndroidUtilities.dp(6);
            row.addView(dot, params);
        }
        root.addView(row, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spacing(root, 8);
    }

    private static void backgroundPicker(LinearLayout root, final Context context) {
        final Row[] rows = new Row[3];
        final String[] names = {"Чёрный (AMOLED, как iOS)", "Графит #1C1C1E", "Узор Telegram"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            rows[i] = Row.action((KamiGramConfig.chatBackgroundIndex() == i ? "● " : "○ ") + names[i],
                new Runnable() {
                    @Override
                    public void run() {
                        KamiGramConfig.setChatBackground(index);
                        applyBackground(index);
                        toast(context, "Фон чата: " + KamiGramConfig.chatBackgroundName());
                    }
                });
        }
        card(root, context, rows);
    }

    private static void fontPicker(LinearLayout root, final Context context, final Runnable onChanged) {
        final Row[] rows = new Row[5];
        for (int i = 0; i < 5; i++) {
            final int boost = i;
            rows[i] = Row.action((KamiGramConfig.fontBoost() == i ? "● " : "○ ")
                + (i == 0 ? "Размер текста как в Telegram (16)" : "Текст крупнее: 16 + " + i),
                new Runnable() {
                    @Override
                    public void run() {
                        KamiGramConfig.setInt(KamiGramConfig.KEY_FONT_BOOST, boost);
                        SharedConfig.fontSize = 16 + boost;
                        toast(context, "Размер текста: " + (16 + boost));
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    }
                });
        }
        card(root, context, rows);
    }

    /** Меняет фон чата (это осознанный выбор пользователя, не «насильный» цвет). */
    public static void applyBackground(int index) {
        try {
            if (index == 1) {
                Theme.setColor(Theme.key_chat_wallpaper, 0xFF1C1C1E, false);
            } else if (index == 0) {
                Theme.setColor(Theme.key_chat_wallpaper, 0xFF000000, false);
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    // ------------------------------------------------------------------ переходы

    /** Родной экран прокси Telegram (дизайн и функционал как в самом ТГ). */
    public static void openProxyScreen(Context context) {
        openFragment(context, "org.telegram.ui.ProxyListActivity");
    }

    /** Родной менеджер загрузок Telegram. */
    public static void openDownloads(Context context) {
        try {
            if (context instanceof org.telegram.ui.LaunchActivity) {
                ((org.telegram.ui.LaunchActivity) context).presentFragment(new CacheControlActivity());
                return;
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
        openFragment(context, "org.telegram.ui.CacheControlActivity");
    }

    private static void openFragment(Context context, String className) {
        try {
            if (!(context instanceof org.telegram.ui.LaunchActivity)) {
                return;
            }
            final Class<?> clazz = Class.forName(className);
            final Object fragment = clazz.newInstance();
            if (fragment instanceof org.telegram.ui.ActionBar.BaseFragment) {
                ((org.telegram.ui.LaunchActivity) context)
                    .presentFragment((org.telegram.ui.ActionBar.BaseFragment) fragment);
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
            toast(context, "Не удалось открыть экран Telegram");
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
            return UserObject.getPublicUsername(user) != null ? UserObject.getPublicUsername(user) : "me";
        } catch (Throwable ignore) {
            return "me";
        }
    }

    private static void copy(Context context, String label, String value) {
        try {
            final ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText(label, value));
                toast(context, "Скопировано: " + value);
            }
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void confirm(final Context context, String title, String message, final Runnable onYes) {
        try {
            final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Да", null)
                .setNegativeButton("Отмена", null)
                .create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    onYes.run();
                }));
            dialog.show();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
    }

    private static void toast(Context context, String text) {
        try {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }

    private static void resetAll() {
        final String[] keys = {
            KamiGramConfig.KEY_GHOST, KamiGramConfig.KEY_STORIES_STEALTH, KamiGramConfig.KEY_NO_RESTRICTIONS,
            KamiGramConfig.KEY_SHOW_IDS, KamiGramConfig.KEY_NO_PERMISSION_NAGS, KamiGramConfig.KEY_NO_SCREENSHOTS,
            KamiGramConfig.KEY_HIDE_NOTIFICATION_TEXT, KamiGramConfig.KEY_SMART_PROXY, KamiGramConfig.KEY_PROXY_FALLBACK,
            KamiGramConfig.KEY_AUTO_PROXY_CLIPBOARD, KamiGramConfig.KEY_FAST_NET, KamiGramConfig.KEY_NO_STICKERS,
            KamiGramConfig.KEY_NO_STORIES, KamiGramConfig.KEY_NO_ANIMATED_EMOJI, KamiGramConfig.KEY_NO_GIFS,
            KamiGramConfig.KEY_NO_LINK_PREVIEW, KamiGramConfig.KEY_NO_GIF_SEARCH, KamiGramConfig.KEY_NO_TOP_PEERS,
            KamiGramConfig.KEY_NO_ADS, KamiGramConfig.KEY_NO_PREMIUM_UI, KamiGramConfig.KEY_KEEP_DOWNLOADS,
            KamiGramConfig.KEY_IOS_DESIGN, KamiGramConfig.KEY_IOS_BUBBLES, KamiGramConfig.KEY_MATERIAL3,
            KamiGramConfig.KEY_COMPACT_CHATS, KamiGramConfig.KEY_ENTER_TO_SEND, KamiGramConfig.KEY_SILENT_SEND,
            KamiGramConfig.KEY_FORCE_SMS, KamiGramConfig.KEY_FAST_LOGIN
        };
        for (String key : keys) {
            KamiGramConfig.set(key, KamiGramConfig.defaultValue(key));
        }
        KamiGramConfig.setAccent(0);
        KamiGramConfig.setInt(KamiGramConfig.KEY_FONT_BOOST, 0);
        KamiGramConfig.setInt(KamiGramConfig.KEY_CHAT_BACKGROUND, 0);
    }

    // ------------------------------------------------------------------ вид

    /** Карточка с заголовком-пояснением и строками. */
    private static void card(LinearLayout root, Context context, Row[] rows) {
        if (rows == null || rows.length == 0) {
            return;
        }
        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(cardBackground(context));
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

    private static GradientDrawable cardBackground(Context context) {
        final GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(ThemeHook.surface());
        shape.setCornerRadius(AndroidUtilities.dp(KamiGramConfig.material3() ? 18 : 12));
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

        private static final int TYPE_SWITCH = 0;
        private static final int TYPE_ACTION = 1;
        private static final int TYPE_INFO = 2;

        private final int type;
        private final String title;
        private final String key;
        private final Runnable click;
        private final Runnable refresh;

        private Row(int type, String title, String key, Runnable click, Runnable refresh) {
            this.type = type;
            this.title = title;
            this.key = key;
            this.click = click;
            this.refresh = refresh;
        }

        static Row switchRow(String title, String key, Runnable refresh) {
            return new Row(TYPE_SWITCH, title, key, null, refresh);
        }

        static Row action(String title, Runnable click) {
            return new Row(TYPE_ACTION, title, null, click, null);
        }

        static Row info(String title) {
            return new Row(TYPE_INFO, title, null, null, null);
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

            final TextView text = new TextView(context);
            text.setText(title);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(type == TYPE_ACTION ? ThemeHook.accent() : ThemeHook.primaryText());
            final LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.rightMargin = AndroidUtilities.dp(10);
            row.addView(text, params);

            if (type == TYPE_SWITCH) {
                final Switch toggle = new Switch(context);
                toggle.setChecked(KamiGramConfig.value(key), false);
                toggle.setOnCheckedChangeListener((view, checked) -> {
                    KamiGramConfig.set(key, checked);
                    if (refresh != null) {
                        refresh.run();
                    }
                });
                row.addView(toggle, new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(37), AndroidUtilities.dp(40)));
            } else if (type == TYPE_ACTION) {
                row.setOnClickListener(v -> {
                    try {
                        click.run();
                    } catch (Throwable throwable) {
                        FileLog.e(throwable);
                    }
                });
            }
            return row;
        }
    }

    private static String versionName() {
        try {
            return MessagesController.getGlobalMainSettings().getString("kamigram_version", "12.10.3 mod");
        } catch (Throwable ignore) {
            return "12.10.3 mod";
        }
    }
}
