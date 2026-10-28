package app.nebulagram.ui;

/** Window-coordinate geometry independent of popup placement flags. */
public final class NebulaMenuMotion {
    private NebulaMenuMotion() { }
    public static float pivot(float anchorCenter, float popupStart, float extent) {
        return Math.max(0, Math.min(extent, anchorCenter - popupStart));
    }
    private static float unit(float value) { return Math.max(0, Math.min(1, value)); }
    /** The source button's size, bounded even for missing or oversized anchors. */
    public static float seedScale(float anchorExtent, float popupExtent) {
        return popupExtent > 0 ? Math.max(.12f, Math.min(.65f, anchorExtent / popupExtent)) : .3f;
    }
    /**
     * Пружинистость движения. Читается с каждого кадра и компилируется в
     * фикстурах без Android, поэтому это поле, а не чтение настроек;
     * значение приносит {@code NebulaMotion.apply()}. По умолчанию — то
     * поведение, что было до появления настройки.
     */
    private static volatile boolean expressive = true;
    public static void setExpressive(boolean value) { expressive = value; }
    public static boolean isExpressive() { return expressive; }

    /**
     * A lightly damped response with exact endpoints and at most 2.5% overshoot.
     *
     * <p>В спокойном режиме — то же время и те же концы, но затухание без
     * перелёта: кубический выход, у которого производная на конце равна нулю,
     * поэтому движение не «дёргается» там, где пружина возвращалась.
     */
    public static float response(float progress) {
        float t = unit(progress);
        if (t == 0 || t == 1) return t;
        if (!expressive) {
            float remaining = 1 - t;
            return 1 - remaining * remaining * remaining;
        }
        double end = 1 - Math.exp(-8) * (Math.cos(6) + 8d / 6 * Math.sin(6));
        return (float) Math.min(1.025, (1 - Math.exp(-8 * t) * (Math.cos(6 * t) + 8d / 6 * Math.sin(6 * t))) / end);
    }
    public static float scale(float seed, float progress) { return seed + (1 - seed) * response(progress); }
    public static float close(float from, float to, float progress) {
        float t = unit(progress);
        return from + (to - from) * t * t;
    }
    public static float radius(float finalRadius, float extent, float progress) {
        float t = unit(response(progress));
        return finalRadius + (Math.max(finalRadius, extent / 2) - finalRadius) * (1 - t);
    }
    public static int focus(float progress) { return Math.round(12 * unit(1 - response(progress) / .86f)); }
    public static float stretchScale(float pull, float extent, float padding) {
        float content = extent - padding * 2;
        return content > 0 ? 1f + Math.abs(pull) / content : 1f;
    }
    public static float stretchOffset(float pull, float extent, float padding) {
        if (extent <= padding * 2) return 0;
        return Math.min(0, pull) + padding * (1f - stretchScale(pull, extent, padding));
    }
}
