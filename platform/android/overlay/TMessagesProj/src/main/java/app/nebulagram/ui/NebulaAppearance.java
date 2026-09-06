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
        return preferences().getBoolean("floating_chat_header_v2", false);
    }

    public static void setChatHeader(boolean enabled) {
        preferences().edit().putBoolean("floating_chat_header_v2", enabled).apply();
    }

    public static boolean centeredHeader() { return preferences().getBoolean("centered_chat_header", true); }
    public static void setCenteredHeader(boolean value) { preferences().edit().putBoolean("centered_chat_header", value).apply(); }
    public static boolean adaptiveHeader() { return preferences().getBoolean("adaptive_chat_header", true); }
    public static void setAdaptiveHeader(boolean value) { preferences().edit().putBoolean("adaptive_chat_header", value).apply(); }

    /** Replace the home title with the selected folder name or emoji. */
    public static boolean folderTitle() {
        return preferences().getBoolean("folder_title", true);
    }

    public static void setFolderTitle(boolean enabled) {
        preferences().edit().putBoolean("folder_title", enabled).apply();
    }

    public static boolean profileStyle() {
        return preferences().getBoolean("profile_style", true);
    }

    public static void setProfileStyle(boolean enabled) {
        preferences().edit().putBoolean("profile_style", enabled).apply();
    }

    /** Show a darkened cover made from the group's existing avatar, when it has one. */
    public static boolean profilePhotoBanner() {
        return preferences().getBoolean("profile_photo_banner", true);
    }

    public static void setProfilePhotoBanner(boolean enabled) {
        preferences().edit().putBoolean("profile_photo_banner", enabled).apply();
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

    /**
     * Показывать ли секунды у времени сообщения. Telegram уже умеет такой
     * формат — им пользуются экраны, где важна точность, — но переключателя
     * для чатов нет.
     */
    public static boolean secondsInTime() {
        return preferences().getBoolean("seconds_in_time", false);
    }

    public static void setSecondsInTime(boolean enabled) {
        preferences().edit().putBoolean("seconds_in_time", enabled).apply();
    }
    public static boolean headerUnread() { return preferences().getBoolean("header_unread", false); }
    public static void setHeaderUnread(boolean value) { preferences().edit().putBoolean("header_unread", value).apply(); }
    public static boolean iosUnread() { return preferences().getBoolean("ios_unread", true); }
    public static void setIosUnread(boolean value) { preferences().edit().putBoolean("ios_unread", value).apply(); }
    public static boolean glassHighlights() { return preferences().getBoolean("glass_highlights", true); }
    public static void setGlassHighlights(boolean value) { preferences().edit().putBoolean("glass_highlights", value).apply(); }
    public static boolean messageMenuBlur() { return preferences().getBoolean("message_menu_blur", false); }
    public static void setMessageMenuBlur(boolean value) { preferences().edit().putBoolean("message_menu_blur", value).apply(); }
    public static boolean messageMenuBelow() { return preferences().getBoolean("message_menu_below", false); }
    public static void setMessageMenuBelow(boolean value) { preferences().edit().putBoolean("message_menu_below", value).apply(); }
    public static boolean hideAllChats() { return preferences().getBoolean("hide_all_chats", false); }
    public static void setHideAllChats(boolean value) { preferences().edit().putBoolean("hide_all_chats", value).apply(); }
    public static boolean folderOutline() { return preferences().getBoolean("folder_outline", false); }
    public static void setFolderOutline(boolean value) { preferences().edit().putBoolean("folder_outline", value).apply(); }
    public static boolean replyBackground() { return preferences().getBoolean("reply_background", true); }
    public static void setReplyBackground(boolean value) { preferences().edit().putBoolean("reply_background", value).apply(); }
    public static boolean replyColors() { return preferences().getBoolean("reply_colors", true); }
    public static void setReplyColors(boolean value) { preferences().edit().putBoolean("reply_colors", value).apply(); }
    public static boolean replyEmoji() { return preferences().getBoolean("reply_emoji", true); }
    public static void setReplyEmoji(boolean value) { preferences().edit().putBoolean("reply_emoji", value).apply(); }
    public static boolean profileChannel() { return preferences().getBoolean("profile_channel", true); }
    public static void setProfileChannel(boolean value) { preferences().edit().putBoolean("profile_channel", value).apply(); }
    public static boolean profileBirthday() { return preferences().getBoolean("profile_birthday", true); }
    public static void setProfileBirthday(boolean value) { preferences().edit().putBoolean("profile_birthday", value).apply(); }
    public static boolean profileBusiness() { return preferences().getBoolean("profile_business", true); }
    public static void setProfileBusiness(boolean value) { preferences().edit().putBoolean("profile_business", value).apply(); }
    public static boolean profileBackground() { return preferences().getBoolean("profile_background", true); }
    public static void setProfileBackground(boolean value) { preferences().edit().putBoolean("profile_background", value).apply(); }
    public static boolean profileEmoji() { return preferences().getBoolean("profile_emoji", true); }
    public static void setProfileEmoji(boolean value) { preferences().edit().putBoolean("profile_emoji", value).apply(); }
    public static boolean hidePremiumStatus() { return preferences().getBoolean("hide_premium_status", false); }
    public static void setHidePremiumStatus(boolean value) { preferences().edit().putBoolean("hide_premium_status", value).apply(); }
    public static boolean menuCall() { return preferences().getBoolean("menu_call", true); }
    public static void setMenuCall(boolean value) { preferences().edit().putBoolean("menu_call", value).apply(); }
    public static boolean menuVideo() { return preferences().getBoolean("menu_video", true); }
    public static void setMenuVideo(boolean value) { preferences().edit().putBoolean("menu_video", value).apply(); }
    public static boolean menuSearch() { return preferences().getBoolean("menu_search", true); }
    public static void setMenuSearch(boolean value) { preferences().edit().putBoolean("menu_search", value).apply(); }
    public static boolean menuMute() { return preferences().getBoolean("menu_mute", true); }
    public static void setMenuMute(boolean value) { preferences().edit().putBoolean("menu_mute", value).apply(); }
    public static int switchStyle() { return Math.max(0, Math.min(3, preferences().getInt("switch_style", 0))); }
    public static void setSwitchStyle(int value) { preferences().edit().putInt("switch_style", Math.max(0, Math.min(3, value))).apply(); }
    public static int folderStyle() { return Math.max(0, Math.min(2, preferences().getInt("folder_style", 0))); }
    public static void setFolderStyle(int value) { preferences().edit().putInt("folder_style", Math.max(0, Math.min(2, value))).apply(); }
    public static boolean centerHome() { return preferences().getBoolean("center_home", false); }
    public static void setCenterHome(boolean value) { preferences().edit().putBoolean("center_home", value).apply(); }
    public static boolean liquidAnimations() { return preferences().getBoolean("liquid_animations", true); }
    public static void setLiquidAnimations(boolean value) { preferences().edit().putBoolean("liquid_animations", value).apply(); }
    public static boolean uniformAvatars() { return preferences().getBoolean("uniform_avatars", true); }
    public static void setUniformAvatars(boolean value) { preferences().edit().putBoolean("uniform_avatars", value).apply(); }
    public static int avatarRound() { return Math.max(0, Math.min(100, preferences().getInt("avatar_round", 100))); }
    public static void setAvatarRound(int value) { preferences().edit().putInt("avatar_round", value).apply(); }
    public static int ownDoubleTap() { return Math.max(0, Math.min(4, preferences().getInt("own_double_tap", 0))); }
    public static void setOwnDoubleTap(int value) { preferences().edit().putInt("own_double_tap", value).apply(); }
}
