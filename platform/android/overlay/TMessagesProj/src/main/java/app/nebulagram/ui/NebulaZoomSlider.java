package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;

/** Compact zoom control for round-video recording. Camera limits come from the active session. */
public final class NebulaZoomSlider extends LinearLayout {
    public interface OnZoomChanged { void onZoomChanged(float factor); }

    private final TextView value;
    private final TextView limit;
    private final SeekBar slider;
    private float maxZoom = 2f;

    public NebulaZoomSlider(Context context, OnZoomChanged callback) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xED192334);
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), 0x554E829B);
        setBackground(background);

        value = label(context);
        addView(value, new LayoutParams(dp(48), dp(48)));
        slider = new SeekBar(context);
        slider.setMax(1000);
        slider.setProgressTintList(ColorStateList.valueOf(0xFF57C5D0));
        slider.setThumbTintList(ColorStateList.valueOf(0xFF57C5D0));
        slider.setProgress(0);
        slider.setContentDescription("Zoom");
        addView(slider, new LayoutParams(0, dp(48), 1));
        limit = label(context);
        addView(limit, new LayoutParams(dp(44), dp(48)));
        setRange(2f);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float factor = 1f + (maxZoom - 1f) * progress / 1000f;
                value.setText(format(factor));
                callback.onZoomChanged(factor);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private static TextView label(Context context) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(14);
        view.setTextColor(0xFFF0F5FF);
        return view;
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }

    private static String format(float factor) {
        return Math.abs(factor - Math.round(factor)) < .05f
                ? Math.round(factor) + "×" : String.format(Locale.ROOT, "%.1f×", factor);
    }

    public void setRange(float max) {
        maxZoom = Math.max(1f, Math.min(8f, max));
        limit.setText(format(maxZoom));
        setCurrent(1f);
    }

    public void setCurrent(float factor) {
        float bounded = Math.max(1f, Math.min(maxZoom, factor));
        slider.setProgress(maxZoom > 1f ? Math.round((bounded - 1f) * 1000f / (maxZoom - 1f)) : 0);
        value.setText(format(bounded));
    }
}
