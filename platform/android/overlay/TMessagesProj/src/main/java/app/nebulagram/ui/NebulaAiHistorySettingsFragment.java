package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaAiHistorySettingsFragment extends BaseFragment {
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("История сообщений", "Message history"));
        LinearLayout content = NebulaFormUi.column(c); NebulaCard card = new NebulaCard(c);
        card.add(new NebulaRow(c).title(text("История сообщений", "Message history")).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiHistory.enabled())
                .withClick(v -> NebulaAiHistory.setEnabled(((NebulaRow) v).toggleChecked())));
        card.add(new NebulaRow(c).title(text("Контекст диалога", "Conversation context")).subtitle(text("Учитывать предыдущие сообщения текущего чата с ИИ", "Include previous messages from this AI conversation"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiOptions.prefs().getBoolean("use_context", true))
                .withClick(v -> NebulaAiOptions.prefs().edit().putBoolean("use_context", ((NebulaRow) v).toggleChecked()).apply()));
        card.add(new NebulaRow(c).title(text("Просмотреть историю", "View history")).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new NebulaAiHistoryFragment())));
        content.addView(card); content.addView(NebulaFormUi.note(c, text("История сохраняется только на этом устройстве. Контекст помогает ИИ учитывать предыдущие вопросы и ответы.", "History is stored only on this device. Context helps AI understand previous questions and answers.")));
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
    }
}
