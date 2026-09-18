package app.nebulagram.ui;

/** Pure gesture policy, shared by hit testing and dispatch. Zero means native reactions. */
public final class NebulaMessageActions {
    private NebulaMessageActions() { }

    public static int doubleTap(int preference, boolean owned, boolean channelPost,
                                boolean canEdit, boolean hasText) {
        // A channel post is not a personal outgoing bubble, even for its administrator.
        if (!owned || channelPost) return 0;
        switch (preference) {
            case 1: return canEdit ? 1 : 0;
            case 2: return 2;
            case 3: return hasText ? 3 : 0;
            case 4: return 4;
            default: return 0;
        }
    }
}
