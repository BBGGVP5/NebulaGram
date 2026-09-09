package app.nebulagram.ui;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

/** Settings history only: never touches chat search or remote story/message data. */
public final class NebulaSettingsHistory {
    private NebulaSettingsHistory() { }
    public static void clear() {
        MessagesController.getGlobalMainSettings().edit().remove("settingsSearchRecent2").apply();
        refresh();
    }
    public static void refresh() {
        // Settings history is global; refresh every open account's settings adapter.
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateSearchSettings);
        }
    }
}
