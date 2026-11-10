package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;
import java.util.ArrayList;
import java.util.function.LongConsumer;
import org.telegram.messenger.AndroidUtilities;

/** Lyrics that follow playback: the current line is centred and highlighted; tapping a line seeks. */
public final class NebulaLyricsView extends View {
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final ArrayList<StaticLayout> layouts = new ArrayList<>();
    private final ArrayList<Integer> tops = new ArrayList<>();
    private final OverScroller scroller;
    private final GestureDetector gestures;
    private NebulaLrc lyrics;
    private CharSequence message;
    private int current = -1, layoutWidth = -1, contentHeight, color = 0xffffffff;
    private float scroll, emphasis[] = new float[0];
    private long lastFrame, userScrollUntil;
    private LongConsumer seek;

    public NebulaLyricsView(Context context) {
        super(context);
        paint.setTextSize(AndroidUtilities.dp(24));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        scroller = new OverScroller(context);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { scroller.forceFinished(true); return true; }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                userScrollUntil = System.currentTimeMillis() + 3000;
                scroll = clamp(scroll + dy); invalidate(); return true;
            }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                userScrollUntil = System.currentTimeMillis() + 3000;
                scroller.fling(0, (int)scroll, 0, (int)-vy, 0, 0, 0, Math.max(0, contentHeight - getHeight()));
                postInvalidateOnAnimation(); return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                int line = lineAt(e.getY() + scroll);
                if (line >= 0 && lyrics != null && lyrics.synced && seek != null) {
                    userScrollUntil = 0; seek.accept(lyrics.lines.get(line).time); return true;
                }
                return false;
            }
        });
    }
    public void setOnSeek(LongConsumer seek) { this.seek = seek; }
    public void setColor(int color) { this.color = color; invalidate(); }
    private boolean apple;
    /** Крупные жирные строки, как в Apple Music: активная яркая, остальные притушены. */
    public void setApple(boolean value) {
        apple = value;
        paint.setTextSize(AndroidUtilities.dp(value ? 30 : 24));
        paint.setTypeface(value ? AndroidUtilities.bold() : null);
        layoutWidth = -1; requestLayout(); invalidate();
    }
    public void setLyrics(NebulaLrc lyrics, CharSequence message) {
        this.lyrics = lyrics; this.message = message; current = -1; scroll = 0; layoutWidth = -1;
        emphasis = new float[lyrics == null ? 0 : lyrics.lines.size()];
        requestLayout(); invalidate();
    }
    public boolean hasLyrics() { return lyrics != null; }
    public void setPosition(long millis) {
        if (lyrics == null) return;
        int index = lyrics.index(millis);
        if (index != current) { current = index; invalidate(); }
    }

    private void build() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width <= 0 || width == layoutWidth) return;
        layoutWidth = width; layouts.clear(); tops.clear();
        int y = getHeight() / 3, spacing = AndroidUtilities.dp(18);
        if (lyrics != null) for (NebulaLrc.Line line : lyrics.lines) {
            StaticLayout layout = StaticLayout.Builder.obtain(line.text.isEmpty() ? "♪" : line.text, 0, line.text.isEmpty() ? 1 : line.text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.05f).build();
            tops.add(y); layouts.add(layout); y += layout.getHeight() + spacing;
        }
        contentHeight = y + getHeight() / 2;
    }
    private int lineAt(float y) {
        for (int i = 0; i < layouts.size(); i++) if (y >= tops.get(i) && y <= tops.get(i) + layouts.get(i).getHeight()) return i;
        return -1;
    }
    private float clamp(float value) { return Math.max(0, Math.min(Math.max(0, contentHeight - getHeight()), value)); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        return handled || super.onTouchEvent(event);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutWidth = -1; }

    @Override protected void onDraw(Canvas canvas) {
        if (lyrics == null) {
            if (message != null) {
                paint.setColor(color); paint.setAlpha(150); paint.setTextSize(AndroidUtilities.dp(17));
                StaticLayout layout = StaticLayout.Builder.obtain(message, 0, message.length(), paint, Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight()))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
                canvas.save(); canvas.translate(getPaddingLeft(), (getHeight() - layout.getHeight()) / 2f); layout.draw(canvas); canvas.restore();
                paint.setTextSize(AndroidUtilities.dp(apple ? 30 : 24));
            }
            return;
        }
        build();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 1 : Math.min(64, now - lastFrame) / 1000f;
        lastFrame = now;
        boolean animating = false;
        if (scroller.computeScrollOffset()) { scroll = scroller.getCurrY(); animating = true; }
        else if (current >= 0 && now > userScrollUntil && current < tops.size()) {
            float target = clamp(tops.get(current) - getHeight() / 3f);
            float next = scroll + (target - scroll) * Math.min(1, dt * 7);
            if (Math.abs(next - target) > 0.5f) animating = true; else next = target;
            scroll = next;
        }
        canvas.save();
        canvas.translate(getPaddingLeft(), -scroll);
        for (int i = 0; i < layouts.size(); i++) {
            float goal = !lyrics.synced || i == current ? 1 : 0;
            emphasis[i] += (goal - emphasis[i]) * Math.min(1, dt * 9);
            if (Math.abs(goal - emphasis[i]) > 0.01f) animating = true;
            int top = tops.get(i);
            if (top - scroll > getHeight() || top + layouts.get(i).getHeight() - scroll < 0) continue;
            float scale = apple ? 1f : 0.92f + 0.08f * emphasis[i];
            paint.setColor(color); paint.setAlpha((int)(255 * ((apple ? 0.3f : 0.38f) + (apple ? 0.7f : 0.62f) * emphasis[i])));
            canvas.save(); canvas.translate(0, top); canvas.scale(scale, scale, 0, 0);
            layouts.get(i).draw(canvas); canvas.restore();
        }
        canvas.restore();
        if (animating) postInvalidateOnAnimation();
    }
}
