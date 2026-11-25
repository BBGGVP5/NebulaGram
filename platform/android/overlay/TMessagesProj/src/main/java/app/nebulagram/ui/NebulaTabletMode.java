package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * "Tablet layout": Telegram decides by the device class (res/values-sw600dp isTablet). "In wide
 * windows" also uses the two-pane layout when the app's own window is wide: an unfolded phone, a
 * desktop mode such as DeX, or a large free-form window. Telegram already re-evaluates the flag on
 * every configuration change and moves the open chats between the panes.
 */
public final class NebulaTabletMode {
    private NebulaTabletMode() { }
    public static final String KEY = "tablet_mode";
    public static final int AUTO = 0, WIDE = 1;
    /** Left pane 280 dp plus chat 360 dp: the NebulaTabletPanes minimums. */
    static final int WIDE_WIDTH_DP = 640;
    /** A phone turned sideways is wide but short; it keeps the phone layout. */
    static final int WIDE_SMALLEST_DP = 480;

    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0); }
    public static int mode() {
        try { return Math.max(AUTO, Math.min(WIDE, prefs().getInt(KEY, AUTO))); } catch (Throwable e) { return AUTO; }
    }
    public static void setMode(int mode) {
        prefs().edit().putInt(KEY, Math.max(AUTO, Math.min(WIDE, mode))).apply();
        // Same path as a configuration change: the next isTablet() reads the new choice.
        AndroidUtilities.resetTabletFlag();
    }
    public static String[] titles() {
        return new String[]{NebulaText.text("Как в Telegram", "As in Telegram"), NebulaText.text("В широких окнах", "In wide windows")};
    }

    /** AndroidUtilities.isTabletForce: the device class, or a wide window when chosen. */
    public static boolean tablet(boolean deviceTablet) {
        int mode = mode();
        if (deviceTablet || mode != WIDE) return deviceTablet;
        // The window, not the display: Telegram keeps displaySize at the activity's own window size.
        float density = AndroidUtilities.density;
        int width = AndroidUtilities.displaySize.x, height = AndroidUtilities.displaySize.y;
        if (density > 0 && width > 0 && height > 0) {
            return decide(mode, false, Math.round(width / density), Math.round(Math.min(width, height) / density));
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return false;
        Configuration configuration = context.getResources().getConfiguration();
        return decide(mode, false, configuration.screenWidthDp, configuration.smallestScreenWidthDp);
    }

    static boolean decide(int mode, boolean deviceTablet, int widthDp, int smallestDp) {
        if (deviceTablet || mode != WIDE) return deviceTablet;
        return widthDp >= WIDE_WIDTH_DP && smallestDp >= WIDE_SMALLEST_DP;
    }
}
