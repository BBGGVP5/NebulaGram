package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Chat generation preferences are captured once and never inherited by translation. */
public final class NebulaAiOptions {
    private NebulaAiOptions() { }
    public static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings", 0); }
    public static NebulaAiClient.Options capture() {
        SharedPreferences p = prefs();
        return new NebulaAiClient.Options(p.getBoolean("stream_response", true), p.getBoolean("reasoning", false), p.getFloat("temperature", 1f));
    }
}
