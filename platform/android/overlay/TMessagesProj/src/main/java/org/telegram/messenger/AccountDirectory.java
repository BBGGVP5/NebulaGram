package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.TreeSet;

/** Android persistence and lifecycle adapters for the local account catalogue. */
public final class AccountDirectory {
    public interface Action { void run(int account); }
    private static volatile AccountCatalog catalog;
    private static final ArrayList<Subscription> subscriptions = new ArrayList<>();

    private AccountDirectory() {}

    private static AccountCatalog catalog() {
        AccountCatalog value = catalog;
        if (value == null) synchronized (AccountDirectory.class) {
            value = catalog;
            if (value == null) {
                Context context = ApplicationLoader.applicationContext;
                SharedPreferences preferences = context.getSharedPreferences("nebula_accounts", Context.MODE_PRIVATE);
                int[] legacy = preferences.contains("catalog") ? new int[0] : discoverLegacy(context);
                value = new AccountCatalog(new AccountCatalog.Storage() {
                    public String read() { return preferences.getString("catalog", null); }
                    public boolean write(String data) { return preferences.edit().putString("catalog", data).commit(); }
                }, legacy);
                catalog = value;
            }
        }
        return value;
    }

    private static int[] discoverLegacy(Context context) {
        TreeSet<Integer> ids = new TreeSet<>();
        File preferencesDirectory = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] preferences = preferencesDirectory.listFiles();
        if (preferences != null) for (File file : preferences) {
            String name = file.getName();
            if (name.equals("userconfing.xml")) {
                ids.add(0);
            } else if (name.matches("(?:userconfig|logininfo2_)[0-9]+\\.xml")) {
                String number = name.replaceFirst("^(?:userconfig|logininfo2_)", "").replaceFirst("\\.xml$", "");
                try { ids.add(Integer.parseInt(number)); } catch (NumberFormatException ignored) {}
            }
        }
        // Native sessions live under files/accountN; their names and keys stay intact.
        File[] directories = ApplicationLoader.getFilesDirFixed().listFiles();
        if (directories != null) for (File file : directories) {
            if (file.isDirectory() && file.getName().matches("account[0-9]+")) {
                try { ids.add(Integer.parseInt(file.getName().substring(7))); } catch (NumberFormatException ignored) {}
            }
        }
        int[] result = new int[ids.size()]; int index = 0;
        for (int id : ids) result[index++] = id;
        return result;
    }

    public static int[] ids() { return catalog().snapshot(); }
    public static boolean contains(int id) { return id >= 0 && catalog().contains(id); }
    public static boolean canAllocate() { return catalog().canAllocate(); }

    public static int allocate() {
        final int id = catalog().allocate();
        AndroidUtilities.runOnUIThread(() -> notifyChanged(id, true));
        return id;
    }

    public static void retire(int id) {
        // Keep existing listeners through the current appDidLogout broadcast;
        // the catalogue changes immediately so a fresh login gets a new ID.
        if (catalog().retire(id)) AndroidUtilities.runOnUIThread(() -> notifyChanged(id, false), 1);
    }

    private static void notifyChanged(int id, boolean added) {
        ArrayList<Subscription> snapshot;
        synchronized (subscriptions) { snapshot = new ArrayList<>(subscriptions); }
        for (Subscription subscription : snapshot) subscription.update(id, added);
    }

    /** Call on the UI thread, matching NotificationCenter's observer lifecycle. */
    public static Subscription observe(Action added, Action removed) {
        Subscription subscription = new Subscription(added, removed);
        synchronized (subscriptions) { subscriptions.add(subscription); }
        for (int id : ids()) subscription.update(id, true);
        return subscription;
    }

    public static final class Subscription {
        private final Action added, removed;
        private final LinkedHashSet<Integer> bound = new LinkedHashSet<>();
        private boolean closed;
        private Subscription(Action added, Action removed) { this.added = added; this.removed = removed; }
        private synchronized void update(int id, boolean add) {
            if (closed) return;
            if (add) {
                if (bound.add(id) && added != null) added.run(id);
            } else if (bound.remove(id) && removed != null) removed.run(id);
        }
        public synchronized void close() {
            if (closed) return;
            closed = true;
            synchronized (subscriptions) { subscriptions.remove(this); }
            if (removed != null) for (int id : new ArrayList<>(bound)) removed.run(id);
            bound.clear();
        }
    }
}
