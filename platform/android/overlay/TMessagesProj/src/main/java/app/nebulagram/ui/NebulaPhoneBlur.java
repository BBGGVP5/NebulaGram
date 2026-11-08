package app.nebulagram.ui;

import android.graphics.BlurMaskFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

/**
 * «Скрывать номера телефонов»: номер в профиле остаётся на своём месте, но
 * размыт. Первое нажатие показывает его на 15 секунд, следующее работает как
 * обычно (позвонить, скопировать, сменить номер).
 */
public final class NebulaPhoneBlur {
    public static final String KEY = "hide_profile_phone";
    private static final long REVEAL_MS = 15_000;
    private static final java.util.Map<TextView, Boolean> blurredViews = new java.util.WeakHashMap<>();
    private static long revealedUntil;

    private NebulaPhoneBlur() { }

    public static boolean enabled() {
        return NebulaMessagePreferences.enabled(KEY, false);
    }

    private static boolean hidden() {
        return enabled() && SystemClock.elapsedRealtime() >= revealedUntil;
    }

    /** Размывает номер в строке профиля, если настройка включена. */
    public static void apply(TextView view) {
        if (view == null) return;
        setBlurred(view, hidden());
    }

    /** Ячейка ушла под другую строку: снять размытие. */
    public static void reset(TextView view) {
        if (view != null) setBlurred(view, false);
    }

    /**
     * Нажатие на строку с номером. Возвращает {@code true}, если нажатие ушло
     * на то, чтобы показать номер.
     */
    public static boolean reveal(View row) {
        TextView number = find(row);
        if (number == null || !blurredViews.containsKey(number)) return false;
        revealedUntil = SystemClock.elapsedRealtime() + REVEAL_MS;
        setBlurred(number, false);
        NebulaHaptics.tick(row);
        number.postDelayed(() -> apply(number), REVEAL_MS + 50);
        return true;
    }

    private static TextView find(View row) {
        if (row instanceof org.telegram.ui.Cells.TextDetailCell) return ((org.telegram.ui.Cells.TextDetailCell) row).textView;
        if (row instanceof TextView) return (TextView) row;
        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0
                && ((ViewGroup) row).getChildAt(0) instanceof TextView) return (TextView) ((ViewGroup) row).getChildAt(0);
        return null;
    }

    private static void setBlurred(TextView view, boolean blurred) {
        boolean was = blurredViews.containsKey(view);
        if (was == blurred) return;
        if (blurred) blurredViews.put(view, Boolean.TRUE); else blurredViews.remove(view);
        float radius = AndroidUtilities.dp(5);
        if (Build.VERSION.SDK_INT >= 31) {
            view.setRenderEffect(blurred ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) : null);
        } else {
            view.setLayerType(blurred ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE, null);
            view.getPaint().setMaskFilter(blurred ? new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) : null);
            view.invalidate();
        }
        view.setContentDescription(blurred ? NebulaText.text("Номер скрыт. Нажмите, чтобы показать", "Number hidden. Tap to show") : null);
    }
}
