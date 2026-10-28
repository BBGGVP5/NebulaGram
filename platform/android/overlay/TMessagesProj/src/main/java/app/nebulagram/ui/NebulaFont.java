package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;

/**
 * Чей шрифт у приложения: собственный Telegram или системный.
 *
 * <p>Подмена делается в одном месте — {@code AndroidUtilities.getTypeface},
 * через которое Telegram берёт все начертания. Поэтому настройка охватывает
 * весь интерфейс сразу, а не те экраны, до которых дошли руки.
 *
 * <p>Начертание восстанавливается по имени файла: его же читает и сам
 * Telegram, когда собирает свой шрифт. Моноширинные остаются моноширинными —
 * ими набран код в сообщениях, и подставить туда обычный шрифт значит сломать
 * выравнивание, ради которого их и выбирают.
 */
public final class NebulaFont {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "font_system";

    private NebulaFont() { }

    public static boolean system() {
        return prefs().getBoolean(KEY, false);
    }

    public static void setSystem(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
    }

    public static String title() {
        return system() ? NebulaText.text("Системный", "System")
                : NebulaText.text("Telegram", "Telegram");
    }

    /**
     * Чем заменить начертание из ресурсов, либо {@code null}, если заменять не
     * нужно и Telegram должен взять своё.
     */
    public static Typeface substitute(String assetPath) {
        if (assetPath == null || !system()) return null;
        try {
            final boolean italic = assetPath.contains("italic");
            if (assetPath.contains("mono")) {
                return Typeface.create(Typeface.MONOSPACE, italic ? Typeface.ITALIC : Typeface.NORMAL);
            }
            final int weight = assetPath.contains("rextrabold") ? 800
                    : assetPath.contains("medium") || assetPath.contains("rbold") ? 700 : 400;
            if (Build.VERSION.SDK_INT >= 28) {
                return Typeface.create(Typeface.DEFAULT, weight, italic);
            }
            final boolean bold = weight >= 700;
            return Typeface.create(Typeface.DEFAULT,
                    bold && italic ? Typeface.BOLD_ITALIC
                            : bold ? Typeface.BOLD : italic ? Typeface.ITALIC : Typeface.NORMAL);
        } catch (Throwable ignored) {
            // Непредвиденное начертание — лучше оставить шрифт Telegram, чем
            // уронить экран ради настройки оформления.
            return null;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
