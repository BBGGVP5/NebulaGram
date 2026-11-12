package app.nebulagram.ui;

import android.os.SystemClock;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * Повтор для {@code SendMessagesHelper.sendMessage}. В схеме Telegram 13.2.1 у
 * {@code messages.sendMultiMedia} нет поля повтора, поэтому альбом с выбранным повтором уходит
 * один раз — об этом говорим прямо, а не рисуем на нём значок повтора.
 */
public final class NebulaScheduleRepeatNotice {
    private static long shownAt;

    private NebulaScheduleRepeatNotice() { }

    public static int forSend(long peer, int scheduleDate, int requested, boolean grouped) {
        int period = NebulaScheduleRepeat.resolve(peer, scheduleDate, requested);
        int sent = NebulaScheduleRepeat.sendable(period, grouped);
        if (sent != period) {
            albumDropped();
        }
        return sent;
    }

    private static void albumDropped() {
        AndroidUtilities.runOnUIThread(() -> {
            long now = SystemClock.elapsedRealtime();
            if (shownAt != 0 && now - shownAt < 5000) {
                return;
            }
            shownAt = now;
            Toast.makeText(ApplicationLoader.applicationContext, NebulaText.text(
                    "Telegram не умеет повторять альбомы: он будет отправлен один раз",
                    "Telegram cannot repeat albums: it will be sent once"), Toast.LENGTH_LONG).show();
        });
    }
}
