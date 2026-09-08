package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

public final class NebulaGlass {
    private NebulaGlass() { }
    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0); }
    public static boolean custom() { return prefs().getBoolean("glass_custom", false); }
    public static void custom(boolean value) { prefs().edit().putBoolean("glass_custom", value).apply(); }
    public static int value(String key, int fallback) { return Math.max(0, Math.min(100, prefs().getInt("glass_"+key, fallback))); }
    public static void setValue(String key, int value) { prefs().edit().putInt("glass_"+key, Math.max(0, Math.min(100, value))).apply(); }
    public static float opacity() { return custom() ? .25f + value("opacity", 63)*.0075f : .72f; }
    public static float blur() { return custom() ? value("blur", 40)*.3f : 12f; }
    public static float refraction() { return custom() ? value("refraction", 44)*.005f : .22f; }
}
