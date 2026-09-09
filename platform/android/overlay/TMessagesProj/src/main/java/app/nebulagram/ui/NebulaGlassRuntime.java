package app.nebulagram.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Main-thread, app-lifetime resource observer; no polling and no retained activities. */
public final class NebulaGlassRuntime {
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<Activity> visible = Collections.newSetFromMap(new WeakHashMap<>());
    private static PowerManager power;
    private static boolean initialized, lowRam, thermalHot;
    private NebulaGlassRuntime() { }
    public static void init(Application app) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> init(app)); return; }
        if (initialized) return;
        initialized = true;
        power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        ActivityManager memory = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        lowRam = memory != null && memory.isLowRamDevice();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { refresh(); }
        };
        IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(receiver, filter);
        if (Build.VERSION.SDK_INT >= 29 && power != null) Thermal.observe(power);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity a) { visible.add(a); refresh(); }
            public void onActivityPaused(Activity a) { visible.remove(a); }
            public void onActivityDestroyed(Activity a) { visible.remove(a); }
            public void onActivityCreated(Activity a, Bundle state) { }
            public void onActivityStarted(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a, Bundle state) { }
        });
        refresh();
    }
    private static final class Thermal {
        static void observe(PowerManager power) {
            try {
                update(power.getCurrentThermalStatus());
                power.addThermalStatusListener(status -> main.post(() -> update(status)));
            } catch (RuntimeException unsupported) { /* Some OEMs do not expose thermal status. */ }
        }
        static void update(int status) {
            // Hysteresis: enter at MODERATE, leave only at NONE to avoid quality oscillation.
            if (status >= PowerManager.THERMAL_STATUS_MODERATE) thermalHot = true;
            else if (status == PowerManager.THERMAL_STATUS_NONE) thermalHot = false;
            refresh();
        }
    }
    private static void refresh() {
        boolean save = power != null && power.isPowerSaveMode();
        if (NebulaGlass.environment(save, thermalHot, lowRam)) invalidateWindows();
    }
    public static void invalidateWindows() {
        main.post(() -> {
            for (Activity activity : visible) {
                if (activity != null && activity.getWindow() != null) {
                    android.view.View root = activity.getWindow().getDecorView();
                    root.requestLayout(); root.invalidate();
                }
            }
        });
    }
}
