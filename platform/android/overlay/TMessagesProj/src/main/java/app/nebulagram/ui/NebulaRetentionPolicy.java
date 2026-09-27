package app.nebulagram.ui;

/** Eligibility at remote-deletion hooks; locally initiated deletion bypasses retention. */
public final class NebulaRetentionPolicy {
    private NebulaRetentionPolicy() { }
    public static boolean receivedOrSaved(boolean incoming, long peer, long owner) {
        return incoming || owner != 0 && peer == owner;
    }
    public static boolean allowed(boolean enabled, boolean secret, boolean expiring,
            boolean saveSecret, boolean saveExpiring, boolean protectedContent,
            boolean incoming, boolean service, boolean validId) {
        return enabled && incoming && !service && validId && !protectedContent
                && (!secret || saveSecret) && (!expiring || saveExpiring);
    }
}
