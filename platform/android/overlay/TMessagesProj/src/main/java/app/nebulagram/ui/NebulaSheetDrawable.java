package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import org.telegram.ui.ActionBar.Theme;

/** Keeps native sheet padding while replacing only the painted surface. */
public final class NebulaSheetDrawable extends Drawable {
    private final Drawable fallback;
    private final Rect padding = new Rect();
    private final NebulaSheetSurface surface;
    private int alpha = 255;

    public NebulaSheetDrawable(Drawable fallback, View host, View anchor, Theme.ResourcesProvider provider) {
        this.fallback = fallback;
        fallback.getPadding(padding);
        surface = NebulaSheetSurface.create(host, anchor, provider);
    }
    @Override public void draw(Canvas canvas) {
        if (surface == null || !surface.draw(canvas, getBounds(), padding.left, padding.top, alpha, 0)) {
            fallback.setBounds(getBounds());
            fallback.draw(canvas);
        }
    }
    @Override public boolean getPadding(Rect out) { out.set(padding); return true; }
    @Override public void setAlpha(int value) { alpha = value; fallback.setAlpha(value); invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { fallback.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
