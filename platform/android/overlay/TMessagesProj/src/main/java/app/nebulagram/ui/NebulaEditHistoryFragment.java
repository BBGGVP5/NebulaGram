package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Account-owned local history; no edit/restore/send action. */
public final class NebulaEditHistoryFragment extends BaseFragment {
    private final MessageObject selected;
    private LinearLayout column;
    private int generation;
    private boolean destroyed;
    public NebulaEditHistoryFragment(int account, MessageObject selected) { currentAccount = account; this.selected = selected; }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public View createView(Context context) {
        NebulaFormUi.bar(this, actionBar, context, t("История правок", "Edit history"));
        column = NebulaFormUi.column(context); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, NebulaFormUi.scroll(context, column));
    }
    private void note(String value) {
        TextView label = new TextView(column.getContext()); label.setText(value); label.setTextSize(14);
        label.setTextColor(NebulaTheme.of(column.getContext()).onSurfaceVariant()); label.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12)); column.addView(label);
    }
    private void rebuild() {
        if (destroyed) return;
        final int ticket = ++generation; column.removeAllViews(); Context context = column.getContext();
        column.addView(new NebulaSettingsHero(context, "📝", t("История правок", "Edit history"), t("Предыдущие версии, полученные этим устройством", "Previous versions received on this device")));
        NebulaCard controls = new NebulaCard(context);
        controls.add(new NebulaRow(context).title(t("Сохранять правки", "Save edits")).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaEditHistory.enabled(currentAccount))
                .withClick(v -> { NebulaEditHistory.setEnabled(currentAccount, !NebulaEditHistory.enabled(currentAccount)); rebuild(); }));
        controls.add(new NebulaRow(context).title(selected == null ? t("Очистить всю историю аккаунта", "Clear all account history") : t("Очистить историю сообщения", "Clear message history"))
                .withClick(v -> showDialog(new NebulaDialog.Builder(context).setTitle(t("Удалить локальные версии?", "Delete local versions?"))
                        .setMessage(t("Текущее сообщение в Telegram останется.", "The current Telegram message stays."))
                        .setNegativeButton(t("Отмена", "Cancel"), null).setPositiveButton(t("Очистить", "Clear"), (d, w) -> NebulaEditHistory.clear(currentAccount, selected == null ? null : selected.getDialogId(), selected == null ? null : selected.getId(), this::rebuild,
                                () -> { if (!destroyed) note(t("Не удалось очистить историю", "Could not clear history")); })).create())));
        column.addView(controls);
        note(t("Только после включения: до 32 версий для 1000 последних изменённых сообщений. Секретные, исчезающие, защищённые и служебные сообщения исключены. Выключение сохраняет прежние версии. Данные находятся в локальной базе аккаунта и удаляются при выходе.",
                "From activation onward: up to 32 versions for the 1000 most recently edited messages. Secret, expiring, protected and service messages are excluded. Turning this off keeps previous versions. Data stays in the local account database and is removed on logout."));
        if (selected == null) { note(t("Откройте инструменты нужного сообщения → «История правок».", "Open a message's tools → Edit history.")); return; }
        column.addView(NebulaCard.header(context, t("Сейчас", "Current"))); addVersion(selected.messageOwner);
        column.addView(NebulaCard.header(context, t("Предыдущие версии", "Previous versions")));
        NebulaEditHistory.read(currentAccount, selected.getDialogId(), selected.getId(), versions -> {
            if (destroyed || ticket != generation) return;
            if (versions.isEmpty()) note(t("Сохранённых версий пока нет", "No saved versions yet"));
            for (TLRPC.Message version : versions) addVersion(version);
        }, () -> { if (!destroyed && ticket == generation) note(t("Не удалось прочитать историю", "Could not read history")); });
    }
    private void addVersion(TLRPC.Message message) {
        Context context = column.getContext(); NebulaCard card = new NebulaCard(context);
        int time = message.edit_date > 0 ? message.edit_date : message.date;
        TextView body = new TextView(context); body.setTextSize(16); body.setTextColor(NebulaTheme.of(context).onSurface());
        body.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        boolean spoiler = false;
        for (TLRPC.MessageEntity entity : message.entities) if (entity instanceof TLRPC.TL_messageEntitySpoiler) { spoiler = true; break; }
        if (spoiler) {
            body.setText(t("Спойлер · нажмите, чтобы показать версию", "Spoiler · tap to reveal this version"));
            body.setOnClickListener(v -> {
                body.setText(NebulaRichText.render(NebulaRichText.copy(message.message, message.entities), body.getPaint().getFontMetricsInt()));
                body.setOnClickListener(null);
            });
        } else body.setText(NebulaRichText.render(NebulaRichText.copy(message.message, message.entities), body.getPaint().getFontMetricsInt()));
        // Links in the static preview are never activated.
        card.add(new NebulaRow(context).title(DateFormat.getDateTimeInstance().format(new Date(time * 1000L)))); card.add(body); column.addView(card);
    }
    @Override public void onFragmentDestroy() { destroyed = true; generation++; super.onFragmentDestroy(); }
}
