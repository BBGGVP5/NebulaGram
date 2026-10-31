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
    private static volatile Typeface custom;
    private static volatile boolean customLoaded;

    public static boolean customEnabled() { return prefs().getBoolean("font_custom", false); }
    public static boolean hasCustom() { return fontFile().exists(); }
    private static java.io.File fontFile() {
        return new java.io.File(ApplicationLoader.applicationContext.getFilesDir(), "nebula-custom-font.sfnt");
    }
    /** Validate in a temporary file before replacing the last working font. Run off UI. */
    public static synchronized void install(java.io.InputStream input) throws java.io.IOException {
        byte[] data = NebulaFontFile.read(input);
        java.io.File temporary = java.io.File.createTempFile("nebula-font-", ".sfnt", ApplicationLoader.applicationContext.getCacheDir());
        final Typeface checked;
        try {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(temporary)) { out.write(data); }
            try { checked = Typeface.createFromFile(temporary); }
            catch (RuntimeException error) { throw new java.io.IOException("Unreadable font", error); }
            android.util.AtomicFile store = new android.util.AtomicFile(fontFile());
            java.io.FileOutputStream out = null;
            try { out = store.startWrite(); out.write(data); store.finishWrite(out); }
            catch (java.io.IOException error) { if (out != null) store.failWrite(out); throw error; }
            try (java.io.InputStream stored = store.openRead()) {
                if (!java.util.Arrays.equals(data, NebulaFontFile.read(stored))) throw new java.io.IOException("Font installation did not complete");
            }
            custom = checked; customLoaded = true;
        } finally { temporary.delete(); }
    }
    public static void choose(int mode) {
        prefs().edit().putBoolean(KEY, mode == 1).putBoolean("font_custom", mode == 2 && hasCustom()).apply();
    }
    private static synchronized Typeface customTypeface() {
        if (!customLoaded) {
            customLoaded = true;
            try { custom = Typeface.createFromFile(fontFile()); } catch (RuntimeException ignored) { custom = null; }
        }
        return custom;
    }

    private NebulaFont() { }

    public static boolean system() {
        return prefs().getBoolean(KEY, false);
    }

    public static void setSystem(boolean value) {
        choose(value ? 1 : 0);
    }

    public static String title() {
        if (customEnabled() && hasCustom()) return NebulaText.text("Свой шрифт", "Custom font");
        return system() ? NebulaText.text("Системный", "System")
                : NebulaText.text("Telegram", "Telegram");
    }

    /**
     * Чем заменить начертание из ресурсов, либо {@code null}, если заменять не
     * нужно и Telegram должен взять своё.
     */
    public static Typeface substitute(String assetPath) {
        if (assetPath == null || (!system() && !customEnabled())) return null;
        try {
            final boolean italic = assetPath.contains("italic");
            if (assetPath.contains("mono")) {
                return Typeface.create(Typeface.MONOSPACE, italic ? Typeface.ITALIC : Typeface.NORMAL);
            }
            final int weight = assetPath.contains("rextrabold") ? 800
                    : assetPath.contains("medium") || assetPath.contains("rbold") ? 700 : 400;
            Typeface base = customEnabled() ? customTypeface() : Typeface.DEFAULT;
            if (base == null) return null;
            if (Build.VERSION.SDK_INT >= 28) {
                return Typeface.create(base, weight, italic);
            }
            final boolean bold = weight >= 700;
            return Typeface.create(base,
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
