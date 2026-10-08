package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/** One moving selection surface, shared by touch and accessibility selection. */
final class NebulaEditorTabs extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final int color;
    private float position;
    private int selected;
    private ValueAnimator motion;
    private final java.util.function.IntConsumer choose;
    private final int touchSlop;
    private float downX, downY, stretch;
    private boolean dragging;
    private BlurredBackgroundDrawable glass;
    NebulaEditorTabs(Context context, String[] names, int[] icons, int initial, java.util.function.IntConsumer choose) {
        super(context);
        this.choose = choose;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        NebulaTheme theme = NebulaTheme.of(context);
        color = theme.primaryContainer();
        setBackground(Theme.createRoundRectDrawable(dp(28), Theme.multAlpha(theme.surfaceContainer(), .38f)));
        position = selected = initial;
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(context);
            tab.setOrientation(VERTICAL); tab.setGravity(android.view.Gravity.CENTER);
            tab.setPadding(dp(4), dp(9), dp(4), dp(9));
            ImageView icon = new ImageView(context); icon.setImageResource(icons[i]); icon.setColorFilter(theme.primary());
            tab.addView(icon, new LayoutParams(dp(22), dp(22)));
            TextView label = new TextView(context); label.setText(names[i]); label.setTextSize(12);
            label.setTextColor(theme.onSurface()); label.setGravity(android.view.Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0); tab.addView(label);
            tab.setFocusable(true); tab.setContentDescription(names[i]); tab.setSelected(i == initial);
            tab.setOnClickListener(v -> commit(index));
            addView(tab, new LayoutParams(0, -2, 1));
        }
    }
    void setGlass(BlurredBackgroundDrawable material) { glass = material; invalidate(); }
    private void commit(int index) {
        boolean changed = selected != index;
        select(index);
        if (changed) choose.accept(index);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY(); dragging = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                && Math.abs(event.getX() - downX) > touchSlop
                && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY)) {
            dragging = true;
            if (motion != null) motion.cancel();
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float width = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float)getChildCount();
                if (width <= 0) return true;
                float next = Math.max(0, Math.min(getChildCount() - 1,
                        (event.getX() - getPaddingLeft()) / width - .5f));
                if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) next = getChildCount() - 1 - next;
                stretch = NebulaMenuStyle.animated() ? Math.min(.12f, Math.abs(next - position) * .35f) : 0;
                position = next; invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false; getParent().requestDisallowInterceptTouchEvent(false);
                commit(Math.round(position));
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false; getParent().requestDisallowInterceptTouchEvent(false);
                select(selected);
                return true;
            default: return true;
        }
    }
    private void select(int index) {
        selected = index;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setSelected(i == index);
        if (motion != null) motion.cancel();
        if (!NebulaMenuStyle.animated()) { position = index; stretch = 0; invalidate(); return; }
        final float from = position, initialStretch = stretch;
        motion = ValueAnimator.ofFloat(0, 1); motion.setDuration(250);
        motion.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT);
        motion.addUpdateListener(a -> {
            float progress = (float)a.getAnimatedValue();
            position = from + (index - from) * progress;
            stretch = initialStretch * (1 - progress); invalidate();
        });
        motion.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) getChildCount();
        float visual = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getChildCount() - 1 - position : position;
        float left = getPaddingLeft() + width * visual;
        bounds.set(left, getPaddingTop(), left + width, getHeight() - getPaddingBottom());
        float extra = width * stretch * .5f;
        bounds.left = Math.max(dp(1), bounds.left - extra);
        bounds.right = Math.min(getWidth() - dp(1), bounds.right + extra);
        if (glass != null) {
            glass.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
            glass.draw(canvas);
        }
        paint.setColor(Theme.multAlpha(color, glass == null ? .65f : .22f));
        canvas.drawRoundRect(bounds, dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(AndroidUtilities.dpf2(.6f));
        paint.setColor(dragging ? 0x80ffffff : 0x38ffffff); bounds.inset(1, 1); canvas.drawRoundRect(bounds, dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDetachedFromWindow() {
        if (motion != null) motion.cancel();
        position = selected; dragging = false; stretch = 0;
        super.onDetachedFromWindow();
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
}
