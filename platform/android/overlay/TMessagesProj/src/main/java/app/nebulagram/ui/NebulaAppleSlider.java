package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Тонкая шкала в духе Apple Music: полоска в 4 точки, которая под пальцем
 * плавно утолщается. Служит и шкалой трека, и громкостью.
 */
public final class NebulaAppleSlider extends View {
    public interface Listener {
        void onDrag(float progress);
        void onSeek(float progress);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress, thickness;
    private boolean dragging;
    private Listener listener;
    private int fill = 0xffffffff, track = 0x40ffffff;

    public NebulaAppleSlider(Context context) {
        super(context);
        thickness = AndroidUtilities.dp(4);
    }

    public void setListener(Listener value) { listener = value; }
    public void setColors(int fill, int track) { this.fill = fill; this.track = track; invalidate(); }
    public boolean isDragging() { return dragging; }
    public void setProgress(float value) {
        if (dragging) return;
        value = Math.max(0, Math.min(1, value));
        if (value != progress) { progress = value; invalidate(); }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float width = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        float value = Math.max(0, Math.min(1, (event.getX() - getPaddingLeft()) / width));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                progress = value;
                if (listener != null) listener.onDrag(progress);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                progress = value;
                if (listener != null) listener.onDrag(progress);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                progress = value;
                dragging = false;
                if (listener != null) listener.onSeek(progress);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override protected void onDraw(Canvas canvas) {
        float target = AndroidUtilities.dp(dragging ? 10 : 4);
        thickness += (target - thickness) * .3f;
        if (Math.abs(target - thickness) > .3f) postInvalidateOnAnimation(); else thickness = target;
        float left = getPaddingLeft(), right = getWidth() - getPaddingRight();
        float cy = getHeight() / 2f, radius = thickness / 2f;
        rect.set(left, cy - radius, right, cy + radius);
        paint.setColor(track);
        canvas.drawRoundRect(rect, radius, radius, paint);
        rect.right = left + (right - left) * progress;
        if (rect.right > rect.left) {
            paint.setColor(fill);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
