package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CodeFieldContainer;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.SlideView;

/** Presentation adapters for the pinned native login widgets; no authentication state. */
public final class NebulaLoginStyle {
    private NebulaLoginStyle() { }

    public static boolean enabled() {
        try {
            return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0)
                    .getBoolean("login_style", true);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void setEnabled(boolean value) {
        ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0)
                .edit().putBoolean("login_style", value).apply();
    }

    /**
     * Во сколько раз ужать вертикаль на этом экране.
     *
     * <p>Размеры входа подобраны на экране высотой около семисот точек. На
     * коротком сумма «картинка, надпись, поля, кнопка» перестаёт помещаться:
     * содержимое центрируется в полосе выше кнопки, а всё, что не влезло,
     * уходит под неё — на снимке с экрана ячейки кода наполовину закрыты
     * «Продолжить». Поэтому вертикальные размеры берутся долей от того, что
     * есть, а не фиксированными точками.
     *
     * <p>Нижняя граница не даёт ужать до нечитаемого: дальше экран прокручивается.
     */
    static float vertical() {
        float density = AndroidUtilities.density;
        if (density <= 0 || AndroidUtilities.displaySize == null) {
            return 1f;
        }
        float points = AndroidUtilities.displaySize.y / density;
        if (points <= 0) {
            return 1f;
        }
        return Math.max(0.62f, Math.min(1f, (points - 120f) / 580f));
    }

    /** Точки, ужатые под высоту экрана. */
    static int compact(float points) {
        return AndroidUtilities.dp(points * vertical());
    }

    public static boolean styled(View slide) {
        return slide != null && slide.getTag(R.id.nebula_auth_step) instanceof Integer;
    }

    /** Встроенная цифровая клавиатура Telegram: её показывает сам экран входа. */
    public interface Keyboard {
        void setVisible(boolean visible);
    }

    public static void phone(SlideView slide, TextView title, TextView subtitle,
                             View country, View phone, View field, Keyboard keyboard) {
        decorate(slide, title, subtitle, 2, R.string.NebulaAuthPhoneEyebrow);
        title.setText(LocaleController.getString(R.string.NebulaAuthPhoneTitle));
        // Phone masks are hints, not completion signals: keep editing under user control.
        if (keyboard != null) field.setOnClickListener(v -> keyboard.setVisible(true));
        fieldMargins(country, Math.round(26 * vertical()), 6);
        fieldMargins(phone, 8, Math.round(12 * vertical()));
        centerFieldContent(country);

        for (int i = 0; i < slide.getChildCount(); i++) {
            View child = slide.getChildAt(i);
            if (child instanceof Space) child.setVisibility(View.GONE);
            else if (child.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.weight = 0;
                params.setMarginStart(0);
                params.setMarginEnd(0);
            }
        }
    }

    public static void code(SlideView slide, TextView title, TextView subtitle,
                            View digits, View illustration, View bottom, View error) {
        if (illustration != null && illustration.getParent() instanceof View) {
            ((View) illustration.getParent()).setVisibility(View.GONE);
        }
        decorate(slide, title, subtitle, 3, R.string.NebulaAuthCodeEyebrow);
        LinearLayout.LayoutParams digitParams = (LinearLayout.LayoutParams) digits.getLayoutParams();
        digitParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        digitParams.topMargin = compact(28);
        digitParams.bottomMargin = compact(8);
        if (bottom != null) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bottom.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.weight = 0;
            params.topMargin = AndroidUtilities.dp(8);
        }
        if (error != null && error.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) error.getLayoutParams();
            params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            params.bottomMargin = 0;
        }
    }

    public static void password(SlideView slide, TextView title, TextView subtitle,
                                View field, View illustration, TextView recovery) {
        if (illustration != null && illustration.getParent() instanceof View) {
            ((View) illustration.getParent()).setVisibility(View.GONE);
        }
        decorate(slide, title, subtitle, 4, R.string.NebulaAuthPasswordEyebrow);
        title.setText(LocaleController.getString(R.string.NebulaAuthPasswordTitle));
        fieldMargins(field, Math.round(28 * vertical()), 8);
        recovery.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        recovery.setGravity(Gravity.CENTER);
        recovery.setMinimumHeight(AndroidUtilities.dp(48));
        recovery.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        FrameLayout.LayoutParams buttonParams = (FrameLayout.LayoutParams) recovery.getLayoutParams();
        buttonParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        buttonParams.bottomMargin = 0;
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        LinearLayout.LayoutParams holderParams = (LinearLayout.LayoutParams) ((View) recovery.getParent()).getLayoutParams();
        holderParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        holderParams.weight = 0;
    }

    private static void decorate(SlideView slide, TextView title, TextView subtitle, int step, int eyebrow) {
        slide.setTag(R.id.nebula_auth_step, step);
        slide.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        slide.setPadding(AndroidUtilities.dp(8), compact(26), AndroidUtilities.dp(8), compact(20));
        slide.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        // Нажатие мимо поля убирает клавиатуру. Пустое место экрана иначе
        // ничем не занято, а клавиатура закрывала кнопку и подсказки.
        slide.setClickable(true);
        slide.setOnClickListener(v -> AndroidUtilities.hideKeyboard(v));

        // Картинка объясняет шаг: на экране номера цифры набираются сами, на
        // экране кода заполняются ячейки. Знак приложения там был не к месту —
        // он одинаков везде и о шаге ничего не говорит.
        final int artAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
        final int artMuted = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), artAccent, .25f);
        final android.graphics.drawable.Drawable art;
        final Runnable release;
        final boolean animated = step == 2 || step == 3;
        if (animated) {
            final NebulaAuthArt animation = new NebulaAuthArt(
                    step == 2 ? NebulaAuthArt.KIND_PHONE : NebulaAuthArt.KIND_CODE,
                    artAccent, artMuted);
            art = animation;
            release = animation::detach;
        } else {
            final NebulaMark mark = new NebulaMark(artAccent,
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            art = mark;
            release = mark::detach;
        }
        ImageView badge = new ImageView(slide.getContext()) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                art.setVisible(isShown(), false);
            }

            @Override
            protected void onDetachedFromWindow() {
                art.setVisible(false, false);
                release.run();
                super.onDetachedFromWindow();
            }
        };
        badge.setImageDrawable(art);
        // Под рисунком шага плитки нет: в макетах её не было, а здесь она ещё
        // и срезала бы движение по краям квадрата. Знак приложения на прочих
        // шагах остаётся на своей подложке.
        if (!animated) {
            badge.setBackground(surface(28));
        }
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        final int side = compact(animated ? 188 : 84);
        // Экрану номера высота квадрата ни к чему: там широкая полоса
        // барабанов, и под ней с над ней оставалась пустота в треть картинки.
        final int tall = step == 2 ? compact(112) : side;
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(side, tall);
        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
        badgeParams.bottomMargin = compact(22);
        slide.addView(badge, 0, badgeParams);
        TextView stepLabel = new TextView(slide.getContext());
        stepLabel.setText(LocaleController.getString(eyebrow));
        stepLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        stepLabel.setTypeface(AndroidUtilities.bold());
        stepLabel.setLetterSpacing(0.06f);
        stepLabel.setGravity(Gravity.CENTER);
        stepLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        stepLabel.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        GradientDrawable stepBackground = new GradientDrawable();
        stepBackground.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), 28));
        stepBackground.setCornerRadius(AndroidUtilities.dp(14));
        stepLabel.setBackground(stepBackground);
        LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepParams.gravity = Gravity.CENTER_HORIZONTAL;
        stepParams.bottomMargin = compact(12);
        slide.addView(stepLabel, 1, stepParams);

        // Кегль ужимается вдвое медленнее отступов: читаемость важнее места.
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22 + 4 * vertical());
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        // Длинный заголовок обрезался: тридцать пунктов в одну строку не
        // помещаются на узком экране. Даём вторую строку вместо многоточия.
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setEllipsize(null);
        title.setLineSpacing(AndroidUtilities.dp(2), 1f);
        margins(title, 0, 0, 0, 0);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 + vertical());
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(AndroidUtilities.dp(3), 1f);
        margins(subtitle, 8, Math.round(10 * vertical()), 8, 0);
    }

    private static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.leftMargin = AndroidUtilities.dp(left);
        params.topMargin = AndroidUtilities.dp(top);
        params.rightMargin = AndroidUtilities.dp(right);
        params.bottomMargin = AndroidUtilities.dp(bottom);
    }

    private static void fieldMargins(View view, int top, int bottom) {
        margins(view, 0, top, 0, bottom);
        view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        view.setMinimumHeight(AndroidUtilities.dp(64));
    }

    /**
     * Ставит содержимое поля по центру его рамки.
     *
     * <p>Рамку и заливку Telegram рисует от верхнего отступа в шесть точек до
     * нижнего края, так что середина поля известна. Дальше всё зависит от
     * разметки, которую он собрал внутри, и у «Страны» там три места, где
     * содержимое уезжает вверх:
     *
     * <ul>
     * <li>единственный потомок поля объявлен во всю высоту, но FrameLayout
     *     растягивает потомков до своей высоты только когда их больше одного,
     *     поэтому он остаётся высотой по тексту и прижимается к верху;
     * <li>у стрелки в разметке гравитация ноль, а ноль — это не «не задана»:
     *     LinearLayout подставляет свою CENTER_VERTICAL только для −1, а ноль
     *     проходит в switch мимо всех веток и кладёт стрелку к верху;
     * <li>надписи внутри переключателя названий добавлены без параметров, то
     *     есть во всю высоту, а вертикальной гравитации у них нет — текст
     *     рисуется по верхнему краю растянутой надписи.
     * </ul>
     *
     * <p>Поэтому центрируем не только сам блок, но и каждый его элемент.
     */
    private static void centerFieldContent(View field) {
        if (!(field instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) field;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.gravity = centered(params.gravity);
                child.setLayoutParams(params);
            }
            centerChildren(child);
        }
    }

    private static void centerChildren(View view) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) params).gravity = centered(((LinearLayout.LayoutParams) params).gravity);
                child.setLayoutParams(params);
            } else if (params instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) params).gravity = centered(((FrameLayout.LayoutParams) params).gravity);
                child.setLayoutParams(params);
            }
            if (child instanceof TextView) {
                ((TextView) child).setGravity(centered(((TextView) child).getGravity()));
            }
            centerChildren(child);
        }
    }

    /** Отрицательная гравитация означает «не задана»: её нельзя маскировать. */
    private static int centered(int gravity) {
        return (gravity < 0 ? 0 : gravity & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL;
    }

    private static GradientDrawable surface(int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.isCurrentThemeDark() ? .075f : .035f));
        background.setCornerRadius(AndroidUtilities.dp(radius));
        return background;
    }

    /** Filled authorization field with a stationary label, preserving native focus/error callbacks. */
    public static class Field extends OutlineTextContainerView {
        private boolean rounded;
        private String caption = "";
        private final android.text.TextPaint captionPaint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float error;
        public Field(Context context, boolean rounded) {
            super(context);
            this.rounded = rounded;
            if (rounded) applySurface();
        }
        private void applySurface() {
            GradientDrawable fill = surface(20);
            if (error > 0f) fill.setColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_text_RedBold), .10f));
            setBackground(fill);
            setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(2));
        }
        @Override public void setText(String text) {
            super.setText(text);
            caption = text;
            setContentDescription(text);
        }
        @Override public void animateError(float value) {
            error = value;
            super.animateError(value);
            if (rounded) applySurface();
        }
        @Override public void updateColor() {
            super.updateColor();
            if (rounded) applySurface();
        }
        @Override protected float getCornerRadius() { return rounded ? AndroidUtilities.dp(20) : super.getCornerRadius(); }
        @Override protected void onDraw(Canvas canvas) {
            if (!rounded) { super.onDraw(canvas); return; }
            captionPaint.setTextSize(AndroidUtilities.dp(11));
            captionPaint.setColor(Theme.getColor(error > 0f ? Theme.key_text_RedBold : Theme.key_windowBackgroundWhiteGrayText));
            float inset = AndroidUtilities.dp(18);
            CharSequence label = android.text.TextUtils.ellipsize(caption, captionPaint,
                    Math.max(0, getWidth() - inset * 2), android.text.TextUtils.TruncateAt.END);
            float x = LocaleController.isRTL ? getWidth() - inset - captionPaint.measureText(label.toString()) : inset;
            canvas.drawText(label.toString(), x, AndroidUtilities.dp(15), captionPaint);
        }
    }

    /** Native OTP input/autofill/backspace/error handling, with adaptive cell geometry. */
    public static class CodeFields extends CodeFieldContainer {
        private final boolean rounded;

        public CodeFields(Context context, boolean rounded) {
            super(context);
            this.rounded = rounded;
            if (rounded) setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        @Override
        protected float getCornerRadius() {
            return rounded ? AndroidUtilities.dp(14) : super.getCornerRadius();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (rounded && codeField != null && codeField.length > 0) {
                int count = codeField.length;
                int gap = AndroidUtilities.dp(count > 6 ? 5 : 8);
                int available = MeasureSpec.getSize(widthMeasureSpec);
                int width = Math.min(AndroidUtilities.dp(50), Math.max(1, (available - gap * (count - 1)) / count));
                int height = compact(60);
                for (int i = 0; i < count; i++) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) codeField[i].getLayoutParams();
                    params.width = width;
                    params.height = height;
                    params.leftMargin = 0;
                    params.rightMargin = i + 1 < count ? gap : 0;
                    codeField[i].setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(
                            26 * getResources().getDisplayMetrics().scaledDensity, width * .68f));
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private GradientDrawable cellFill;
        private int cellPalette;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (rounded) {
                int color = Theme.getColor(Theme.key_windowBackgroundWhite);
                if (cellFill == null || cellPalette != color) { cellFill = surface(18); cellPalette = color; }
                GradientDrawable fill = cellFill;
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    fill.setBounds(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                    fill.draw(canvas);
                }
            }
            super.dispatchDraw(canvas);
        }
    }

    /** Measures natural content before native exact-height placement, making short screens scroll. */
    public static int measureContent(SlideView current, int width, int availableHeight) {
        if (!styled(current)) return availableHeight;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) current.getLayoutParams();
        int side = Math.max(AndroidUtilities.dp(24), (width - AndroidUtilities.dp(480)) / 2);
        params.leftMargin = params.rightMargin = side;
        current.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width - side * 2), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return Math.max(availableHeight, current.getMeasuredHeight() + params.topMargin - AndroidUtilities.dp(16));
    }

    /** The actual native action stays in its root and keeps all listeners and progress state. */
    public static final class Chrome {
        private final FrameLayout root;
        private final ScrollView scroll;
        private final CustomPhoneKeyboardView keyboard;
        private final FragmentFloatingButton button;
        private final View icon;
        private final TextView label;
        private final LinearLayout footer;
        private final NebulaAuthStatus status;
        private View progress;
        private int step = -1;
        private boolean active;
        /**
         * Сколько места занимала клавиатура на прошлом измерении, и видима ли
         * она сейчас по мнению самого экрана входа.
         *
         * <p>По видимости вью судить нельзя: она становится скрытой только
         * когда её отъезд уже закончился. Разметка тогда пересчитывалась одним
         * кадром, и кнопка «Продолжить» прыгала на всю высоту клавиатуры.
         */
        private Boolean keyboardVisible;
        private int lastButtonBottom = -1;
        private int lastScrollHeight = -1;
        private ValueAnimator settle;
        private final ViewOutlineProvider originalOutline;

        public Chrome(FrameLayout root, ScrollView scroll, LinearLayout originalKeyboardParent,
                      CustomPhoneKeyboardView keyboard, FragmentFloatingButton button, View icon) {
            this.root = root;
            this.scroll = scroll;
            this.keyboard = keyboard;
            this.button = button;
            this.icon = icon;
            originalOutline = button.getOutlineProvider();

            // Keep the native keyboard anchored while the form above it can scroll.
            originalKeyboardParent.removeView(keyboard);
            root.addView(keyboard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP), Gravity.BOTTOM));
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setClipToPadding(false);

            label = new TextView(root.getContext());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            label.setTypeface(AndroidUtilities.bold());
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setPadding(AndroidUtilities.dp(54), AndroidUtilities.dp(10), AndroidUtilities.dp(54), AndroidUtilities.dp(10));
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            button.addAdditionalView(label);

            footer = new LinearLayout(root.getContext());
            footer.setOrientation(LinearLayout.VERTICAL);
            footer.setGravity(Gravity.CENTER_HORIZONTAL);
            status = new NebulaAuthStatus(root.getContext(), true);
            footer.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(footer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }

        /** Called after native keyboard/bulletin margins have been calculated. */
        /** Экран входа сообщает о своём решении раньше, чем клавиатура уедет. */
        public void setKeyboardVisible(boolean visible) {
            if (keyboardVisible != null && keyboardVisible == visible) {
                return;
            }
            keyboardVisible = visible;
            root.requestLayout();
        }

        public void measure(int width, int height, SlideView current) {
            boolean shown = keyboardVisible != null
                    ? keyboardVisible : keyboard.getVisibility() != View.GONE;
            int keyboardHeight = shown ? keyboard.getLayoutParams().height : 0;
            FrameLayout.LayoutParams scrollParams = (FrameLayout.LayoutParams) scroll.getLayoutParams();
            scrollParams.height = Math.max(1, height - keyboardHeight);
            scrollParams.gravity = Gravity.TOP;
            boolean nextActive = styled(current);
            boolean changed = nextActive != active;
            active = nextActive;
            FrameLayout.LayoutParams buttonParams = (FrameLayout.LayoutParams) button.getLayoutParams();
            FrameLayout.LayoutParams iconParams = (FrameLayout.LayoutParams) icon.getLayoutParams();
            if (active) {
                int nextStep = (Integer) current.getTag(R.id.nebula_auth_step);
                if (step != nextStep) {
                    step = nextStep;
                    if (progress != null) footer.removeView(progress);
                    progress = NebulaProgress.build(root.getContext(), 4, Math.min(3, step));
                    footer.addView(progress);
                }
                String text = LocaleController.getString(step == 4
                        ? R.string.NebulaAuthSignIn : R.string.Continue);
                label.setText(text);
                button.setContentDescription(text);
                footer.setVisibility(View.VISIBLE);
                int contentWidth = Math.min(AndroidUtilities.dp(480), Math.max(1, width - AndroidUtilities.dp(48)));
                footer.measure(View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                FrameLayout.LayoutParams footerParams = (FrameLayout.LayoutParams) footer.getLayoutParams();
                footerParams.width = contentWidth;
                int nativeButtonBottom = buttonParams.bottomMargin;
                footerParams.bottomMargin = nativeButtonBottom;
                buttonParams.width = contentWidth;
                buttonParams.height = Math.max(AndroidUtilities.dp(56), (int) (20 * root.getResources().getDisplayMetrics().scaledDensity) + AndroidUtilities.dp(28));
                buttonParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                buttonParams.leftMargin = buttonParams.rightMargin = 0;
                buttonParams.bottomMargin = nativeButtonBottom + footer.getMeasuredHeight() + AndroidUtilities.dp(10);
                iconParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                iconParams.setMarginEnd(AndroidUtilities.dp(8));
                int reserved = footer.getMeasuredHeight() + buttonParams.height + compact(38);
                if (current.getPaddingBottom() != reserved) {
                    current.setPadding(0, current.getPaddingTop(), 0, reserved);
                }
            } else {
                footer.setVisibility(View.GONE);
                buttonParams.width = buttonParams.height = AndroidUtilities.dp(56);
                buttonParams.gravity = (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM;
                buttonParams.leftMargin = buttonParams.rightMargin = AndroidUtilities.dp(20);
                iconParams.gravity = Gravity.CENTER;
                iconParams.setMarginEnd(0);
                button.setContentDescription(LocaleController.getString(R.string.Done));
            }
            label.setVisibility(active && !button.getProgressVisible() ? View.VISIBLE : View.GONE);
            if (changed) updateColors();
            // Сдвиг считаем по самой разметке, а не по высоте клавиатуры: кнопка
            // стоит от нижнего края и уезжает совсем не на её высоту. Пока
            // сдвигом считалась высота клавиатуры, кнопка на время движения
            // улетала втрое дальше нужного — вместе с областью нажатия.
            final boolean centred = current.getMeasuredHeight() > 0
                    && current.getMeasuredHeight() < scrollParams.height;
            if (active && lastButtonBottom >= 0
                    && (lastButtonBottom != buttonParams.bottomMargin
                        || lastScrollHeight != scrollParams.height)) {
                settle(buttonParams.bottomMargin - lastButtonBottom,
                        centred ? (lastScrollHeight - scrollParams.height) / 2f : 0f);
            }
            lastButtonBottom = active ? buttonParams.bottomMargin : -1;
            lastScrollHeight = scrollParams.height;
        }

        /**
         * Сглаживает перестановку при появлении и уходе клавиатуры.
         *
         * <p>Разметка меняется одним кадром, поэтому кнопка и полоски прыгали
         * на всю высоту клавиатуры разом. Ставим их туда, где они были, и
         * возвращаем на место коротким движением. Содержимое над ними стоит
         * по центру, поэтому едет вдвое меньше — если оно вообще помещается
         * целиком; иначе оно прижато к верху и никуда не двигается.
         */
        private void settle(int delta, float contentShift) {
            if (delta == 0 && contentShift == 0f) {
                return;
            }
            if (settle != null) {
                settle.cancel();
            }
            // Отступ снизу уменьшился — значит кнопка уехала вниз ровно на эту
            // разницу. Ставим её обратно и отпускаем.
            final float from = delta;
            final float content = contentShift;
            settle = ValueAnimator.ofFloat(1f, 0f);
            settle.setDuration(180);
            settle.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            settle.addUpdateListener(animation -> {
                final float value = (float) animation.getAnimatedValue();
                button.setTranslationY(from * value);
                footer.setTranslationY(from * value);
                scroll.setTranslationY(content * value);
            });
            settle.start();
        }

        public void setProgressVisible(boolean visible) {
            label.setVisibility(active && !visible ? View.VISIBLE : View.GONE);
        }

        public void updateColors() {
            if (active) {
                button.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
                    }
                });
                button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(32),
                        Theme.getColor(Theme.key_featuredStickers_addButton),
                        Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            } else {
                button.setOutlineProvider(originalOutline);
                button.updateColors();
            }
            label.setTextColor(Theme.getColor(Theme.key_chats_actionIcon));
            status.refreshColors();
        }
    }
}
