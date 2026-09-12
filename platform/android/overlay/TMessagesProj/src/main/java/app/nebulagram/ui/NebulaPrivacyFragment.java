package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Локальная приватность: сохранение удалённых сообщений и их кэш.
 *
 * <p>Экран собран из тех же карточек и строк, что и остальные разделы
 * NebulaGram, а не из голого списка: разделы отделены шапками, у каждой строки
 * есть значок, пояснения стоят под карточкой мелким кеглем.
 */
public final class NebulaPrivacyFragment extends BaseFragment {
    private LinearLayout content;
    private long owner;

    private String text(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        owner = NebulaDeletedArchive.owner(currentAccount);
        actionBar.setTitle(text("Конфиденциальность", "Privacy"));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(24));
        scroll.setBackgroundColor(theme.surface());
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fragmentView = scroll;
        rebuild();
        return scroll;
    }

    @Override public boolean isLightStatusBar() {
        return !NebulaTheme.of(getContext()).isDark();
    }

    private NebulaRow row(int icon, String title) {
        return new NebulaRow(content.getContext()).icon(icon).title(title);
    }

    private void header(String title) {
        content.addView(NebulaCard.header(content.getContext(), title));
    }

    private void card(View... rows) {
        NebulaCard card = new NebulaCard(content.getContext());
        for (View row : rows) card.add(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        content.addView(card, params);
    }

    /** Пояснение под карточкой: мельче и тише текста строк, как в Material 3. */
    private void note(String text) {
        Context context = content.getContext();
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(13);
        label.setLineSpacing(AndroidUtilities.dp(2), 1f);
        label.setTextColor(NebulaTheme.of(context).onSurfaceVariant());
        label.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        content.addView(label);
    }

    private void rebuild() {
        if (content == null) return;
        content.removeAllViews();

        header(text("Удалённые сообщения", "Deleted messages"));
        card(row(R.drawable.msg_delete, text("Сохранять удалённые сообщения", "Save deleted messages"))
                        .subtitle(text("Остаются на своём месте в чате", "They stay in place in the chat"), false)
                        .trailing(NebulaRow.TRAIL_SWITCH)
                        .checked(NebulaDeletedArchive.enabled(owner))
                        .withClick(v -> {
                            if (NebulaDeletedArchive.enabled(owner)) { NebulaDeletedArchive.setEnabled(owner, false); rebuild(); return; }
                            showDialog(new AlertDialog.Builder(getParentActivity())
                                    .setTitle(text("Включить локальный архив?", "Enable local archive?"))
                                    .setMessage(text("Полученные сообщения останутся на своём месте в чате с отметкой удаления. Их содержимое и вложения остаются в обычном локальном кэше Telegram; на сервер ничего не отправляется. Секретные и исчезающие сообщения требуют отдельных переключателей. Защита от копирования сохраняется.", "Received messages stay in place with a deletion marker. Content and attachments remain in Telegram’s normal local cache; nothing is sent to the server. Secret and expiring messages require separate switches. Copy protection is respected."))
                                    .setNegativeButton(text("Отмена", "Cancel"), null)
                                    .setPositiveButton(text("Включить", "Enable"), (d, w) -> { NebulaDeletedArchive.setEnabled(owner, true); rebuild(); })
                                    .create());
                        }),
                extraToggle(true), extraToggle(false));
        note(text("Архив хранится столько, сколько нужно вам: ни по числу сообщений, ни по сроку он не ограничен и очищается только вручную. Текст и уже полученные вложения. Медиа может удаляться стандартной очисткой кэша; недоступные файлы восстановить нельзя. Фоновое сохранение возможно лишь при получении приложением события удаления. Выключение не удаляет существующий архив. Клиент собеседника не определяется.",
                "The archive is kept for as long as you want it: no message-count or time limit, and it is cleared only by hand. Text and received attachments. Standard cache eviction can remove media; unavailable files cannot be recovered. Background capture requires the app to receive the deletion update. Disabling does not erase existing entries. Peer clients are not detected."));

        header(text("Оформление", "Appearance"));
        card(row(R.drawable.msg_emoji_smiles, text("Значок удалённого сообщения", "Deleted message icon"))
                .subtitle(NebulaDeletedArchive.icon(), true)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> icons()));
        note(text("Значок показывается в чате вместо слова «Удалено».",
                "The icon replaces the word Deleted in the chat."));

        header(text("Ограничения отправителя", "Sender restrictions"));
        card(row(R.drawable.msg_forward, text("Разрешить снимки и пересылку", "Allow screenshots and forwarding"))
                .subtitle(text("В чатах, где включена защита содержимого", "In chats with content protection on"), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaContentProtection.enabled())
                .withClick(v -> {
                    if (NebulaContentProtection.enabled()) { NebulaContentProtection.setEnabled(false); rebuild(); return; }
                    showDialog(new AlertDialog.Builder(getParentActivity())
                            .setTitle(text("Снять ограничения отправителя?", "Lift sender restrictions?"))
                            .setMessage(text("Снимки экрана, пересылка, копирование и сохранение снова заработают в чатах, где отправитель их запретил. Речь только о содержимом, которое уже пришло на это устройство: флаг собеседника никуда не девается, и о снятии ограничения он не узнаёт. Отвечать за то, что делать с чужим содержимым, придётся вам.", "Screenshots, forwarding, copying and saving start working again in chats where the sender disabled them. This applies only to content already delivered to this device: the sender's flag remains, and they are not told it was lifted. What you then do with someone else's content is your responsibility."))
                            .setNegativeButton(text("Отмена", "Cancel"), null)
                            .setPositiveButton(text("Снять", "Lift"), (d, w) -> { NebulaContentProtection.setEnabled(true); rebuild(); })
                            .create());
                }));
        note(text("Выключено по умолчанию. Настройка меняет только этот клиент и только на этом устройстве.",
                "Off by default. The setting changes this client on this device and nothing else."));

        header(text("Локальный кэш", "Local cache"));
        card(row(R.drawable.msg_clearcache, text("Очистить кэш удалённых сообщений", "Clear retained-message cache"))
                .destructive()
                .withClick(v -> confirmClear(this, currentAccount, 0, this::rebuild)));

        // The archive preview is gone: retained messages live in the chat
        // itself, so a second copy of them here only duplicated the
        // conversation and put other people's text on a settings screen.
        if (NebulaDeletedArchive.hasError(owner)) {
            note(text("Часть сообщений не удалось сохранить. Архив не сброшен.",
                    "Some messages could not be saved. The archive was not reset."));
        }
    }

    private NebulaRow extraToggle(boolean secret) {
        boolean selected = secret ? NebulaDeletedArchive.saveSecret(owner) : NebulaDeletedArchive.saveExpiring(owner);
        return row(secret ? R.drawable.msg_secret : R.drawable.msg_autodelete,
                secret ? text("Сохранять в секретных чатах", "Retain in secret chats")
                       : text("Сохранять исчезающие сообщения", "Retain expiring messages"))
                .trailing(NebulaRow.TRAIL_SWITCH).checked(selected).withClick(v -> {
            if (selected) { NebulaDeletedArchive.setExtra(owner, secret, false); rebuild(); return; }
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(text("Оставлять локальную копию?", "Keep a local copy?"))
                    .setMessage(text("При включённом сохранении копия останется после удаления или таймера. Это меняет ожидаемое поведение секретных и исчезающих сообщений. Работает только с уже полученным содержимым на этом устройстве.", "When retention is enabled, a local copy remains after deletion or expiry. This changes the expected behavior of secret and expiring messages. Only content already received on this device can be kept."))
                    .setNegativeButton(text("Отмена", "Cancel"), null)
                    .setPositiveButton(text("Включить", "Enable"), (d, w) -> { NebulaDeletedArchive.setExtra(owner, secret, true); rebuild(); })
                    .create());
        });
    }

    public static void confirmClear(BaseFragment fragment, int account, long peer, Runnable done) {
        if (fragment.getParentActivity() == null) return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle(NebulaText.text(peer == 0 ? "Очистить все сохранённые удалённые сообщения?" : "Очистить удалённые сообщения этого чата?", peer == 0 ? "Clear all retained messages?" : "Clear retained messages in this chat?"))
            .setMessage(NebulaText.text("Только локальные сохранённые копии. Обычная переписка и общий медиакэш не удаляются.", "Only locally retained copies. Ordinary history and shared media cache are not deleted."))
            .setNegativeButton(NebulaText.text("Отмена", "Cancel"), null)
            .setPositiveButton(NebulaText.text("Очистить", "Clear"), (d, w) -> NebulaDeletedArchive.clearAsync(account, peer, () -> { if (done != null) done.run(); }, () -> {
                if (fragment.getParentActivity() != null) fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setMessage(NebulaText.text("Не удалось очистить сохранённые сообщения.", "Could not clear retained messages.")).setPositiveButton("OK", null).create());
            })).create());
    }

    private void icons() {
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Значок удалённого сообщения", "Deleted message icon"))
            .setItems(new CharSequence[]{"🗑", "✕", "◌", text("Свой символ / эмодзи", "Custom symbol / emoji")}, (dialog, which) -> {
                if (which < 3) { NebulaDeletedArchive.setIcon(new String[]{"🗑", "✕", "◌"}[which]); rebuild(); return; }
                EditText input = new EditText(getParentActivity());
                input.setSingleLine(true);
                input.setText(NebulaDeletedArchive.icon());
                input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Свой значок", "Custom icon"))
                    .setMessage(text("До четырёх символов или эмодзи. Значок показывается вместо слова «Удалено».", "Up to four symbols or emoji. The icon replaces the word Deleted."))
                    .setView(input)
                    .setNegativeButton(text("Отмена", "Cancel"), null)
                    .setPositiveButton(text("Сохранить", "Save"), (d, w) -> { NebulaDeletedArchive.setIcon(input.getText().toString()); rebuild(); }).create());
            }).create());
    }

    @Override public void onFragmentDestroy() { content = null; super.onFragmentDestroy(); }
}
