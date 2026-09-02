package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * One line of a NebulaLink screen, in the Material 3 list-item shape: a rounded
 * icon container on the left, title over an optional subtitle, and a trailing
 * value, chevron or switch.
 *
 * <p>The rows are built from the schema the Go core returns, so this class
 * knows nothing about individual settings — only how a row looks.
 */
public class NebulaRow extends FrameLayout {

    /** Trailing element. */
    public static final int TRAIL_NONE = 0;
    public static final int TRAIL_CHEVRON = 1;
    public static final int TRAIL_SWITCH = 2;

    private final NebulaTheme theme;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView icon;
    private Switch toggle;

    public NebulaRow(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);

        setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        setForeground(new RippleDrawable(
                ColorStateList.valueOf(NebulaTheme.stateLayer(theme.onSurface(), 0.08f)), null, null));

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(theme.primary(), PorterDuff.Mode.SRC_IN);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setCornerRadius(NebulaTheme.cornerSmall());
        iconBackground.setColor(NebulaTheme.stateLayer(theme.primary(), 0.14f));
        icon.setBackground(iconBackground);
        icon.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9),
                AndroidUtilities.dp(9), AndroidUtilities.dp(9));

        LayoutParams iconParams = new LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
        iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        addView(icon, iconParams);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTextColor(theme.onSurface());
        title.setTypeface(AndroidUtilities.bold());
        text.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(theme.onSurfaceVariant());
        subtitle.setLineSpacing(AndroidUtilities.dp(1), 1f);
        subtitle.setVisibility(GONE);
        text.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams textParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textParams.leftMargin = AndroidUtilities.dp(64);
        textParams.rightMargin = AndroidUtilities.dp(36);
        addView(text, textParams);
    }

    public NebulaRow icon(int resource) {
        if (resource == 0) {
            icon.setVisibility(GONE);
        } else {
            icon.setVisibility(VISIBLE);
            icon.setImageResource(resource);
        }
        return this;
    }

    public NebulaRow title(CharSequence value) {
        title.setText(value);
        return this;
    }

    /**
     * The line under the title. In this schema a row's current value and its
     * explanation share that line: the value comes first and takes the accent
     * colour, so a screen can be read by scanning the coloured words.
     */
    public NebulaRow subtitle(CharSequence value, boolean isValue) {
        if (value == null || value.length() == 0) {
            subtitle.setVisibility(GONE);
            return this;
        }
        subtitle.setVisibility(VISIBLE);
        subtitle.setText(value);
        subtitle.setTextColor(isValue ? theme.primary() : theme.onSurfaceVariant());
        return this;
    }

    public NebulaRow destructive() {
        title.setTextColor(0xFFFF6E6E);
        icon.setColorFilter(0xFFFF6E6E, PorterDuff.Mode.SRC_IN);
        return this;
    }

    public NebulaRow trailing(int kind) {
        if (kind == TRAIL_CHEVRON) {
            ImageView chevron = new ImageView(getContext());
            chevron.setImageResource(org.telegram.messenger.R.drawable.msg_arrowright);
            chevron.setColorFilter(theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN);
            chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LayoutParams params = new LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            addView(chevron, params);
        } else if (kind == TRAIL_SWITCH) {
            toggle = new Switch(getContext());
            toggle.setClickable(false);
            toggle.setFocusable(false);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            addView(toggle, params);
        }
        return this;
    }

    /** Sets the switch state without firing a listener. */
    public NebulaRow checked(boolean value) {
        if (toggle != null) {
            toggle.setChecked(value);
        }
        return this;
    }

    public boolean isChecked() {
        return toggle != null && toggle.isChecked();
    }

    /** Flips the switch, for a row whose whole surface is the control. */
    public boolean toggleChecked() {
        if (toggle == null) {
            return false;
        }
        toggle.setChecked(!toggle.isChecked());
        return toggle.isChecked();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int minimum = subtitle.getVisibility() == VISIBLE
                ? AndroidUtilities.dp(72) : AndroidUtilities.dp(56);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int height = Math.max(minimum, getMeasuredHeight());
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /** A hairline divider drawn between rows inside one card. */
    public static View divider(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        View line = new View(context);
        line.setBackgroundColor(NebulaTheme.stateLayer(theme.outline(), 0.35f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, AndroidUtilities.dp(0.5f)));
        params.leftMargin = AndroidUtilities.dp(64);
        line.setLayoutParams(params);
        return line;
    }
}
