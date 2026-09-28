package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

/** A hairline in the menu content, so it scrolls and animates with the actions. */
public final class NebulaMenuDivider extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Theme.ResourcesProvider resourcesProvider;

    public NebulaMenuDivider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setTag(R.id.fit_width_tag, 1);
    }

    public static void add(ItemOptions menu, Context context, Theme.ResourcesProvider resourcesProvider) {
        menu.addView(new NebulaMenuDivider(context, resourcesProvider),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 5));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // LinearLayout first measures its wrap-content rows with AT_MOST, then
        // fills MATCH_PARENT children with the chosen action-row width. A plain
        // View consumes the full AT_MOST limit and pushes a right-anchored menu left.
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec) : 0;
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        int color = NebulaMenuStyle.foreground(this,
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), resourcesProvider);
        paint.setColor(color);
        paint.setAlpha(Math.round(255 * .22f));
        float edge = AndroidUtilities.dpf2(18f);
        float thickness = Math.max(1f, AndroidUtilities.dpf2(.5f));
        float top = (getHeight() - thickness) / 2f;
        canvas.drawRect(edge, top, getWidth() - edge, top + thickness, paint);
    }
}
