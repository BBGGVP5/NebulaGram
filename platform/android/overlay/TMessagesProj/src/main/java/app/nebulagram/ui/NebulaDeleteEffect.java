package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Как исчезает удалённое сообщение: просто пропадает или рассыпается в пыль.
 *
 * <p>Само распыление у Telegram уже есть — писать его не нужно. Оно закрыто
 * флагом экономии энергии {@code LiteMode.FLAG_CHAT_THANOS}, который живёт в
 * чужом экране и выключается заодно с десятком других анимаций.
 *
 * <p>Поэтому «Пыль» отменяет только этот флаг, а не проверку устройства:
 * эффект считается на GPU, и на железе, которое его не тянет, включать нечего.
 * «Нет» работает в обе стороны и гасит эффект, даже когда Telegram его
 * разрешает.
 */
public final class NebulaDeleteEffect {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "delete_effect";

    /** Сообщение просто пропадает. */
    public static final int NONE = 0;
    /** Рассыпается в пыль. */
    public static final int DUST = 1;

    private NebulaDeleteEffect() { }

    public static int style() {
        return prefs().getInt(KEY, DUST) == NONE ? NONE : DUST;
    }

    public static void setStyle(int value) {
        prefs().edit().putInt(KEY, value == NONE ? NONE : DUST).apply();
    }

    /**
     * Решение вместо флага экономии энергии.
     *
     * @param liteModeAllows что сказал бы Telegram сам по себе
     */
    public static boolean dust(boolean liteModeAllows) {
        switch (style()) {
            case NONE: return false;
            case DUST: return true;
            default: return liteModeAllows;
        }
    }

    public static String title() {
        return style() == NONE ? NebulaText.text("Нет", "None")
                : NebulaText.text("Пыль", "Dust");
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
