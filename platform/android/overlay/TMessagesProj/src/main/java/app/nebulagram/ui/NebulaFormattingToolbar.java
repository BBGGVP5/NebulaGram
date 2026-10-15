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
import android.widget.ImageView;
import org.telegram.messenger.R;
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
    private int palette, paletteInk, paletteChip;

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
        addView(row, new LayoutParams(-2, -1));
        addIcon(R.drawable.msg_clear, "Убрать форматирование", "Clear formatting", editor::makeSelectedRegular);
        addIcon(R.drawable.formatting_quote, "Цитата", "Quote", editor::makeSelectedQuote);
        addIcon(R.drawable.formatting_spoiler, "Спойлер", "Spoiler", editor::makeSelectedSpoiler);
        add("B", "Жирный", "Bold", Typeface.BOLD, editor::makeSelectedBold);
        add("I", "Курсив", "Italic", Typeface.ITALIC, editor::makeSelectedItalic);
        add("M", "Моноширинный", "Monospace", Typeface.NORMAL, editor::makeSelectedMono);
        addIcon(R.drawable.msg_link, "Ссылка", "Link", editor::makeSelectedUrl);
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
        bind(button, ru, en, action);
    }

    private void addIcon(int resource, String ru, String en, Runnable action) {
        ImageView button = new ImageView(getContext());
        button.setImageResource(resource);button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset = AndroidUtilities.dp(12);button.setPadding(inset,inset,inset,inset);
        bind(button,ru,en,action);
    }

    private void bind(View button, String ru, String en, Runnable action) {
        button.setContentDescription(NebulaText.text(ru, en));
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), -1);
        params.setMarginEnd(AndroidUtilities.dp(6));
        row.addView(button, params);
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
        int chip = theme.surface();
        if (palette == color && paletteInk == ink && paletteChip == chip && getBackground() != null) return;
        palette = color;
        paletteInk = ink;
        paletteChip = chip;
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(AndroidUtilities.dp(16));
        setBackground(background);
        for (int i = 0; i < row.getChildCount(); i++) {
            View button = row.getChildAt(i);
            if (button instanceof TextView) ((TextView) button).setTextColor(ink);
            else ((ImageView) button).setColorFilter(ink);
            GradientDrawable tile = new GradientDrawable();tile.setColor(chip);tile.setCornerRadius(AndroidUtilities.dp(10));
            button.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(NebulaTheme.stateLayer(ink,.1f)),tile,null));
        }
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
