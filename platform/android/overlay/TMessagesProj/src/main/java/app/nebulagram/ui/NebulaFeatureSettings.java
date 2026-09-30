package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.tgnet.tl.TL_stories;
import java.util.*;

/** Opt-in behavior preferences. Peer destinations and mention selections are account-local. */
public final class NebulaFeatureSettings {
    private NebulaFeatureSettings() { }
    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }
    public static boolean enabled(String key) {
        return prefs().getBoolean(key, "predictive_back".equals(key) || "custom_chat_wallpaper".equals(key)
                || "archive_user_stories".equals(key));
    }
    public static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); }
    public static float fade(float progress) { return NebulaFeaturePolicy.fade(progress, enabled("smooth_fade")); }
    public static boolean haptic(android.view.View view, int feedback, int flags) {
        return !enabled("disable_chat_vibration") && view != null && view.performHapticFeedback(feedback, flags);
    }
    public static boolean ignoredMessage(int account, MessageObject message) {
        return message != null && message.messageOwner.mentioned && ignoreMention(account, message.getDialogId());
    }
    private static String accountKey(String key, int account) {
        return key + "_" + UserConfig.getInstance(account).getClientUserId();
    }
    public static String mentionSelection(int account) { return prefs().getString(accountKey("ignored_mention_peers", account), "*"); }
    public static void setMentionSelection(int account, String value) { prefs().edit().putString(accountKey("ignored_mention_peers", account), value).apply(); }
    public static boolean ignoreMention(int account, long dialog) {
        return NebulaFeaturePolicy.ignoreMention(enabled("ignore_mentions"), mentionSelection(account), dialog);
    }
    public static long savedTarget(int account) {
        return prefs().getLong(accountKey("saved_destination", account), UserConfig.getInstance(account).getClientUserId());
    }
    public static void setSavedTarget(int account, long peer) { prefs().edit().putLong(accountKey("saved_destination", account), peer).apply(); }
    public static boolean silenceUnknown(int account, MessageObject message) {
        if (message == null || !enabled("mute_non_contacts") || message.isStoryPush || message.isReactionPush) return false;
        long id = message.getDialogId();
        TLRPC.User user = MessagesController.getInstance(account).getUser(id);
        boolean contact = user != null && (user.contact || user.mutual_contact)
                || ContactsController.getInstance(account).contactsDict.get(id) != null;
        return NebulaFeaturePolicy.silenceUnknown(true, id, contact,
                id == UserConfig.getInstance(account).getClientUserId() || UserObject.isService(id));
    }
    public static java.util.ArrayList<TLRPC.Dialog> visibleDialogs(java.util.ArrayList<TLRPC.Dialog> dialogs, int folder) {
        if (folder != 0 || !enabled("hide_archive")) return dialogs;
        java.util.ArrayList<TLRPC.Dialog> visible = new java.util.ArrayList<>(dialogs.size());
        for (TLRPC.Dialog dialog : dialogs) if (!(dialog instanceof TLRPC.TL_dialogFolder)) visible.add(dialog);
        return visible;
    }
    private static final Set<String> pendingStories = new HashSet<>();
    public static void archiveStories(int account, StoriesController controller, ArrayList<TL_stories.PeerStories> stories) {
        if (!enabled("auto_archive_stories")) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) return;
        int delay = 0;
        for (TL_stories.PeerStories story : new ArrayList<>(stories)) {
            long peer = DialogObject.getPeerDialogId(story.peer);
            if (peer == UserConfig.getInstance(account).getClientUserId() || peer == 0
                    || !enabled(peer > 0 ? "archive_user_stories" : "archive_channel_stories")) continue;
            String token = accountKey("story", account) + "_" + peer;
            if (!pendingStories.add(token)) continue;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (owner != UserConfig.getInstance(account).getClientUserId()) return;
                    if (!enabled("auto_archive_stories") || !enabled(peer > 0 ? "archive_user_stories" : "archive_channel_stories")) return;
                    TLRPC.User user = peer > 0 ? MessagesController.getInstance(account).getUser(peer) : null;
                    TLRPC.Chat chat = peer < 0 ? MessagesController.getInstance(account).getChat(-peer) : null;
                    if (user != null && !user.stories_hidden || chat != null && !chat.stories_hidden)
                        controller.toggleHidden(peer, true, true, true);
                } finally { pendingStories.remove(token); }
            }, delay);
            delay += 120;
        }
    }
}
