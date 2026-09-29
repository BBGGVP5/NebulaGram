package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.util.ArrayList;
import java.util.UUID;

/** Bounded local AI conversations. Disk writes follow the existing history opt-in. */
public final class NebulaAiChats {
    public static final class Chat {
        public final String id;
        public String title;
        public String identity;
        public final ArrayList<String[]> turns = new ArrayList<>();
        private Chat(String id, String title, String identity) {
            this.id = id; this.title = title; this.identity = identity;
        }
    }

    private static final ArrayList<Chat> chats = new ArrayList<>();
    private static String selected;
    private static boolean loaded;
    private NebulaAiChats() { }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_chats", Context.MODE_PRIVATE);
    }
    private static String limit(String value, int length) {
        return value == null ? "" : value.length() <= length ? value : value.substring(0, length);
    }
    private static void ensure() {
        if (loaded) return;
        loaded = true;
        if (NebulaAiHistory.enabled()) {
            try {
                JSONArray saved = new JSONArray(prefs().getString("chats", "[]"));
                for (int i = 0; i < saved.length() && chats.size() < 20; i++) {
                    JSONObject data = saved.optJSONObject(i);
                    if (data == null) continue;
                    Chat chat = new Chat(data.optString("id"), data.optString("title"), data.optString("identity"));
                    if (chat.id.isEmpty()) continue;
                    JSONArray turns = data.optJSONArray("turns");
                    if (turns != null) for (int j = 0; j < turns.length() && chat.turns.size() < 20; j++) {
                        JSONArray pair = turns.optJSONArray(j);
                        if (pair != null && pair.length() == 2) chat.turns.add(new String[]{pair.optString(0), pair.optString(1)});
                    }
                    chats.add(chat);
                }
            } catch (Exception ignored) { chats.clear(); }
            selected = prefs().getString("selected", null);
        }
        if (find(selected) == null) selected = chats.isEmpty() ? create().id : chats.get(0).id;
    }
    private static Chat find(String id) {
        if (id == null) return null;
        for (Chat chat : chats) if (chat.id.equals(id)) return chat;
        return null;
    }
    private static Chat create() {
        Chat chat = new Chat(UUID.randomUUID().toString(), "", "");
        chats.add(0, chat); selected = chat.id; persist(); return chat;
    }
    public static synchronized Chat current() { ensure(); return find(selected); }
    public static synchronized ArrayList<Chat> list() { ensure(); return new ArrayList<>(chats); }
    public static synchronized Chat fresh() {
        ensure();
        Chat current = find(selected);
        return current != null && current.turns.isEmpty() ? current : create();
    }
    public static synchronized Chat select(String id) {
        ensure(); Chat chat = find(id);
        if (chat != null) { selected = id; persist(); }
        return chat;
    }
    public static synchronized void append(String id, String identity, String input, String output) {
        ensure(); Chat chat = find(id);
        if (chat == null) return;
        chat.identity = limit(identity, 512);
        if (chat.title.isEmpty()) chat.title = limit(input.trim().replace('\n', ' '), 48);
        chat.turns.add(new String[]{limit(input, 8000), limit(output, 8000)});
        while (chat.turns.size() > 20) chat.turns.remove(0);
        chats.remove(chat); chats.add(0, chat);
        while (chats.size() > 20) chats.remove(chats.size() - 1);
        persist();
    }
    public static synchronized void clear() {
        chats.clear(); selected = null; loaded = true;
        prefs().edit().remove("chats").remove("selected").apply();
        create();
    }
    private static void persist() {
        if (!NebulaAiHistory.enabled()) return;
        JSONArray array = new JSONArray();
        try {
            for (Chat chat : chats) {
                if (chat.turns.isEmpty()) continue;
                JSONObject data = new JSONObject();
                data.put("id", chat.id); data.put("title", chat.title); data.put("identity", chat.identity);
                JSONArray turns = new JSONArray();
                for (String[] turn : chat.turns) turns.put(new JSONArray().put(turn[0]).put(turn[1]));
                data.put("turns", turns); array.put(data);
            }
            prefs().edit().putString("chats", array.toString()).putString("selected", selected).apply();
        } catch (Exception ignored) { }
    }
}
