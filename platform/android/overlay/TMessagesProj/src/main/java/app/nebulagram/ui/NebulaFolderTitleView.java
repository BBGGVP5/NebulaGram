package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

/** Attached native text views preserve animated emoji while titles exchange places. */
public final class NebulaFolderTitleView extends FrameLayout {
    private SimpleTextView current, outgoing;
    private ValueAnimator animator;
    private int textColor;
    private float statusAlpha = 1f;
    private boolean showStatus = true;
    private Runnable onAnimationUpdate;

    public NebulaFolderTitleView(Context context) {
        super(context);
        setClipChildren(true);
        current = createTitle();
    }

    private SimpleTextView createTitle() {
        SimpleTextView view = new SimpleTextView(getContext());
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextSize(20);
        view.setWidthWrapContent(true);
        view.setEllipsizeByGradient(true);
        view.setTextColor(textColor);
        addView(view, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        return view;
    }

    public CharSequence getText() { return current.getText(); }
    public void setText(CharSequence text) { current.setText(text); }
    public void setTextColor(int color) {
        textColor = color;
        current.setTextColor(color);
        if (outgoing != null) outgoing.setTextColor(color);
    }
    public float getStatusAlpha() { return statusAlpha; }
    public void setOnAnimationUpdate(Runnable callback) { onAnimationUpdate = callback; }

    public void setTitle(CharSequence text, int cacheType, boolean statusVisible, boolean animated) {
        boolean changed = !TextUtils.equals(current.getText(), text) || showStatus != statusVisible;
        if (!changed) { current.setEmojiCacheType(cacheType); return; }
        if (animator != null) { animator.cancel(); animator = null; }
        if (outgoing != null) { removeView(outgoing); outgoing = null; }
        showStatus = statusVisible;
        if (!animated || !isAttachedToWindow() || getWidth() == 0) {
            current.setText(text);
            current.setEmojiCacheType(cacheType);
            current.setAlpha(1f);
            current.setTranslationY(0);
            statusAlpha = showStatus ? 1f : 0f;
            update();
            return;
        }
        outgoing = current;
        final SimpleTextView old = outgoing;
        final float oldAlpha = old.getAlpha(), oldY = old.getTranslationY();
        current = createTitle();
        current.setText(text);
        current.setEmojiCacheType(cacheType);
        current.setAlpha(0);
        current.setTranslationY(-AndroidUtilities.dp(20));
        final float fromStatus = statusAlpha, toStatus = showStatus ? 1f : 0f;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(240);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            old.setAlpha(oldAlpha * (1 - p));
            old.setTranslationY(oldY + AndroidUtilities.dp(20) * p);
            current.setAlpha(p);
            current.setTranslationY(-AndroidUtilities.dp(20) * (1 - p));
            statusAlpha = fromStatus + (toStatus - fromStatus) * p;
            if (p == 1 && outgoing == old) { removeView(old); outgoing = null; }
            update();
        });
        animator.start();
    }

    private void update() {
        if (onAnimationUpdate != null) onAnimationUpdate.run();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        if (outgoing != null) { removeView(outgoing); outgoing = null; }
        current.setAlpha(1f);
        current.setTranslationY(0);
        statusAlpha = showStatus ? 1f : 0f;
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.min(MeasureSpec.getSize(widthSpec), AndroidUtilities.displaySize.x / 2);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightSpec);
    }
}
