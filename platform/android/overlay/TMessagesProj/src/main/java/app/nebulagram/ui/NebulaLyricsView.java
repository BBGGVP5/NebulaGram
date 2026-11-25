package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
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
    /** Latin readings under lines ("Transliteration"), null where a line needs none. */
    private final ArrayList<StaticLayout> readings = new ArrayList<>();
    private final TextPaint readingPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private boolean rich, transliterate;
    private long position, positionAt;
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
    /** "Word-by-word sync" and "Transliteration" from the player settings. */
    public void setRich(boolean words, boolean latin) {
        if (rich == words && transliterate == latin) return;
        rich = words; transliterate = latin && android.os.Build.VERSION.SDK_INT >= 29;
        layoutWidth = -1; invalidate();
    }
    public void setPosition(long millis) {
        if (lyrics == null) return;
        boolean moved = millis != position;
        position = millis; positionAt = System.currentTimeMillis();
        int index = lyrics.index(millis);
        if (index != current) { current = index; invalidate(); }
        else if (moved && rich && current >= 0 && lyrics.lines.get(current).hasWords()) invalidate();
    }
    /** Playback position between two progress updates, so the word fill moves smoothly. */
    private long now() {
        long since = System.currentTimeMillis() - positionAt;
        return since > 0 && since < 1000 ? position + since : position;
    }
    /** Dims background vocals: the paint's alpha is already set per line when this runs. */
    private static final class Backing extends CharacterStyle {
        @Override public void updateDrawState(TextPaint tp) { tp.setAlpha((int) (tp.getAlpha() * .62f)); }
    }
    private CharSequence styled(NebulaLrc.Line line) {
        if (!rich || line.text.isEmpty()) return line.text;
        int[] ranges = NebulaLrc.backgroundRanges(line.text);
        if (ranges.length == 0) return line.text;
        SpannableString text = new SpannableString(line.text);
        for (int i = 0; i + 1 < ranges.length; i += 2) {
            text.setSpan(new RelativeSizeSpan(.78f), ranges[i], ranges[i + 1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new Backing(), ranges[i], ranges[i + 1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }
    private static Object latin;
    private static String transliterated(String text) {
        if (android.os.Build.VERSION.SDK_INT < 29 || text.isEmpty() || !NebulaLyricsText.needsReading(text)) return null;
        try {
            if (latin == null) latin = android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII");
            String out = ((android.icu.text.Transliterator) latin).transliterate(text).trim();
            return out.isEmpty() || out.equals(text) ? null : out;
        } catch (Throwable error) {
            return null;
        }
    }

    private void build() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width <= 0 || width == layoutWidth) return;
        layoutWidth = width; layouts.clear(); tops.clear(); readings.clear();
        int y = getHeight() / 3, spacing = AndroidUtilities.dp(18);
        readingPaint.setTextSize(paint.getTextSize() * .55f);
        readingPaint.setTypeface(Typeface.DEFAULT);
        if (lyrics != null) for (NebulaLrc.Line line : lyrics.lines) {
            CharSequence text = line.text.isEmpty() ? "♪" : styled(line);
            // A second duet voice sits on the opposite side, both voices together in the middle.
            Layout.Alignment alignment = rich && line.voice == 1 ? Layout.Alignment.ALIGN_OPPOSITE
                    : rich && line.voice == 2 ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL;
            StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(alignment).setLineSpacing(0, 1.05f).build();
            tops.add(y); layouts.add(layout); y += layout.getHeight();
            String reading = transliterate ? transliterated(line.text) : null;
            StaticLayout readingLayout = reading == null ? null : StaticLayout.Builder.obtain(reading, 0, reading.length(), readingPaint, width)
                .setAlignment(alignment).build();
            readings.add(readingLayout);
            if (readingLayout != null) y += AndroidUtilities.dp(4) + readingLayout.getHeight();
            y += spacing;
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

    /** Draws characters [0, sung) of the layout: whole lines before, then the current line up to the fill point. */
    private void drawSung(Canvas canvas, StaticLayout layout, float sung) {
        int length = layout.getText().length();
        if (length == 0) return;
        int whole = (int) Math.min(length, Math.floor(sung));
        int row = layout.getLineForOffset(Math.min(whole, length - 1));
        for (int r = 0; r <= row; r++) {
            int lineEnd = layout.getLineEnd(r);
            float left = layout.getLineLeft(r), right = layout.getLineRight(r);
            boolean rtl = layout.getParagraphDirection(r) == Layout.DIR_RIGHT_TO_LEFT;
            canvas.save();
            if (r < row || whole >= lineEnd) {
                canvas.clipRect(left, layout.getLineTop(r), right, layout.getLineBottom(r));
            } else {
                float a = layout.getPrimaryHorizontal(whole);
                float b = whole + 1 < lineEnd ? layout.getPrimaryHorizontal(whole + 1) : (rtl ? left : right);
                float fill = a + (b - a) * (sung - whole);
                if (rtl) canvas.clipRect(fill, layout.getLineTop(r), right, layout.getLineBottom(r));
                else canvas.clipRect(left, layout.getLineTop(r), fill, layout.getLineBottom(r));
            }
            layout.draw(canvas);
            canvas.restore();
        }
    }

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
            StaticLayout layout = layouts.get(i);
            NebulaLrc.Line line = lyrics.lines.get(i);
            float dim = apple ? 0.3f : 0.38f;
            boolean karaoke = rich && i == current && line.hasWords();
            paint.setColor(color); paint.setAlpha((int)(255 * (karaoke ? dim + 0.2f : dim + (apple ? 0.7f : 0.62f) * emphasis[i])));
            canvas.save(); canvas.translate(0, top); canvas.scale(scale, scale, 0, 0);
            layout.draw(canvas);
            if (karaoke) {
                // The sung part of the line is drawn again at full strength, clipped to the voice.
                long end = i + 1 < lyrics.lines.size() ? lyrics.lines.get(i + 1).time : Long.MAX_VALUE;
                float sung = NebulaLrc.sung(line, now(), end);
                if (sung > 0) {
                    paint.setAlpha(255);
                    drawSung(canvas, layout, sung);
                }
                animating = true;
            }
            StaticLayout reading = i < readings.size() ? readings.get(i) : null;
            if (reading != null) {
                readingPaint.setColor(color);
                readingPaint.setAlpha((int)(255 * (0.3f + 0.4f * emphasis[i])));
                canvas.translate(0, layout.getHeight() + AndroidUtilities.dp(4));
                reading.draw(canvas);
            }
            canvas.restore();
        }
        canvas.restore();
        if (animating) postInvalidateOnAnimation();
    }
}
