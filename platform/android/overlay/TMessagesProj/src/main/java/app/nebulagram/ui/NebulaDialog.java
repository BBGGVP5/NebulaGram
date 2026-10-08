package app.nebulagram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

/** Shared presentation for Nebula choices, confirmations and small editors. */
public final class NebulaDialog {
    private NebulaDialog() { }
    private static int dp(float value) { return AndroidUtilities.dp(value); }

    public static final class Builder {
        private final Context context;
        private CharSequence title, message, positive, negative, neutral;
        private CharSequence[] items, descriptions;
        private int selected = -1;
        private int sectionStart = -1;
        private CharSequence sectionTitle;
        private boolean choices;
        private View customView;
        private final Theme.ResourcesProvider resourcesProvider;
        private DialogInterface.OnClickListener itemClick, positiveClick, negativeClick, neutralClick;

        public Builder(Context context) { this.context = context; resourcesProvider = null; }
        public Builder(Context context, Theme.ResourcesProvider provider) {
            this.context = context; resourcesProvider = provider;
        }
        public Builder setTitle(CharSequence value) { title = value; return this; }
        public Builder setMessage(CharSequence value) { message = value; return this; }
        public Builder setView(View value) { customView = value; return this; }
        public Builder setItems(CharSequence[] values, DialogInterface.OnClickListener listener) {
            items = values; itemClick = listener; return this;
        }
        public Builder setSelectedIndex(int index) { choices = true; selected = index; return this; }
        public Builder setDescriptions(CharSequence[] values) { descriptions = values; return this; }
        public Builder setSection(int start, CharSequence title) {
            sectionStart = start; sectionTitle = title; return this;
        }
        public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener listener) {
            positive = label; positiveClick = listener; return this;
        }
        public Builder setNegativeButton(CharSequence label, DialogInterface.OnClickListener listener) {
            negative = label; negativeClick = listener; return this;
        }

        public Builder setNeutralButton(CharSequence label, DialogInterface.OnClickListener listener) {
            neutral = label; neutralClick = listener; return this;
        }

        public AlertDialog create() {
            final int container = Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider);
            final int onSurface = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
            final int muted = Theme.getColor(Theme.key_dialogTextGray, resourcesProvider);
            final int accent = Theme.getColor(Theme.key_dialogTextBlue, resourcesProvider);
            final int outline = stateLayer(onSurface, .24f);
            // Native AlertDialog owns the title, scroll viewport, footer and keyboard insets.
            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider)
                    .setDimEnabled(true).setDimAlpha(.6f).twoRowsButtonsWhenNeeded();
            if (title != null) builder.setTitle(title);
            if (message != null) builder.setMessage(message);
            if (negative != null || negativeClick != null) {
                builder.setNegativeButton(negative != null ? negative : NebulaText.text("Отмена", "Cancel"),
                        (dialog, which) -> { if (negativeClick != null) negativeClick.onClick(dialog, which); });
            }
            if (neutral != null) builder.setNeutralButton(neutral,
                    (dialog, which) -> { if (neutralClick != null) neutralClick.onClick(dialog, which); });
            if (positive != null) builder.setPositiveButton(positive,
                    (dialog, which) -> { if (positiveClick != null) positiveClick.onClick(dialog, which); });
            AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(true);
            dialog.getWindow().setGravity(Gravity.CENTER);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(24), 0, dp(24), dp(8));
            LinearLayout choicesGroup = new LinearLayout(context);
            choicesGroup.setOrientation(LinearLayout.VERTICAL);
            choicesGroup.setBackground(shape(container, 16));
            choicesGroup.setClipToOutline(true);
            choicesGroup.setPadding(dp(4), dp(4), dp(4), dp(4));
            if (items != null) content.addView(choicesGroup, new LinearLayout.LayoutParams(-1, -2));
            if (items != null) for (int i = 0; i < items.length; i++) {
                if (items[i] == null) continue;
                if (i == sectionStart && i > 0) {
                    TextView heading = text(sectionTitle, 13, muted);
                    heading.setPadding(dp(14), dp(16), dp(14), dp(8));
                    content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
                    choicesGroup = new LinearLayout(context);
                    choicesGroup.setOrientation(LinearLayout.VERTICAL);
                    choicesGroup.setBackground(shape(container, 16));
                    choicesGroup.setClipToOutline(true);
                    choicesGroup.setPadding(dp(4), dp(4), dp(4), dp(4));
                    content.addView(choicesGroup, new LinearLayout.LayoutParams(-1, -2));
                }
                final int index = i;
                final boolean checked = choices && i == selected;
                LinearLayout row = new LinearLayout(context) {
                    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(info);
                        if (choices) {
                            info.setClassName("android.widget.RadioButton");
                            info.setCheckable(true); info.setChecked(checked);
                        }
                    }
                };
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(52));
                row.setSelected(checked);
                row.setFocusable(true);
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(stateLayer(accent, .12f)),
                        shape(checked ? androidx.core.graphics.ColorUtils.compositeColors(
                                stateLayer(accent, .16f), container) : container, 12),
                        shape(0xffffffff, 12)));
                row.setPaddingRelative(dp(14), dp(10), dp(14), dp(10));
                LinearLayout labels = new LinearLayout(context);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                labels.addView(text(items[i], 16, checked ? accent : onSurface));
                row.setContentDescription(items[i]);
                if (descriptions != null && i < descriptions.length && descriptions[i] != null) {
                    TextView detail = text(descriptions[i], 13, muted);
                    detail.setPadding(0, dp(4), 0, 0); labels.addView(detail);
                    row.setContentDescription(items[i] + ". " + descriptions[i]);
                }
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (itemClick != null) itemClick.onClick(dialog, index);
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                choicesGroup.addView(row, rowParams);
            }
            if (customView != null) {
                if (customView instanceof android.widget.EditText) {
                    android.widget.EditText editor = (android.widget.EditText) customView;
                    GradientDrawable background = shape(container, 16);
                    background.setStroke(dp(1), outline);
                    editor.setBackground(background);
                    editor.setPadding(dp(14), dp(10), dp(14), dp(10));
                    editor.setMinHeight(dp(48));
                    editor.setTextColor(onSurface); editor.setHintTextColor(muted);
                }
                content.addView(customView, new LinearLayout.LayoutParams(-1, -2));
            }
            if (items != null || customView != null) {
                builder.setView(content);
            }
            return dialog;
        }

        public AlertDialog show() { AlertDialog dialog = create(); dialog.show(); return dialog; }

        private TextView text(CharSequence value, int size, int color) {
            TextView view = new TextView(context);
            view.setText(value); view.setTextSize(size); view.setTextColor(color);
            view.setGravity(Gravity.START); view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            view.setLineSpacing(dp(2), 1f); return view;
        }
        private static int stateLayer(int color, float opacity) {
            return androidx.core.graphics.ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * opacity));
        }
        private GradientDrawable shape(int color, int radius) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
        }
    }
}
