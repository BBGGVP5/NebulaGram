package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * "Photo counter" on an expanded profile photo: a glass pill whose digits roll to the next page.
 * Telegram shows its flat "3/25" pill only above 20 photos; with the setting on it appears for any
 * gallery of two or more. Setting "profile_photo_counter", off by default.
 */
public final class NebulaPhotoCounter {
    public static final String KEY = "profile_photo_counter";
    private final View host;
    private final AnimatedTextView.AnimatedTextDrawable text;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float shineTop = Float.NaN, shineBottom;
    private String shown;

    public static boolean enabled() { return NebulaMessagePreferences.enabled(KEY, false); }

    /** Whether the pill shows: Telegram's own rule when off, any gallery of two or more when on. */
    public static boolean visible(boolean enabled, boolean pulledDown, int count, boolean telegram) {
        return enabled ? pulledDown && count > 1 : telegram;
    }
    public static boolean visible(boolean pulledDown, int count, boolean telegram) { return visible(enabled(), pulledDown, count, telegram); }

    /** Digits that changed roll; "3/12" to "4/12" moves only the first number. */
    static boolean animate(String previous, String next, boolean visible) {
        return visible && previous != null && next != null && !previous.equals(next);
    }

    public NebulaPhotoCounter(View host) {
        this.host = host;
        text = new AnimatedTextView.AnimatedTextDrawable(false, true, false);
        text.setTextSize(AndroidUtilities.dp(15));
        text.setTextColor(0xFFFFFFFF);
        text.setTypeface(AndroidUtilities.bold());
        text.setGravity(Gravity.CENTER);
        text.setAnimationProperties(.35f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        text.setCallback(new Drawable.Callback() {
            @Override public void invalidateDrawable(Drawable who) { host.invalidate(); }
            @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { host.postDelayed(what, Math.max(0, when - android.os.SystemClock.uptimeMillis())); }
            @Override public void unscheduleDrawable(Drawable who, Runnable what) { host.removeCallbacks(what); }
        });
        fill.setColor(0x38000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(0.8f)));
        stroke.setColor(0x52FFFFFF);
    }

    public void setText(String title, boolean visible) {
        boolean animated = animate(shown, title, visible);
        shown = title;
        text.setText(title, animated);
    }

    /** Width of the pill for the current text, with its side padding. */
    public float width(float plainTextWidth) {
        return Math.max(plainTextWidth, text.getCurrentWidth()) + AndroidUtilities.dp(22);
    }

    public void draw(Canvas canvas, RectF rect) {
        float radius = rect.height() / 2f;
        canvas.drawRoundRect(rect, radius, radius, fill);
        if (shineTop != rect.top || shineBottom != rect.bottom) {
            shineTop = rect.top; shineBottom = rect.bottom;
            shine.setShader(new LinearGradient(0, rect.top, 0, rect.centerY(), 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(rect, radius, radius, shine);
        float inset = stroke.getStrokeWidth() / 2f;
        canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, radius, radius, stroke);
        text.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
        text.draw(canvas);
    }
}
