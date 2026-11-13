package app.nebulagram.ui;

import org.json.JSONObject;
import java.util.*;

/**
 * Private chat notes as tagged Saved Messages pages. Pure Java (org.json only), checked on the JVM.
 * Each note carries its own edit time; merging keeps the newest edit, and deletions stay as
 * short-lived tombstones so another device cannot resurrect them.
 */
public final class NebulaNoteDocument {
    public static final String MARKER = "#NebulaGramNotesV1\n";
    public static final int MAX_NOTE = 1000, MAX_PAGE = 3900, MAX_PAGES = 40;
    public static final long TOMBSTONE_MS = 90L * 24 * 60 * 60 * 1000;

    public static final class Entry {
        public final String text;
        public final long updated;
        public Entry(String text, long updated) {
            if (text == null || text.length() > MAX_NOTE) throw new IllegalArgumentException("note");
            if (updated < 1 || updated > 4102444800000L) throw new IllegalArgumentException("time");
            this.text = text; this.updated = updated;
        }
        public boolean deleted() { return text.isEmpty(); }
        @Override public boolean equals(Object o) { return o instanceof Entry && ((Entry) o).text.equals(text) && ((Entry) o).updated == updated; }
        @Override public int hashCode() { return text.hashCode() * 31 + Long.hashCode(updated); }
        @Override public String toString() { return updated + ":" + text; }
    }

    public static final class Page {
        public final int index;
        public final SortedMap<Long, Entry> notes;
        Page(int index, SortedMap<Long, Entry> notes) { this.index = index; this.notes = notes; }
    }

    private NebulaNoteDocument() { }

    public static boolean newer(Entry a, Entry b) {
        if (b == null) return a != null;
        if (a == null) return false;
        if (a.updated != b.updated) return a.updated > b.updated;
        // Same millisecond on two devices: deterministic winner, tombstones win ties.
        if (a.deleted() != b.deleted()) return a.deleted();
        return a.text.compareTo(b.text) > 0;
    }

    public static SortedMap<Long, Entry> merge(Map<Long, Entry> a, Map<Long, Entry> b) {
        SortedMap<Long, Entry> out = new TreeMap<>(a);
        for (Map.Entry<Long, Entry> e : b.entrySet()) if (newer(e.getValue(), out.get(e.getKey()))) out.put(e.getKey(), e.getValue());
        return out;
    }

    public static SortedMap<Long, Entry> prune(Map<Long, Entry> notes, long now) {
        SortedMap<Long, Entry> out = new TreeMap<>();
        for (Map.Entry<Long, Entry> e : notes.entrySet())
            if (!e.getValue().deleted() || now - e.getValue().updated < TOMBSTONE_MS) out.put(e.getKey(), e.getValue());
        return out;
    }

    static String page(int index, Map<Long, Entry> notes) throws Exception {
        JSONObject map = new JSONObject();
        for (Map.Entry<Long, Entry> e : notes.entrySet())
            map.put(Long.toString(e.getKey()), new JSONObject().put("t", e.getValue().text).put("u", e.getValue().updated));
        return MARKER + new JSONObject().put("version", 1).put("page", index).put("notes", map);
    }

    /** Greedy split into pages that each fit one Telegram message. Empty input still yields page 0. */
    public static List<String> encode(Map<Long, Entry> notes) throws Exception {
        List<String> pages = new ArrayList<>();
        SortedMap<Long, Entry> current = new TreeMap<>();
        for (Map.Entry<Long, Entry> e : new TreeMap<>(notes).entrySet()) {
            current.put(e.getKey(), e.getValue());
            if (page(pages.size(), current).length() > MAX_PAGE) {
                current.remove(e.getKey());
                if (current.isEmpty()) throw new IllegalArgumentException("note too large");
                pages.add(page(pages.size(), current));
                current = new TreeMap<>(); current.put(e.getKey(), e.getValue());
                if (page(pages.size(), current).length() > MAX_PAGE) throw new IllegalArgumentException("note too large");
            }
        }
        pages.add(page(pages.size(), current));
        if (pages.size() > MAX_PAGES) throw new IllegalArgumentException("pages");
        return pages;
    }

    public static Page parse(String text) throws Exception {
        if (text == null || text.length() > 4096 || !text.startsWith(MARKER)) throw new IllegalArgumentException("marker");
        JSONObject json = new JSONObject(text.substring(MARKER.length()));
        if (json.getInt("version") != 1) throw new IllegalArgumentException("version");
        int index = json.getInt("page");
        if (index < 0 || index >= MAX_PAGES) throw new IllegalArgumentException("page");
        JSONObject map = json.getJSONObject("notes");
        SortedMap<Long, Entry> notes = new TreeMap<>();
        for (Iterator<String> it = map.keys(); it.hasNext(); ) {
            String key = it.next();
            if (!key.matches("-?\\d{1,19}")) throw new IllegalArgumentException("dialog");
            long dialog = Long.parseLong(key);
            if (dialog == 0) throw new IllegalArgumentException("dialog");
            JSONObject n = map.getJSONObject(key);
            Object t = n.get("t"), u = n.get("u");
            if (!(t instanceof String) || !(u instanceof Number)) throw new IllegalArgumentException("entry");
            notes.put(dialog, new Entry((String) t, ((Number) u).longValue()));
        }
        return new Page(index, notes);
    }

    /** Local storage form: the same entry map without the page envelope. */
    public static String store(Map<Long, Entry> notes) throws Exception {
        JSONObject map = new JSONObject();
        for (Map.Entry<Long, Entry> e : notes.entrySet())
            map.put(Long.toString(e.getKey()), new JSONObject().put("t", e.getValue().text).put("u", e.getValue().updated));
        return map.toString();
    }

    public static SortedMap<Long, Entry> load(String json) throws Exception {
        SortedMap<Long, Entry> notes = new TreeMap<>();
        if (json == null || json.isEmpty()) return notes;
        Page page = parse(MARKER + new JSONObject().put("version", 1).put("page", 0).put("notes", new JSONObject(json)));
        return page.notes;
    }
}
