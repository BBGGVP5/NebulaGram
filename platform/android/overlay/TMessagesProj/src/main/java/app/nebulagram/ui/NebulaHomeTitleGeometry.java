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
                                       float titleWidth, int prefix, float progress) {
        float animatedPrefix = prefix * Math.max(0f, Math.min(1f, progress));
        float groupWidth = titleWidth + animatedPrefix;
        return Math.max(startInset, Math.min((barWidth - groupWidth) / 2f,
                barWidth - endInset - groupWidth));
    }

    private static float smooth(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        return p * p * (3f - 2f * p);
    }

    /** The horizontal slot settles early; vertical movement has no separate phase seam. */
    public static float gather(float progress) { return smooth(progress / .6f); }
    public static float lift(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        // Keep large rings below the controls until they fit the horizontal slot.
        return smooth(p * p);
    }

    public static float avatarCenterY(float expandedCenter, float toolbarCenter, float progress) {
        return expandedCenter + (toolbarCenter - expandedCenter) * lift(progress);
    }

    /**
     * Кольцо историй едет вверх вместе со своим рядом (а не висит на месте ряда до
     * прокрутки над уехавшими под него чатами), но не заходит в полосу заголовка,
     * пока по горизонтали ещё задевает заголовок или «Изм.». Удержание включается
     * плавно, за 12 dp до касания, поэтому кольцо не прыгает.
     *
     * @param dp один dp в пикселях
     */
    public static float clearCenterY(float y, float toolbarCenter, float radius,
                                     float ringLeft, float ringRight, float titleLeft, float dp) {
        float title = ramp((ringRight - (titleLeft - 3 * dp) + 12 * dp) / (12 * dp));
        float edit = ramp((76 * dp - ringLeft + 12 * dp) / (12 * dp));
        float result = y;
        result = Math.max(result, y + (toolbarCenter + 14.5f * dp + radius - y) * title);
        result = Math.max(result, y + (toolbarCenter + 22.5f * dp + radius - y) * edit);
        return result;
    }

    private static float ramp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
