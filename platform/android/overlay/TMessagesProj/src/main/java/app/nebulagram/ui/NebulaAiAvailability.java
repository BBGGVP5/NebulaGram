package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Local readiness only; opening a message menu never sends a request to a provider. */
public final class NebulaAiAvailability {
    private NebulaAiAvailability() { }
    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings", 0);
    }
    public static boolean enabled() { return prefs().getBoolean("enabled", true); }
    public static void setEnabled(boolean enabled) { prefs().edit().putBoolean("enabled", enabled).apply(); }
    public static boolean available() {
        SharedPreferences p = prefs();
        int provider = p.getInt("provider", 0);
        if (!enabled() || provider < 0 || provider > 3
                || p.getString("model_" + provider, "").trim().isEmpty()
                || !NebulaAiSecrets.exists(provider)) return false;
        try {
            NebulaAiClient.base(provider, p.getString("endpoint", "https://api.openai.com/v1"));
            return true;
        } catch (Exception ignored) { return false; }
    }
}
