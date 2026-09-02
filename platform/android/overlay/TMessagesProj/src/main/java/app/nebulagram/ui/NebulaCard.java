package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * A group of rows on one rounded surface — the Material 3 way of showing a
 * settings section, in place of Telegram's own full-width rows on a flat
 * background.
 *
 * <p>An optional header sits above the card rather than inside it, which is
 * what keeps a screen of several cards readable at a glance.
 */
public class NebulaCard extends LinearLayout {

    private final NebulaTheme theme;
    private boolean hasRows;

    public NebulaCard(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(NebulaTheme.cornerMedium());
        background.setColor(theme.surfaceContainer());
        setBackground(background);
        // Rows draw ripples that must be clipped to the rounded shape.
        setClipToOutline(true);
    }

    /** Adds a row, with a divider between neighbours. */
    public NebulaCard add(View row) {
        if (hasRows) {
            addView(NebulaRow.divider(getContext()));
        }
        addView(row, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hasRows = true;
        return this;
    }

    public boolean isEmpty() {
        return !hasRows;
    }

    /**
     * The label above a card. Material 3 sets section headers in the accent
     * colour at label-small size, which is also how they read in the mockup.
     */
    public static View header(Context context, CharSequence text) {
        NebulaTheme theme = NebulaTheme.of(context);
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        view.setTextColor(theme.primary());
        view.setTypeface(AndroidUtilities.bold());
        view.setLetterSpacing(0.06f);
        view.setAllCaps(true);
        view.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(18),
                AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        return view;
    }
}
