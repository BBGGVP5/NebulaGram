package app.nebulagram.ui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Search and presentation order only; account IDs never depend on list position. */
public final class NebulaAccountList {
    public static final class Entry {
        public final int id, loginTime;
        public final String name, username;
        public Entry(int id, String name, String username, int loginTime) {
            this.id = id; this.name = name == null ? "" : name;
            this.username = username == null ? "" : username; this.loginTime = loginTime;
        }
    }
    private NebulaAccountList() {}
    private static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }
    public static List<Entry> visible(List<Entry> entries, String query, int order) {
        String needle = normalized(query);
        if (needle.startsWith("@")) needle = needle.substring(1);
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (needle.isEmpty() || normalized(entry.name).contains(needle)
                    || normalized(entry.username).contains(needle)) result.add(entry);
        }
        Comparator<Entry> comparator = order == 1
                ? Comparator.comparing(entry -> normalized(entry.name))
                : order == 2 ? Comparator.comparingInt(entry -> entry.id)
                : Comparator.<Entry>comparingInt(entry -> entry.loginTime).reversed();
        result.sort(comparator.thenComparingInt(entry -> entry.id));
        return result;
    }
}
