package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

/** Lightweight, wrapping settings header. No readbacks, blur or animation loop. */
public final class NebulaSettingsHero extends LinearLayout {
    private final TextView status;
    private final GradientDrawable background;

    public NebulaSettingsHero(Context context, int icon, String title, String description) {
        super(context);
        NebulaTheme theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(16), dp(16), dp(16));
        background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{theme.surfaceContainer(), theme.surfaceContainer()});
        background.setCornerRadius(dp(16));
        setBackground(background);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(context);
        image.setImageResource(icon);
        image.setColorFilter(theme.primary());
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(image, new LayoutParams(dp(28), dp(28)));
        TextView heading = label(title, 18, theme.onSurface());
        heading.setTypeface(AndroidUtilities.bold());
        LayoutParams headingParams = new LayoutParams(0, -2, 1);
        headingParams.setMarginStart(dp(12));
        top.addView(heading, headingParams);
        addView(top);
        TextView explanation = label(description, 14, theme.onSurfaceVariant());
        explanation.setLineSpacing(dp(2), 1f);
        LayoutParams descriptionParams = new LayoutParams(-1, -2); descriptionParams.topMargin = dp(8);
        addView(explanation, descriptionParams);
        status = label("", 12, theme.primary());
        status.setTypeface(AndroidUtilities.bold());
        LayoutParams statusParams = new LayoutParams(-2, -2); statusParams.topMargin = dp(10);
        addView(status, statusParams);
        setStatus("");
    }

    public void setStatus(String value) { setStatus(value, false); }

    /** Active describes this feature only, never the security of the whole screen. */
    public void setStatus(String value, boolean active) {
        NebulaTheme theme = NebulaTheme.of(getContext());
        int accent = active ? theme.success() : theme.onSurfaceVariant();
        int right = active ? androidx.core.graphics.ColorUtils.blendARGB(theme.surfaceContainer(), theme.success(), .10f)
                : theme.surfaceContainer();
        background.setColors(new int[]{theme.surfaceContainer(), right});
        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(dp(8)); badge.setColor(NebulaTheme.stateLayer(accent, .12f));
        badge.setStroke(dp(1), NebulaTheme.stateLayer(accent, .25f));
        status.setBackground(badge); status.setPadding(dp(10), dp(6), dp(10), dp(6));
        status.setTextColor(accent);
        status.setText((active ? "●  " : "○  ") + (value == null ? "" : value));
        status.setContentDescription(value);
        status.setVisibility(value == null || value.isEmpty() ? GONE : VISIBLE);
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(getContext());
        label.setText(text); label.setTextSize(size); label.setTextColor(color);
        return label;
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
}
