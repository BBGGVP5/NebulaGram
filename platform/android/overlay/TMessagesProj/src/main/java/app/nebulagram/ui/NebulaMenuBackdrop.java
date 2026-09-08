package app.nebulagram.ui;

import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

/** Captures the originating window, including its wallpaper, for a separate popup window. */
public final class NebulaMenuBackdrop {
    private NebulaMenuBackdrop() { }
    public static void attach(View popup, View anchor, Theme.ResourcesProvider provider) {
        if (Build.VERSION.SDK_INT < 31 || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
            popup.setBackground(NebulaMenuStyle.fallback(provider));
            return;
        }
        attachHardware(popup, anchor.getRootView(), provider);
    }

    @android.annotation.TargetApi(31)
    private static void attachHardware(View popup, View root, Theme.ResourcesProvider provider) {
        BlurredBackgroundSourceColor fallback = new BlurredBackgroundSourceColor();
        fallback.setColor(NebulaMenuStyle.surface(provider));
        BlurredBackgroundSourceRenderNode source = new BlurredBackgroundSourceRenderNode(fallback);
        source.setBlur(AndroidUtilities.dpf2(NebulaGlass.blur()));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
        factory.setLiquidGlassEffectAllowed(NebulaMenuStyle.animated());
        BlurredBackgroundDrawable material = factory.create(popup, true)
                .setColorProvider(NebulaMenuStyle.provider(provider)).setRadius(NebulaMenuStyle.radius())
                .setPadding(AndroidUtilities.dp(8)).setHasPadding(true);
        material.setThickness(AndroidUtilities.dp(5)); material.setIntensity(NebulaGlass.refraction());
        popup.setBackground(material);
        int[] origin = new int[2], position = new int[2];
        ViewTreeObserver.OnPreDrawListener capture = () -> {
            if (root.getWidth() > 0 && root.getHeight() > 0 && !source.inRecording()) {
                Canvas canvas = source.beginRecording(root.getWidth(), root.getHeight());
                try { root.draw(canvas); } finally { source.endRecording(); }
                root.getLocationOnScreen(origin);
                popup.getLocationOnScreen(position);
                material.setSourceOffset(position[0] - origin[0], position[1] - origin[1]);
            }
            return true;
        };
        popup.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().addOnPreDrawListener(capture);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                if (v.getViewTreeObserver().isAlive()) v.getViewTreeObserver().removeOnPreDrawListener(capture);
                v.removeOnAttachStateChangeListener(this);
            }
        });
    }
}
