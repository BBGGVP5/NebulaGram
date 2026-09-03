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

    /**
     * Прятать ли кнопку выбора отправителя. Она нужна тем, кто пишет от имени
     * канала; всем остальным занимает место слева от поля ввода.
     */
    public static boolean hideSendAs() {
        return preferences().getBoolean("hide_send_as", false);
    }

    public static void setHideSendAs(boolean enabled) {
        preferences().edit().putBoolean("hide_send_as", enabled).apply();
    }

    /**
     * Отключать ли переход к следующему каналу протягиванием вверх. Жест
     * срабатывает при обычной прокрутке к концу ленты и уводит из чата, чего
     * от прокрутки не ждёшь.
     */
    public static boolean disableNextChannel() {
        return preferences().getBoolean("disable_next_channel", false);
    }

    public static void setDisableNextChannel(boolean enabled) {
        preferences().edit().putBoolean("disable_next_channel", enabled).apply();
    }

    /**
     * Убирать ли линии между чатами. В Material 3 списки разделяют отступом и
     * плотностью, а не чертой; линия остаётся полезной там, где строки
     * одинаковые по высоте, поэтому это выбор, а не умолчание.
     */
    public static boolean hideDividers() {
        return preferences().getBoolean("hide_dividers", false);
    }

    public static void setHideDividers(boolean enabled) {
        preferences().edit().putBoolean("hide_dividers", enabled).apply();
    }

    /**
     * Прятать ли счётчики непрочитанного на вкладках папок. Цифры на каждой
     * вкладке превращают ряд папок в пёструю ленту; сами непрочитанные при
     * этом никуда не деваются и видны в списке чатов.
     */
    public static boolean hideTabCounters() {
        return preferences().getBoolean("hide_tab_counters", false);
    }

    public static void setHideTabCounters(boolean enabled) {
        preferences().edit().putBoolean("hide_tab_counters", enabled).apply();
    }

    /**
     * Прятать ли поле поиска в шапке списка чатов. Освобождает строку экрана;
     * поиск остаётся доступен со своей вкладки и из меню.
     */
    public static boolean hideSearchField() {
        return preferences().getBoolean("hide_search_field", false);
    }

    public static void setHideSearchField(boolean enabled) {
        preferences().edit().putBoolean("hide_search_field", enabled).apply();
    }

    // --- флаги самого Telegram ----------------------------------------------
    //
    // Эти настройки Telegram читает при запуске из "mainconfig" и держит в
    // статических полях SharedConfig. Своего экрана у части из них нет, хотя
    // код им пользуется. Пишем в тот же файл и обновляем поле, чтобы новое
    // значение действовало сразу, а не только после перезапуска.

    private static android.content.SharedPreferences main() {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings();
    }

    public static boolean systemEmoji() {
        return org.telegram.messenger.SharedConfig.useSystemEmoji;
    }

    public static void setSystemEmoji(boolean enabled) {
        org.telegram.messenger.SharedConfig.useSystemEmoji = enabled;
        main().edit().putBoolean("useSystemEmoji", enabled).apply();
    }

    public static boolean systemBoldFont() {
        return org.telegram.messenger.SharedConfig.useSystemBoldFont;
    }

    public static void setSystemBoldFont(boolean enabled) {
        org.telegram.messenger.SharedConfig.useSystemBoldFont = enabled;
        main().edit().putBoolean("useSystemBoldFont", enabled).apply();
    }
}
