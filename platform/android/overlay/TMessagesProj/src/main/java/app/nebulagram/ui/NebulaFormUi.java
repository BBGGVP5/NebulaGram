package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

/** Shared, explicitly themed controls for tools, tasks and private settings. */
public final class NebulaFormUi {
    private NebulaFormUi() { }
    public static int dp(float n) { return AndroidUtilities.dp(n); }
    public static void bar(BaseFragment host, ActionBar bar, Context c, String title) {
        NebulaTheme theme = NebulaTheme.of(c);
        bar.setTitle(title);
        bar.setBackButtonImage(R.drawable.ic_ab_back);
        bar.setBackgroundColor(theme.surface());
        bar.setTitleColor(theme.onSurface());
        bar.setItemsColor(theme.onSurface(), false);
        bar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) host.finishFragment(); }
        });
    }
    public static LinearLayout column(Context c) {
        LinearLayout view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(12), dp(16), dp(28));
        return view;
    }
    public static ScrollView scroll(Context c, LinearLayout column) {
        ScrollView view = new ScrollView(c);
        view.setBackgroundColor(NebulaTheme.of(c).surface());
        view.setFillViewport(true);
        view.setClipToPadding(false);
        view.addView(column);
        return view;
    }
    public static TextView note(Context c, String text) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(NebulaTheme.of(c).onSurfaceVariant());
        view.setLineSpacing(dp(2), 1f);
        view.setPadding(dp(16), dp(10), dp(16), dp(12));
        return view;
    }
    public static void group(LinearLayout column, String title, View content) {
        column.addView(NebulaCard.header(column.getContext(), title));
        column.addView(content, new LinearLayout.LayoutParams(-1, -2));
    }
    public static EditTextBoldCursor field(Context c, String hint, int lines, int limit) {
        NebulaTheme theme = NebulaTheme.of(c);
        EditTextBoldCursor view = new EditTextBoldCursor(c);
        view.setTextColor(theme.onSurface());
        view.setHintTextColor(theme.onSurfaceVariant());
        view.setCursorColor(theme.primary());
        view.setTextSize(16);
        view.setHint(hint);
        view.setContentDescription(hint);
        view.setGravity(Gravity.TOP | Gravity.START);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setMinLines(lines);
        view.setMaxLines(lines == 1 ? 1 : 8);
        view.setSingleLine(lines == 1);
        view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, fieldBackground(theme, true));
        states.addState(new int[]{}, fieldBackground(theme, false));
        view.setBackground(states);
        return view;
    }
    private static GradientDrawable fieldBackground(NebulaTheme theme, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surfaceContainer());
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), focused ? theme.primary() : theme.outline());
        return background;
    }
    public static NebulaRow action(Context c, int icon, String title, String subtitle, View.OnClickListener click) {
        return new NebulaRow(c).icon(icon).title(title).subtitle(subtitle, false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(click);
    }
    public static NebulaButton primary(Context c, String text, View.OnClickListener click) {
        NebulaButton button = new NebulaButton(c, NebulaButton.STYLE_FILLED);
        button.setText(text);
        button.setSingleLine(false);
        button.setMinHeight(dp(52));
        button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(20);
        button.setLayoutParams(params);
        return button;
    }
}
