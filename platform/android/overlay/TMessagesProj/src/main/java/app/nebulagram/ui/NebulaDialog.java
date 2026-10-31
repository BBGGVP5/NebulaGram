package app.nebulagram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
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
            final int onSurface = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
            final int muted = Theme.getColor(Theme.key_dialogTextGray, resourcesProvider);
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
            // Списки выбора идут ячейками Telegram, и отступы по бокам у них
            // свои. Поля ввода остаются на отступах диалога.
            content.setPadding(items != null ? 0 : dp(24), 0,
                    items != null ? 0 : dp(24), dp(8));
            if (items != null) for (int i = 0; i < items.length; i++) {
                if (items[i] == null) continue;
                if (i == sectionStart && i > 0) {
                    TextView heading = text(sectionTitle, 14, muted);
                    heading.setPadding(dp(21), dp(14), dp(21), dp(6));
                    content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
                }
                final int index = i;
                final boolean checked = choices && i == selected;
                final CharSequence detail = descriptions != null && i < descriptions.length
                        ? descriptions[i] : null;
                final View row;
                if (choices) {
                    // Та же ячейка, которой Telegram строит выбор в своих
                    // диалогах: вместе с ней достаются кружок, его цвета по
                    // теме и правильная роль для озвучки.
                    org.telegram.ui.Cells.RadioColorCell cell =
                            new org.telegram.ui.Cells.RadioColorCell(context, resourcesProvider);
                    cell.setPadding(dp(4), 0, dp(4), 0);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider),
                            Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
                    if (detail != null) cell.setTextAndText2AndValue(items[i], detail, checked);
                    else cell.setTextAndValue(items[i], checked);
                    row = cell;
                } else {
                    // Без выбора кружок не нужен: это список действий, а не
                    // переключатель, и пустой кружок обещал бы состояние.
                    TextView label = text(items[i], 16, onSurface);
                    label.setPadding(dp(21), dp(14), dp(21), dp(14));
                    row = label;
                }
                row.setContentDescription(detail != null ? items[i] + ". " + detail : items[i]);
                row.setBackground(Theme.createSelectorDrawable(
                        Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (itemClick != null) itemClick.onClick(dialog, index);
                });
                content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            if (customView != null) {
                if (customView instanceof android.widget.EditText) {
                    android.widget.EditText editor = (android.widget.EditText) customView;
                    // Поле диалога у Telegram — строка с подчёркиванием, а не
                    // залитый прямоугольник: подчёркивание само красится по теме
                    // и подсвечивается при фокусе.
                    editor.setBackground(Theme.createEditTextDrawable(context, true));
                    editor.setPadding(0, dp(6), 0, dp(8));
                    editor.setMinHeight(dp(42));
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
    }
}
