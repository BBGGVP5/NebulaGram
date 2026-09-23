package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;

/** Cached presentation option: layout does not repeatedly read preferences. */
public final class NebulaWidePosts {
    private NebulaWidePosts() { }
    private static volatile Boolean cached;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || "wide_posts".equals(key)) cached = prefs.getBoolean("wide_posts", false);
    };

    public static boolean enabled() {
        Boolean value = cached;
        if (value != null) return value;
        synchronized (NebulaWidePosts.class) {
            if (cached == null) {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
                prefs.registerOnSharedPreferenceChangeListener(listener);
                cached = prefs.getBoolean("wide_posts", false);
            }
            return cached;
        }
    }

    /** Only broadcast channel messages are posts; groups and private chats keep native widths. */
    public static boolean enabledFor(MessageObject message) {
        return enabled() && message != null && message.messageOwner != null
                && message.messageOwner.peer_id != null
                && message.messageOwner.peer_id.channel_id != 0
                && !message.isSupergroup();
    }

    public static void setEnabled(boolean value) {
        // Initialize the listener before writing so imports and resets share the same path.
        enabled();
        ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0)
                .edit().putBoolean("wide_posts", value).apply();
        cached = value;
    }

    /** Text padding; upstream still deducts avatars, side menus and the share inset. */
    public static int textMargin(boolean wide, boolean article, boolean share) {
        if (article) return 40;
        return wide ? (share ? 64 : 48) : 80;
    }

    /** Photo padding includes space for the native share control when present. */
    public static int mediaMargin(boolean share) { return share ? 64 : 32; }
}
