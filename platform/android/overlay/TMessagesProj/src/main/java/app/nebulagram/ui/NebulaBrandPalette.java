package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import org.telegram.ui.LauncherIconController;

import java.util.EnumMap;

/**
 * Colors of the "About" and "App icon" screens, taken from the launcher icon itself.
 *
 * The icon artwork is sampled once, so a new icon in {@link LauncherIconController}
 * gets a matching backdrop without a hand-written color table.
 */
public final class NebulaBrandPalette {
    /** The bottom of every backdrop: the screens fade to near black like a stage. */
    public static final int FLOOR = 0xFF060608;

    public final int top, glow, accent;

    private static final EnumMap<LauncherIconController.LauncherIcon, NebulaBrandPalette> CACHE =
            new EnumMap<>(LauncherIconController.LauncherIcon.class);

    private NebulaBrandPalette(int top, int glow, int accent) {
        this.top = top;
        this.glow = glow;
        this.accent = accent;
    }

    /** The icon the launcher shows right now. */
    public static LauncherIconController.LauncherIcon current() {
        for (LauncherIconController.LauncherIcon icon : LauncherIconController.LauncherIcon.values()) {
            try {
                if (LauncherIconController.isEnabled(icon)) return icon;
            } catch (Exception ignored) { }
        }
        return LauncherIconController.LauncherIcon.DEFAULT;
    }

    public static NebulaBrandPalette of(Context context, LauncherIconController.LauncherIcon icon) {
        NebulaBrandPalette cached = CACHE.get(icon);
        if (cached != null) return cached;
        NebulaBrandPalette palette = fromColor(sample(context, icon));
        CACHE.put(icon, palette);
        return palette;
    }

    /** Average color of the artwork, weighted towards saturated pixels. */
    private static float[] sample(Context context, LauncherIconController.LauncherIcon icon) {
        final int size = 32;
        float r = 0, g = 0, b = 0, weight = 0, saturation = 0;
        try {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Drawable background = context.getDrawable(icon.background).mutate();
            Drawable foreground = context.getDrawable(icon.foreground).mutate();
            background.setBounds(0, 0, size, size);
            foreground.setBounds(0, 0, size, size);
            background.draw(canvas);
            foreground.draw(canvas);
            int[] pixels = new int[size * size];
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
            bitmap.recycle();
            float[] hsv = new float[3];
            for (int pixel : pixels) {
                float alpha = Color.alpha(pixel) / 255f;
                if (alpha <= 0) continue;
                Color.colorToHSV(pixel, hsv);
                float w = alpha * (0.06f + hsv[1] * hsv[2]);
                r += Color.red(pixel) * w;
                g += Color.green(pixel) * w;
                b += Color.blue(pixel) * w;
                saturation += hsv[1] * w;
                weight += w;
            }
        } catch (Exception ignored) { }
        if (weight <= 0) return new float[]{215, 0.6f, 0.6f};
        float[] hsv = new float[3];
        Color.RGBToHSV(Math.round(r / weight), Math.round(g / weight), Math.round(b / weight), hsv);
        hsv[1] = Math.max(hsv[1], saturation / weight);
        return hsv;
    }

    /**
     * Builds the three colors from a hue: a dim top, a brighter glow behind the
     * artwork and a button accent. Grey icons stay grey instead of gaining a hue.
     */
    static NebulaBrandPalette fromColor(float[] hsv) {
        float hue = hsv[0];
        float saturation = hsv[1];
        boolean neutral = saturation < 0.14f;
        float s = neutral ? saturation * 0.8f : clamp(saturation, 0.38f, 0.66f);
        int top = Color.HSVToColor(new float[]{hue, s, 0.34f});
        int glow = Color.HSVToColor(new float[]{hue, neutral ? s : clamp(saturation, 0.42f, 0.72f), 0.52f});
        int accent = Color.HSVToColor(new float[]{hue, neutral ? Math.min(saturation, 0.12f) : clamp(saturation, 0.48f, 0.7f),
                neutral ? 0.40f : 0.64f});
        return new NebulaBrandPalette(top, glow, accent);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int blend(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    public static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(255 * Math.max(0, Math.min(1, alpha))) << 24);
    }
}
