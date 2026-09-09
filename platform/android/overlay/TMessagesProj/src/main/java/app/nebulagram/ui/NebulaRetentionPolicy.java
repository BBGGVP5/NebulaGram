package app.nebulagram.ui;

/** Explicit opt-ins apply only to received content, never locally initiated deletion. */
public final class NebulaRetentionPolicy {
    private NebulaRetentionPolicy() { }
    public static boolean allowed(boolean enabled, boolean secret, boolean expiring,
            boolean saveSecret, boolean saveExpiring, boolean protectedContent,
            boolean incoming, boolean service, boolean validId) {
        return enabled && incoming && !service && validId && !protectedContent
                && (!secret || saveSecret) && (!expiring || saveExpiring);
    }
}
