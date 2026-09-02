package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The NebulaGram mark: a rounded dart with a comet trail, drifting gently up
 * and down.
 *
 * <p>Drawn rather than shipped as a bitmap so it takes its colours from the
 * current palette — on Android 12 that means the mark follows the wallpaper
 * like the rest of the screen — and stays sharp at any size without four
 * density buckets of PNGs.
 */
public class NebulaMark extends Drawable {

    /** Geometry on a 200x200 canvas, the same one the icon generator uses. */
    private static final float CANVAS = 200f;
    private static final float[] DART = {148f, 52f, 96f, 138f, 88f, 104f, 54f, 96f};

    /** The comet trail: two cubic curves behind the dart. */
    private static final float[][] TRAILS = {
            {44f, 148f, 58f, 142f, 70f, 139f, 82f, 139f},
            {40f, 124f, 49f, 120f, 57f, 118f, 65f, 118f},
    };
    private static final float DART_STROKE = 13f;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private ValueAnimator drift;
    private float phase;
    private boolean animated = true;

    public NebulaMark(int dartTone, int trailTone) {
        body.setColor(dartTone);
        body.setStyle(Paint.Style.FILL_AND_STROKE);
        body.setStrokeWidth(DART_STROKE);
        body.setStrokeJoin(Paint.Join.ROUND);
        body.setStrokeCap(Paint.Cap.ROUND);

        trail.setColor(trailTone);
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
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

        // Filling and stroking with one paint rounds the corners, which is the
        // same trick the SVG and the launcher icon use, so the three agree.
        path.reset();
        path.moveTo(DART[0], DART[1]);
        for (int i = 2; i < DART.length; i += 2) {
            path.lineTo(DART[i], DART[i + 1]);
        }
        path.close();
        canvas.drawPath(path, body);

        for (int i = 0; i < TRAILS.length; i++) {
            float[] curve = TRAILS[i];
            trail.setStrokeWidth(i == 0 ? 9f : 7f);
            trail.setAlpha(i == 0 ? 218 : 140);
            path.reset();
            path.moveTo(curve[0], curve[1]);
            path.cubicTo(curve[2], curve[3], curve[4], curve[5], curve[6], curve[7]);
            canvas.drawPath(path, trail);
        }

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
        body.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        body.setColorFilter(colorFilter);
        trail.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
