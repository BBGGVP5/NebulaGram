package app.nebulagram.ui;

import org.telegram.ui.ActionBar.ActionBar;

/** Controls only the optional centred Liquid Glass layout of a normal chat. */
public final class NebulaChatStyle {
    private NebulaChatStyle() { }

    public static void header(ActionBar bar, boolean normalChat) {
        if (bar != null) {
            bar.setNebulaFloatingChatHeader(normalChat && NebulaAppearance.chatHeader());
        }
    }
}
