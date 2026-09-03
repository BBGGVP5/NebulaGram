package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
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

    public NebulaOnboardingLayout(Context context) {
        super(context);
        setBackgroundColor(NebulaTheme.of(context).surface());
        setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(AndroidUtilities.dp(24), AndroidUtilities.statusBarHeight + AndroidUtilities.dp(20),
                AndroidUtilities.dp(24), AndroidUtilities.dp(16));
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
        content.setPadding(0, AndroidUtilities.dp(24), 0, AndroidUtilities.dp(32));
        column.addView(content, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, 1f));

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        column.addView(actions, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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
