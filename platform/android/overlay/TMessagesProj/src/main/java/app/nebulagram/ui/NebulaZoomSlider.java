package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Optical lens pill which expands into a camera-limited, scrollable zoom ruler. */
public final class NebulaZoomSlider extends View {
    public interface OnZoomChanged { void onZoomChanged(float factor); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OnZoomChanged callback;
    private final Runnable collapse = () -> setExpanded(false);
    private final Runnable longPress = () -> { held = true; setExpanded(true); };
    private float[] cameraStops = {1f};
    private float[] rulerMarks = {1f};
    private float maximum = 1f;
    private float minimum = 1f;
    private float current = 1f;
    private float dragStartX;
    private float dragStartZoom;
    private boolean dragged;
    private boolean held;
    private boolean dragOnRuler;
    private boolean expanded;
    private float expansion;
    private ValueAnimator expansionAnimator;

    public NebulaZoomSlider(Context context, OnZoomChanged callback) {
        super(context);
        this.callback = callback;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setContentDescription(NebulaText.text("Увеличение видео", "Video zoom"));
    }

    public void setRange(float max) { setRange(1f, max); }

    public void setRange(float min, float max) {
        if (Float.isNaN(min) || Float.isInfinite(min) || min <= 0f
                || Float.isNaN(max) || Float.isInfinite(max) || max < min) return;
        minimum = min;
        maximum = max;
        setCurrent(current);
        updateRulerMarks();
    }

    public void setCameraStops(float[] values) {
        java.util.TreeSet<Float> stops = new java.util.TreeSet<>();
        for (float value : values) if (value >= minimum && value <= maximum) stops.add(value);
        if (stops.isEmpty()) stops.add(Math.max(minimum, Math.min(1f, maximum)));
        cameraStops = new float[stops.size()];
        int index = 0; for (float value : stops) cameraStops[index++] = value;
        updateRulerMarks();
        invalidate();
    }

    private void updateRulerMarks() {
        java.util.TreeSet<Float> labels = new java.util.TreeSet<>();
        labels.add(minimum); labels.add(maximum);
        for (float stop : cameraStops) if (stop >= minimum && stop <= maximum) labels.add(stop);
        for (float value : new float[]{1f, 2f, 5f, 10f, 20f, 50f, 100f})
            if (value > minimum && value < maximum) labels.add(value);
        rulerMarks = new float[labels.size()];
        int index = 0; for (float value : labels) rulerMarks[index++] = value;
    }

    public void setCurrent(float factor) {
        if (Float.isNaN(factor) || Float.isInfinite(factor)) return;
        current = Math.max(minimum, Math.min(factor, maximum));
        setContentDescription(NebulaText.text("Увеличение видео", "Video zoom") + " · "
                + label(current));
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

    private float compactWidth() { return Math.min(fullWidth(), dp(16 + 56 * cameraStops.length)); }
    private float fullWidth() { return Math.max(dp(48), getWidth() - dp(80)); }
    private float widthForProgress() { return compactWidth() + (fullWidth() - compactWidth()) * expansion; }
    private RectF capsuleBounds() {
        float half = widthForProgress() / 2f;
        return new RectF(getWidth() / 2f - half, getHeight() / 2f - dp(20),
                getWidth() / 2f + half, getHeight() / 2f + dp(20));
    }
    private float pixelsPerOctave() {
        float octaves = (float) (Math.log(maximum / minimum) / Math.log(2));
        return Math.max(dp(96), (fullWidth() - dp(40)) / Math.max(.01f, octaves));
    }
    private float xForZoom(float zoom) {
        float left = (getWidth() - fullWidth()) / 2f + dp(20);
        float right = (getWidth() + fullWidth()) / 2f - dp(20);
        float span = (float) (Math.log(maximum / minimum) / Math.log(2)) * pixelsPerOctave();
        float origin = getWidth() / 2f - (float) (Math.log(current / minimum) / Math.log(2)) * pixelsPerOctave();
        origin = Math.max(right - span, Math.min(left, origin));
        return origin + (float) (Math.log(zoom / minimum) / Math.log(2)) * pixelsPerOctave();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = Theme.getColor(Theme.key_chat_messagePanelSend);
        int ink = Theme.getColor(Theme.key_chat_messagePanelText);
        int surface = Theme.getColor(Theme.key_chat_messagePanelBackground);
        float center = getWidth() / 2f;
        float middle = getHeight() / 2f;
        RectF capsule = capsuleBounds();
        paint.setStyle(Paint.Style.FILL);
        float depth = NebulaGlass.depth();
        paint.setShader(new LinearGradient(0, capsule.top, 0, capsule.bottom,
                blend(surface, Color.WHITE, NebulaGlass.highlights() ? .05f + depth * .12f : 0f),
                blend(surface, Color.BLACK, depth * .12f), Shader.TileMode.CLAMP));
        if (depth > 0f) paint.setShadowLayer(dp(4 + depth * 10), 0, dp(1 + depth * 4), Color.argb(Math.round(150 * depth), 0, 0, 0));
        canvas.drawRoundRect(capsule, dp(24), dp(24), paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1f));
        paint.setColor(blend(surface, Color.WHITE, .32f));
        paint.setAlpha(180);
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
            float previousX = -Float.MAX_VALUE;
            for (float value : rulerMarks) {
                float markX = xForZoom(value);
                if (markX - previousX < dp(25)) continue;
                drawMark(canvas, label(value).replace("×", ""), value, capsule, middle);
                previousX = markX;
            }
            canvas.restore();
            paint.setColor(accent);
            paint.setAlpha(alpha);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(5));
            float needle = Math.max(capsule.left + dp(20), Math.min(capsule.right - dp(20), xForZoom(current)));
            canvas.drawLine(needle, middle - dp(16), needle, middle + dp(5), paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
        drawStep(canvas, capsule.left - dp(18), middle, false, accent);
        drawStep(canvas, capsule.right + dp(18), middle, true, accent);
        paint.setAlpha(255);
    }

    private String label(float value) {
        return Math.abs(value - Math.round(value)) < .04f ? Math.round(value) + "×"
                : String.format(java.util.Locale.US, "%.1f×", value);
    }
    private void drawPresets(Canvas canvas, RectF capsule, int accent, float opacity) {
        float[] values = cameraStops;
        int selected = 0;
        for (int i = 1; i < values.length; i++) if (current >= values[i] - .04f) selected = i;
        float segment = capsule.width() / values.length;
        float x = capsule.left + segment * selected + segment / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent); paint.setAlpha(Math.round(255 * opacity));
        if (NebulaGlass.depth() > 0f) paint.setShadowLayer(dp(6), 0, dp(2), (accent & 0x00FFFFFF) | (Math.round(102 * NebulaGlass.depth()) << 24));
        float radius = Math.min(dp(18), (segment - dp(4)) / 2f);
        canvas.drawCircle(x, capsule.centerY(), radius, paint);
        paint.clearShadowLayer();
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(12));
        for (int i = 0; i < values.length; i++) {
            paint.setColor(i == selected ? Theme.getColor(Theme.key_chat_messagePanelVoicePressed) : Theme.getColor(Theme.key_chat_messagePanelText));
            paint.setAlpha(Math.round(255 * opacity));
            canvas.drawText(i == selected ? label(current) : label(values[i]).replace("×", ""), capsule.left + segment * (i + .5f), capsule.centerY() + dp(4), paint);
        }
    }
    private void drawStep(Canvas canvas, float x, float y, boolean plus, int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(Theme.getColor(Theme.key_chat_messagePanelBackground), Color.WHITE, .08f));
        if (NebulaGlass.depth() > 0f) paint.setShadowLayer(dp(6), 0, dp(2), Color.argb(Math.round(102 * NebulaGlass.depth()), 0, 0, 0));
        canvas.drawCircle(x, y, dp(14), paint); paint.clearShadowLayer();
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
        float[] values = cameraStops; float left = (getWidth() - compactWidth()) / 2f;
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
                if (!expanded && Math.abs(x - getWidth() / 2f) > compactWidth() / 2f + dp(34)) return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                removeCallbacks(collapse);
                dragStartX = x;
                dragStartZoom = current;
                dragged = false;
                held = false;
                dragOnRuler = expanded || Math.abs(x - getWidth() / 2f) < compactWidth() / 2f;
                if (!expanded && Math.abs(x - getWidth() / 2f) < compactWidth() / 2f) postDelayed(longPress, 250);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - dragStartX) > dp(6)) dragged = true;
                if (dragged && dragOnRuler) {
                    removeCallbacks(longPress);
                    setExpanded(true);
                    setCurrent((float) (dragStartZoom * Math.pow(2, (dragStartX - x) / pixelsPerOctave())));
                    callback.onZoomChanged(current);
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!dragged && !held) {
                    float half = widthForProgress() / 2f;
                    if (x < getWidth() / 2f - half) setCurrent(step(false));
                    else if (x > getWidth() / 2f + half) setCurrent(step(true));
                    else if (expanded) setCurrent((float) (current * Math.pow(2, (x - xForZoom(current)) / pixelsPerOctave())));
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

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(collapse);
        removeCallbacks(longPress);
        if (expansionAnimator != null) expansionAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override public boolean performClick() { super.performClick(); return true; }
    private static int blend(int from, int to, float amount) {
        float keep = 1f - amount;
        return Color.argb(240,
                Math.round(Color.red(from) * keep + Color.red(to) * amount),
                Math.round(Color.green(from) * keep + Color.green(to) * amount),
                Math.round(Color.blue(from) * keep + Color.blue(to) * amount));
    }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
