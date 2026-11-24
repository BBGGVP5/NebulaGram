package app.nebulagram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/** Shared pieces of the dark, icon-tinted "About" and "App icon" screens. */
public final class NebulaBrandUi {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0x99FFFFFF;
    public static final int CARD = 0x17FFFFFF;
    public static final int DIVIDER = 0x14FFFFFF;

    private NebulaBrandUi() { }

    static int dp(float value) { return AndroidUtilities.dp(value); }

    public static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    /** A pressable background: the shape plus a light ripple clipped to it. */
    public static Drawable pressable(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), rounded(color, radiusDp), rounded(0xFFFFFFFF, radiusDp));
    }

    public static TextView text(Context context, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        view.setIncludeFontPadding(false);
        return view;
    }

    /** Round frosted button with the back arrow, in place of the action bar's. */
    public static View backButton(Context context, Runnable onBack) {
        FrameLayout button = new FrameLayout(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(0x52000000);
        shape.setStroke(Math.max(1, dp(0.66f)), 0x29FFFFFF);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask));
        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_ab_back);
        arrow.setColorFilter(new PorterDuffColorFilter(TEXT, PorterDuff.Mode.SRC_IN));
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        button.addView(arrow, new FrameLayout.LayoutParams(-1, -1));
        button.setContentDescription(org.telegram.messenger.LocaleController.getString(R.string.AccDescrGoBack));
        button.setOnClickListener(v -> onBack.run());
        return button;
    }

    /** The fixed bar: back button on the left, the title centered on the screen. */
    public static FrameLayout topBar(Context context, String title, Runnable onBack) {
        FrameLayout bar = new FrameLayout(context);
        bar.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
        FrameLayout.LayoutParams back = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.START | Gravity.CENTER_VERTICAL);
        back.leftMargin = dp(10);
        bar.addView(backButton(context, onBack), back);
        TextView label = text(context, 20, TEXT, true);
        label.setText(title);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        params.leftMargin = params.rightMargin = dp(64);
        bar.addView(label, params);
        bar.setMinimumHeight(AndroidUtilities.statusBarHeight + dp(60));
        return bar;
    }
}
