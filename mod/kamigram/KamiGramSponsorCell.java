package org.telegram.messenger.kamigram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Permanent, non-blocking AsuMeo sponsor row for the main dialogs list.
 * It is presentation only: it never joins, checks, blocks, or gates anything.
 */
public final class KamiGramSponsorCell extends FrameLayout {

    private final TextView title;
    private final TextView subtitle;
    private final TextView developer;

    public KamiGramSponsorCell(final Context context) {
        super(context);
        setMinimumHeight(AndroidUtilities.dp(68));
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));
        setClickable(true);
        setFocusable(true);
        setContentDescription("AsuMeo — Разработчик");

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(AndroidUtilities.dp(14));
        setBackground(background);

        title = new TextView(context);
        title.setText("AsuMeo");
        title.setTextSize(16);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER_VERTICAL);
        addView(title, new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, AndroidUtilities.dp(26), Gravity.TOP | Gravity.LEFT));

        subtitle = new TextView(context);
        subtitle.setText("Спонсор KamiProxy");
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams subtitleParams = new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, AndroidUtilities.dp(24), Gravity.TOP | Gravity.LEFT);
        subtitleParams.topMargin = AndroidUtilities.dp(27);
        addView(subtitle, subtitleParams);

        developer = new TextView(context);
        developer.setText("Разработчик");
        developer.setTextSize(11);
        developer.setGravity(Gravity.CENTER);
        developer.setTypeface(AndroidUtilities.bold());
        FrameLayout.LayoutParams developerParams = new FrameLayout.LayoutParams(
            AndroidUtilities.dp(104), AndroidUtilities.dp(30), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        developerParams.leftMargin = AndroidUtilities.dp(8);
        addView(developer, developerParams);

        setOnClickListener(view -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(KamiGramChannelGuard.CHANNEL_URL));
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
            } catch (Throwable ignore) {
            }
        });
        bindTheme();
    }

    public void bindTheme() {
        int surface = Theme.getColor(Theme.key_windowBackgroundWhite);
        int titleColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int muted = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int accent = ThemeHook.YORU_PURPLE;
        if (getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) getBackground()).setColor(surface);
        }
        title.setTextColor(titleColor);
        subtitle.setTextColor(muted);
        developer.setTextColor(0xFF21152F);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(accent);
        badge.setCornerRadius(AndroidUtilities.dp(15));
        developer.setBackground(badge);
    }
}
