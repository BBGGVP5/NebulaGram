package app.nebulagram.ui;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Плеер, открытый из профиля, как в iOS: лист вырастает из плашки песни и
 * сворачивается обратно в неё. Скруглённый контур листа плавно переходит из
 * прямоугольника плашки в полный лист; содержимое проявляется на ходу.
 * Профиль задаёт плашку перед показом плеера; после закрытия цель сбрасывается.
 */
public final class NebulaSheetCollapse {
    private static WeakReference<View> target;
    private static Supplier<RectF> pill;

    private NebulaSheetCollapse() {}

    /** Плашка (вид и её прямоугольник внутри вида), из которой открывается следующий плеер. */
    public static void target(View view, Supplier<RectF> localPill) {
        target = view == null ? null : new WeakReference<>(view);
        pill = view == null ? null : localPill;
    }

    /**
     * Анимация открытия (opening) или закрытия листа. visibility получает 0..1 —
     * насколько лист открыт, для затемнения; end — в конце. null, если плашки нет на экране:
     * тогда лист анимируется как обычно. Закрытие сбрасывает цель.
     */
    public static AnimatorSet morph(View sheet, boolean opening, Consumer<Float> visibility, Runnable end) {
        final View view = target != null ? target.get() : null;
        final Supplier<RectF> local = pill;
        if (!opening) target(null, null);
        if (sheet == null || view == null || !view.isAttachedToWindow() || !view.isShown()
                || view.getWidth() <= 0 || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
            return null;
        }
        final RectF from = new RectF();
        final RectF inner = local != null ? local.get() : null;
        if (inner != null && inner.width() > 0 && inner.height() > 0) from.set(inner);
        else from.set(0, 0, view.getWidth(), view.getHeight());
        final int[] at = new int[2];
        view.getLocationInWindow(at);
        from.offset(at[0], at[1]);
        sheet.getLocationInWindow(at);
        from.offset(-(at[0] - sheet.getTranslationX()), -(at[1] - sheet.getTranslationY()));
        final RectF to = new RectF(0, 0, sheet.getWidth(), sheet.getHeight());
        final float fromRadius = from.height() / 2f, toRadius = AndroidUtilities.dp(16);

        final ViewOutlineProvider savedProvider = sheet.getOutlineProvider();
        final boolean savedClip = sheet.getClipToOutline();
        final RectF current = new RectF();
        final float[] radius = {opening ? fromRadius : toRadius};
        current.set(opening ? from : to);
        sheet.setAlpha(opening ? 0f : sheet.getAlpha());
        sheet.setTranslationX(0);
        sheet.setTranslationY(0);
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(Math.round(current.left), Math.round(current.top),
                        Math.round(current.right), Math.round(current.bottom), radius[0]);
            }
        });
        sheet.setClipToOutline(true);

        final ValueAnimator shape = ValueAnimator.ofFloat(opening ? 0f : 1f, opening ? 1f : 0f);
        shape.addUpdateListener(a -> {
            final float t = (float) a.getAnimatedValue();
            current.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            radius[0] = lerp(fromRadius, toRadius, t);
            // Содержимое видно, когда контур уже заметно больше плашки.
            sheet.setAlpha(clamp((t - .05f) / .45f));
            sheet.invalidateOutline();
            if (visibility != null) visibility.accept(t);
        });
        final AnimatorSet set = new AnimatorSet();
        set.play(shape);
        set.setDuration(opening ? 460 : 380);
        set.setInterpolator(opening ? CubicBezierInterpolator.EASE_OUT_QUINT : CubicBezierInterpolator.EASE_OUT);
        set.addListener(new AnimatorListenerAdapter() {
            private boolean done;
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (done) return;
                done = true;
                sheet.setOutlineProvider(savedProvider);
                sheet.setClipToOutline(savedClip);
                sheet.setAlpha(opening ? 1f : 0f);
                if (end != null) end.run();
            }
        });
        return set;
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
