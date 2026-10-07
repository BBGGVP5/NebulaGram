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
    private static double response(double time, double duration, double bounce) {
        double omega = 2 * Math.PI / duration, damping = (1 - bounce) * omega;
        if (bounce == 0) return 1 - Math.exp(-omega * time) * (1 + omega * time);
        double frequency = omega * Math.sqrt(1 - (1 - bounce) * (1 - bounce));
        return 1 - Math.exp(-damping * time) * (Math.cos(frequency * time)
                + damping / frequency * Math.sin(frequency * time));
    }
    private static float spring(float t, float animationDuration, float duration, float bounce) {
        t = unit(t);
        if (t == 0 || t == 1) return t;
        // FlClash common/motion.dart: SpringDescription.withDurationAndBounce
        // supplies physical periods; SpringCurve samples actual animation time
        // and linearly removes the endpoint residual. No artificial peak cap.
        return (float) (response(t * animationDuration, duration, bounce)
                + (1 - response(animationDuration, duration, bounce)) * t);
    }
    private static float cubic(float t, float x1, float y1, float x2, float y2) {
        t = unit(t);
        if (t == 0 || t == 1) return t;
        float lo = 0, hi = 1, u = t;
        for (int i = 0; i < 16; i++) {
            u = (lo + hi) / 2;
            float v = 1 - u;
            float x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u;
            if (x < t) lo = u; else hi = u;
        }
        float v = 1 - u;
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u;
    }
    public static void opening(Frame out, float progress, float width, float height,
                               float originX, float originY, float seed, float radius) {
        opening(out,progress,width,height,originX,originY,seed,seed,Math.min(seed,Math.min(width,height))/2,radius);
    }
    public static void opening(Frame out, float progress, float width, float height,
                               float originX, float originY, float seedWidth, float seedHeight, float seedRadius, float radius) {
        float t = unit(progress);
        float growth = spring(t, 750, 500, .3f);
        out.x = mix(originX, width / 2, spring(t, 750, 520, .2f));
        out.y = mix(originY, height / 2, spring(t, 750, 340, .12f));
        out.width = mix(seedWidth, width, growth);
        out.height = mix(seedHeight, height, growth);
        out.radius = mix(seedRadius, radius, unit(growth));
        out.alpha = 1;
        float reveal = unit((t - .04f) / .41f);
        out.content = cubic(reveal, .215f, .61f, .355f, 1);
    }
    public static void closing(Frame out, Frame from, float progress, float width, float height,
                               float originX, float originY, float seed) {
        closing(out,from,progress,width,height,originX,originY,Math.min(seed,width),Math.min(seed,height),Math.min(seed,Math.min(width,height))/2);
    }
    public static void closing(Frame out, Frame from, float progress, float width, float height,
                               float originX, float originY, float seedWidth, float seedHeight, float seedRadius) {
        float t = unit(progress), shrink = spring(t, 400, 340, 0);
        out.x = mix(from.x, originX, spring(t, 400, 300, 0));
        out.y = mix(from.y, originY, spring(t, 400, 440, 0));
        out.width = mix(from.width, seedWidth, shrink);
        out.height = mix(from.height, seedHeight, shrink);
        out.radius = mix(from.radius, seedRadius, shrink);
        out.alpha = from.alpha * (1 - smooth((t - .55f) / .45f));
        out.content = from.content * cubic(1 - t * 2, .42f, 0, 1, 1);
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
        openingWithin(out,progress,width,height,x,y,seed,seed,Math.min(seed,Math.min(width,height))/2,radius,left,top,right,bottom);
    }
    public static void openingWithin(Frame out, float progress, float width, float height,
                                     float x, float y, float seedWidth, float seedHeight, float seedRadius, float radius,
                                     float left, float top, float right, float bottom) {
        opening(out, progress, width, height, x, y, seedWidth, seedHeight, seedRadius, radius);
        if (progress <= 0 || right <= left || bottom <= top) return;
        // The native final popup may extend past the visible frame by its shadow
        // padding. Constrain travel relative to that same final rectangle, so
        // reaching progress=1 never switches from a shifted to an unshifted menu.
        left=Math.min(left,0);top=Math.min(top,0);right=Math.max(right,width);bottom=Math.max(bottom,height);
        float growth = Math.abs(width-seedWidth)>1 ? (out.width-seedWidth)/(width-seedWidth)
                : Math.abs(height-seedHeight)>1 ? (out.height-seedHeight)/(height-seedHeight) : 1;
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
        return outset(width,height,x,y,seed,seed,shadow);
    }
    public static float outset(float width, float height, float x, float y, float seedWidth, float seedHeight, float shadow) {
        float halfW = Math.max(seedWidth,width + Math.max(0, width - seedWidth) * .05f) / 2;
        float halfH = Math.max(seedHeight,height + Math.max(0, height - seedHeight) * .05f) / 2;
        float extraX = Math.abs(x - width / 2) * .025f;
        float extraY = Math.abs(y - height / 2) * .005f;
        float left = Math.min(x, width / 2) - extraX - halfW;
        float right = Math.max(x, width / 2) + extraX + halfW - width;
        float top = Math.min(y, height / 2) - extraY - halfH;
        float bottom = Math.max(y, height / 2) + extraY + halfH - height;
        return Math.max(0, Math.max(Math.max(-left, right), Math.max(-top, bottom))) + shadow;
    }
}
