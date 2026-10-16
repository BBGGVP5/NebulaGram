package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
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

/** Source reference: Swiftgram SGInputToolbar (cf8b23be); independent native editor accessory. */
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
        setClipToOutline(true);
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        setFocusable(false);
        row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(-2, -1));
        addIcon(R.drawable.nebula_format_newline, "Новая строка", "New line", this::insertNewLine, false);
        addIcon(R.drawable.nebula_format_clear, "Убрать форматирование", "Clear formatting", editor::makeSelectedRegular);
        addIcon(R.drawable.nebula_format_quote, "Цитата", "Quote", editor::makeSelectedQuote);
        addIcon(R.drawable.nebula_format_spoiler, "Спойлер", "Spoiler", editor::makeSelectedSpoiler);
        add("B", "Жирный", "Bold", Typeface.BOLD, editor::makeSelectedBold);
        add("I", "Курсив", "Italic", Typeface.ITALIC, editor::makeSelectedItalic);
        add("M", "Моноширинный", "Monospace", Typeface.NORMAL, editor::makeSelectedMono);
        addIcon(R.drawable.msg_link, "Ссылка", "Link", editor::makeSelectedUrl);
        addIcon(R.drawable.formatting_underline, "Подчёркнутый", "Underline", editor::makeSelectedUnderline);
        addIcon(R.drawable.formatting_strikethrough, "Зачёркнутый", "Strikethrough", editor::makeSelectedStrike);
        addIcon(R.drawable.formatting_code, "Блок кода", "Code block", editor::makeSelectedCode);
        updatePalette();
        setVisibility(GONE);
    }

    private void add(String glyph, String ru, String en, int style, Runnable action) {
        add(glyph, ru, en, style, action, true);
    }

    private void add(String glyph, String ru, String en, int style, Runnable action, boolean needsSelection) {
        TextView button = new TextView(getContext());
        button.setText(glyph);
        button.setTextSize(19);
        button.setTypeface("M".equals(glyph) ? Typeface.MONOSPACE : Typeface.defaultFromStyle(style));
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(NebulaText.text(ru, en));
        bind(button, ru, en, action, needsSelection);
    }

    private void addIcon(int resource, String ru, String en, Runnable action) {
        addIcon(resource, ru, en, action, true);
    }

    private void addIcon(int resource, String ru, String en, Runnable action, boolean needsSelection) {
        ImageView button = new ImageView(getContext());
        button.setImageResource(resource);button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bind(button,ru,en,action,needsSelection);
    }

    private void bind(View button, String ru, String en, Runnable action, boolean needsSelection) {
        button.setContentDescription(NebulaText.text(ru, en));
        button.setFocusable(false); // tapping must not move focus/selection away from the IME
        button.setOnClickListener(v -> {
            if (!needsSelection) { action.run(); return; }
            int[] selection = NebulaFormattingSelection.range(editor.getText(), editor.getSelectionStart(), editor.getSelectionEnd(), true);
            if (selection == null || selection[0] == selection[1]) {
                announceForAccessibility(NebulaText.text("Сначала выделите текст", "Select text first"));
                return;
            }
            editor.setSelection(selection[0], selection[1]);
            editor.setSelectionOverride(selection[0], selection[1]);
            action.run();
            editor.invalidate();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), -1);
        // 39dp visual tile + 5dp touch inset + 7dp gap = Swiftgram's 12dp visual spacing.
        params.setMarginEnd(AndroidUtilities.dp(row.getChildCount() == 1 ? 19 : 7));
        row.addView(button, params);
    }

    private void insertNewLine() {
        int[] range = NebulaFormattingSelection.range(editor.getText(), editor.getSelectionStart(), editor.getSelectionEnd(), false);
        if (range == null) return;
        // Do not synthesize Enter: Telegram may interpret that IME action as Send.
        editor.getText().replace(range[0], range[1], "\n");
        editor.setSelection(range[0] + 1);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getSize(heightMeasureSpec) == 0) {
            // Native FrameLayout ignores this overlay for its minimum height. Its final
            // accessory measurement follows once; do not measure every tile at 0 then 52.
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
        int chip = blend(color, ink, .10f);
        if (palette == color && paletteInk == ink && paletteChip == chip && getBackground() != null) return;
        palette = color;
        paletteInk = ink;
        paletteChip = chip;
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(AndroidUtilities.dp(18));
        setBackground(background);
        for (int i = 0; i < row.getChildCount(); i++) {
            View button = row.getChildAt(i);
            if (button instanceof TextView) ((TextView) button).setTextColor(ink);
            else ((ImageView) button).setColorFilter(ink);
            GradientDrawable tile = new GradientDrawable();tile.setColor(chip);tile.setCornerRadius(AndroidUtilities.dp(11));
            button.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(NebulaTheme.stateLayer(ink,.1f)),
                    new InsetDrawable(tile, AndroidUtilities.dp(2.5f)),null));
            // A new RippleDrawable adopts its InsetDrawable's padding. Restore the
            // glyph's own inset after every theme/background change, not before it.
            if (button instanceof ImageView) {
                int inset = AndroidUtilities.dp(11);
                button.setPadding(inset, inset, inset, inset);
            }
        }
    }

    private static int blend(int base, int ink, float fraction) {
        return android.graphics.Color.rgb(Math.round(android.graphics.Color.red(base) * (1 - fraction) + android.graphics.Color.red(ink) * fraction),
                Math.round(android.graphics.Color.green(base) * (1 - fraction) + android.graphics.Color.green(ink) * fraction),
                Math.round(android.graphics.Color.blue(base) * (1 - fraction) + android.graphics.Color.blue(ink) * fraction));
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
