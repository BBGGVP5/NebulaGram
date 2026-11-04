package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;

/** Material 3 Expressive seek bar: a travelling wave while playing, a flat track when paused. */
public final class NebulaWaveSeekBar extends View {
    public interface Listener {
        void onSeek(float progress);
        default void onDrag(float progress) { }
    }
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG), inactive = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final RectF rect = new RectF();
    private float progress, buffered = 1f, amplitude, phase;
    private boolean playing, dragging;
    private long lastFrame;
    private Listener listener;

    public NebulaWaveSeekBar(Context context) {
        super(context);
        active.setStyle(Paint.Style.STROKE); active.setStrokeCap(Paint.Cap.ROUND); active.setStrokeWidth(AndroidUtilities.dp(4));
        inactive.setStyle(Paint.Style.STROKE); inactive.setStrokeCap(Paint.Cap.ROUND); inactive.setStrokeWidth(AndroidUtilities.dp(4));
        setColors(0xffffffff, 0x55ffffff);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setColors(int activeColor, int trackColor) {
        active.setColor(activeColor); thumb.setColor(activeColor); inactive.setColor(trackColor); invalidate();
    }
    public void setProgress(float value) {
        if (dragging) return;
        progress = Math.max(0, Math.min(1, value)); invalidate();
    }
    public void setBuffered(float value) { buffered = Math.max(0, Math.min(1, value)); invalidate(); }
    public void setPlaying(boolean value) { if (playing != value) { playing = value; lastFrame = 0; invalidate(); } }
    public boolean isDragging() { return dragging; }
    public float getProgress() { return progress; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(40));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float inset = AndroidUtilities.dp(12), width = getWidth() - inset * 2;
        float value = width <= 0 ? 0 : Math.max(0, Math.min(1, (event.getX() - inset) / width));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true; getParent().requestDisallowInterceptTouchEvent(true);
                progress = value; if (listener != null) listener.onDrag(value); invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                progress = value; if (listener != null) listener.onDrag(value); invalidate(); return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) return false;
                dragging = false; progress = value;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) listener.onSeek(value);
                invalidate(); return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false; invalidate(); return true;
        }
        return false;
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(64, now - lastFrame) / 1000f;
        lastFrame = now;
        float target = playing && !dragging && !NebulaGlass.reduced() ? AndroidUtilities.dp(3) : 0;
        amplitude += (target - amplitude) * Math.min(1, dt * 8);
        phase = (phase + dt * (float)(Math.PI * 2) * 0.8f) % (float)(Math.PI * 2);

        float inset = AndroidUtilities.dp(12), cy = getHeight() / 2f;
        float width = getWidth() - inset * 2, x = inset + width * progress;
        float gap = AndroidUtilities.dp(6), wavelength = AndroidUtilities.dp(28);
        // Inactive track, with a stop indicator at its end as in M3 Expressive.
        if (x + gap < inset + width) canvas.drawLine(x + gap, cy, inset + width, cy, inactive);
        canvas.drawCircle(inset + width, cy, AndroidUtilities.dp(2), thumb);
        float end = Math.max(inset, x - gap);
        wave.reset(); wave.moveTo(inset, cy);
        for (float px = inset; px <= end; px += AndroidUtilities.dp(1)) {
            // Fade the wave in from the left edge so the rounded cap stays on the axis.
            float ramp = Math.min(1, (px - inset) / AndroidUtilities.dp(10));
            wave.lineTo(px, cy + (float)Math.sin((px - inset) / wavelength * Math.PI * 2 - phase) * amplitude * ramp);
        }
        if (end > inset) canvas.drawPath(wave, active);
        float thumbHeight = AndroidUtilities.dp(dragging ? 22 : 18), thumbWidth = AndroidUtilities.dp(dragging ? 3 : 4);
        rect.set(x - thumbWidth / 2, cy - thumbHeight / 2, x + thumbWidth / 2, cy + thumbHeight / 2);
        canvas.drawRoundRect(rect, thumbWidth / 2, thumbWidth / 2, thumb);
        if (amplitude > 0.05f || target > 0) postInvalidateOnAnimation();
    }
}
