package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Camera-limited Cherrygram-style preset rail with independent step buttons and drag ruler. */
public final class NebulaZoomSlider extends View {
    public interface OnZoomChanged { void onZoomChanged(float factor); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OnZoomChanged callback;
    private final Runnable collapse = () -> setExpanded(false);
    private final Runnable longPress = () -> setExpanded(true);
    private float maximum = 2f;
    private float minimum = 1f;
    private float current = 1f;
    private float dragStartX;
    private float dragStartZoom;
    private boolean dragged;
    private boolean expanded;
    private float expansion;
    private ValueAnimator expansionAnimator;

    public NebulaZoomSlider(Context context, OnZoomChanged callback) {
        super(context);
        this.callback = callback;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription(NebulaText.text("Увеличение видео", "Video zoom"));
    }

    public void setRange(float max) {
        maximum = Math.max(1.05f, Math.min(10f, max));
        setCurrent(1f);
        setExpanded(false);
    }

    public void setCurrent(float factor) {
        current = Math.max(minimum, Math.min(factor, maximum));
        setContentDescription(NebulaText.text("Увеличение видео", "Video zoom") + " · "
                + String.format(java.util.Locale.US, "%.1f×", current));
        invalidate();
    }

    private void setExpanded(boolean value) {
        removeCallbacks(collapse);
        if (expanded == value) return;
        expanded = value;
        if (expansionAnimator != null) expansionAnimator.cancel();
        expansionAnimator = ValueAnimator.ofFloat(expansion, value ? 1f : 0f);
        expansionAnimator.setDuration(180);
        expansionAnimator.addUpdateListener(animation -> {
            expansion = (float) animation.getAnimatedValue();
            invalidate();
        });
        expansionAnimator.start();
    }

    private void scheduleCollapse() {
        removeCallbacks(collapse);
        postDelayed(collapse, 1400);
    }

    private float compactWidth() { return Math.min(fullWidth(), dp(maximum >= 3 ? 176 : 120)); }
    private float fullWidth() { return getWidth() - dp(62); }
    private float widthForProgress() { return compactWidth() + (fullWidth() - compactWidth()) * expansion; }
    private float pixelsPerOctave() {
        return Math.max(1f, (fullWidth() / 2f - dp(27)) / (float) (Math.log(maximum / minimum) / Math.log(2)));
    }
    private float xForZoom(float zoom) {
        return getWidth() / 2f + (float) (Math.log(zoom / current) / Math.log(2)) * pixelsPerOctave();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = Theme.getColor(Theme.key_chat_messagePanelSend);
        int ink = Theme.getColor(Theme.key_chat_messagePanelText);
        float center = getWidth() / 2f;
        float middle = getHeight() / 2f;
        float halfWidth = widthForProgress() / 2f;
        RectF capsule = new RectF(center - halfWidth, dp(6), center + halfWidth, getHeight() - dp(6));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF023222B);
        paint.setShadowLayer(dp(7), 0, dp(3), 0x55000000);
        canvas.drawRoundRect(capsule, dp(24), dp(24), paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(.8f));
        paint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(capsule, dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.FILL);

        if (expansion < 1f) drawPresets(canvas, capsule, accent, 1f - expansion);

        if (expansion > 0f) {
            canvas.save();
            canvas.clipRect(capsule.left + dp(15), capsule.top + dp(3), capsule.right - dp(15), capsule.bottom - dp(3));
            int alpha = (int) (255 * expansion);
            int ticks = (int) Math.ceil(Math.log(maximum / minimum) / Math.log(2) * 8);
            for (int index = 0; index <= ticks; index++) {
                float zoom = Math.min(maximum, (float) (minimum * Math.pow(2, index / 8f)));
                float x = xForZoom(zoom);
                if (x < capsule.left + dp(16) || x > capsule.right - dp(16)) continue;
                boolean major = index % 8 == 0 || index == ticks;
                paint.setColor(major ? accent : ((ink & 0x00FFFFFF) | 0xA0000000));
                paint.setAlpha(alpha);
                paint.setStrokeWidth(dp(major ? 2 : 1));
                canvas.drawLine(x, middle - dp(13), x, middle + dp(major ? 5 : 1), paint);
            }
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(11));
            paint.setColor(accent);
            paint.setAlpha(alpha);
            drawMark(canvas, "1", 1f, capsule, middle);
            if (maximum >= 2f) drawMark(canvas, "2", 2f, capsule, middle);
            if (maximum >= 3f) drawMark(canvas, "3", 3f, capsule, middle);
            if (maximum >= 5f) drawMark(canvas, "5", 5f, capsule, middle);
            if (maximum >= 10f) drawMark(canvas, "10", 10f, capsule, middle);
            else if (maximum < 1.95f || maximum > 2.4f && Math.abs(maximum - 5f) > .3f) {
                drawMark(canvas, String.format(java.util.Locale.US, "%.1f", maximum), maximum, capsule, middle);
            }
            canvas.restore();
            paint.setColor(accent);
            paint.setAlpha(alpha);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(5));
            canvas.drawLine(center, middle - dp(16), center, middle + dp(5), paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
        drawStep(canvas, capsule.left - dp(18), middle, false, accent);
        drawStep(canvas, capsule.right + dp(18), middle, true, accent);
        paint.setAlpha(255);
    }

    private float[] presets() {
        if (maximum < 1.95f) return new float[]{minimum, maximum};
        if (maximum < 3f) return new float[]{minimum, 2f};
        if (maximum < 10f) return new float[]{minimum, 3f, maximum};
        return new float[]{minimum, 3f, 10f};
    }
    private String label(float value) {
        return Math.abs(value - Math.round(value)) < .04f ? Math.round(value) + "×"
                : String.format(java.util.Locale.US, "%.1f×", value);
    }
    private void drawPresets(Canvas canvas, RectF capsule, int accent, float opacity) {
        float[] values = presets();
        int selected = 0;
        for (int i = 1; i < values.length; i++) if (Math.abs(values[i] - current) < Math.abs(values[selected] - current)) selected = i;
        float segment = capsule.width() / values.length;
        float x = capsule.left + segment * selected + segment / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent); paint.setAlpha(Math.round(255 * opacity));
        float radius = Math.min(dp(18), (segment - dp(4)) / 2f);
        canvas.drawCircle(x, capsule.centerY(), radius, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(12));
        for (int i = 0; i < values.length; i++) {
            paint.setColor(i == selected ? 0xFFFFFFFF : 0xFFE5E5EB);
            paint.setAlpha(Math.round(255 * opacity));
            canvas.drawText(i == selected ? label(current) : label(values[i]), capsule.left + segment * (i + .5f), capsule.centerY() + dp(4), paint);
        }
    }
    private void drawStep(Canvas canvas, float x, float y, boolean plus, int accent) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xE626222E); paint.setShadowLayer(dp(5), 0, dp(2), 0x55000000);
        canvas.drawCircle(x, y, dp(15), paint); paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(accent); paint.setAlpha(255);
        canvas.drawLine(x - dp(5), y, x + dp(5), y, paint);
        if (plus) canvas.drawLine(x, y - dp(5), x, y + dp(5), paint);
        paint.setStyle(Paint.Style.FILL); paint.setStrokeCap(Paint.Cap.BUTT);
    }
    private float step(boolean plus) {
        float delta = current < 2f ? .1f : current < 5f ? .5f : 1f;
        float next = Math.round((current + (plus ? delta : -delta)) / delta) * delta;
        return Math.max(minimum, Math.min(maximum, next));
    }
    private float presetAt(float x) {
        float[] values = presets(); float left = (getWidth() - compactWidth()) / 2f;
        int index = Math.min(values.length - 1, Math.max(0, (int) ((x - left) * values.length / compactWidth())));
        return values[index];
    }

    private void drawMark(Canvas canvas, String label, float zoom, RectF capsule, float middle) {
        float x = xForZoom(zoom);
        if (x >= capsule.left + dp(20) && x <= capsule.right - dp(20)) {
            canvas.drawText(label, x, middle + dp(18), paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!expanded && Math.abs(x - getWidth() / 2f) > compactWidth() / 2f + dp(36)) return false;
                removeCallbacks(collapse);
                dragStartX = x;
                dragStartZoom = current;
                dragged = false;
                if (!expanded && Math.abs(x - getWidth() / 2f) < compactWidth() / 2f) postDelayed(longPress, 250);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - dragStartX) > dp(6)) dragged = true;
                if (dragged && Math.abs(dragStartX - getWidth() / 2f) < compactWidth() / 2f) {
                    removeCallbacks(longPress);
                    setExpanded(true);
                    setCurrent((float) (dragStartZoom * Math.pow(2, (dragStartX - x) / pixelsPerOctave())));
                    callback.onZoomChanged(current);
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!dragged) {
                    float half = widthForProgress() / 2f;
                    if (x < getWidth() / 2f - half) setCurrent(step(false));
                    else if (x > getWidth() / 2f + half) setCurrent(step(true));
                    else if (expanded) setCurrent((float) (current * Math.pow(2, (x - getWidth() / 2f) / pixelsPerOctave())));
                    else setCurrent(presetAt(x));
                    callback.onZoomChanged(current);
                }
                scheduleCollapse();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                scheduleCollapse();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
