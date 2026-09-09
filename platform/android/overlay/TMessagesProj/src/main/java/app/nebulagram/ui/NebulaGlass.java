package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Immutable render-time settings: no preference locks/lookups in warmed draw calls. */
public final class NebulaGlass {
    private NebulaGlass() { }
    private static SharedPreferences preferences;
    private static volatile Snapshot cached;
    private static volatile boolean powerSave, thermalHot, lowRam;
    // SharedPreferences keeps weak listeners. Keep this one strongly, without holding any View/Activity.
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || "glass_custom".equals(key) || "glass_opacity".equals(key)
                || "glass_quality".equals(key) || "glass_blur".equals(key) || "glass_refraction".equals(key)) refresh();
    };

    private static final class Snapshot {
        final boolean custom;
        final int quality;
        final float opacity, blur, refraction;
        Snapshot(SharedPreferences prefs) {
            custom = prefs.getBoolean("glass_custom", false);
            quality = Math.max(0, Math.min(2, prefs.getInt("glass_quality", 0)));
            opacity = custom ? .25f + clamp(prefs.getInt("glass_opacity", 63)) * .0075f : .72f;
            blur = custom ? clamp(prefs.getInt("glass_blur", 40)) * .3f : 12f;
            refraction = custom ? clamp(prefs.getInt("glass_refraction", 44)) * .005f : .22f;
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
    private static synchronized void refresh() { cached = new Snapshot(prefs()); NebulaGlassRuntime.invalidateWindows(); }
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
        return before != reduced();
    }
    public static float opacity() { return reduced() ? Math.max(.85f, snapshot().opacity) : snapshot().opacity; }
    public static float blur() { return reduced() ? Math.min(6f, snapshot().blur) : snapshot().blur; }
    public static float refraction() { return reduced() ? 0f : snapshot().refraction; }
}
