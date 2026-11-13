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
    /** Вид своего плеера: 0 — Material 3 Expressive, 1 — Apple Music. */
    public static final int STYLE_EXPRESSIVE = 0, STYLE_APPLE = 1;
    public static int playerStyle() { return prefs().getInt("player_style", STYLE_EXPRESSIVE); }
    public static void setPlayerStyle(int value) { prefs().edit().putInt("player_style", value).apply(); }
    public static boolean pdfViewer() { return prefs().getBoolean("pdf_viewer", true); }
    public static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); }
}
