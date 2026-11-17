package app.nebulagram.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.lang.ref.WeakReference;

/**
 * Плеер, открытый из профиля, сворачивается обратно в плашку песни, как в iOS,
 * а не уезжает вниз. Профиль задаёт цель перед показом плеера; цель одноразовая.
 */
public final class NebulaSheetCollapse {
    private static WeakReference<View> target;

    private NebulaSheetCollapse() {}

    /** Плашка, в которую свернётся следующий закрытый плеер; null — обычное закрытие. */
    public static void target(View view) {
        target = view == null ? null : new WeakReference<>(view);
    }

    /**
     * Анимация сжатия листа в плашку или null, если плашки нет на экране.
     * dim получает 1..0 для затемнения позади листа; end вызывается в конце.
     */
    public static AnimatorSet collapse(View sheet, java.util.function.Consumer<Float> dim, Runnable end) {
        final View view = target != null ? target.get() : null;
        target = null;
        if (sheet == null || view == null || !view.isAttachedToWindow() || !view.isShown()
                || view.getWidth() <= 0 || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
            return null;
        }
        final int[] at = new int[2];
        view.getLocationInWindow(at);
        final float tx = at[0] + view.getWidth() / 2f, ty = at[1] + view.getHeight() / 2f;
        sheet.getLocationInWindow(at);
        // Расположение без текущих сдвигов: от него считается центр листа.
        final float sx = at[0] - sheet.getTranslationX() + sheet.getWidth() / 2f;
        final float sy = at[1] - sheet.getTranslationY() + sheet.getHeight() / 2f;
        final float scale = Math.max(.06f, Math.min(1f, AndroidUtilities.dp(200) / (float) sheet.getWidth()));
        sheet.setPivotX(sheet.getWidth() / 2f);
        sheet.setPivotY(sheet.getHeight() / 2f);
        final AnimatorSet set = new AnimatorSet();
        final ValueAnimator fade = ValueAnimator.ofFloat(1f, 0f);
        fade.addUpdateListener(a -> {
            if (dim != null) dim.accept((float) a.getAnimatedValue());
        });
        final ObjectAnimator alpha = ObjectAnimator.ofFloat(sheet, View.ALPHA, sheet.getAlpha(), 0f);
        alpha.setStartDelay(150);
        alpha.setDuration(230);
        set.playTogether(
                ObjectAnimator.ofFloat(sheet, View.SCALE_X, sheet.getScaleX(), scale),
                ObjectAnimator.ofFloat(sheet, View.SCALE_Y, sheet.getScaleY(), scale * .35f),
                ObjectAnimator.ofFloat(sheet, View.TRANSLATION_X, sheet.getTranslationX(), tx - sx),
                ObjectAnimator.ofFloat(sheet, View.TRANSLATION_Y, sheet.getTranslationY(), ty - sy),
                alpha, fade);
        set.setDuration(380);
        set.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean done;
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (done) return;
                done = true;
                if (end != null) end.run();
            }
        });
        return set;
    }
}
