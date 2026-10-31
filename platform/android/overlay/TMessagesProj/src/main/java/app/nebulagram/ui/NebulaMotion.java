package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Характер движения: спокойный или пружинистый.
 *
 * <p>Это не «включить анимации», а то, чем они кончаются. Expressive —
 * нынешнее поведение: лёгкий перелёт и возврат, как у iOS. Стандарт — то же
 * время, но затухание без перелёта, для тех, кому пружина мешает.
 *
 * <p>Сама кривая живёт в {@link NebulaMenuMotion} и настроек не читает: её
 * вызывают на каждом кадре каждого открытого меню, и она же компилируется в
 * проверочных фикстурах без Android вокруг. Поэтому значение сюда
 * проталкивается — {@link #apply()} на старте и при изменении.
 */
public final class NebulaMotion {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "motion_expressive";

    private NebulaMotion() { }

    public static boolean expressive() {
        return prefs().getBoolean(KEY, true);
    }

    public static void setExpressive(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
        NebulaMenuMotion.setExpressive(value);
    }

    /** Переносит сохранённый выбор в кривую. Вызывается при старте приложения. */
    public static void apply() {
        try {
            NebulaMenuMotion.setExpressive(expressive());
        } catch (Throwable ignored) {
            // Настройки могут быть недоступны раньше готовности контекста;
            // кривая остаётся на значении по умолчанию, и это верный выбор.
        }
    }

    public static String title() {
        return expressive() ? NebulaText.text("Expressive", "Expressive")
                : NebulaText.text("Стандарт", "Standard");
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
