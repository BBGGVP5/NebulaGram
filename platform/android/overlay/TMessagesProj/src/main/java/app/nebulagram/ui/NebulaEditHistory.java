package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.*;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Previous text/entities, inside the owning account DB. Never makes network requests. */
public final class NebulaEditHistory {
    public static final int MAX_VERSIONS = 32, MAX_MESSAGES = 1000;
    private static final Set<SQLiteDatabase> prepared = Collections.newSetFromMap(new WeakHashMap<SQLiteDatabase, Boolean>());
    private NebulaEditHistory() {}
    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0); }
    private static String setting(int account) { return "edit_history_" + UserConfig.getInstance(account).getClientUserId(); }
    public static boolean enabled(int account) { return UserConfig.getInstance(account).getClientUserId() != 0 && prefs().getBoolean(setting(account), false); }
    public static void setEnabled(int account, boolean enabled) { prefs().edit().putBoolean(setting(account), enabled).apply(); }
    private static void prepare(SQLiteDatabase db) throws Exception {
        synchronized (prepared) {
            if (prepared.contains(db)) return;
            db.executeFast("CREATE TABLE IF NOT EXISTS nebula_edit_history (seq INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER NOT NULL, mid INTEGER NOT NULL, data BLOB NOT NULL)").stepThis().dispose();
            db.executeFast("CREATE INDEX IF NOT EXISTS nebula_edit_history_message ON nebula_edit_history(uid, mid, seq)").stepThis().dispose();
            prepared.add(db);
        }
    }
    static boolean eligible(TLRPC.Message message, long peer) {
        return message != null && message.id > 0 && peer != 0 && peer != 777000 && !DialogObject.isEncryptedDialog(peer)
                && !(message instanceof TLRPC.TL_messageService) && !message.noforwards && message.ttl == 0 && message.ttl_period == 0
                && (message.media == null || message.media.ttl_seconds == 0);
    }
    static boolean changed(TLRPC.Message previous, TLRPC.Message incoming) {
        return incoming.edit_date > 0 && incoming.edit_date >= previous.edit_date
                && !NebulaRichText.same(NebulaRichText.copy(previous.message, previous.entities), NebulaRichText.copy(incoming.message, incoming.entities));
    }
    private static boolean protectedPeer(int account, long peer) {
        MessagesController controller = MessagesController.getInstance(account);
        if (peer < 0) { TLRPC.Chat chat = controller.getChat(-peer); return chat == null || chat.noforwards; }
        TLRPC.UserFull full = controller.getUserFull(peer);
        return full != null && (full.noforwards_my_enabled || full.noforwards_peer_enabled);
    }
    /** Called on Telegram's storage queue, before the native row is replaced. */
    public static void capture(int account, TLRPC.Message incoming) {
        if (!enabled(account) || incoming == null || incoming.edit_date <= 0) return;
        final long peer = MessageObject.getDialogId(incoming);
        if (!eligible(incoming, peer) || protectedPeer(account, peer)) return;
        SQLiteCursor cursor = null;
        NativeByteBuffer data = null;
        SQLitePreparedStatement insert = null;
        try {
            SQLiteDatabase db = MessagesStorage.getInstance(account).getDatabase();
            cursor = db.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE uid = %d AND mid = %d", peer, incoming.id));
            if (!cursor.next()) return;
            data = cursor.byteBufferValue(0);
            if (data == null) return;
            TLRPC.Message previous = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
            if (!eligible(previous, peer) || !changed(previous, incoming)) return;
            if (previous.message == null || previous.message.length() > 50_000 || previous.entities.size() > 1000) return;
            prepare(db);
            data.rewind();
            insert = db.executeFast("INSERT INTO nebula_edit_history(uid, mid, data) VALUES(?, ?, ?)");
            insert.bindLong(1, peer); insert.bindInteger(2, incoming.id); insert.bindByteBuffer(3, data); insert.step();
            db.executeFast(String.format(Locale.US, "DELETE FROM nebula_edit_history WHERE uid = %d AND mid = %d AND seq NOT IN (SELECT seq FROM nebula_edit_history WHERE uid = %d AND mid = %d ORDER BY seq DESC LIMIT %d)", peer, incoming.id, peer, incoming.id, MAX_VERSIONS)).stepThis().dispose();
            db.executeFast("DELETE FROM nebula_edit_history WHERE (uid || ':' || mid) NOT IN (SELECT uid || ':' || mid FROM nebula_edit_history GROUP BY uid, mid ORDER BY MAX(seq) DESC LIMIT " + MAX_MESSAGES + ")").stepThis().dispose();
        } catch (Exception error) { FileLog.e(error); }
        finally { if (insert != null) insert.dispose(); if (data != null) data.reuse(); if (cursor != null) cursor.dispose(); }
    }
    public static void read(int account, long peer, int mid, Consumer<List<TLRPC.Message>> result, Runnable error) {
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                SQLiteDatabase db = MessagesStorage.getInstance(account).getDatabase(); prepare(db);
                cursor = db.queryFinalized(String.format(Locale.US, "SELECT data FROM nebula_edit_history WHERE uid = %d AND mid = %d ORDER BY seq DESC LIMIT %d", peer, mid, MAX_VERSIONS));
                ArrayList<TLRPC.Message> messages = new ArrayList<>();
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) continue;
                    try { TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false); if (message != null) messages.add(message); }
                    finally { data.reuse(); }
                }
                final List<TLRPC.Message> snapshot = Collections.unmodifiableList(messages);
                AndroidUtilities.runOnUIThread(() -> result.accept(snapshot));
            } catch (Exception failure) { AndroidUtilities.runOnUIThread(error); }
            finally { if (cursor != null) cursor.dispose(); }
        });
    }
    public static void clear(int account, Long peer, Integer mid, Runnable done, Runnable error) {
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase db = MessagesStorage.getInstance(account).getDatabase(); prepare(db);
                String where = peer != null && mid != null ? String.format(Locale.US, " WHERE uid = %d AND mid = %d", peer, mid) : "";
                db.executeFast("DELETE FROM nebula_edit_history" + where).stepThis().dispose();
                AndroidUtilities.runOnUIThread(done);
            } catch (Exception failure) { AndroidUtilities.runOnUIThread(error); }
        });
    }
}
