package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.List;

public final class NebulaMessageDetailsFragment extends BaseFragment {
    private final List<NebulaMessageDetails.Field> fields;
    public NebulaMessageDetailsFragment(MessageObject message) {
        currentAccount = message.currentAccount;
        fields = NebulaMessageDetails.snapshot(message, org.telegram.messenger.LocaleController.getInstance().getCurrentLocale().getLanguage().equals("ru"));
    }
    @Override public View createView(Context context) {
        NebulaFormUi.bar(this, actionBar, context, NebulaText.text("Подробности сообщения", "Message details"));
        LinearLayout column = NebulaFormUi.column(context);
        column.addView(new NebulaSettingsHero(context, "📄", NebulaText.text("Подробности сообщения", "Message details"),
                NebulaText.text("Даты и свойства выбранного сообщения. Нажмите строку, чтобы скопировать значение.", "Dates and properties of the selected message. Tap a row to copy its value.")));
        NebulaCard card = new NebulaCard(context);
        for (NebulaMessageDetails.Field field : fields) {
            card.add(new NebulaRow(context).icon(R.drawable.msg_info).title(field.title).subtitle(field.value, false)
                    .withClick(v -> AndroidUtilities.addToClipboard(field.value)));
        }
        column.addView(card);
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, NebulaFormUi.scroll(context, column));
    }
}
