package app.nebulagram.ui;

import android.graphics.Canvas;
import android.os.PowerManager;
import android.content.Context;
import android.view.View;
import java.util.WeakHashMap;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.SnowflakesEffect;

/** Reuses Telegram's bounded particle effect; rendering stops offscreen or in power-saving mode. */
public final class NebulaSnow {
    private static final WeakHashMap<View, SnowflakesEffect> effects = new WeakHashMap<>();
    private NebulaSnow() { }
    public static void draw(View view, Canvas canvas) {
        if (!NebulaFeatureSettings.enabled("snowflakes") || !SharedConfig.animationsEnabled()
                || !view.isShown() || !view.isAttachedToWindow()) { effects.remove(view); return; }
        PowerManager power = (PowerManager) view.getContext().getSystemService(Context.POWER_SERVICE);
        if (power != null && power.isPowerSaveMode()) { effects.remove(view); return; }
        SnowflakesEffect effect = effects.get(view);
        if (effect == null) { effect = new SnowflakesEffect(0); effects.put(view, effect); }
        effect.onDraw(view, canvas);
    }
}
