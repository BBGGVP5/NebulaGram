package app.nebulagram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.SharedConfig;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Состояние звонков: что на самом деле происходит с голосом и видео.
 *
 * <p>Строка «Состояние звонков» раньше уходила в ядро командой
 * {@code calls.stats}, а ядро о звонках ничего не знает — оно поднимает
 * туннель, и только. Ответом было «unknown method». Данные для этого экрана
 * есть на устройстве: маршрут знает {@link NebulaLink}, объём и время —
 * {@link StatsController}, тот же счётчик, из которого Telegram рисует раздел
 * «Передача данных».
 *
 * <p>Ничего не выдумываем: если звонков не было, так и написано.
 */
public final class NebulaCallState {
    private NebulaCallState() { }

    /** Заголовок окна. */
    public static String title() {
        return NebulaText.text("Состояние звонков", "Call state");
    }

    /** Полный отчёт для диалога. */
    public static CharSequence report() {
        StringBuilder text = new StringBuilder();

        boolean routed = NebulaLink.callsRoutedNow();
        boolean chosen = NebulaLink.callsThroughTunnel();
        line(text, NebulaText.text("Маршрут", "Route"), route(routed, chosen));

        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        line(text, NebulaText.text("Через", "Via"), routed && proxy != null
                ? proxy.settings.getAddress() + ":" + proxy.settings.getPort()
                : NebulaText.text("напрямую, серверами Telegram", "directly, over Telegram servers"));

        text.append('\n');

        int account = UserConfig.selectedAccount;
        StatsController stats = StatsController.getInstance(account);
        long sent = 0, received = 0;
        int seconds = 0;
        for (int network : new int[]{StatsController.TYPE_MOBILE, StatsController.TYPE_WIFI, StatsController.TYPE_ROAMING}) {
            sent += stats.getSentBytesCount(network, StatsController.TYPE_CALLS);
            received += stats.getReceivedBytesCount(network, StatsController.TYPE_CALLS);
            seconds += stats.getCallsTotalTime(network);
        }

        if (sent == 0 && received == 0 && seconds == 0) {
            text.append(NebulaText.text(
                    "Звонков на этом устройстве ещё не было.",
                    "No calls have been placed on this device yet."));
            return text;
        }

        line(text, NebulaText.text("В эфире", "Airtime"), LocaleController.formatDuration(seconds));
        line(text, NebulaText.text("Отправлено", "Sent"), AndroidUtilities.formatFileSize(sent));
        line(text, NebulaText.text("Получено", "Received"), AndroidUtilities.formatFileSize(received));

        text.append('\n').append(NebulaText.text(
                "Счётчик общий с разделом «Передача данных» и обнуляется вместе с ним.",
                "The counters are shared with Data Usage and reset together with it."));
        return text;
    }

    private static String route(boolean routed, boolean chosen) {
        if (routed) {
            return NebulaText.text("через NebulaLink", "through NebulaLink");
        }
        if (chosen) {
            // Выбран, но не применён: туннель не поднят, и голос идёт мимо.
            return NebulaText.text("выбран NebulaLink, но туннель не поднят",
                    "NebulaLink is selected, but the tunnel is down");
        }
        return NebulaText.text("напрямую", "direct");
    }

    private static void line(StringBuilder text, String label, String value) {
        if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
        text.append(label).append(": ").append(value).append('\n');
    }
}
