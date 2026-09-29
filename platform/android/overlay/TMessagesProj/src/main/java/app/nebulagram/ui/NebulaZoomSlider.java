package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Always-visible zoom ruler beneath the round-video preview. */
public final class NebulaZoomSlider extends View {
    public interface OnZoomChanged { void onZoomChanged(float factor); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OnZoomChanged callback;
    private float maximum = 2f;
    private float current = 1f;
    private float dragStartX;
    private float dragStartZoom;
    private boolean dragged;

    public NebulaZoomSlider(Context context, OnZoomChanged callback) {
        super(context);
        this.callback = callback;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription(NebulaText.text("Шкала увеличения", "Zoom ruler"));
    }

    public void setRange(float max) {
        maximum = Math.max(1.05f, Math.min(8f, max));
        current = Math.max(1f, Math.min(current, maximum));
        invalidate();
    }

    public void setCurrent(float factor) {
        current = Math.max(1f, Math.min(factor, maximum));
        setContentDescription(NebulaText.text("Шкала увеличения", "Zoom ruler") + " · "
                + String.format(java.util.Locale.US, "%.1f×", current));
        invalidate();
    }

    private float pixelsPerZoom() {
        return Math.max(1f, (getWidth() - dp(58)) / Math.max(.05f, Math.min(2f, maximum - 1f)));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = Theme.getColor(Theme.key_chat_messagePanelSend);
        int ink = Theme.getColor(Theme.key_chat_messagePanelText);
        float center = getWidth() / 2f;
        float middle = getHeight() / 2f;
        paint.setColor(0xE61B1B1E);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(dp(7), 0, dp(3), 0x55000000);
        canvas.drawRoundRect(dp(2), dp(3), getWidth() - dp(2), getHeight() - dp(3), dp(24), dp(24), paint);
        paint.clearShadowLayer();
        paint.setColor((ink & 0x00FFFFFF) | 0x77000000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(.8f));
        canvas.drawRoundRect(dp(2), dp(3), getWidth() - dp(2), getHeight() - dp(3), dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.FILL);

        // The scale travels under a fixed center needle, like the recording reference.
        canvas.save();
        canvas.clipRect(dp(17), dp(5), getWidth() - dp(17), getHeight() - dp(5));
        int ticks = (int) Math.ceil((maximum - 1f) * 12f);
        for (int index = 0; index <= ticks; index++) {
            float value = Math.min(maximum, 1f + index / 12f);
            float x = center + (value - current) * pixelsPerZoom();
            if (x < dp(20) || x > getWidth() - dp(20)) continue;
            boolean major = index % 12 == 0 || index == ticks;
            paint.setColor(major ? accent : ((ink & 0x00FFFFFF) | 0x99000000));
            paint.setStrokeWidth(dp(major ? 2 : 1));
            canvas.drawLine(x, middle - dp(13), x, middle + dp(major ? 5 : 1), paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(11));
        paint.setColor(accent);
        drawMark(canvas, "1", 1f, center, middle);
        if (maximum >= 1.95f) drawMark(canvas, "2", 2f, center, middle);
        if (maximum >= 5f) drawMark(canvas, "5", 5f, center, middle);
        if (maximum < 1.95f || maximum > 2.4f && Math.abs(maximum - 5f) > .3f) {
            String end = Math.abs(maximum - Math.round(maximum)) < .05f
                    ? Integer.toString(Math.round(maximum))
                    : Float.toString(Math.round(maximum * 10f) / 10f);
            drawMark(canvas, end, maximum, center, middle);
        }
        canvas.restore();
        paint.setColor(accent);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(5));
        canvas.drawLine(center, middle - dp(16), center, middle + dp(5), paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawMark(Canvas canvas, String label, float value, float center, float middle) {
        float x = center + (value - current) * pixelsPerZoom();
        if (x >= dp(24) && x <= getWidth() - dp(24)) canvas.drawText(label, x, middle + dp(18), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX();
                dragStartZoom = current;
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - dragStartX) > dp(2)) dragged = true;
                if (!dragged) return true;
                setCurrent(dragStartZoom - (event.getX() - dragStartX) / pixelsPerZoom());
                callback.onZoomChanged(current);
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragged) {
                    setCurrent(current + (event.getX() - getWidth() / 2f) / pixelsPerZoom());
                    callback.onZoomChanged(current);
                }
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
