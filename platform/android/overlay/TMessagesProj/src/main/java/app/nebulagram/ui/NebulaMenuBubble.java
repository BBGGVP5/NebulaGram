package app.nebulagram.ui;

/** Independent surface growth, center travel and content reveal, without frame allocations. */
public final class NebulaMenuBubble {
    public static final int SEED_DP = 44, OPEN_DURATION_MS = 420, CLOSE_MIN_MS = 160, CLOSE_MAX_MS = 240;
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
    /** Zero velocity and acceleration at either end; no spring ringing or tail snap. */
    private static float ease(float t) { double x=unit(t);return (float)(x*x*x*(x*(x*6-15)+10)); }
    public static void opening(Frame out, float progress, float width, float height,
                               float originX, float originY, float seed, float radius) {
        opening(out,progress,width,height,originX,originY,seed,seed,Math.min(seed,Math.min(width,height))/2,radius);
    }
    public static void opening(Frame out, float progress, float width, float height,
                               float originX, float originY, float seedWidth, float seedHeight, float seedRadius, float radius) {
        float t = unit(progress);
        float growth = ease(t);
        out.x = mix(originX, width / 2, growth);
        out.y = mix(originY, height / 2, growth);
        out.width = mix(seedWidth, width, growth);
        out.height = mix(seedHeight, height, growth);
        float round=seedRadius+(Math.min(out.width,out.height)-Math.min(seedWidth,seedHeight))/2;
        out.radius = mix(round, radius, ease((t-.08f)/.82f));
        out.alpha = 1;
        out.content = ease((t - .22f) / .52f);
    }
    public static void closing(Frame out, Frame from, float progress, float width, float height,
                               float originX, float originY, float seed) {
        closing(out,from,progress,width,height,originX,originY,Math.min(seed,width),Math.min(seed,height),Math.min(seed,Math.min(width,height))/2);
    }
    public static void closing(Frame out, Frame from, float progress, float width, float height,
                               float originX, float originY, float seedWidth, float seedHeight, float seedRadius) {
        float t = unit(progress), shrink = ease(t);
        out.x = mix(from.x, originX, shrink);
        out.y = mix(from.y, originY, shrink);
        out.width = mix(from.width, seedWidth, shrink);
        out.height = mix(from.height, seedHeight, shrink);
        float round=seedRadius+(Math.min(out.width,out.height)-Math.min(seedWidth,seedHeight))/2;
        out.radius = mix(from.radius, round, shrink);
        out.alpha = from.alpha * (1 - ease((t - .65f) / .35f));
        out.content = from.content * (1 - ease(t/.52f));
    }
    public static int closeDuration(Frame from, float width, float height, float seed) {
        float extent = Math.max((from.width - seed) / Math.max(1, width - seed),
                (from.height - seed) / Math.max(1, height - seed));
        return Math.round(CLOSE_MIN_MS + (CLOSE_MAX_MS-CLOSE_MIN_MS) * (float) Math.sqrt(unit(extent)));
    }
    private static float lead(float center, float source, float target, float half, float start, float end, float growth) {
        float distance = Math.abs(target - source);
        if (distance < 1) return center;
        float required = source > target ? (source + half - end) / distance : (start + half - source) / distance;
        if (required <= 0) return center;
        float current = (center - source) / (target - source);
        float next = required;
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
        // Labels emerge in place under the circular clip, rather than shrinking
        // into unreadable miniature icons that expand with the entire surface.
        return .96f+.04f*unit(frame.content);
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
