package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Ссылка «Поддержать проект».
 *
 * <p>Сам значок сюда больше не относится: его выдаёт опубликованный список,
 * см. {@link NebulaBadges}. Переключателя «показать мой значок» нет намеренно —
 * значок нужен для того, чтобы его видели другие, а собственный экран себе и
 * так никто не запрещал.
 */
public final class NebulaDonation {
    private static final String PREFS = "nebulagram";
    private static final String KEY_LINK = "donation_link";

    private NebulaDonation() { }

    /** Куда ведёт строка «Поддержать проект». Пусто — строка предложит задать ссылку. */
    public static String link() {
        return prefs().getString(KEY_LINK, "");
    }

    public static void setLink(String value) {
        prefs().edit().putString(KEY_LINK, value == null ? "" : value.trim()).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
