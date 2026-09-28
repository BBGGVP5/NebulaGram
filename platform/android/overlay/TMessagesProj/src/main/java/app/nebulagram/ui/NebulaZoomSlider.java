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

    public NebulaZoomSlider(Context context, OnZoomChanged callback) {
        super(context);
        this.callback = callback;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Zoom ruler");
    }

    public void setRange(float max) {
        maximum = Math.max(1.05f, Math.min(8f, max));
        current = Math.max(1f, Math.min(current, maximum));
        invalidate();
    }

    public void setCurrent(float factor) {
        current = Math.max(1f, Math.min(factor, maximum));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = Theme.getColor(Theme.key_chat_messagePanelSend);
        int ink = Theme.getColor(Theme.key_chat_messagePanelText);
        float left = dp(29), right = getWidth() - dp(29);
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

        for (int index = 0; index <= 24; index++) {
            float value = 1f + (maximum - 1f) * index / 24f;
            float x = left + (right - left) * index / 24f;
            boolean major = index == 0 || index == 24 || Math.abs(value - 2f) < (maximum - 1f) / 48f
                    || Math.abs(value - 5f) < (maximum - 1f) / 48f;
            paint.setColor(major ? accent : ((ink & 0x00FFFFFF) | 0x99000000));
            paint.setStrokeWidth(dp(major ? 2 : 1));
            canvas.drawLine(x, middle - dp(13), x, middle + dp(major ? 5 : 1), paint);
        }

        float selected = left + (right - left) * (current - 1f) / (maximum - 1f);
        paint.setColor(accent);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(5));
        canvas.drawLine(selected, middle - dp(16), selected, middle + dp(5), paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(11));
        canvas.drawText("1", left, middle + dp(18), paint);
        if (maximum >= 1.95f) drawMark(canvas, "2", left + (right - left) / (maximum - 1f), middle);
        if (maximum >= 5f) drawMark(canvas, "5", left + (right - left) * 4f / (maximum - 1f), middle);
        if (maximum > 2.05f && maximum < 4.95f || maximum > 5.05f) {
            drawMark(canvas, Integer.toString(Math.round(maximum)), right, middle);
        }
    }

    private void drawMark(Canvas canvas, String label, float x, float middle) {
        canvas.drawText(label, x, middle + dp(18), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float left = dp(29), span = Math.max(1f, getWidth() - dp(58));
                setCurrent(1f + (maximum - 1f) * (event.getX() - left) / span);
                callback.onZoomChanged(current);
                return true;
            case MotionEvent.ACTION_UP:
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
