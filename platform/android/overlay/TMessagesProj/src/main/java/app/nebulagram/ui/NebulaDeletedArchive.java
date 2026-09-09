package app.nebulagram.ui;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.security.KeyStore;
import java.util.ArrayList;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted deletion index. Content/media stay in Telegram's ordinary local cache, never re-sent. */
public final class NebulaDeletedArchive {
    private NebulaDeletedArchive() { }
    private static final String ALIAS = "NebulaGram.DeletedArchive.v1";
    /** Признак испорченного файла, а не квота хранения: см. {@link #read(long)}. */
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;
    public static long owner(int account) { return UserConfig.getInstance(account).getClientUserId(); }
    public static boolean enabled(long owner) { return owner != 0 && ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getBoolean("deleted_archive_" + owner, false); }
    public static void setEnabled(long owner, boolean enabled) { ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).edit().putBoolean("deleted_archive_" + owner, enabled).apply(); }
    public static String icon() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getString("deleted_archive_icon", "🗑"); }
    public static void setIcon(String value) {
        if (value == null || value.trim().isEmpty()) return;
        String text = value.trim();
        // Telegram's emoji ranges keep ZWJ families, flags and skin tones together,
        // including on Android 21 where android.icu is unavailable.
        ArrayList<org.telegram.messenger.Emoji.EmojiSpanRange> emojis = org.telegram.messenger.Emoji.parseEmojis(text);
        java.text.BreakIterator characters = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
        characters.setText(text);
        int end = 0;
        for (int count = 0; count < 4 && end < text.length(); count++) {
            int next = characters.following(end);
            if (next == java.text.BreakIterator.DONE) break;
            for (org.telegram.messenger.Emoji.EmojiSpanRange emoji : emojis) {
                if (emoji.start <= end && emoji.end > end) { next = emoji.end; break; }
            }
            end = next;
        }
        if (end > 0) ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0)
            .edit().putString("deleted_archive_icon", text.substring(0, end)).apply();
    }
    public static boolean saveSecret(long owner) { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).getBoolean("deleted_archive_secret_"+owner,false); }
    public static boolean saveExpiring(long owner) { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).getBoolean("deleted_archive_expiring_"+owner,false); }
    public static void setExtra(long owner, boolean secret, boolean enabled) { ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).edit().putBoolean((secret?"deleted_archive_secret_":"deleted_archive_expiring_")+owner,enabled).apply(); }
    private static File file(long owner) throws Exception {
        if (owner == 0) throw new IllegalArgumentException("No account");
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "deleted-" + owner + ".enc");
    }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    private static JSONArray read(long owner) throws Exception {
        File file = file(owner);
        if (!file.exists()) return new JSONArray();
        // Архив не ограничен ни числом сообщений, ни сроком: чистит его только
        // сам пользователь. Верхняя граница здесь — защита от повреждённого
        // файла, а не лимит хранения: индекс целиком читается в память, и без
        // потолка испорченная длина превратилась бы в OutOfMemory.
        if (file.length() > MAX_ARCHIVE_BYTES || file.length() < 28) throw new IllegalStateException("Invalid archive");
        byte[] bytes = new android.util.AtomicFile(file).readFully();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12));
        cipher.updateAAD(Long.toString(owner).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new JSONArray(new String(cipher.doFinal(bytes, 12, bytes.length - 12), java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void write(long owner, JSONArray entries) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(Long.toString(owner).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] bytes = cipher.doFinal(entries.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        android.util.AtomicFile target = new android.util.AtomicFile(file(owner));
        java.io.FileOutputStream output = null;
        try { output = target.startWrite(); output.write(cipher.getIV()); output.write(bytes); target.finishWrite(output); }
        catch (Exception e) { target.failWrite(output); throw e; }
    }
    /**
     * Приводит индекс в порядок, ничего не выбрасывая по сроку или количеству:
     * записи хранятся, пока их не удалит сам пользователь. Отбрасываются лишь
     * записи из будущего — их дата пришла бы только из испорченного файла.
     */
    private static JSONArray prune(JSONArray input) throws Exception {
        ArrayList<JSONObject> values = new ArrayList<>(); long now = System.currentTimeMillis();
        for (int i=0;i<input.length();i++) { JSONObject entry=input.getJSONObject(i);long date=entry.getLong("deletedAt");
            if(date <= now+60000) values.add(entry); }
        values.sort((a,b)->Long.compare(b.optLong("deletedAt"),a.optLong("deletedAt")));
        JSONArray result=new JSONArray(); for(JSONObject value:values) result.put(value); return result;
    }
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.Set<String>> markers = new java.util.concurrent.ConcurrentHashMap<>();
    private static String marker(long peer, int id) { return peer + ":" + id; }
    public static boolean isDeleted(int account, long peer, int id) {
        java.util.Set<String> values = markers.get(owner(account));
        return values != null && values.contains(marker(peer, id));
    }
    private static void publish(long owner, JSONArray entries) {
        java.util.Set<String> next = new java.util.HashSet<>();
        for (int i=0;i<entries.length();i++) {
            JSONObject e=entries.optJSONObject(i);
            if(e!=null && e.optBoolean("inline")) next.add(marker(e.optLong("peer"),e.optInt("id")));
        }
        markers.put(owner, java.util.Collections.unmodifiableSet(next));
    }
    public static synchronized JSONArray entries(long owner) throws Exception { return read(owner); }
    public static boolean hasError(long owner) { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).getBoolean("deleted_archive_error_"+owner,false); }
    private static void failed(long owner) { ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).edit().putBoolean("deleted_archive_error_"+owner,true).apply(); }
    // Must run on the account's storage queue after its database has opened.
    public static synchronized void initialize(int account) {
        long owner=owner(account); if(owner==0)return;
        try { JSONArray all=read(owner), kept=prune(all); purgeDifference(account,all,kept);write(owner,kept);publish(owner,kept); }
        catch(Exception e){failed(owner);}
    }
    private static boolean contains(JSONArray entries, long peer, int id) {
        for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e!=null&&e.optBoolean("inline")&&e.optLong("peer")==peer&&e.optInt("id")==id)return true;}
        return false;
    }
    private static void purgeDifference(int account, JSONArray before, JSONArray after) throws Exception {
        java.util.Map<Long,ArrayList<Integer>> removed=new java.util.HashMap<>();
        for(int i=0;i<before.length();i++) {
            JSONObject e=before.getJSONObject(i);long peer=e.getLong("peer");int id=e.getInt("id");
            if(e.optBoolean("inline")&&!contains(after,peer,id))removed.computeIfAbsent(peer,k->new ArrayList<>()).add(id);
        }
        for(java.util.Map.Entry<Long,ArrayList<Integer>> group:removed.entrySet()) {
            long peer=group.getKey();ArrayList<Integer> ids=group.getValue();
            MessagesStorage storage=MessagesStorage.getInstance(account);
            // Cached media may be shared by other messages. Never delete those shared files here.
            ArrayList<Long> dialogs=storage.markMessagesAsDeleted(peer,ids,false,false,0,0);
            long channel = peer<0 && org.telegram.messenger.ChatObject.isChannel(org.telegram.messenger.MessagesController.getInstance(account).getChat(-peer)) ? -peer : 0;
            storage.updateDialogsWithDeletedMessages(peer, channel, ids, dialogs);
            org.telegram.messenger.AndroidUtilities.runOnUIThread(()->{
                TLRPC.Chat chat=org.telegram.messenger.MessagesController.getInstance(account).getChat(-peer);
                long notificationChannel=peer<0&&org.telegram.messenger.ChatObject.isChannel(chat)?-peer:0;
                org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.messagesDeleted,ids,notificationChannel,false);
            });
        }
    }
    public static void clearAsync(int account, long peer, Runnable done, Runnable error) {
        final long expected=owner(account);
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(()->{
            try { synchronized(NebulaDeletedArchive.class) {
                if(owner(account)!=expected)throw new IllegalStateException("Account changed");
                JSONArray all=read(expected), kept=new JSONArray();
                for(int i=0;i<all.length();i++){JSONObject entry=all.getJSONObject(i);if(peer!=0&&entry.optLong("peer")!=peer)kept.put(entry);}
                purgeDifference(account,all,kept);
                write(expected,kept);publish(expected,kept);
                ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).edit().remove("deleted_archive_error_"+expected).apply();
            }org.telegram.messenger.AndroidUtilities.runOnUIThread(done); }
            catch(Exception e){org.telegram.messenger.AndroidUtilities.runOnUIThread(error);}
        });
    }
    private static boolean protectedPeer(int account,long peer) {
        if(DialogObject.isEncryptedDialog(peer))return false;
        org.telegram.messenger.MessagesController controller=org.telegram.messenger.MessagesController.getInstance(account);
        if(peer<0){TLRPC.Chat chat=controller.getChat(-peer);return chat==null||chat.noforwards;}
        TLRPC.UserFull full=controller.getUserFull(peer);
        return full!=null&&(full.noforwards_my_enabled||full.noforwards_peer_enabled);
    }
    // Before both remote-deletion UI delivery and database deletion, on the storage queue.
    // Returns only IDs which Telegram should actually remove locally.
    public static synchronized ArrayList<Integer> retain(int account, long dialog, ArrayList<Integer> ids) {
        long owner=owner(account);ArrayList<Integer> result=new ArrayList<>(ids);
        if(owner==0||ids.isEmpty())return result;
        try {
            JSONArray before=read(owner), entries=prune(before);
            java.util.Map<Long,ArrayList<TLRPC.Message>> changed=new java.util.HashMap<>();
            if(enabled(owner))for(int offset=0;offset<ids.size();offset+=100) {
                ArrayList<Integer> batch=new ArrayList<>(ids.subList(offset,Math.min(ids.size(),offset+100)));
                String condition=dialog==0?"is_channel = 0":"uid = "+dialog;
                SQLiteCursor cursor=MessagesStorage.getInstance(account).getDatabase().queryFinalized("SELECT uid,data,mid,read_state,ttl FROM messages_v2 WHERE mid IN("+TextUtils.join(",",batch)+") AND "+condition);
                try {while(cursor.next()) {
                    long peer=cursor.longValue(0);int id=cursor.intValue(2), readState=cursor.intValue(3);
                    if(DialogObject.isEncryptedDialog(peer)&&!saveSecret(owner)||peer==777000||peer==owner||protectedPeer(account,peer))continue;
                    NativeByteBuffer data=cursor.byteBufferValue(1);if(data==null)continue;
                    TLRPC.Message message;
                    try {message=TLRPC.Message.TLdeserialize(data,data.readInt32(false),false);if(message!=null)message.readAttachPath(data,owner);}finally{data.reuse();}
                    if(message==null)continue;
                    boolean secret=DialogObject.isEncryptedDialog(peer);
                    boolean expiring=message.ttl_period>0||cursor.intValue(4)>0||message.media!=null&&message.media.ttl_seconds>0;
                    if(!NebulaRetentionPolicy.allowed(enabled(owner),secret,expiring,saveSecret(owner),saveExpiring(owner),message.noforwards,!message.out,message instanceof TLRPC.TL_messageService,id!=0&&(id>0||secret)))continue;
                    if(contains(entries,peer,id))continue;
                    message.id=id;message.dialog_id=peer;message.unread=(readState&1)==0;message.media_unread=(readState&2)==0;
                    entries.put(new JSONObject().put("peer",peer).put("id",id).put("scope",dialog).put("inline",true).put("timestamp",message.date)
                        .put("deletedAt",System.currentTimeMillis()).put("text",message.message==null?"":message.message.substring(0,Math.min(message.message.length(),4096))));
                    changed.computeIfAbsent(peer,k->new ArrayList<>()).add(message);
                }}finally{cursor.dispose();}
            }
            entries=prune(entries);
            write(owner,entries); // Fail closed: do not retain new messages if the index cannot be persisted.
            purgeDifference(account,before,entries);publish(owner,entries);
            for(int i=0;i<entries.length();i++) {JSONObject e=entries.getJSONObject(i);if(e.optBoolean("inline")&&e.optLong("scope")==dialog)result.remove(Integer.valueOf(e.optInt("id")));}
            for(java.util.Map.Entry<Long,ArrayList<TLRPC.Message>> group:changed.entrySet()) {
                long peer=group.getKey();ArrayList<TLRPC.Message> retained=new ArrayList<>();
                for(TLRPC.Message message:group.getValue())if(contains(entries,peer,message.id))retained.add(message);
                if(retained.isEmpty())continue;
                MessagesStorage storage=MessagesStorage.getInstance(account);
                for(TLRPC.Message message:retained) {
                    // A retained local copy must no longer use the expired-media viewer/countdown.
                    message.ttl=0;message.ttl_period=0;if(message.media!=null)message.media.ttl_seconds=0;
                    NativeByteBuffer serialized=new NativeByteBuffer(message.getObjectSize());
                    org.telegram.SQLite.SQLitePreparedStatement update=null;
                    try {
                        message.serializeToStream(serialized);
                        update=storage.getDatabase().executeFast("UPDATE messages_v2 SET data = ?, ttl = 0 WHERE uid = ? AND mid = ?");
                        update.bindByteBuffer(1,serialized);update.bindLong(2,peer);update.bindInteger(3,message.id);update.step();
                    }finally{if(update!=null)update.dispose();serialized.reuse();}
                }
                org.telegram.messenger.AndroidUtilities.runOnUIThread(()->{
                    ArrayList<org.telegram.messenger.MessageObject> objects=new ArrayList<>();
                    for(TLRPC.Message message:retained)objects.add(new org.telegram.messenger.MessageObject(account,message,true,true));
                    org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.replaceMessagesObjects,peer,objects);
                });
            }
        }catch(Exception e){failed(owner);}
        return result;
    }
}
