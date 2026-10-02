package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Immutable render-time settings: no preference locks/lookups in warmed draw calls. */
public final class NebulaGlass {
    private NebulaGlass() { }
    private static SharedPreferences preferences;
    private static volatile Snapshot cached;
    private static volatile long revision;
    private static volatile boolean powerSave, thermalHot, lowRam;
    // SharedPreferences keeps weak listeners. Keep this one strongly, without holding any View/Activity.
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || "glass_custom".equals(key) || "glass_opacity".equals(key)
                || "glass_highlights".equals(key) || "glass_quality".equals(key) || "glass_blur".equals(key)
                || "glass_refraction".equals(key) || "glass_depth".equals(key)
                || "glass_depth_enabled".equals(key)) refresh();
    };

    private static final class Snapshot {
        final boolean custom, highlights;
        final int quality;
        final float opacity, blur, refraction, depth;
        Snapshot(SharedPreferences prefs) {
            custom = prefs.getBoolean("glass_custom", false);
            highlights = prefs.getBoolean("glass_highlights", true);
            quality = Math.max(0, Math.min(2, prefs.getInt("glass_quality", 0)));
            opacity = custom ? .25f + clamp(prefs.getInt("glass_opacity", 63)) * .0075f : .72f;
            blur = custom ? clamp(prefs.getInt("glass_blur", 40)) * .3f : 12f;
            refraction = custom ? clamp(prefs.getInt("glass_refraction", 0)) * .005f : 0f;
            depth = prefs.getBoolean("glass_depth_enabled", true)
                    ? clamp(prefs.getInt("glass_depth", 35)) / 100f : 0f;
        }
        boolean sameAs(Snapshot other) {
            return other != null && custom == other.custom && highlights == other.highlights
                    && quality == other.quality && opacity == other.opacity && blur == other.blur
                    && refraction == other.refraction && depth == other.depth;
        }
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static synchronized SharedPreferences prefs() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
            preferences.registerOnSharedPreferenceChangeListener(listener);
        }
        return preferences;
    }
    private static synchronized void refresh() {
        Snapshot next = new Snapshot(prefs());
        if (next.sameAs(cached)) return;
        cached = next;
        revision++;
        NebulaGlassRuntime.invalidateWindows();
    }

    /** One transaction avoids intermediate materials and repeated window updates. */
    public static void preset(int index) {
        int[] values = index == 1 ? new int[]{47, 30, 20, 35}
                : index == 2 ? new int[]{80, 60, 0, 20} : new int[]{63, 40, 12, 35};
        prefs().edit().putBoolean("glass_custom", true).putBoolean("glass_highlights", true)
                .putBoolean("glass_depth_enabled", true)
                .putInt("glass_opacity", values[0]).putInt("glass_blur", values[1])
                .putInt("glass_refraction", values[2]).putInt("glass_depth", values[3]).apply();
        refresh();
    }
    private static Snapshot snapshot() {
        Snapshot value = cached;
        if (value == null) {
            synchronized (NebulaGlass.class) {
                if (cached == null) refresh();
                value = cached;
            }
        }
        return value;
    }
    public static boolean highlights() { return snapshot().highlights; }
    public static long revision() { snapshot(); return revision; }
    public static boolean custom() { return snapshot().custom; }
    public static void custom(boolean value) {
        prefs().edit().putBoolean("glass_custom", value).apply();
        refresh(); // Also immediate for callers off the main thread; its listener may arrive later.
    }
    public static int value(String key, int fallback) { return clamp(prefs().getInt("glass_" + key, fallback)); }
    public static void setValue(String key, int value) {
        prefs().edit().putInt("glass_" + key, clamp(value)).apply();
        refresh();
    }
    public static int quality() { return snapshot().quality; }
    public static void quality(int value) { prefs().edit().putInt("glass_quality", Math.max(0, Math.min(2, value))).apply(); refresh(); }
    public static boolean reduced() { return NebulaGlassPolicy.reduced(quality(), powerSave, thermalHot, lowRam); }
    public static boolean environment(boolean save, boolean hot, boolean low) {
        boolean before = reduced(); powerSave = save; thermalHot = hot; lowRam = low;
        boolean changed = before != reduced();
        if (changed) revision++;
        return changed;
    }
    public static float opacity() { return reduced() ? Math.max(.85f, snapshot().opacity) : snapshot().opacity; }
    public static float blur() { return reduced() ? Math.min(6f, snapshot().blur) : snapshot().blur; }
    public static float refraction() { return reduced() ? 0f : snapshot().refraction; }
    /** Zero removes the shadow entirely; the rest of the range changes its depth. */
    public static float depth() { return snapshot().depth; }
    public static boolean depthEnabled() { return prefs().getBoolean("glass_depth_enabled", true); }
    public static void depthEnabled(boolean enabled) {
        prefs().edit().putBoolean("glass_depth_enabled", enabled).apply();
        refresh();
    }
}
