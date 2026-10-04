package app.nebulagram.ui;

/** Independent surface growth, center travel and content reveal, without frame allocations. */
public final class NebulaMenuBubble {
    private NebulaMenuBubble() { }
    public static final class Frame {
        public float x, y, width, height, radius, alpha = 1, content = 1;
        public void copy(Frame other) {
            x = other.x; y = other.y; width = other.width; height = other.height;
            radius = other.radius; alpha = other.alpha; content = other.content;
        }
    }
    private static float unit(float x) { return Math.max(0, Math.min(1, x)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float t) { t = unit(t); return t * t * (3 - 2 * t); }
    private static float spring(float t, float damping, float frequency, float peak) {
        t = unit(t);
        if (t == 0 || t == 1) return t;
        double end = 1 - Math.exp(-damping) * (Math.cos(frequency) + damping / frequency * Math.sin(frequency));
        double value = 1 - Math.exp(-damping * t) * (Math.cos(frequency * t)
                + damping / frequency * Math.sin(frequency * t));
        return (float) Math.max(0, Math.min(peak, value / end));
    }
    public static float sourceCenter(float origin, float extent, float seed) {
        float half = Math.min(extent, seed) / 2;
        return Math.max(half, Math.min(extent - half, origin));
    }
    public static void opening(Frame out, float progress, float width, float height,
                               float originX, float originY, float seed, float radius) {
        float t = unit(progress);
        float growth = spring(t, 8, 9, 1.045f);
        out.x = mix(sourceCenter(originX, width, seed), width / 2, spring(t, 8, 6, 1.025f));
        out.y = mix(sourceCenter(originY, height, seed), height / 2, spring(t, 12, 5, 1));
        out.width = mix(Math.min(seed, width), width, growth);
        out.height = mix(Math.min(seed, height), height, growth);
        out.radius = mix(Math.min(seed, Math.min(width, height)) / 2, radius, unit(growth));
        out.alpha = smooth(t / .09f);
        float reveal = unit((t - .05f) / .42f);
        out.content = 1 - (1 - reveal) * (1 - reveal) * (1 - reveal);
    }
    public static void closing(Frame out, Frame from, float progress, float width, float height,
                               float originX, float originY, float seed) {
        float t = unit(progress), shrink = 1 - (1 - t) * (1 - t) * (1 - t);
        float travel = smooth((t - .12f) / .88f);
        out.x = mix(from.x, sourceCenter(originX, width, seed), travel);
        out.y = mix(from.y, sourceCenter(originY, height, seed), travel);
        out.width = mix(from.width, Math.min(seed, width), shrink);
        out.height = mix(from.height, Math.min(seed, height), shrink);
        out.radius = mix(from.radius, Math.min(seed, Math.min(width, height)) / 2, shrink);
        out.alpha = from.alpha * (1 - smooth((t - .55f) / .45f));
        out.content = from.content * (1 - smooth(t / .65f));
    }
    public static float contentScale(Frame frame, float width, float height, float padding) {
        float sx = Math.max(1, frame.width - padding * 2) / Math.max(1, width - padding * 2);
        float sy = Math.max(1, frame.height - padding * 2) / Math.max(1, height - padding * 2);
        return Math.max(.08f, Math.max(sx, sy));
    }
    /** PopupWindow clips its own viewport; keep the glass's padded edges inside it. */
    public static void fit(Frame out, float width, float height, float padding) {
        out.width = Math.min(out.width, width + padding * 2);
        out.height = Math.min(out.height, height + padding * 2);
        out.x = Math.max(out.width / 2 - padding, Math.min(width - out.width / 2 + padding, out.x));
        out.y = Math.max(out.height / 2 - padding, Math.min(height - out.height / 2 + padding, out.y));
    }
}
