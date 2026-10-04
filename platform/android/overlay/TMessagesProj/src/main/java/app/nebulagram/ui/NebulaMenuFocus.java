package app.nebulagram.ui;

import android.os.Build;
import android.view.View;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;

/** A small popup-only focus transition; effects are cached, never allocated per frame. */
public final class NebulaMenuFocus {
    private NebulaMenuFocus() { }
    @android.annotation.TargetApi(31)
    private static final class Effects {
        static final RenderEffect[] cached = new RenderEffect[49];
        static float density;
        static void apply(View view, int step) {
            float nextDensity = AndroidUtilities.dpf2(1);
            if (density != nextDensity) { java.util.Arrays.fill(cached, null); density = nextDensity; }
            if (step == 0) { view.setRenderEffect(null); return; }
            RenderEffect effect = cached[step];
            if (effect == null) cached[step] = effect = RenderEffect.createBlurEffect(
                    nextDensity * step * (2f / 3), nextDensity * step * (2f / 3), Shader.TileMode.CLAMP);
            view.setRenderEffect(effect);
        }
    }
    public static void apply(View view, int step) {
        if (Build.VERSION.SDK_INT < 31) return;
        boolean active = view.isHardwareAccelerated() && NebulaMenuStyle.animated() && !NebulaGlass.reduced()
                && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
        Effects.apply(view, active ? Math.max(0, Math.min(48, step)) : 0);
    }
    public static void clear(View view) { if (Build.VERSION.SDK_INT >= 31) Effects.apply(view, 0); }
}
