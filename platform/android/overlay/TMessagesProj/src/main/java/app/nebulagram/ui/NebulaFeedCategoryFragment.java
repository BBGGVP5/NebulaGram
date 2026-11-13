package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

import static app.nebulagram.ui.NebulaText.text;

/** Новая категория ленты или правка существующей: значок, название и каналы. */
public final class NebulaFeedCategoryFragment extends BaseFragment {
    private static final String[] EMOJI = {
            "📰", "💼", "🎮", "🎵", "🎬", "📚", "💻", "⚽", "🍔", "✈️", "💰", "🔬", "🎨", "😂", "❤️", "⭐"
    };

    private final long categoryId;
    private NebulaFeedModel model;
    private NebulaFeedModel.Category category;
    private EditTextBoldCursor emoji, name;
    private LinearLayout emojiRow;
    private NebulaRow channelsRow;

    public NebulaFeedCategoryFragment(int account, long categoryId) {
        currentAccount = account;
        this.categoryId = categoryId;
    }

    @Override
    public View createView(Context context) {
        model = NebulaFeedStore.load(currentAccount);
        category = categoryId == 0 ? null : model.find(categoryId);
        NebulaFormUi.bar(this, actionBar, context, category == null
                ? text("Новая категория", "New category") : text("Категория", "Category"));
        LinearLayout body = NebulaFormUi.column(context);
        ScrollView scroll = NebulaFormUi.scroll(context, body);

        body.addView(NebulaCard.header(context, text("Значок", "Icon")));
        HorizontalScrollView emojiScroll = new HorizontalScrollView(context);
        emojiScroll.setHorizontalScrollBarEnabled(false);
        emojiRow = new LinearLayout(context);
        emojiScroll.addView(emojiRow);
        body.addView(emojiScroll, new LinearLayout.LayoutParams(-1, -2));
        emoji = NebulaFormUi.field(context, text("Свой эмодзи", "Custom emoji"), 1, 16);
        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(-1, -2);
        emojiParams.topMargin = AndroidUtilities.dp(8);
        body.addView(emoji, emojiParams);
        emoji.setText(category == null ? EMOJI[0] : category.emoji);
        emoji.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { buildEmoji(); }
        });
        buildEmoji();

        body.addView(NebulaCard.header(context, text("Название", "Name")));
        name = NebulaFormUi.field(context, text("Например, Новости", "For example, News"), 1, 32);
        if (category != null) name.setText(category.name);
        body.addView(name, new LinearLayout.LayoutParams(-1, -2));

        if (category != null) {
            body.addView(NebulaCard.header(context, text("Каналы", "Channels")));
            NebulaCard card = new NebulaCard(context);
            channelsRow = new NebulaRow(context).icon(R.drawable.msg_channel).title(text("Каналы категории", "Category channels"))
                    .subtitle("", false).trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> presentFragment(new NebulaFeedChannelsFragment(currentAccount, categoryId)));
            card.add(channelsRow);
            card.add(new NebulaRow(context).icon(R.drawable.msg_delete).title(text("Удалить категорию", "Delete category"))
                    .destructive().withClick(v -> {
                        model.remove(categoryId);
                        NebulaFeedStore.save(currentAccount, model);
                        finishFragment();
                    }));
            body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }

        body.addView(NebulaFormUi.primary(context, category == null
                ? text("Создать и выбрать каналы", "Create and choose channels") : text("Сохранить", "Save"), v -> save()));
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (category == null || channelsRow == null) return;
        // Каналы правит отдельный экран; поля этого экрана при этом не теряются.
        NebulaFeedModel stored = NebulaFeedStore.load(currentAccount);
        NebulaFeedModel.Category fresh = stored.find(categoryId);
        if (fresh != null) {
            category.channels.clear();
            category.channels.addAll(fresh.channels);
            model.channels.clear();
            model.channels.addAll(stored.channels);
        }
        channelsRow.subtitle(NebulaFeedStore.channelsCount(model.channelsOf(category).size()), false);
    }

    private void buildEmoji() {
        Context context = emojiRow.getContext();
        String current = emoji.getText() == null ? "" : emoji.getText().toString().trim();
        NebulaTheme theme = NebulaTheme.of(context);
        emojiRow.removeAllViews();
        for (String value : EMOJI) {
            TextView view = new TextView(context);
            view.setText(value);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            view.setGravity(Gravity.CENTER);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(AndroidUtilities.dp(14));
            background.setColor(value.equals(current) ? theme.primaryContainer() : theme.surfaceContainer());
            view.setBackground(background);
            view.setOnClickListener(v -> emoji.setText(value));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48));
            params.rightMargin = AndroidUtilities.dp(8);
            emojiRow.addView(view, params);
        }
    }

    private void save() {
        String title = name.getText() == null ? "" : name.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            AndroidUtilities.shakeViewSpring(name, 4);
            return;
        }
        String icon = emoji.getText() == null ? "" : emoji.getText().toString().trim();
        NebulaFeedModel stored = NebulaFeedStore.load(currentAccount);
        if (category == null) {
            NebulaFeedModel.Category created = stored.create(icon, title);
            NebulaFeedStore.save(currentAccount, stored);
            presentFragment(new NebulaFeedChannelsFragment(currentAccount, created.id), true);
            return;
        }
        NebulaFeedModel.Category target = stored.find(categoryId);
        if (target != null) {
            target.emoji = icon;
            target.name = title;
            NebulaFeedStore.save(currentAccount, stored);
        }
        finishFragment();
    }
}
