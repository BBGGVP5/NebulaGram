package app.nebulagram.ui;

import android.graphics.Canvas;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

/** Records only the scrolling body, never the header or whole window. Android 31+. */
@android.annotation.TargetApi(31)
final class NebulaSettingsBackdrop {
    private final BlurredBackgroundSourceRenderNode source;
    private final BlurredBackgroundDrawable drawable;
    NebulaSettingsBackdrop(int surface){
        BlurredBackgroundSourceColor fallback=new BlurredBackgroundSourceColor();fallback.setColor(surface);
        source=new BlurredBackgroundSourceRenderNode(fallback);
        BlurredBackgroundDrawableViewFactory factory=new BlurredBackgroundDrawableViewFactory(source);
        factory.setLiquidGlassEffectAllowed(false);
        drawable=factory.create().setColorProvider(new NebulaMenuStyle.Material(null)
            .setBackgroundColor((r,dark)->androidx.core.graphics.ColorUtils.setAlphaComponent(surface,195))
            .setStrokeColorTop(0,0).setStrokeColorBottom(0,0).setStrokeWidth(0,0).setShadowColor(0,0).build());
    }
    void draw(Canvas canvas,View body,int width,int height){
        source.setBlur(AndroidUtilities.dpf2(NebulaGlass.blur()));
        Canvas recording=source.beginRecording(width,height);body.draw(recording);source.endRecording();
        drawable.setRadius(0);drawable.setIntensity(0);drawable.setBounds(0,0,width,height);drawable.draw(canvas);
    }
}
