package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * С какой камеры открывается видеосообщение — «кружок».
 *
 * <p>У Telegram выбора нет: на старом Camera1 кружок всегда начинался с
 * фронтальной, на Camera2 — с той, что осталась с прошлого раза, и повлиять на
 * это было нельзя. Кто снимает кружки не себя, а происходящее вокруг, каждый раз
 * переворачивал камеру вручную.
 */
public final class NebulaRoundCamera {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "round_camera";
    private static final String KEY_LAST = "round_camera_last_front";

    /** Та, которой снимали в прошлый раз. */
    public static final int LAST = 0;
    /** Всегда фронтальная. */
    public static final int FRONT = 1;
    /** Всегда основная. */
    public static final int BACK = 2;

    private NebulaRoundCamera() { }

    public static int mode() {
        return Math.max(LAST, Math.min(BACK, prefs().getInt(KEY, LAST)));
    }

    public static void setMode(int value) {
        prefs().edit().putInt(KEY, Math.max(LAST, Math.min(BACK, value))).apply();
    }

    public static String title() {
        switch (mode()) {
            case FRONT: return NebulaText.text("Фронтальная", "Front");
            case BACK: return NebulaText.text("Основная", "Rear");
            default: return NebulaText.text("Как в прошлый раз", "Last used");
        }
    }

    /**
     * Какую камеру открывать. {@code current} — та, что стоит сейчас: для
     * режима «как в прошлый раз» ответ зависит от неё и от запомненного выбора.
     */
    public static boolean frontface(boolean current) {
        switch (mode()) {
            case FRONT: return true;
            case BACK: return false;
            default: return prefs().getBoolean(KEY_LAST, current);
        }
    }

    /** Запоминаем разворот камеры: только для режима «как в прошлый раз». */
    public static void remember(boolean frontface) {
        prefs().edit().putBoolean(KEY_LAST, frontface).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
