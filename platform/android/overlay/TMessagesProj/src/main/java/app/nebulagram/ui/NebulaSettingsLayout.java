package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.ui.ActionBar.ActionBar;

/** One native bar. Scrolling forms reserve its inset and sample only the content beneath it. */
public final class NebulaSettingsLayout extends FrameLayout {
    private int linkSection = -100;
    private final ActionBar bar;
    private final View body;
    private final boolean overlayHeader;
    private final android.widget.ScrollView scroll;
    private final int initialTopPadding;
    private final android.view.ViewTreeObserver.OnScrollChangedListener scrollListener = this::updateTitle;
    private NebulaSettingsBackdrop backdrop;
    private final android.graphics.Paint fallbackPaint = new android.graphics.Paint();

    public static View wrap(Context context, ActionBar bar, View body) {
        return new NebulaSettingsLayout(context, bar, body);
    }

    public static View wrap(Context context, ActionBar bar, View body, int section) {
        NebulaSettingsLayout layout = new NebulaSettingsLayout(context, bar, body);
        layout.linkSection = section;
        return layout;
    }

    private NebulaSettingsLayout(Context context, ActionBar bar, View body) {
        super(context);
        this.bar = bar;
        this.body = body;
        scroll = findScroll(body);
        overlayHeader = scroll != null && NebulaProfileGlass.supported();
        initialTopPadding = scroll == null ? 0 : scroll.getPaddingTop();
        if (scroll != null) scroll.setClipToPadding(false);
        if (overlayHeader) bar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setBackgroundColor(NebulaTheme.of(context).surface());
        setClipChildren(true);
        // Telegram must not add a second sibling bar or reserve its height twice.
        bar.setAddToContainer(false);
        if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        if (body.getParent() instanceof ViewGroup) ((ViewGroup) body.getParent()).removeView(body);
        addView(body);
        addView(bar);
        android.widget.ImageView back = bar.getBackButton();
        if (back != null) {
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(NebulaTheme.of(context).surfaceContainer());
            android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(NebulaTheme.stateLayer(NebulaTheme.of(context).onSurface(), .12f)), circle, null);
            back.setBackground(new android.graphics.drawable.InsetDrawable(ripple, org.telegram.messenger.AndroidUtilities.dp(7)));
        }
    }

    /** The section page owns a full-size wrapper around its only scrolling child. */
    private static android.widget.ScrollView findScroll(View view) {
        if (view instanceof android.widget.ScrollView) return (android.widget.ScrollView) view;
        if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() == 1)
            return findScroll(((ViewGroup) view).getChildAt(0));
        return null;
    }
    private void updateTitle() {
        float alpha = 1f;
        if (scroll != null && scroll.getChildCount() == 1 && scroll.getChildAt(0) instanceof ViewGroup) {
            ViewGroup column = (ViewGroup) scroll.getChildAt(0);
            if (column.getChildCount() > 0 && column.getChildAt(0) instanceof NebulaSettingsHero) {
                int anchor = ((NebulaSettingsHero) column.getChildAt(0)).titleScrollAnchor();
                float range = org.telegram.messenger.AndroidUtilities.dp(24);
                alpha = Math.max(0, Math.min(1, (scroll.getScrollY() - anchor + range) / range));
            }
        }
        if (bar.getTitleTextView() != null) bar.getTitleTextView().setAlpha(alpha);
        if (bar.getTitleTextView2() != null) bar.getTitleTextView2().setAlpha(alpha);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (scroll != null) scroll.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
    }
    @Override protected void onDetachedFromWindow() {
        if (scroll != null && scroll.getViewTreeObserver().isAlive()) scroll.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        super.onDetachedFromWindow();
    }

    @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
        if (!overlayHeader) { super.dispatchDraw(canvas); return; }
        drawChild(canvas, body, getDrawingTime());
        int height = bar.getMeasuredHeight();
        if (canvas.isHardwareAccelerated() && NebulaProfileGlass.supported()) {
            if (backdrop == null) backdrop = new NebulaSettingsBackdrop(NebulaTheme.of(getContext()).surface());
            backdrop.draw(canvas, body, getWidth(), height);
        } else {
            fallbackPaint.setColor(NebulaTheme.of(getContext()).surface() | 0xFF000000);
            canvas.drawRect(0, 0, getWidth(), height, fallbackPaint);
        }
        drawChild(canvas, bar, getDrawingTime());
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        bar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        int top = bar.getVisibility() == GONE ? 0 : bar.getMeasuredHeight();
        if (overlayHeader) scroll.setPadding(scroll.getPaddingLeft(), initialTopPadding + top, scroll.getPaddingRight(), scroll.getPaddingBottom());
        body.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - (overlayHeader ? 0 : top)), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = bar.getVisibility() == GONE ? 0 : bar.getMeasuredHeight();
        bar.layout(0, 0, getMeasuredWidth(), top);
        int bodyTop = overlayHeader ? 0 : top;
        body.layout(0, bodyTop, getMeasuredWidth(), bodyTop + body.getMeasuredHeight());
        updateTitle();
        if (linkSection != -100) NebulaSettingsLinks.bind(body, linkSection);
    }
}
