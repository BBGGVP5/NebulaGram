package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaAiRolesFragment extends BaseFragment {
    private LinearLayout content;
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Роли", "Roles")); content = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
    }
    @Override public void onResume() { super.onResume(); if (content != null) rebuild(); }
    private void rebuild() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(new NebulaSettingsHero(c, "🎭", text("Роли", "Roles"), text("Создавайте инструкции для ваших задач.", "Create instructions for your tasks.")));
        NebulaCard presets = new NebulaCard(c), custom = new NebulaCard(c);
        for (NebulaAiRoles.Role role : NebulaAiRoles.list()) {
            NebulaRow row = new NebulaRow(c).title(role.name).animatedEmoji(currentAccount, role.emoji)
                    .subtitle(role.prompt.length() > 110 ? role.prompt.substring(0, 110) + "…" : role.prompt, false)
                    .radio(role.id.equals(NebulaAiRoles.selected())).withClick(v -> { NebulaAiRoles.select(role.id); rebuild(); });
            if (!role.preset) row.setOnLongClickListener(v -> { showDialog(new NebulaDialog.Builder(c).setTitle(role.name).setItems(new CharSequence[]{text("Изменить", "Edit"), text("Удалить", "Delete")}, (d, which) -> {
                if (which == 0) presentFragment(new Editor(role));
                else { try { NebulaAiRoles.delete(role.id); rebuild(); } catch (Exception ignored) { } }
            }).create()); return true; });
            (role.preset ? presets : custom).add(row);
        }
        NebulaFormUi.group(content, text("Предложения", "Suggestions"), presets);
        custom.add(new NebulaRow(c).icon(R.drawable.msg_add).title(text("Создать роль", "Create role")).withClick(v -> presentFragment(new Editor(null))));
        NebulaFormUi.group(content, text("Мои роли", "My roles"), custom);
        content.addView(NebulaFormUi.note(c, text("Роль применяется к новому запросу в чате с ИИ. Переводчик использует отдельные инструкции.", "The role applies to new requests in the AI chat. Translation uses separate instructions.")));
    }
    public static final class Editor extends BaseFragment {
        private final NebulaAiRoles.Role role;
        public Editor(NebulaAiRoles.Role role) { this.role = role; }
        @Override public View createView(Context c) {
            NebulaFormUi.bar(this, actionBar, c, text("Роль", "Role")); LinearLayout content = NebulaFormUi.column(c); NebulaCard card = new NebulaCard(c);
            EditText name = NebulaFormUi.field(c, text("Название", "Name"), 1, 64), emoji = NebulaFormUi.field(c, text("Эмодзи", "Emoji"), 1, 64), prompt = NebulaFormUi.field(c, text("Инструкции для ИИ", "AI instructions"), 5, 20000);
            name.setText(role == null ? "" : role.name); emoji.setText(role == null ? "🤖" : role.emoji); prompt.setText(role == null ? "" : role.prompt);
            card.add(name); card.add(emoji); card.add(prompt); content.addView(card);
            content.addView(NebulaFormUi.primary(c, text("Сохранить", "Save"), v -> {
                try { NebulaAiRoles.save(role == null ? null : role.id, name.getText().toString(), prompt.getText().toString(), emoji.getText().toString()); finishFragment(); }
                catch (Exception e) { Toast.makeText(c, text("Введите название и инструкции.", "Enter a name and instructions."), Toast.LENGTH_LONG).show(); }
            }));
            return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
        }
    }
}
