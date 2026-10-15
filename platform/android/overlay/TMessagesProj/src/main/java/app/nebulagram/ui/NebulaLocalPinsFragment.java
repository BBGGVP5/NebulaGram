package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaLocalPinsFragment extends BaseFragment {
    private LinearLayout column;
    public NebulaLocalPinsFragment(int account) { currentAccount = account; }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public View createView(Context context) {
        NebulaFormUi.bar(this, actionBar, context, t("Локальные закрепления", "Local pins"));
        column = NebulaFormUi.column(context); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, NebulaFormUi.scroll(context, column));
    }
    private void rebuild() {
        Context context = column.getContext(); column.removeAllViews(); NebulaLocalPinStore.Snapshot state = NebulaLocalPins.snapshot(currentAccount);
        column.addView(new NebulaSettingsHero(context, "📌", t("На этом устройстве", "On this device"), t("Отдельный порядок для аккаунта и каждой папки", "Separate order for each account and folder")));
        NebulaCard card = new NebulaCard(context);
        card.add(new NebulaRow(context).title(t("Показывать локальные закрепления", "Show local pins"))
                .trailing(NebulaRow.TRAIL_SWITCH).checked(state.enabled).withClick(v -> {
                    if (!NebulaLocalPins.setEnabled(currentAccount, !state.enabled)) failed(); rebuild();
                }));
        card.add(new NebulaRow(context).title(t("Очистить локальные закрепления", "Clear local pins")).subtitle(Integer.toString(state.count()), false)
                .withClick(v -> showDialog(new NebulaDialog.Builder(context).setTitle(t("Очистить закрепления на устройстве?", "Clear pins on this device?"))
                        .setMessage(t("Закрепления Telegram и сами чаты сохранятся.", "Telegram pins and the chats remain."))
                        .setNegativeButton(t("Отмена", "Cancel"), null).setPositiveButton(t("Очистить", "Clear"), (d, w) -> { if (!NebulaLocalPins.clear(currentAccount)) failed(); rebuild(); }).create())));
        column.addView(card);
        column.addView(NebulaFormUi.note(context, t("Удерживайте чат → «Закрепить на устройстве». Сначала идут закрепления Telegram, затем локальные, затем остальные чаты. Порядок главного списка, архива и каждой папки независим. До 1000 закреплений на аккаунт; секретные чаты не поддерживаются. На сервер порядок не отправляется. Выключение сохраняет список.",
                "Hold a chat → Pin on this device. Telegram pins come first, then local pins, then the remaining chats. The main list, archive and each folder have independent orders. Up to 1000 pins per account; secret chats are unsupported. The order is not sent to the server. Turning this off keeps the list.")));
    }
    @Override public void onResume() { super.onResume(); if (column != null) rebuild(); }
    private void failed() { showDialog(new NebulaDialog.Builder(getContext()).setTitle(t("Не удалось сохранить", "Could not save")).setMessage(t("Проверьте свободное место и повторите.", "Check free storage and try again.")).setPositiveButton(t("Понятно", "OK"), null).create()); }
}
