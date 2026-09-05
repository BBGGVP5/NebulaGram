package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.AvatarDrawable;

/** Controls only the optional centred Liquid Glass layout of a normal chat. */
public final class NebulaChatStyle {
    private NebulaChatStyle() { }

    /** Outer blur bounds; 6dp of drawable padding surrounds each visible surface. */
    public static int headerWidth(int barWidth, int textWidth) {
        return Math.max(0, Math.min(Math.max(AndroidUtilities.dp(112), textWidth + AndroidUtilities.dp(40)),
                barWidth - AndroidUtilities.dp(128)));
    }

    public static void header(ActionBar bar, boolean normalChat, boolean savedMessages) {
        if (bar != null) {
            boolean enabled = normalChat && NebulaAppearance.chatHeader();
            bar.setNebulaFloatingChatHeader(enabled, enabled && !savedMessages, enabled && savedMessages);
        }
    }

    /**
     * Saved Messages keeps its search and menu behaviour, but the menu's
     * visible affordance is the familiar bookmark mark rather than dots.
     */
    public static void savedMessagesMenuIcon(ActionBarMenuItem item, boolean savedMessages) {
        if (item == null || !savedMessages || !NebulaAppearance.chatHeader()) return;
        AvatarDrawable icon = new AvatarDrawable();
        icon.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
        item.setIcon(new SizedDrawable(icon, AndroidUtilities.dp(30)));
    }

    private static final class SizedDrawable extends Drawable {
        private final Drawable drawable;
        private final int size;

        SizedDrawable(Drawable drawable, int size) {
            this.drawable = drawable;
            this.size = size;
        }

        @Override
        public void draw(Canvas canvas) {
            drawable.setBounds(getBounds());
            drawable.draw(canvas);
        }

        @Override public void setAlpha(int alpha) { drawable.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { drawable.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
    }
}
