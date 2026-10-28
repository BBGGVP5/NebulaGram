package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * Строки плавно уходят за верхний край вместо того, чтобы обрезаться.
 *
 * <p>Сделано преобразованием самой строки, а не правкой её отрисовки.
 * {@code DialogCell.onDraw} считает десятки координат и имеет несколько ранних
 * выходов; оборачивать всё это в масштаб канвы значило бы влезть в самый
 * горячий путь списка ради эффекта по краю. Масштаб и прозрачность вида
 * применяет система отрисовки, и строка остаётся нетронутой.
 *
 * <p>Снизу ничего не делается: там заканчивается список, а не экран, и
 * уползающая вниз последняя строка выглядела бы поломкой, а не эффектом.
 */
public final class NebulaEdgeCompression {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "edge_compression";

    /** На какой высоте от края эффект уже полностью набран. */
    private static final int RANGE_DP = 56;
    /** Насколько строка ужимается у самого края. */
    private static final float MINIMUM_SCALE = .92f;

    private NebulaEdgeCompression() { }

    public static boolean enabled() {
        return prefs().getBoolean(KEY, false);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
    }

    /**
     * Применяет сжатие к видимым строкам. Вызывается на прокрутке и после
     * вёрстки: иначе первая отрисовка показала бы строки нетронутыми.
     */
    public static void apply(ViewGroup list) {
        if (list == null) return;
        final boolean on = enabled();
        // Пока список проигрывает свою анимацию, строки принадлежат ей: она
        // ведёт их прозрачность при добавлении и удалении. Перебивать её на
        // каждой прокрутке значит гасить исчезающий чат на середине. Строки
        // вернутся под сжатие со следующей прокруткой или вёрсткой.
        if (on && list instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView.ItemAnimator animator =
                    ((androidx.recyclerview.widget.RecyclerView) list).getItemAnimator();
            if (animator != null && animator.isRunning()) return;
        }
        final float top = list.getPaddingTop();
        final float range = AndroidUtilities.dp(RANGE_DP);
        for (int i = 0, count = list.getChildCount(); i < count; i++) {
            final View child = list.getChildAt(i);
            if (!on) {
                // Возврат к исходному состоянию обязателен: иначе строки,
                // сжатые до выключения настройки, останутся такими навсегда —
                // список переиспользует виды, а не создаёт их заново.
                if (child.getScaleX() != 1f) {
                    child.setScaleX(1f);
                    child.setScaleY(1f);
                    child.setAlpha(1f);
                }
                continue;
            }
            final float distance = child.getY() - top;
            final float progress = distance >= 0 || range <= 0
                    ? 1f : Math.max(0f, 1f + distance / range);
            final float scale = MINIMUM_SCALE + (1f - MINIMUM_SCALE) * progress;
            child.setPivotX(child.getWidth() / 2f);
            child.setPivotY(child.getHeight());
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(progress);
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
