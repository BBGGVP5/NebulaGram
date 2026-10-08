package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** One moving selection surface, shared by touch and accessibility selection. */
final class NebulaEditorTabs extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final int color;
    private float position;
    private int selected;
    private ValueAnimator motion;
    NebulaEditorTabs(Context context, String[] names, int[] icons, int initial, java.util.function.IntConsumer choose) {
        super(context);
        setWillNotDraw(false);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        NebulaTheme theme = NebulaTheme.of(context);
        color = theme.primaryContainer();
        setBackground(Theme.createRoundRectDrawable(dp(28), Theme.multAlpha(theme.surfaceContainer(), .8f)));
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
            tab.setOnClickListener(v -> { if (selected != index) { select(index); choose.accept(index); } });
            addView(tab, new LayoutParams(0, -2, 1));
        }
    }
    private void select(int index) {
        selected = index;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setSelected(i == index);
        if (motion != null) motion.cancel();
        if (!NebulaMenuStyle.animated()) { position = index; invalidate(); return; }
        motion = ValueAnimator.ofFloat(position, index); motion.setDuration(230);
        motion.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT);
        motion.addUpdateListener(a -> { position = (float) a.getAnimatedValue(); invalidate(); });
        motion.start();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) getChildCount();
        float visual = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getChildCount() - 1 - position : position;
        float left = getPaddingLeft() + width * visual;
        bounds.set(left, getPaddingTop(), left + width, getHeight() - getPaddingBottom());
        paint.setColor(Theme.multAlpha(color, .85f)); canvas.drawRoundRect(bounds, dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(AndroidUtilities.dpf2(.6f));
        paint.setColor(0x28ffffff); bounds.inset(1, 1); canvas.drawRoundRect(bounds, dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDetachedFromWindow() {
        if (motion != null) motion.cancel();
        position = selected;
        super.onDetachedFromWindow();
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
}
