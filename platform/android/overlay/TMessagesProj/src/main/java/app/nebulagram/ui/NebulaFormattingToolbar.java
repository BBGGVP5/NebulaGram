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
 * можно открыть круглой кнопкой «A», которая висит над правым краем поля:
 * панель раскрывается из кнопки влево и сворачивается обратно в неё. Ручное открытие сбрасывается после отправки и при скрытии
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
    /** 0 — панель свёрнута в кнопку, 1 — раскрыта на всю ширину. */
    private float revealProgress;
    private final android.graphics.Path revealClip = new android.graphics.Path();
    private FloatingOpener opener;
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
        // Справа место под «A»: открытая панель разворачивается из неё влево,
        // и кнопка остаётся у правого конца панели.
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(HEIGHT_DP + 8), AndroidUtilities.dp(4));
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
    private java.util.function.Supplier<View> anchor;
    /** Кнопка отправки/голосового: «A» встаёт ровно над ней. */
    public void setAnchor(java.util.function.Supplier<View> value) { anchor = value; }

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

    @Override public void draw(android.graphics.Canvas canvas) {
        if (revealProgress >= 1f) { super.draw(canvas); return; }
        float min = AndroidUtilities.dp(HEIGHT_DP);
        float width = getWidth();
        float left = width - (min + (width - min) * Math.max(0f, revealProgress));
        float radius = AndroidUtilities.dp(18) + (getHeight() / 2f - AndroidUtilities.dp(18)) * (1f - Math.min(1f, revealProgress));
        int save = canvas.save();
        revealClip.rewind();
        revealClip.addRoundRect(getScrollX() + left, 0, getScrollX() + width, getHeight(), radius, radius, android.graphics.Path.Direction.CW);
        canvas.clipPath(revealClip);
        super.draw(canvas);
        canvas.restoreToCount(save);
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
    public boolean toggleVisible() { return false; }

    /** Плавающая «A» видна, пока панель вообще может появиться. */
    private boolean openerVisible() { return eligible && preferenceEnabled; }

    /**
     * Ряд «A» над полем, который ещё не занят самой панелью. Поле ввода
     * добавляет его только к высоте для списка сообщений: список поднимается,
     * и кнопка не ложится поверх последнего сообщения. Отступ самого поля и
     * анимация вылета сообщения от него не зависят.
     */
    public int openerRow() {
        return openerVisible() ? Math.max(0, AndroidUtilities.dp(HEIGHT_DP + GAP_DP) - reservedHeight) : 0;
    }

    /** Нажатие на плавающую «A»: открыть или свернуть панель. */
    private void toggleManually() {
        if (expanded) {
            // Закрытие закрывает и панель, выехавшую из-за выделения.
            manual = false;
            selection = false;
        } else {
            manual = true;
        }
        tintToggle();
        layoutChanged.run();
    }

    /** Вызывается, когда «Aa» должна появиться или пропасть: поле меняет ширину. */
    public void setOnEligibilityChanged(Runnable value) { eligibilityChanged = value; }

    /** Выделение в поле: непустое — панель выезжает сама. */
    public void onSelection(int start, int end) {
        boolean value = start != end && start >= 0;
        if (value != selection) { selection = value; layoutChanged.run(); }
        if (expanded) highlightFormats();
    }

    private void tintToggle() {
        if (opener != null) opener.update();
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
            if (opener != null) opener.update();
        }
        boolean visible = eligible && preferenceEnabled && (manual || selection);
        if (expanded != visible) {
            expanded = visible;
            if (reveal != null) { reveal.cancel(); reveal = null; }
            int target = visible ? AndroidUtilities.dp(HEIGHT_DP + GAP_DP) : 0;
            if (opener != null) opener.update();
            if (!isAttachedToWindow() || !(getParent() instanceof View) || !((View) getParent()).isLaidOut() || NebulaGlass.reduced()) {
                reservedHeight = target;
                revealProgress = visible ? 1f : 0f;
                setAlpha(visible ? 1f : 0f);
                setVisibility(visible ? VISIBLE : GONE);
            } else {
                setVisibility(VISIBLE);
                setAlpha(1f);
                // Ряд под панель занимается сразу, без сдвига поля вниз, а сама
                // панель разворачивается из «A» влево и так же сворачивается в неё.
                if (visible) reservedHeight = target;
                final float fromProgress = revealProgress;
                reveal = android.animation.ValueAnimator.ofFloat(0f, 1f);
                reveal.setDuration(visible ? 340 : 240);
                reveal.setInterpolator(visible ? new android.view.animation.OvershootInterpolator(.8f)
                        : new android.view.animation.AccelerateDecelerateInterpolator());
                reveal.addUpdateListener(animation -> {
                    float t = (Float) animation.getAnimatedValue();
                    float linear = animation.getAnimatedFraction();
                    revealProgress = Math.max(0f, fromProgress + ((visible ? 1f : 0f) - fromProgress) * t);
                    row.setAlpha(Math.max(0f, Math.min(1f, (revealProgress - .35f) / .65f)));
                    // Свёрнутый остаток панели (круг 44 точки) гаснет под «A», а не
                    // остаётся тёмным пятном рядом с ней.
                    if (!visible) setAlpha(Math.max(0f, Math.min(1f, revealProgress / .3f)));
                    if (!visible && linear >= 1f) { reservedHeight = 0; setVisibility(GONE); }
                    invalidate();
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
        if (opener == null) opener = FloatingOpener.attach(this);
    }
    @Override protected void onDetachedFromWindow() {
        if (reveal != null) { reveal.cancel(); reveal = null; }
        expanded = false;
        reservedHeight = 0;
        revealProgress = 0f;
        if (opener != null) { opener.detach(); opener = null; }
        setAlpha(0f);
        setVisibility(GONE);
        preferences().unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        editor.removeTextChangedListener(emptiness);
        super.onDetachedFromWindow();
    }

    /**
     * Круглая «A» над правым краем поля ввода. Живёт в контейнере экрана чата,
     * чтобы висеть над перепиской, и каждый кадр встаёт над полем ввода.
     */
    private static final class FloatingOpener extends android.widget.FrameLayout
            implements android.view.ViewTreeObserver.OnPreDrawListener {
        private final NebulaFormattingToolbar toolbar;
        private final android.view.ViewGroup host;
        private final ImageView icon;
        private final GradientDrawable circle = new GradientDrawable();
        private boolean shown;
        private Boolean shownOpen;

        static FloatingOpener attach(NebulaFormattingToolbar toolbar) {
            android.view.ViewParent parent = toolbar.getParent();
            while (parent != null && !(parent instanceof org.telegram.ui.Components.SizeNotifierFrameLayout)) parent = parent.getParent();
            if (!(parent instanceof android.view.ViewGroup)) return null;
            return new FloatingOpener(toolbar, (android.view.ViewGroup) parent);
        }

        private FloatingOpener(NebulaFormattingToolbar toolbar, android.view.ViewGroup host) {
            super(toolbar.getContext());
            this.toolbar = toolbar;
            this.host = host;
            int size = AndroidUtilities.dp(40);
            circle.setShape(GradientDrawable.OVAL);
            setBackground(circle);
            setElevation(AndroidUtilities.dp(3));
            icon = new ImageView(getContext());
            icon.setImageResource(R.drawable.msg_text_outlined);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon, new LayoutParams(-1, -1));
            setContentDescription(NebulaText.text("Форматирование", "Formatting"));
            if (android.os.Build.VERSION.SDK_INT >= 26) setTooltipText(getContentDescription());
            setFocusable(false);
            setOnClickListener(v -> { NebulaHaptics.tick(v); toolbar.toggleManually(); });
            setVisibility(GONE);
            setAlpha(0f);
            host.addView(this, new android.widget.FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
            host.getViewTreeObserver().addOnPreDrawListener(this);
            update();
        }

        void detach() {
            host.getViewTreeObserver().removeOnPreDrawListener(this);
            animate().cancel();
            host.removeView(this);
        }

        /** Цвет, значок и видимость по состоянию панели. */
        void update() {
            NebulaTheme theme = NebulaTheme.of(getContext());
            boolean open = toolbar.expanded;
            circle.setColor(open ? theme.primary() : theme.surfaceContainer());
            circle.setStroke(AndroidUtilities.dp(1), NebulaTheme.stateLayer(theme.onSurface(), open ? 0f : .12f));
            icon.setColorFilter(open ? theme.onPrimary() : theme.onSurface());
            if (shownOpen != open) {
                // Значок меняется коротким «поджатием», без поворота: повёрнутая
                // на 90° «A» выглядела как стрелка «Отправить».
                shownOpen = open;
                icon.animate().cancel();
                icon.setRotation(0f);
                icon.setImageResource(open ? R.drawable.nebula_ios_close : R.drawable.msg_text_outlined);
                icon.setScaleX(.6f); icon.setScaleY(.6f); icon.setAlpha(.4f);
                icon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start();
            }
            boolean visible = toolbar.openerVisible();
            if (visible == shown) return;
            shown = visible;
            animate().cancel();
            if (visible) {
                setVisibility(VISIBLE);
                setScaleX(.4f); setScaleY(.4f);
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withEndAction(null).start();
            } else {
                // Прячем сразу: ряд под «A» в списке сообщений освобождается в тот же
                // кадр, и гаснущая кнопка ложилась поверх последнего сообщения.
                setAlpha(0f);
                setVisibility(GONE);
            }
        }

        private final int[] hostLocation = new int[2], composerLocation = new int[2], anchorLocation = new int[2], toolbarLocation = new int[2];

        @Override public boolean onPreDraw() {
            View composer = toolbar.getParent() instanceof View ? (View) toolbar.getParent() : null;
            if (composer == null || !shown) return true;
            // Поле ввода скрыто (другой экран поверх, режим записи): кнопку не показываем.
            int wanted = composer.isShown() && composer.getAlpha() > 0f ? VISIBLE : INVISIBLE;
            if (getVisibility() != wanted) setVisibility(wanted);
            if (wanted != VISIBLE) return true;
            host.getLocationInWindow(hostLocation);
            composer.getLocationInWindow(composerLocation);
            int size = getLayoutParams().width;
            // Над полем ввода. Раскрытая панель — верхняя часть поля ввода, так
            // что кнопка поднимается вместе с ней и висит над ней.
            float x = composerLocation[0] - hostLocation[0] + composer.getWidth() - size - AndroidUtilities.dp(12);
            // Пока панель занимает ряд над полем (и всё время, пока сворачивается),
            // верх поля ввода выше на этот ряд. Свёрнутое положение считаем от
            // самого поля: иначе при закрытии «A» уезжала вверх на последнее
            // сообщение, а в конце рывком падала на место.
            float y = composerLocation[1] - hostLocation[1] + toolbar.reservedHeight - size - AndroidUtilities.dp(6);
            // По центру кнопки голосового/отправки, а не на глаз от края поля.
            View target = toolbar.anchor != null ? toolbar.anchor.get() : null;
            if (target != null && target.isShown() && target.getWidth() > 0) {
                target.getLocationInWindow(anchorLocation);
                // Контейнер шире самой кнопки (100 точек под «Отправить»), а круглая
                // кнопка голосового/отправки — его правые 48 точек.
                float button = Math.min(target.getWidth(), AndroidUtilities.dp(48));
                x = anchorLocation[0] - hostLocation[0] + target.getWidth() - (button + size) / 2f;
                y = Math.min(y, anchorLocation[1] - hostLocation[1] - size - AndroidUtilities.dp(8));
            }
            // Открытая панель: «A» стоит в её правом конце, панель разворачивается
            // из кнопки влево. Между положениями кнопка едет вместе с разворотом.
            float progress = Math.max(0f, Math.min(1f, toolbar.revealProgress));
            if (progress > 0f && toolbar.getVisibility() == VISIBLE && toolbar.getWidth() > 0) {
                toolbar.getLocationInWindow(toolbarLocation);
                float cap = AndroidUtilities.dp(HEIGHT_DP);
                float openX = toolbarLocation[0] - hostLocation[0] + toolbar.getWidth() - (cap + size) / 2f;
                float openY = toolbarLocation[1] - hostLocation[1] + (toolbar.getHeight() - size) / 2f;
                x += (openX - x) * progress;
                y += (openY - y) * progress;
            }
            if (getX() != x) setX(x);
            if (getY() != y) setY(y);
            return true;
        }
    }
}
