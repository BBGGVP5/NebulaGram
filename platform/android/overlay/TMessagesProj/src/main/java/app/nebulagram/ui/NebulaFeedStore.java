package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/** Лента каждого аккаунта хранится отдельно и только на устройстве. */
public final class NebulaFeedStore {
    private NebulaFeedStore() { }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram_feed", 0);
    }

    private static String key(int account) {
        return "feed_" + UserConfig.getInstance(account).getClientUserId();
    }

    public static NebulaFeedModel load(int account) {
        return NebulaFeedModel.parse(prefs().getString(key(account), null));
    }

    public static void save(int account, NebulaFeedModel model) {
        prefs().edit().putString(key(account), model.serialize()).apply();
    }

    /** Каналы, на которые подписан аккаунт, в порядке списка чатов. */
    public static ArrayList<TLRPC.Chat> subscribedChannels(int account) {
        ArrayList<TLRPC.Chat> result = new ArrayList<>();
        MessagesController controller = MessagesController.getInstance(account);
        for (TLRPC.Dialog dialog : new ArrayList<>(controller.getAllDialogs())) {
            if (dialog.id >= 0) continue;
            TLRPC.Chat chat = controller.getChat(-dialog.id);
            if (chat != null && ChatObject.isChannelAndNotMegaGroup(chat) && !ChatObject.isNotInChat(chat)) result.add(chat);
        }
        return result;
    }

    public static String channelsCount(int count) {
        int mod10 = count % 10, mod100 = count % 100;
        String ru = mod10 == 1 && mod100 != 11 ? " канал" : mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20) ? " канала" : " каналов";
        return count + NebulaText.text(ru, count == 1 ? " channel" : " channels");
    }
}
