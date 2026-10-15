package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.function.Consumer;

public final class NebulaQuickRepliesFragment extends BaseFragment {
    private final long owner;
    private final String source;
    private final Consumer<String> choose;
    private LinearLayout column;
    public NebulaQuickRepliesFragment(int account, String source, Consumer<String> choose) {
        currentAccount = account; owner = NebulaTasks.user(account); this.source = source; this.choose = choose;
    }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    private boolean active() { return owner > 0 && owner == NebulaTasks.user(currentAccount); }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("Быстрые ответы", "Quick replies"));
        column = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }
    @Override public void onResume() { super.onResume(); if (column != null) rebuild(); }
    private void rebuild() {
        column.removeAllViews(); if (!active()) { finishFragment(); return; }
        Context c = column.getContext();
        column.addView(NebulaFormUi.note(c, t("Личные шаблоны на этом устройстве, отдельно для каждого аккаунта. Нажмите, чтобы использовать; удерживайте для изменения. Текст вставляется без форматирования и не отправляется автоматически.",
            "Personal templates on this device, separate for each account. Tap to use; hold to edit. Text is inserted without formatting and is never sent automatically.")));
        column.addView(NebulaFormUi.primary(c, t("Новый ответ", "New reply"), v -> edit(null, "")));
        if (source != null && !source.trim().isEmpty() && source.length() <= NebulaQuickReplies.MAX_TEXT) {
            NebulaButton save = new NebulaButton(c, NebulaButton.STYLE_TEXT);
            save.setText(t("Сохранить исходный текст", "Save source text")); save.setOnClickListener(v -> edit(null, source)); column.addView(save);
        }
        NebulaCard card = new NebulaCard(c);
        for (NebulaQuickReplies.Entry entry : NebulaQuickReplies.list(owner)) {
            int count = entry.text.codePointCount(0, entry.text.length());
            String preview = count > 140 ? entry.text.substring(0, entry.text.offsetByCodePoints(0, 140)) + "…" : entry.text;
            NebulaRow row = new NebulaRow(c).title(entry.title).subtitle(preview.replace('\n', ' '), false).trailing(NebulaRow.TRAIL_CHEVRON);
            row.withClick(v -> { if (!active()) return; if (choose == null) edit(entry, entry.text); else { choose.accept(entry.text); finishFragment(); } });
            row.setOnLongClickListener(v -> { edit(entry, entry.text); return true; }); card.add(row);
        }
        column.addView(card);
    }
    private void edit(NebulaQuickReplies.Entry entry, String initial) {
        if (active()) presentFragment(new NebulaQuickReplyEditorFragment(currentAccount, owner, entry, initial));
    }
}
