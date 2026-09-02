package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The NebulaGram mark: a two-tone plane with softly rounded corners, drifting
 * gently up and down.
 *
 * <p>Drawn rather than shipped as a bitmap so it takes its colours from the
 * current palette — on Android 12 that means the mark follows the wallpaper
 * like the rest of the screen — and stays sharp at any size without four
 * density buckets of PNGs.
 */
public class NebulaMark extends Drawable {

    /** Geometry on a 120x120 canvas, scaled to whatever bounds we are given. */
    private static final float CANVAS = 120f;
    private static final float TIP_X = 97f, TIP_Y = 27f;
    private static final float WING_X = 28f, WING_Y = 63f;
    private static final float NOTCH_X = 57f, NOTCH_Y = 74f;
    private static final float TAIL_X = 64f, TAIL_Y = 101f;

    private final Paint upper = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lower = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private ValueAnimator drift;
    private float phase;
    private boolean animated = true;

    public NebulaMark(int lightTone, int darkTone) {
        upper.setColor(lightTone);
        lower.setColor(darkTone);
        // Rounding the path itself, rather than faking it with a thick stroke,
        // keeps the silhouette exact at every size.
        CornerPathEffect corners = new CornerPathEffect(8f);
        upper.setPathEffect(corners);
        lower.setPathEffect(corners);
    }

    /** Turns the drift off, for a static mark in a list row. */
    public NebulaMark still() {
        animated = false;
        stopDrift();
        return this;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        if (bounds.isEmpty()) {
            return;
        }
        float scale = Math.min(bounds.width(), bounds.height()) / CANVAS;
        float offsetX = bounds.left + (bounds.width() - CANVAS * scale) / 2f;
        // The drift is a seventh of the mark's height, eased into a sine.
        float lift = animated ? (float) Math.sin(phase * 2 * Math.PI) * CANVAS * 0.05f : 0f;
        float offsetY = bounds.top + (bounds.height() - CANVAS * scale) / 2f + lift * scale;

        int saved = canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        path.reset();
        path.moveTo(TIP_X, TIP_Y);
        path.lineTo(WING_X, WING_Y);
        path.lineTo(NOTCH_X, NOTCH_Y);
        path.close();
        canvas.drawPath(path, upper);

        path.reset();
        path.moveTo(TIP_X, TIP_Y);
        path.lineTo(NOTCH_X, NOTCH_Y);
        path.lineTo(TAIL_X, TAIL_Y);
        path.close();
        canvas.drawPath(path, lower);

        canvas.restoreToCount(saved);
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        if (animated && drift == null && !bounds.isEmpty()) {
            startDrift();
        }
    }

    private void startDrift() {
        drift = ValueAnimator.ofFloat(0f, 1f);
        drift.setDuration(4600);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        drift.start();
    }

    private void stopDrift() {
        if (drift != null) {
            drift.cancel();
            drift = null;
        }
    }

    /** Must be called when the view holding the mark goes away. */
    public void detach() {
        stopDrift();
    }

    @Override
    public void setAlpha(int alpha) {
        upper.setAlpha(alpha);
        lower.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        upper.setColorFilter(colorFilter);
        lower.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
