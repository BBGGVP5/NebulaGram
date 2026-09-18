package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.UserConfig;

/**
 * Значок в профиле для тех, кто поддержал проект.
 *
 * <p>Проверять пожертвование клиенту нечем: сервера у форка нет, и любая
 * «проверка» на устройстве — это переключатель, который может нажать кто
 * угодно. Поэтому честно так и сделано: значок включает сам человек, и включает
 * его себе. Никому, кроме владельца устройства, он не виден — ни собеседнику,
 * ни серверу, — и ничьё чужое имя он не украшает.
 *
 * <p>Значок дописывается к имени эмодзи-спаном, а не занимает место справа от
 * имени: там живут отметка проверенного аккаунта и эмодзи-статус Telegram, и
 * отнимать у них место значило бы прятать то, что показывает сам Telegram.
 */
public final class NebulaDonation {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "donation_badge";
    private static final String KEY_ICON = "donation_badge_icon";
    private static final String KEY_LINK = "donation_link";

    /** Значок по умолчанию. */
    public static final String DEFAULT_ICON = "💎";

    private NebulaDonation() { }

    public static boolean enabled() {
        return prefs().getBoolean(KEY, false);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
    }

    public static String icon() {
        String value = prefs().getString(KEY_ICON, DEFAULT_ICON);
        return value == null || value.trim().isEmpty() ? DEFAULT_ICON : value;
    }

    public static void setIcon(String value) {
        if (value == null || value.trim().isEmpty()) {
            prefs().edit().remove(KEY_ICON).apply();
            return;
        }
        prefs().edit().putString(KEY_ICON, value.trim()).apply();
    }

    /** Куда ведёт строка «Поддержать проект». Пусто — строка ничего не открывает. */
    public static String link() {
        return prefs().getString(KEY_LINK, "");
    }

    public static void setLink(String value) {
        prefs().edit().putString(KEY_LINK, value == null ? "" : value.trim()).apply();
    }

    /** Свой ли это профиль: значок ставится только на него. */
    public static boolean owns(long id) {
        return id != 0 && id == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    /**
     * Имя со значком. Возвращает исходное имя, если значок выключен или профиль
     * чужой, — вызывающему не нужно об этом помнить.
     */
    public static CharSequence decorate(CharSequence name, long id, TextPaint paint) {
        if (name == null || !enabled() || !owns(id)) {
            return name;
        }
        CharSequence badge = Emoji.replaceEmoji(icon(),
                paint == null ? null : paint.getFontMetricsInt(), false);
        return new SpannableStringBuilder(name).append(" ").append(badge);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
