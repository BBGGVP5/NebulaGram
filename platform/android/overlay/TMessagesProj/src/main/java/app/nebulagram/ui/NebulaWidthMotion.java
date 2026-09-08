package app.nebulagram.ui;

/** One time base for the navigation surface and every tab's measured bounds. */
public final class NebulaWidthMotion {
    private float start, target, value;
    private long changedAt;
    private boolean initialized;
    public float resolve(float next, long now, boolean animated) {
        if (!initialized || !animated) {
            initialized = true; start = target = value = next; changedAt = now;
            return value;
        }
        float t = Math.max(0, Math.min(1, (now - changedAt) / 280f));
        value = start + (target - start) * (1 - (float)Math.pow(1-t, 4));
        if (next != target) { start = value; target = next; changedAt = now; }
        return value;
    }
    public void reset() { initialized = false; }
}
