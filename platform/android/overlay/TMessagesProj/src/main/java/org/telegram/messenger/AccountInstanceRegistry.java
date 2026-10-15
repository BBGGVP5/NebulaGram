package org.telegram.messenger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Lazy, process-lifetime controller instances keyed by the existing account ID.
 * Does not allocate account IDs or change authentication/session ownership.
 * Entries deliberately survive logout, just as Telegram's singleton arrays did:
 * controllers must finish their native cleanup before a slot may be reused.
 */
public final class AccountInstanceRegistry<T> {
    public interface Factory<T> {
        T create(int account);
    }

    private static final class Entry<T> {
        volatile T value;
    }

    private final ConcurrentHashMap<Integer, Entry<T>> entries = new ConcurrentHashMap<>();

    /** Read without constructing a controller or allocating an entry. */
    public T peek(int account) {
        checkAccount(account);
        Entry<T> entry = entries.get(account);
        return entry == null ? null : entry.value;
    }

    private Entry<T> entryFor(int account) {
        checkAccount(account);
        Entry<T> entry = entries.get(account);
        if (entry == null) {
            Entry<T> candidate = new Entry<>();
            Entry<T> existing = entries.putIfAbsent(account, candidate);
            entry = existing == null ? candidate : existing;
        }
        return entry;
    }

    /** The same monitor guards controller creation and its lazy child controllers. */
    public Object lockFor(int account) {
        return entryFor(account);
    }

    /** Existing values only; diagnostics must not initialize inactive accounts. */
    public List<T> valuesSnapshot() {
        ArrayList<T> values = new ArrayList<>();
        for (Entry<T> entry : entries.values()) {
            T value = entry.value;
            if (value != null) values.add(value);
        }
        return values;
    }

    public T get(int account, Factory<T> factory) {
        Entry<T> entry = entryFor(account);
        T value = entry.value;
        if (value == null) {
            // Never hold a ConcurrentHashMap bin lock while constructing a
            // controller: constructors can look up other account controllers.
            synchronized (entry) {
                value = entry.value;
                if (value == null) {
                    value = factory.create(account);
                    if (value == null) {
                        throw new NullPointerException("Account controller factory returned null");
                    }
                    entry.value = value;
                }
            }
        }
        return value;
    }

    /** Wallet teardown may discard only the instance it actually cleaned up. */
    public void clearIfSame(int account, T expected) {
        checkAccount(account);
        Entry<T> entry = entries.get(account);
        if (entry != null) {
            synchronized (entry) {
                if (entry.value == expected) {
                    entry.value = null;
                }
            }
        }
    }

    private static void checkAccount(int account) {
        if (account < 0) {
            throw new IllegalArgumentException("Account ID must be non-negative");
        }
    }
}
