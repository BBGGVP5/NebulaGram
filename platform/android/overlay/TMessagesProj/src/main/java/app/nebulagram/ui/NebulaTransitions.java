package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** User-selectable fragment transition style. Preview and swipe-back transitions stay native. */
public final class NebulaTransitions {
    public static final int STANDARD = 0, AOSP = 1, SPRING = 2;
    private NebulaTransitions() { }

    public static int style() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return STANDARD;
        SharedPreferences prefs = context.getSharedPreferences("nebulagram", Context.MODE_PRIVATE);
        int value = prefs.getInt("fragment_transition_style", 0);
        return value < STANDARD || value > SPRING ? STANDARD : value;
    }
}
