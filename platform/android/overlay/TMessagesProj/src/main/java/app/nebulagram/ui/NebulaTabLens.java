package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

/** A single, interruptible selection lens over the bar's native live blur. */
public final class NebulaTabLens {
    private final View host;
    private final RectF current = new RectF(), target = new RectF(), start = new RectF(), draw = new RectF();
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix gradientMatrix = new Matrix();
    private final LinearGradient highlight = new LinearGradient(0, 0, 0, 1,
            new int[]{0x66ffffff, 0x0affffff, 0x24ffffff}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP);
    private ValueAnimator animation;
    private boolean initialized;
    private float stretch;

    public NebulaTabLens(View host) {
        this.host = host;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(AndroidUtilities.dpf2(.65f));
        edge.setShader(highlight);
    }

    public void draw(Canvas canvas, float left, float top, float right, float bottom, int accent) {
        if (right <= left || bottom <= top) return;
        if (!initialized) {
            target.set(left, top, right, bottom);
            current.set(target);
            initialized = true;
        } else if (Math.abs(target.left - left) > .5f || Math.abs(target.right - right) > .5f
                || Math.abs(target.top - top) > .5f || Math.abs(target.bottom - bottom) > .5f) {
            if (animation != null) animation.cancel();
            start.set(current);
            target.set(left, top, right, bottom);
            final float travel = Math.abs(target.centerX() - start.centerX());
            final float bulge = Math.min(AndroidUtilities.dpf2(10), travel * .10f);
            animation = ValueAnimator.ofFloat(0, 1);
            animation.setDuration(320);
            animation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animation.addUpdateListener(a -> {
                float p = (float) a.getAnimatedValue();
                current.set(mix(start.left, target.left, p), mix(start.top, target.top, p),
                        mix(start.right, target.right, p), mix(start.bottom, target.bottom, p));
                stretch = (float) Math.sin(Math.PI * p) * bulge;
                host.invalidate();
            });
            animation.start();
        }
        draw.set(current);
        draw.inset(-stretch, stretch * .12f);
        paint(canvas, draw, accent);
    }

    public void drawDrag(Canvas canvas, float left, float top, float right, float bottom, int accent) {
        reset();
        draw.set(left, top, right, bottom);
        paint(canvas, draw, accent);
    }

    private void paint(Canvas canvas, RectF bounds, int accent) {
        // Limit overshoot to the inside of the glass capsule, including compact bars.
        bounds.left = Math.max(host.getPaddingLeft(), bounds.left);
        bounds.right = Math.min(host.getWidth() - host.getPaddingRight(), bounds.right);
        if (bounds.width() <= 0) return;
        float radius = Math.min(bounds.width(), bounds.height()) * .5f;
        tint.setColor(Theme.multAlpha(accent, Theme.isCurrentThemeDark() ? .16f : .11f));
        canvas.drawRoundRect(bounds, radius, radius, tint);
        tint.setColor(Theme.isCurrentThemeDark() ? 0x0fffffff : 0x32ffffff);
        canvas.drawRoundRect(bounds, radius, radius, tint);
        gradientMatrix.setScale(1, bounds.height());
        gradientMatrix.postTranslate(0, bounds.top);
        highlight.setLocalMatrix(gradientMatrix);
        float inset = edge.getStrokeWidth() * .5f;
        bounds.inset(inset, inset);
        canvas.drawRoundRect(bounds, radius, radius, edge);
    }

    public void reset() {
        if (animation != null) { animation.cancel(); animation = null; }
        initialized = false;
        stretch = 0;
    }

    private static float mix(float a, float b, float p) { return a + (b - a) * p; }
}
