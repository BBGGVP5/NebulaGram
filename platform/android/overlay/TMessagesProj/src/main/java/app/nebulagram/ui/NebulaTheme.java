package app.nebulagram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;

/**
 * Material 3 colour roles for NebulaGram's own screens.
 *
 * <p>On Android 12 and later the palette is the system one: the framework
 * already derives Material You tonal ranges from the wallpaper and publishes
 * them as {@code android.R.color.system_*}, so we get dynamic colour without a
 * single extra dependency — no Material Components, no Compose, nothing that
 * would have to be kept in step with an upstream Telegram release. Older
 * devices fall back to the brand palette.
 *
 * <p>Only our screens read this. Telegram's own screens keep their own theme;
 * see docs/DESIGN.md for how the two layers relate.
 */
public final class NebulaTheme {

    // Brand fallback for devices without dynamic colour, in Material 3 tones.
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
    private static final int BRAND_SURFACE_LIGHT = 0xFFF7F9FF;
    private static final int BRAND_SURFACE_CONTAINER_LIGHT = 0xFFECEFF6;
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
    private final Context context;

    private NebulaTheme(Context context, boolean dark) {
        this.context = context;
        this.dark = dark;
    }

    /**
     * Resolves the palette for the current configuration. Cheap enough to call
     * from a view's constructor; nothing is cached because the wallpaper, and
     * therefore the palette, can change while the app is running.
     */
    public static NebulaTheme of(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return new NebulaTheme(context, mode == Configuration.UI_MODE_NIGHT_YES);
    }

    public boolean isDark() {
        return dark;
    }

    /** Whether the palette came from the system rather than the fallback. */
    public boolean isDynamic() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public int primary() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        }
        return dark ? BRAND_PRIMARY_DARK : BRAND_PRIMARY_LIGHT;
    }

    public int onPrimary() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        }
        return dark ? BRAND_ON_PRIMARY_DARK : BRAND_ON_PRIMARY_LIGHT;
    }

    public int primaryContainer() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
        }
        return dark ? BRAND_PRIMARY_CONTAINER_DARK : BRAND_PRIMARY_CONTAINER_LIGHT;
    }

    public int onPrimaryContainer() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_accent1_100 : android.R.color.system_accent1_900);
        }
        return dark ? BRAND_ON_PRIMARY_CONTAINER_DARK : BRAND_ON_PRIMARY_CONTAINER_LIGHT;
    }

    public int surface() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
        }
        return dark ? BRAND_SURFACE_DARK : BRAND_SURFACE_LIGHT;
    }

    public int surfaceContainer() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_100);
        }
        return dark ? BRAND_SURFACE_CONTAINER_DARK : BRAND_SURFACE_CONTAINER_LIGHT;
    }

    public int onSurface() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900);
        }
        return dark ? BRAND_ON_SURFACE_DARK : BRAND_ON_SURFACE_LIGHT;
    }

    public int onSurfaceVariant() {
        if (isDynamic()) {
            return system(dark ? android.R.color.system_neutral2_200 : android.R.color.system_neutral2_700);
        }
        return dark ? BRAND_ON_SURFACE_VARIANT_DARK : BRAND_ON_SURFACE_VARIANT_LIGHT;
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
        return dark ? 0xFF7FA6E0 : 0xFF08409B;
    }

    public int outline() {
        if (isDynamic()) {
            return system(android.R.color.system_neutral2_500);
        }
        return dark ? BRAND_OUTLINE_DARK : BRAND_OUTLINE_LIGHT;
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
