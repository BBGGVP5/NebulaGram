package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** A horizontal scrub selects only on release. Native taps and vertical scroll remain native. */
public final class NebulaTabGesture {
    public interface Tabs {
        int count();
        int selected();
        boolean bounds(int index, RectF out);
        /** Touch cells may include padding; keep the visible lens bounds separate. */
        default boolean hitBounds(int index, RectF out) { return bounds(index, out); }
        void select(int index);
    }
    private final View host;
    private final Tabs tabs;
    private final NebulaTabLens lens;
    private final RectF rect = new RectF();
    private final int slop;
    private float downX, downY, fingerX;
    private boolean tracking, dragging, cancelChild, swallowUntilUp;
    private int target = -1;

    public NebulaTabGesture(View host, Tabs tabs) {
        this.host = host;
        this.tabs = tabs;
        lens = new NebulaTabLens(host);
        slop = ViewConfiguration.get(host.getContext()).getScaledTouchSlop();
    }

    public boolean onTouch(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
            downX = fingerX = event.getX(); downY = event.getY();
            tracking = hit(downX, downY) >= 0;
            return false;
        }
        if (swallowUntilUp) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) reset();
            return true;
        }
        if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = dragging;
            reset();
            swallowUntilUp = consumed && action != MotionEvent.ACTION_CANCEL;
            return consumed;
        }
        if (!tracking) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
            if (!dragging && dy > slop && dy >= dx) { reset(); return false; }
            if (!dragging && dx > slop && dx > dy) {
                dragging = true; cancelChild = true;
                lens.setPressed(true);
                if (host.getParent() != null) host.getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (dragging) {
                fingerX = event.getX(); target = hitDrag(fingerX, event.getY());
                host.invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            boolean consumed = dragging;
            int chosen = dragging ? hitDrag(event.getX(), event.getY()) : -1;
            tracking = dragging = false; target = -1;
            lens.setPressed(false);
            if (host.getParent() != null) host.getParent().requestDisallowInterceptTouchEvent(false);
            if (chosen >= 0 && chosen != tabs.selected()) { tabs.select(chosen); lens.pulse(); }
            host.invalidate();
            return consumed;
        }
        return dragging;
    }

    private int hit(float x, float y) {
        for (int i = 0; i < tabs.count(); i++) {
            if (tabs.hitBounds(i, rect) && rect.contains(x, y)) return i;
        }
        return -1;
    }

    private int hitDrag(float x, float y) {
        for (int i = 0; i < tabs.count(); i++) {
            if (tabs.hitBounds(i, rect) && x >= rect.left && x < rect.right
                    && y >= rect.top - slop && y < rect.bottom + slop) return i;
        }
        return -1;
    }

    public boolean takeChildCancel() {
        boolean value = cancelChild; cancelChild = false; return value;
    }

    public boolean isDragging() { return dragging; }

    public void draw(Canvas canvas, int accent) {
        int index = dragging && target >= 0 ? target : tabs.selected();
        if (index < 0 || !tabs.bounds(index, rect)) return;
        if (dragging) {
            float width = rect.width();
            float center = Math.max(width / 2f, Math.min(host.getWidth() - width / 2f, fingerX));
            rect.offset(center - rect.centerX(), 0);
            lens.drawDrag(canvas, rect.left, rect.top, rect.right, rect.bottom, accent);
        } else {
            lens.draw(canvas, rect.left, rect.top, rect.right, rect.bottom, accent);
        }
    }

    /** ViewPager's own continuous offset, without a second lagging animation. */
    public void drawInterpolated(Canvas canvas, RectF bounds, int accent) {
        if (dragging) draw(canvas, accent);
        else lens.drawDrag(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom, accent);
    }

    public void reset() {
        tracking = dragging = cancelChild = swallowUntilUp = false; target = -1;
        lens.reset();
        if (host.getParent() != null) host.getParent().requestDisallowInterceptTouchEvent(false);
        host.invalidate();
    }
}
