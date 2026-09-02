package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int style;
    private final float radius;

    public NebulaButton(@NonNull Context context, int style) {
        super(context);
        this.style = style;
        this.radius = NebulaTheme.cornerFull();

        NebulaTheme theme = NebulaTheme.of(context);
        setGravity(Gravity.CENTER);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
        setTypeface(AndroidUtilities.bold());

        if (style == STYLE_FILLED) {
            background.setColor(theme.primary());
            setTextColor(theme.onPrimary());
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            setLetterSpacing(0.02f);
            setMinimumHeight(AndroidUtilities.dp(52));
            setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
            setRipple(theme.onPrimary());
        } else {
            setTextColor(theme.primary());
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            setMinimumHeight(AndroidUtilities.dp(44));
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            setRipple(theme.primary());
        }
    }

    /**
     * Material 3 asks for a ripple bounded by the button's own shape; a
     * rectangular ripple on a fully rounded button is the tell-tale sign of a
     * control that was never restyled.
     */
    private void setRipple(int contentColor) {
        float[] corners = new float[8];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = radius;
        }
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(corners, null, null));
        setBackground(new RippleDrawable(
                ColorStateList.valueOf(NebulaTheme.stateLayer(contentColor, 0.12f)), null, mask));
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (style == STYLE_FILLED) {
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, radius, radius, background);
        }
        super.onDraw(canvas);
    }
}
