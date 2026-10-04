package app.nebulagram.ui;

import android.graphics.Canvas;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import java.util.IdentityHashMap;

/** Selection actions have independent material; native child hit areas stay unchanged. */
public final class NebulaSelectionGlass {
    private final IdentityHashMap<View, BlurredBackgroundDrawable> actions = new IdentityHashMap<>();
    private BlurredBackgroundDrawableViewFactory factory;
    private BlurredBackgroundColorProvider provider;
    private ActionBarMenu owner;
    public void setup(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider provider) {
        this.factory = factory; this.provider = provider; owner = null; actions.clear();
    }
    public void updateColors() {
        for (BlurredBackgroundDrawable action : actions.values()) action.updateColors();
    }
    public static int counterRight(ActionBarMenu menu, int fallback) {
        int edge = fallback;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (!(child instanceof ActionBarMenuItem) || child.getVisibility() != View.VISIBLE
                    || child.getWidth() == 0 || child.getAlpha() <= 0f) continue;
            edge = Math.min(edge, Math.round(menu.getX() + child.getX()) - AndroidUtilities.dp(4));
        }
        return edge;
    }
    public void drawActions(Canvas canvas, ActionBarMenu menu, BlurredBackgroundDrawable material,
                                   int width, int top, int bottom, float factor) {
        if (material == null || factory == null) return;
        if (owner != menu) { actions.clear(); owner = menu; }
        int first = width, last = 0, centerY = (top + bottom) / 2;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (!(child instanceof ActionBarMenuItem) || child.getVisibility() != View.VISIBLE
                    || child.getWidth() == 0 || child.getAlpha() <= 0f) continue;
            int left = Math.round(menu.getX() + child.getX()), right = left + child.getWidth();
            int extent = Math.min(bottom - top, child.getWidth() + AndroidUtilities.dp(4));
            int centerX = (left + right) / 2;
            BlurredBackgroundDrawable action = actions.get(child);
            if (action == null) {
                action = factory.create().setColorProvider(provider).setRadius(AndroidUtilities.dp(23))
                        .setPadding(AndroidUtilities.dp(6));
                actions.put(child, action);
            }
            action.setSourceOffset(material.getSourceOffsetX(), material.getSourceOffsetY());
            action.setBounds(centerX - extent / 2, centerY - extent / 2,
                    centerX + extent / 2, centerY + extent / 2);
            action.setAlpha(Math.round(255 * factor * child.getAlpha()));
            action.draw(canvas);
            first = Math.min(first, left); last = Math.max(last, right);
        }
        // The ActionBar uses this union only for its initial touch dispatch check.
        material.setBounds(first < last ? first : 0, top, first < last ? last : 0, bottom);
    }
}
