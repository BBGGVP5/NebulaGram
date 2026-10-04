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
    private static float spring(float t, float damping, float frequency) {
        t = unit(t);
        if (t == 0 || t == 1) return t;
        double end = 1 - Math.exp(-damping) * (Math.cos(frequency) + damping / frequency * Math.sin(frequency));
        double slope = Math.exp(-damping) * (frequency + damping * damping / frequency) * Math.sin(frequency);
        double value = 1 - Math.exp(-damping * t) * (Math.cos(frequency * t)
                + damping / frequency * Math.sin(frequency * t));
        // Smooth endpoint correction keeps the spring's natural peak instead of
        // clipping it into a flat plateau with abrupt changes of velocity.
        return (float) (value - smooth(t) * (end - 1) - t * t * (t - 1) * slope);
    }
    public static void opening(Frame out, float progress, float width, float height,
                               float originX, float originY, float seed, float radius) {
        float t = unit(progress);
        float growth = spring(t * 750 / 500, 9, 8);
        out.x = mix(originX, width / 2, spring(t * 750 / 520, 8, 6));
        out.y = mix(originY, height / 2, spring(t * 750 / 340, 12, 5));
        out.width = mix(Math.min(seed, width), width, growth);
        out.height = mix(Math.min(seed, height), height, growth);
        out.radius = mix(Math.min(seed, Math.min(width, height)) / 2, radius, unit(growth));
        out.alpha = 1;
        float reveal = unit((t - .04f) / .41f);
        out.content = 1 - (1 - reveal) * (1 - reveal) * (1 - reveal);
    }
    public static void closing(Frame out, Frame from, float progress, float width, float height,
                               float originX, float originY, float seed) {
        float t = unit(progress), shrink = smooth(t);
        float travel = smooth((t - .12f) / .88f);
        out.x = mix(from.x, originX, travel);
        out.y = mix(from.y, originY, travel);
        out.width = mix(from.width, Math.min(seed, width), shrink);
        out.height = mix(from.height, Math.min(seed, height), shrink);
        out.radius = mix(from.radius, Math.min(seed, Math.min(width, height)) / 2, shrink);
        out.alpha = from.alpha * (1 - smooth((t - .55f) / .45f));
        out.content = from.content * (1 - smooth(t / .65f));
    }
    public static int closeDuration(Frame from, float width, float height, float seed) {
        float extent = Math.max((from.width - seed) / Math.max(1, width - seed),
                (from.height - seed) / Math.max(1, height - seed));
        return Math.round(200 + 200 * (float) Math.sqrt(unit(extent)));
    }
    private static float lead(float center, float source, float target, float half, float start, float end, float growth) {
        float distance = Math.abs(target - source);
        if (distance < 1) return center;
        float required = source > target ? (source + half - end) / distance : (start + half - source) / distance;
        if (required <= 0) return center;
        float current = (center - source) / (target - source);
        // Add a smooth inward bow instead of pinning a growing edge to the screen.
        float bow = .16f * (float) Math.sin(Math.PI * unit(growth)) * unit(required * 4);
        float next = required + bow;
        float softness = .04f * (float) Math.sin(Math.PI * unit(growth));
        float blend = softness > 0 ? Math.max(0, softness - Math.abs(current - next)) / softness : 0;
        // A smooth maximum avoids a velocity kink where screen clearance starts
        // to lead the free spring. It still never falls below required clearance.
        return mix(source, target, Math.max(current, next) + softness * blend * blend / 4);
    }
    public static void openingWithin(Frame out, float progress, float width, float height,
                                     float x, float y, float seed, float radius,
                                     float left, float top, float right, float bottom) {
        opening(out, progress, width, height, x, y, seed, radius);
        if (progress <= 0 || right <= left || bottom <= top) return;
        float growth = width > seed ? (out.width - seed) / (width - seed)
                : height > seed ? (out.height - seed) / (height - seed) : 1;
        out.x = lead(out.x, x, width / 2, out.width / 2, left, right, growth);
        out.y = lead(out.y, y, height / 2, out.height / 2, top, bottom, growth);
    }
    public static float contentScale(Frame frame, float width, float height, float padding, boolean closing) {
        float sx = Math.max(1, frame.width - padding * 2) / Math.max(1, width - padding * 2);
        float sy = Math.max(1, frame.height - padding * 2) / Math.max(1, height - padding * 2);
        return Math.max(.01f, closing ? Math.min(sx, sy) : Math.max(sx, sy));
    }
    /** Reserve drawing space; never change the trajectory to fit the logical hit rectangle. */
    public static float outset(float width, float height, float x, float y, float seed, float shadow) {
        float halfW = (width + Math.max(0, width - seed) * .045f) / 2;
        float halfH = (height + Math.max(0, height - seed) * .045f) / 2;
        float extraX = Math.abs(x - width / 2) * .025f;
        float left = Math.min(x, width / 2) - extraX - halfW;
        float right = Math.max(x, width / 2) + extraX + halfW - width;
        float top = Math.min(y, height / 2) - halfH;
        float bottom = Math.max(y, height / 2) + halfH - height;
        return Math.max(0, Math.max(Math.max(-left, right), Math.max(-top, bottom))) + shadow;
    }
}
