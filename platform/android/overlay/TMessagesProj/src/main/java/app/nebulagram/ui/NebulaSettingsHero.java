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

    public NebulaSettingsHero(Context context, int icon, String title, String description) {
        super(context);
        NebulaTheme theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);
        setPadding(dp(22), dp(22), dp(22), dp(22));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{theme.primaryContainer(), theme.surfaceContainer()});
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), NebulaTheme.stateLayer(theme.primary(), .18f));
        setBackground(background);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = new ImageView(context);
        image.setImageResource(icon);
        image.setColorFilter(theme.primary());
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(image, new LayoutParams(dp(28), dp(28)));
        TextView brand = label("NEBULAGRAM", 11, theme.onSurfaceVariant());
        brand.setLetterSpacing(.12f);
        LayoutParams brandParams = new LayoutParams(0, -2, 1);
        brandParams.setMarginStart(dp(12));
        top.addView(brand, brandParams);
        addView(top);
        TextView heading = label(title, 28, theme.onSurface());
        heading.setTypeface(AndroidUtilities.bold());
        LayoutParams headingParams = new LayoutParams(-1, -2); headingParams.topMargin = dp(18);
        addView(heading, headingParams);
        TextView explanation = label(description, 14, theme.onSurfaceVariant());
        explanation.setLineSpacing(dp(3), 1f);
        LayoutParams descriptionParams = new LayoutParams(-1, -2); descriptionParams.topMargin = dp(8);
        addView(explanation, descriptionParams);
        status = label("", 12, theme.primary());
        status.setTypeface(AndroidUtilities.bold());
        LayoutParams statusParams = new LayoutParams(-1, -2); statusParams.topMargin = dp(16);
        addView(status, statusParams);
    }

    public void setStatus(String value) {
        status.setText(value);
        status.setVisibility(value == null || value.isEmpty() ? GONE : VISIBLE);
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(getContext());
        label.setText(text); label.setTextSize(size); label.setTextColor(color);
        return label;
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
}
