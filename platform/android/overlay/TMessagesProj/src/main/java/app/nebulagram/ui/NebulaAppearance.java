package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/** Presentation preferences, independent from Telegram's messaging settings. */
public final class NebulaAppearance {
    private NebulaAppearance() {}

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }

    public static boolean iosComposer() {
        return preferences().getBoolean("ios_composer", true);
    }

    public static void setIosComposer(boolean enabled) {
        preferences().edit().putBoolean("ios_composer", enabled).apply();
    }

    public static boolean chatHeader() {
        return preferences().getBoolean("floating_chat_header", true);
    }

    public static void setChatHeader(boolean enabled) {
        preferences().edit().putBoolean("floating_chat_header", enabled).apply();
    }

    public static boolean profileStyle() {
        return preferences().getBoolean("profile_style", true);
    }

    public static void setProfileStyle(boolean enabled) {
        preferences().edit().putBoolean("profile_style", enabled).apply();
    }

    /**
     * Прятать ли живую камеру в меню вложений.
     *
     * <p>Она включает камеру при каждом открытии меню — это заметно по расходу
     * батареи и по индикатору доступа к камере в статусной строке. По
     * умолчанию оставляем как в Telegram: скрытие меняет привычное поведение.
     */
    public static boolean hideAttachCamera() {
        return preferences().getBoolean("hide_attach_camera", false);
    }

    public static void setHideAttachCamera(boolean enabled) {
        preferences().edit().putBoolean("hide_attach_camera", enabled).apply();
    }
}
