package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.telegram.ui.ActionBar.ActionBar;

/** Settings own one native bar and lay content below it, never behind it. */
public final class NebulaSettingsLayout extends FrameLayout {
    private int linkSection = -100;
    private final ActionBar bar;
    private final View body;

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
        setBackgroundColor(NebulaTheme.of(context).surface());
        setClipChildren(true);
        // Telegram must not add a second sibling bar or reserve its height twice.
        bar.setAddToContainer(false);
        if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
        if (body.getParent() instanceof ViewGroup) ((ViewGroup) body.getParent()).removeView(body);
        addView(body);
        addView(bar);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        bar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        int top = bar.getVisibility() == GONE ? 0 : bar.getMeasuredHeight();
        body.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, height - top), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = bar.getVisibility() == GONE ? 0 : bar.getMeasuredHeight();
        bar.layout(0, 0, getMeasuredWidth(), top);
        body.layout(0, top, getMeasuredWidth(), top + body.getMeasuredHeight());
        if (linkSection != -100) NebulaSettingsLinks.bind(body, linkSection);
    }
}
