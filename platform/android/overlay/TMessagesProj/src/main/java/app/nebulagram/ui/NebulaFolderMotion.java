package app.nebulagram.ui;

/** Timing and drag handoff shared with the native folder indicator. */
public final class NebulaFolderMotion {
    private NebulaFolderMotion() { }

    public static float advance(float progress, long elapsedMillis) {
        return Math.min(1f, Math.max(0f, progress) + Math.max(0L, elapsedMillis) / 320f);
    }

    public static float release(float dragged, float target, float progress) {
        return dragged + (target - dragged) * Math.max(0f, Math.min(1f, progress));
    }
}
