package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;

import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.ContactAddActivity;

import java.util.*;

/**
 * Private notes for groups, channels and people who are not contacts. Stored per Telegram user on
 * this device; optionally mirrored into Saved Messages as #NebulaGramNotesV1 pages. Contacts use
 * Telegram's own contact note instead, so the two never hold competing text.
 */
public final class NebulaNotes {
    private NebulaNotes() { }
    private static final Set<Long> busy = new HashSet<>();
    private static final Map<Long, Long> lastPull = new HashMap<>();
    private static final Map<Long, Runnable> pending = new HashMap<>();

    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_notes", 0); }
    private static long owner(int account) { return UserConfig.getInstance(account).getClientUserId(); }

    static SortedMap<Long, NebulaNoteDocument.Entry> all(int account) {
        long id = owner(account);
        if (id == 0) return new TreeMap<>();
        try { return NebulaNoteDocument.load(prefs().getString("notes_" + id, "")); }
        catch (Exception e) { FileLog.e(e); return new TreeMap<>(); }
    }
    private static boolean save(long owner, Map<Long, NebulaNoteDocument.Entry> notes) {
        try { return prefs().edit().putString("notes_" + owner, NebulaNoteDocument.store(notes)).commit(); }
        catch (Exception e) { FileLog.e(e); return false; }
    }
    public static String get(int account, long dialog) {
        NebulaNoteDocument.Entry entry = all(account).get(dialog);
        return entry == null ? "" : entry.text;
    }
    public static boolean has(int account, long dialog) { return !get(account, dialog).isEmpty(); }
    public static void set(int account, long dialog, String value) {
        long id = owner(account);
        if (id == 0 || dialog == 0) return;
        String clean = value == null ? "" : value.trim();
        if (clean.length() > NebulaNoteDocument.MAX_NOTE) clean = clean.substring(0, NebulaNoteDocument.MAX_NOTE);
        SortedMap<Long, NebulaNoteDocument.Entry> notes = all(account);
        NebulaNoteDocument.Entry old = notes.get(dialog);
        if (old == null && clean.isEmpty() || old != null && old.text.equals(clean)) return;
        long now = System.currentTimeMillis();
        notes.put(dialog, new NebulaNoteDocument.Entry(clean, old == null ? now : Math.max(now, old.updated + 1)));
        save(id, NebulaNoteDocument.prune(notes, now));
        schedule(account, 2000);
    }

    public static boolean syncEnabled(int account) { long id = owner(account); return id != 0 && prefs().getBoolean("sync_" + id, false); }
    public static void setSyncEnabled(int account, boolean value) {
        long id = owner(account);
        if (id == 0) return;
        prefs().edit().putBoolean("sync_" + id, value).apply();
        if (value) sync(account, null);
    }
    private static void schedule(int account, long delay) {
        long id = owner(account);
        Runnable old = pending.remove(id);
        if (old != null) AndroidUtilities.cancelRunOnUIThread(old);
        if (!syncEnabled(account)) return;
        Runnable task = () -> { pending.remove(id); sync(account, null); };
        pending.put(id, task);
        AndroidUtilities.runOnUIThread(task, delay);
    }
    /** At most every ten minutes per account; a manual edit or toggle always syncs. */
    public static void pull(int account, Runnable changed) {
        long id = owner(account), now = android.os.SystemClock.elapsedRealtime();
        Long last = lastPull.get(id);
        if (!syncEnabled(account) || last != null && now - last < 600_000) return;
        lastPull.put(id, now);
        sync(account, changed);
    }

    private static final class Remote { int id; String text; }

    /** Read every own tagged page, merge by edit time, write the merged pages back in place. */
    public static void sync(int account, Runnable changed) {
        long user = owner(account);
        if (user == 0 || !syncEnabled(account) || !busy.add(user)) return;
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = new TLRPC.TL_inputPeerSelf(); req.q = NebulaNoteDocument.MARKER.trim();
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty(); req.limit = 100;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                if (owner(account) != user || !syncEnabled(account) || error != null || !(response instanceof TLRPC.messages_Messages)) return;
                TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
                SortedMap<Long, NebulaNoteDocument.Entry> remote = new TreeMap<>();
                Map<Integer, Remote> messages = new TreeMap<>();
                for (TLRPC.Message m : result.messages) {
                    if (!m.out || m.peer_id == null || m.peer_id.user_id != user || m.fwd_from != null
                            || m.message == null || !m.message.startsWith(NebulaNoteDocument.MARKER)) continue;
                    NebulaNoteDocument.Page page;
                    try { page = NebulaNoteDocument.parse(m.message); } catch (Exception bad) { continue; }
                    remote = NebulaNoteDocument.merge(remote, page.notes);
                    Remote known = messages.get(page.index);
                    if (known == null || known.id < m.id) { Remote r = new Remote(); r.id = m.id; r.text = m.message; messages.put(page.index, r); }
                }
                long now = System.currentTimeMillis();
                SortedMap<Long, NebulaNoteDocument.Entry> local = all(account);
                SortedMap<Long, NebulaNoteDocument.Entry> merged = NebulaNoteDocument.prune(NebulaNoteDocument.merge(local, remote), now);
                if (!merged.equals(local)) { save(user, merged); if (changed != null) changed.run(); }
                List<String> pages = NebulaNoteDocument.encode(merged);
                int count = Math.max(pages.size(), messages.isEmpty() ? 0 : Collections.max(messages.keySet()) + 1);
                for (int i = 0; i < count; i++) {
                    String body = i < pages.size() ? pages.get(i) : NebulaNoteDocument.page(i, Collections.emptyMap());
                    Remote existing = messages.get(i);
                    if (existing != null && body.equals(existing.text)) continue;
                    if (existing == null && i >= pages.size()) continue;
                    TLObject request;
                    if (existing != null) {
                        TLRPC.TL_messages_editMessage edit = new TLRPC.TL_messages_editMessage();
                        edit.peer = new TLRPC.TL_inputPeerSelf(); edit.id = existing.id; edit.message = body; edit.flags = 2048; edit.no_webpage = true;
                        request = edit;
                    } else {
                        TLRPC.TL_messages_sendMessage send = new TLRPC.TL_messages_sendMessage();
                        send.peer = new TLRPC.TL_inputPeerSelf(); send.message = body; send.no_webpage = true; send.silent = true;
                        long random = Utilities.random.nextLong(); send.random_id = random == 0 ? 1 : random;
                        request = send;
                    }
                    ConnectionsManager.getInstance(account).sendRequest(request, (r, e) -> {
                        if (r instanceof TLRPC.Updates) MessagesController.getInstance(account).processUpdates((TLRPC.Updates) r, false);
                    });
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                busy.remove(user);
            }
        }));
    }

    // ---- Profile row --------------------------------------------------------------------------

    private static boolean contact(int account, long dialog) {
        if (dialog <= 0) return false;
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialog);
        return user != null && user.contact && !user.bot;
    }
    /** Contacts with Telegram's own note show only that row, unless an older local note still exists. */
    public static boolean row(int account, long dialog, boolean nativeNote) {
        if (dialog == 0 || dialog == owner(account) || DialogObject.isEncryptedDialog(dialog)) return false;
        if (dialog > 0 && MessagesController.getInstance(account).getUser(dialog) == null) return false;
        if (has(account, dialog)) return true;
        return !(contact(account, dialog) && nativeNote);
    }
    public static CharSequence rowText(int account, long dialog) {
        String note = get(account, dialog);
        return note.isEmpty() ? text("Добавить заметку", "Add a note") : note;
    }
    public static CharSequence rowTitle(int account, long dialog) {
        return has(account, dialog) ? "📝 " + text("Заметка", "Note") : text("Заметка", "Note");
    }
    public static CharSequence rowInfo(int account, long dialog) {
        if (!has(account, dialog) && contact(account, dialog)) return text("Заметка Telegram о контакте · видна только вам", "Telegram contact note · only visible to you");
        return syncEnabled(account) ? text("Видна только вам · копия в «Избранном»", "Only visible to you · copy in Saved Messages")
                : text("Видна только вам · хранится на устройстве", "Only visible to you · stored on this device");
    }

    // ---- Editor -------------------------------------------------------------------------------

    public static void edit(BaseFragment host, int account, long dialog, Runnable changed) {
        if (host == null || host.getParentActivity() == null || dialog == 0) return;
        if (!has(account, dialog) && contact(account, dialog)) {
            Bundle args = new Bundle();
            args.putLong("user_id", dialog);
            args.putBoolean("focus_notes", true);
            host.presentFragment(new ContactAddActivity(args, host.getResourceProvider()));
            return;
        }
        android.content.Context c = host.getParentActivity();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), 0);
        EditTextBoldCursor field = NebulaFormUi.field(c, text("Текст заметки", "Note text"), 4, NebulaNoteDocument.MAX_NOTE);
        String before = get(account, dialog);
        field.setText(before);
        field.setSelection(field.length());
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        NebulaRow sync = new NebulaRow(c).title(text("Синхронизировать через «Избранное»", "Sync through Saved Messages"))
                .subtitle(text("Заметки сохранятся служебным сообщением #NebulaGramNotesV1 в вашем «Избранном»", "Notes are kept as a #NebulaGramNotesV1 service message in your Saved Messages"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(syncEnabled(account));
        sync.withClick(v -> setSyncEnabled(account, ((NebulaRow) v).toggleChecked()));
        box.addView(sync, new LinearLayout.LayoutParams(-1, -2));
        NebulaDialog.Builder builder = new NebulaDialog.Builder(c, host.getResourceProvider())
                .setTitle(text("Заметка", "Note"))
                .setMessage(text("Видна только вам. Собеседник её не получит.", "Only you can see it. The other side never receives it."))
                .setView(box)
                .setPositiveButton(text("Сохранить", "Save"), (d, w) -> { set(account, dialog, field.getText().toString()); if (changed != null) changed.run(); })
                .setNegativeButton(text("Отмена", "Cancel"), null);
        if (!before.isEmpty()) builder.setNeutralButton(text("Удалить", "Delete"), (d, w) -> { set(account, dialog, ""); if (changed != null) changed.run(); });
        host.showDialog(builder.create());
        pull(account, () -> {
            if (field.getText().toString().equals(before)) { String fresh = get(account, dialog); field.setText(fresh); field.setSelection(field.length()); }
            if (changed != null) changed.run();
        });
    }
}
