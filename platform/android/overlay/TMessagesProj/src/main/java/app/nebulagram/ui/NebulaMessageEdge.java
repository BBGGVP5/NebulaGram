package app.nebulagram.ui;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import org.telegram.messenger.AndroidUtilities;

/** Draw-only transform: item animator properties and recycler reuse stay untouched. */
public final class NebulaMessageEdge {
    private NebulaMessageEdge() { }
    public static float scale(float top, float bottom, float viewportTop, float viewportBottom, float range) {
        if (range <= 0 || viewportBottom <= viewportTop) return 1f;
        float overflow = Math.max(0f, Math.max(viewportTop - top, bottom - viewportBottom));
        return 1f - .08f * Math.min(1f, overflow / range);
    }
    public static int begin(Canvas canvas, View child, ViewGroup list) {
        if (android.os.Build.VERSION.SDK_INT < 33 || !NebulaFeatureSettings.enabled("message_edge_compression")) return -1;
        if (list instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = ((androidx.recyclerview.widget.RecyclerView) list).getItemAnimator();
            if (animator != null && animator.isRunning()) return -1;
        }
        float top = list.getPaddingTop(), bottom = list.getHeight() - list.getPaddingBottom();
        float scale = scale(child.getY(), child.getY() + child.getHeight(), top, bottom, AndroidUtilities.dp(56));
        if (scale == 1f) return -1;
        int save = canvas.save();
        canvas.scale(scale, scale, child.getX() + child.getWidth() / 2f,
            child.getY() < top ? child.getY() + child.getHeight() : child.getY());
        return save;
    }
}
