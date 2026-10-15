package org.telegram.messenger;

import java.util.TreeSet;

/** Persistent local IDs. Retired IDs are never assigned to another login. */
public final class AccountCatalog {
    public interface Storage {
        String read();
        boolean write(String value);
    }

    private final Storage storage;
    private final TreeSet<Integer> ids = new TreeSet<>();
    private long next;

    public AccountCatalog(Storage storage, int[] legacyIds) {
        this.storage = storage;
        String saved = storage.read();
        if (saved == null) {
            for (int id : legacyIds) {
                checkId(id);
                ids.add(id);
                next = Math.max(next, (long) id + 1);
            }
            // Preserve account 0 for the first login on a fresh installation.
            if (ids.isEmpty()) { ids.add(0); next = 1; }
            persist(ids, next);
        } else {
            String[] parts = saved.split("\\n", -1);
            try {
                if (parts.length != 3 || !"1".equals(parts[0])) throw new IllegalArgumentException();
                next = Long.parseLong(parts[1]);
                if (next < 1 || next > (long) Integer.MAX_VALUE + 1) throw new IllegalArgumentException();
                if (!parts[2].isEmpty()) for (String value : parts[2].split(",", -1)) {
                    int id = Integer.parseInt(value);
                    checkId(id);
                    if (id >= next || !ids.add(id)) throw new IllegalArgumentException();
                }
            } catch (RuntimeException invalid) {
                // Never reset the allocator on malformed storage: that could
                // silently attach a new identity to an old account directory.
                throw new IllegalStateException("Invalid persisted account catalogue", invalid);
            }
        }
    }

    public synchronized int[] snapshot() {
        int[] result = new int[ids.size()];
        int index = 0;
        for (int id : ids) result[index++] = id;
        return result;
    }

    public synchronized boolean contains(int id) { return ids.contains(id); }

    public synchronized boolean canAllocate() { return next <= Integer.MAX_VALUE; }

    public synchronized int allocate() {
        if (!canAllocate()) throw new IllegalStateException("Account ID space exhausted");
        int id = (int) next;
        TreeSet<Integer> updated = new TreeSet<>(ids);
        updated.add(id);
        persist(updated, next + 1);
        ids.add(id);
        next++;
        return id;
    }

    public synchronized boolean retire(int id) {
        if (!ids.contains(id)) return false;
        TreeSet<Integer> updated = new TreeSet<>(ids);
        updated.remove(id);
        persist(updated, next);
        ids.remove(id);
        return true;
    }

    private void persist(TreeSet<Integer> values, long nextId) {
        StringBuilder encoded = new StringBuilder("1\n").append(nextId).append('\n');
        for (int id : values) {
            if (encoded.charAt(encoded.length() - 1) != '\n') encoded.append(',');
            encoded.append(id);
        }
        if (!storage.write(encoded.toString())) throw new IllegalStateException("Unable to save account IDs");
    }

    private static void checkId(int id) {
        if (id < 0) throw new IllegalArgumentException("Account ID must be non-negative");
    }
}
