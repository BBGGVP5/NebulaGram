package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.util.Base64;
import androidx.core.content.FileProvider;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Executors;

/** Uses the signed-in Telegram account and the owner's public channel; no bot token. */
public final class NebulaTelegramUpdates implements NotificationCenter.NotificationCenterDelegate {
    private static final Map<Integer, NebulaTelegramUpdates> INSTANCES = new HashMap<>();
    private static final long INTERVAL = 6 * 60 * 60 * 1000L;
    public static NebulaTelegramUpdates get(int account) {
        NebulaTelegramUpdates instance = INSTANCES.get(account);
        if (instance == null) { instance = new NebulaTelegramUpdates(account); INSTANCES.put(account, instance); }
        return instance;
    }
    private final int account;
    private final SharedPreferences prefs;
    private final List<Runnable> listeners = new ArrayList<>(), completions = new ArrayList<>();
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private TLRPC.Message message, found, changelog;
    private TLRPC.InputPeer peer;
    private int request, generation;
    private boolean cancelledDownload;
    private Runnable timeout;
    public boolean checking, verifying;
    public String error;

    private NebulaTelegramUpdates(int account) {
        this.account = account;
        prefs = ApplicationLoader.applicationContext.getSharedPreferences("nebula_updates_" + account, 0);
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (prefs.getLong("owner", 0) != owner) prefs.edit().clear().putLong("owner", owner).apply();
        try {
            String cached = prefs.getString("message", "");
            if (!cached.isEmpty()) {
                SerializedData data = new SerializedData(Base64.decode(cached, Base64.NO_WRAP));
                message = TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);
                data.cleanup();
                if (release(message) == null) message = null;
                String caption = prefs.getString("changelog", "");
                if (message != null && !caption.isEmpty()) {
                    SerializedData notes = new SerializedData(Base64.decode(caption, Base64.NO_WRAP));
                    changelog = TLRPC.Message.TLdeserialize(notes, notes.readInt32(true), true); notes.cleanup();
                    if (changelog == null || changelog.peer_id == null || changelog.peer_id.channel_id != NebulaRelease.CHANNEL_ID) changelog = null;
                }
            }
        } catch (Exception ignored) { message = null; changelog = null; }
        for (int event : new int[]{NotificationCenter.fileLoaded, NotificationCenter.fileLoadFailed,
                NotificationCenter.fileLoadProgressChanged, NotificationCenter.appDidLogout}) {
            NotificationCenter.getInstance(account).addObserver(this, event);
        }
    }

    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    public boolean automatic() { return prefs.getBoolean("automatic", true); }
    public void setAutomatic(boolean value) { prefs.edit().putBoolean("automatic", value).apply(); }
    public long lastCheck() { return prefs.getLong("last_check", 0); }
    public NebulaRelease release() { return release(message); }
    public TLRPC.Message post() { return changelog != null ? changelog : message; }
    public TLRPC.Document document() { return message == null ? null : MessageObject.getDocument(message); }
    public boolean available() { NebulaRelease r = release(); return r != null && r.versionCode > installedCode(); }
    public static int installedCode() {
        try { Context c = ApplicationLoader.applicationContext; return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; }
        catch (Exception ignored) { return Integer.MAX_VALUE; }
    }
    public static String installedVersion() {
        try { Context c = ApplicationLoader.applicationContext; return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "NebulaGram"; }
    }
    private static NebulaRelease release(TLRPC.Message m) {
        if (m == null || m.peer_id == null || m.peer_id.channel_id != NebulaRelease.CHANNEL_ID) return null;
        TLRPC.Document doc = MessageObject.getDocument(m);
        if (doc == null || doc.size <= 0 || doc.size > 1500000000L) return null;
        NebulaRelease r = NebulaRelease.parse(FileLoader.getDocumentFileName(doc));
        return r != null && r.compatible(Build.SUPPORTED_ABIS) ? r : null;
    }
    public String postUrl() { return "https://t.me/" + NebulaRelease.CHANNEL + (post() != null ? "/" + post().id : ""); }

    public void check(boolean force, Runnable done) {
        if (checking) { if (done != null) completions.add(done); return; }
        long now = System.currentTimeMillis();
        long elapsed = now - lastCheck(), attempted = now - prefs.getLong("last_attempt", 0);
        if (!force && (!automatic() || elapsed >= 0 && elapsed < INTERVAL || attempted >= 0 && attempted < 15 * 60 * 1000L) || downloading() || verifying) {
            if (done != null) done.run(); return;
        }
        if (done != null) completions.add(done);
        if (!UserConfig.getInstance(account).isClientActivated()) { finish(text("Войдите в аккаунт Telegram, чтобы проверить канал.", "Sign in to Telegram to check the channel.")); return; }
        checking = true; error = null; found = null;
        prefs.edit().putLong("last_attempt", now).putLong("owner", UserConfig.getInstance(account).getClientUserId()).apply(); changed();
        int token = ++generation;
        timeout = () -> {
            if (token != generation || !checking) return;
            generation++; ConnectionsManager.getInstance(account).cancelRequest(request, true);
            finish(text("Не удалось связаться с Telegram. Повторите проверку.", "Could not connect to Telegram. Try again."));
        };
        AndroidUtilities.runOnUIThread(timeout, 45000);
        TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername(); resolve.username = NebulaRelease.CHANNEL;
        request = ConnectionsManager.getInstance(account).sendRequest(resolve, (response, failure) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != generation || !checking) return;
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer)) { finish(text("Канал обновлений недоступен. Попробуйте позже.", "The update channel is unavailable. Try later.")); return; }
            TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
            TLRPC.Chat channel = null;
            for (TLRPC.Chat chat : resolved.chats) if (chat.id == NebulaRelease.CHANNEL_ID && chat.broadcast) channel = chat;
            if (channel == null || resolved.peer == null || resolved.peer.channel_id != NebulaRelease.CHANNEL_ID) {
                finish(text("Не удалось подтвердить канал NebulaGram.", "Could not verify the NebulaGram channel.")); return;
            }
            MessagesController.getInstance(account).putUsers(resolved.users, false);
            MessagesController.getInstance(account).putChats(resolved.chats, false);
            MessagesStorage.getInstance(account).putUsersAndChats(resolved.users, resolved.chats, true, true);
            TLRPC.TL_inputPeerChannel input = new TLRPC.TL_inputPeerChannel(); input.channel_id = channel.id; input.access_hash = channel.access_hash;
            peer = input; search(token, 0, 0);
        }));
    }

    private void search(int token, int offset, int page) {
        TLRPC.TL_messages_search query = new TLRPC.TL_messages_search();
        query.peer = peer; query.q = ""; query.filter = new TLRPC.TL_inputMessagesFilterDocument();
        query.limit = 100; query.offset_id = offset; query.saved_reaction = null;
        request = ConnectionsManager.getInstance(account).sendRequest(query, (response, failure) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != generation || !checking) return;
            if (!(response instanceof TLRPC.messages_Messages)) { finish(text("Не удалось получить посты с обновлениями.", "Could not load update posts.")); return; }
            TLRPC.messages_Messages history = (TLRPC.messages_Messages) response;
            int lastId = 0;
            for (TLRPC.Message post : history.messages) {
                lastId = post.id;
                NebulaRelease r = release(post);
                if (r != null && r.preferredTo(release(found), Build.SUPPORTED_ABIS)) found = post;
            }
            if (history.messages.size() == 100 && page < 4 && lastId > 0 && lastId != offset) { search(token, lastId, page + 1); return; }
            if (found != null && found.grouped_id != 0) loadAlbumCaption(token);
            else resolveCaption(token, found);
        }));
    }

    private void loadAlbumCaption(int token) {
        TLRPC.TL_messages_getHistory query = new TLRPC.TL_messages_getHistory();
        query.peer = peer; query.offset_id = (int) Math.min(Integer.MAX_VALUE, (long) found.id + 11); query.limit = 21;
        request = ConnectionsManager.getInstance(account).sendRequest(query, (response, failure) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != generation || !checking) return;
            if (!(response instanceof TLRPC.messages_Messages)) { finish(text("Не удалось получить подпись альбома. Повторите проверку.", "Could not load the album caption. Check again.")); return; }
            resolveCaption(token, NebulaChangelog.albumCaption(found, ((TLRPC.messages_Messages) response).messages));
        }));
    }

    private void resolveCaption(int token, TLRPC.Message caption) {
        int id = NebulaChangelog.reference(caption);
        if (id == 0 || caption != null && id == caption.id) { commitFound(caption); return; }
        TLRPC.TL_channels_getMessages query = new TLRPC.TL_channels_getMessages();
        TLRPC.TL_inputChannel channel = new TLRPC.TL_inputChannel(); channel.channel_id = NebulaRelease.CHANNEL_ID; channel.access_hash = peer.access_hash;
        query.channel = channel;
        query.id.add(id);
        request = ConnectionsManager.getInstance(account).sendRequest(query, (response, failure) -> AndroidUtilities.runOnUIThread(() -> {
            if (token != generation || !checking) return;
            if (response instanceof TLRPC.messages_Messages) for (TLRPC.Message post : ((TLRPC.messages_Messages) response).messages) {
                if (post.id == id && post.peer_id != null && post.peer_id.channel_id == NebulaRelease.CHANNEL_ID && post.message != null && !post.message.isEmpty()) {
                    commitFound(post); return;
                }
            }
            finish(text("Пост со списком изменений недоступен. Проверьте ссылку в подписи к APK.", "The changelog post is unavailable. Check the link in the APK caption."));
        }));
    }

    private void commitFound(TLRPC.Message caption) {
        message = found; changelog = caption;
        SharedPreferences.Editor edit = prefs.edit().putLong("last_check", System.currentTimeMillis());
        storeMessage(edit, "message", message); storeMessage(edit, "changelog", changelog);
        edit.apply(); finish(null);
    }
    private static void storeMessage(SharedPreferences.Editor edit, String key, TLRPC.Message value) {
        if (value == null) { edit.remove(key); return; }
        SerializedData data = new SerializedData(value.getObjectSize()); value.serializeToStream(data);
        edit.putString(key, Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)); data.cleanup();
    }

    private void finish(String failure) {
        checking = false; error = failure;
        if (timeout != null) AndroidUtilities.cancelRunOnUIThread(timeout);
        changed(); List<Runnable> callbacks = new ArrayList<>(completions); completions.clear();
        for (Runnable callback : callbacks) callback.run();
    }
    public boolean downloading() { return document() != null && FileLoader.getInstance(account).isLoadingFile(FileLoader.getAttachFileName(document())); }
    public boolean downloaded() { return document() != null && downloadedFile().isFile() && downloadedFile().length() == document().size; }
    private File downloadedFile() { return FileLoader.getInstance(account).getPathToAttach(document(), true); }
    public float progress() { Float progress = document() == null ? null : ImageLoader.getInstance().getFileProgress(FileLoader.getAttachFileName(document())); return progress == null ? 0 : progress; }
    public void download() {
        if (!available() || checking || verifying) return;
        if (peer == null) {
            check(true, () -> { if (error == null && peer != null) download(); });
            return;
        }
        error = null; cancelledDownload = false;
        FileLoader.getInstance(account).loadFile(document(), new MessageObject(account, message, false, false), FileLoader.PRIORITY_NORMAL, 1);
        changed();
    }
    public void cancelDownload() { cancelledDownload = true; error = null; if (document() != null) FileLoader.getInstance(account).cancelLoadFile(document()); changed(); }
    public void redownload() {
        if (document() == null || verifying || checking) return;
        cancelDownload();
        File file = downloadedFile();
        if (file.exists() && !file.delete()) { error = text("Не удалось удалить предыдущую загрузку.", "Could not remove the previous download."); changed(); return; }
        download();
    }
    public void install(Activity activity) {
        if (!available() || !downloaded() || verifying) return;
        if (!ApplicationLoader.applicationLoaderInstance.checkApkInstallPermissions(activity)) return;
        verifying = true; error = null; changed();
        NebulaRelease r = release(); File source = downloadedFile(); long size = document().size;
        int session = generation;
        WeakReference<Activity> target = new WeakReference<>(activity);
        worker.execute(() -> {
            File verified = null;
            try { verified = NebulaApkVerifier.prepare(ApplicationLoader.applicationContext, source, r, size); }
            catch (Exception e) { FileLog.e(e); }
            File apk = verified;
            AndroidUtilities.runOnUIThread(() -> {
                verifying = false;
                if (session != generation) { if (apk != null) apk.delete(); changed(); return; }
                Activity a = target.get();
                if (apk == null) error = text("APK не прошёл проверку. Проверьте обновления и скачайте сборку заново.", "APK verification failed. Check for updates and download again.");
                else if (a != null && !a.isFinishing() && !a.isDestroyed()) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(FileProvider.getUriForFile(a, ApplicationLoader.getApplicationId() + ".provider", apk), "application/vnd.android.package-archive");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        a.startActivity(intent);
                    } catch (Exception e) { error = text("Не удалось открыть установщик Android.", "Could not open the Android installer."); FileLog.e(e); }
                }
                changed();
            });
        });
    }

    @Override public void didReceivedNotification(int id, int eventAccount, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            generation++; ConnectionsManager.getInstance(account).cancelRequest(request, true);
            if (timeout != null) AndroidUtilities.cancelRunOnUIThread(timeout);
            cancelDownload(); message = null; changelog = null; peer = null; prefs.edit().clear().apply();
            finish(text("Войдите в аккаунт Telegram.", "Sign in to Telegram.")); return;
        }
        if (document() != null && args.length > 0 && FileLoader.getAttachFileName(document()).equals(args[0])) {
            if (id == NotificationCenter.fileLoadFailed && !cancelledDownload) error = text("Загрузка прервана. Повторите проверку, если APK был перезалит.", "Download interrupted. Check again if the APK was reuploaded.");
            changed();
        }
    }

    public static boolean isUpdateLink(String value) {
        return NebulaRelease.isUpdateLink(value);
    }
    public static void checkFromLaunch(LaunchActivity activity, boolean force, Browser.Progress progress) {
        if (force) {
            BaseFragment last = activity.getLastFragment();
            if (last != null && !(last instanceof NebulaUpdatesFragment)) last.presentFragment(new NebulaUpdatesFragment());
        }
        NebulaTelegramUpdates updater = get(UserConfig.selectedAccount);
        if (progress != null) progress.init();
        WeakReference<LaunchActivity> target = new WeakReference<>(activity);
        updater.check(force, () -> {
            if (progress != null) progress.end();
            LaunchActivity a = target.get();
            if (a == null || a.isFinishing() || a.isDestroyed() || !updater.available() || updater.error != null || updater.downloading() || updater.verifying) return;
            if (UserConfig.selectedAccount != updater.account || SharedConfig.isWaitingForPasscodeEnter || SharedConfig.appLocked || ApplicationLoader.mainInterfacePaused) return;
            if (!force && (!updater.automatic() || updater.prefs.getInt("prompted", 0) == updater.release().versionCode)) return;
            updater.prefs.edit().putInt("prompted", updater.release().versionCode).apply();
            updater.showOffer(a);
        });
    }
    public void showOffer(Activity activity) {
        if (!available()) return;
        final int offeredCode = release().versionCode;
        final long offeredDocument = document().id;
        final String offeredPost = postUrl();
        android.widget.LinearLayout content = new android.widget.LinearLayout(activity);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        android.widget.TextView details = new android.widget.TextView(activity);
        details.setTextSize(13); details.setTextColor(NebulaTheme.of(activity).onSurfaceVariant());
        details.setText("Telegram " + release().telegramVersion + " · " + AndroidUtilities.formatFileSize(document().size));
        content.addView(details);
        NebulaChangelogView changelog = new NebulaChangelogView(activity); changelog.setPost(post());
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2); lp.topMargin = AndroidUtilities.dp(16);
        content.addView(changelog, lp);
        new AlertDialog.Builder(activity).setTitle("NebulaGram " + release().versionName)
                .setView(content)
                .setPositiveButton(downloaded() ? text("Установить", "Install") : text("Скачать", "Download"), (dialog, which) -> {
                    if (!available()) return;
                    if (release().versionCode != offeredCode || document().id != offeredDocument) { showOffer(activity); return; }
                    if (downloaded()) install(activity); else download();
                    if (activity instanceof LaunchActivity) {
                        BaseFragment last = ((LaunchActivity) activity).getLastFragment();
                        if (last != null && !(last instanceof NebulaUpdatesFragment)) last.presentFragment(new NebulaUpdatesFragment());
                    }
                })
                .setNeutralButton(text("Открыть пост", "Open post"), (dialog, which) -> Browser.openUrl(activity, offeredPost))
                .setNegativeButton(text("Позже", "Later"), null).show();
    }
}
