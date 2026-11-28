package app.nebulagram.ui;

import android.content.Context;
import org.telegram.messenger.ApplicationLoader;

public final class NebulaMonetPalette {
    private NebulaMonetPalette() { }
    public static int selected() { return Math.max(0, Math.min(3, ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getInt("monet_palette", 0))); }
    public static void select(int value) { ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).edit().putInt("monet_palette", Math.max(0, Math.min(3, value))).apply(); }
    public static String[] names() { return new String[]{NebulaText.text("Основная", "Primary"), NebulaText.text("Мягкая", "Secondary"), NebulaText.text("Дополнительная", "Tertiary"), NebulaText.text("Нейтральная", "Neutral")}; }
    public static int resource(Context context, int original) {
        if (android.os.Build.VERSION.SDK_INT < 31 || selected() == 0) return original;
        String name = context.getResources().getResourceEntryName(original);
        if (!name.startsWith("system_accent1_")) return original;
        String family = selected() == 1 ? "system_accent2_" : selected() == 2 ? "system_accent3_" : "system_neutral1_";
        int mapped = context.getResources().getIdentifier(family + name.substring("system_accent1_".length()), "color", "android");
        return mapped == 0 ? original : mapped;
    }
}
