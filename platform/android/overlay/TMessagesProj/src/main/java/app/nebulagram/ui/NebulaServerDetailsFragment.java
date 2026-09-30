package app.nebulagram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import app.nebulagram.nebulalink.NebulaLink;

/** User-owned billing fields, separate from imported connection credentials. */
public final class NebulaServerDetailsFragment extends BaseFragment {
    private static final String[] KEYS = {"provider", "plan", "amount", "currency", "due_date", "period_days", "notes"};
    private final String serverId;
    private final JSONObject details;
    private final EditText[] fields = new EditText[KEYS.length];
    private TextView save;
    private boolean saving;

    public NebulaServerDetailsFragment(JSONObject server) {
        serverId = server.optString("id");
        JSONObject stored = server.optJSONObject("details");
        details = stored == null ? new JSONObject() : stored;
    }

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(NebulaText.text("Провайдер и оплата", "Provider and payment"));
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(theme.surface());
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = AndroidUtilities.dp(16);
        content.setPadding(inset, inset, inset, inset);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        String[] labels = {NebulaText.text("Провайдер", "Provider"), NebulaText.text("Тариф", "Plan"),
            NebulaText.text("Сумма", "Amount"), NebulaText.text("Валюта (RUB, USD, EUR…)", "Currency (RUB, USD, EUR…)"),
            NebulaText.text("Следующая оплата (ГГГГ-ММ-ДД)", "Next payment (YYYY-MM-DD)"),
            NebulaText.text("Период оплаты, дней", "Payment period, days"), NebulaText.text("Заметки", "Notes")};
        for (int i = 0; i < KEYS.length; i++) {
            content.addView(NebulaCard.header(context, labels[i]));
            EditText field = fields[i] = new EditText(context);
            field.setTextColor(theme.onSurface());
            field.setHintTextColor(theme.onSurfaceVariant());
            field.setTextSize(16);
            field.setSingleLine(i != 6);
            field.setInputType(i == 2 ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    : i == 5 ? InputType.TYPE_CLASS_NUMBER
                    : i == 6 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    : InputType.TYPE_CLASS_TEXT);
            field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(
                    i == 6 ? 2000 : i == 0 || i == 1 ? 160 : i == 3 ? 3 : i == 4 ? 10 : 20)});
            field.setText(i == 5 && details.optInt(KEYS[i]) == 0 ? "" : details.optString(KEYS[i], ""));
            field.setPadding(inset, AndroidUtilities.dp(14), inset, AndroidUtilities.dp(14));
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(theme.surfaceContainer()); background.setCornerRadius(AndroidUtilities.dp(14));
            field.setBackground(background);
            content.addView(field, new LinearLayout.LayoutParams(-1, -2));
            if (i == 4) {
                field.setFocusable(false);
                field.setOnClickListener(v -> chooseDate(context, field));
                field.setOnLongClickListener(v -> { field.setText(""); return true; });
                field.setHint(NebulaText.text("Выбрать дату · удержать для очистки", "Choose date · hold to clear"));
            }
        }
        save = new TextView(context);
        save.setText(NebulaText.text("Сохранить", "Save"));
        save.setTextColor(theme.onPrimary());
        save.setTextSize(16); save.setGravity(android.view.Gravity.CENTER);
        save.setPadding(inset, inset, inset, inset);
        save.setBackground(org.telegram.ui.ActionBar.Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(20), theme.primary(), theme.primaryContainer()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = inset;
        content.addView(save, params);
        save.setOnClickListener(v -> save());
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll, -1);
    }

    private void chooseDate(Context context, EditText field) {
        java.util.Calendar date = java.util.Calendar.getInstance();
        try {
            String[] parts = field.getText().toString().split("-");
            if (parts.length == 3) date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) { }
        new android.app.DatePickerDialog(context, (picker, year, month, day) ->
                field.setText(String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)),
                date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH), date.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void save() {
        if (saving) return;
        JSONObject value = new JSONObject(); JSONObject payload = new JSONObject();
        try {
            for (int i = 0; i < KEYS.length; i++) {
                String text = fields[i].getText().toString().trim();
                value.put(KEYS[i], i == 5 ? text.isEmpty() ? 0 : Integer.parseInt(text)
                        : i == 2 ? text.replace(',', '.') : text);
            }
            payload.put("id", serverId); payload.put("details", value);
        } catch (Exception invalid) { report(); return; }
        saving = true; save.setEnabled(false);
        NebulaLink.call("server.details", payload, result -> {
            saving = false;
            if (save != null) save.setEnabled(true);
            if (getParentActivity() == null) return;
            if (result.ok) finishFragment(); else report();
        });
    }
    private void report() {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), NebulaText.text(
                "Проверьте сумму, валюту и период (0–3660 дней). Не удалось сохранить данные.",
                "Check amount, currency and period (0–3660 days). Could not save details."), Toast.LENGTH_LONG).show();
    }
}
