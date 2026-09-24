package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import java.text.DateFormat;
import java.util.Date;

public final class NebulaTasksFragment extends BaseFragment {
    private String initial;
    private LinearLayout content;
    public NebulaTasksFragment() { this(""); }
    public NebulaTasksFragment(String initial) { this.initial = initial; }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("Список дел", "Tasks"));
        content = NebulaFormUi.column(c);
        ScrollView scroll = NebulaFormUi.scroll(c, content);
        build();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, scroll);
    }
    @Override public void onResume() {
        super.onResume(); if (content != null) build();
        if (!initial.isEmpty()) {
            String draft = initial; initial = "";
            content.post(() -> presentFragment(new NebulaTaskEditorFragment(null, draft, currentAccount)));
        }
    }
    private void build() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(NebulaFormUi.primary(c, t("＋  Новая задача", "＋  New task"), v -> presentFragment(new NebulaTaskEditorFragment(null, "", currentAccount))));
        try {
            JSONArray tasks = NebulaTasks.read(NebulaTasks.user(currentAccount));
            if (tasks.length() == 0) {
                LinearLayout empty = new LinearLayout(c); empty.setOrientation(1); empty.setGravity(Gravity.CENTER);
                empty.setPadding(NebulaFormUi.dp(24), NebulaFormUi.dp(48), NebulaFormUi.dp(24), NebulaFormUi.dp(36));
                ImageView icon = new ImageView(c); icon.setImageResource(R.drawable.msg_calendar);
                icon.setColorFilter(NebulaTheme.of(c).primary()); empty.addView(icon, new LinearLayout.LayoutParams(NebulaFormUi.dp(48), NebulaFormUi.dp(48)));
                TextView heading = NebulaFormUi.note(c, t("Всё начинается с одной задачи", "Start with one task"));
                heading.setTextColor(NebulaTheme.of(c).onSurface()); heading.setTextSize(18); heading.setGravity(Gravity.CENTER); empty.addView(heading);
                TextView note = NebulaFormUi.note(c, t("Сохрани идею, добавь подробности и выбери время напоминания.", "Save an idea, add details and choose a reminder time."));
                note.setGravity(Gravity.CENTER); empty.addView(note); content.addView(empty); return;
            }
            for (boolean done : new boolean[]{false, true}) {
                NebulaCard card = new NebulaCard(c); int count = 0;
                for (int i = 0; i < tasks.length(); i++) {
                    JSONObject task = tasks.getJSONObject(i); if (task.optBoolean("done") != done) continue;
                    count++;
                    LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
                    CheckBox check = new CheckBox(c); check.setChecked(done);
                    check.setButtonTintList(android.content.res.ColorStateList.valueOf(NebulaTheme.of(c).primary()));
                    check.setContentDescription(t("Выполнено: ", "Completed: ") + task.optString("title"));
                    row.addView(check, new LinearLayout.LayoutParams(NebulaFormUi.dp(52), NebulaFormUi.dp(56)));
                    String subtitle = task.optString("description");
                    if (task.optLong("remind") > 0) subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(task.optLong("remind")));
                    NebulaRow details = new NebulaRow(c).title(task.optString("title")).subtitle(subtitle, false)
                            .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new NebulaTaskEditorFragment(task, "", currentAccount)));
                    if (done) details.setAlpha(.65f);
                    row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
                    check.setOnCheckedChangeListener((button, checked) -> {
                        try { task.put("done", checked); NebulaTasks.put(NebulaTasks.user(currentAccount), task); build(); }
                        catch (Exception e) { error(c); }
                    });
                    card.add(row);
                }
                if (count > 0) NebulaFormUi.group(content, (done ? t("Выполнено", "Completed") : t("Предстоит", "To do")) + " · " + count, card);
            }
        } catch (Exception e) { error(c); }
    }
    private void error(Context c) { Toast.makeText(c, t("Не удалось прочитать или сохранить задачи", "Could not read or save tasks"), Toast.LENGTH_LONG).show(); }
}
