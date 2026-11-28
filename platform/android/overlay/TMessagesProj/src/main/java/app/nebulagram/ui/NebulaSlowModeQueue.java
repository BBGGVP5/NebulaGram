package app.nebulagram.ui;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

/** Native scheduled messages own delivery, failures, editing and cancellation. */
public final class NebulaSlowModeQueue {
    private NebulaSlowModeQueue() { }
    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_slowmode_queue", 0);
    }
    private static String key(int account, long dialog) { return UserConfig.getInstance(account).getClientUserId() + ":" + dialog; }
    public static int next(int account, long dialog, int serverNext, int interval) {
        return NebulaSlowModePolicy.next(ConnectionsManager.getInstance(account).getCurrentTime(), serverNext,
                prefs().getInt(key(account, dialog), 0), interval);
    }
    public static void sent(int account, long dialog, int date) { prefs().edit().putInt(key(account, dialog), date).apply(); }
}
