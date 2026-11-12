package app.nebulagram.ui;

/**
 * Повтор отложенного сообщения. Окно выбора даты в Telegram 13.2 уже даёт выбрать
 * повтор (поле {@code schedule_repeat_period} сервера), но многие вызывающие места —
 * стикеры, геопозиция, опросы, контакты, фото из галереи, голосовые, ответы инлайн-ботов —
 * передают дальше только дату и теряют выбор. Окно запоминает выбор, а
 * {@code SendMessagesHelper.sendMessage} подставляет его, если сообщение уходит в тот же
 * диалог ровно на ту же секунду в течение нескольких минут после выбора.
 * Чистая логика без Android: проверяется {@code scripts/check-schedule-repeat.py}.
 */
public final class NebulaScheduleRepeat {
    static final long WINDOW_MS = 10 * 60 * 1000L;
    /** Значения, которые принимает сервер вне тестового контура. */
    public static final int[] PERIODS = { 0, 86400, 7 * 86400, 14 * 86400, 30 * 86400, 91 * 86400, 182 * 86400, 365 * 86400 };

    private static long dialogId;
    private static int scheduleDate;
    private static int period;
    private static long rememberedAt;

    private NebulaScheduleRepeat() { }

    public static void remember(long dialog, int date, int repeat) {
        remember(dialog, date, repeat, System.currentTimeMillis());
    }

    static synchronized void remember(long dialog, int date, int repeat, long now) {
        if (date <= 0 || repeat <= 0) {
            clear();
            return;
        }
        dialogId = dialog;
        scheduleDate = date;
        period = repeat;
        rememberedAt = now;
    }

    public static int resolve(long peer, int date, int requested) {
        return resolve(peer, date, requested, System.currentTimeMillis());
    }

    static synchronized int resolve(long peer, int date, int requested, long now) {
        if (requested != 0 || date <= 0 || period == 0) {
            return requested;
        }
        if (now < rememberedAt || now - rememberedAt > WINDOW_MS) {
            clear();
            return 0;
        }
        if (date != scheduleDate) {
            return 0;
        }
        // -1 и 0 — окна без одного диалога (пересылка в несколько чатов).
        if (dialogId != 0 && dialogId != -1 && dialogId != peer) {
            return 0;
        }
        return period;
    }

    /**
     * Повтор, который реально уйдёт на сервер. У {@code messages.sendMultiMedia} в схеме 13.2.1
     * нет {@code schedule_repeat_period}: альбом отправляется один раз, и значок повтора на нём
     * показывать нельзя.
     */
    public static int sendable(int period, boolean grouped) {
        return grouped ? 0 : period;
    }

    static synchronized void clear() {
        dialogId = 0;
        scheduleDate = 0;
        period = 0;
        rememberedAt = 0;
    }
}
