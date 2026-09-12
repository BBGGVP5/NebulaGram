package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Как показывать идентификатор собеседника или чата.
 *
 * <p>Для человека оба формата совпадают: его номер положителен и там, и там.
 * Расходятся они на группах и каналах. MTProto — то, чем оперирует сам клиент,
 * — хранит их положительными; Bot API тот же канал называет отрицательным
 * числом с приставкой {@code -100}, а обычную группу — просто отрицательным.
 * Один и тот же чат поэтому выглядит как {@code 1234567890} или как
 * {@code -1001234567890}, и какой из них «правильный», зависит от того, куда
 * его потом вставят: в бота или в клиентский инструмент.
 */
public final class NebulaIds {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "id_format";

    /** Числа как их видит сам клиент: канал и группа положительные. */
    public static final int TELEGRAM = 0;
    /** Числа как их ждёт Bot API: канал с приставкой -100, группа со знаком. */
    public static final int BOT_API = 1;

    private NebulaIds() { }

    private static volatile Integer cached;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> { if (key == null || KEY.equals(key)) cached = null; };

    public static int format() {
        Integer value = cached;
        if (value != null) {
            return value;
        }
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
            synchronized (NebulaIds.class) {
                if (cached == null) {
                    prefs.registerOnSharedPreferenceChangeListener(listener);
                    cached = Math.max(TELEGRAM, Math.min(BOT_API, prefs.getInt(KEY, TELEGRAM)));
                }
                return cached;
            }
        } catch (Throwable e) {
            return TELEGRAM;
        }
    }

    public static void setFormat(int value) {
        int clamped = Math.max(TELEGRAM, Math.min(BOT_API, value));
        cached = clamped;
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .edit().putInt(KEY, clamped).apply();
    }

    /**
     * Идентификатор пользователя. Формат на него не влияет — он один и тот же,
     * — но метод существует, чтобы вызывающему не приходилось об этом помнить.
     */
    public static String user(long id) {
        return Long.toString(id);
    }

    /** Идентификатор канала или супергруппы, в котором Bot API ставит -100. */
    public static String channel(long id) {
        long value = Math.abs(id);
        return format() == BOT_API ? "-100" + value : Long.toString(value);
    }

    /** Идентификатор обычной группы: Bot API отличает её только знаком. */
    public static String group(long id) {
        long value = Math.abs(id);
        return format() == BOT_API ? "-" + value : Long.toString(value);
    }

    /** Название выбранного формата для строки настроек. */
    public static String title() {
        return format() == BOT_API
                ? NebulaText.text("Bot API", "Bot API")
                : NebulaText.text("Telegram", "Telegram");
    }
}
