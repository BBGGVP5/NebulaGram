package app.nebulagram.ui;

import android.content.Context;
import android.widget.FrameLayout;
import org.telegram.messenger.UserConfig;

/** Telegram's animated rocket: the shared receiver owns loading, replay and detach. */
public final class NebulaUpdateMascot extends FrameLayout {
    public NebulaUpdateMascot(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Decode the native page animation at 88 dp, fit it to the compact header.
        addView(new NebulaAnimatedEmoji(context, UserConfig.selectedAccount, "🚀", 88), new LayoutParams(-1, -1));
    }
}
