package app.nebulagram.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

/** Plain contextual summary/status. The screen title already lives in the action bar. */
public final class NebulaSettingsHero extends LinearLayout {
    private final TextView status;

    public NebulaSettingsHero(Context context, int icon, String title, String description) {
        super(context);
        NebulaTheme theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(12));
        TextView explanation = label(description, 13, theme.onSurfaceVariant());
        explanation.setLineSpacing(dp(2), 1f);
        addView(explanation, new LayoutParams(-1, -2));
        status = label("", 12, theme.primary());
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = dp(6);
        addView(status, params);
        setStatus("");
    }

    public void setStatus(String value) { setStatus(value, false); }

    public void setStatus(String value, boolean active) {
        NebulaTheme theme = NebulaTheme.of(getContext());
        status.setTextColor(active ? theme.success() : theme.onSurfaceVariant());
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
