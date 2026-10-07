package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;

/**
 * A settings row with a themed outline glyph, an optional subtitle,
 * and a trailing value, chevron or switch.
 *
 * <p>The rows are built from the schema the Go core returns, so this class
 * knows nothing about individual settings — only how a row looks.
 */
public class NebulaRow extends FrameLayout {

    /** Trailing element. */
    public static final int TRAIL_NONE = 0;
    public static final int TRAIL_CHEVRON = 1;
    public static final int TRAIL_SWITCH = 2;

    private final NebulaTheme theme;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView icon;
    private final LinearLayout text;
    private TextView emojiIcon;
    private NebulaAnimatedEmoji animatedEmoji;
    private android.widget.RadioButton radio;
    private TextView badge;
    private boolean valueMode;
    private NebulaSwitch toggle;

    public NebulaRow(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);

        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13),
                AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        setMinimumHeight(AndroidUtilities.dp(58));
        setForeground(new RippleDrawable(
                ColorStateList.valueOf(NebulaTheme.stateLayer(theme.onSurface(), 0.08f)), null, null));

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN);
        icon.setBackground(null);
        icon.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4),
                AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        // Контейнер значка нарисован ещё до того, как значок задан. Пока его
        // нет, показывать пустой скруглённый квадрат нечем оправдать: строка
        // без значка должна начинаться с текста, как в Material 3.
        icon.setVisibility(GONE);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        LayoutParams iconParams = new LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        addView(icon, iconParams);

        text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(theme.onSurface());
        title.setTypeface(android.graphics.Typeface.DEFAULT);
        text.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(theme.onSurfaceVariant());
        subtitle.setLineSpacing(AndroidUtilities.dp(1), 1f);
        subtitle.setVisibility(GONE);
        text.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams textParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textParams.setMarginStart(0);
        textParams.setMarginEnd(AndroidUtilities.dp(36));
        addView(text, textParams);
    }

    /** Отступ текста под значок — только когда значок действительно есть. */
    private void indent(boolean leading) {
        LayoutParams params = (LayoutParams) text.getLayoutParams();
        params.setMarginStart(leading ? AndroidUtilities.dp(48) : 0);
        text.setLayoutParams(params);
    }

    /** Dividers align with the text, including rows without a leading tile. */
    public boolean hasLeadingIcon() {
        return icon.getVisibility() == VISIBLE || (emojiIcon != null && emojiIcon.getVisibility() == VISIBLE)
                || (animatedEmoji != null && animatedEmoji.getVisibility() == VISIBLE);
    }

    private android.animation.ValueAnimator highlightAnimation;
    private android.graphics.drawable.Drawable normalForeground;
    public void highlight() {
        if (highlightAnimation != null) highlightAnimation.cancel();
        if (normalForeground == null) normalForeground = getForeground();
        GradientDrawable glow = new GradientDrawable();
        glow.setColor(theme.primary()); glow.setCornerRadius(NebulaTheme.cornerSmall());
        setForeground(glow);
        highlightAnimation = android.animation.ValueAnimator.ofFloat(0f, 1f);
        highlightAnimation.setDuration(1400);
        highlightAnimation.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            glow.setAlpha(Math.round(55 * Math.min(1f, t / .12f) * Math.min(1f, (1f-t) / .35f)));
        });
        highlightAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { setForeground(normalForeground); }
        });
        highlightAnimation.start();
    }

    public NebulaRow icon(int resource) {
        String glyph=NebulaSettingsEmoji.forIcon(resource);
        if(glyph!=null)return animatedEmoji(org.telegram.messenger.UserConfig.selectedAccount,glyph);
        if (animatedEmoji != null) animatedEmoji.setVisibility(GONE);
        if (emojiIcon != null) {
            emojiIcon.setVisibility(GONE);
        }
        if (resource == 0) {
            icon.setVisibility(GONE);
        } else {
            icon.setVisibility(VISIBLE);
            icon.setImageResource(NebulaSettingsIcons.resource(resource));
            icon.setColorFilter(theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN);
            icon.setBackground(null);
            icon.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4),
                    AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        }
        indent(resource != 0);
        return this;
    }

    /** Preserve the original colors of Nebula's bundled artwork. */
    public NebulaRow artwork(int resource) {
        if(animatedEmoji!=null)animatedEmoji.setVisibility(GONE);
        if(emojiIcon!=null)emojiIcon.setVisibility(GONE);
        icon.setVisibility(resource==0?GONE:VISIBLE);icon.setImageResource(resource);indent(resource!=0);
        icon.clearColorFilter();
        icon.setBackground(null);
        icon.setPadding(0, 0, 0, 0);
        return this;
    }

    /** Country emoji keeps its colours instead of inheriting the icon tint. */
    public NebulaRow emojiIcon(String flag) {
        if (animatedEmoji != null) animatedEmoji.setVisibility(GONE);
        if (flag == null || flag.isEmpty()) {
            return this;
        }
        icon.setVisibility(GONE);
        if (emojiIcon == null) {
            emojiIcon = new TextView(getContext());
            emojiIcon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
            // EmojiSpan inherits TextPaint alpha. Android's default secondary
            // text color is translucent, which made otherwise untinted flags dim.
            emojiIcon.setTextColor(0xFFFFFFFF);
            emojiIcon.setGravity(Gravity.CENTER);
            emojiIcon.setIncludeFontPadding(false);
            emojiIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            emojiIcon.setBackground(null);
            LayoutParams params = new LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            addView(emojiIcon, params);
        }
        emojiIcon.setVisibility(VISIBLE);
        emojiIcon.setText(flag);
        indent(true);
        return this;
    }

    public NebulaRow animatedEmoji(int account, String emoji) {
        icon.setVisibility(GONE);
        if (emojiIcon != null) emojiIcon.setVisibility(GONE);
        if (animatedEmoji == null) {
            animatedEmoji = new NebulaAnimatedEmoji(getContext(), account, emoji, 32);
            LayoutParams params = new LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.START; addView(animatedEmoji, params);
        } else animatedEmoji.setEmoji(emoji);
        animatedEmoji.setVisibility(VISIBLE); indent(true); return this;
    }

    /** The row owns interaction and accessibility; its radio is a visual indicator. */
    public NebulaRow radio(boolean selected) {
        if (radio == null) {
            radio = new android.widget.RadioButton(getContext());
            radio.setClickable(false); radio.setFocusable(false); radio.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            radio.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{theme.primary(),theme.onSurfaceVariant()}));
            LayoutParams params = new LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END; addView(radio, params);
        }
        radio.setChecked(selected); setSelected(selected); return this;
    }

    /** A compact value at the end, with space reserved from its measured width. */
    public NebulaRow badge(CharSequence value, int color) {
        if (badge == null) {
            badge = new TextView(getContext());
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setSingleLine();
            badge.setEllipsize(android.text.TextUtils.TruncateAt.END);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(5),
                    AndroidUtilities.dp(9), AndroidUtilities.dp(5));
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            addView(badge, params);
        }
        badge.setText(value);
        badge.setTextColor(color);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(AndroidUtilities.dp(10));
        background.setColor(NebulaTheme.stateLayer(color, 0.14f));
        badge.setBackground(background);
        return this;
    }

    public NebulaRow selection(boolean selected) {
        setSelected(selected);
        setBackgroundColor(selected ? androidx.core.graphics.ColorUtils.compositeColors(
                NebulaTheme.stateLayer(theme.primary(), 0.18f), theme.surfaceContainer()) : 0);
        title.setTextColor(selected ? theme.primary() : theme.onSurface());
        return this;
    }

    /** Right-aligned selection value, as in the settings prototype. */
    public NebulaRow value(CharSequence value) {
        badge(value == null ? "" : value.toString(), theme.primary());
        valueMode = true;
        badge.setBackground(null);
        badge.setPadding(0, 0, 0, 0);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setGravity(Gravity.END);
        LayoutParams params = (LayoutParams) badge.getLayoutParams();
        params.setMarginEnd(AndroidUtilities.dp(28));
        badge.setLayoutParams(params);
        icon.setVisibility(GONE);
        if (emojiIcon != null) emojiIcon.setVisibility(GONE);
        if (animatedEmoji != null) animatedEmoji.setVisibility(GONE);
        indent(false);
        return this;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (badge != null) {
            badge.setMaxWidth(valueMode ? Math.min(AndroidUtilities.dp(106), MeasureSpec.getSize(widthMeasureSpec) / 3)
                    : Math.max(AndroidUtilities.dp(48), MeasureSpec.getSize(widthMeasureSpec) / 3));
            measureChildWithMargins(badge, widthMeasureSpec, 0, heightMeasureSpec, 0);
            LayoutParams params = (LayoutParams) text.getLayoutParams();
            params.setMarginEnd(badge.getMeasuredWidth() + AndroidUtilities.dp(valueMode ? 40 : 12));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public String linkTitle() { return title.getText().toString(); }

    public NebulaRow title(CharSequence value) {
        title.setText(value);
        return this;
    }

    /** Keep names that may begin with RTL text aligned consistently in a left-to-right list. */
    public NebulaRow leftToRightText() {
        title.setTextDirection(View.TEXT_DIRECTION_LTR);
        subtitle.setTextDirection(View.TEXT_DIRECTION_LTR);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return this;
    }

    /**
     * The line under the title. In this schema a row's current value and its
     * explanation share that line: the value comes first and takes the accent
     * colour, so a screen can be read by scanning the coloured words.
     */
    public NebulaRow subtitle(CharSequence value, boolean isValue) {
        if (value == null || value.length() == 0) {
            subtitle.setVisibility(GONE);
            setMinimumHeight(AndroidUtilities.dp(58));
            return this;
        }
        subtitle.setVisibility(VISIBLE);
        setMinimumHeight(AndroidUtilities.dp(64));
        subtitle.setText(value);
        subtitle.setTextColor(isValue ? theme.primary() : theme.onSurfaceVariant());
        return this;
    }

    public NebulaRow connected(boolean connected) {
        int accent = connected ? theme.success() : theme.primary();
        title.setTextColor(connected ? accent : theme.onSurface());
        subtitle.setTextColor(connected ? accent : theme.onSurfaceVariant());
        icon.setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        return this;
    }

    public NebulaRow destructive() {
        int color = theme.isDark() ? 0xFFFF8585 : 0xFFC43838;
        title.setTextColor(color);
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return this;
    }

    public NebulaRow trailing(int kind) {
        if (kind == TRAIL_CHEVRON) {
            ImageView chevron = new ImageView(getContext());
            chevron.setImageResource(org.telegram.messenger.R.drawable.msg_arrowright);
            chevron.setColorFilter(theme.onSurfaceVariant(), PorterDuff.Mode.SRC_IN);
            chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LayoutParams params = new LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            addView(chevron, params);
        } else if (kind == TRAIL_SWITCH) {
            // The control owns the trailing slot; decorative leading emoji stay out of its text.
            icon.setVisibility(GONE);
            if (emojiIcon != null) emojiIcon.setVisibility(GONE);
            if (animatedEmoji != null) animatedEmoji.setVisibility(GONE);
            indent(false);
            // Переключатель шире стрелки: 52dp против 24dp. С прежним отступом
            // в 36dp текст заезжал под него на треть — отсюда обрезанные
            // подписи во всех наших списках. Считаем от его настоящей ширины.
            LayoutParams textParams = (LayoutParams) text.getLayoutParams();
            textParams.setMarginEnd(AndroidUtilities.dp(64));
            text.setLayoutParams(textParams);
            toggle = new NebulaSwitch(getContext());
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            addView(toggle, params);
        }
        return this;
    }

    /** Цепочечный вариант setOnClickListener, чтобы строка собиралась в одно выражение. */
    public NebulaRow withClick(OnClickListener listener) {
        setOnClickListener(listener);
        return this;
    }

    /** Sets the switch state without firing a listener. */
    public NebulaRow checked(boolean value) {
        if (toggle != null) {
            // Без анимации: строка только что создана, и переключатель должен
            // сразу стоять в нужном положении, а не переезжать на глазах.
            toggle.setChecked(value, false);
        }
        return this;
    }

    public boolean isChecked() {
        return toggle != null && toggle.isChecked();
    }

    /** Flips the switch, for a row whose whole surface is the control. */
    public boolean toggleChecked() {
        if (toggle == null) {
            return false;
        }
        toggle.setChecked(!toggle.isChecked());
        return toggle.isChecked();
    }


    /** A hairline divider drawn between rows inside one card. */
    public static View divider(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        View line = new View(context);
        line.setBackgroundColor(NebulaTheme.stateLayer(theme.outline(), 0.16f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, AndroidUtilities.dp(0.5f)));
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(16);
        line.setLayoutParams(params);
        return line;
    }
}
