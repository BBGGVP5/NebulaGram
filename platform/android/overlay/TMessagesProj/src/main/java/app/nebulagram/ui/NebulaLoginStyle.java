package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
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
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.FragmentFloatingButton;
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

    public static boolean styled(View slide) {
        return slide != null && slide.getTag(R.id.nebula_auth_step) instanceof Integer;
    }

    public static void phone(SlideView slide, TextView title, TextView subtitle,
                             View country, View phone) {
        decorate(slide, title, subtitle, 2, R.string.NebulaAuthPhoneEyebrow);
        title.setText(LocaleController.getString(R.string.NebulaAuthPhoneTitle));
        fieldMargins(country, 26, 6);
        fieldMargins(phone, 8, 12);
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
        digitParams.topMargin = AndroidUtilities.dp(28);
        digitParams.bottomMargin = AndroidUtilities.dp(8);
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
        fieldMargins(field, 28, 8);
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
        slide.setGravity(Gravity.CENTER_VERTICAL);
        slide.setPadding(0, AndroidUtilities.dp(42), 0, AndroidUtilities.dp(24));
        slide.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        ImageView badge = new ImageView(slide.getContext());
        badge.setImageDrawable(new NebulaMark(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4),
                Theme.getColor(Theme.key_windowBackgroundWhiteValueText)).still());
        badge.setBackground(surface(28));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(88), AndroidUtilities.dp(88));
        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
        badgeParams.bottomMargin = AndroidUtilities.dp(22);
        slide.addView(badge, 0, badgeParams);
        TextView stepLabel = new TextView(slide.getContext());
        stepLabel.setText(LocaleController.getString(eyebrow));
        stepLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        stepLabel.setTypeface(AndroidUtilities.bold());
        stepLabel.setLetterSpacing(0.10f);
        stepLabel.setGravity(Gravity.CENTER);
        stepLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepParams.bottomMargin = AndroidUtilities.dp(10);
        slide.addView(stepLabel, 1, stepParams);

        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        // Длинный заголовок обрезался: тридцать пунктов в одну строку не
        // помещаются на узком экране. Даём вторую строку вместо многоточия.
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setEllipsize(null);
        title.setLineSpacing(AndroidUtilities.dp(2), 1f);
        margins(title, 0, 0, 0, 0);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(AndroidUtilities.dp(3), 1f);
        margins(subtitle, 4, 12, 4, 0);
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

    private static GradientDrawable surface(int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.isCurrentThemeDark() ? .075f : .035f));
        background.setCornerRadius(AndroidUtilities.dp(radius));
        return background;
    }

    /** Same outline, hint, focus and error animator, with a rounded filled surface. */
    public static class Field extends OutlineTextContainerView {
        /**
         * Скругление поля — ровно там, где Telegram начинает вырез под
         * заголовок (PADDING_LEFT - PADDING_TEXT = 10dp). При большем радиусе
         * вырез попадал внутрь дуги: заголовок наезжал на закругление, а концы
         * линий висели в воздухе. Теперь линия начинается точно в точке
         * касания, и подгонять на глаз нечего.
         */
        private static final int RADIUS = 10;

        private boolean rounded;

        public Field(Context context, boolean rounded) {
            super(context);
            this.rounded = rounded;
            if (rounded) applySurface();
        }

        /**
         * Ставит фон, не потеряв отступ сверху.
         *
         * <p>Конструктор Telegram задаёт полю отступ в шесть точек — это место,
         * куда рисуется заголовок поверх рамки. Установка фона сбрасывает
         * отступы вью на отступы самого фона, а у заливки их нет: место
         * исчезало, и у «Страны» с «Номером телефона» срезало верх букв.
         */
        private void applySurface() {
            setBackground(surface(RADIUS));
            setPadding(0, AndroidUtilities.dp(6), 0, 0);
        }

        @Override
        protected float getCornerRadius() {
            return rounded ? AndroidUtilities.dp(RADIUS) : super.getCornerRadius();
        }

        @Override
        public void updateColor() {
            super.updateColor();
            if (rounded) applySurface();
        }

        // Обрезка по форме поля здесь была и оказалась вредной: заголовок
        // сидит на линии рамки, и его верхняя половина уходила под обрезку —
        // «Страна» и «Номер телефона» читались срезанными. Вылет линий за
        // дугу, ради которого она вводилась, решён радиусом: он совпадает с
        // началом выреза, и обрезать больше нечего.
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
                int height = AndroidUtilities.dp(60);
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

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (rounded) {
                GradientDrawable fill = surface(14);
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
        public void measure(int width, int height, SlideView current) {
            int keyboardHeight = keyboard.getVisibility() == View.GONE ? 0 : keyboard.getLayoutParams().height;
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
                int reserved = footer.getMeasuredHeight() + buttonParams.height + AndroidUtilities.dp(38);
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
