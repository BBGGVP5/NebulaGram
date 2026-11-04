package app.nebulagram.ui;

import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Выбор пункта любого всплывающего меню без отрыва пальца.
 *
 * <p>Касание, открывшее меню (удержание сообщения, долгое нажатие на кнопку,
 * протяжка вниз от трёх точек), продолжает приходить в окно Activity, а не в
 * окно меню. Поэтому жест перехватывается в {@code dispatchTouchEvent}: как
 * только палец сдвинулся, экрану под меню уходит отмена, пункт под пальцем
 * подсвечивается, а при отпускании нажимается — тем же {@code performClick},
 * что и обычное касание, так что действия всех меню остаются их собственными.
 */
public final class NebulaMenuDrag {
    public interface Dispatcher { boolean dispatch(MotionEvent event); }

    private static final NebulaMenuDragTracker tracker = new NebulaMenuDragTracker();
    private static final ArrayList<View> items = new ArrayList<>();
    private static final int[] location = new int[2];
    private static final Rect visible = new Rect();
    private static int[] rects = new int[64];
    private static WeakReference<PopupWindow> popup;
    private static View hovered;
    private static boolean pointerDown;
    private static float lastX, lastY;

    private NebulaMenuDrag() { }

    public static boolean enabled() {
        try {
            return NebulaFeatureSettings.enabled("menu_drag_select");
        } catch (Throwable e) {
            return true;
        }
    }

    /** Кнопкам с подменю — открываться протяжкой вниз, как у Telegram у отдельных кнопок. */
    public static boolean openByDrag() {
        return enabled();
    }

    /** Меню только что показано. Если палец ещё на экране, начинаем следить за ним. */
    public static void shown(PopupWindow window) {
        if (window == null || !pointerDown || !enabled()) return;
        popup = new WeakReference<>(window);
        hovered = null;
        tracker.begin(lastX, lastY, AndroidUtilities.dp(10));
    }

    /** Вызывается из {@code Activity.dispatchTouchEvent}. true — событие поглощено. */
    public static boolean intercept(MotionEvent event, Dispatcher target) {
        int action = event.getActionMasked();
        lastX = event.getRawX();
        lastY = event.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerDown = true;
            finish();
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN && !tracker.active()) {
            finish();
            return false;
        }
        if (!tracker.tracking()) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pointerDown = false;
            return false;
        }
        PopupWindow window = popup == null ? null : popup.get();
        boolean showing = window != null && window.isShowing();
        if (action == MotionEvent.ACTION_MOVE) {
            if (!tracker.active()) {
                if (!showing) {
                    finish();
                    return false;
                }
                if (!tracker.move(lastX, lastY)) return false;
                // Экран под меню получает отмену один раз: список не прокрутится,
                // а кнопка, открывшая меню, снимет нажатие.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                try { target.dispatch(cancel); } finally { cancel.recycle(); }
            }
            hover(showing ? find(window, lastX, lastY) : null);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pointerDown = false;
            boolean active = tracker.active();
            View chosen = action == MotionEvent.ACTION_UP && active && showing ? find(window, lastX, lastY) : null;
            finish();
            if (chosen != null) chosen.performClick();
            return active;
        }
        return tracker.active();
    }

    private static void finish() {
        hover(null);
        tracker.reset();
        popup = null;
    }

    private static void hover(View view) {
        if (hovered == view) return;
        if (hovered != null) {
            hovered.setPressed(false);
            hovered.setSelected(false);
        }
        hovered = view;
        if (view != null) {
            view.getLocationOnScreen(location);
            view.drawableHotspotChanged(lastX - location[0], lastY - location[1]);
            view.setPressed(true);
            view.setSelected(true);
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) { }
        }
    }

    private static View find(PopupWindow window, float x, float y) {
        View root = window.getContentView();
        if (root == null) return null;
        items.clear();
        collect(root);
        if (rects.length < items.size() * 4) rects = new int[items.size() * 8];
        for (int i = 0; i < items.size(); i++) {
            View item = items.get(i);
            item.getLocationOnScreen(location);
            rects[i * 4] = location[0];
            rects[i * 4 + 1] = location[1];
            rects[i * 4 + 2] = location[0] + item.getWidth();
            rects[i * 4 + 3] = location[1] + item.getHeight();
        }
        int index = NebulaMenuDragTracker.pick(x, y, rects, items.size());
        View found = index < 0 ? null : items.get(index);
        items.clear();
        return found;
    }

    private static void collect(View view) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() < .05f) return;
        if (view instanceof ActionBarMenuSubItem) {
            // Пункт за краем прокрутки или на скрытой странице подменю не виден.
            if (view.isEnabled() && view.isClickable() && view.getGlobalVisibleRect(visible)) items.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
        }
    }
}
