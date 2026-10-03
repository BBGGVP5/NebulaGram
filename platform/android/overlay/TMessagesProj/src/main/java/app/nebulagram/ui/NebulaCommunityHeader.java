package app.nebulagram.ui;

import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

/** Capture the underlying window and scrolling list without sampling sheet controls. */
@android.annotation.TargetApi(31)
public final class NebulaCommunityHeader {
    private final BlurredBackgroundSourceColor fallback;
    private final BlurredBackgroundSourceRenderNode source;
    private final BlurredBackgroundDrawable material;
    private final Theme.ResourcesProvider provider;
    private View backdrop;
    private final int[] origin = new int[2], position = new int[2];
    public void setBackdrop(View view) { backdrop = view; }

    public static NebulaCommunityHeader create(ActionBar bar, boolean hasAvatar, Theme.ResourcesProvider provider) {
        if (!NebulaAppearance.homeGlassHeader()) return null;
        return new NebulaCommunityHeader(bar, hasAvatar, provider);
    }

    private NebulaCommunityHeader(ActionBar bar, boolean hasAvatar, Theme.ResourcesProvider provider) {
        this.provider = provider;
        fallback = new BlurredBackgroundSourceColor();
        fallback.setColor(Theme.getColor(Theme.key_windowBackgroundGray, provider));
        source = Build.VERSION.SDK_INT >= 31 ? new BlurredBackgroundSourceRenderNode(fallback) : null;
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source != null ? source : fallback);
        factory.setLiquidGlassEffectAllowed(NebulaMenuStyle.animated());
        material = factory.create().setColorProvider(BlurredBackgroundProviderImpl.topPanel(provider));
        material.setPadding(0);
        material.setRadius(AndroidUtilities.dp(14), AndroidUtilities.dp(14), 0, 0);
        bar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanel(provider));
        bar.setNebulaCommunityGlass(true, hasAvatar);
        bar.getTitleTextView().setTranslationX(0);
    }

    /** Called in the parent's drawChild, after the current sheet/page position is settled. */
    public void draw(Canvas canvas, ActionBar bar, View list) {
        if (bar.getWidth() <= 0 || bar.getHeight() <= 0) return;
        int color = Theme.getColor(Theme.key_windowBackgroundGray, provider);
        fallback.setColor(color);
        int padding = 0;
        if (source != null) {
            boolean blur = canvas.isHardwareAccelerated() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) && !NebulaGlass.reduced();
            source.setBlur(blur ? AndroidUtilities.dpf2(NebulaGlass.blur()) : 0);
            padding = blur ? AndroidUtilities.dp(NebulaGlass.blur() * 2 + 8) : 0;
            Canvas capture = source.beginRecording(bar.getWidth() + padding * 2, bar.getHeight() + padding * 2);
            try {
                capture.drawColor(color);
                if (blur && backdrop != null && backdrop.isAttachedToWindow() && backdrop.getVisibility() == View.VISIBLE
                        && backdrop.getRootView() != bar.getRootView()) {
                    backdrop.getLocationOnScreen(origin); bar.getLocationOnScreen(position);
                    capture.save();
                    capture.translate(padding + origin[0] - position[0], padding + origin[1] - position[1]);
                    backdrop.draw(capture);
                    capture.restore();
                }
                if (blur && list.getVisibility() == View.VISIBLE) {
                    capture.translate(padding + list.getX() - bar.getX(), padding + list.getY() - bar.getY());
                    list.draw(capture);
                }
            } finally {
                source.endRecording();
            }
            source.invalidateDisplayListForDrawables();
        }
        float radius = bar.getY() > AndroidUtilities.statusBarHeight ? AndroidUtilities.dp(14) : 0;
        material.setRadius(radius, radius, 0, 0);
        material.setSourceOffset(padding, padding);
        material.setBounds(0, 0, bar.getWidth(), bar.getHeight());
        canvas.save();
        canvas.translate(bar.getX(), bar.getY());
        material.draw(canvas);
        canvas.restore();
    }
}
