package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;

/** Compact 1×/2× presets that expand into a camera-limited zoom ruler. */
public final class NebulaZoomSlider extends FrameLayout {
    public interface OnZoomChanged { void onZoomChanged(float factor); }
    private static final int ACCENT = 0xFFE33492;
    private final OnZoomChanged callback;
    private final FrameLayout capsule;
    private final LinearLayout presets;
    private final TextView currentValue;
    private final TextView secondValue;
    private final ZoomRuler ruler;
    private float maximum = 2f;
    private float current = 1f;
    private boolean expanded;
    private ValueAnimator widthAnimator;
    private final Runnable collapse = () -> setExpanded(false);

    public NebulaZoomSlider(Context context, OnZoomChanged callback) {
        super(context);
        this.callback = callback;
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE211C2B);
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), 0x667E718D);
        capsule = new FrameLayout(context);
        capsule.setBackground(background);
        addView(capsule, new LayoutParams(dp(224), LayoutParams.MATCH_PARENT, Gravity.CENTER));
        presets = new LinearLayout(context);
        presets.setGravity(Gravity.CENTER);
        capsule.addView(presets, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        currentValue = preset(context);
        secondValue = preset(context);
        presets.addView(currentValue, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        presets.addView(secondValue, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        currentValue.setOnClickListener(v -> {
            updateZoom(1f, true);
            setExpanded(true);
        });
        secondValue.setOnClickListener(v -> { updateZoom(Math.min(2f, maximum), true); setExpanded(true); });
        currentValue.setOnLongClickListener(v -> { setExpanded(true); return true; });
        secondValue.setOnLongClickListener(v -> { setExpanded(true); return true; });
        ruler = new ZoomRuler(context);
        ruler.setVisibility(GONE);
        capsule.addView(ruler, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setRange(2f);
    }

    private TextView preset(Context context) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(0xFFF9F5FF);
        view.setTextSize(18);
        view.setTypeface(null, 1);
        return view;
    }

    private void updatePresets() {
        boolean secondSelected = maximum > 1.05f && Math.abs(current - Math.min(2f, maximum)) < .05f;
        boolean firstSelected = Math.abs(current - 1f) < .05f;
        currentValue.setText("1×");
        secondValue.setText(format(Math.min(2f, maximum)));
        currentValue.setContentDescription("Zoom 1×");
        secondValue.setContentDescription("Zoom " + format(Math.min(2f, maximum)));
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(ACCENT);
        selected.setCornerRadius(dp(24));
        currentValue.setBackground(firstSelected ? selected : null);
        secondValue.setBackground(secondSelected ? selected : null);
        ruler.invalidate();
    }

    private void setExpanded(boolean value) {
        removeCallbacks(collapse);
        if (expanded == value) { if (value) postDelayed(collapse, 1800); return; }
        expanded = value;
        if (widthAnimator != null) widthAnimator.cancel();
        LayoutParams capsuleParams = (LayoutParams) capsule.getLayoutParams();
        widthAnimator = ValueAnimator.ofInt(capsuleParams.width, dp(value ? 320 : 224));
        widthAnimator.setDuration(220);
        widthAnimator.addUpdateListener(animation -> {
            LayoutParams params = (LayoutParams) capsule.getLayoutParams();
            params.width = (int) animation.getAnimatedValue();
            capsule.setLayoutParams(params);
        });
        widthAnimator.start();
        View incoming = value ? ruler : presets;
        View outgoing = value ? presets : ruler;
        incoming.setVisibility(VISIBLE);
        incoming.setAlpha(0f);
        incoming.setScaleX(value ? .92f : 1.04f);
        outgoing.animate().alpha(0f).setDuration(150).withEndAction(() -> outgoing.setVisibility(GONE)).start();
        incoming.animate().alpha(1f).scaleX(1f).setDuration(220).start();
        if (value) postDelayed(collapse, 1800);
    }

    private void updateZoom(float factor, boolean fromUser) {
        current = Math.max(1f, Math.min(maximum, factor));
        updatePresets();
        if (fromUser) callback.onZoomChanged(current);
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private static String format(float factor) {
        return Math.abs(factor - Math.round(factor)) < .05f
                ? Math.round(factor) + "×" : String.format(Locale.ROOT, "%.1f×", factor);
    }
    public void setRange(float max) { maximum = Math.max(1f, Math.min(8f, max)); updateZoom(1f, false); setExpanded(false); }
    public void setCurrent(float factor) { updateZoom(factor, false); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(collapse);
        if (widthAnimator != null) widthAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private final class ZoomRuler extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ZoomRuler(Context context) { super(context); setContentDescription("Zoom ruler"); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(20), right = getWidth() - dp(20), span = Math.max(1, right - left);
            for (int index = 0; index <= 24; index++) {
                float value = 1f + (maximum - 1f) * index / 24f;
                float x = left + span * index / 24f;
                boolean major = index == 0 || index == 24 || Math.abs(value - 2f) < (maximum - 1f) / 48f
                        || Math.abs(value - 5f) < (maximum - 1f) / 48f;
                paint.setColor(major ? ACCENT : 0x99D4C4D2);
                paint.setStrokeWidth(dp(major ? 2 : 1));
                canvas.drawLine(x, dp(12), x, dp(major ? 29 : 23), paint);
            }
            float selectedX = left + span * (current - 1f) / Math.max(.001f, maximum - 1f);
            paint.setColor(ACCENT);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(selectedX, dp(8), selectedX, dp(31), paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setTextSize(dp(11));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("1", left, dp(43), paint);
            float lastMark = 1f;
            if (maximum >= 2f) {
                canvas.drawText("2", left + span / (maximum - 1f), dp(43), paint);
                lastMark = 2f;
            }
            if (maximum >= 5f) {
                canvas.drawText("5", left + span * 4f / (maximum - 1f), dp(43), paint);
                lastMark = 5f;
            }
            if (maximum - lastMark > .1f) {
                canvas.drawText(format(maximum).replace("×", ""), right, dp(43), paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                removeCallbacks(collapse);
                float left = dp(20), span = Math.max(1, getWidth() - dp(40));
                updateZoom(1f + (maximum - 1f) * (event.getX() - left) / span, true);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP) performClick();
                postDelayed(collapse, 1800);
                return true;
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
