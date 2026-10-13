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

    public static int storyPrefix(int count, int footprint, int step, int gap) {
        return count <= 0 ? 0 : footprint + step * (Math.min(3, count) - 1) + gap;
    }

    public static int visibleStories(int count, int available, int titleWidth,
                                     int footprint, int step, int gap) {
        count = Math.max(0, Math.min(3, count));
        while (count > 0 && titleWidth + storyPrefix(count, footprint, step, gap) > available) count--;
        return count;
    }

    public static int groupLeft(int barWidth, int startInset, int endInset,
                                int titleWidth, int prefix, float progress) {
        int animatedPrefix = Math.round(prefix * Math.max(0f, Math.min(1f, progress)));
        return left(barWidth, startInset, endInset, titleWidth + animatedPrefix);
    }

    public static float groupLeftFloat(int barWidth, int startInset, int endInset,
                                       int titleWidth, int prefix, float progress) {
        float animatedPrefix = prefix * Math.max(0f, Math.min(1f, progress));
        float groupWidth = titleWidth + animatedPrefix;
        return Math.max(startInset, Math.min((barWidth - groupWidth) / 2f,
                barWidth - endInset - groupWidth));
    }

    private static float smooth(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        return p * p * (3f - 2f * p);
    }

    /** Gather full rings below the toolbar before raising them into the title slot. */
    public static float gather(float progress) { return smooth(progress / .6f); }
    public static float lift(float progress) { return smooth((progress - .6f) / .4f); }

    public static float avatarCenterY(float expandedCenter, float toolbarBottom,
                                       float toolbarCenter, float ringRadius, float gap, float progress) {
        float gatheredCenter = toolbarBottom + ringRadius + gap;
        float center = expandedCenter + (gatheredCenter - expandedCenter) * gather(progress);
        return center + (toolbarCenter - center) * lift(progress);
    }
}
