package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * KamiGram: обязательная подписка на канал (r70, п.5).
 *
 * Правило заказчика:
 *   * проверка — ТОЛЬКО после входа в аккаунт (не на экране логина);
 *   * если на {@code https://t.me/AsuMeo} не подписан — показывается
 *     красивое окно, которое НЕВОЗМОЖНО закрыть;
 *   * кнопка «Подписаться» сама выполняет подписку через API
 *     ({@code channels.joinChannel}) и сама открывает канал в приложении —
 *     пользователю не нужно нажимать ничего ещё;
 *   * если отпишется — через короткое время спросим снова.
 *
 * Без подписки пользоваться приложением нельзя: диалог перекрывает весь
 * интерфейс (не отменяется, блокирует «Назад» и свайпы).
 */
public final class KamiGramChannelGuard {

    public static final String CHANNEL_USERNAME = "AsuMeo";
    public static final String CHANNEL_URL = "https://t.me/AsuMeo";

    private static final long RECHECK_INTERVAL = 10 * 60 * 1000L; // 10 минут
    private static final String PREF_LAST_CHECK = "kamigram_channel_last_check";

    private static volatile boolean checking;
    private static volatile boolean dialogShowing;
    private static volatile long lastCheckAt;
    private static java.lang.ref.WeakReference<AlertDialog> shownDialog;

    private KamiGramChannelGuard() {
    }

    /**
     * Проверить подписку. Вызывается из LaunchActivity.onResume:
     * до входа в аккаунт ничего не делает.
     */
    public static void check(final Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (checking) {
                return;
            }
            // если окно показывали, но оно уже закрылось (экран пересоздался) —
            // снимаем флаг и проверяем подписку СРАЗУ (без 10-минутного интервала):
            // без подписки пользоваться нельзя
            boolean dialogLost = false;
            if (dialogShowing) {
                final AlertDialog dialog = shownDialog == null ? null : shownDialog.get();
                if (dialog == null || !dialog.isShowing()) {
                    dialogShowing = false;
                    dialogLost = true;
                }
            }
            final int account = UserConfig.selectedAccount;
            final UserConfig config = UserConfig.getInstance(account);
            if (config == null || !config.isClientActivated()) {
                return; // ещё не в аккаунте — проверка только ПОСЛЕ входа
            }
            final long now = System.currentTimeMillis();
            if (!dialogLost && now - lastCheckAt < RECHECK_INTERVAL) {
                return;
            }
            lastCheckAt = now;
            checking = true;
            TLRPC.TL_contacts_resolveUsername request = new TLRPC.TL_contacts_resolveUsername();
            request.username = CHANNEL_USERNAME;
            ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> {
                checking = false;
                if (error != null || response == null) {
                    // офлайн/сбой: просто повторим на следующем onResume
                    return;
                }
                final TLRPC.TL_contacts_resolvedPeer result =
                    (TLRPC.TL_contacts_resolvedPeer) response;
                final TLRPC.User user = result.users != null && result.users.size() > 0
                    ? result.users.get(0) : null;
                if (!(user instanceof TLRPC.TL_user)) {
                    return;
                }
                final TLRPC.TL_user channel = (TLRPC.TL_user) user;
                final boolean joined = !channel.left;
                AndroidUtilities.runOnUIThread(() -> {
                    if (joined) {
                        markJoined(account);
                    } else {
                        showSubscribeDialog(activity, account, channel);
                    }
                });
            });
        } catch (Throwable t) {
            checking = false;
            FileLog.e(t);
        }
    }

    private static void markJoined(int account) {
        try {
            final android.content.SharedPreferences prefs =
                MessagesController.getGlobalMainSettings();
            if (prefs != null) {
                prefs.edit().putLong(PREF_LAST_CHECK + "_" + account, System.currentTimeMillis()).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    // ------------------------------------------------------------------ диалог

    private static void showSubscribeDialog(final Activity activity, final int account,
                                            final TLRPC.TL_user channel) {
        if (activity == null || activity.isFinishing() || dialogShowing) {
            return;
        }
        dialogShowing = true;

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(buildView(activity, channel));
        builder.setPositiveButton("Подписаться", (d, which) -> {
            // «Подписаться» — само нажимаем: подписка через API + открытие канала
            doJoin(activity, account, channel, () -> {
                dialogShowing = false;
            });
        });
        final AlertDialog dialog = builder.create();
        // Окно невозможно закрыть: ни крестик, ни «Назад», ни свайп
        dialog.setCancelable(false);
        dialog.show();
        shownDialog = new java.lang.ref.WeakReference<>(dialog);
    }

    /** Красивое содержимое окна: аватар канала, название, короткий текст. */
    private static View buildView(Context ctx, TLRPC.TL_user channel) {
        final LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24),
            AndroidUtilities.dp(24), AndroidUtilities.dp(18));

        // аватар канала (круг, наша палитра, первая буква названия)
        final TextView avatar = new TextView(ctx);
        final android.graphics.drawable.GradientDrawable avatarBg =
            new android.graphics.drawable.GradientDrawable();
        avatarBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatarBg.setColor(ThemeHook.YORU_CARD_HIGH);
        avatarBg.setStroke(AndroidUtilities.dp(2), ThemeHook.YORU_LINE);
        avatar.setBackground(avatarBg);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(AndroidUtilities.bold());
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        avatar.setTextColor(ThemeHook.YORU_TEXT);
        final String title = TextUtils.isEmpty(channel.title) ? CHANNEL_USERNAME : channel.title;
        avatar.setText(String.valueOf(title.charAt(0)));
        root.addView(avatar, new LinearLayout.LayoutParams(
            AndroidUtilities.dp(72), AndroidUtilities.dp(72)));

        // название канала
        final TextView name = new TextView(ctx);
        name.setText(TextUtils.isEmpty(channel.title) ? CHANNEL_USERNAME : channel.title);
        name.setTextSize(20);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(ThemeHook.YORU_TEXT);
        name.setGravity(Gravity.CENTER);
        root.addView(name, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView handle = new TextView(ctx);
        handle.setText("@" + CHANNEL_USERNAME);
        handle.setTextSize(14);
        handle.setTextColor(ThemeHook.YORU_PURPLE);
        handle.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        handleParams.topMargin = AndroidUtilities.dp(4);
        root.addView(handle, handleParams);

        final TextView text = new TextView(ctx);
        text.setText("KamiGram работает только для подписчиков канала. "
            + "Подпишись, чтобы пользоваться приложением. "
            + "Если отпишешься — мы снова попросим.");
        text.setTextSize(14);
        text.setTextColor(ThemeHook.YORU_MUTED);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.2f);
        final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = AndroidUtilities.dp(14);
        root.addView(text, textParams);

        return root;
    }

    // ------------------------------------------------------------------ подписка

    /**
     * «Приложение само нажимает подписаться»: подписка через API + авто-открытие
     * канала. Если подписка прошла — окно закрывается.
     */
    private static void doJoin(final Activity activity, final int account,
                               final TLRPC.TL_user channel, final Runnable afterJoin) {
        try {
            final TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
            inputChannel.channel_id = channel.id;
            inputChannel.access_hash = channel.access_hash;
            final TLRPC.TL_channels_joinChannel request = new TLRPC.TL_channels_joinChannel();
            request.channel = inputChannel;
            ConnectionsManager.getInstance(account).sendRequestTyped(request, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        markJoined(account);
                        if (afterJoin != null) {
                            afterJoin.run();
                        }
                        // открываем канал прямо в приложении — там уже «Подписан»
                        openChannelInApp(activity);
                    }
                    // если не удалось (нет сети) — окно остаётся, нажмём ещё раз
                });
            });
        } catch (Throwable t) {
            FileLog.e(t);
            afterJoin.run();
        }
    }

    /** Открыть канал внутри нашего приложения (t.me-ссылка обрабатывается клиентом). */
    private static void openChannelInApp(Activity activity) {
        try {
            final Context ctx = activity != null ? activity : ApplicationLoader.applicationContext;
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL));
            intent.setPackage(ctx.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(intent);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Открыть системные настройки «Поверх других приложений» (если вдруг понадобится). */
    public static void requestOverlayPermission(Activity activity) {
        try {
            final Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable ignore) {
            try {
                final Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
