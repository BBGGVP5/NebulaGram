package app.nebulagram.ui;

import android.content.Context;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.community.CommunitySheet;
import org.telegram.ui.community.cells.CommunityLinkView;

/** Telegram's real community peer card; title, avatar and linked chats come from Telegram. */
public final class NebulaCommunityCard extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    public static final String SOURCE_USERNAME = "nebulaguard_channel";
    private final BaseFragment fragment;
    private final int account;
    private final String username;
    private final CommunityLinkView card;
    private TLRPC.Chat community;
    private long sourceChatId;
    private long boundChatId, boundPhotoId, requestedCommunityId;
    private int boundPhotoDc;
    private boolean attached;
    private int generation, request;

    public NebulaCommunityCard(Context context, BaseFragment fragment) {
        this(context, fragment, SOURCE_USERNAME);
    }

    public NebulaCommunityCard(Context context, BaseFragment fragment, String username) {
        super(context);
        this.fragment = fragment;
        this.account = fragment.getCurrentAccount();
        this.username = username;
        card = new CommunityLinkView(context, fragment.getResourceProvider());
        card.avatarView.getImageReceiver().setCurrentAccount(account);
        card.avatarView.getImageReceiver().setCrossfadeWithOldImage(true);
        card.setTitle("NebulaHub");
        card.setSubtitle(NebulaText.text("Сообщество Telegram", "Telegram community"));
        card.setBackground(Theme.getSelectorDrawable(false));
        card.setOnClickListener(v -> {
            if (community != null) fragment.showDialog(new CommunitySheet(fragment, community.id));
            else Browser.openUrl(context, "https://t.me/" + username);
        });
        addView(card, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        card.updateColors();
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.chatInfoDidLoad);
        final int token = ++generation;
        MessagesController cachedController = MessagesController.getInstance(account);
        Object cached = cachedController.getUserOrChat(username);
        if (cached instanceof TLRPC.Chat) {
            TLRPC.Chat peer = cachedController.getChat(((TLRPC.Chat) cached).id);
            if (peer == null) peer = (TLRPC.Chat) cached;
            sourceChatId = peer.id;
            selectCommunity(peer);
            cachedController.loadFullChat(peer.id, fragment.getClassGuid(), false);
        }
        TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
        resolve.username = username;
        request = ConnectionsManager.getInstance(account).sendRequest(resolve, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!attached || token != generation) return;
            request = 0;
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                if (community == null) card.setSubtitle(NebulaText.text("Нажмите, чтобы открыть в Telegram", "Tap to open in Telegram"));
                return;
            }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            MessagesController controller = MessagesController.getInstance(account);
            controller.putUsers(resolved.users, false);
            controller.putChats(resolved.chats, false);
            MessagesStorage.getInstance(account).putUsersAndChats(resolved.users, resolved.chats, true, true);
            long dialog = DialogObject.getPeerDialogId(resolved.peer);
            TLRPC.Chat peer = dialog < 0 ? controller.getChat(-dialog) : null;
            if (peer == null) return;
            sourceChatId = peer.id;
            selectCommunity(peer);
            // Channel full info includes its linked community and the peer's access hash.
            // Do not treat the channel itself as a community or guess its linked chats.
            controller.loadFullChat(peer.id, fragment.getClassGuid(), false);
        }));
    }

    private void selectCommunity(TLRPC.Chat peer) {
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat target = ChatObject.isCommunity(peer) ? peer : controller.getChat(peer.linked_community_id);
        community = target != null && ChatObject.isCommunity(target) ? target : null;
        if (community == null) {
            if (boundChatId != 0) card.avatarView.setImageDrawable(null);
            boundChatId = boundPhotoId = requestedCommunityId = 0;
            boundPhotoDc = 0;
            card.setTitle("NebulaHub");
            card.setSubtitle(NebulaText.text("Сообщество Telegram", "Telegram community"));
            return;
        }
        refresh();
        if (community.id != sourceChatId && requestedCommunityId != community.id
                && controller.getChatFull(community.id) == null) {
            requestedCommunityId = community.id;
            controller.loadFullChat(community.id, fragment.getClassGuid(), false);
        }
    }

    private void refresh() {
        if (!attached || community == null) return;
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.Chat latest = controller.getChat(community.id);
        if (latest != null) community = latest;
        long photoId = community.photo == null ? 0 : community.photo.photo_id;
        int photoDc = community.photo == null ? 0 : community.photo.dc_id;
        // Full-chat notifications update the count, not the image request. Store
        // primitive IDs because Telegram can mutate the cached Chat in place.
        if (boundChatId != community.id || boundPhotoId != photoId || boundPhotoDc != photoDc) {
            card.setChat(account, community);
            boundChatId = community.id;
            boundPhotoId = photoId;
            boundPhotoDc = photoDc;
        }
        card.setTitle(DialogObject.getShortName(community));
        TLRPC.ChatFull full = controller.getChatFull(community.id);
        // Unknown metadata is not a community with zero chats.
        if (full == null)
            card.setSubtitle(NebulaText.text("Сообщество Telegram", "Telegram community"));
        else card.setSubtitle(LocaleController.formatPluralString("CommunityWithChats", full.linked_peers.size()));
    }

    @Override public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if (!attached || currentAccount != account || id != NotificationCenter.chatInfoDidLoad || args.length == 0) return;
        if (!(args[0] instanceof TLRPC.ChatFull)) return;
        long loadedId = ((TLRPC.ChatFull) args[0]).id;
        if (loadedId == sourceChatId) {
            TLRPC.Chat peer = MessagesController.getInstance(account).getChat(sourceChatId);
            if (peer != null) selectCommunity(peer);
        } else if (community != null && loadedId == community.id) refresh();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        generation++;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        if (request != 0) ConnectionsManager.getInstance(account).cancelRequest(request, true);
        request = 0;
        requestedCommunityId = 0;
        super.onDetachedFromWindow();
    }
}
