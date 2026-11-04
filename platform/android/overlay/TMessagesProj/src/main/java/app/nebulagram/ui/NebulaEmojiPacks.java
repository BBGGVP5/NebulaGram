package app.nebulagram.ui;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Extra sources for the message menu's "contains emoji from N packs" row: custom
 * reactions on the message and custom-emoji icons of its inline buttons. Only
 * documents the app already resolved are used, so opening the menu sends nothing.
 */
public final class NebulaEmojiPacks {
    private NebulaEmojiPacks() {}

    /** Custom-emoji document ids from the message's reactions and inline button icons, in display order. */
    public static ArrayList<Long> extraDocumentIds(MessageObject message) {
        ArrayList<Long> ids = new ArrayList<>();
        TLRPC.Message owner = message == null ? null : message.messageOwner;
        if (owner == null) return ids;
        if (owner.reactions != null && owner.reactions.results != null) {
            for (TLRPC.ReactionCount count : owner.reactions.results) {
                if (count != null && count.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    addOnce(ids, ((TLRPC.TL_reactionCustomEmoji) count.reaction).document_id);
                }
            }
        }
        if (owner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) owner.reply_markup).rows) {
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                    if (button != null && button.style != null) addOnce(ids, button.style.icon);
                }
            }
        } else if (owner.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup) {
            for (TL_keyboard.KeyboardButtonRow row : ((TLRPC.TL_replyKeyboardMarkup) owner.reply_markup).rows) {
                for (TL_keyboard.KeyboardButton button : row.buttons) {
                    if (button != null && button.style != null) addOnce(ids, button.style.icon);
                }
            }
        }
        return ids;
    }

    public static boolean hasExtra(int account, MessageObject message) {
        ArrayList<TLRPC.InputStickerSet> sets = new ArrayList<>();
        addExtra(account, message, sets);
        return !sets.isEmpty();
    }

    /** Appends the packs of resolved reaction and button emoji to {@code sets}, skipping ones already listed. */
    public static void addExtra(int account, MessageObject message, List<TLRPC.InputStickerSet> sets) {
        for (long id : extraDocumentIds(message)) {
            TLRPC.Document document = AnimatedEmojiDrawable.findDocument(account, id);
            TLRPC.InputStickerSet set = document == null ? null : MessageObject.getInputStickerSet(document);
            if (set == null) continue;
            boolean found = false;
            for (TLRPC.InputStickerSet existing : sets) {
                if (existing.id == set.id) { found = true; break; }
            }
            if (!found) sets.add(set);
        }
    }

    /** Long press on the packs row: refresh one pack, or all of them, from the server. */
    public static void showRefresh(BaseFragment fragment, List<TLRPC.InputStickerSet> sets, Theme.ResourcesProvider resources) {
        if (fragment == null || fragment.getParentActivity() == null || sets.isEmpty()) return;
        int account = fragment.getCurrentAccount();
        CharSequence[] items = new CharSequence[sets.size() + 1];
        items[0] = NebulaText.text("Обновить все наборы", "Refresh all packs");
        for (int i = 0; i < sets.size(); i++) items[i + 1] = NebulaText.text("Обновить: ", "Refresh: ") + name(account, sets.get(i));
        new AlertDialog.Builder(fragment.getParentActivity(), resources)
                .setTitle(NebulaText.text("Наборы эмодзи", "Emoji packs"))
                .setItems(items, (dialog, which) -> {
                    List<TLRPC.InputStickerSet> chosen = which == 0 ? sets : sets.subList(which - 1, which);
                    int[] left = {chosen.size()}, failed = {0};
                    for (TLRPC.InputStickerSet set : chosen) {
                        refresh(account, set, ok -> {
                            if (!ok) failed[0]++;
                            if (--left[0] == 0) done(fragment, chosen.size(), failed[0]);
                        });
                    }
                })
                .show();
    }

    /** Re-fetches a pack with hash 0, so the server returns it whole even if a cached copy exists. */
    static void refresh(int account, TLRPC.InputStickerSet set, org.telegram.messenger.Utilities.Callback<Boolean> onDone) {
        TLRPC.TL_messages_getStickerSet request = new TLRPC.TL_messages_getStickerSet();
        request.stickerset = set;
        request.hash = 0;
        ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            boolean ok = response instanceof TLRPC.TL_messages_stickerSet;
            if (ok) MediaDataController.getInstance(account).putStickerSet((TLRPC.TL_messages_stickerSet) response);
            onDone.run(ok);
        }));
    }

    private static void done(BaseFragment fragment, int total, int failed) {
        if (fragment.getParentActivity() == null) return;
        String text = failed == 0
                ? NebulaText.text("Наборы обновлены: ", "Packs refreshed: ") + total
                : NebulaText.text("Не удалось обновить: ", "Failed to refresh: ") + failed + " / " + total;
        BulletinFactory.of(fragment).createSimpleBulletin(org.telegram.messenger.R.raw.done, text).show();
    }

    private static String name(int account, TLRPC.InputStickerSet set) {
        TLRPC.TL_messages_stickerSet loaded = MediaDataController.getInstance(account).getStickerSet(set, true);
        if (loaded != null && loaded.set != null && loaded.set.title != null) return loaded.set.title;
        return set.short_name != null ? set.short_name : Long.toString(set.id);
    }

    private static void addOnce(ArrayList<Long> ids, long id) {
        if (id != 0 && !ids.contains(id)) ids.add(id);
    }
}
