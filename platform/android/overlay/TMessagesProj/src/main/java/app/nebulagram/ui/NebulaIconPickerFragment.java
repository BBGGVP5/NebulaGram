package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LauncherIconController;

/**
 * App icon picker: a swipeable carousel with the chosen icon large in the
 * middle and its neighbours faded at the sides, a strip of thumbnails and an
 * Apply button. The backdrop and the button follow the carousel continuously,
 * blending the colors of the two icons between which it currently stands.
 *
 * Nothing changes on the home screen until Apply: the switch itself is
 * Telegram's {@link LauncherIconController}, which toggles activity aliases.
 */
public final class NebulaIconPickerFragment extends BaseFragment {
    private static final float SIDE_SCALE = 0.62f;
    private static final float SIDE_ALPHA = 0.45f;

    private LauncherIconController.LauncherIcon[] icons;
    private NebulaBrandPalette[] palettes;
    private int applied, selected;

    private NebulaBrandBackdrop backdrop;
    private Carousel carousel;
    private RecyclerView strip;
    private LinearLayoutManager stripLayout;
    private TextView name, hint, apply;
    private GradientDrawable applyShape;
    private int accent;

    @Override public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);

        icons = LauncherIconController.LauncherIcon.values();
        palettes = new NebulaBrandPalette[icons.length];
        for (int i = 0; i < icons.length; i++) palettes[i] = NebulaBrandPalette.of(context, icons[i]);
        LauncherIconController.LauncherIcon current = NebulaBrandPalette.current();
        for (int i = 0; i < icons.length; i++) if (icons[i] == current) applied = i;
        selected = applied;

        FrameLayout root = new FrameLayout(context);
        backdrop = new NebulaBrandBackdrop(context);
        backdrop.setGeometry(0.42f, 0.95f, 0.84f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        column.addView(NebulaBrandUi.topBar(context, text("Иконка приложения", "App icon"), this::finishFragment),
                new LinearLayout.LayoutParams(-1, AndroidUtilities.statusBarHeight + dp(60)));
        column.addView(new View(context), new LinearLayout.LayoutParams(-1, 0, 1f));

        carousel = new Carousel(context);
        column.addView(carousel, new LinearLayout.LayoutParams(-1, dp(230)));

        name = NebulaBrandUi.text(context, 32, NebulaBrandUi.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(22);
        nameParams.leftMargin = nameParams.rightMargin = dp(24);
        column.addView(name, nameParams);

        hint = NebulaBrandUi.text(context, 16, NebulaBrandUi.TEXT_SECONDARY, false);
        hint.setIncludeFontPadding(true);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(1), 1f);
        hint.setMinLines(2);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(12);
        hintParams.leftMargin = hintParams.rightMargin = dp(36);
        column.addView(hint, hintParams);
        column.addView(new View(context), new LinearLayout.LayoutParams(-1, 0, 1.3f));

        strip = new RecyclerView(context);
        stripLayout = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        strip.setLayoutManager(stripLayout);
        strip.setItemAnimator(null);
        strip.setClipToPadding(false);
        strip.setPadding(dp(10), 0, dp(10), 0);
        strip.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setAdapter(new StripAdapter());
        column.addView(strip, new LinearLayout.LayoutParams(-1, dp(78)));

        apply = NebulaBrandUi.text(context, 18, NebulaBrandUi.TEXT, true);
        apply.setGravity(Gravity.CENTER);
        apply.setText(text("Применить", "Apply"));
        applyShape = NebulaBrandUi.rounded(palettes[selected].accent, 100);
        apply.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), applyShape,
                NebulaBrandUi.rounded(0xFFFFFFFF, 100)));
        apply.setOnClickListener(v -> applySelected(v));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(-1, dp(56));
        applyParams.leftMargin = applyParams.rightMargin = dp(20);
        applyParams.topMargin = dp(14);
        applyParams.bottomMargin = dp(18);
        column.addView(apply, applyParams);

        follow(selected);
        updateSelection();
        stripLayout.scrollToPositionWithOffset(selected, AndroidUtilities.displaySize.x / 2 - dp(32));
        return fragmentView = root;
    }

    private int dp(float value) { return AndroidUtilities.dp(value); }

    private void applySelected(View view) {
        if (selected == applied) return;
        try {
            LauncherIconController.setIcon(icons[selected]);
        } catch (Exception e) {
            Toast.makeText(view.getContext(), text("Не удалось сменить иконку", "Could not change the icon"), Toast.LENGTH_SHORT).show();
            return;
        }
        applied = selected;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        Toast.makeText(view.getContext(), text("Иконка приложения обновлена", "App icon updated"), Toast.LENGTH_SHORT).show();
        updateSelection();
        refreshApplyColor();
        carousel.transformChildren();
    }

    /** Colors for a fractional carousel position: 2.3 is 70% of icon 2 and 30% of icon 3. */
    private void follow(float position) {
        if (backdrop == null || applyShape == null || strip == null || name == null) return;
        position = Math.max(0, Math.min(icons.length - 1, position));
        int from = (int) Math.floor(position);
        int to = Math.min(icons.length - 1, from + 1);
        float t = position - from;
        NebulaBrandPalette a = palettes[from], b = palettes[to];
        backdrop.setColors(NebulaBrandPalette.blend(a.top, b.top, t), NebulaBrandPalette.blend(a.glow, b.glow, t));
        accent = NebulaBrandPalette.blend(a.accent, b.accent, t);
        int nearest = Math.round(position);
        if (nearest != selected) {
            selected = nearest;
            updateSelection();
            centerStrip(selected);
        }
        refreshApplyColor();
    }

    /** The button keeps the carousel's blended color; it dims when the icon is already in use. */
    private void refreshApplyColor() {
        applyShape.setColor(selected == applied ? NebulaBrandPalette.blend(accent, NebulaBrandPalette.FLOOR, 0.4f) : accent);
    }

    private void updateSelection() {
        name.setText(NebulaLauncherIconLabels.title(name.getContext(), icons[selected]));
        boolean inUse = selected == applied;
        hint.setText(inUse
                ? text("Эта иконка уже используется.", "This icon is already in use.")
                : text("Листайте или нажмите на миниатюру, чтобы посмотреть. Значок сменится только после «Применить».",
                        "Swipe or tap a thumbnail to preview. The icon changes only after you tap Apply."));
        apply.setEnabled(!inUse);
        apply.setTextColor(inUse ? 0x80FFFFFF : NebulaBrandUi.TEXT);
        if (strip.getAdapter() != null) strip.getAdapter().notifyItemRangeChanged(0, icons.length);
    }

    private void centerStrip(int index) {
        View child = stripLayout.findViewByPosition(index);
        if (child != null && strip.getWidth() > 0) {
            strip.smoothScrollBy(child.getLeft() + child.getWidth() / 2 - strip.getWidth() / 2, 0);
        } else {
            stripLayout.scrollToPositionWithOffset(index, Math.max(0, strip.getWidth() / 2 - dp(32)));
        }
    }

    private void showIcon(int index) {
        if (carousel == null || carousel.itemWidth <= 0) return;
        float distance = index - carousel.position();
        if (Math.abs(distance) < 0.01f) return;
        carousel.smoothScrollBy(Math.round(distance * carousel.itemWidth), 0);
    }

    @Override public boolean isSwipeBackEnabled(MotionEvent event) {
        // The carousel and the strip scroll sideways; a swipe there is not "back".
        if (event != null && (inside(carousel, event) || inside(strip, event))) return false;
        return super.isSwipeBackEnabled(event);
    }

    private static boolean inside(View view, MotionEvent event) {
        if (view == null || !view.isShown()) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        Rect bounds = new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
        return bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }

    @Override public boolean isLightStatusBar() { return false; }

    @Override public int getNavigationBarColor() { return NebulaBrandPalette.FLOOR; }

    // --- carousel -----------------------------------------------------------

    private final class Carousel extends RecyclerView {
        int itemWidth;
        private final LinearLayoutManager layout;

        Carousel(Context context) {
            super(context);
            layout = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            setLayoutManager(layout);
            setItemAnimator(null);
            setClipToPadding(false);
            setClipChildren(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setHorizontalScrollBarEnabled(false);
            // A first guess from the screen width; onSizeChanged corrects it.
            int width = AndroidUtilities.displaySize.x;
            itemWidth = Math.max(1, Math.round(width * 0.4f));
            setPadding((width - itemWidth) / 2, 0, width - itemWidth - (width - itemWidth) / 2, 0);
            layout.scrollToPositionWithOffset(selected, 0);
            setAdapter(new CarouselAdapter());
            new LinearSnapHelper().attachToRecyclerView(this);
            addOnScrollListener(new OnScrollListener() {
                @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                    transformChildren();
                    follow(position());
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w == oldw || w <= 0) return;
            final int target = selected;
            // Not inside the layout pass: the adapter may not be notified while
            // RecyclerView computes a layout.
            post(() -> {
                itemWidth = Math.max(1, Math.round(w * 0.4f));
                int start = (w - itemWidth) / 2;
                setPadding(start, 0, w - itemWidth - start, 0);
                if (getAdapter() != null) getAdapter().notifyDataSetChanged();
                layout.scrollToPositionWithOffset(target, 0);
                post(this::transformChildren);
            });
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            transformChildren();
        }

        /** Fractional index of the icon under the center of the carousel. */
        float position() {
            View first = getChildAt(0);
            if (first == null || itemWidth <= 0) return selected;
            int index = getChildAdapterPosition(first);
            if (index == NO_POSITION) return selected;
            return index + (getPaddingLeft() - first.getLeft()) / (float) itemWidth;
        }

        void transformChildren() {
            float center = getWidth() / 2f;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                float distance = Math.min(1f, Math.abs(child.getLeft() + child.getWidth() / 2f - center) / Math.max(1, itemWidth));
                NebulaIconArt art = (NebulaIconArt) ((ViewGroup) child).getChildAt(0);
                View badge = ((ViewGroup) child).getChildAt(1);
                float scale = 1f - (1f - SIDE_SCALE) * distance;
                art.setScaleX(scale);
                art.setScaleY(scale);
                art.setAlpha(1f - (1f - SIDE_ALPHA) * distance);
                art.setElevation(dp(20) * (1f - distance));
                // The check sits on the lower right corner of the icon in use
                // and leaves with it as it slides to the side.
                int index = getChildAdapterPosition(child);
                float offset = art.getLayoutParams().width * 0.42f * scale;
                badge.setTranslationX(offset);
                badge.setTranslationY(offset);
                badge.setAlpha(index == applied ? 1f - distance : 0f);
                badge.setScaleX(index == applied ? 1f - 0.3f * distance : 0.5f);
                badge.setScaleY(badge.getScaleX());
            }
        }
    }

    private final class CarouselAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout item = new FrameLayout(parent.getContext());
            item.setClipChildren(false);
            NebulaIconArt art = new NebulaIconArt(parent.getContext());
            item.addView(art, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
            ImageView badge = new ImageView(parent.getContext());
            badge.setImageResource(R.drawable.checkbig);
            badge.setColorFilter(new PorterDuffColorFilter(NebulaBrandUi.TEXT, PorterDuff.Mode.SRC_IN));
            badge.setScaleType(ImageView.ScaleType.CENTER);
            badge.setElevation(dp(22));
            badge.setAlpha(0f);
            item.addView(badge, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            return new RecyclerView.ViewHolder(item) { };
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View badge = ((ViewGroup) holder.itemView).getChildAt(1);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(palettes[position].accent);
            circle.setStroke(dp(3), NebulaBrandPalette.FLOOR);
            badge.setBackground(circle);
            int width = carousel == null ? dp(160) : Math.max(1, carousel.itemWidth);
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
            NebulaIconArt art = (NebulaIconArt) ((ViewGroup) holder.itemView).getChildAt(0);
            int size = Math.min(Math.round(width * 0.92f), dp(176));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) art.getLayoutParams();
            params.width = params.height = size;
            art.setLayoutParams(params);
            art.setIcon(icons[position]);
            art.setContentDescription(NebulaLauncherIconLabels.title(art.getContext(), icons[position]));
            holder.itemView.setOnClickListener(v -> showIcon(holder.getAdapterPosition()));
        }

        @Override public int getItemCount() { return icons.length; }
    }

    // --- thumbnail strip ----------------------------------------------------

    private final class StripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout item = new FrameLayout(parent.getContext());
            item.setLayoutParams(new RecyclerView.LayoutParams(dp(66), ViewGroup.LayoutParams.MATCH_PARENT));
            View ring = new View(parent.getContext());
            item.addView(ring, new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.CENTER));
            NebulaIconArt art = new NebulaIconArt(parent.getContext());
            art.setCornerFraction(0.28f);
            item.addView(art, new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER));
            return new RecyclerView.ViewHolder(item) { };
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ViewGroup item = (ViewGroup) holder.itemView;
            View ring = item.getChildAt(0);
            NebulaIconArt art = (NebulaIconArt) item.getChildAt(1);
            art.setIcon(icons[position]);
            boolean active = position == selected;
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(19));
            shape.setColor(0);
            shape.setStroke(dp(2.5f), active ? NebulaBrandPalette.blend(palettes[position].accent, 0xFFFFFFFF, 0.25f) : 0);
            ring.setBackground(shape);
            art.setAlpha(active || position == applied ? 1f : 0.9f);
            item.setContentDescription(NebulaLauncherIconLabels.title(item.getContext(), icons[position]));
            item.setOnClickListener(v -> showIcon(holder.getAdapterPosition()));
        }

        @Override public int getItemCount() { return icons.length; }
    }
}
