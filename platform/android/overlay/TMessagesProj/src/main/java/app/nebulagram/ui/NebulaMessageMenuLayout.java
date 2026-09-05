package app.nebulagram.ui;

/** Screen-bound geometry, independent of Android rendering for regression checks. */
public final class NebulaMessageMenuLayout {
    public final float top, scale, menuTop;
    private NebulaMessageMenuLayout(float top, float scale, float menuTop) {
        this.top = top; this.scale = scale; this.menuTop = menuTop;
    }
    public static NebulaMessageMenuLayout calculate(float sourceTop, float messageHeight,
            float minTop, float bottom, float menuHeight, float gap) {
        float available = Math.max(1, bottom - minTop - menuHeight - gap);
        float scale = Math.min(1, available / Math.max(1, messageHeight));
        float top = Math.max(minTop, Math.min(sourceTop, bottom - menuHeight - gap - messageHeight * scale));
        return new NebulaMessageMenuLayout(top, scale, top + messageHeight * scale + gap);
    }
}
