package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import org.telegram.messenger.ApplicationLoader;

public final class NebulaHaptics {
    private static long lastTick = -100;
    private NebulaHaptics() { }
    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0); }
    public static boolean enabled() { return prefs().getBoolean("glass_haptics", false); }
    public static void enabled(boolean value) { prefs().edit().putBoolean("glass_haptics", value).apply(); }
    public static int strength() { return Math.max(1, Math.min(100, prefs().getInt("glass_haptic_strength", 35))); }
    public static void strength(int value) { prefs().edit().putInt("glass_haptic_strength", Math.max(1, Math.min(100, value))).apply(); }
    public static void tick(View view) {
        if (!enabled() || !view.isHapticFeedbackEnabled()) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!accept(now, lastTick)) return;
        Vibrator vibrator = (Vibrator) view.getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            lastTick = now;
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(8 + strength()/8, 25 + strength()*2));
            else view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (SecurityException ignored) { }
    }
    public static boolean accept(long now, long previous) { return now - previous >= 40; }
}
