package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;

/** A bounded, scrollable page: actions occupy space instead of covering content. */
public final class NebulaOnboardingLayout extends FrameLayout {
    public final LinearLayout content;
    public final LinearLayout actions;
    private final LinearLayout column;
    private OnSwipeListener onSwipeListener;
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean interceptingSwipe;

    public interface OnSwipeListener {
        /** direction is +1 for advancing and -1 for going back. */
        void onSwipe(int direction);
    }

    public NebulaOnboardingLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackgroundColor(NebulaTheme.of(context).surface());
        setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(AndroidUtilities.dp(24), AndroidUtilities.statusBarHeight + NebulaLoginStyle.compact(20),
                AndroidUtilities.dp(24), NebulaLoginStyle.compact(16));
        addView(scroll, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        FrameLayout bounded = new FrameLayout(context);
        scroll.addView(bounded, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        column = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(480));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        bounded.addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        // Отступы колонки тоже доля от высоты: на коротком экране именно они
        // съедают место, которого не хватает картинке и полям.
        content.setPadding(0, NebulaLoginStyle.compact(24), 0, NebulaLoginStyle.compact(32));
        column.addView(content, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, 1f));

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        column.addView(actions, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        onSwipeListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (onSwipeListener == null) {
            return super.onInterceptTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                interceptingSwipe = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    interceptingSwipe = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                interceptingSwipe = false;
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interceptingSwipe || onSwipeListener == null) {
            return super.onTouchEvent(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                onSwipeListener.onSwipe(dx < 0 ? 1 : -1);
            }
            interceptingSwipe = false;
            return true;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            interceptingSwipe = false;
            return true;
        }
        return true;
    }

    /** Text can wrap at the user's chosen font size without clipping an action. */
    public static void action(NebulaButton button) {
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        button.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14),
                AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
