package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

/** A local vector aligned to the name's font metrics, independent of emoji fonts. */
public final class NebulaBadgeSpan extends ReplacementSpan {
    private final Drawable drawable;

    public NebulaBadgeSpan(Drawable drawable) {
        this.drawable = drawable.mutate();
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
        drawable.draw(canvas);
        canvas.restoreToCount(save);
    }
}
