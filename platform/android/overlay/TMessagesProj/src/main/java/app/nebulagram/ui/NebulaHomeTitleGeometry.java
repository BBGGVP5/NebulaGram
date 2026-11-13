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

    /**
     * Разъезд стопки в ряд, рост колец и сдвиг заголовка к центру — один общий
     * параметр. Он приходится на конец оттягивания: раньше под шапкой нет места,
     * и кольца, уходящие под заголовок, задели бы текст.
     */
    public static float gather(float progress) { return smooth(progress / GATHER); }

    /** Доля, на которую кольца притянуты к шапке сверх своего ряда (для табов). */
    public static float lift(float progress) { return smooth((progress - .5f) / .5f); }

    static final float GATHER = .18f;

    /**
     * Все кольца едут вместе со своим рядом историй — одной строкой, без
     * удержаний по отдельности (из-за них стопка вставала треугольником), — но не
     * выше центра шапки: пока ряд ещё спрятан под шапкой, стопка стоит в ней.
     * Переход к полу сглажен на 6 dp, чтобы не было излома.
     *
     * @param rowCenter центр кольца в ряду при текущей прокрутке
     * @param dp один dp в пикселях
     */
    public static float avatarCenterY(float rowCenter, float toolbarCenter, float progress, float dp) {
        float k = 6 * dp, x = rowCenter - toolbarCenter;
        float below = x >= k ? x : x <= -k ? 0f : (x + k) * (x + k) / (4 * k);
        return toolbarCenter + below * (1f - lift(progress));
    }
}
