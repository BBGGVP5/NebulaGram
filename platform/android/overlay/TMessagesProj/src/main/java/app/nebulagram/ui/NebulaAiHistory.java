package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

/** Explicitly opt-in, local-only AI history. Stored outside synchronized settings and credentials. */
public final class NebulaAiHistory {
    private static final String STORE = "nebula_ai_history";
    private static final int MAX_ITEMS = 40;
    private NebulaAiHistory() { }
    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }
    public static boolean enabled() { return prefs().getBoolean("enabled", false); }
    public static void setEnabled(boolean value) { prefs().edit().putBoolean("enabled", value).apply(); }
    public static synchronized void add(String provider, String input, String output) {
        if (!enabled() || input == null || output == null || input.trim().isEmpty() || output.trim().isEmpty()) return;
        JSONArray old;
        try { old = new JSONArray(prefs().getString("items", "[]")); } catch (Exception ignored) { old = new JSONArray(); }
        JSONArray next = new JSONArray();
        JSONObject item = new JSONObject();
        try {
            item.put("provider", provider); item.put("input", trim(input)); item.put("output", trim(output));
            item.put("time", System.currentTimeMillis()); next.put(item);
            for (int i = 0; i < old.length() && next.length() < MAX_ITEMS; i++) next.put(old.getJSONObject(i));
            prefs().edit().putString("items", next.toString()).apply();
        } catch (Exception ignored) { }
    }
    private static String trim(String value) { return value.length() > 8000 ? value.substring(0, 8000) : value; }
    public static synchronized JSONArray items() {
        try { return new JSONArray(prefs().getString("items", "[]")); } catch (Exception ignored) { return new JSONArray(); }
    }
    public static void clear() { prefs().edit().remove("items").apply(); }
}
