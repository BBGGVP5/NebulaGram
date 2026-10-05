package app.nebulagram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import org.telegram.ui.Components.CubicBezierInterpolator;

/** A single owner cancels navigation without delivering an obsolete completion callback. */
public final class NebulaNavigationAnimation {
    private ValueAnimator animator;
    private SpringAnimation spring;
    private boolean canceled;
    public static boolean enabled() {
        return !NebulaGlass.reduced() && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
    }
    public void start(View front, View back, boolean open, int style, java.util.function.Consumer<Float> progress, Runnable end) {
        float width = Math.max(1, front.getWidth());
        java.util.function.Consumer<Float> frame = value -> {
            if (canceled) return;
            float p = Math.max(0, Math.min(1, value));
            front.setAlpha(1); back.setAlpha(1);
            front.setTranslationX(open ? (1-p)*width : -(1-p)*width*.35f);
            back.setTranslationX(open ? -p*width*.35f : p*width);
            View foreground = open ? front : back;
            View background = open ? back : front;
            float scale = style == NebulaTransitions.SPRING ? (open ? .85f+.15f*p : 1-.15f*p) : 1;
            foreground.setScaleX(scale); foreground.setScaleY(scale);
            background.setScaleX(1); background.setScaleY(1);
            progress.accept(p);
        };
        frame.accept(0f);
        if (!enabled()) { frame.accept(1f); end.run(); return; }
        if (style == NebulaTransitions.SPRING) {
            spring = new SpringAnimation(new FloatValueHolder(0f));
            spring.setSpring(new SpringForce(1000f).setStiffness(900f).setDampingRatio(1f));
            spring.addUpdateListener((animation, value, velocity) -> frame.accept(value/1000f));
            spring.addEndListener((animation, stopped, value, velocity) -> { if (!canceled && !stopped) { frame.accept(1f); end.run(); } });
            spring.start();
        } else {
            animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(220); animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.addUpdateListener(animation -> frame.accept((Float)animation.getAnimatedValue()));
            animator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator animation) { if (!canceled) end.run(); } });
            animator.start();
        }
    }
    public void cancel() { canceled = true; if (spring != null) spring.cancel(); if (animator != null) animator.cancel(); }
}
