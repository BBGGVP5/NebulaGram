package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import java.util.WeakHashMap;

/** Interruptible height animation: the following rows move with the expanding row. */
public final class NebulaVisibility {
    private static final WeakHashMap<View, ValueAnimator> animations = new WeakHashMap<>();
    private NebulaVisibility() { }
    public static void animate(View view, boolean show) {
        ValueAnimator previous = animations.remove(view);
        if (previous != null) previous.cancel();
        ViewGroup parent = (ViewGroup) view.getParent();
        int from = view.getVisibility() == View.GONE ? 0 : view.getHeight();
        int width = Math.max(0, parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight());
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int to = show ? view.getMeasuredHeight() : 0;
        view.setVisibility(View.VISIBLE);
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animations.put(view, animator);
        animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a -> {
            view.getLayoutParams().height = (int) a.getAnimatedValue();
            view.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (animations.get(view) != a) return;
                animations.remove(view);
                view.setVisibility(show ? View.VISIBLE : View.GONE);
                view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.requestLayout();
            }
        });
        animator.start();
    }
}
