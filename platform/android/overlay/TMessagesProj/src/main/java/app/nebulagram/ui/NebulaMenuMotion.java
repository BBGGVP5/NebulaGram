package app.nebulagram.ui;

/** Window-coordinate geometry independent of popup placement flags. */
public final class NebulaMenuMotion {
    private NebulaMenuMotion() { }
    public static float pivot(float anchorCenter, float popupStart, float extent) {
        return Math.max(0, Math.min(extent, anchorCenter - popupStart));
    }
    public static float scale(float progress) { return .86f + .14f * Math.max(0, Math.min(1, progress)); }
    public static float stretchScale(float pull, float extent, float padding) {
        float content = extent - padding * 2;
        return content > 0 ? 1f + Math.abs(pull) / content : 1f;
    }
    public static float stretchOffset(float pull, float extent, float padding) {
        if (extent <= padding * 2) return 0;
        return Math.min(0, pull) + padding * (1f - stretchScale(pull, extent, padding));
    }
}
