package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.util.SparseArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

/** One backdrop for all profile actions; never captures the controls or the full window. */
@android.annotation.TargetApi(31)
final class NebulaProfileGlass {
    private final BlurredBackgroundSourceRenderNode source;
    private final BlurredBackgroundDrawableViewFactory factory;
    private final SparseArray<BlurredBackgroundDrawable> buttons = new SparseArray<>();
    private final BlurredBackgroundProvider material;

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 31 && NebulaMenuStyle.enabled()
                && !NebulaGlass.reduced() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
    }

    NebulaProfileGlass(Theme.ResourcesProvider provider) {
        BlurredBackgroundSourceColor fallback = new BlurredBackgroundSourceColor();
        fallback.setColor(Theme.getColor(Theme.key_windowBackgroundGray, provider));
        source = new BlurredBackgroundSourceRenderNode(fallback);
        factory = new BlurredBackgroundDrawableViewFactory(source);
        factory.setLiquidGlassEffectAllowed(NebulaMenuStyle.animated());
        // The photograph supplies colour; the neutral veil keeps the native white
        // labels legible. No stroke on either edge of the glass.
        material = new NebulaMenuStyle.Material(provider)
                .setBackgroundColor((r, dark) -> 0x52101010)
                .setStrokeColorTop(0, 0).setStrokeColorBottom(0, 0)
                .setStrokeWidth(0, 0).setShadowColor(0, 0).build();
    }

    Canvas begin(int width, int height) {
        source.setBlur(AndroidUtilities.dpf2(NebulaGlass.blur()));
        return source.beginRecording(width, height);
    }

    void end() { source.endRecording(); }

    void draw(Canvas canvas, RectF rect, int key, float radius, float alpha, float x, float y) {
        if (!canvas.isHardwareAccelerated()) return;
        BlurredBackgroundDrawable button = buttons.get(key);
        if (button == null) {
            button = factory.create().setColorProvider(material);
            button.setThickness(AndroidUtilities.dp(3));
            buttons.put(key, button);
        }
        button.setIntensity(NebulaGlass.custom() ? NebulaGlass.refraction() : .08f);
        button.setRadius(radius);
        button.setSourceOffset(x, y);
        button.setBounds(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom));
        button.setAlpha(Math.round(255 * alpha));
        button.draw(canvas);
    }
}
