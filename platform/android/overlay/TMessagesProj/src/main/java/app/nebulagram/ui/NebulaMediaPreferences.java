package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Music player and document viewer choices. Network lookups stay off until the user enables them. */
public final class NebulaMediaPreferences {
    private NebulaMediaPreferences() { }
    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_media_settings", 0);
    }
    public static boolean expressivePlayer() { return prefs().getBoolean("expressive_player", false); }
    public static boolean lyrics() { return prefs().getBoolean("player_lyrics", false); }
    public static boolean appleCovers() { return prefs().getBoolean("player_apple_covers", true); }
    public static boolean pdfViewer() { return prefs().getBoolean("pdf_viewer", true); }
    public static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); }
}
