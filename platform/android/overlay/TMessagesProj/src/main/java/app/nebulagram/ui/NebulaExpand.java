package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.animation.ValueAnimator;

/** Animates measured height, so following settings move with the revealed controls. */
public final class NebulaExpand extends LinearLayout {
    private float fraction;
    private ValueAnimator animator;
    public NebulaExpand(Context context, boolean expanded) {
        super(context); setOrientation(VERTICAL); fraction = expanded ? 1f : 0f;
        setVisibility(expanded ? VISIBLE : GONE);
    }
    public void expand(boolean expanded) {
        if (animator != null) animator.cancel();
        setVisibility(VISIBLE);
        animator = ValueAnimator.ofFloat(fraction, expanded ? 1f : 0f);
        animator.setDuration(NebulaAppearance.liquidAnimations() ? 260 : 0);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator(2));
        animator.addUpdateListener(a -> { fraction = (float) a.getAnimatedValue(); setAlpha(fraction); requestLayout(); });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { if (fraction == 0) setVisibility(GONE); }
        });
        animator.start();
    }
    @Override protected void onMeasure(int width, int height) {
        super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredHeight() * fraction));
    }
    @Override protected void onDetachedFromWindow() { if (animator != null) animator.cancel(); super.onDetachedFromWindow(); }
}
