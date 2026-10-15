package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

/** Full account/dialog identities, without truncating IDs into notification hashes. */
public final class AccountNotificationIdentity {
    private AccountNotificationIdentity() {}

    public static String summary(int account) { return prefix(account) + "summary"; }

    public static String dialog(int account, long dialog, boolean story) {
        return prefix(account) + (story ? "stories" : "dialog:" + dialog);
    }

    private static String prefix(int account) {
        if (account < 0) throw new IllegalArgumentException("Invalid account ID");
        return "nebula.messages.account:" + account + ":";
    }

    public static Intent scope(Intent intent, int account) {
        // Categories participate in PendingIntent identity on every supported
        // Android release. Keep Telegram's action/data/component routing intact.
        intent.addCategory(prefix(account) + "intent");
        Bundle extras = intent.getExtras();
        if (extras != null) for (String key : new String[]{
                "dialog_id", "dialogId", "did", "chatId", "userId", "encId", "topic_id", "topicId", "story", "storyReaction"}) {
            Object value = extras.get(key);
            if (value instanceof Number || value instanceof Boolean) {
                intent.addCategory("nebula.message." + key + ":" + value);
            }
        }
        return intent;
    }

    public static void removeLegacyMessages(Context context) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            for (StatusBarNotification entry : manager.getActiveNotifications()) {
                Notification notification = entry.getNotification();
                if (entry.getTag() == null && Notification.CATEGORY_MESSAGE.equals(notification.category)
                        && (notification.flags & Notification.FLAG_ONGOING_EVENT) == 0) {
                    manager.cancel(entry.getId());
                }
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
    }
}
