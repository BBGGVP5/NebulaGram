package app.nebulagram.ui;

/** Keeps centered titles readable when the toolbar buttons are asymmetric. */
public final class NebulaHomeTitleGeometry {
    private NebulaHomeTitleGeometry() { }

    public static int width(int barWidth, int startInset, int endInset, int contentWidth) {
        int available = Math.max(0, barWidth - startInset - endInset);
        int symmetric = Math.max(0, barWidth - 2 * Math.max(startInset, endInset));
        return Math.min(available, Math.max(symmetric, contentWidth));
    }

    public static int left(int barWidth, int startInset, int endInset, int titleWidth) {
        int centered = (barWidth - titleWidth) / 2;
        int lastSafeStart = barWidth - endInset - titleWidth;
        return Math.max(startInset, Math.min(centered, lastSafeStart));
    }
}
