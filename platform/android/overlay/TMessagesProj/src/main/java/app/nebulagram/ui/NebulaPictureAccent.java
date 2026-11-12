package app.nebulagram.ui;

/**
 * Turns the swatches androidx Palette found in a picture into one accent.
 *
 * <p>Pure arithmetic on ARGB ints, with no Android types, so
 * {@code scripts/check-picture-accent.py} can compile and exercise it as is.
 *
 * <p>The choice is deliberately not "the most common colour": a portrait is
 * mostly skin and background, a wallpaper mostly sky. A swatch earns its place
 * by being both visible (population) and colourful (chroma), and the winner is
 * then pulled into a lightness band where it reads on the theme's surfaces.
 */
public final class NebulaPictureAccent {
    private NebulaPictureAccent() { }

    /** Below this chroma a picture is grey, and its "accent" would be noise. */
    static final float MIN_CHROMA = .08f;

    /**
     * The seed colour of a picture, or 0 when it has none worth using.
     *
     * @param rgb        swatch colours, alpha ignored
     * @param population how many pixels each swatch stands for
     */
    public static int seed(int[] rgb, int[] population) {
        if (rgb == null || population == null) return 0;
        int count = Math.min(rgb.length, population.length);
        long total = 0;
        for (int i = 0; i < count; i++) if (population[i] > 0) total += population[i];
        if (total <= 0) return 0;
        int best = 0;
        double bestScore = 0;
        for (int i = 0; i < count; i++) {
            if (population[i] <= 0) continue;
            float chroma = chroma(rgb[i]);
            if (chroma < MIN_CHROMA) continue;
            double score = Math.sqrt(population[i] / (double) total) * chroma;
            if (score > bestScore) {
                bestScore = score;
                best = rgb[i] | 0xFF000000;
            }
        }
        return best;
    }

    /** Swatch indices from best to worst, for the preview chips. */
    public static int[] order(int[] rgb, int[] population) {
        int count = rgb == null || population == null ? 0 : Math.min(rgb.length, population.length);
        Integer[] index = new Integer[count];
        double[] score = new double[count];
        long total = 0;
        for (int i = 0; i < count; i++) total += Math.max(0, population[i]);
        for (int i = 0; i < count; i++) {
            index[i] = i;
            score[i] = total <= 0 ? 0 : Math.sqrt(Math.max(0, population[i]) / (double) total) * (.15 + chroma(rgb[i]));
        }
        java.util.Arrays.sort(index, (a, b) -> Double.compare(score[b], score[a]));
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = index[i];
        return result;
    }

    /**
     * The accent a seed becomes on a dark or light theme: the seed's hue,
     * saturation kept lively but not neon, lightness moved into the band
     * Telegram's own accents live in, so text and icons stay readable.
     */
    public static int tone(int seed, boolean dark) {
        if (seed == 0) return 0;
        float[] hsl = hsl(seed);
        hsl[1] = clamp(hsl[1], .38f, .85f);
        hsl[2] = dark ? clamp(hsl[2], .62f, .76f) : clamp(hsl[2], .36f, .48f);
        // HSL lightness is not what the eye sees: a yellow at .48 is nearly
        // white. Walk further from the surface until text in the accent reads.
        int surface = dark ? DARK_SURFACE : 0xFFFFFFFF;
        int color = rgb(hsl);
        for (int step = 0; step < 30 && contrast(color, surface) < MIN_CONTRAST; step++) {
            hsl[2] = clamp(hsl[2] + (dark ? .01f : -.01f), .05f, .95f);
            color = rgb(hsl);
        }
        return color;
    }

    /** Telegram's darkest stock chat surface, the hardest case for a dark accent. */
    static final int DARK_SURFACE = 0xFF1C242C;
    static final double MIN_CONTRAST = 3.2;

    static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private static double luminance(int color) {
        double sum = 0;
        double[] weight = {.2126, .7152, .0722};
        for (int i = 0; i < 3; i++) {
            double channel = (color >> (16 - 8 * i) & 255) / 255.0;
            channel = channel <= .03928 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
            sum += weight[i] * channel;
        }
        return sum;
    }

    /** HSL chroma in 0..1: saturation that is actually visible. */
    static float chroma(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255f;
    }

    static float[] hsl(int color) {
        float r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h = 0f, s = 0f, d = max - min;
        if (d > 0f) {
            s = d / (1f - Math.abs(2f * l - 1f));
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0f) h += 360f;
        }
        return new float[]{h, Math.min(1f, s), l};
    }

    static int rgb(float[] hsl) {
        float h = hsl[0], s = hsl[1], l = hsl[2];
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | channel(r + m) << 16 | channel(g + m) << 8 | channel(b + m);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
