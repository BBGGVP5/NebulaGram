package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Consumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/** One explicit, account-scoped history read. Each page waits for its consumer. */
public final class NebulaHistorySession {
    public interface Page { void accept(ArrayList<TLRPC.Message> messages, Runnable next); }
    private final int account, topic;
    private final long dialog, owner;
    private int request;
    private boolean cancelled;
    private NebulaHistoryPager pager;
    private Page page;
    private Runnable done;
    private Consumer<String> failure;
    public NebulaHistorySession(int account, long dialog, int topic) {
        this.account = account; this.dialog = dialog; this.topic = topic;
        owner = UserConfig.getInstance(account).getClientUserId();
    }
    public boolean current() { return !cancelled && owner != 0 && UserConfig.getInstance(account).getClientUserId() == owner; }
    public void cancel() { cancelled = true; if (request != 0) ConnectionsManager.getInstance(account).cancelRequest(request, true); request = 0; }
    public void start(int count, Page page, Runnable done, Consumer<String> failure) {
        this.pager = new NebulaHistoryPager(count); this.page = page; this.done = done; this.failure = failure;
        if (!current() || DialogObject.isEncryptedDialog(dialog) || topic < 0) { failure.accept("UNSUPPORTED_CHAT"); return; }
        next();
    }
    private void next() {
        if (!current()) { if (!cancelled) failure.accept("ACCOUNT_CHANGED"); return; }
        TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(dialog);
        TLObject query;
        if (topic != 0) {
            TLRPC.TL_messages_getReplies q = new TLRPC.TL_messages_getReplies();
            q.peer = peer; q.msg_id = topic; q.offset_id = pager.offset(); q.limit = pager.remaining(); query = q;
        } else {
            TLRPC.TL_messages_getHistory q = new TLRPC.TL_messages_getHistory();
            q.peer = peer; q.offset_id = pager.offset(); q.limit = pager.remaining(); query = q;
        }
        request = ConnectionsManager.getInstance(account).sendRequest(query, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            request = 0;
            if (!current()) { if (!cancelled) failure.accept("ACCOUNT_CHANGED"); return; }
            if (!(response instanceof TLRPC.messages_Messages)) { failure.accept(error == null ? "HISTORY_FAILED" : error.text); return; }
            TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
            MessagesController.getInstance(account).putUsers(result.users, false);
            MessagesController.getInstance(account).putChats(result.chats, false);
            ArrayList<TLRPC.Message> accepted = new ArrayList<>(); HashSet<Integer> seen = new HashSet<>();
            int minimum = Integer.MAX_VALUE;
            for (TLRPC.Message message : result.messages) {
                if (!pager.accepts(message.id) || !seen.add(message.id)) continue;
                minimum = Math.min(minimum, message.id); accepted.add(message);
                if (accepted.size() >= pager.remaining()) break;
            }
            boolean more = pager.advance(minimum, accepted.size());
            if (accepted.isEmpty()) { done.run(); return; }
            page.accept(accepted, () -> {
                if (!current()) { if (!cancelled) failure.accept("ACCOUNT_CHANGED"); return; }
                if (more) next(); else done.run();
            });
        }));
    }
}
