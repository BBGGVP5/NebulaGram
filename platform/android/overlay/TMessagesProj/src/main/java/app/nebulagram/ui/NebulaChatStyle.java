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
        if (!NebulaAppearance.chatHeader()) return Math.max(0, barWidth - NebulaHeaderCounter.backWidth() - AndroidUtilities.dp(58));
        if (!NebulaAppearance.adaptiveHeader()) return Math.max(0, barWidth - NebulaHeaderCounter.backWidth() - AndroidUtilities.dp(70));
        return Math.max(0, Math.min(Math.max(AndroidUtilities.dp(112), textWidth + AndroidUtilities.dp(40)),
                barWidth - NebulaHeaderCounter.backWidth() - AndroidUtilities.dp(70)));
    }

    public static int headerLeft(int barWidth, int headerWidth) {
        return NebulaAppearance.chatHeader() && NebulaAppearance.centeredHeader()
                ? Math.max(NebulaHeaderCounter.backWidth(), (barWidth - headerWidth) / 2) : NebulaHeaderCounter.backWidth();
    }

    public static int headerTextLeft(int containerWidth, int capsuleWidth, int textWidth) {
        if (!NebulaAppearance.chatHeader()) return NebulaHeaderCounter.backWidth() + AndroidUtilities.dp(54);
        return headerLeft(containerWidth + AndroidUtilities.dp(12), capsuleWidth)
                - AndroidUtilities.dp(12) / 2 + (capsuleWidth - textWidth) / 2;
    }

    public static void header(ActionBar bar, boolean normalChat, boolean savedMessages) {
        if (bar != null) {
            bar.setNebulaClassicSavedHeader(normalChat && savedMessages && NebulaTheme.materialYouEnabled());
            boolean enabled = normalChat && !savedMessages && NebulaTheme.materialYouEnabled();
            bar.setNebulaFloatingChatHeader(enabled, enabled && NebulaAppearance.chatHeader() && !savedMessages, enabled && savedMessages);
        }
    }

    /**
     * Saved Messages keeps its search and menu behaviour, but the menu's
     * visible affordance is the familiar bookmark mark rather than dots.
     */
    public static void savedMessagesMenuIcon(ActionBarMenuItem item, boolean savedMessages) {
        // Saved Messages uses Telegram's native header and overflow icon.
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
