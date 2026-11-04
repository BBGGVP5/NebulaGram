package app.nebulagram.ui;

import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Пружины вкладки нижней панели: подложка выбранной вкладки вспухает с лёгким
 * перелётом, а нажатая вкладка проседает и отпружинивает обратно.
 *
 * <p>Обе величины — множители масштаба, которые вкладка применяет в
 * {@code dispatchDraw}. Собственный аниматор выбора Telegram при этом остаётся:
 * он ведёт цвет и прозрачность, пружина — только размер.
 */
public final class NebulaTabSpring {
    private static final float PRESSED = .88f;
    private final View view;
    private final FloatValueHolder selection = new FloatValueHolder(1f), press = new FloatValueHolder(1f);
    private final SpringAnimation selectionSpring, pressSpring;

    public NebulaTabSpring(View view) {
        this.view = view;
        selectionSpring = spring(selection, SpringForce.STIFFNESS_LOW * 1.6f, .48f);
        pressSpring = spring(press, SpringForce.STIFFNESS_MEDIUM, .55f);
    }

    public static boolean enabled() {
        try {
            return NebulaFeatureSettings.enabled("tab_springs");
        } catch (Throwable e) {
            return true;
        }
    }

    private SpringAnimation spring(FloatValueHolder holder, float stiffness, float damping) {
        SpringAnimation animation = new SpringAnimation(holder);
        animation.setSpring(new SpringForce(1f).setStiffness(stiffness).setDampingRatio(damping));
        animation.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
        animation.addUpdateListener((a, value, velocity) -> view.invalidate());
        return animation;
    }

    /** Вкладка стала выбранной: подложка стартует меньше и перелетает к размеру. */
    public void select(boolean animated) {
        if (!animated || !enabled()) {
            selectionSpring.cancel();
            selection.setValue(1f);
            return;
        }
        selectionSpring.cancel();
        selection.setValue(.72f);
        selectionSpring.animateToFinalPosition(1f);
    }

    public void press(boolean pressed) {
        if (!enabled()) {
            pressSpring.cancel();
            press.setValue(1f);
            return;
        }
        pressSpring.animateToFinalPosition(pressed ? PRESSED : 1f);
    }

    public float selectionScale() { return selection.getValue(); }

    public float pressScale() { return press.getValue(); }
}
