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
    /** Есть ли что защищать: файл архива заведён только после включения. */
    private static boolean present(long owner) {
        try { return file(owner).exists(); } catch (Exception e) { return false; }
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
    // One snapshot per account, under this class's monitor. A single slot made
    // every deletion event of the other account decrypt and parse its whole
    // archive again on the storage queue, holding the lock both accounts share.
    // Published snapshots are never mutated in place; pending writes stay readable until committed.
    private static final java.util.Map<Long, Snapshot> cachedSnapshots = new java.util.HashMap<>();
    // Message deletion runs on Telegram's single storage queue. Re-encrypting and
    // atomically replacing the entire archive there can stall chat sync for the
    // full duration of a large archive write. Keep only the newest pending
    // snapshot per account and serialize file writes on a background worker.
    private static final java.util.concurrent.ExecutorService ARCHIVE_WRITER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "NebulaDeletedArchive");
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
    private static final java.util.Map<Long, Snapshot> pendingWrites = new java.util.HashMap<>();
    // A queued snapshot must remain readable even after the worker takes it.
    // Otherwise switching accounts can reload an older file and lose newer entries.
    private static final java.util.Map<Long, Snapshot> uncommittedSnapshots = new java.util.HashMap<>();
    private static final java.util.Set<Long> writingOwners = new java.util.HashSet<>();
    private static final java.util.Map<Long, ArrayList<Runnable>> writeDone = new java.util.HashMap<>();
    private static final java.util.Map<Long, ArrayList<Runnable>> writeError = new java.util.HashMap<>();
    private static final class Snapshot {
        final JSONArray entries;
        final java.util.Set<String> keys = new java.util.HashSet<>();
        final java.util.Map<Long, java.util.Set<Integer>> scopes = new java.util.HashMap<>();
        Snapshot(JSONArray entries) throws Exception {
            this.entries = entries;
            index(0);
        }
        /**
         * Следующий снимок после дописывания в конец. Готовый индекс копируется
         * целиком, а разбираются только новые записи: раньше каждое удаление,
         * попавшее в архив, заново читало поля всех записей на очереди базы.
         */
        Snapshot(JSONArray entries, Snapshot previous) throws Exception {
            this.entries = entries;
            keys.addAll(previous.keys);
            for (java.util.Map.Entry<Long, java.util.Set<Integer>> scope : previous.scopes.entrySet())
                scopes.put(scope.getKey(), new java.util.HashSet<>(scope.getValue()));
            index(previous.entries.length());
        }
        private void index(int from) throws Exception {
            for (int i = from; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                if (!e.optBoolean("inline")) continue;
                keys.add(marker(e.optLong("peer"), e.optInt("id")));
                scopes.computeIfAbsent(e.optLong("scope"), k -> new java.util.HashSet<>()).add(e.optInt("id"));
                scopes.computeIfAbsent(e.optLong("peer"), k -> new java.util.HashSet<>()).add(e.optInt("id"));
                if (e.optLong("peer") > 0 && !DialogObject.isEncryptedDialog(e.optLong("peer"))) {
                    scopes.computeIfAbsent(0L, k -> new java.util.HashSet<>()).add(e.optInt("id"));
                }
            }
        }
        void filter(long scope, ArrayList<Integer> ids) {
            java.util.Set<Integer> retained = scopes.get(scope);
            if (retained != null) ids.removeAll(retained);
        }
    }
    private static Snapshot snapshot(long owner) throws Exception {
        synchronized (pendingWrites) {
            Snapshot pending = uncommittedSnapshots.get(owner);
            if (pending != null) return pending;
        }
        Snapshot cached = cachedSnapshots.get(owner);
        if (cached != null) return cached;
        Snapshot loaded = new Snapshot(read(owner));
        cachedSnapshots.put(owner, loaded);
        return loaded;
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
        Snapshot next = new Snapshot(entries);
        writeFile(owner, entries);
        cachedSnapshots.put(owner, next);
    }
    private static void writeFile(long owner, JSONArray entries) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(Long.toString(owner).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] bytes = cipher.doFinal(entries.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        android.util.AtomicFile target = new android.util.AtomicFile(file(owner));
        java.io.FileOutputStream output = null;
        try { output = target.startWrite(); output.write(cipher.getIV()); output.write(bytes); target.finishWrite(output); }
        catch (Exception e) { target.failWrite(output); throw e; }
    }
    private static void writeAsync(long owner, Snapshot snapshot) {
        writeAsync(owner, snapshot, null, null);
    }
    private static void writeAsync(long owner, Snapshot snapshot, Runnable done, Runnable error) {
        synchronized (pendingWrites) {
            pendingWrites.put(owner, snapshot);
            uncommittedSnapshots.put(owner, snapshot);
            if (done != null) writeDone.computeIfAbsent(owner, k -> new ArrayList<>()).add(done);
            if (error != null) writeError.computeIfAbsent(owner, k -> new ArrayList<>()).add(error);
            if (!writingOwners.add(owner)) return;
        }
        ARCHIVE_WRITER.execute(() -> {
            boolean persisted = false;
            while (true) {
                Snapshot next;
                ArrayList<Runnable> completed = null, failed = null;
                synchronized (pendingWrites) {
                    next = pendingWrites.remove(owner);
                    if (next == null) {
                        writingOwners.remove(owner);
                        completed = writeDone.remove(owner);
                        failed = writeError.remove(owner);
                    }
                }
                if (next == null) {
                    ArrayList<Runnable> callbacks = persisted ? completed : failed;
                    if (callbacks != null) for (Runnable callback : callbacks)
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(callback);
                    return;
                }
                try {
                    writeFile(owner, next.entries);
                    synchronized (pendingWrites) {
                        if (uncommittedSnapshots.get(owner) == next) uncommittedSnapshots.remove(owner);
                    }
                    persisted = true;
                    ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0)
                            .edit().remove("deleted_archive_error_" + owner).apply();
                } catch (Exception e) {
                    persisted = false;
                    failed(owner);
                }
            }
        });
    }
    /** Keep persisted order on the sync queue; sorting is only needed by the archive screen. */
    private static JSONArray copyEntries(JSONArray input) throws Exception {
        // Preserve all entries, including after clock/timezone corrections. Retention has no TTL.
        // Clone the array before appending, so the pre-write snapshot remains unchanged on failure.
        JSONArray result = new JSONArray();
        for (int i = 0; i < input.length(); i++) result.put(input.getJSONObject(i));
        return result;
    }
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.Set<String>> markers = new java.util.concurrent.ConcurrentHashMap<>();
    private static String marker(long peer, int id) { return peer + ":" + id; }
    public static boolean isDeleted(int account, long peer, int id) {
        java.util.Set<String> values = markers.get(owner(account));
        return values != null && values.contains(marker(peer, id));
    }
    private static void publish(long owner, Snapshot snapshot) {
        markers.put(owner, java.util.Collections.unmodifiableSet(snapshot.keys));
    }
    public static JSONArray entries(long owner) throws Exception {
        final JSONArray stored;
        synchronized (NebulaDeletedArchive.class) { stored = snapshot(owner).entries; }
        ArrayList<JSONObject> values = new ArrayList<>();
        for (int i = 0; i < stored.length(); i++) values.add(stored.getJSONObject(i));
        values.sort((a,b) -> Long.compare(b.optLong("deletedAt"), a.optLong("deletedAt")));
        // Do not let callers mutate the committed in-memory snapshot.
        JSONArray result = new JSONArray();
        for (JSONObject value : values) result.put(new JSONObject(value.toString()));
        return result;
    }
    public static boolean hasError(long owner) { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).getBoolean("deleted_archive_error_"+owner,false); }
    private static void failed(long owner) { ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).edit().putBoolean("deleted_archive_error_"+owner,true).apply(); }
    // Чтение и расшифровка всего архива идут на своём потоке. Раньше они шли
    // прямо здесь, на очереди базы сразу после её открытия: с большим архивом
    // список чатов и сообщения ждали, пока он целиком расшифруется и
    // разберётся. Метки «удалено» в чате появятся, как только поток закончит;
    // удаление, пришедшее раньше, дождётся того же снимка под общим замком и не
    // потеряет ни одного сохранённого сообщения.
    private static final java.util.concurrent.ExecutorService ARCHIVE_LOADER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "NebulaDeletedArchiveLoad");
                thread.setPriority(Thread.MIN_PRIORITY + 1);
                return thread;
            });
    // Called on the account's storage queue after its database has opened; returns at once.
    public static void initialize(int account) {
        long owner=owner(account); if(owner==0)return;
        // Выходим до первого обращения к хранилищу ключей. Этот метод вызывает
        // MessagesStorage сразу после открытия базы, на её же очереди: пока он
        // не вернётся, база не отвечает ни на один запрос. Для тех, кто архив
        // не включал, здесь была пара операций AndroidKeyStore и запись файла
        // на каждый запуск — и список чатов ждал их.
        if(!enabled(owner)&&!present(owner))return;
        ARCHIVE_LOADER.execute(()->{
            synchronized (NebulaDeletedArchive.class) {
                try { publish(owner, snapshot(owner)); }
                catch(Exception e){failed(owner);}
            }
        });
    }
    // Build membership once per batch, not once for every archived message.
    private static java.util.Set<String> entryKeys(JSONArray entries) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i);
            if (e != null && e.optBoolean("inline")) keys.add(marker(e.optLong("peer"), e.optInt("id")));
        }
        return keys;
    }
    private static java.util.Map<Long,ArrayList<Integer>> removedEntries(JSONArray before, JSONArray after) throws Exception {
        java.util.Set<String> kept = entryKeys(after);
        java.util.Map<Long,ArrayList<Integer>> removed = new java.util.HashMap<>();
        for (int i = 0; i < before.length(); i++) {
            JSONObject e = before.getJSONObject(i);
            long peer = e.getLong("peer"); int id = e.getInt("id");
            if (e.optBoolean("inline") && !kept.contains(marker(peer, id)))
                removed.computeIfAbsent(peer, k -> new ArrayList<>()).add(id);
        }
        return removed;
    }
    private static void purgeDifference(int account, JSONArray before, JSONArray after) throws Exception {
        java.util.Map<Long,ArrayList<Integer>> removed = removedEntries(before, after);
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
                JSONArray all=snapshot(expected).entries, kept=new JSONArray();
                for(int i=0;i<all.length();i++){JSONObject entry=all.getJSONObject(i);if(peer!=0&&entry.optLong("peer")!=peer)kept.put(entry);}
                purgeDifference(account,all,kept);
                Snapshot next = new Snapshot(kept);
                cachedSnapshots.put(expected, next);
                publish(expected,next);
                ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0).edit().remove("deleted_archive_error_"+expected).apply();
                writeAsync(expected, next, done, error);
            } }
            catch(Exception e){org.telegram.messenger.AndroidUtilities.runOnUIThread(error);}
        });
    }
    private static boolean protectedPeer(int account,long peer) {
        if(DialogObject.isEncryptedDialog(peer))return false;
        org.telegram.messenger.MessagesController controller=org.telegram.messenger.MessagesController.getInstance(account);
        if(peer<0){
            TLRPC.Chat chat=controller.getChat(-peer);
            // A group or channel not yet loaded into memory is not "protected":
            // read it from the database (we are on its queue) instead of
            // silently dropping every deleted message of that chat.
            if(chat==null)chat=MessagesStorage.getInstance(account).getChat(-peer);
            return chat!=null&&chat.noforwards;
        }
        TLRPC.UserFull full=controller.getUserFull(peer);
        return full!=null&&(full.noforwards_my_enabled||full.noforwards_peer_enabled);
    }
    // Before both remote-deletion UI delivery and database deletion, on the storage queue.
    // Returns only IDs which Telegram should actually remove locally.
    public static ArrayList<Integer> retain(int account, long dialog, ArrayList<Integer> ids) {
        long owner=owner(account);ArrayList<Integer> result=new ArrayList<>(ids);
        if(owner==0||ids.isEmpty())return result;
        // Тот же выход, что и в initialize, и по той же причине: это тоже
        // очередь базы, а события удаления идут потоком — их шлёт каждый канал.
        // Выключенный архив без файла не должен стоить ни одной операции с
        // ключом. Если файл есть, войти придётся: даже с выключенным
        // сохранением уже сохранённые сообщения нельзя отдавать на удаление.
        if(!enabled(owner)&&!present(owner))return result;
        // Замок берём только здесь: выключенный архив не должен ждать, пока
        // архив другого аккаунта расшифровывается.
        synchronized (NebulaDeletedArchive.class) {
        int asked=result.size(), found=0, kept=0;
        int[] skipped=new int[SKIP_REASONS];
        try {
            Snapshot before = snapshot(owner);
            before.filter(dialog,result);
            if (result.isEmpty()) return result;
            JSONArray entries = null; // No whole-index copy/read/sort for an irrelevant deletion event.
            java.util.Set<String> addedKeys = new java.util.HashSet<>();
            java.util.Map<Long,ArrayList<TLRPC.Message>> changed=new java.util.HashMap<>();
            final boolean capture = enabled(owner), keepSecret = saveSecret(owner), keepExpiring = saveExpiring(owner);
            asked=result.size();
            if(capture)for(int offset=0;offset<result.size();offset+=100) {
                ArrayList<Integer> batch=new ArrayList<>(result.subList(offset,Math.min(result.size(),offset+100)));
                String condition=dialog==0?"is_channel = 0":"uid = "+dialog;
                SQLiteCursor cursor=MessagesStorage.getInstance(account).getDatabase().queryFinalized("SELECT uid,data,mid,read_state,ttl FROM messages_v2 WHERE mid IN("+TextUtils.join(",",batch)+") AND "+condition);
                try {while(cursor.next()) {
                    found++;
                    long peer=cursor.longValue(0);int id=cursor.intValue(2), readState=cursor.intValue(3);
                    if(DialogObject.isEncryptedDialog(peer)&&!keepSecret||peer==777000||protectedPeer(account,peer)){skipped[SKIP_PROTECTED]++;continue;}
                    // Где сохранять — выбор пользователя: личные чаты, группы,
                    // каналы и боты включаются по отдельности.
                    if(!NebulaDeletedStyle.retains(account,peer)){skipped[SKIP_SCOPE]++;continue;}
                    if(before.keys.contains(marker(peer,id)))continue;
                    NativeByteBuffer data=cursor.byteBufferValue(1);if(data==null){skipped[SKIP_UNREADABLE]++;continue;}
                    TLRPC.Message message;
                    try {message=TLRPC.Message.TLdeserialize(data,data.readInt32(false),false);if(message!=null)message.readAttachPath(data,owner);}finally{data.reuse();}
                    if(message==null){skipped[SKIP_UNREADABLE]++;continue;}
                    message.ttl=Math.max(message.ttl,cursor.intValue(4));
                    boolean secret=DialogObject.isEncryptedDialog(peer);
                    boolean expiring=message.ttl_period>0||cursor.intValue(4)>0||message.media!=null&&message.media.ttl_seconds>0;
                    if(!NebulaRetentionPolicy.allowed(capture,secret,expiring,keepSecret,keepExpiring,message.noforwards,NebulaRetentionPolicy.receivedOrSaved(!message.out,peer,owner),message instanceof TLRPC.TL_messageService,id!=0&&(id>0||secret))){skipped[SKIP_POLICY]++;continue;}
                    if(!addedKeys.add(marker(peer,id)))continue;
                    kept++;
                    if(entries==null)entries=copyEntries(before.entries);
                    message.id=id;message.dialog_id=peer;message.unread=(readState&1)==0;message.media_unread=(readState&2)==0;
                    entries.put(new JSONObject().put("peer",peer).put("id",id).put("scope",dialog).put("inline",true).put("timestamp",message.date)
                        .put("deletedAt",System.currentTimeMillis()).put("text",message.message==null?"":message.message.substring(0,Math.min(message.message.length(),4096))));
                    changed.computeIfAbsent(peer,k->new ArrayList<>()).add(message);
                }}finally{cursor.dispose();}
            }
            // Commit the in-memory index before allowing Telegram to delete IDs.
            // Ciphering and the AtomicFile replacement are coalesced off the sync
            // queue, so an archive's size cannot hold up loading other chats.
            if(entries!=null) {
                Snapshot next = new Snapshot(entries, before);
                cachedSnapshots.put(owner, next);
                writeAsync(owner, next);
            }
            Snapshot committed = snapshot(owner);
            if(entries!=null || !markers.containsKey(owner)) publish(owner,committed);
            committed.filter(dialog,result); // O(incoming IDs), not O(entire archive) per update.
            for(java.util.Map.Entry<Long,ArrayList<TLRPC.Message>> group:changed.entrySet()) {
                long peer=group.getKey();ArrayList<TLRPC.Message> retained=new ArrayList<>();
                for(TLRPC.Message message:group.getValue())if(committed.keys.contains(marker(peer,message.id)))retained.add(message);
                if(retained.isEmpty())continue;
                MessagesStorage storage=MessagesStorage.getInstance(account);
                org.telegram.SQLite.SQLitePreparedStatement update=null;
                try {
                for(TLRPC.Message message:retained) {
                    // Ordinary text/media already exist in messages_v2. Only timers need a rewrite.
                    if (message.ttl==0 && message.ttl_period==0 && (message.media==null || message.media.ttl_seconds==0)) continue;
                    // A retained local copy must no longer use the expired-media viewer/countdown.
                    message.ttl=0;message.ttl_period=0;if(message.media!=null)message.media.ttl_seconds=0;
                    NativeByteBuffer serialized=new NativeByteBuffer(message.getObjectSize());
                    try {
                        message.serializeToStream(serialized);
                        if(update==null)update=storage.getDatabase().executeFast("UPDATE messages_v2 SET data = ?, ttl = 0 WHERE uid = ? AND mid = ?");
                        else update.requery();
                        update.bindByteBuffer(1,serialized);update.bindLong(2,peer);update.bindInteger(3,message.id);update.step();
                    }finally{serialized.reuse();}
                }
                } finally {if(update!=null)update.dispose();}
                org.telegram.messenger.AndroidUtilities.runOnUIThread(()->{
                    ArrayList<org.telegram.messenger.MessageObject> objects=new ArrayList<>();
                    for(TLRPC.Message message:retained)objects.add(new org.telegram.messenger.MessageObject(account,message,true,true));
                    org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.replaceMessagesObjects,peer,objects);
                });
            }
        }catch(Exception e){failed(owner);skipped[SKIP_ERROR]++;}
        diagnose(account,dialog,asked,found,kept,skipped);
        return result;
        }
    }

    private static final int SKIP_PROTECTED=0, SKIP_SCOPE=1, SKIP_POLICY=2, SKIP_UNREADABLE=3, SKIP_ERROR=4, SKIP_REASONS=5;
    /**
     * Почему удалённое не попало в архив. Без устройства это не воспроизвести,
     * поэтому пишем короткую сводку в журнал Telegram (только когда журнал
     * включён в отладочном меню). Текст сообщений туда не попадает: одни счётчики.
     * notInDb — сообщение не было загружено на это устройство до удаления.
     */
    private static void diagnose(int account,long dialog,int asked,int found,int kept,int[] skipped){
        if(!org.telegram.messenger.BuildVars.LOGS_ENABLED||asked==kept)return;
        org.telegram.messenger.FileLog.d("NebulaDeletedArchive: account="+account+" scope="+(dialog==0?"private/groups":dialog<0?"channel":"dialog")
            +" asked="+asked+" notInDb="+(asked-found)+" kept="+kept+" protected="+skipped[SKIP_PROTECTED]+" scopeOff="+skipped[SKIP_SCOPE]
            +" policy="+skipped[SKIP_POLICY]+" unreadable="+skipped[SKIP_UNREADABLE]+" error="+skipped[SKIP_ERROR]);
    }

    /** Есть ли у аккаунта архив, которому нужны события удаления. Дёшево: настройка и наличие файла. */
    public static boolean active(int account) {
        long owner=owner(account);
        return owner!=0&&(enabled(owner)||present(owner));
    }

    /**
     * Пачка обновлений с удалениями сначала проходит архив на очереди базы, и
     * только потом её видит интерфейс. Без удалений или без архива задача
     * выполняется сразу, как у Telegram, и не ждёт очередь базы.
     */
    public static void beforeDeletes(int account, boolean hasDeletes, Runnable task) {
        if (hasDeletes && active(account)) MessagesStorage.getInstance(account).getStorageQueue().postRunnable(task);
        else task.run();
    }

    /**
     * Условие для массового стирания сообщений чата (перезагрузка канала, сброс
     * списка чатов): " AND mid NOT IN(...)" с сохранёнными в архиве сообщениями
     * этого чата или пустая строка. Вызывается с очереди базы.
     */
    public static String keepClause(int account, long peer) {
        if(!active(account))return "";
        long owner=owner(account);
        java.util.Set<Integer> ids;
        synchronized (NebulaDeletedArchive.class) {
            try { java.util.Set<Integer> scoped=snapshot(owner).scopes.get(peer); ids=scoped==null?null:new java.util.TreeSet<>(scoped); }
            catch(Exception e){failed(owner);return "";}
        }
        if(ids==null||ids.isEmpty())return "";
        return " AND mid NOT IN("+TextUtils.join(",",ids)+")";
    }
}
