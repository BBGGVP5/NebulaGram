package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Camera preferences are device-local; credentials and hardware choices are not transferred. */
public final class NebulaCameraSettings {
    public static final int AUTO = 0, LEGACY = 1, CAMERA2 = 2, CAMERAX = 3, SYSTEM = 4;
    private static volatile SharedPreferences preferences;
    private NebulaCameraSettings() { }
    public static SharedPreferences prefs() {
        if (preferences == null) synchronized (NebulaCameraSettings.class) {
            if (preferences == null) preferences = ApplicationLoader.applicationContext.getSharedPreferences("nebula_camera", 0);
        }
        return preferences;
    }
    public static int backend() { return number("backend", AUTO, AUTO, SYSTEM); }
    public static int aspect() { return number("aspect", 0, 0, 3); }
    public static int exposure() { return number("exposure", 0, 0, 3); }
    public static int quality() {
        int value = prefs().getInt("quality", 0);
        return value == 480 || value == 720 || value == 1080 || value == 2160 ? value : 0;
    }
    public static int number(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, prefs().getInt(key, fallback)));
    }
    public static boolean enabled(String key) {
        return prefs().getBoolean(key, "switch_blur".equals(key) || "dual".equals(key));
    }
    public static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); }
    public static void set(String key, int value) { prefs().edit().putInt(key, value).apply(); }
    public static boolean camera2(boolean nativeChoice) {
        int backend = backend();
        return backend == CAMERA2 || backend == CAMERAX || backend != LEGACY && nativeChoice;
    }
    public static boolean effect(String key) { return enabled("enhancements") && enabled(key); }
}
