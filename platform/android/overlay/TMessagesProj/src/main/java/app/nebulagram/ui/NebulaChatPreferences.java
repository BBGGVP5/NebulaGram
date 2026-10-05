package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

/** Draw calls read a snapshot; account notification choices are indexed by the user's ID. */
public final class NebulaChatPreferences {
    private static volatile SharedPreferences preferences;
    private static volatile int stickerTime;
    private static volatile boolean forwardCount, math, unmuted;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, k) -> refresh();
    private static synchronized SharedPreferences prefs() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
            preferences.registerOnSharedPreferenceChangeListener(listener); refresh();
        }
        return preferences;
    }
    private static void refresh() {
        SharedPreferences p = preferences; if(p == null)return;
        stickerTime = Math.max(0, Math.min(2, p.getInt("sticker_time_style", 0)));
        forwardCount = p.getBoolean("channel_forward_count", false);
        math = p.getBoolean("inline_math", false); unmuted = p.getBoolean("folder_unmuted_only", false);
    }
    public static int stickerTime() { if (preferences == null) prefs(); return stickerTime; }
    public static boolean forwardCount() { if (preferences == null) prefs(); return forwardCount; }
    public static boolean math() { if (preferences == null) prefs(); return math; }
    public static boolean unmuted() { if (preferences == null) prefs(); return unmuted; }
    public static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); refresh(); }
    public static void stickerTime(int value) { prefs().edit().putInt("sticker_time_style", Math.max(0,Math.min(2,value))).apply(); refresh(); }
    public static boolean notifications(int account) { long owner=UserConfig.getInstance(account).getClientUserId(); return owner == 0 || prefs().getBoolean("notifications_account_"+owner, true); }
    public static void notifications(int account, boolean enabled) { long owner=UserConfig.getInstance(account).getClientUserId(); if(owner!=0)prefs().edit().putBoolean("notifications_account_"+owner, enabled).apply(); }
}
