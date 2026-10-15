package app.nebulagram.ui;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Versioned account/scope ordering. No Telegram or Android dependency. */
public final class NebulaLocalPinStore {
    public interface Storage { String read(long owner); boolean write(long owner, String value); }
    public static final int MAX_PINS = 1000;
    public static final class Snapshot {
        public final boolean enabled;
        public final Map<String, List<Long>> orders;
        public final Map<Long, String> peers;
        private final Map<String, Map<Long, Integer>> ranks;
        Snapshot(boolean enabled, Map<String, List<Long>> orders, Map<Long, String> peers) {
            this.enabled = enabled;
            Map<String, List<Long>> lists = new HashMap<>(); Map<String, Map<Long, Integer>> index = new HashMap<>();
            for (Map.Entry<String, List<Long>> entry : orders.entrySet()) {
                List<Long> ids = Collections.unmodifiableList(new ArrayList<>(entry.getValue())); lists.put(entry.getKey(), ids);
                Map<Long, Integer> positions = new HashMap<>(); for (int i = 0; i < ids.size(); i++) positions.put(ids.get(i), i); index.put(entry.getKey(), positions);
            }
            this.orders = Collections.unmodifiableMap(lists); this.peers = Collections.unmodifiableMap(new HashMap<>(peers)); this.ranks = index;
        }
        public int rank(String scope, long peer) { Map<Long, Integer> values = ranks.get(scope); return !enabled || values == null ? -1 : values.getOrDefault(peer, -1); }
        public int count() { int count = 0; for (List<Long> ids : orders.values()) count += ids.size(); return count; }
    }
    private final Storage storage;
    private final Map<Long, Snapshot> cached = new HashMap<>();
    public NebulaLocalPinStore(Storage storage) { this.storage = storage; }
    public static boolean validScope(String scope) { return scope != null && scope.matches("root:[01]|filter:[1-9][0-9]{0,9}"); }
    public synchronized Snapshot read(long owner) {
        if (owner <= 0) return new Snapshot(false, Collections.emptyMap(), Collections.emptyMap());
        Snapshot result = cached.get(owner); if (result != null) return result;
        Map<String, List<Long>> orders = new HashMap<>(); Map<Long, String> peers = new HashMap<>(); boolean enabled = true;
        try {
            String raw = storage.read(owner);
            if (raw != null && !raw.isEmpty()) {
                if (raw.length() > 1_000_000) throw new IllegalArgumentException("Invalid local pins");
                JSONObject data = new JSONObject(raw); if (data.getInt("version") != 1) throw new IllegalArgumentException("Unsupported local pins");
                enabled = data.optBoolean("enabled", true); JSONObject saved = data.getJSONObject("orders"); int total = 0;
                for (Iterator<String> keys = saved.keys(); keys.hasNext();) {
                    String scope = keys.next(); if (!validScope(scope)) continue;
                    JSONArray values = saved.getJSONArray(scope); LinkedHashSet<Long> ids = new LinkedHashSet<>();
                    for (int i = 0; i < values.length() && total < MAX_PINS; i++) { long id = Long.parseLong(values.get(i).toString()); if (id != 0 && ids.add(id)) total++; }
                    if (!ids.isEmpty()) orders.put(scope, new ArrayList<>(ids));
                }
                JSONObject metadata = data.optJSONObject("peers");
                if (metadata != null) for (List<Long> ids : orders.values()) for (long id : ids) {
                    String input = metadata.optString(Long.toString(id), ""); if (input.length() <= 4096) peers.put(id, input);
                }
            }
        } catch (Exception malformed) { orders.clear(); peers.clear(); enabled = false; }
        result = new Snapshot(enabled, orders, peers); cached.put(owner, result); return result;
    }
    private boolean write(long owner, Snapshot next) {
        if (owner <= 0) return false;
        try {
            JSONObject data = new JSONObject(); data.put("version", 1); data.put("enabled", next.enabled);
            JSONObject orders = new JSONObject(); for (Map.Entry<String, List<Long>> entry : next.orders.entrySet()) orders.put(entry.getKey(), new JSONArray(entry.getValue())); data.put("orders", orders);
            JSONObject peers = new JSONObject(); for (Map.Entry<Long, String> entry : next.peers.entrySet()) peers.put(entry.getKey().toString(), entry.getValue()); data.put("peers", peers);
            if (!storage.write(owner, data.toString())) return false;
            cached.put(owner, next); return true;
        } catch (Exception error) { return false; }
    }
    public synchronized boolean pin(long owner, String scope, long peer, boolean pinned, String metadata) {
        if (owner <= 0 || peer == 0 || !validScope(scope) || metadata == null || metadata.length() > 4096) return false;
        Snapshot old = read(owner); Map<String, List<Long>> orders = new HashMap<>(old.orders); List<Long> ids = new ArrayList<>(orders.getOrDefault(scope, Collections.emptyList()));
        if (pinned == ids.contains(peer)) return true;
        if (pinned && old.count() >= MAX_PINS) return false;
        if (pinned) ids.add(0, peer); else ids.remove(peer);
        if (ids.isEmpty()) orders.remove(scope); else orders.put(scope, ids);
        Map<Long, String> peers = new HashMap<>(old.peers); if (pinned) peers.put(peer, metadata);
        Set<Long> retained = new HashSet<>(); for (List<Long> values : orders.values()) retained.addAll(values); peers.keySet().retainAll(retained);
        return write(owner, new Snapshot(old.enabled, orders, peers));
    }
    public synchronized boolean setEnabled(long owner, boolean enabled) { Snapshot old = read(owner); return write(owner, new Snapshot(enabled, old.orders, old.peers)); }
    public synchronized boolean clear(long owner) { return write(owner, new Snapshot(read(owner).enabled, Collections.emptyMap(), Collections.emptyMap())); }
    public static List<Long> merge(List<Long> remote, List<Long> local) { LinkedHashSet<Long> result = new LinkedHashSet<>(remote); result.addAll(local); result.remove(0L); return new ArrayList<>(result); }
}
