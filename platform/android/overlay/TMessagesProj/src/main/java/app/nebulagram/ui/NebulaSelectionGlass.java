package app.nebulagram.ui;

import android.graphics.Canvas;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/** Selection actions have independent material; native child hit areas stay unchanged. */
public final class NebulaSelectionGlass {
    private NebulaSelectionGlass() { }
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
    public static void drawActions(Canvas canvas, ActionBarMenu menu, BlurredBackgroundDrawable material,
                                   int width, int top, int bottom, float factor) {
        if (material == null) return;
        int first = width, last = 0, centerY = (top + bottom) / 2;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (!(child instanceof ActionBarMenuItem) || child.getVisibility() != View.VISIBLE
                    || child.getWidth() == 0 || child.getAlpha() <= 0f) continue;
            int left = Math.round(menu.getX() + child.getX()), right = left + child.getWidth();
            int extent = Math.min(bottom - top, child.getWidth() + AndroidUtilities.dp(4));
            int centerX = (left + right) / 2;
            material.setBounds(centerX - extent / 2, centerY - extent / 2,
                    centerX + extent / 2, centerY + extent / 2);
            material.setAlpha(Math.round(255 * factor * child.getAlpha()));
            material.draw(canvas);
            first = Math.min(first, left); last = Math.max(last, right);
        }
        // The ActionBar uses this union only for its initial touch dispatch check.
        material.setBounds(first < last ? first : 0, top, first < last ? last : 0, bottom);
    }
}
