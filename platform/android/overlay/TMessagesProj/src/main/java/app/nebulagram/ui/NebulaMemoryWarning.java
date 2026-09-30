package app.nebulagram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import android.widget.Toast;

/** Once per process, only when Android reports active memory pressure. */
public final class NebulaMemoryWarning {
    private static boolean warned;
    private NebulaMemoryWarning() { }
    public static void onPressure(int level) {
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level < android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return;
        AndroidUtilities.runOnUIThread(() -> {
            if (warned || ApplicationLoader.mainInterfacePaused || !NebulaFeatureSettings.enabled("memory_warning")) return;
            warned = true;
            Toast.makeText(ApplicationLoader.applicationContext, NebulaText.text("Android сообщает о нехватке памяти", "Android reports low memory"), Toast.LENGTH_LONG).show();
        });
    }
}
