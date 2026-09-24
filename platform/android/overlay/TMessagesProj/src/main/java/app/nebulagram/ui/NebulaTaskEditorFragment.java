package app.nebulagram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import java.text.DateFormat;
import java.util.*;

/** Scrollable task draft with one reminder control. */
public final class NebulaTaskEditorFragment extends BaseFragment {
    private final JSONObject previous;
    private final String initial;
    private EditText title, description;
    private long remind;
    private NebulaRow reminder, date;
    public NebulaTaskEditorFragment(JSONObject previous, String initial, int account) {
        this.previous = previous; this.initial = initial; currentAccount = account;
        remind = previous == null ? 0 : previous.optLong("remind");
    }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, previous == null ? t("Новая задача", "New task") : t("Изменить задачу", "Edit task"));
        LinearLayout column = NebulaFormUi.column(c);
        ScrollView scroll = NebulaFormUi.scroll(c, column);
        title = NebulaFormUi.field(c, t("Что нужно сделать?", "What needs to be done?"), 1, 200);
        title.setText(previous == null ? initial.split("\n", 2)[0] : previous.optString("title"));
        NebulaFormUi.group(column, t("Название", "Title"), title);
        description = NebulaFormUi.field(c, t("Подробности, ссылки, заметки", "Details, links, notes"), 4, 10000);
        description.setText(previous == null ? initial : previous.optString("description"));
        NebulaFormUi.group(column, t("Описание · необязательно", "Description · optional"), description);
        NebulaCard timing = new NebulaCard(c);
        reminder = new NebulaRow(c).icon(R.drawable.msg_notifications).title(t("Напомнить", "Remind me"))
                .subtitle(t("Уведомление в выбранное время", "Notify me at a chosen time"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(remind > 0).withClick(v -> {
                    if (remind > 0) { remind = 0; updateReminder(); } else pickTime();
                });
        timing.add(reminder);
        date = NebulaFormUi.action(c, R.drawable.msg_calendar, t("Дата и время", "Date and time"), "", v -> pickTime());
        timing.add(date);
        NebulaFormUi.group(column, t("Напоминание", "Reminder"), timing);
        updateReminder();
        column.addView(NebulaFormUi.note(c, t("Задача сохраняется на этом устройстве. Для напоминаний разреши уведомления NebulaGram.", "Tasks are saved on this device. Allow NebulaGram notifications to receive reminders.")));
        column.addView(NebulaFormUi.primary(c, t("Сохранить задачу", "Save task"), v -> save()));
        if (previous != null) {
            NebulaButton delete = new NebulaButton(c, NebulaButton.STYLE_TEXT);
            delete.setText(t("Удалить задачу", "Delete task"));
            delete.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_text_RedRegular));
            delete.setOnClickListener(v -> showDialog(new AlertDialog.Builder(c)
                    .setTitle(t("Удалить задачу?", "Delete task?")).setMessage(previous.optString("title"))
                    .setNegativeButton(t("Отмена", "Cancel"), null).setPositiveButton(t("Удалить", "Delete"), (d, w) -> {
                        try { NebulaTasks.delete(NebulaTasks.user(currentAccount), previous.getString("id")); finishFragment(); }
                        catch (Exception e) { error(); }
                    }).create()));
            column.addView(delete);
        }
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, scroll);
    }
    private void updateReminder() {
        reminder.checked(remind > 0); date.setVisibility(remind > 0 ? View.VISIBLE : View.GONE);
        if (remind > 0) date.subtitle(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(remind)), false);
    }
    private void pickTime() {
        Context c = getContext(); if (c == null) return;
        Calendar cal = Calendar.getInstance(); cal.setTimeInMillis(remind > System.currentTimeMillis() ? remind : System.currentTimeMillis() + 3600000);
        new DatePickerDialog(c, (d, y, month, day) -> {
            cal.set(y, month, day);
            new TimePickerDialog(c, (time, hour, minute) -> {
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                    Toast.makeText(c, t("Выбери время в будущем", "Choose a future time"), Toast.LENGTH_SHORT).show(); return;
                }
                remind = cal.getTimeInMillis(); updateReminder();
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(c)).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void save() {
        if (title.getText().toString().trim().isEmpty()) { title.setError(t("Введите название", "Enter a title")); title.requestFocus(); return; }
        if (remind > 0 && remind <= System.currentTimeMillis()) { pickTime(); return; }
        try {
            JSONObject task = previous == null ? new JSONObject().put("id", UUID.randomUUID().toString()).put("done", false) : new JSONObject(previous.toString());
            task.put("title", title.getText().toString().trim()).put("description", description.getText().toString()).put("remind", remind);
            NebulaTasks.put(NebulaTasks.user(currentAccount), task); AndroidUtilities.hideKeyboard(title); finishFragment();
        } catch (Exception e) { error(); }
    }
    private void error() { Toast.makeText(getContext(), t("Не удалось сохранить задачу", "Could not save the task"), Toast.LENGTH_LONG).show(); }
}
