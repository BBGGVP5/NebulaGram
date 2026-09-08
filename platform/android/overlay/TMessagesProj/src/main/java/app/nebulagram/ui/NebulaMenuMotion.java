package app.nebulagram.ui;

/** Window-coordinate geometry independent of popup placement flags. */
public final class NebulaMenuMotion {
    private NebulaMenuMotion() { }
    public static float pivot(float anchorCenter, float popupStart, float extent) {
        return Math.max(0, Math.min(extent, anchorCenter - popupStart));
    }
    public static float scale(float progress) { return .86f + .14f * Math.max(0, Math.min(1, progress)); }
}
