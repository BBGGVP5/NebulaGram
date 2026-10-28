package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Вид поля ввода: как у Telegram, островом или только кнопками.
 *
 * <p>Раскладкой по-прежнему заведует {@link NebulaAppearance#iosComposer()}:
 * она решает, стоит ли поле отдельной капсулой с круглыми кнопками по краям.
 * Третий вариант — та же раскладка, но без подложки под самим полем: стекло
 * остаётся только под кнопками.
 *
 * <p>Сделано надстройкой, а не третьим значением той же настройки, потому что
 * раскладку спрашивают из двенадцати мест серии патчей. Превращать её в
 * перечисление значило бы переписать их все ради разницы, которая видна лишь
 * в одном — там, где рисуются подложки.
 */
public final class NebulaComposer {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "composer_buttons_only";

    /** Как у Telegram: сплошная полоса ввода. */
    public static final int TELEGRAM = 0;
    /** Остров: поле капсулой, кнопки кружками. */
    public static final int EXPRESSIVE = 1;
    /** Та же раскладка, но фон только под кнопками. */
    public static final int BUTTONS = 2;

    private NebulaComposer() { }

    public static int style() {
        if (!NebulaAppearance.iosComposer()) return TELEGRAM;
        return prefs().getBoolean(KEY, false) ? BUTTONS : EXPRESSIVE;
    }

    public static void setStyle(int style) {
        NebulaAppearance.setIosComposer(style != TELEGRAM);
        prefs().edit().putBoolean(KEY, style == BUTTONS).apply();
    }

    /** Нужна ли подложка под самим полем ввода. */
    public static boolean fieldSurface() {
        return style() != BUTTONS;
    }

    public static String title() {
        switch (style()) {
            case EXPRESSIVE: return NebulaText.text("Expressive", "Expressive");
            case BUTTONS: return NebulaText.text("Кнопки", "Buttons");
            default: return "Telegram";
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
