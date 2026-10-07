package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

/** Wrapping, accessible tool buttons with icons above labels. */
public final class NebulaToolGrid extends ViewGroup {
    private int columns = 3, cellWidth;
    private int[] rowHeights = new int[0];

    public NebulaToolGrid(Context context) { super(context); }
    private int dp(float value) { return AndroidUtilities.dp(value); }

    public void add(int icon, String label, String description, OnClickListener click) {
        addItem(icon, -1, null, label, description, click);
    }
    public void addEmoji(int account, String emoji, String label, String description, OnClickListener click) {
        addItem(0, account, emoji, label, description, click);
    }
    private void addItem(int icon, int account, String emoji, String label, String description, OnClickListener click) {
        Context c = getContext();
        NebulaTheme theme = NebulaTheme.of(c);
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(8), dp(8), dp(8), dp(8));
        cell.setMinimumHeight(dp(72));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xffffffff); mask.setCornerRadius(dp(16));
        cell.setBackground(new RippleDrawable(ColorStateList.valueOf(NebulaTheme.stateLayer(theme.primary(), .14f)), null, mask));
        View glyph;
        if (emoji != null) glyph = new NebulaAnimatedEmoji(c, account, emoji, 40);
        else {
            ImageView image = new ImageView(c); image.setImageResource(icon); image.setColorFilter(theme.primary()); glyph = image;
        }
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        cell.addView(glyph, new LinearLayout.LayoutParams(dp(emoji == null ? 24 : 40), dp(emoji == null ? 24 : 40)));
        TextView title = new TextView(c);
        title.setText(label); title.setTextSize(13); title.setTextColor(theme.onSurface());
        title.setGravity(Gravity.CENTER);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        cell.addView(title, titleParams);
        cell.setContentDescription(label + ". " + description);
        cell.setFocusable(true); cell.setOnClickListener(click);
        addView(cell, new LayoutParams(-2, -2));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int gap = dp(8);
        int minimum = dp(92 * Math.max(1f, getResources().getConfiguration().fontScale * .85f));
        columns = Math.max(1, Math.min(3, (width + gap) / (minimum + gap)));
        cellWidth = Math.max(0, (width - gap * (columns - 1)) / columns);
        rowHeights = new int[(getChildCount() + columns - 1) / columns];
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], child.getMeasuredHeight());
        }
        int height = Math.max(0, rowHeights.length - 1) * gap;
        for (int row : rowHeights) height += row;
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int y = 0, gap = dp(8);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < getChildCount(); i++) {
            int column = i % columns;
            int count = Math.min(columns, getChildCount() - (i / columns) * columns);
            int inset = (getWidth() - count * cellWidth - (count - 1) * gap) / 2;
            int x = rtl ? getWidth() - inset - cellWidth - column * (cellWidth + gap)
                    : inset + column * (cellWidth + gap);
            getChildAt(i).layout(x, y, x + cellWidth, y + rowHeights[i / columns]);
            if (column == columns - 1) y += rowHeights[i / columns] + gap;
        }
    }
}
