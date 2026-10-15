package app.nebulagram.ui;

import android.util.Base64;
import org.telegram.messenger.*;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.ConnectionsManager;
import java.util.*;

/** View ordering only: never sets Dialog.pinned or sends a pin/filter update. */
public final class NebulaLocalPins {
    private NebulaLocalPins() {}
    private static final NebulaLocalPinStore store = new NebulaLocalPinStore(new NebulaLocalPinStore.Storage() {
        public String read(long owner) { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_local_pins", 0).getString(Long.toString(owner), ""); }
        public boolean write(long owner, String value) { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_local_pins", 0).edit().putString(Long.toString(owner), value).commit(); }
    });
    private static long owner(int account) { return UserConfig.getInstance(account).getClientUserId(); }
    public static NebulaLocalPinStore.Snapshot snapshot(int account) { return store.read(owner(account)); }
    private static final Map<Integer, String> filterScopes = new java.util.concurrent.ConcurrentHashMap<>();
    public static String scope(int group, MessagesController.DialogFilter filter) {
        if (filter == null || filter.id == 0) return group == 0 ? "root:0" : group == 1 ? "root:1" : "";
        return filterScopes.computeIfAbsent(filter.id, id -> "filter:" + id);
    }
    public static int compare(int account, MessagesController.DialogFilter filter, TLRPC.Dialog first, TLRPC.Dialog second) {
        NebulaLocalPinStore.Snapshot state = snapshot(account); if (!state.enabled || state.orders.isEmpty()) return 0;
        int a = state.rank(scope(first.folder_id, filter), first.id), b = state.rank(scope(second.folder_id, filter), second.id);
        if (a < 0 && b < 0) return 0; if (a < 0) return 1; if (b < 0) return -1; return Integer.compare(a, b);
    }
    public static boolean pinned(int account, String scope, long peer) { return snapshot(account).orders.getOrDefault(scope, Collections.emptyList()).contains(peer); }
    public static boolean toggle(int account, String scope, long peer) {
        if (DialogObject.isEncryptedDialog(peer)) return false;
        boolean pinned = pinned(account, scope, peer); String metadata = "";
        if (!pinned) {
            TLRPC.InputPeer input = MessagesController.getInstance(account).getInputPeer(peer);
            if (input == null || input instanceof TLRPC.TL_inputPeerEmpty) return false;
            SerializedData data = new SerializedData(); try { input.serializeToStream(data); metadata = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP); } finally { data.cleanup(); }
        }
        if (!store.pin(owner(account), scope, peer, !pinned, metadata)) return false;
        refresh(account); return true;
    }
    public static boolean setEnabled(int account, boolean enabled) {
        if (!store.setEnabled(owner(account), enabled)) return false;
        refresh(account); if (enabled) { warmed.remove(account); warmup(account); } return true;
    }
    public static boolean clear(int account) { if (!store.clear(owner(account))) return false; refresh(account); return true; }
    private static void refresh(int account) {
        MessagesController.getInstance(account).sortDialogs(null);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }
    private static final Set<Integer> warmed = new HashSet<>();
    /** Restore old pinned chats in sequential read-only batches; native rows keep their remote pin state. */
    public static void warmup(int account) {
        if (!warmed.add(account)) return;
        NebulaLocalPinStore.Snapshot state = snapshot(account); if (!state.enabled || state.peers.isEmpty()) return;
        ArrayList<TLRPC.InputPeer> peers = new ArrayList<>();
        for (String encoded : state.peers.values()) {
            if (encoded.isEmpty()) continue;
            try { SerializedData data = new SerializedData(Base64.decode(encoded, Base64.NO_WRAP)); try { TLRPC.InputPeer peer = TLRPC.InputPeer.TLdeserialize(data, data.readInt32(false), false); if (peer != null) peers.add(peer); } finally { data.cleanup(); } }
            catch (Exception invalid) { /* Stale peer metadata does not affect ordinary list loading. */ }
        }
        loadBatch(account, owner(account), peers, 0);
    }
    private static void loadBatch(int account, long expectedOwner, ArrayList<TLRPC.InputPeer> peers, int offset) {
        if (owner(account) != expectedOwner || !snapshot(account).enabled || offset >= peers.size()) return;
        TLRPC.TL_messages_getPeerDialogs request = new TLRPC.TL_messages_getPeerDialogs();
        int end = Math.min(peers.size(), offset + 50);
        for (int i = offset; i < end; i++) { TLRPC.TL_inputDialogPeer value = new TLRPC.TL_inputDialogPeer(); value.peer = peers.get(i); request.peers.add(value); }
        ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (owner(account) != expectedOwner || !snapshot(account).enabled) return;
            if (error != null) { warmed.remove(account); return; }
            if (response instanceof TLRPC.TL_messages_peerDialogs) MessagesController.getInstance(account).applyNebulaLocalPinDialogs((TLRPC.TL_messages_peerDialogs) response);
            loadBatch(account, expectedOwner, peers, end);
        }));
    }
}
