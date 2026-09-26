package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.style.ReplacementSpan;
import android.view.View;

import java.lang.ref.WeakReference;

/** Local artwork aligned to the name's font metrics, independent of emoji fonts. */
public final class NebulaBadgeSpan extends ReplacementSpan {
    private final Drawable drawable;
    private final WeakReference<View> host;
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NebulaBadgeSpan(Drawable drawable) {
        this(drawable, null);
    }

    public NebulaBadgeSpan(Drawable drawable, View host) {
        this.drawable = drawable.mutate();
        this.host = new WeakReference<>(host);
    }

    @Override public int getSize(Paint paint, CharSequence text, int start, int end,
                                 Paint.FontMetricsInt metrics) {
        Paint.FontMetricsInt font = paint.getFontMetricsInt();
        if (metrics != null) {
            metrics.top = font.top; metrics.ascent = font.ascent;
            metrics.descent = font.descent; metrics.bottom = font.bottom;
        }
        return Math.max(1, font.descent - font.ascent);
    }

    @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
                               float x, int top, int y, int bottom, Paint paint) {
        Paint.FontMetricsInt font = paint.getFontMetricsInt();
        int size = Math.max(1, font.descent - font.ascent);
        drawable.setBounds(0, 0, size, size);
        drawable.setAlpha(paint.getAlpha());
        int save = canvas.save();
        canvas.translate(x, y + font.ascent);
        View view = host.get();
        if (view != null && view.isAttachedToWindow()) view.postInvalidateOnAnimation();
        float pulse = (float) ((Math.sin(SystemClock.uptimeMillis() * (Math.PI * 2 / 1800.0)) + 1) * .5);
        glow.setColor(Color.rgb(74, 211, 255));
        glow.setAlpha(Math.round((10 + 24 * pulse) * paint.getAlpha() / 255f));
        canvas.drawCircle(size * .5f, size * .5f, size * (.48f + .18f * pulse), glow);
        drawable.draw(canvas);
        canvas.restoreToCount(save);
    }
}
