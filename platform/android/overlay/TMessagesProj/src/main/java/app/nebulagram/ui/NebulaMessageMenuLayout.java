package app.nebulagram.ui;

/** Screen-bound geometry, independent of Android rendering for regression checks. */
public final class NebulaMessageMenuLayout {
    public final float top, scale, menuTop, visibleHeight;
    private NebulaMessageMenuLayout(float top, float scale, float menuTop, float visibleHeight) {
        this.top = top; this.scale = scale; this.menuTop = menuTop; this.visibleHeight = visibleHeight;
    }
    public static float sourceY(float screenY, float sourceTop, float targetTop, float scale, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        float currentScale = Math.max(.01f, 1f + (scale - 1f) * p);
        return sourceTop + (screenY - sourceTop - (targetTop - sourceTop) * p) / currentScale;
    }
    public static NebulaMessageMenuLayout calculate(float sourceTop, float messageHeight,
            float minTop, float bottom, float menuHeight, float gap) {
        float available = Math.max(1, bottom - minTop - menuHeight - gap);
        // Long posts get a clipped preview instead of unreadable miniature text.
        float scale = Math.max(.9f, Math.min(1, available / Math.max(1, messageHeight)));
        float visible = Math.min(available, Math.max(1, messageHeight) * scale);
        float top = Math.max(minTop, Math.min(sourceTop, bottom - menuHeight - gap - visible));
        return new NebulaMessageMenuLayout(top, scale, top + visible + gap, visible);
    }
}
