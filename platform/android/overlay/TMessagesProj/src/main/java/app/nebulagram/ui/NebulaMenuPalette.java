package app.nebulagram.ui;

/** Stable popup material: tint must remain readable even before blur is ready. */
public final class NebulaMenuPalette {
    private NebulaMenuPalette() { }
    public static int surface(boolean dark) { return dark ? 0xff242426 : 0xfff5f5f7; }
    // 64% leaves the backdrop visible while keeping white labels readable even
    // against a white blur source (including the drawable's 8-bit alpha rounding).
    public static float opacity(float requested) { return Math.max(.64f, Math.min(1f, requested)); }
    public static int contrastSurface(int surface, float opacity) {
        // The opposite underlay is the worst case for the palette's text polarity.
        boolean dark = (surface & 255) < 128;
        int underlay = dark ? 255 : 0;
        int color = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (surface >>> shift) & 255;
            color |= Math.round(channel * opacity + underlay * (1f - opacity)) << shift;
        }
        return color;
    }
}
