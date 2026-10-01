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
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

/** Optical lens pill which expands into a camera-limited, scrollable zoom ruler. */
public final class NebulaZoomSlider extends View {
    public interface OnZoomChanged { void onZoomChanged(float factor); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OnZoomChanged callback;
    private final Theme.ResourcesProvider resourcesProvider;
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
    private ValueAnimator zoomAnimator;
    private boolean frontFacing;
    private BlurredBackgroundDrawable glass;
    private String announcedLabel;

    public NebulaZoomSlider(Context context, OnZoomChanged callback, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.callback = callback;
        this.resourcesProvider = resourcesProvider;
        setContentDescription(NebulaText.text("Увеличение видео", "Video zoom"));
    }

    public void setRange(float max) { setRange(1f, max); }

    public void setCameraFacing(boolean front) {
        if (frontFacing != front) {
            if (zoomAnimator != null) zoomAnimator.cancel();
            removeCallbacks(longPress);
            setExpanded(false);
        }
        frontFacing = front;
    }

    public void setGlass(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider provider) {
        glass = factory.create(this, provider);
        glass.setPadding(dp(6));
        glass.setRadius(dp(24));
        glass.setCallback(this);
        invalidate();
    }

    @Override protected boolean verifyDrawable(android.graphics.drawable.Drawable drawable) {
        return drawable == glass || super.verifyDrawable(drawable);
    }

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
        if (!frontFacing) for (float value : values)
            if (!Float.isNaN(value) && value >= minimum && value <= maximum) stops.add(value);
        // Digital shortcuts complement optical modules; they do not claim a new lens.
        if (minimum <= 1f && maximum >= 1f) addQuickStop(stops, 1f);
        if (minimum <= 2f && maximum >= 2f) addQuickStop(stops, 2f);
        if (!frontFacing) {
            if (minimum <= 5f && maximum >= 5f) addQuickStop(stops, 5f);
            addQuickStop(stops, maximum);
        }
        if (stops.isEmpty()) stops.add(Math.max(minimum, Math.min(1f, maximum)));
        cameraStops = new float[stops.size()];
        int index = 0; for (float value : stops) cameraStops[index++] = value;
        updateRulerMarks();
        invalidate();
    }

    private void addQuickStop(java.util.TreeSet<Float> stops, float value) {
        // Real focal ratios can be e.g. 2.001x: keep the optical route instead of two "2" buttons.
        for (float stop : stops) if (Math.abs(value / stop - 1f) < .02f) return;
        stops.add(value);
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
        float next = Math.max(minimum, Math.min(factor, maximum));
        if (current == next && announcedLabel != null) return;
        current = next;
        String display = label(current);
        if (!display.equals(announcedLabel)) {
            announcedLabel = display;
            setContentDescription(NebulaText.text("Увеличение видео", "Video zoom") + " · " + display);
        }
        invalidate();
    }

    private void animateZoom(float factor) {
        if (zoomAnimator != null) zoomAnimator.cancel();
        float target = Math.max(minimum, Math.min(maximum, factor));
        zoomAnimator = ValueAnimator.ofFloat((float) Math.log(current), (float) Math.log(target));
        zoomAnimator.setDuration(240);
        zoomAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        zoomAnimator.addUpdateListener(animation -> {
            setCurrent((float) Math.exp((float) animation.getAnimatedValue()));
            callback.onZoomChanged(current);
        });
        zoomAnimator.start();
    }

    private void selectPreset(float factor) {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
        float target = Math.max(minimum, Math.min(maximum, factor));
        setCurrent(target);
        callback.onZoomChanged(target);
    }

    private void setExpanded(boolean value) {
        removeCallbacks(collapse);
        if (expanded == value) return;
        expanded = value;
        if (expansionAnimator != null) expansionAnimator.cancel();
        expansionAnimator = ValueAnimator.ofFloat(expansion, value ? 1f : 0f);
        expansionAnimator.setDuration(180);
        expansionAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
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

    private float compactWidth() { return Math.min(fullWidth(), dp(12 + 46 * cameraStops.length)); }
    private float fullWidth() { return Math.max(dp(48), getWidth() - dp(32)); }
    private float widthForProgress() { return compactWidth() + (fullWidth() - compactWidth()) * expansion; }
    private RectF capsuleBounds() {
        float half = widthForProgress() / 2f;
        return new RectF(getWidth() / 2f - half, getHeight() / 2f - dp(24),
                getWidth() / 2f + half, getHeight() / 2f + dp(24));
    }
    private float pixelsPerOctave() {
        float octaves = (float) (Math.log(maximum / minimum) / Math.log(2));
        return Math.max(dp(96), (fullWidth() - dp(40)) / Math.max(.01f, octaves));
    }
    private float xForZoom(float zoom) {
        return getWidth() / 2f + (float) (Math.log(zoom / current) / Math.log(2)) * pixelsPerOctave();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int accent = Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
        int ink = Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider);
        int surface = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        float center = getWidth() / 2f;
        float middle = getHeight() / 2f;
        RectF capsule = capsuleBounds();
        paint.setStyle(Paint.Style.FILL);
        float depth = NebulaGlass.depth();
        if (glass != null) {
            glass.setBounds(Math.round(capsule.left) - dp(6), Math.round(capsule.top) - dp(6),
                    Math.round(capsule.right) + dp(6), Math.round(capsule.bottom) + dp(6));
            glass.draw(canvas);
        } else {
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
        }

        if (expansion < 1f) drawPresets(canvas, capsule, accent, 1f - expansion);

        if (expansion > 0f) {
            canvas.save();
            canvas.clipRect(capsule.left + dp(15), capsule.top + dp(3), capsule.right - dp(15), capsule.bottom - dp(3));
            int alpha = (int) (255 * expansion);
            int ticks = (int) Math.ceil(Math.log(maximum / minimum) / Math.log(2) * 8);
            float markTolerance = (float) Math.pow(2, dp(3) / pixelsPerOctave());
            for (int index = 0; index <= ticks; index++) {
                float zoom = Math.min(maximum, (float) (minimum * Math.pow(2, index / 8f)));
                float x = xForZoom(zoom);
                if (x < capsule.left + dp(16) || x > capsule.right - dp(16)) continue;
                boolean nearMark = false;
                for (float value : rulerMarks) {
                    if (zoom < value * markTolerance && value < zoom * markTolerance) { nearMark = true; break; }
                }
                if (nearMark) continue;
                paint.setColor(ink);
                paint.setAlpha(Math.round(alpha * .65f));
                paint.setStrokeWidth(dp(1));
                canvas.drawLine(x, middle - dp(13), x, middle - dp(3), paint);
            }
            paint.setColor(accent);
            paint.setAlpha(alpha);
            paint.setStrokeWidth(dp(2));
            for (float value : rulerMarks) {
                float x = xForZoom(value);
                if (x >= capsule.left + dp(16) && x <= capsule.right - dp(16)) {
                    canvas.drawLine(x, middle - dp(13), x, middle - dp(1), paint);
                }
            }
            canvas.restore();
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(12));
            paint.setColor(accent);
            paint.setAlpha(alpha);
            float previousX = -Float.MAX_VALUE;
            for (float value : rulerMarks) {
                float markX = xForZoom(value);
                if (value != maximum && Math.abs(xForZoom(maximum) - markX) < dp(25)) continue;
                if (markX - previousX < dp(25)) continue;
                drawMark(canvas, label(value).replace("×", ""), value, capsule, middle);
                previousX = markX;
            }
            paint.setColor(accent);
            paint.setAlpha(alpha);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(5));
            canvas.drawLine(center, middle - dp(14), center, middle + dp(4), paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
        paint.setAlpha(255);
    }

    private String label(float value) {
        return Math.abs(value - Math.round(value)) < .04f ? Math.round(value) + "×"
                : (Math.round(value * 10f) / 10f) + "×";
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
        float radius = Math.min(dp(21), (segment - dp(4)) / 2f);
        canvas.drawCircle(x, capsule.centerY(), radius, paint);
        paint.clearShadowLayer();
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < values.length; i++) {
            paint.setColor(Theme.getColor(i == selected ? Theme.key_chat_messagePanelVoicePressed : Theme.key_chat_messagePanelText, resourcesProvider));
            paint.setAlpha(Math.round(255 * opacity));
            String text = i == selected ? label(current) : label(values[i]).replace("×", "");
            paint.setTextSize(dp(14));
            float available = i == selected ? radius * 2 - dp(5) : segment - dp(6);
            float measured = paint.measureText(text);
            if (measured > available) paint.setTextSize(paint.getTextSize() * available / measured);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            canvas.drawText(text, capsule.left + segment * (i + .5f),
                    capsule.centerY() - (metrics.ascent + metrics.descent) / 2f, paint);
        }
    }
    private float presetAt(float x) {
        float[] values = cameraStops; float left = (getWidth() - compactWidth()) / 2f;
        int index = Math.min(values.length - 1, Math.max(0, (int) ((x - left) * values.length / compactWidth())));
        return values[index];
    }

    private void drawMark(Canvas canvas, String label, float zoom, RectF capsule, float middle) {
        float x = xForZoom(zoom);
        if (x >= capsule.left + dp(20) && x <= capsule.right - dp(20)) {
            canvas.drawText(label, x, middle + dp(19) - paint.getFontMetrics().descent, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.abs(x - getWidth() / 2f) > widthForProgress() / 2f + dp(6)) return false;
                if (Math.abs(event.getY() - getHeight() / 2f) > dp(30)) return false;
                if (zoomAnimator != null) zoomAnimator.cancel();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                removeCallbacks(collapse);
                dragStartX = x;
                dragStartZoom = current;
                dragged = false;
                held = false;
                dragOnRuler = true;
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
                    if (expanded) animateZoom((float) (current * Math.pow(2, (x - getWidth() / 2f) / pixelsPerOctave())));
                    else selectPreset(presetAt(x));
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
        if (zoomAnimator != null) zoomAnimator.cancel();
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
