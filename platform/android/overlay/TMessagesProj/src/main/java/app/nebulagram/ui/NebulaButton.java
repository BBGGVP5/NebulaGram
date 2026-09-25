package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * A Material 3 button: filled for the primary action, plain text for the
 * secondary one.
 *
 * <p>Written as a small view rather than pulled from Material Components,
 * because adding that library to Telegram's module would mean another
 * dependency to reconcile on every upstream release, for two button styles.
 */
public class NebulaButton extends TextView {

    /** Filled: the one action a screen wants you to take. */
    public static final int STYLE_FILLED = 0;
    /** Text: secondary actions, "skip" and the like. */
    public static final int STYLE_TEXT = 1;
    /** Outlined container: a compact utility action such as changing language. */
    public static final int STYLE_OUTLINED = 2;
    /** Soft accent container: secondary navigation such as skipping an intro. */
    public static final int STYLE_TONAL = 3;

    private final int style;
    private final float radius;

    public NebulaButton(@NonNull Context context, int style) {
        super(context);
        this.style = style;
        this.radius = AndroidUtilities.dp(14);

        NebulaTheme theme = NebulaTheme.of(context);
        setGravity(Gravity.CENTER);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
        setTypeface(AndroidUtilities.bold());

        if (style == STYLE_FILLED) {
            setTextColor(theme.onPrimary());
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            setLetterSpacing(0);
            setMinimumHeight(AndroidUtilities.dp(52));
            setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
            setRipple(theme.onPrimary(), theme.primary(), 0);
        } else if (style == STYLE_OUTLINED) {
            setTextColor(theme.onSurface());
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            setMinimumHeight(AndroidUtilities.dp(44));
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            setRipple(theme.primary(), theme.surfaceContainer(), theme.outline());
        } else if (style == STYLE_TONAL) {
            setTextColor(theme.onPrimaryContainer());
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            setMinimumHeight(AndroidUtilities.dp(44));
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            setRipple(theme.primary(), theme.primaryContainer(), 0);
        } else {
            setTextColor(theme.primary());
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            setMinimumHeight(AndroidUtilities.dp(44));
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            setRipple(theme.primary(), 0, 0);
        }
    }

    /**
     * Material 3 asks for a ripple bounded by the button's own shape; a
     * rectangular ripple on a fully rounded button is the tell-tale sign of a
     * control that was never restyled.
     */
    private void setRipple(int contentColor, int fillColor, int strokeColor) {
        float[] corners = new float[8];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = radius;
        }
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(corners, null, null));

        // Заливка — обычный drawable, а не рисование в onDraw: так её видно
        // всегда, а ripple остаётся ограничен формой кнопки.
        GradientDrawable fill = null;
        if (fillColor != 0) {
            fill = new GradientDrawable();
            fill.setCornerRadius(radius);
            fill.setColor(fillColor);
            if (strokeColor != 0) {
                fill.setStroke(AndroidUtilities.dp(1), strokeColor);
            }
        }
        setBackground(new RippleDrawable(
                ColorStateList.valueOf(NebulaTheme.stateLayer(contentColor, 0.14f)), fill, mask));
        setClickable(true);
        setFocusable(true);
    }

}
