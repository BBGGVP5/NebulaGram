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

/**
 * Панель форматирования над полем ввода.
 *
 * <p>Раньше она стояла всегда, пока открыта клавиатура, и отнимала у
 * переписки 47 точек, даже когда человек просто пишет. Теперь её не видно,
 * пока она не нужна: она выезжает сама, когда в поле выделен текст, и её
 * можно открыть кнопкой «Aa» в поле, чтобы включить формат заранее или
 * вставить строку. Ручное открытие сбрасывается после отправки и при скрытии
 * клавиатуры. Формат, который уже есть в выделении, подсвечен.
 *
 * <p>Source reference: Swiftgram SGInputToolbar (cf8b23be); independent native editor accessory.
 */
public final class NebulaFormattingToolbar extends HorizontalScrollView {
    public static final String KEY = "formatting_toolbar";
    /** Ниже прежних 52: панель стоит над полем ввода и отъедает у переписки. */
    public static final int HEIGHT_DP = 44;
    public static final int GAP_DP = 3;
    private final EditTextCaption editor;
    private final Runnable layoutChanged;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged = (prefs, key) -> {
        if (KEY.equals(key)) refreshPreference();
    };
    private final LinearLayout row;
    private boolean preferenceEnabled;
    private boolean expanded;
    private boolean eligible, manual, selection;
    private Runnable eligibilityChanged;
    private ImageView toggle;
    private final java.util.Map<View, Integer> formatFlags = new java.util.HashMap<>();
    private int reservedHeight;
    private android.animation.ValueAnimator reveal;
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
        // Keep scrolling tiles inside the rounded bar's inner gutter.
        setClipToPadding(true);
        setClipToOutline(true);
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        setFocusable(false);
        row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(-2, -1));
        addIcon(R.drawable.nebula_format_newline, "Новая строка", "New line", this::insertNewLine, false);
        addIcon(R.drawable.nebula_format_clear, "Убрать форматирование", "Clear formatting", editor::makeSelectedRegular);
        nextFlag = FLAG_QUOTE; addIcon(R.drawable.nebula_format_quote, "Цитата", "Quote", editor::makeSelectedQuote);
        nextFlag = FLAG_SPOILER; addIcon(R.drawable.nebula_format_spoiler, "Спойлер", "Spoiler", editor::makeSelectedSpoiler);
        nextFlag = FLAG_BOLD; add("B", "Жирный", "Bold", Typeface.BOLD, editor::makeSelectedBold);
        nextFlag = FLAG_ITALIC; add("I", "Курсив", "Italic", Typeface.ITALIC, editor::makeSelectedItalic);
        nextFlag = FLAG_MONO; add("M", "Моноширинный", "Monospace", Typeface.NORMAL, editor::makeSelectedMono);
        nextFlag = FLAG_LINK; addIcon(R.drawable.nebula_format_link, "Ссылка", "Link", editor::makeSelectedUrl);
        nextFlag = FLAG_UNDERLINE; addIcon(R.drawable.formatting_underline, "Подчёркнутый", "Underline", editor::makeSelectedUnderline);
        nextFlag = FLAG_STRIKE; addIcon(R.drawable.formatting_strikethrough, "Зачёркнутый", "Strikethrough", editor::makeSelectedStrike);
        nextFlag = FLAG_CODE; addIcon(R.drawable.nebula_format_code, "Блок кода", "Code block", editor::makeSelectedCode);
        nextFlag = 0;
        updatePalette();
        setVisibility(GONE);
    }

    /** Биты форматов: стили совпадают с TextStyleSpan, остальное — свои. */
    public static final int FLAG_BOLD = 1, FLAG_ITALIC = 1 << 1, FLAG_MONO = 1 << 2, FLAG_STRIKE = 1 << 3,
            FLAG_UNDERLINE = 1 << 4, FLAG_SPOILER = 1 << 8,
            FLAG_QUOTE = 1 << 28, FLAG_LINK = 1 << 29, FLAG_CODE = 1 << 30;
    /** Какие форматы целиком покрывают выделение. Даёт поле ввода: знает его спаны. */
    public interface Formats { int present(android.text.Spanned text, int start, int end); }
    private Formats formats;
    private int toggleIdle;
    private int nextFlag;
    public void setFormats(Formats value) { formats = value; }

    private void add(String glyph, String ru, String en, int style, Runnable action) {
        add(glyph, ru, en, style, action, true);
    }

    private final java.util.List<View> selectionButtons = new java.util.ArrayList<>();
    private boolean shownEmpty = true;

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
        if (android.os.Build.VERSION.SDK_INT >= 26) button.setTooltipText(button.getContentDescription());
        button.setOnClickListener(v -> {
            if (!expanded) return;
            if (!needsSelection) { action.run(); return; }
            int caret = editor.getSelectionStart();
            boolean implicit = caret == editor.getSelectionEnd();
            int[] selection = NebulaFormattingSelection.range(editor.getText(), editor.getSelectionStart(), editor.getSelectionEnd(), true);
            if (selection == null || selection[0] == selection[1]) {
                announceForAccessibility(NebulaText.text("Сначала выделите текст", "Select text first"));
                return;
            }
            editor.beginBatchEdit();
            try {
                editor.setSelection(selection[0], selection[1]);
                editor.setSelectionOverride(selection[0], selection[1]);
                action.run();
                // Selecting a word on the user's behalf must not make subsequent
                // typing replace it. Keep explicit selections and native quote caret moves.
                if (implicit && editor.getSelectionStart() == selection[0]
                        && editor.getSelectionEnd() == selection[1]) {
                    editor.setSelection(Math.min(caret, editor.length()));
                }
            } finally {
                editor.setSelectionOverride(-1, -1);
                editor.endBatchEdit();
            }
            editor.invalidate();
            highlightFormats();
        });
        if (needsSelection) selectionButtons.add(button);
        if (nextFlag != 0) formatFlags.put(button, nextFlag);
        // Отклик на касание: кнопка коротко поджимается и возвращается. Без
        // него нажатие на плитку ничем не отличается от промаха по панели.
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (view.isEnabled()) view.animate().scaleX(.88f).scaleY(.88f).setDuration(70).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                    break;
                default:
                    break;
            }
            return false; // жест остаётся у onClick и ряби
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

    /** Кнопка «Aa» для поля ввода: открывает и закрывает панель вручную. */
    public ImageView toggle(Context context, int icon, int idleColor) {
        if (toggle != null) return toggle;
        toggleIdle = idleColor;
        toggle = new ImageView(context);
        toggle.setImageResource(icon);
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        toggle.setContentDescription(NebulaText.text("Форматирование", "Formatting"));
        if (android.os.Build.VERSION.SDK_INT >= 26) toggle.setTooltipText(toggle.getContentDescription());
        toggle.setFocusable(false);
        NebulaComposerStyle.registerFormatToggle(toggle);
        toggle.setVisibility(GONE);
        toggle.setOnClickListener(v -> { manual = !manual; tintToggle(); layoutChanged.run(); });
        tintToggle();
        return toggle;
    }

    /** «Aa» показывается, пока панель вообще может появиться. */
    public boolean toggleVisible() { return toggle != null && eligible && preferenceEnabled; }

    /** Вызывается, когда «Aa» должна появиться или пропасть: поле меняет ширину. */
    public void setOnEligibilityChanged(Runnable value) { eligibilityChanged = value; }

    /** Выделение в поле: непустое — панель выезжает сама. */
    public void onSelection(int start, int end) {
        boolean value = start != end && start >= 0;
        if (value != selection) { selection = value; layoutChanged.run(); }
        if (expanded) highlightFormats();
    }

    private void tintToggle() {
        if (toggle == null) return;
        NebulaTheme theme = NebulaTheme.of(getContext());
        toggle.setColorFilter(manual ? theme.primary() : toggleIdle);
    }

    public int prepare(boolean eligible) {
        if (this.eligible != eligible) {
            this.eligible = eligible;
            // Клавиатура убрана — ручное открытие сбрасывается.
            if (!eligible && manual) { manual = false; tintToggle(); }
            if (eligibilityChanged != null) post(eligibilityChanged);
        }
        boolean visible = eligible && preferenceEnabled && (manual || selection);
        if (expanded != visible) {
            expanded = visible;
            if (reveal != null) { reveal.cancel(); reveal = null; }
            int target = visible ? AndroidUtilities.dp(HEIGHT_DP + GAP_DP) : 0;
            if (!isAttachedToWindow() || !(getParent() instanceof View) || !((View) getParent()).isLaidOut() || NebulaGlass.reduced()) {
                reservedHeight = target;
                setAlpha(visible ? 1f : 0f);
                setVisibility(visible ? VISIBLE : GONE);
            } else {
                setVisibility(VISIBLE);
                reveal = android.animation.ValueAnimator.ofInt(reservedHeight, target);
                reveal.setDuration(180);
                reveal.setInterpolator(new android.view.animation.DecelerateInterpolator());
                reveal.addUpdateListener(animation -> {
                    reservedHeight = (Integer) animation.getAnimatedValue();
                    setAlpha(Math.min(1f, reservedHeight / (float) AndroidUtilities.dp(HEIGHT_DP + GAP_DP)));
                    if (reservedHeight == 0 && !expanded) setVisibility(GONE);
                    layoutChanged.run();
                });
                reveal.start();
            }
        }
        if (visible) { updatePalette(); refreshState(); highlightFormats(); }
        return reservedHeight;
    }

    /**
     * Гасит кнопки, которым нечего форматировать.
     *
     * <p>Состояние форматов панель не показывает намеренно: подсветка на
     * одиннадцати плитках спорит за внимание с самим текстом, который в этот
     * момент и пишут. Доступность — другое дело: кнопка, молчащая на нажатие,
     * выглядит сломанной.
     */
    private void refreshState() {
        final CharSequence text = editor.getText();
        final boolean empty = text == null || text.length() == 0;
        if (empty == shownEmpty) return;
        shownEmpty = empty;
        for (int i = 0; i < selectionButtons.size(); i++) {
            selectionButtons.get(i).setEnabled(!empty);
            selectionButtons.get(i).setAlpha(empty ? .45f : 1f);
        }
    }

    /** Подсвечивает форматы, которые уже есть во всём выделении. */
    private void highlightFormats() {
        android.text.Editable text = editor.getText();
        int start = editor.getSelectionStart(), end = editor.getSelectionEnd();
        int present = text != null && formats != null && start >= 0 && end > start ? formats.present(text, start, end) : 0;
        for (java.util.Map.Entry<View, Integer> entry : formatFlags.entrySet()) {
            boolean on = (present & entry.getValue()) != 0;
            View button = entry.getKey();
            if (button.isSelected() == on) continue;
            button.setSelected(on);
            int ink = on ? NebulaTheme.of(getContext()).primary() : paletteInk;
            if (button instanceof TextView) ((TextView) button).setTextColor(ink);
            else ((ImageView) button).setColorFilter(ink);
        }
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
            int buttonInk = button.isSelected() ? NebulaTheme.of(getContext()).primary() : ink;
            if (button instanceof TextView) ((TextView) button).setTextColor(buttonInk);
            else ((ImageView) button).setColorFilter(buttonInk);
            GradientDrawable tile = new GradientDrawable();
            tile.setColor(chip);
            tile.setCornerRadius(NebulaShape.radius(11));
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

    private final android.text.TextWatcher emptiness = new android.text.TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(android.text.Editable s) {
            refreshState();
            // Сообщение отправлено — поле опустело, ручное открытие закрывается.
            if (manual && s.length() == 0) { manual = false; tintToggle(); layoutChanged.run(); }
        }
    };

    private void refreshPreference() {
        boolean was = preferenceEnabled;
        preferenceEnabled = enabled();
        if (was != preferenceEnabled && eligibilityChanged != null) post(eligibilityChanged);
        layoutChanged.run();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences().registerOnSharedPreferenceChangeListener(preferencesChanged);
        editor.addTextChangedListener(emptiness);
        refreshState();
        refreshPreference();
    }
    @Override protected void onDetachedFromWindow() {
        if (reveal != null) { reveal.cancel(); reveal = null; }
        expanded = false;
        reservedHeight = 0;
        setAlpha(0f);
        setVisibility(GONE);
        preferences().unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        editor.removeTextChangedListener(emptiness);
        super.onDetachedFromWindow();
    }
}
