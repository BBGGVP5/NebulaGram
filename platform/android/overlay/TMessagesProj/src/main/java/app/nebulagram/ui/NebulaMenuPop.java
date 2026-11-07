package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Cherrygram-style entrance of the message menu: the whole card grows out of
 * the edge that faces the message, with a light overshoot, and fades in. It
 * replaces the liquid morph when chosen, so the two never run together.
 */
public final class NebulaMenuPop {
    public static final float START_SCALE = .72f;
    public static final long DURATION = 280;

    private NebulaMenuPop() { }

    public static boolean enabled() {
        return NebulaMessageMenuSettings.enabled("cherry_motion", true)
                && !NebulaGlass.reduced()
                && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
    }

    /** Pivot on the edge nearest the message: above the menu → top, below → bottom. */
    public static float pivotY(float menuTop, float menuHeight, float messageCenterY) {
        return messageCenterY <= menuTop + menuHeight / 2f ? 0f : Math.max(0f, menuHeight);
    }

    /** Horizontal pivot under the message centre, kept inside the card. */
    public static float pivotX(float menuLeft, float menuWidth, float messageCenterX) {
        return Math.max(0f, Math.min(menuWidth, messageCenterX - menuLeft));
    }

    public static float scale(float progress) {
        return START_SCALE + (1f - START_SCALE) * progress;
    }

    public static float alpha(float linear) {
        return Math.max(0f, Math.min(1f, linear * 1.8f));
    }

    public static void animateIn(View card, float pivotX, float pivotY) {
        if (card == null) return;
        card.setPivotX(pivotX);
        card.setPivotY(pivotY);
        card.setScaleX(START_SCALE);
        card.setScaleY(START_SCALE);
        card.setAlpha(0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        OvershootInterpolator overshoot = new OvershootInterpolator(1.1f);
        animator.addUpdateListener(a -> {
            float linear = a.getAnimatedFraction();
            float value = overshoot.getInterpolation(linear);
            card.setScaleX(scale(value));
            card.setScaleY(scale(value));
            card.setAlpha(alpha(linear));
        });
        animator.start();
    }
}
