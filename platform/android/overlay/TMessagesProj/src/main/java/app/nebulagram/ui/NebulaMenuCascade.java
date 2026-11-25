package app.nebulagram.ui;

import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import org.telegram.ui.Components.ScaleStateListAnimator;

/**
 * "Cascade in glass menus": while the liquid glass menu grows, its items fade and slide in one
 * after another, and a pressed item springs down slightly. Telegram's flat menu already cascades;
 * the glass morph showed every item at once. Setting "menu_cascade", off by default.
 */
public final class NebulaMenuCascade {
    private NebulaMenuCascade() { }
    public static final String KEY = "menu_cascade";
    /** Items arrive during the first part of the morph, so the last one lands before it settles. */
    static final float SPAN = .7f;
    static final float WAVE = 4f;

    public static boolean enabled() { return NebulaFeatureSettings.enabled(KEY); }

    /** Progress 0..1 of item {@code index} of {@code count} at menu progress {@code t}. */
    static float item(float t, int index, int count) {
        float local = Math.max(0f, Math.min(1f, t / SPAN));
        if (count <= 1) return local;
        // Same staggering curve as Telegram's own menu cascade.
        float start = index / (count + WAVE) , length = (WAVE + 1f) / (count + WAVE);
        return Math.max(0f, Math.min(1f, (local - start) / length));
    }

    static void prepare(ActionBarPopupWindowLayout content) {
        int count = content.getItemsCount();
        for (int i = 0; i < count; i++) {
            View child = content.getItemAt(i);
            if (child == null || child instanceof ActionBarPopupWindow.GapView) continue;
            child.setAlpha(0f);
            child.setTranslationY(-AndroidUtilities.dp(8));
            if (child instanceof ActionBarMenuSubItem) ScaleStateListAnimator.apply(child, .04f, 1.4f);
        }
    }

    static void step(ActionBarPopupWindowLayout content, float t) {
        int count = content.getItemsCount();
        int visible = 0;
        for (int i = 0; i < count; i++) {
            View child = content.getItemAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE && !(child instanceof ActionBarPopupWindow.GapView)) visible++;
        }
        int index = 0;
        for (int i = 0; i < count; i++) {
            View child = content.getItemAt(i);
            if (child == null || child instanceof ActionBarPopupWindow.GapView || child.getVisibility() != View.VISIBLE) continue;
            float at = item(t, index++, visible);
            child.setAlpha(at * (child.isEnabled() ? 1f : .5f));
            child.setTranslationY((1f - at) * -AndroidUtilities.dp(8));
        }
    }

    /** End or cancel: every item in its resting place. */
    static void settle(ActionBarPopupWindowLayout content) {
        int count = content.getItemsCount();
        for (int i = 0; i < count; i++) {
            View child = content.getItemAt(i);
            if (child == null || child instanceof ActionBarPopupWindow.GapView) continue;
            child.setAlpha(child.isEnabled() ? 1f : .5f);
            child.setTranslationY(0);
        }
    }
}
