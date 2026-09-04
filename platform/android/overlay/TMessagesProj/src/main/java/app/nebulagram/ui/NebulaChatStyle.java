package app.nebulagram.ui;

import org.telegram.ui.ActionBar.ActionBar;

/** Controls only the optional centred Liquid Glass layout of a normal chat. */
public final class NebulaChatStyle {
    private NebulaChatStyle() { }

    public static void header(ActionBar bar, boolean normalChat, boolean savedMessages) {
        if (bar != null) {
            boolean enabled = normalChat && NebulaAppearance.chatHeader();
            bar.setNebulaFloatingChatHeader(enabled, enabled && !savedMessages);
        }
    }
}
