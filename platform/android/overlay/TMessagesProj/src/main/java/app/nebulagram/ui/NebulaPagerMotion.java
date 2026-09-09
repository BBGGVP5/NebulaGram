package app.nebulagram.ui;

/** Geometry follows the pager's frame, without a second animation/timer. */
public final class NebulaPagerMotion {
    private NebulaPagerMotion() { }
    public static float edge(float from, float to, float progress) {
        float p = Float.isNaN(progress) ? 0f : Math.max(0f, Math.min(1f, progress));
        return from + (to - from) * p;
    }
}
