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
    private final int initialTopPadding;
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
        overlayHeader = body instanceof android.widget.ScrollView && NebulaProfileGlass.supported();
        initialTopPadding = body.getPaddingTop();
        if (overlayHeader) bar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setBackgroundColor(NebulaTheme.of(context).surface());
        setClipChildren(true);
        // Telegram must not add a second sibling bar or reserve its height twice.
        bar.setAddToContainer(false);
        if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        if (body.getParent() instanceof ViewGroup) ((ViewGroup) body.getParent()).removeView(body);
        addView(body);
        addView(bar);
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
        if (overlayHeader) body.setPadding(body.getPaddingLeft(), initialTopPadding + top, body.getPaddingRight(), body.getPaddingBottom());
        body.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - (overlayHeader ? 0 : top)), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = bar.getVisibility() == GONE ? 0 : bar.getMeasuredHeight();
        bar.layout(0, 0, getMeasuredWidth(), top);
        int bodyTop = overlayHeader ? 0 : top;
        body.layout(0, bodyTop, getMeasuredWidth(), bodyTop + body.getMeasuredHeight());
        if (linkSection != -100) NebulaSettingsLinks.bind(body, linkSection);
    }
}
