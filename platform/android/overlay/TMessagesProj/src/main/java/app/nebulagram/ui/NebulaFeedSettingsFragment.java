package app.nebulagram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

import static app.nebulagram.ui.NebulaText.text;

/**
 * Настройки ленты: какие каналы в неё входят и карточка «Категории». Порядок
 * категорий меняется перетаскиванием за ручку, глазок прячет вкладку, нажатие
 * на категорию открывает её редактор.
 */
public final class NebulaFeedSettingsFragment extends BaseFragment {
    private NebulaFeedModel model;
    private NebulaRow channelsRow;
    private RecyclerView categories;
    private NebulaTheme theme;

    public NebulaFeedSettingsFragment(int account) {
        currentAccount = account;
    }

    @Override
    public View createView(Context context) {
        theme = NebulaTheme.of(context);
        model = NebulaFeedStore.load(currentAccount);
        NebulaFormUi.bar(this, actionBar, context, text("Лента", "Feed"));
        LinearLayout body = NebulaFormUi.column(context);
        ScrollView scroll = NebulaFormUi.scroll(context, body);

        body.addView(NebulaCard.header(context, text("Каналы", "Channels")));
        NebulaCard channels = new NebulaCard(context);
        channelsRow = new NebulaRow(context).icon(R.drawable.msg_channel).title(text("Каналы ленты", "Feed channels"))
                .subtitle("", false).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaFeedChannelsFragment(currentAccount, 0)));
        channels.add(channelsRow);
        body.addView(channels, new LinearLayout.LayoutParams(-1, -2));

        body.addView(NebulaCard.header(context, text("Категории", "Categories")));
        NebulaCard card = new NebulaCard(context);
        categories = new RecyclerView(context) {
            // Список целиком внутри прокрутки страницы; свою прокрутку не заводит.
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y * 4, MeasureSpec.AT_MOST));
            }
        };
        categories.setNestedScrollingEnabled(false);
        categories.setLayoutManager(new LinearLayoutManager(context));
        categories.setAdapter(new Adapter());
        touchHelper.attachToRecyclerView(categories);
        card.add(categories);
        card.add(new NebulaRow(context).icon(R.drawable.msg_add).title(text("Создать категорию", "Create category"))
                .withClick(v -> presentFragment(new NebulaFeedCategoryFragment(currentAccount, 0))));
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        body.addView(NebulaFormUi.note(context, text(
                "Категории появляются вкладками над лентой. Перетащите, чтобы изменить порядок, нажмите на глазок, чтобы скрыть вкладку.",
                "Categories appear as tabs at the top of the feed. Drag to reorder, tap the eye to hide a tab.")));
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        model = NebulaFeedStore.load(currentAccount);
        if (channelsRow != null) channelsRow.subtitle(NebulaFeedStore.channelsCount(model.channels.size()), false);
        if (categories != null) categories.getAdapter().notifyDataSetChanged();
    }

    private void save() {
        NebulaFeedStore.save(currentAccount, model);
    }

    private final ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            int a = from.getAdapterPosition(), b = to.getAdapterPosition();
            if (a == RecyclerView.NO_POSITION || b == RecyclerView.NO_POSITION) return false;
            model.move(a, b);
            view.getAdapter().notifyItemMoved(a, b);
            return true;
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) { }

        @Override public boolean isLongPressDragEnabled() { return true; }

        @Override
        public void clearView(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(view, holder);
            holder.itemView.setAlpha(1f);
            save();
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
            super.onSelectedChanged(holder, state);
            if (holder != null && state == ItemTouchHelper.ACTION_STATE_DRAG) holder.itemView.setAlpha(.85f);
        }
    });

    private final class Row extends LinearLayout {
        final ImageView handle = new ImageView(getContext()), eye = new ImageView(getContext());
        final TextView emoji = new TextView(getContext()), title = new TextView(getContext()), subtitle = new TextView(getContext());

        @SuppressLint("ClickableViewAccessibility")
        Row(Context context, RecyclerView.ViewHolder holder) {
            super(context);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(AndroidUtilities.dp(60));
            setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            setBackground(org.telegram.ui.ActionBar.Theme.getSelectorDrawable(false));
            handle.setImageResource(R.drawable.list_reorder);
            handle.setColorFilter(new PorterDuffColorFilter(theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            handle.setScaleType(ImageView.ScaleType.CENTER);
            handle.setContentDescription(text("Перетащить", "Drag"));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder);
                return false;
            });
            addView(handle, new LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(48)));
            emoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            emoji.setGravity(Gravity.CENTER);
            addView(emoji, new LayoutParams(AndroidUtilities.dp(36), -2));
            LinearLayout titles = new LinearLayout(context);
            titles.setOrientation(VERTICAL);
            titles.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTextColor(theme.onSurface());
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(theme.onSurfaceVariant());
            titles.addView(title);
            titles.addView(subtitle);
            addView(titles, new LayoutParams(0, -2, 1f));
            eye.setScaleType(ImageView.ScaleType.CENTER);
            eye.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(NebulaTheme.stateLayer(theme.onSurface(), .12f), 1));
            addView(eye, new LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48)));
        }

        void bind(NebulaFeedModel.Category category) {
            emoji.setText(TextUtils.isEmpty(category.emoji) ? "📁" : category.emoji);
            title.setText(category.name);
            subtitle.setText(NebulaFeedStore.channelsCount(model.channelsOf(category).size()));
            boolean visible = !category.hidden;
            eye.setImageResource(visible ? R.drawable.msg_views : R.drawable.msg_archive_hide);
            eye.setColorFilter(new PorterDuffColorFilter(visible ? theme.primary() : theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            eye.setContentDescription(visible ? text("Скрыть вкладку", "Hide tab") : text("Показать вкладку", "Show tab"));
            title.setAlpha(visible ? 1f : .5f);
            emoji.setAlpha(visible ? 1f : .5f);
            eye.setOnClickListener(v -> {
                category.hidden = !category.hidden;
                save();
                bind(category);
            });
            setOnClickListener(v -> presentFragment(new NebulaFeedCategoryFragment(currentAccount, category.id)));
        }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder[] self = new RecyclerView.ViewHolder[1];
            LinearLayout frame = new LinearLayout(parent.getContext());
            frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            self[0] = new RecyclerView.ViewHolder(frame) { };
            Row row = new Row(parent.getContext(), self[0]);
            frame.addView(row, new LinearLayout.LayoutParams(-1, -2));
            return self[0];
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((Row) ((ViewGroup) holder.itemView).getChildAt(0)).bind(model.categories.get(position));
        }

        @Override
        public int getItemCount() { return model == null ? 0 : model.categories.size(); }
    }
}
