package org.telegram.messenger.kamigram;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.FilteredSearchView;

/**
 * Поиск внутри канала/чата с расширенными фильтрами (r109).
 *
 * Работает через родной серверный поиск Telegram (messages.search с фильтром
 * типа и peer текущего чата) — находятся АБСОЛЮТНО все сообщения канала,
 * а не только локально загруженные. Фильтры: все, фото, видео, GIF, медиа,
 * ссылки, файлы, музыка, голосовые. Фото/видео/GIF выдаются родной
 * бесконечной сеткой (SharedPhotoVideoAdapter), остальные — списком.
 * Нажатие на результат открывает сообщение в чате.
 */
public class KamiGramChatSearch extends BaseFragment {

    private static final String[] CHIP_TITLES = {
        "Все", "Фото", "Видео", "GIF", "Медиа", "Ссылки", "Файлы", "Музыка", "Голосовые"
    };

    private long dialogId;
    private long topicId;

    private EditText searchField;
    private FilteredSearchView searchView;
    private final TextView[] chipViews = new TextView[CHIP_TITLES.length];
    /* «Медиа» (индекс 4) — сразу после открытия видна бесконечная сетка медиа
       чата/канала; остальные фильтры переключаются чипсами. */
    private int selectedChip = 4;
    private Runnable searchRunnable;

    public KamiGramChatSearch(Bundle args) {
        super(args);
        if (args != null) {
            dialogId = args.getLong("dialog_id", 0);
            topicId = args.getLong("topic_id", 0);
        }
    }

    @Override
    public boolean canBeginSlide() {
        return false;
    }

    @Override
    public View createView(Context context) {
        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        actionBar = new ActionBar(context);
        actionBar.setBackButtonImage(R.drawable.msg_arrow_back);
        actionBar.setTitle("Поиск Sakura");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) { /* кнопка «назад» */
                    finishFragment();
                }
            }
        });
        ((FrameLayout) fragmentView).addView(actionBar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        // --- поле запроса ---
        searchField = new EditText(context);
        searchField.setHint("Поиск сообщений");
        searchField.setHintTextColor(KamiGramUi.secondaryText());
        searchField.setTextColor(KamiGramUi.primaryText());
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchField.setSingleLine(true);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchField.setBackground(fieldBackground());
        searchField.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        final LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.leftMargin = dp(12);
        fieldParams.rightMargin = dp(12);
        content.addView(searchField, fieldParams);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // --- чипсы фильтров ---
        final HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < CHIP_TITLES.length; i++) {
            final int index = i;
            chipViews[i] = chip(context, CHIP_TITLES[i], i == selectedChip);
            chipViews[i].setOnClickListener(v -> {
                selectedChip = index;
                for (int j = 0; j < chipViews.length; j++) {
                    paintChip(chipViews[j], j == index);
                }
                runSearch(true);
            });
            final LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.leftMargin = index == 0 ? dp(12) : dp(6);
            if (index == CHIP_TITLES.length - 1) {
                chipParams.rightMargin = dp(12);
            }
            chips.addView(chipViews[i], chipParams);
        }
        chipsScroll.addView(chips);
        final LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = dp(10);
        chipsParams.bottomMargin = dp(4);
        content.addView(chipsScroll, chipsParams);

        // --- результаты: родной FilteredSearchView (сетка фото/видео, списки) ---
        searchView = new FilteredSearchView(this);
        searchView.setDelegate((showMediaFilters, users, dates, archive) -> {
        }, false);
        searchView.setUiCallback(new FilteredSearchView.UiCallback() {
            @Override
            public void goToMessage(MessageObject messageObject) {
                if (messageObject == null) {
                    return;
                }
                final Bundle args = new Bundle();
                args.putLong("dialog_id", messageObject.getDialogId());
                args.putInt("message_id", messageObject.getId());
                if (topicId != 0) {
                    args.putLong("topic_id", topicId);
                }
                presentFragment(new ChatActivity(args), true);
            }

            @Override
            public boolean actionModeShowing() {
                return false;
            }

            @Override
            public void toggleItemSelection(MessageObject item, View view, int a) {
            }

            @Override
            public boolean isSelected(FilteredSearchView.MessageHashId messageHashId) {
                return false;
            }

            @Override
            public void showActionMode() {
            }

            @Override
            public int getFolderId() {
                return 0;
            }
        });
        final LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        content.addView(searchView, resultsParams);

        final FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentParams.topMargin = ActionBar.getCurrentActionBarHeight();
        ((FrameLayout) fragmentView).addView(content, contentParams);

        AndroidUtilities.runOnUIThread(() -> {
            if (searchField != null) {
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            }
        }, 220);
        runSearch(true);
        return fragmentView;
    }

    /** Индекс фильтра в FiltersView.filters с защитой от короткого массива. */
    private FiltersView.MediaFilterData currentFilter() {
        final FiltersView.MediaFilterData[] filters = FiltersView.filters;
        switch (selectedChip) {
            case 0:
                return null; /* «Все» — поиск без фильтра типа */
            case 1:
                return filters.length > 5 ? filters[5] : filters[0];
            case 2:
                return filters.length > 6 ? filters[6] : filters[0];
            case 3:
                return filters.length > 7 ? filters[7] : filters[0];
            case 4:
                return filters[0];
            case 5:
                return filters[1];
            case 6:
                return filters[2];
            case 7:
                return filters[3];
            case 8:
                return filters[4];
            default:
                return null;
        }
    }

    private void scheduleSearch() {
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        }
        searchRunnable = () -> runSearch(true);
        AndroidUtilities.runOnUIThread(searchRunnable, 250);
    }

    private void runSearch(boolean reset) {
        if (searchView == null || dialogId == 0) {
            return;
        }
        final String query = searchField == null || searchField.getText() == null
            ? "" : searchField.getText().toString();
        /* Серверный поиск по всему чату/каналу: messages.search, peer=dialogId. */
        searchView.search(dialogId, 0, 0, 0, currentFilter(), false, query, reset);
    }

    // ------------------------------------------------------------------ оформление

    private static TextView chip(Context context, String text, boolean selected) {
        final TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setSingleLine(true);
        chip.setClickable(true);
        chip.setFocusable(true);
        paintChip(chip, selected);
        return chip;
    }

    private static void paintChip(TextView chip, boolean selected) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(14));
        if (selected) {
            chip.setTextColor(KamiGramUi.accent());
            drawable.setColor((KamiGramUi.accent() & 0x00FFFFFF) | 0x1F000000);
        } else {
            chip.setTextColor(KamiGramUi.secondaryText());
            drawable.setColor(0);
            drawable.setStroke(dp(1), KamiGramUi.separator());
        }
        chip.setBackground(drawable);
    }

    private static GradientDrawable fieldBackground() {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(12));
        drawable.setColor(0);
        drawable.setStroke(dp(1), KamiGramUi.separator());
        return drawable;
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}
