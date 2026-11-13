package app.nebulagram.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * Будильник отложенной истории и включение телефона. Поднимает Telegram и передаёт ждущие
 * истории в обычную загрузку; та сама держит процесс через {@code StoryUploadingService}.
 */
public final class NebulaStoryScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        try {
            ApplicationLoader.postInitApplication();
            NebulaStorySchedule.onWake();
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // Дать загрузке стартовать, пока система держит процесс ради этого вызова.
        AndroidUtilities.runOnUIThread(result::finish, 8000);
    }
}
