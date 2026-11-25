package app.nebulagram.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

/**
 * "Export data": account information, chats and media saved to Downloads/NebulaGram as HTML pages,
 * a Telegram Desktop style result.json, or both. One export at a time; it keeps running when its
 * progress sheet is closed and stops with the app process.
 */
public final class NebulaExport implements Runnable {
    /** What the progress sheet shows; written by the worker, read on the UI thread. */
    public static final class State {
        public volatile int percent, chatIndex, chatCount, message, messageCount;
        public volatile String step = "", chat = "", file = "", folder = "", error = "";
        public volatile long fileDone, fileTotal;
        public volatile boolean finished, stopped, mediaFile;
    }

    private static volatile NebulaExport running;
    public static NebulaExport current() { return running; }

    public final State state = new State();
    private final int account;
    private final NebulaExportOptions options;
    private final String folder;
    private final TimeZone zone = TimeZone.getDefault();
    private final Map<Long, TLRPC.User> users = new HashMap<>();
    private final Map<Long, TLRPC.Chat> chats = new HashMap<>();
    private final Set<String> used = new HashSet<>();
    private volatile boolean stop;
    private Writer json;
    private boolean firstChat = true;

    private NebulaExport(int account, NebulaExportOptions options) {
        this.account = account;
        this.options = options;
        this.folder = NebulaExportOptions.folderName(System.currentTimeMillis());
        state.folder = Environment.DIRECTORY_DOWNLOADS + "/NebulaGram/" + folder;
    }

    /** Starts an export unless one is running; returns the running one either way. */
    public static synchronized NebulaExport start(int account, NebulaExportOptions options) {
        if (running != null && !running.state.finished) return running;
        NebulaExport export = new NebulaExport(account, options);
        running = export;
        Thread thread = new Thread(export, "NebulaExport");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return export;
    }

    public void stop() { stop = true; state.stopped = true; }

    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    // ---- output ----

    /** A new file inside the export folder: MediaStore on Android 10+, a plain file before. */
    private OutputStream open(String path, String mime) throws Exception {
        int slash = path.lastIndexOf('/');
        String dir = slash < 0 ? "" : "/" + path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, state.folder + dir);
            ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("MediaStore refused " + path);
            return resolver.openOutputStream(uri);
        }
        File base = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NebulaGram/" + folder + dir);
        if (!base.isDirectory() && !base.mkdirs()) throw new IllegalStateException("No folder " + base);
        return new FileOutputStream(new File(base, name));
    }

    private Writer text(String path, String mime) throws Exception {
        return new java.io.BufferedWriter(new OutputStreamWriter(open(path, mime), StandardCharsets.UTF_8), 65536);
    }

    /** A unique path for a media file in a chat folder. */
    private String unique(String dir, String name) {
        String path = dir + "/" + name;
        int dot = name.lastIndexOf('.'), n = 2;
        while (!used.add(path)) path = dir + "/" + (dot > 0 ? name.substring(0, dot) + " (" + n++ + ")" + name.substring(dot) : name + " (" + n++ + ")");
        return path;
    }

    // ---- network ----

    private TLObject call(TLObject request) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            if (stop) throw new InterruptedException();
            final TLObject[] result = new TLObject[1];
            final TLRPC.TL_error[] failure = new TLRPC.TL_error[1];
            CountDownLatch latch = new CountDownLatch(1);
            ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> { result[0] = response; failure[0] = error; latch.countDown(); });
            if (!latch.await(60, TimeUnit.SECONDS)) continue;
            if (failure[0] == null) return result[0];
            String text = failure[0].text == null ? "" : failure[0].text;
            if (text.startsWith("FLOOD_WAIT_")) {
                int seconds = 5;
                try { seconds = Integer.parseInt(text.substring(11)); } catch (NumberFormatException ignored) { }
                state.step = t("Пауза по просьбе сервера: ", "Server asked to wait: ") + seconds + t(" с", " s");
                Thread.sleep(Math.min(300, seconds) * 1000L + 500);
                continue;
            }
            throw new IllegalStateException(text);
        }
        throw new IllegalStateException("timeout");
    }

    private void remember(List<TLRPC.User> u, List<TLRPC.Chat> c) {
        for (TLRPC.User user : u) users.put(user.id, user);
        for (TLRPC.Chat chat : c) chats.put(chat.id, chat);
        MessagesController controller = MessagesController.getInstance(account);
        AndroidUtilities.runOnUIThread(() -> { controller.putUsers(new ArrayList<>(u), false); controller.putChats(new ArrayList<>(c), false); });
    }

    // ---- export ----

    @Override public void run() {
        try {
            if (options.format != NebulaExportOptions.FORMAT_HTML) {
                json = text("result.json", "application/json");
                json.write("{\n \"about\": " + NebulaExportOptions.json("Exported by NebulaGram"));
            }
            exportAccount();
            exportChats();
            if (json != null) { json.write("\n}\n"); json.close(); json = null; }
            state.percent = 100;
            state.step = t("Готово", "Done");
        } catch (InterruptedException stopped) {
            state.step = t("Экспорт остановлен", "Export stopped");
        } catch (Throwable error) {
            FileLog.e(error);
            state.error = error.getMessage() == null ? error.toString() : error.getMessage();
            state.step = t("Ошибка: ", "Error: ") + state.error;
        } finally {
            if (json != null) try { json.close(); } catch (Exception ignored) { }
            state.finished = true;
        }
    }

    private List<String[]> contacts = new ArrayList<>();
    private TLRPC.User self;

    private void exportAccount() throws Exception {
        self = UserConfig.getInstance(account).getCurrentUser();
        if (options.has(NebulaExportOptions.INFO) && self != null && json != null) {
            json.write(",\n \"personal_information\": {\n  \"user_id\": " + self.id + ",\n  \"first_name\": " + NebulaExportOptions.json(self.first_name)
                    + ",\n  \"last_name\": " + NebulaExportOptions.json(self.last_name) + ",\n  \"phone_number\": " + NebulaExportOptions.json(self.phone == null ? "" : "+" + self.phone)
                    + ",\n  \"username\": " + NebulaExportOptions.json(self.username == null ? "" : "@" + self.username) + "\n }");
        }
        if (options.has(NebulaExportOptions.CONTACTS)) {
            state.step = t("Контакты", "Contacts");
            TLRPC.TL_contacts_getContacts request = new TLRPC.TL_contacts_getContacts();
            TLObject response = call(request);
            if (response instanceof TLRPC.contacts_Contacts) {
                TLRPC.contacts_Contacts result = (TLRPC.contacts_Contacts) response;
                remember(result.users, new ArrayList<>());
                for (TLRPC.TL_contact contact : result.contacts) {
                    TLRPC.User user = users.get(contact.user_id);
                    if (user != null) contacts.add(new String[]{String.valueOf(user.id), user.first_name == null ? "" : user.first_name, user.last_name == null ? "" : user.last_name, user.phone == null ? "" : "+" + user.phone});
                }
            }
            if (json != null) {
                json.write(",\n \"contacts\": {\n  \"list\": [");
                for (int i = 0; i < contacts.size(); i++) {
                    String[] c = contacts.get(i);
                    json.write((i == 0 ? "\n" : ",\n") + "   {\"user_id\": " + c[0] + ", \"first_name\": " + NebulaExportOptions.json(c[1]) + ", \"last_name\": " + NebulaExportOptions.json(c[2]) + ", \"phone_number\": " + NebulaExportOptions.json(c[3]) + "}");
                }
                json.write("\n  ]\n }");
            }
        }
        List<String[]> sessions = new ArrayList<>();
        if (options.has(NebulaExportOptions.SESSIONS)) {
            state.step = t("Сеансы", "Sessions");
            TLObject response = call(new TL_account.getAuthorizations());
            if (response instanceof TL_account.authorizations) {
                for (TLRPC.TL_authorization a : ((TL_account.authorizations) response).authorizations)
                    sessions.add(new String[]{a.app_name + " " + a.app_version, a.device_model + ", " + a.platform + " " + a.system_version, a.ip + " " + a.country, NebulaExportOptions.isoDate(a.date_active, zone)});
            }
            if (json != null) {
                json.write(",\n \"sessions\": {\n  \"list\": [");
                for (int i = 0; i < sessions.size(); i++) {
                    String[] s = sessions.get(i);
                    json.write((i == 0 ? "\n" : ",\n") + "   {\"app\": " + NebulaExportOptions.json(s[0]) + ", \"device\": " + NebulaExportOptions.json(s[1]) + ", \"place\": " + NebulaExportOptions.json(s[2]) + ", \"last_active\": " + NebulaExportOptions.json(s[3]) + "}");
                }
                json.write("\n  ]\n }");
            }
        }
        if (options.format != NebulaExportOptions.FORMAT_JSON) accountPage(sessions);
    }

    private void accountPage(List<String[]> sessions) throws Exception {
        if (!options.any(0)) return;
        Writer page = text("account.html", "text/html");
        page.write(head(t("Аккаунт", "Account")));
        if (options.has(NebulaExportOptions.INFO) && self != null) {
            page.write("<h2>" + NebulaExportOptions.html(t("Информация", "Information")) + "</h2><div class=\"card\">");
            page.write("<p><b>" + NebulaExportOptions.html(ContactsController.formatName(self.first_name, self.last_name)) + "</b></p>");
            if (self.username != null) page.write("<p>@" + NebulaExportOptions.html(self.username) + "</p>");
            if (self.phone != null) page.write("<p>+" + NebulaExportOptions.html(self.phone) + "</p>");
            page.write("<p class=\"dim\">ID " + self.id + "</p></div>");
        }
        if (options.has(NebulaExportOptions.CONTACTS)) {
            page.write("<h2>" + NebulaExportOptions.html(t("Контакты", "Contacts")) + " · " + contacts.size() + "</h2><div class=\"card\">");
            for (String[] c : contacts) page.write("<p>" + NebulaExportOptions.html((c[1] + " " + c[2]).trim()) + " <span class=\"dim\">" + NebulaExportOptions.html(c[3]) + "</span></p>");
            page.write("</div>");
        }
        if (options.has(NebulaExportOptions.SESSIONS)) {
            page.write("<h2>" + NebulaExportOptions.html(t("Сеансы", "Sessions")) + "</h2><div class=\"card\">");
            for (String[] s : sessions) page.write("<p><b>" + NebulaExportOptions.html(s[0]) + "</b><br>" + NebulaExportOptions.html(s[1]) + "<br><span class=\"dim\">" + NebulaExportOptions.html(s[2] + " · " + s[3]) + "</span></p>");
            page.write("</div>");
        }
        page.write("</body></html>");
        page.close();
    }

    static String head(String title) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + NebulaExportOptions.html(title)
                + "</title><style>body{font-family:system-ui,sans-serif;background:#f1f2f6;margin:0;padding:16px;color:#111}h1,h2{font-weight:600}"
                + ".card{background:#fff;border-radius:16px;padding:12px 16px;margin:8px 0}.dim{color:#888;font-size:13px}"
                + ".m{background:#fff;border-radius:14px;padding:8px 12px;margin:6px 0;max-width:640px}.o{margin-left:auto;background:#e1f5d6}"
                + ".f{font-weight:600;font-size:13px;color:#2a7ab0}.media{color:#2a7ab0}img{max-width:320px;border-radius:10px;display:block;margin:4px 0}"
                + "@media(prefers-color-scheme:dark){body{background:#101215;color:#eee}.card,.m{background:#1c1f24}.o{background:#2b5278}}</style></head><body>"
                + "<h1>" + NebulaExportOptions.html(title) + "</h1>";
    }

    /** Which chats the options ask for. */
    private boolean wanted(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = users.get(dialogId);
            if (self != null && dialogId == self.id) return options.has(NebulaExportOptions.SAVED);
            if (user != null && user.bot) return options.has(NebulaExportOptions.BOTS);
            return options.has(NebulaExportOptions.PERSONAL);
        }
        TLRPC.Chat chat = chats.get(-dialogId);
        if (chat == null) return false;
        if (ChatObject.isChannelAndNotMegaGroup(chat)) return options.has(NebulaExportOptions.CHANNELS);
        return options.has(ChatObject.isPublic(chat) ? NebulaExportOptions.PUBLIC_GROUPS : NebulaExportOptions.PRIVATE_GROUPS);
    }

    private String name(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = users.get(dialogId);
            if (self != null && dialogId == self.id) return t("Избранное", "Saved Messages");
            return user == null ? String.valueOf(dialogId) : UserObject.getUserName(user);
        }
        TLRPC.Chat chat = chats.get(-dialogId);
        return chat == null ? String.valueOf(dialogId) : chat.title;
    }

    private String type(long dialogId) {
        if (dialogId > 0) {
            TLRPC.User user = users.get(dialogId);
            if (self != null && dialogId == self.id) return "saved_messages";
            return user != null && user.bot ? "bot_chat" : "personal_chat";
        }
        TLRPC.Chat chat = chats.get(-dialogId);
        if (chat != null && ChatObject.isChannelAndNotMegaGroup(chat)) return ChatObject.isPublic(chat) ? "public_channel" : "private_channel";
        return chat != null && ChatObject.isPublic(chat) ? "public_supergroup" : "private_group";
    }

    private void exportChats() throws Exception {
        if (!options.any(1)) return;
        state.step = t("Список чатов", "Chat list");
        List<Long> dialogs = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int offsetDate = 0, offsetId = 0;
        TLRPC.InputPeer offsetPeer = new TLRPC.TL_inputPeerEmpty();
        for (int page = 0; page < 200; page++) {
            TLRPC.TL_messages_getDialogs request = new TLRPC.TL_messages_getDialogs();
            request.offset_date = offsetDate; request.offset_id = offsetId; request.offset_peer = offsetPeer; request.limit = 100;
            TLObject response = call(request);
            if (!(response instanceof TLRPC.messages_Dialogs)) break;
            TLRPC.messages_Dialogs result = (TLRPC.messages_Dialogs) response;
            remember(result.users, result.chats);
            for (TLRPC.Dialog dialog : result.dialogs) {
                long id = MessageObject.getPeerId(dialog.peer);
                if (seen.add(id) && wanted(id)) dialogs.add(id);
            }
            if (result.dialogs.size() < 100 || !(result instanceof TLRPC.TL_messages_dialogsSlice)) break;
            TLRPC.Message last = null;
            TLRPC.Dialog tail = result.dialogs.get(result.dialogs.size() - 1);
            for (TLRPC.Message m : result.messages) if (m.id == tail.top_message && MessageObject.getPeerId(m.peer_id) == MessageObject.getPeerId(tail.peer)) last = m;
            if (last == null) break;
            offsetDate = last.date; offsetId = last.id;
            offsetPeer = peer(MessageObject.getPeerId(tail.peer));
        }
        state.chatCount = dialogs.size();
        if (json != null) json.write(",\n \"chats\": {\n  \"list\": [");
        List<String[]> index = new ArrayList<>();
        int since = options.since(System.currentTimeMillis());
        for (int i = 0; i < dialogs.size(); i++) {
            if (stop) throw new InterruptedException();
            long id = dialogs.get(i);
            state.chatIndex = i + 1;
            state.chat = name(id);
            state.message = 0; state.messageCount = 0;
            state.step = t("Чаты", "Chats");
            String dir = "chats/chat_" + String.format(java.util.Locale.ROOT, "%03d", i + 1);
            int count = exportChat(id, dir, since, i, dialogs.size());
            index.add(new String[]{dir + "/messages.html", state.chat, String.valueOf(count)});
        }
        if (json != null) json.write("\n  ]\n }");
        if (options.format != NebulaExportOptions.FORMAT_JSON) {
            Writer page = text("export_results.html", "text/html");
            page.write(head(t("Экспорт данных", "Exported data")));
            if (options.any(0)) page.write("<div class=\"card\"><a href=\"account.html\">" + NebulaExportOptions.html(t("Аккаунт", "Account")) + "</a></div>");
            page.write("<h2>" + NebulaExportOptions.html(t("Чаты", "Chats")) + " · " + index.size() + "</h2>");
            for (String[] row : index) page.write("<div class=\"card\"><a href=\"" + NebulaExportOptions.href(row[0]) + "\">" + NebulaExportOptions.html(row[1]) + "</a> <span class=\"dim\">" + row[2] + "</span></div>");
            page.write("</body></html>");
            page.close();
        }
    }

    private TLRPC.InputPeer peer(long id) {
        TLRPC.InputPeer input = MessagesController.getInstance(account).getInputPeer(id);
        if (input instanceof TLRPC.TL_inputPeerEmpty || input == null) {
            if (id > 0 && users.get(id) != null) {
                TLRPC.TL_inputPeerUser user = new TLRPC.TL_inputPeerUser();
                user.user_id = id; user.access_hash = users.get(id).access_hash;
                return user;
            }
            TLRPC.Chat chat = chats.get(-id);
            if (chat != null) {
                if (ChatObject.isChannel(chat)) { TLRPC.TL_inputPeerChannel channel = new TLRPC.TL_inputPeerChannel(); channel.channel_id = chat.id; channel.access_hash = chat.access_hash; return channel; }
                TLRPC.TL_inputPeerChat group = new TLRPC.TL_inputPeerChat(); group.chat_id = chat.id; return group;
            }
        }
        return input;
    }

    /** One chat, oldest messages first in the files. Returns how many messages were written. */
    private int exportChat(long id, String dir, int since, int chatIndex, int chatCount) throws Exception {
        TLRPC.InputPeer input = peer(id);
        List<TLRPC.Message> messages = new ArrayList<>();
        int offset = 0;
        boolean done = false;
        while (!done) {
            if (stop) throw new InterruptedException();
            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            request.peer = input; request.offset_id = offset; request.limit = 100;
            TLObject response = call(request);
            if (!(response instanceof TLRPC.messages_Messages)) break;
            TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
            remember(result.users, result.chats);
            if (result.count > 0) state.messageCount = result.count;
            for (TLRPC.Message m : result.messages) {
                if (since > 0 && m.date < since) { done = true; break; }
                messages.add(m);
            }
            if (state.messageCount == 0) state.messageCount = messages.size();
            state.message = messages.size();
            state.percent = NebulaExportOptions.percent(chatIndex, chatCount, state.messageCount <= 0 ? 0 : messages.size() / (float) state.messageCount * .5f);
            if (result.messages.size() < 100) break;
            offset = result.messages.get(result.messages.size() - 1).id;
            if (messages.size() > 200000) break;
        }
        java.util.Collections.reverse(messages);
        Writer page = options.format != NebulaExportOptions.FORMAT_JSON ? text(dir + "/messages.html", "text/html") : null;
        if (page != null) page.write(head(name(id)));
        if (json != null) {
            json.write((firstChat ? "\n" : ",\n") + "   {\n    \"name\": " + NebulaExportOptions.json(name(id)) + ",\n    \"type\": " + NebulaExportOptions.json(type(id)) + ",\n    \"id\": " + Math.abs(id) + ",\n    \"messages\": [");
            firstChat = false;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (stop) throw new InterruptedException();
            TLRPC.Message m = messages.get(i);
            state.message = i + 1;
            state.messageCount = messages.size();
            state.percent = NebulaExportOptions.percent(chatIndex, chatCount, .5f + .5f * (i + 1) / Math.max(1, messages.size()));
            String media = media(m, dir);
            long from = m.from_id != null ? MessageObject.getPeerId(m.from_id) : id;
            String author = from > 0 ? (users.get(from) == null ? String.valueOf(from) : UserObject.getUserName(users.get(from))) : name(from);
            String body = m.message == null ? "" : m.message;
            if (m.action != null && !(m.action instanceof TLRPC.TL_messageActionEmpty)) body = t("Служебное сообщение", "Service message");
            if (json != null) {
                json.write((i == 0 ? "\n" : ",\n") + "     {\"id\": " + m.id + ", \"type\": " + NebulaExportOptions.json(m.action != null ? "service" : "message")
                        + ", \"date\": " + NebulaExportOptions.json(NebulaExportOptions.isoDate(m.date, zone)) + ", \"date_unixtime\": " + NebulaExportOptions.json(String.valueOf(m.date))
                        + ", \"from\": " + NebulaExportOptions.json(author) + ", \"from_id\": " + NebulaExportOptions.json((from > 0 ? "user" : "channel") + Math.abs(from))
                        + (m.reply_to != null && m.reply_to.reply_to_msg_id != 0 ? ", \"reply_to_message_id\": " + m.reply_to.reply_to_msg_id : "")
                        + (media.isEmpty() ? "" : ", \"file\": " + NebulaExportOptions.json(media))
                        + ", \"text\": " + NebulaExportOptions.json(body) + "}");
            }
            if (page != null) {
                boolean out = m.out || self != null && from == self.id;
                page.write("<div class=\"m" + (out ? " o" : "") + "\"><div class=\"f\">" + NebulaExportOptions.html(author) + " <span class=\"dim\">"
                        + NebulaExportOptions.html(NebulaExportOptions.isoDate(m.date, zone).replace('T', ' ')) + "</span></div>");
                if (!media.isEmpty()) {
                    String link = NebulaExportOptions.href(media.substring(dir.length() + 1));
                    if (media.contains("/photos/")) page.write("<a href=\"" + link + "\"><img src=\"" + link + "\" loading=\"lazy\"></a>");
                    else page.write("<div class=\"media\"><a href=\"" + link + "\">" + NebulaExportOptions.html(media.substring(media.lastIndexOf('/') + 1)) + "</a></div>");
                } else if (m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty) && !(m.media instanceof TLRPC.TL_messageMediaWebPage)) {
                    page.write("<div class=\"dim\">" + NebulaExportOptions.html(t("[медиа не выгружено]", "[media not exported]")) + "</div>");
                }
                if (!body.isEmpty()) page.write("<div>" + NebulaExportOptions.html(body) + "</div>");
                page.write("</div>");
            }
        }
        if (json != null) json.write("\n    ]\n   }");
        if (page != null) { page.write("</body></html>"); page.close(); }
        return messages.size();
    }

    /** Copies a message's media into the chat folder when its kind is chosen and it fits the limit; returns its path or "". */
    private String media(TLRPC.Message m, String dir) {
        if (m.media == null) return "";
        int kind;
        String sub;
        if (m.media instanceof TLRPC.TL_messageMediaPhoto) { kind = NebulaExportOptions.PHOTOS; sub = "photos"; }
        else if (MessageObject.isRoundVideoMessage(m)) { kind = NebulaExportOptions.ROUND; sub = "round_video_messages"; }
        else if (MessageObject.isVoiceMessage(m)) { kind = NebulaExportOptions.VOICE; sub = "voice_messages"; }
        else if (MessageObject.isStickerMessage(m)) { kind = NebulaExportOptions.STICKERS; sub = "stickers"; }
        else if (MessageObject.isGifMessage(m)) { kind = NebulaExportOptions.GIFS; sub = "animations"; }
        else if (MessageObject.isVideoMessage(m)) { kind = NebulaExportOptions.VIDEOS; sub = "video_files"; }
        else if (m.media instanceof TLRPC.TL_messageMediaDocument) { kind = NebulaExportOptions.FILES; sub = "files"; }
        else return "";
        if (!options.has(kind)) return "";
        try {
            MessageObject object = new MessageObject(account, m, false, false);
            TLObject attach;
            long size;
            String fileName;
            TLRPC.Photo photo = MessageObject.getPhoto(m);
            TLRPC.Document document = MessageObject.getDocument(m);
            if (kind == NebulaExportOptions.PHOTOS) {
                if (photo == null) return "";
                TLRPC.PhotoSize best = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 2560);
                if (best == null) return "";
                attach = best; size = best.size; fileName = "photo_" + m.id + "@" + NebulaExportOptions.isoDate(m.date, zone).replace(':', '-').replace('T', '_') + ".jpg";
            } else {
                if (document == null) return "";
                attach = document; size = document.size;
                String original = FileLoader.getDocumentFileName(document);
                fileName = original == null || original.isEmpty() ? sub + "_" + m.id + extension(document.mime_type) : original;
            }
            if (size <= 0 || size > options.sizeLimit) return "";
            File file = fetch(object, attach, document, photo, size, fileName);
            if (file == null) return "";
            String path = unique(dir + "/" + sub, NebulaExportOptions.safeName(fileName, sub + "_" + m.id));
            try (InputStream in = new FileInputStream(file); OutputStream out = open(path, document != null && document.mime_type != null ? document.mime_type : "image/jpeg")) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) > 0) { if (stop) throw new InterruptedException(); out.write(buffer, 0, n); }
            }
            return path;
        } catch (InterruptedException stopped) {
            return "";
        } catch (Throwable error) {
            FileLog.e(error);
            return "";
        }
    }

    private static String extension(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case "audio/ogg": return ".ogg";
            case "video/mp4": return ".mp4";
            case "image/webp": return ".webp";
            case "application/x-tgsticker": return ".tgs";
            case "video/webm": return ".webm";
            default: return "";
        }
    }

    /** The local file of the media: already downloaded, or downloaded now through FileLoader. */
    private File fetch(MessageObject object, TLObject attach, TLRPC.Document document, TLRPC.Photo photo, long size, String name) throws InterruptedException {
        FileLoader loader = FileLoader.getInstance(account);
        File existing = loader.getPathToMessage(object.messageOwner);
        if (existing != null && existing.exists() && existing.length() >= size) return existing;
        File cached = loader.getPathToAttach(attach, true);
        if (cached != null && cached.exists() && cached.length() >= size) return cached;
        state.file = name; state.fileTotal = size; state.fileDone = 0; state.mediaFile = photo != null;
        String key = FileLoader.getAttachFileName(attach);
        AndroidUtilities.runOnUIThread(() -> {
            if (document != null) loader.loadFile(document, object, FileLoader.PRIORITY_NORMAL, 1);
            else loader.loadFile(ImageLocation.getForPhoto((TLRPC.PhotoSize) attach, photo), object, "jpg", FileLoader.PRIORITY_NORMAL, 1);
        });
        long deadline = System.currentTimeMillis() + 60000 + size / 30;
        while (System.currentTimeMillis() < deadline) {
            if (stop) {
                if (document != null) AndroidUtilities.runOnUIThread(() -> loader.cancelLoadFile(document));
                throw new InterruptedException();
            }
            Thread.sleep(250);
            long[] progress = ImageLoader.getInstance().getFileProgressSizes(key);
            if (progress != null && progress.length >= 2) state.fileDone = progress[0];
            if (cached != null && cached.exists() && cached.length() >= size) { state.fileDone = size; return cached; }
            File now = loader.getPathToAttach(attach, true);
            if (now != null && now.exists() && now.length() >= size) { state.fileDone = size; return now; }
        }
        return null;
    }
}
