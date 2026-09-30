package app.nebulagram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
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
        private boolean choices;
        private boolean selectionIndicatorVisible = true;
        private View customView;
        private final Theme.ResourcesProvider resourcesProvider;
        private final boolean useTelegramTheme;
        private DialogInterface.OnClickListener itemClick, positiveClick, negativeClick, neutralClick;

        public Builder(Context context) { this.context = context; resourcesProvider = null; useTelegramTheme = false; }
        public Builder(Context context, Theme.ResourcesProvider provider) {
            this.context = context; resourcesProvider = provider; useTelegramTheme = true;
        }
        public Builder setTitle(CharSequence value) { title = value; return this; }
        public Builder setMessage(CharSequence value) { message = value; return this; }
        public Builder setView(View value) { customView = value; return this; }
        public Builder setItems(CharSequence[] values, DialogInterface.OnClickListener listener) {
            items = values; itemClick = listener; return this;
        }
        public Builder setSelectedIndex(int index) { choices = true; selected = index; return this; }
        public Builder setSelectionIndicatorVisible(boolean visible) { selectionIndicatorVisible = visible; return this; }
        public Builder setDescriptions(CharSequence[] values) { descriptions = values; return this; }
        public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener listener) {
            positive = label; positiveClick = listener; return this;
        }
        public Builder setNegativeButton(CharSequence label, DialogInterface.OnClickListener listener) {
            negative = label; negativeClick = listener; return this;
        }

        public Builder setNeutralButton(CharSequence label, DialogInterface.OnClickListener listener) {
            neutral = label; neutralClick = listener; return this;
        }

        public BottomSheet create() {
            NebulaTheme theme = NebulaTheme.of(context);
            final int surface = (useTelegramTheme ? Theme.getColor(Theme.key_dialogBackground, resourcesProvider) : theme.modalSurface()) | 0xFF000000;
            final int container = (useTelegramTheme ? Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider) : theme.surfaceContainer()) | 0xFF000000;
            final int onSurface = useTelegramTheme ? Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider) : theme.onSurface();
            final int muted = useTelegramTheme ? Theme.getColor(Theme.key_dialogTextGray, resourcesProvider) : theme.onSurfaceVariant();
            final int accent = useTelegramTheme ? Theme.getColor(Theme.key_dialogTextBlue, resourcesProvider) : theme.primary();
            final int outline = useTelegramTheme ? NebulaTheme.stateLayer(onSurface, .24f) : theme.outline();
            BottomSheet sheet = new BottomSheet.Builder(context, customView != null, surface).create();
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            sheet.setApplyTopPadding(false);
            sheet.setApplyBottomPadding(false);
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(shape(surface, 28));
            root.setPadding(dp(16), dp(10), dp(16), dp(12));
            root.setClipToOutline(true);
            View handle = new View(context);
            handle.setBackground(shape(NebulaTheme.stateLayer(muted, .4f), 2));
            handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(32), dp(4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.bottomMargin = dp(16);
            root.addView(handle, handleParams);

            ScrollView scroll = new ScrollView(context) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int cap = Math.round(getResources().getDisplayMetrics().heightPixels * .65f);
                    int footer = 0;
                    // Reserve actual wrapped button heights, including large accessibility fonts.
                    for (int i = 0; i < root.getChildCount(); i++) {
                        View child = root.getChildAt(i);
                        if (!(child instanceof NebulaButton)) continue;
                        child.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
                        footer += child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
                    }
                    int size = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                            ? cap : Math.min(cap, Math.max(0, MeasureSpec.getSize(heightSpec) - footer));
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST));
                }
            };
            scroll.setClipToPadding(false);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(content);
            if (title != null) {
                TextView heading = text(title, 22, onSurface);
                heading.setTypeface(AndroidUtilities.bold());
                heading.setPadding(dp(8), 0, dp(8), dp(16));
                if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
                content.addView(heading);
            }
            if (message != null) {
                TextView description = text(message, 15, muted);
                description.setPadding(dp(8), 0, dp(8), dp(16));
                content.addView(description);
            }
            if (items != null) for (int i = 0; i < items.length; i++) {
                if (items[i] == null) continue;
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
                row.setMinimumHeight(dp(60));
                row.setSelected(checked);
                row.setFocusable(true);
                row.setBackground(new RippleDrawable(ColorStateList.valueOf(NebulaTheme.stateLayer(accent, .12f)),
                        shape(checked ? androidx.core.graphics.ColorUtils.compositeColors(
                                NebulaTheme.stateLayer(accent, .18f), container) : container, 16),
                        shape(0xffffffff, 16)));
                row.setPaddingRelative(dp(16), dp(14), dp(16), dp(14));
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
                if (choices && selectionIndicatorVisible) {
                    View indicator = new View(context) {
                        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        @Override protected void onDraw(Canvas canvas) {
                            paint.setColor(checked ? accent : muted);
                            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
                            float x = getWidth() / 2f, y = getHeight() / 2f;
                            canvas.drawCircle(x, y, dp(9), paint);
                            if (checked) { paint.setStyle(Paint.Style.FILL); canvas.drawCircle(x, y, dp(5), paint); }
                        }
                    };
                    indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(24), dp(24));
                    indicatorParams.setMarginStart(dp(16)); row.addView(indicator, indicatorParams);
                }
                row.setOnClickListener(v -> {
                    sheet.dismiss();
                    if (itemClick != null) itemClick.onClick(sheet, index);
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = dp(6); content.addView(row, rowParams);
            }
            if (customView != null) {
                if (customView instanceof android.widget.EditText) {
                    android.widget.EditText editor = (android.widget.EditText) customView;
                    GradientDrawable background = shape(container, 16);
                    background.setStroke(dp(1), outline);
                    editor.setBackground(background);
                    editor.setPadding(dp(16), dp(14), dp(16), dp(14));
                    editor.setMinHeight(dp(56));
                    editor.setTextColor(onSurface); editor.setHintTextColor(muted);
                }
                content.addView(customView, new LinearLayout.LayoutParams(-1, -2));
            }
            root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
            if (positive != null) addButton(root, sheet, positive, true, positiveClick, DialogInterface.BUTTON_POSITIVE, accent);
            if (neutral != null) addButton(root, sheet, neutral, false, neutralClick, DialogInterface.BUTTON_NEUTRAL, accent);
            if (negative != null || positive == null) addButton(root, sheet, negative != null ? negative : NebulaText.text("Закрыть", "Close"),
                    false, negativeClick, DialogInterface.BUTTON_NEGATIVE, accent);
            sheet.setCustomView(root);
            return sheet;
        }

        public BottomSheet show() { BottomSheet sheet = create(); sheet.show(); return sheet; }

        private TextView text(CharSequence value, int size, int color) {
            TextView view = new TextView(context);
            view.setText(value); view.setTextSize(size); view.setTextColor(color);
            view.setGravity(Gravity.START); view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            view.setLineSpacing(dp(2), 1f); return view;
        }
        private void addButton(LinearLayout root, BottomSheet sheet, CharSequence label, boolean primary,
                               DialogInterface.OnClickListener listener, int which, int accent) {
            NebulaButton button = new NebulaButton(context, primary ? NebulaButton.STYLE_FILLED : NebulaButton.STYLE_TEXT);
            if (useTelegramTheme) {
                button.setTextColor(primary ? (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > .5 ? android.graphics.Color.BLACK : android.graphics.Color.WHITE) : accent);
                button.setBackground(new RippleDrawable(ColorStateList.valueOf(NebulaTheme.stateLayer(accent, .16f)),
                        primary ? shape(accent, 16) : shape(android.graphics.Color.TRANSPARENT, 16), shape(android.graphics.Color.WHITE, 16)));
            }
            button.setText(label);
            button.setSingleLine(false); button.setEllipsize(null);
            button.setPadding(dp(16), dp(12), dp(16), dp(12));
            button.setOnClickListener(v -> { sheet.dismiss(); if (listener != null) listener.onClick(sheet, which); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8); root.addView(button, params);
        }
        private GradientDrawable shape(int color, int radius) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
        }
    }
}
