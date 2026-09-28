package app.nebulagram.ui;

import android.content.Context;
import android.content.ComponentCallbacks;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/** Settings colors follow the active Telegram theme; Material You remains opt-in. */
public final class NebulaTheme {

    // Fallbacks used before Telegram has loaded its active theme.
    private static final int BRAND_PRIMARY_DARK = 0xFFA8C7FA;
    private static final int BRAND_ON_PRIMARY_DARK = 0xFF062E6F;
    private static final int BRAND_PRIMARY_CONTAINER_DARK = 0xFF0842A0;
    private static final int BRAND_ON_PRIMARY_CONTAINER_DARK = 0xFFD3E3FD;
    private static final int BRAND_SURFACE_DARK = 0xFF101318;
    private static final int BRAND_SURFACE_CONTAINER_DARK = 0xFF1B1F26;
    private static final int BRAND_ON_SURFACE_DARK = 0xFFE1E2E8;
    private static final int BRAND_ON_SURFACE_VARIANT_DARK = 0xFFA6ABB7;
    private static final int BRAND_OUTLINE_DARK = 0xFF3A3F48;

    private static final int BRAND_PRIMARY_LIGHT = 0xFF0B57D0;
    private static final int BRAND_ON_PRIMARY_LIGHT = 0xFFFFFFFF;
    private static final int BRAND_PRIMARY_CONTAINER_LIGHT = 0xFFD3E3FD;
    private static final int BRAND_ON_PRIMARY_CONTAINER_LIGHT = 0xFF041E49;
    private static final int BRAND_SURFACE_LIGHT = 0xFFF2F3F5;
    private static final int BRAND_SURFACE_CONTAINER_LIGHT = 0xFFFFFFFF;
    private static final int BRAND_ON_SURFACE_LIGHT = 0xFF191C20;
    private static final int BRAND_ON_SURFACE_VARIANT_LIGHT = 0xFF43474E;
    private static final int BRAND_OUTLINE_LIGHT = 0xFF74777F;

    /** Material 3 shape scale, in pixels. */
    public static int cornerSmall() {
        return AndroidUtilities.dp(12);
    }

    public static int cornerMedium() {
        return AndroidUtilities.dp(16);
    }

    /** Full-height corner of a 52dp button, the M3 "full" shape. */
    public static int cornerFull() {
        return AndroidUtilities.dp(26);
    }

    private final boolean dark;
    private final boolean dynamic;
    private final Context context;

    private NebulaTheme(Context context, boolean dark, boolean dynamic) {
        this.context = context;
        this.dark = dark;
        this.dynamic = dynamic;
    }

    private static volatile NebulaTheme cached;

    public static NebulaTheme of(Context context) {
        NebulaTheme theme = cached;
        if (theme != null) return theme;
        Context application = context == null ? ApplicationLoader.applicationContext : context.getApplicationContext();
        return initialize(application);
    }

    private static synchronized NebulaTheme initialize(Context application) {
        if (cached != null) return cached;
        if (application == null) return new NebulaTheme(null, false, false);
        cached = fixedPalette(application, application.getResources().getConfiguration());
        application.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration configuration) {
                cached = fixedPalette(application, configuration);
            }

            @Override
            public void onLowMemory() { }
        });
        return cached;
    }

    private static NebulaTheme fixedPalette(Context application, Configuration configuration) {
        boolean dark = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new NebulaTheme(application, dark, false);
    }

    public boolean isDark() {
        return Theme.getActiveTheme() == null ? dark : Theme.isCurrentThemeDark();
    }

    /** Connection success stays green regardless of the wallpaper accent. */
    public int success() {
        return isDark() ? 0xFF81D99A : 0xFF236C3D;
    }

    /** Whether Android can supply a wallpaper accent for Telegram. */
    public static boolean supportsDynamic() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public int primary() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        }
        return telegram(Theme.key_windowBackgroundWhiteBlueText, dark ? BRAND_PRIMARY_DARK : BRAND_PRIMARY_LIGHT);
    }

    public int onPrimary() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        }
        return ColorUtils.calculateLuminance(primary()) > .5f ? 0xFF101318 : 0xFFFFFFFF;
    }

    public int primaryContainer() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
        }
        return blend(surfaceContainer(), primary(), isDark() ? .23f : .14f);
    }

    public int onPrimaryContainer() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_100 : android.R.color.system_accent1_900);
        }
        return primary();
    }

    public int surface() {
        return telegram(Theme.key_windowBackgroundGray, isDark() ? BRAND_SURFACE_DARK : BRAND_SURFACE_LIGHT);
    }

    public int surfaceContainer() {
        return telegram(Theme.key_windowBackgroundWhite, isDark() ? BRAND_SURFACE_CONTAINER_DARK : BRAND_SURFACE_CONTAINER_LIGHT);
    }

    public int onSurface() {
        return telegram(Theme.key_windowBackgroundWhiteBlackText, isDark() ? BRAND_ON_SURFACE_DARK : BRAND_ON_SURFACE_LIGHT);
    }

    public int onSurfaceVariant() {
        return telegram(Theme.key_windowBackgroundWhiteGrayText2, isDark() ? BRAND_ON_SURFACE_VARIANT_DARK : BRAND_ON_SURFACE_VARIANT_LIGHT);
    }

    /**
     * The deeper of the two tones the app mark is drawn in. A darker tone of
     * the same hue, not the primary at reduced opacity: transparency would let
     * the surface show through and make the fold look washed out.
     */
    public int primaryShade() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_400 : android.R.color.system_accent1_800);
        }
        return blend(primary(), onSurface(), isDark() ? .18f : .12f);
    }

    public int outline() {
        return telegram(Theme.key_divider, isDark() ? BRAND_OUTLINE_DARK : BRAND_OUTLINE_LIGHT);
    }

    private int telegram(int key, int fallback) {
        return Theme.getActiveTheme() == null ? fallback : Theme.getColor(key);
    }

    private static int blend(int background, int foreground, float amount) {
        int inverse = Math.round((1f - amount) * 255);
        int weight = 255 - inverse;
        int red = ((background >> 16 & 255) * inverse + (foreground >> 16 & 255) * weight) / 255;
        int green = ((background >> 8 & 255) * inverse + (foreground >> 8 & 255) * weight) / 255;
        int blue = ((background & 255) * inverse + (foreground & 255) * weight) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /**
     * Hands the wallpaper's accent to Telegram's own theming, so the whole app
     * follows Material You — including the screens we do not own, such as the
     * login flow.
     *
     * <p>This is deliberately not a restyling of individual screens. Telegram
     * derives its entire palette from one accent colour, and setting that
     * accent is a supported, public path — the same one its own appearance
     * settings use. It therefore survives upstream releases, whereas hand-drawn
     * replacements of Telegram's screens would not.
     */
    private static boolean applying;

    public static void applyMaterialYou(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !materialYouEnabled()) {
            return; // нечего брать или пользователь отказался
        }
        // Guard synchronous callbacks. Queued callbacks stop at the accent equality
        // check below, so a second theme loaded during startup is never throttled away.
        if (applying) return;
        try {
            applying = true;
            // This opt-in affects Telegram, never the independent settings palette.
            Context application = context == null ? ApplicationLoader.applicationContext : context.getApplicationContext();
            int accent = new NebulaTheme(application == null ? context : application,
                    Theme.isCurrentThemeDark(), true).primary();
            Theme.ThemeInfo active = Theme.getActiveTheme();
            if (active == null) {
                return;
            }
            Theme.ThemeAccent current = active.getAccent(false);
            if (current == null || current.accentColor == accent) {
                return; // nothing to do, or already following the wallpaper
            }
            // Запоминаем, каким акцент был до нас: иначе выключить Material You
            // невозможно — цвет обоев уже сохранён в теме, и возвращать нечего.
            SharedPreferences prefs =
                    ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
            String backup = accentBackupKey(active, current);
            if (!prefs.contains(backup)) {
                prefs.edit().putInt(backup, prefs.getInt(KEY_SAVED_ACCENT, current.accentColor))
                        .remove(KEY_SAVED_ACCENT).apply();
            }
            current.accentColor = accent;
            // Сохраняем, а не только держим в памяти: при запуске из
            // уведомления Telegram применяет свою тему раньше, чем до нас
            // доходит очередь, и несохранённый акцент терялся.
            Theme.saveThemeAccents(active, true, false, false, false);
            Theme.refreshThemeColors();
        } catch (Throwable e) {
            // Theming is cosmetic: a failure here must never keep the app from
            // starting, and the upstream API may move between releases.
            FileLog.e(e);
        } finally {
            applying = false;
        }
    }

    private static final String PREFS = "nebulagram";
    private static final String KEY_MATERIAL_YOU = "material_you";
    private static final String KEY_SAVED_ACCENT = "accent_before_material_you";

    /**
     * Следовать ли палитре обоев. По умолчанию да — это и есть Material You.
     *
     * <p>Значение держим в памяти: {@link #of(Context)} зовут в том числе из
     * onDraw, а там это был поход в SharedPreferences на каждый кадр.
     * SharedPreferences держит слушателей слабо, поэтому ссылка на него —
     * своя, и она не удерживает ни View, ни Activity.
     */
    private static volatile Boolean materialYou;
    private static final SharedPreferences.OnSharedPreferenceChangeListener materialYouListener =
            (preferences, key) -> { if (key == null || KEY_MATERIAL_YOU.equals(key)) { materialYou = null; } };

    public static boolean materialYouEnabled() {
        Boolean value = materialYou;
        if (value != null) {
            return value;
        }
        try {
            SharedPreferences prefs =
                    ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
            synchronized (NebulaTheme.class) {
                if (materialYou == null) {
                    prefs.registerOnSharedPreferenceChangeListener(materialYouListener);
                    materialYou = prefs.getBoolean(KEY_MATERIAL_YOU, true);
                }
                return materialYou;
            }
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setMaterialYouEnabled(boolean value) {
        SharedPreferences prefs =
                ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
        prefs.edit().putBoolean(KEY_MATERIAL_YOU, value).apply();
        materialYou = value;
        if (!value) {
            restoreAccent(prefs);
        }
    }

    /**
     * Возвращает акцент, который стоял до Material You.
     *
     * <p>Без этого переключатель выглядел сломанным: он честно переставал
     * красить, но цвет обоев оставался — Telegram хранит акцент в теме, а не
     * пересчитывает его на каждом запуске.
     */
    private static void restoreAccent(SharedPreferences prefs) {
        try {
            applying = true;
            Theme.ThemeInfo active = Theme.getActiveTheme();
            // Older versions kept one backup. Associate it with the active accent once.
            if (active != null && active.getAccent(false) != null && prefs.contains(KEY_SAVED_ACCENT)) {
                String key = accentBackupKey(active, active.getAccent(false));
                prefs.edit().putInt(key, prefs.getInt(key, prefs.getInt(KEY_SAVED_ACCENT, 0)))
                        .remove(KEY_SAVED_ACCENT).apply();
            }
            if (Theme.themes != null) {
                for (Theme.ThemeInfo info : Theme.themes) restoreThemeAccents(prefs, info);
            }
            if (active != null) restoreThemeAccents(prefs, active);
            Theme.refreshThemeColors();
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            applying = false;
        }
    }

    private static String accentBackupKey(Theme.ThemeInfo info, Theme.ThemeAccent accent) {
        return KEY_SAVED_ACCENT + ":" + info.getKey() + ":" + accent.id;
    }

    private static void restoreThemeAccents(SharedPreferences prefs, Theme.ThemeInfo info) {
        if (info.themeAccents == null) return;
        java.util.ArrayList<String> restored = new java.util.ArrayList<>();
        for (Theme.ThemeAccent accent : info.themeAccents) {
            String key = accentBackupKey(info, accent);
            if (prefs.contains(key)) {
                accent.accentColor = prefs.getInt(key, accent.accentColor);
                restored.add(key);
            }
        }
        if (restored.isEmpty()) return;
        Theme.saveThemeAccents(info, true, false, false, false);
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : restored) editor.remove(key);
        editor.apply();
    }

    /**
     * Material 3 state layer: the tint a pressed or focused surface takes,
     * expressed as the content colour at a low opacity.
     */
    public static int stateLayer(int color, float opacity) {
        int alpha = Math.round(255 * opacity);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // Context.getColor arrived in API 23 and Telegram still supports 21, so the
    // compat call is not decoration even though dynamic colour itself needs 31.
    private int system(int resource) {
        return ContextCompat.getColor(context, resource);
    }
}
