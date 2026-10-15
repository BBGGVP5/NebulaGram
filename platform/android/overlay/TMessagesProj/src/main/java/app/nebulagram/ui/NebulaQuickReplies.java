package app.nebulagram.ui;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

/** Personal plain-text templates. Never calls Telegram Business or sends messages. */
public final class NebulaQuickReplies {
    public static final int MAX_COUNT = 100, MAX_TEXT = 4096, MAX_TITLE = 80;
    public static final class Entry {
        public final String id, title, text;
        public Entry(String id, String title, String text) { this.id = id; this.title = title; this.text = text; }
    }
    private static SharedPreferences prefs(long user) {
        if (user <= 0) throw new IllegalArgumentException("account");
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_quick_replies_" + user, 0);
    }
    public static synchronized ArrayList<Entry> list(long user) {
        ArrayList<Entry> entries = new ArrayList<>();
        String raw = prefs(user).getString("entries", "[]");
        if (raw == null || raw.length() > 3_000_000) return entries;
        try {
            JSONArray array = new JSONArray(raw);
            java.util.HashSet<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < array.length() && entries.size() < MAX_COUNT; i++) {
                JSONObject item = array.optJSONObject(i); if (item == null) continue;
                String id = item.optString("id"), title = item.optString("title"), text = item.optString("text");
                if (!id.isEmpty() && id.length() <= 64 && valid(title, text) && ids.add(id)) entries.add(new Entry(id, title, text));
            }
        } catch (Exception ignored) { }
        return entries;
    }
    private static boolean valid(String title, String text) {
        return title != null && text != null && !title.trim().isEmpty() && !text.trim().isEmpty()
            && title.length() <= MAX_TITLE && text.length() <= MAX_TEXT;
    }
    public static synchronized void put(long user, String id, String title, String text) throws Exception {
        title = title.trim();
        if (!valid(title, text)) throw new IllegalArgumentException("text");
        ArrayList<Entry> entries = list(user);
        if (id == null) {
            if (entries.size() >= MAX_COUNT) throw new IllegalStateException("limit");
            entries.add(new Entry(UUID.randomUUID().toString(), title, text));
        } else {
            int index = -1;
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) { index = i; break; }
            if (index < 0) throw new IllegalStateException("missing");
            entries.set(index, new Entry(id, title, text));
        }
        write(user, entries);
    }
    public static synchronized void delete(long user, String id) throws Exception {
        ArrayList<Entry> entries = list(user); entries.removeIf(entry -> entry.id.equals(id)); write(user, entries);
    }
    private static void write(long user, ArrayList<Entry> entries) throws Exception {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) array.put(new JSONObject().put("id", entry.id).put("title", entry.title).put("text", entry.text));
        prefs(user).edit().putString("entries", array.toString()).apply();
    }
}
