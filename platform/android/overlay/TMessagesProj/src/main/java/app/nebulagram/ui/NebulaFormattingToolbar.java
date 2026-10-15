package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.EditTextCaption;

/** A keyboard accessory. Native editor operations retain Telegram entities and draft callbacks. */
public final class NebulaFormattingToolbar extends HorizontalScrollView {
    public static final String KEY = "formatting_toolbar";
    public static final int HEIGHT_DP = 52;
    private final EditTextCaption editor;
    private final Runnable layoutChanged;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> {
        if (KEY.equals(key)) refreshPreference();
    };
    private final LinearLayout row;
    private boolean preferenceEnabled;
    private int palette, paletteInk;

    public static boolean enabled() { return preferences().getBoolean("formatting_toolbar", false); }
    public static void setPreferenceEnabled(boolean value) { preferences().edit().putBoolean(KEY, value).apply(); }
    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }

    public NebulaFormattingToolbar(Context context, EditTextCaption editor, Runnable layoutChanged) {
        super(context);
        this.editor = editor;
        this.layoutChanged = layoutChanged;
        preferenceEnabled = enabled();
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        setClipToPadding(false);
        setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        setFocusable(false);
        row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumWidth(AndroidUtilities.dp(308));
        addView(row, new LayoutParams(-1, -1));
        add("B", "Жирный", "Bold", Typeface.BOLD, editor::makeSelectedBold);
        add("I", "Курсив", "Italic", Typeface.ITALIC, editor::makeSelectedItalic);
        add("M", "Моноширинный", "Monospace", Typeface.NORMAL, editor::makeSelectedMono);
        add("❞", "Цитата", "Quote", Typeface.NORMAL, editor::makeSelectedQuote);
        add("◉", "Спойлер", "Spoiler", Typeface.NORMAL, editor::makeSelectedSpoiler);
        add("↗", "Ссылка", "Link", Typeface.NORMAL, editor::makeSelectedUrl);
        add("T×", "Убрать форматирование", "Clear formatting", Typeface.NORMAL, editor::makeSelectedRegular);
        updatePalette();
        setVisibility(GONE);
    }

    private void add(String glyph, String ru, String en, int style, Runnable action) {
        TextView button = new TextView(getContext());
        button.setText(glyph);
        button.setTextSize(19);
        button.setTypeface("M".equals(glyph) ? Typeface.MONOSPACE : Typeface.defaultFromStyle(style));
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(NebulaText.text(ru, en));
        button.setMinimumWidth(AndroidUtilities.dp(44));
        button.setFocusable(false); // tapping must not move focus/selection away from the IME
        button.setOnClickListener(v -> {
            int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
            int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
            if (start < 0 || end <= start || end > editor.length()) {
                announceForAccessibility(NebulaText.text("Сначала выделите текст", "Select text first"));
                return;
            }
            editor.setSelection(start, end);
            editor.setSelectionOverride(start, end);
            action.run();
            editor.invalidate();
        });
        row.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
    }

    public int prepare(boolean eligible) {
        boolean visible = eligible && preferenceEnabled;
        if (getVisibility() != (visible ? VISIBLE : GONE)) setVisibility(visible ? VISIBLE : GONE);
        if (visible) updatePalette();
        return visible ? AndroidUtilities.dp(HEIGHT_DP) : 0;
    }

    private void updatePalette() {
        NebulaTheme theme = NebulaTheme.of(getContext());
        int color = theme.surfaceContainer();
        int ink = theme.onSurface();
        if (palette == color && paletteInk == ink && getBackground() != null) return;
        palette = color;
        paletteInk = ink;
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(AndroidUtilities.dp(22));
        setBackground(background);
        for (int i = 0; i < row.getChildCount(); i++) ((TextView) row.getChildAt(i)).setTextColor(ink);
    }

    private void refreshPreference() { preferenceEnabled = enabled(); layoutChanged.run(); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences().registerOnSharedPreferenceChangeListener(preferencesChanged);
        refreshPreference();
    }
    @Override protected void onDetachedFromWindow() {
        preferences().unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        super.onDetachedFromWindow();
    }
}
