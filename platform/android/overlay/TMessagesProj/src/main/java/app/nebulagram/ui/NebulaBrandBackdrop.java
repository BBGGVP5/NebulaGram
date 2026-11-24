package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Full-screen gradient in the icon's color: tinted at the top, a soft glow
 * behind the artwork, fading to {@link NebulaBrandPalette#FLOOR} at the bottom.
 */
public class NebulaBrandBackdrop extends View {
    private final Paint linear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int top = 0xFF1B2A44, glow = 0xFF2C4C80;
    private float glowCenter = 0.2f, glowRadius = 0.8f, fadeEnd = 0.72f;
    private boolean dirty = true;
    private ValueAnimator animator;

    public NebulaBrandBackdrop(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Where the glow sits and how far down the tint reaches, as fractions of the height. */
    public void setGeometry(float glowCenter, float glowRadius, float fadeEnd) {
        this.glowCenter = glowCenter;
        this.glowRadius = glowRadius;
        this.fadeEnd = fadeEnd;
        dirty = true;
        invalidate();
    }

    public int top() { return top; }

    public void setColors(int top, int glow) {
        if (animator != null) animator.cancel();
        if (this.top == top && this.glow == glow) return;
        this.top = top;
        this.glow = glow;
        dirty = true;
        invalidate();
    }

    public void animateColors(int toTop, int toGlow) {
        if (animator != null) animator.cancel();
        final int fromTop = top, fromGlow = glow;
        if (fromTop == toTop && fromGlow == toGlow) return;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(380);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            top = NebulaBrandPalette.blend(fromTop, toTop, t);
            glow = NebulaBrandPalette.blend(fromGlow, toGlow, t);
            dirty = true;
            invalidate();
        });
        animator.start();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dirty = true;
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (dirty) {
            dirty = false;
            int middle = NebulaBrandPalette.blend(top, NebulaBrandPalette.FLOOR, 0.55f);
            linear.setShader(new LinearGradient(0, 0, 0, h * fadeEnd,
                    new int[]{top, middle, NebulaBrandPalette.FLOOR}, new float[]{0, 0.55f, 1},
                    Shader.TileMode.CLAMP));
            float radius = Math.max(1, w * glowRadius);
            radial.setShader(new RadialGradient(w / 2f, h * glowCenter, radius,
                    new int[]{NebulaBrandPalette.withAlpha(glow, 0.62f), NebulaBrandPalette.withAlpha(glow, 0.22f),
                            NebulaBrandPalette.withAlpha(glow, 0f)},
                    new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        }
        canvas.drawColor(NebulaBrandPalette.FLOOR);
        canvas.drawRect(0, 0, w, h, linear);
        canvas.drawRect(0, 0, w, h, radial);
    }
}
