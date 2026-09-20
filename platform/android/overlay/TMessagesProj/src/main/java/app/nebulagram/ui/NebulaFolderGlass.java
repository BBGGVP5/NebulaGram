package app.nebulagram.ui;

import android.os.Build;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

/** A light tint over captured chats, not the opaque color of the top toolbar. */
public final class NebulaFolderGlass {
    private NebulaFolderGlass() { }

    public static BlurredBackgroundProvider provider(Theme.ResourcesProvider resources) {
        return new BlurredBackgroundProviderBuilder(resources)
                .setBackgroundColor((r, dark) -> {
                    int color = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    boolean fallback = Build.VERSION.SDK_INT < 31 || !SharedConfig.chatBlurEnabled() || NebulaGlass.reduced();
                    return Theme.multAlpha(color, fallback ? .90f : dark ? .22f : .30f);
                })
                .setStrokeColorTop(0xBFFFFFFF, 0x50FFFFFF)
                .setStrokeColorBottom(0x22000000, 0x18FFFFFF)
                .setStrokeWidth(AndroidUtilities.dpf2(.65f), AndroidUtilities.dpf2(.45f))
                .setShadowColor(0x18000000, 0x14000000)
                .setShadowLayer(AndroidUtilities.dpf2(3), 0, AndroidUtilities.dpf2(1))
                .build();
    }
}
