package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.telegram.ui.LauncherIconController;

/**
 * A launcher icon drawn the way a launcher shows it: the adaptive layers are
 * 108dp with a 72dp visible center, so both layers are drawn a quarter larger
 * than the view and the view's outline cuts the rounded shape.
 */
public class NebulaIconArt extends View {
    /** 108 / 72: how far the adaptive layers reach past the visible shape. */
    private static final float BLEED = 0.25f;
    private LauncherIconController.LauncherIcon icon;
    private Drawable background, foreground;
    private float cornerFraction = 0.3f;

    public NebulaIconArt(Context context) {
        super(context);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                int w = view.getWidth(), h = view.getHeight();
                outline.setRoundRect(0, 0, w, h, Math.min(w, h) * cornerFraction);
            }
        });
        setClipToOutline(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public LauncherIconController.LauncherIcon icon() { return icon; }

    public void setIcon(LauncherIconController.LauncherIcon value) {
        if (value == icon) return;
        icon = value;
        try {
            background = getContext().getDrawable(value.background).mutate();
            foreground = getContext().getDrawable(value.foreground).mutate();
        } catch (Exception e) {
            background = foreground = null;
        }
        invalidate();
    }

    public void setCornerFraction(float value) {
        cornerFraction = value;
        invalidateOutline();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int dx = Math.round(w * BLEED), dy = Math.round(h * BLEED);
        if (background != null) {
            background.setBounds(-dx, -dy, w + dx, h + dy);
            background.draw(canvas);
        }
        if (foreground != null) {
            foreground.setBounds(-dx, -dy, w + dx, h + dy);
            foreground.draw(canvas);
        }
    }
}
