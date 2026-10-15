package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaQuickReplyEditorFragment extends BaseFragment {
    private final long owner;
    private final NebulaQuickReplies.Entry entry;
    private final String initial;
    public NebulaQuickReplyEditorFragment(int account, long owner, NebulaQuickReplies.Entry entry, String initial) {
        currentAccount = account; this.owner = owner; this.entry = entry; this.initial = initial;
    }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("Быстрый ответ", "Quick reply"));
        LinearLayout column = NebulaFormUi.column(c);
        EditText title = NebulaFormUi.field(c, t("Название", "Title"), 1, NebulaQuickReplies.MAX_TITLE);
        title.setText(entry == null ? "" : entry.title);
        EditText body = NebulaFormUi.field(c, t("Текст ответа", "Reply text"), 6, NebulaQuickReplies.MAX_TEXT);
        body.setText(initial);
        NebulaFormUi.group(column, t("Название", "Title"), title);
        NebulaFormUi.group(column, t("Текст", "Text"), body);
        column.addView(NebulaFormUi.primary(c, t("Сохранить", "Save"), v -> {
            if (owner <= 0 || owner != NebulaTasks.user(currentAccount)) { finishFragment(); return; }
            if (title.getText().toString().trim().isEmpty()) { title.setError(t("Введите название", "Enter a title")); return; }
            if (body.getText().toString().trim().isEmpty()) { body.setError(t("Введите текст", "Enter text")); return; }
            try { NebulaQuickReplies.put(owner, entry == null ? null : entry.id, title.getText().toString(), body.getText().toString()); finishFragment(); }
            catch (Exception error) { Toast.makeText(c, t("Не удалось сохранить. Лимит — 100 ответов.", "Could not save. The limit is 100 replies."), Toast.LENGTH_LONG).show(); }
        }));
        if (entry != null) {
            NebulaButton delete = new NebulaButton(c, NebulaButton.STYLE_TEXT); delete.setText(t("Удалить", "Delete"));
            delete.setOnClickListener(v -> {
                if (owner != NebulaTasks.user(currentAccount)) return;
                try { NebulaQuickReplies.delete(owner, entry.id); finishFragment(); }
                catch (Exception error) { Toast.makeText(c, t("Не удалось удалить", "Could not delete"), Toast.LENGTH_SHORT).show(); }
            }); column.addView(delete);
        }
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }
}
