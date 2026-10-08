package app.nebulagram.ui;

import android.os.Bundle;
import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ActionBar.BaseFragment;

/** Native peer pickers with account identity checks; no destinations are shared across accounts. */
public final class NebulaPeerSelections {
    private NebulaPeerSelections() { }
    private static String text(String ru, String en) { return NebulaText.text(ru, en); }
    static String name(int account, long peer) {
        if (peer == UserConfig.getInstance(account).getClientUserId()) return LocaleController.getString(R.string.SavedMessages);
        TLRPC.User user = peer > 0 ? MessagesController.getInstance(account).getUser(peer) : null;
        TLRPC.Chat chat = peer < 0 ? MessagesController.getInstance(account).getChat(-peer) : null;
        return user != null ? UserObject.getUserName(user) : chat != null ? chat.title : Long.toString(peer);
    }
    static void choose(BaseFragment host, boolean writable, java.util.function.LongConsumer chosen) {
        int account = host.getCurrentAccount(); long owner = UserConfig.getInstance(account).getClientUserId();
        Bundle args = new Bundle(); args.putBoolean("onlySelect", true); args.putInt("dialogsType", writable ? DialogsActivity.DIALOGS_TYPE_FORWARD : DialogsActivity.DIALOGS_TYPE_DEFAULT);
        DialogsActivity picker = new DialogsActivity(args); picker.setCurrentAccount(account);
        picker.setDelegate((fragment, dialogs, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (owner == 0 || owner != UserConfig.getInstance(account).getClientUserId() || dialogs.isEmpty()) return true;
            long peer = dialogs.get(0).dialogId;
            if (DialogObject.isEncryptedDialog(peer)) return false;
            chosen.accept(peer); fragment.finishFragment(); return true;
        });
        host.presentFragment(picker);
    }
    public static void translation(BaseFragment host) {
        int account = host.getCurrentAccount(); long owner = UserConfig.getInstance(account).getClientUserId();
        Bundle args = new Bundle(); args.putBoolean("onlySelect", true); args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        DialogsActivity picker = new DialogsActivity(args); picker.setCurrentAccount(account);
        picker.setDelegate((fragment, dialogs, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (owner == 0 || owner != UserConfig.getInstance(account).getClientUserId() || dialogs.isEmpty()) return true;
            long peer = dialogs.get(0).dialogId;
            if (DialogObject.isEncryptedDialog(peer)) return false;
            fragment.presentFragment(new NebulaTranslationFragment(account, peer), true);
            return true;
        });
        host.presentFragment(picker);
    }
    public static void savedRow(BaseFragment host, NebulaCard card) {
        int account = host.getCurrentAccount(); Context context = card.getContext();
        NebulaRow row = new NebulaRow(context).title(text("Чат для сохранения сообщений", "Save messages to"))
                .subtitle(name(account, NebulaFeatureSettings.savedTarget(account)), false).trailing(NebulaRow.TRAIL_CHEVRON);
        row.withClick(v -> host.showDialog(new NebulaDialog.Builder(context).setTitle(text("Куда сохранять", "Save destination"))
                .setItems(new CharSequence[]{LocaleController.getString(R.string.SavedMessages), text("Выбрать чат", "Choose chat")}, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) { NebulaFeatureSettings.setSavedTarget(account, UserConfig.getInstance(account).getClientUserId()); row.subtitle(name(account, NebulaFeatureSettings.savedTarget(account)), false); }
                    else choose(host, true, peer -> { NebulaFeatureSettings.setSavedTarget(account, peer); row.subtitle(name(account, peer), false); });
                }).create())); card.add(row);
    }
    public static void mentionRow(BaseFragment host, NebulaCard card) {
        NebulaRow row = new NebulaRow(card.getContext()).title(text("Чаты с игнорированием упоминаний", "Chats with ignored mentions"))
                .subtitle(text("Все чаты или выбранные переписки", "All chats or selected conversations"), false).trailing(NebulaRow.TRAIL_CHEVRON);
        row.withClick(v -> mentions(host)); card.add(row);
    }
    private static void mentions(BaseFragment host) {
        int account = host.getCurrentAccount(); long owner = UserConfig.getInstance(account).getClientUserId();
        String current = NebulaFeatureSettings.mentionSelection(account);
        LinkedHashSet<Long> peers = new LinkedHashSet<>();
        if (!"*".equals(current)) for (String id : current.split(",")) { try { peers.add(Long.parseLong(id)); } catch (NumberFormatException ignored) { } }
        ArrayList<CharSequence> labels = new ArrayList<>();
        labels.add(text("Все чаты", "All chats")); labels.add(text("Добавить чат", "Add chat"));
        ArrayList<Long> ids = new ArrayList<>(peers); for (long id : ids) labels.add(text("Убрать: ", "Remove: ") + name(account, id));
        host.showDialog(new NebulaDialog.Builder(host.getParentActivity()).setTitle(text("Игнорирование упоминаний", "Ignored mentions"))
                .setItems(labels.toArray(new CharSequence[0]), (dialog, index) -> {
                    dialog.dismiss(); if (owner != UserConfig.getInstance(account).getClientUserId()) return;
                    if (index == 0) NebulaFeatureSettings.setMentionSelection(account, "*");
                    else if (index == 1) choose(host, false, peer -> { peers.add(peer); store(account, peers); });
                    else { peers.remove(ids.get(index - 2)); store(account, peers); }
                }).setSelectedIndex("*".equals(current) ? 0 : -1).create());
    }
    private static void store(int account, LinkedHashSet<Long> peers) {
        StringBuilder joined = new StringBuilder(); for (long peer : peers) { if (joined.length() > 0) joined.append(','); joined.append(peer); }
        NebulaFeatureSettings.setMentionSelection(account, joined.toString());
    }
    public static void forward(BaseFragment host, ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return;
        int account = host.getCurrentAccount(); long peer = NebulaFeatureSettings.savedTarget(account);
        if (peer == 0 || DialogObject.isEncryptedDialog(peer)) return;
        for (MessageObject message : messages) if (message == null || message.currentAccount != account || message.messageOwner.noforwards
                || message.isSecret() || message.isExpiredStory() || message.type == MessageObject.TYPE_PAID_MEDIA || message.isSponsored()) return;
        Bundle args = new Bundle(); if (peer > 0) args.putLong("user_id", peer); else args.putLong("chat_id", -peer);
        if (!MessagesController.getInstance(account).checkCanOpenChat(args, host)) return;
        ChatActivity destination = new ChatActivity(args); destination.setCurrentAccount(account);
        if (host.presentFragment(destination)) destination.showFieldPanelForForward(true, messages);
    }
}
