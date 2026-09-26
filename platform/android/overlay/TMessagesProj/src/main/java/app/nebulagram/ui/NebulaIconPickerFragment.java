package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LauncherIconController;

/** NebulaGram-owned picker for the original launcher icon set. */
public final class NebulaIconPickerFragment extends BaseFragment {
    private LauncherIconController.LauncherIcon selected;
    private LinearLayout grid;
    private NebulaButton apply;

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text("Иконка приложения", "App icon"));
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) { selected = icon; break; }
        }
        if (selected == null) selected = LauncherIconController.LauncherIcon.DEFAULT;

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(theme.surface());
        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(92));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(new NebulaSettingsHero(context, R.drawable.msg_customize,
                text("Иконки NebulaGram", "NebulaGram icons"),
                text("Выбери значок приложения. Все иконки созданы для NebulaGram и доступны без Premium.",
                        "Choose an app icon. Every design is made for NebulaGram and available without Premium.")));
        content.addView(NebulaCard.header(context, text("Коллекция", "Collection")));
        NebulaCard card = new NebulaCard(context);
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(6), dp(8), dp(6), dp(8));
        addGrid(context);
        card.add(grid);
        content.addView(card);

        FrameLayout footer = new FrameLayout(context);
        footer.setPadding(dp(16), dp(8), dp(16), dp(12));
        footer.setBackgroundColor(theme.surface());
        apply = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        apply.setText(text("Использовать иконку", "Use icon"));
        apply.setOnClickListener(v -> {
            LauncherIconController.setIcon(selected);
            toast(text("Иконка приложения обновлена", "App icon updated"));
            finishFragment();
        });
        footer.addView(apply, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        root.addView(footer, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        updateSelected();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, root);
    }

    private int dp(float value) { return AndroidUtilities.dp(value); }

    private void addGrid(Context context) {
        LauncherIconController.LauncherIcon[] icons = LauncherIconController.LauncherIcon.values();
        int columns = 4;
        for (int start = 0; start < icons.length; start += columns) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int index = start; index < Math.min(icons.length, start + columns); index++) {
                LauncherIconController.LauncherIcon icon = icons[index];
                LinearLayout tile = new LinearLayout(context);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(4), dp(9), dp(4), dp(9));
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Drawable background = ApplicationLoader.applicationContext.getDrawable(icon.background).mutate();
                Drawable foreground = ApplicationLoader.applicationContext.getDrawable(icon.foreground).mutate();
                image.setImageDrawable(new LayerDrawable(new Drawable[]{background, foreground}));
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(58), dp(58));
                tile.addView(image, imageParams);
                TextView title = new TextView(context);
                title.setText(iconTitle(context, icon));
                title.setTextSize(12);
                title.setGravity(Gravity.CENTER);
                title.setMaxLines(1);
                title.setSingleLine(true);
                title.setTextColor(NebulaTheme.of(context).onSurface());
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
                titleParams.topMargin = dp(6);
                tile.addView(title, titleParams);
                tile.setOnClickListener(v -> { selected = icon; updateSelected(); });
                row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private String iconTitle(Context context, LauncherIconController.LauncherIcon icon) {
        try { return context.getString(icon.title); }
        catch (Exception ignored) { return icon.name(); }
    }

    private void updateSelected() {
        if (grid == null) return;
        LauncherIconController.LauncherIcon[] icons = LauncherIconController.LauncherIcon.values();
        int child = 0;
        for (int start = 0; start < icons.length; start += 4) {
            LinearLayout row = (LinearLayout) grid.getChildAt(start / 4);
            for (int index = start; index < Math.min(icons.length, start + 4); index++) {
                View tile = row.getChildAt(index - start);
                boolean active = icons[index] == selected;
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setCornerRadius(dp(18));
                shape.setColor(active ? NebulaTheme.stateLayer(NebulaTheme.of(tile.getContext()).primary(), .14f) : android.graphics.Color.TRANSPARENT);
                if (active) shape.setStroke(dp(1), NebulaTheme.stateLayer(NebulaTheme.of(tile.getContext()).primary(), .7f));
                tile.setBackground(shape);
                child++;
            }
        }
        if (apply != null) apply.setEnabled(selected != null);
    }
}
