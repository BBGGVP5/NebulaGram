package app.nebulagram.ui;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import org.telegram.ui.ActionBar.Theme;

/** Repair incomplete per-chat palettes against the surface actually drawn. */
public final class NebulaChatColors {
    private NebulaChatColors() { }

    public static int backgroundKey(int key) {
        if (!NebulaAppearance.chatHeader() && !NebulaAppearance.iosComposer()) return -1;
        if (key == Theme.key_actionBarDefaultSubmenuItem || key == Theme.key_actionBarDefaultSubmenuItemIcon) {
            return Theme.key_actionBarDefaultSubmenuBackground;
        }
        if (key == Theme.key_actionBarDefaultTitle || key == Theme.key_actionBarDefaultSubtitle
                || key == Theme.key_glass_defaultText || key == Theme.key_glass_defaultIcon) {
            return Theme.key_chat_messagePanelBackground;
        }
        if (key == Theme.key_chat_messagePanelText || key == Theme.key_chat_messagePanelIcons
                || key == Theme.key_chat_messagePanelSend || key == Theme.key_chat_replyPanelIcons
                || key == Theme.key_chat_searchPanelIcons || key == Theme.key_chat_botButtonText) return Theme.key_chat_messagePanelBackground;
        return -1;
    }

    public static int foreground(int original, int background) {
        background |= 0xFF000000;
        if (ColorUtils.calculateContrast(original, background) >= 4.5) return original;
        int opaque = original | 0xFF000000;
        if (ColorUtils.calculateContrast(opaque, background) >= 4.5) return opaque;
        return ColorUtils.calculateContrast(Color.WHITE, background) >= ColorUtils.calculateContrast(Color.BLACK, background)
                ? Color.WHITE : Color.BLACK;
    }
}
