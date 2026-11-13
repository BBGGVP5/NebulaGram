import app.nebulagram.ui.NebulaNoteDocument;
import app.nebulagram.ui.NebulaNoteDocument.Entry;
import java.util.*;

/** Serialisation, paging and merging of #NebulaGramNotesV1 pages. */
public final class NoteSyncCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static void rejects(String text, String what) {
        try { NebulaNoteDocument.parse(text); } catch (Exception expected) { return; }
        throw new AssertionError("accepted " + what);
    }
    public static void main(String[] args) throws Exception {
        long t = 1_750_000_000_000L;
        SortedMap<Long, Entry> local = new TreeMap<>();
        local.put(-1001234567890L, new Entry("канал: \"цитаты\" и \\ слэш\nвторая строка 😀", t));
        local.put(777L, new Entry("не контакт", t + 5));

        List<String> pages = NebulaNoteDocument.encode(local);
        check(pages.size() == 1 && pages.get(0).startsWith(NebulaNoteDocument.MARKER), "one page");
        NebulaNoteDocument.Page page = NebulaNoteDocument.parse(pages.get(0));
        check(page.index == 0 && page.notes.equals(local), "round trip " + page.notes);
        check(NebulaNoteDocument.load(NebulaNoteDocument.store(local)).equals(local), "local store round trip");
        check(NebulaNoteDocument.load("").isEmpty() && NebulaNoteDocument.load(null).isEmpty(), "empty store");
        check(NebulaNoteDocument.encode(new TreeMap<>()).size() == 1, "empty still has page 0");

        // Newest edit wins per chat; ties are deterministic and prefer the deletion.
        SortedMap<Long, Entry> remote = new TreeMap<>();
        remote.put(777L, new Entry("с другого устройства", t + 9));
        remote.put(-1001234567890L, new Entry("старое", t - 1));
        remote.put(42L, new Entry("новая", t));
        SortedMap<Long, Entry> merged = NebulaNoteDocument.merge(local, remote);
        check(merged.get(777L).text.equals("с другого устройства") && merged.get(-1001234567890L).text.startsWith("канал") && merged.get(42L).text.equals("новая"), "lww");
        check(merged.equals(NebulaNoteDocument.merge(remote, local)), "merge is symmetric");
        SortedMap<Long, Entry> a = new TreeMap<>(), b = new TreeMap<>();
        a.put(1L, new Entry("x", t)); b.put(1L, new Entry("", t));
        check(NebulaNoteDocument.merge(a, b).get(1L).deleted() && NebulaNoteDocument.merge(b, a).get(1L).deleted(), "tombstone wins tie");

        // Tombstones keep a deletion from resurrecting, then expire.
        SortedMap<Long, Entry> deleted = new TreeMap<>(); deleted.put(5L, new Entry("", t + 100));
        SortedMap<Long, Entry> stale = new TreeMap<>(); stale.put(5L, new Entry("старая заметка", t));
        check(NebulaNoteDocument.merge(stale, deleted).get(5L).deleted(), "delete beats older text");
        check(NebulaNoteDocument.prune(deleted, t + 100 + 1000).containsKey(5L), "fresh tombstone kept");
        check(!NebulaNoteDocument.prune(deleted, t + 100 + NebulaNoteDocument.TOMBSTONE_MS).containsKey(5L), "old tombstone pruned");
        check(NebulaNoteDocument.prune(stale, t + 10 * NebulaNoteDocument.TOMBSTONE_MS).containsKey(5L), "text never pruned");

        // Many long notes split into pages that each fit one Telegram message.
        SortedMap<Long, Entry> big = new TreeMap<>();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < NebulaNoteDocument.MAX_NOTE; i++) text.append(i % 2 == 0 ? 'я' : '"');
        for (long d = 1; d <= 30; d++) big.put(-d, new Entry(text.toString(), t + d));
        List<String> split = NebulaNoteDocument.encode(big);
        check(split.size() > 1, "paged");
        SortedMap<Long, Entry> joined = new TreeMap<>();
        for (int i = 0; i < split.size(); i++) {
            check(split.get(i).length() <= 4096, "page size " + split.get(i).length());
            NebulaNoteDocument.Page p = NebulaNoteDocument.parse(split.get(i));
            check(p.index == i, "page index");
            for (Long key : p.notes.keySet()) check(!joined.containsKey(key), "note in one page only");
            joined.putAll(p.notes);
        }
        check(joined.equals(big), "pages rejoin");

        // Malformed or hostile pages are rejected instead of half-applied.
        rejects("#NebulaGramSettingsV1\n{}", "other tag");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":2,\"page\":0,\"notes\":{}}", "version");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":1,\"page\":99,\"notes\":{}}", "page range");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":1,\"page\":0,\"notes\":{\"abc\":{\"t\":\"x\",\"u\":1}}}", "dialog key");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":1,\"page\":0,\"notes\":{\"0\":{\"t\":\"x\",\"u\":1}}}", "zero dialog");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":1,\"page\":0,\"notes\":{\"5\":{\"t\":7,\"u\":1}}}", "text type");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":1,\"page\":0,\"notes\":{\"5\":{\"t\":\"x\",\"u\":-3}}}", "time");
        rejects(NebulaNoteDocument.MARKER + "{\"version\":1,\"page\":0,\"notes\":{\"5\":{\"t\":\"" + new String(new char[NebulaNoteDocument.MAX_NOTE + 1]).replace('\0', 'я') + "\",\"u\":1}}}", "note too long");
        rejects(null, "null");
        System.out.println("note sync: ok");
    }
}
