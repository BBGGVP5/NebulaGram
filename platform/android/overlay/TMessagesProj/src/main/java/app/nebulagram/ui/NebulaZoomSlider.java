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

/** Compact zoom presets that expand into a scrollable, camera-limited ruler. */
public final class NebulaZoomSlider extends View {
    public interface OnZoomChanged { void onZoomChanged(float factor); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OnZoomChanged callback;
    private final Runnable collapse = () -> setExpanded(false);
    private final Runnable longPress = () -> setExpanded(true);
    private float maximum = 2f;
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
        maximum = Math.max(1.05f, Math.min(8f, max));
        setCurrent(1f);
        setExpanded(false);
    }

    public void setCurrent(float factor) {
        current = Math.max(1f, Math.min(factor, maximum));
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

    private float compactWidth() { return Math.min(getWidth() - dp(4), dp(156)); }
    private float fullWidth() { return getWidth() - dp(4); }
    private float widthForProgress() { return compactWidth() + (fullWidth() - compactWidth()) * expansion; }
    private float pixelsPerOctave() {
        return Math.max(1f, (fullWidth() / 2f - dp(27)) / (float) (Math.log(maximum) / Math.log(2)));
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
        RectF capsule = new RectF(center - halfWidth, dp(3), center + halfWidth, getHeight() - dp(3));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xE626222E);
        paint.setShadowLayer(dp(7), 0, dp(3), 0x55000000);
        canvas.drawRoundRect(capsule, dp(24), dp(24), paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(.8f));
        paint.setColor(0x66FFFFFF);
        canvas.drawRoundRect(capsule, dp(24), dp(24), paint);
        paint.setStyle(Paint.Style.FILL);

        if (expansion < 1f) {
            paint.setColor((accent & 0x00FFFFFF) | ((int) (255 * (1f - expansion)) << 24));
            float activeX = current < Math.min(1.5f, maximum) ? capsule.left : center;
            RectF active = new RectF(activeX + dp(3), capsule.top + dp(3), activeX + compactWidth() / 2f - dp(3), capsule.bottom - dp(3));
            canvas.drawRoundRect(active, dp(22), dp(22), paint);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(15));
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (255 * (1f - expansion)));
            canvas.drawText("1×", center - compactWidth() / 4f, middle + dp(5), paint);
            String second = maximum >= 1.95f ? "2×" : String.format(java.util.Locale.US, "%.1f×", maximum);
            canvas.drawText(second, center + compactWidth() / 4f, middle + dp(5), paint);
        }

        if (expansion > 0f) {
            canvas.save();
            canvas.clipRect(capsule.left + dp(15), capsule.top + dp(3), capsule.right - dp(15), capsule.bottom - dp(3));
            int alpha = (int) (255 * expansion);
            int ticks = (int) Math.ceil(Math.log(maximum) / Math.log(2) * 5);
            for (int index = 0; index <= ticks; index++) {
                float zoom = Math.min(maximum, (float) Math.pow(2, index / 5f));
                float x = xForZoom(zoom);
                if (x < capsule.left + dp(16) || x > capsule.right - dp(16)) continue;
                boolean major = index % 5 == 0 || index == ticks;
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
            if (maximum >= 1.95f) drawMark(canvas, "2", 2f, capsule, middle);
            if (maximum >= 5f) drawMark(canvas, "5", 5f, capsule, middle);
            if (maximum >= 7.95f) drawMark(canvas, "8", 8f, capsule, middle);
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
        paint.setAlpha(255);
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
                if (!expanded && Math.abs(x - getWidth() / 2f) > compactWidth() / 2f + dp(8)) return false;
                removeCallbacks(collapse);
                dragStartX = x;
                dragStartZoom = current;
                dragged = false;
                if (!expanded) postDelayed(longPress, 250);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - dragStartX) > dp(6)) dragged = true;
                if (dragged) {
                    removeCallbacks(longPress);
                    setExpanded(true);
                    setCurrent((float) (dragStartZoom * Math.pow(2, (dragStartX - x) / pixelsPerOctave())));
                    callback.onZoomChanged(current);
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!dragged) {
                    if (expanded) setCurrent((float) (current * Math.pow(2, (x - getWidth() / 2f) / pixelsPerOctave())));
                    else setCurrent(x < getWidth() / 2f ? 1f : Math.min(2f, maximum));
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
