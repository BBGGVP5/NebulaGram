package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/**
 * Ширина левой панели в планшетной раскладке, которую можно менять.
 *
 * <p>Хранится доля ширины содержимого, а не пиксели, — тогда поворот экрана и
 * мультиоконный режим не превращают удобную ширину в неудобную. Доля всегда
 * зажимается так, чтобы ни одной панели не стало уже её минимума.
 */
public final class NebulaTabletPanes {
    private static final String KEY = "tablet_left_fraction";
    /** Нет сохранённой доли — Telegram считает ширину сам. */
    public static final float UNSET = -1f;
    public static final float MIN_FRACTION = .22f, MAX_FRACTION = .62f;
    private static float cached = Float.NaN;

    private NebulaTabletPanes() { }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }

    public static float fraction() {
        if (Float.isNaN(cached)) {
            try {
                cached = prefs().getFloat(KEY, UNSET);
            } catch (Throwable e) {
                cached = UNSET;
            }
        }
        return cached;
    }

    public static void setFraction(float value) {
        cached = value;
        prefs().edit().putFloat(KEY, value).apply();
    }

    public static void reset() {
        cached = UNSET;
        prefs().edit().remove(KEY).apply();
    }

    /**
     * Ширина левой панели вместе с левым вырезом. Без Android-зависимостей.
     *
     * @param fallback ширина, которую посчитал Telegram
     */
    public static int width(int fullWidth, int insetLeft, int insetRight, float fraction,
                            int minLeft, int minRight, int fallback) {
        int content = fullWidth - insetLeft - insetRight;
        if (content <= 0) return fallback;
        if (fraction == UNSET || Float.isNaN(fraction)) return fallback;
        float clamped = Math.max(MIN_FRACTION, Math.min(MAX_FRACTION, fraction));
        int left = Math.round(content * clamped);
        // Узкий экран может не вместить оба минимума — тогда правой панели важнее.
        left = Math.min(left, content - minRight);
        left = Math.max(left, Math.min(minLeft, content - minRight));
        if (left <= 0) return fallback;
        return insetLeft + left;
    }

    /** Доля, соответствующая положению разделителя на экране. */
    public static float fractionAt(float x, int fullWidth, int insetLeft, int insetRight) {
        int content = fullWidth - insetLeft - insetRight;
        if (content <= 0) return UNSET;
        return Math.max(MIN_FRACTION, Math.min(MAX_FRACTION, (x - insetLeft) / content));
    }

    /** Хук из {@code AndroidUtilities.getTabletLeftFragmentSize}. */
    public static int leftWidth(int fullWidth, int insetLeft, int insetRight, int fallback) {
        return width(fullWidth, insetLeft, insetRight, fraction(),
                AndroidUtilities.dp(280), AndroidUtilities.dp(360), fallback);
    }

    /** Перетаскиваемый разделитель между панелями; живёт в корневой раскладке планшета. */
    public static final class Divider {
        private final View host;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF handle = new RectF();
        private boolean dragging;
        private float downX;
        private int insetLeft, insetRight;

        public Divider(View host) {
            this.host = host;
        }

        /** @param split обе панели на экране; @param leftWidth текущая граница */
        public boolean intercept(MotionEvent event, boolean split, int leftWidth, int insetLeft, int insetRight) {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return dragging;
            if (!split) return false;
            if (Math.abs(event.getX() - leftWidth) > AndroidUtilities.dp(10)) return false;
            this.insetLeft = insetLeft;
            this.insetRight = insetRight;
            downX = event.getX();
            dragging = true;
            if (host.getParent() != null) host.getParent().requestDisallowInterceptTouchEvent(true);
            try {
                host.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) { }
            host.invalidate();
            return true;
        }

        public boolean touch(MotionEvent event) {
            if (!dragging) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                float value = fractionAt(event.getX(), host.getWidth(), insetLeft, insetRight);
                if (value != UNSET && value != cached) {
                    cached = value;
                    host.requestLayout();
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                dragging = false;
                if (Math.abs(event.getX() - downX) > AndroidUtilities.dp(2) && cached != UNSET) setFraction(cached);
                host.invalidate();
            }
            return true;
        }

        public boolean dragging() { return dragging; }

        /** Тонкая ручка на границе: заметна всегда, ярче — пока её тянут. */
        public void draw(Canvas canvas, boolean split, int leftWidth) {
            if (!split) return;
            float cy = host.getHeight() / 2f;
            float w = AndroidUtilities.dp(dragging ? 6 : 4), h = AndroidUtilities.dp(dragging ? 64 : 44);
            handle.set(leftWidth - w / 2f, cy - h / 2f, leftWidth + w / 2f, cy + h / 2f);
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            paint.setColor(Theme.multAlpha(color, dragging ? .9f : .45f));
            canvas.drawRoundRect(handle, w / 2f, w / 2f, paint);
        }
    }
}
