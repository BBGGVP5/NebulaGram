package app.nebulagram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

/** One material, colour basis and motion for iOS-style menus. */
public final class NebulaMenuStyle {
    private NebulaMenuStyle() { }
    public static boolean enabled() { return NebulaAppearance.iosComposer() || NebulaAppearance.chatHeader(); }
    public static boolean animated() { return enabled() && NebulaAppearance.liquidAnimations(); }
    public static int radius() { return AndroidUtilities.dp(enabled() ? 24 : 12); }
    public static int surface(Theme.ResourcesProvider provider) {
        int base = Theme.getColor(Theme.key_windowBackgroundWhite, provider) | 0xff000000;
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, provider);
        return ColorUtils.blendARGB(base, accent, .09f);
    }
    public static BlurredBackgroundProvider provider(Theme.ResourcesProvider provider) {
        return new BlurredBackgroundProviderBuilder(provider)
                .setBackgroundColor((r, dark) -> Theme.multAlpha(surface(r),
                        LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) ? .78f : 1f))
                .setStrokeColorTop(0x66ffffff, 0x66ffffff)
                .setStrokeColorBottom(0x22000000, 0x28ffffff)
                .setStrokeWidth(AndroidUtilities.dpf2(.8f), AndroidUtilities.dpf2(.55f))
                .setShadowColor(0x30000000, 0x60000000)
                .setShadowLayer(AndroidUtilities.dpf2(8), 0, AndroidUtilities.dpf2(3)).build();
    }
    public static Drawable fallback(Theme.ResourcesProvider provider) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {ColorUtils.blendARGB(surface(provider), Color.WHITE, .06f), surface(provider)});
        fill.setCornerRadius(radius()); fill.setStroke(AndroidUtilities.dp(1), 0x44ffffff);
        return new InsetDrawable(fill, AndroidUtilities.dp(8)) {
            // Native callers tint the old monochrome popup asset. This surface already has its palette.
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        };
    }
    public static void styleRows(View view, Theme.ResourcesProvider provider) {
        if (!enabled()) return;
        if (view instanceof ActionBarMenuSubItem) {
            ActionBarMenuSubItem row = (ActionBarMenuSubItem) view;
            int color = row.getTextView().getCurrentTextColor();
            // Keep semantic colours (destructive actions, subscription actions).
            float[] hsv = new float[3]; Color.colorToHSV(color, hsv);
            if (hsv[1] < .25f) {
                color = NebulaChatColors.foreground(color, surface(provider));
                row.setTextColor(color); row.setIconColor(color);
            }
            row.setSelectorColor(Theme.multAlpha(color, .08f));
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) styleRows(group.getChildAt(i), provider);
        }
    }
    public static AnimatorSet opening(ActionBarPopupWindowLayout content, float finalScaleY) {
        content.setBackScaleY(finalScaleY); content.setBackAlpha(255);
        content.setPivotX(content.getMeasuredWidth() - AndroidUtilities.dp(28));
        content.setPivotY(content.shownFromBottom ? content.getMeasuredHeight() - AndroidUtilities.dp(28) : AndroidUtilities.dp(28));
        content.setScaleX(.66f); content.setScaleY(.76f); content.setAlpha(0);
        ValueAnimator frame = ValueAnimator.ofFloat(0f, 1f);
        frame.setInterpolator(new android.view.animation.LinearInterpolator());
        frame.addUpdateListener(a -> {
            float t=(float)a.getAnimatedValue();
            float p=(float)(1-Math.exp(-8*t)*Math.cos(7*t));
            content.setScaleX(.66f+.34f*p); content.setScaleY(.76f+.24f*p);
            content.setAlpha(Math.min(1,t*5));
            for(int i=0;i<content.getItemsCount();i++) {
                View child=content.getItemAt(i);
                child.setAlpha(Math.min(1, t*3)*(child.isEnabled()?1f:.5f));
                child.setTranslationY(AndroidUtilities.dp(5)*(1-Math.min(1,t*2.5f)));
            }
        });
        AnimatorSet set=new AnimatorSet(); set.playTogether(frame); set.setDuration(260);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                content.setScaleX(1); content.setScaleY(1); content.setAlpha(1);
                for(int i=0;i<content.getItemsCount();i++) {
                    View child=content.getItemAt(i); child.setTranslationY(0); child.setAlpha(child.isEnabled()?1f:.5f);
                }
            }
        });
        return set;
    }
}
